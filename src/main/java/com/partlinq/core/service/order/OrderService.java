package com.partlinq.core.service.order;

import com.partlinq.core.exception.EntityNotFoundException;
import com.partlinq.core.model.dto.OrderRequest;
import com.partlinq.core.model.dto.OrderResponse;
import com.partlinq.core.model.entity.*;
import com.partlinq.core.model.enums.OrderStatus;
import com.partlinq.core.model.enums.TrustEventType;
import com.partlinq.core.repository.*;
import com.partlinq.core.service.credit.CreditService;
import com.partlinq.core.service.inventory.InventoryService;
import com.partlinq.core.service.trust.TrustGraphService;
import com.partlinq.core.service.udhaar.UdhaarService;
import com.partlinq.core.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing order lifecycle.
 * Handles order placement, confirmation, fulfillment, and completion.
 * Integrates with inventory, credit, and trust services.
 */
@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class OrderService {

	private final OrderRepository orderRepository;
	private final TechnicianRepository technicianRepository;
	private final PartsShopRepository partsShopRepository;
	private final SparePartRepository sparePartRepository;
	private final InventoryItemRepository inventoryItemRepository;
	private final TrustEventRepository trustEventRepository;
	private final InventoryService inventoryService;
	private final CreditService creditService;
	private final TrustGraphService trustGraphService;
	private final UdhaarService udhaarService;
	private final NotificationService notificationService;

	private static final Random RANDOM = new Random();
	private static final DateTimeFormatter ORDER_NUMBER_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");

	/**
	 * Place a new order with inventory reservation and credit validation.
	 *
	 * @param request OrderRequest with technician, shop, and items
	 * @return OrderResponse with order details
	 * @throws EntityNotFoundException if technician or shop not found
	 * @throws IllegalStateException if inventory unavailable or credit declined
	 */
	public OrderResponse placeOrder(OrderRequest request) {
		log.info("Placing order for technician {} at shop {}", request.technicianId(), request.shopId());

		// Validate technician and shop exist
		Technician technician = technicianRepository.findById(request.technicianId())
			.orElseThrow(() -> new EntityNotFoundException("Technician", request.technicianId()));

		PartsShop shop = partsShopRepository.findById(request.shopId())
			.orElseThrow(() -> new EntityNotFoundException("Shop", request.shopId()));

		// Build order items and calculate totals
		List<OrderItem> orderItems = new ArrayList<>();
		BigDecimal orderTotal = BigDecimal.ZERO;

		for (OrderRequest.OrderItemRequest itemRequest : request.items()) {
			SparePart part = sparePartRepository.findById(itemRequest.sparePartId())
				.orElseThrow(() -> new EntityNotFoundException("SparePart", itemRequest.sparePartId()));

			// Get selling price from inventory
			Optional<InventoryItem> invItem = inventoryItemRepository.findByShopIdAndSparePartId(
				shop.getId(), itemRequest.sparePartId()
			);

			BigDecimal unitPrice = invItem.map(InventoryItem::getSellingPrice)
				.orElse(part.getMrp());

			BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(itemRequest.quantity()));

			OrderItem orderItem = OrderItem.builder()
				.sparePart(part)
				.quantity(itemRequest.quantity())
				.unitPrice(unitPrice)
				.lineTotal(lineTotal)
				.build();

			orderItems.add(orderItem);
			orderTotal = orderTotal.add(lineTotal);
		}

		// Check credit if needed
		Boolean creditUsed = false;
		LocalDateTime paymentDueDate = null;
		if (orderTotal.compareTo(BigDecimal.ZERO) > 0) {
			if (!creditService.canExtendCredit(request.technicianId(), orderTotal)) {
				log.warn("Credit declined for technician {}: amount={}", request.technicianId(), orderTotal);
				throw new IllegalStateException("Insufficient credit limit for this order");
			}
			creditUsed = true;
			paymentDueDate = LocalDateTime.now().plusDays(30);
		}

		// Reserve inventory
		if (!inventoryService.reserveForOrder(shop.getId(), orderItems)) {
			log.error("Inventory reservation failed for order");
			throw new IllegalStateException("Unable to reserve inventory for this order");
		}

		// Generate order number
		String orderNumber = generateOrderNumber();

		// Create and save order
		Order order = Order.builder()
			.orderNumber(orderNumber)
			.technician(technician)
			.shop(shop)
			.status(OrderStatus.PLACED)
			.totalAmount(orderTotal)
			.creditUsed(creditUsed)
			.paymentDueDate(paymentDueDate)
			.notes(request.notes())
			.build();

		Order savedOrder = orderRepository.save(order);

		// Save order items — initialize if null (Lombok @Builder skips field initializers)
		if (savedOrder.getOrderItems() == null) {
			savedOrder.setOrderItems(new HashSet<>());
		}
		for (OrderItem item : orderItems) {
			item.setOrder(savedOrder);
		}
		savedOrder.getOrderItems().addAll(orderItems);
		orderRepository.save(savedOrder);

		// Record in udhaar ledger if credit was used
		if (creditUsed) {
			udhaarService.recordCredit(
				request.technicianId(), request.shopId(), savedOrder.getId(), orderTotal);
		}

		// Queue order confirmation notification
		notificationService.queueOrderPlaced(savedOrder);

		log.info("Order placed successfully: {} with {} items, total={}",
			orderNumber, orderItems.size(), orderTotal);

		return mapToResponse(savedOrder);
	}

	/**
	 * Confirm an order (shop confirms receipt).
	 * Transitions order from PLACED to CONFIRMED status.
	 *
	 * @param orderId UUID of the order to confirm
	 * @return Updated OrderResponse
	 * @throws EntityNotFoundException if order not found
	 */
	public OrderResponse confirmOrder(UUID orderId) {
		log.info("Confirming order: {}", orderId);

		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new EntityNotFoundException("Order", orderId));

		if (!order.getStatus().equals(OrderStatus.PLACED)) {
			throw new IllegalStateException("Order must be in PLACED status to confirm");
		}

		order.setStatus(OrderStatus.CONFIRMED);
		Order savedOrder = orderRepository.save(order);

		log.info("Order confirmed: {}", orderId);
		return mapToResponse(savedOrder);
	}

	/**
	 * Mark order as ready for pickup (shop marks items ready).
	 * Transitions order from CONFIRMED to READY status.
	 *
	 * @param orderId UUID of the order
	 * @return Updated OrderResponse
	 * @throws EntityNotFoundException if order not found
	 */
	public OrderResponse markReady(UUID orderId) {
		log.info("Marking order ready: {}", orderId);

		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new EntityNotFoundException("Order", orderId));

		if (!order.getStatus().equals(OrderStatus.CONFIRMED)) {
			throw new IllegalStateException("Order must be in CONFIRMED status to mark as ready");
		}

		order.setStatus(OrderStatus.READY);
		Order savedOrder = orderRepository.save(order);

		// Notify technician that order is ready for pickup
		notificationService.queueOrderReady(savedOrder);

		log.info("Order marked ready: {}", orderId);
		return mapToResponse(savedOrder);
	}

	/**
	 * Mark order as picked up (technician picks up items).
	 * Transitions order from READY to PICKED_UP status.
	 * Confirms inventory reservation.
	 *
	 * @param orderId UUID of the order
	 * @return Updated OrderResponse
	 * @throws EntityNotFoundException if order not found
	 */
	public OrderResponse markPickedUp(UUID orderId) {
		log.info("Marking order picked up: {}", orderId);

		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new EntityNotFoundException("Order", orderId));

		if (!order.getStatus().equals(OrderStatus.READY)) {
			throw new IllegalStateException("Order must be in READY status to mark as picked up");
		}

		// Confirm inventory reservation
		List<OrderItem> items = new ArrayList<>(order.getOrderItems());
		inventoryService.confirmPickup(orderId, order.getShop().getId(), items);

		order.setStatus(OrderStatus.PICKED_UP);
		Order savedOrder = orderRepository.save(order);

		log.info("Order picked up: {}", orderId);
		return mapToResponse(savedOrder);
	}

	/**
	 * Complete an order (payment received).
	 * Transitions order to COMPLETED status.
	 * Records trust events for payment timing.
	 *
	 * @param orderId UUID of the order
	 * @param paymentDate Optional payment date (defaults to now)
	 * @return Updated OrderResponse
	 * @throws EntityNotFoundException if order not found
	 */
	public OrderResponse completeOrder(UUID orderId, LocalDateTime paymentDate) {
		log.info("Completing order: {}", orderId);

		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new EntityNotFoundException("Order", orderId));

		if (order.getStatus().equals(OrderStatus.COMPLETED) || order.getStatus().equals(OrderStatus.CANCELLED)) {
			throw new IllegalStateException("Order cannot be completed in its current status");
		}

		LocalDateTime paidDate = paymentDate != null ? paymentDate : LocalDateTime.now();
		order.setPaidAt(paidDate);
		order.setStatus(OrderStatus.COMPLETED);

		Order savedOrder = orderRepository.save(order);

		// Record trust events
		Technician tech = order.getTechnician();
		double currentScore = tech.getTrustScore();

		if (order.getCreditUsed() && order.getPaymentDueDate() != null) {
			TrustEventType eventType;
			int daysFromInvoice;

			// createdAt may be null if @CreationTimestamp hasn't flushed yet
			LocalDateTime orderDate = order.getCreatedAt() != null ? order.getCreatedAt() : LocalDateTime.now();

			if (paidDate.isBefore(order.getPaymentDueDate())) {
				eventType = TrustEventType.PAYMENT_ON_TIME;
				daysFromInvoice = (int) java.time.temporal.ChronoUnit.DAYS.between(
					orderDate, paidDate
				);
			} else {
				eventType = TrustEventType.PAYMENT_LATE;
				daysFromInvoice = (int) java.time.temporal.ChronoUnit.DAYS.between(
					orderDate, paidDate
				);
			}

			double paymentDelta = eventType.equals(TrustEventType.PAYMENT_ON_TIME) ? 2.0 : -3.0;
			double newScoreAfterPayment = Math.max(0.0, Math.min(100.0, currentScore + paymentDelta));

			TrustEvent trustEvent = TrustEvent.builder()
				.technician(tech)
				.eventType(eventType)
				.reason("Payment for order " + order.getOrderNumber())
				.scoreDelta(paymentDelta)
				.previousScore(currentScore)
				.newScore(newScoreAfterPayment)
				.createdAt(LocalDateTime.now())
				.build();

			trustEventRepository.save(trustEvent);
			currentScore = newScoreAfterPayment;

			// Update credit record
			creditService.recordPayment(tech.getId(), orderId, daysFromInvoice);
		}

		// Record ORDER_COMPLETED event
		double completionDelta = 1.0;
		double newScoreAfterCompletion = Math.max(0.0, Math.min(100.0, currentScore + completionDelta));

		TrustEvent completionEvent = TrustEvent.builder()
			.technician(tech)
			.eventType(TrustEventType.ORDER_COMPLETED)
			.reason("Order " + order.getOrderNumber() + " completed")
			.scoreDelta(completionDelta)
			.previousScore(currentScore)
			.newScore(newScoreAfterCompletion)
			.createdAt(LocalDateTime.now())
			.build();

		trustEventRepository.save(completionEvent);

		// Update technician's trust score in DB
		tech.setTrustScore(newScoreAfterCompletion);
		tech.setTotalTransactions(tech.getTotalTransactions() + 1);
		technicianRepository.save(tech);

		log.info("Order completed: {} with trust event recorded", orderId);
		return mapToResponse(savedOrder);
	}

	/**
	 * Cancel an order.
	 * Transitions order to CANCELLED status.
	 * Restores reserved inventory.
	 *
	 * @param orderId UUID of the order to cancel
	 * @return Updated OrderResponse
	 * @throws EntityNotFoundException if order not found
	 */
	public OrderResponse cancelOrder(UUID orderId) {
		log.info("Cancelling order: {}", orderId);

		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new EntityNotFoundException("Order", orderId));

		if (order.getStatus().equals(OrderStatus.COMPLETED) || order.getStatus().equals(OrderStatus.CANCELLED)) {
			throw new IllegalStateException("Order cannot be cancelled in its current status");
		}

		// Cancel inventory reservations
		List<OrderItem> items = new ArrayList<>(order.getOrderItems());
		inventoryService.cancelOrder(orderId, order.getShop().getId(), items);

		order.setStatus(OrderStatus.CANCELLED);
		Order savedOrder = orderRepository.save(order);

		log.info("Order cancelled: {}", orderId);
		return mapToResponse(savedOrder);
	}

	/**
	 * Get order by ID.
	 *
	 * @param orderId UUID of the order
	 * @return OrderResponse
	 * @throws EntityNotFoundException if order not found
	 */
	public OrderResponse getOrderById(UUID orderId) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new EntityNotFoundException("Order", orderId));
		return mapToResponse(order);
	}

	/**
	 * Get all orders for a technician.
	 *
	 * @param technicianId UUID of the technician
	 * @return List of OrderResponse
	 */
	public List<OrderResponse> getOrdersByTechnician(UUID technicianId) {
		List<Order> orders = orderRepository.findByTechnicianIdOrderByCreatedAtDesc(technicianId);
		return orders.stream()
			.map(this::mapToResponse)
			.collect(Collectors.toList());
	}

	/**
	 * Get all orders for a shop.
	 *
	 * @param shopId UUID of the shop
	 * @return List of OrderResponse
	 */
	public List<OrderResponse> getOrdersByShop(UUID shopId) {
		List<Order> orders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopId);
		return orders.stream()
			.map(this::mapToResponse)
			.collect(Collectors.toList());
	}

	/**
	 * Convert Order entity to OrderResponse DTO.
	 *
	 * @param order Order entity
	 * @return OrderResponse DTO
	 */
	private OrderResponse mapToResponse(Order order) {
		List<OrderResponse.OrderItemResponse> itemResponses = order.getOrderItems().stream()
			.map(item -> new OrderResponse.OrderItemResponse(
				item.getId(),
				item.getSparePart().getId(),
				item.getSparePart().getName(),
				item.getSparePart().getPartNumber(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getLineTotal()
			))
			.collect(Collectors.toList());

		return new OrderResponse(
			order.getId(),
			order.getOrderNumber(),
			order.getTechnician().getId(),
			order.getTechnician().getFullName(),
			order.getShop().getId(),
			order.getShop().getShopName(),
			order.getStatus(),
			order.getTotalAmount(),
			order.getCreditUsed(),
			order.getPaymentDueDate(),
			order.getPaidAt(),
			order.getCreatedAt(),
			order.getUpdatedAt(),
			order.getNotes(),
			itemResponses
		);
	}

	/**
	 * Generate unique order number in format PLQ-YYYYMMDD-XXXXX.
	 *
	 * @return Generated order number
	 */
	private String generateOrderNumber() {
		String datePart = LocalDateTime.now().format(ORDER_NUMBER_FORMATTER);
		String randomPart = String.format("%05d", RANDOM.nextInt(100000));
		return "PLQ-" + datePart + "-" + randomPart;
	}
}
