package com.partlinq.core.service.invoice;

import com.partlinq.core.exception.EntityNotFoundException;
import com.partlinq.core.model.dto.InvoiceData;
import com.partlinq.core.model.entity.Order;
import com.partlinq.core.model.entity.OrderItem;
import com.partlinq.core.repository.OrderRepository;
import com.partlinq.core.service.udhaar.UdhaarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Invoice generation service.
 *
 * Generates invoices/receipts (parchi) for orders.
 * Output is an immutable InvoiceData record that can be:
 * - Rendered as WhatsApp text (most common — shop owner forwards to technician)
 * - Rendered as HTML/PDF (for printing)
 * - Stored for GST compliance
 *
 * GST calculation: 18% on spare parts (standard rate for electrical/mechanical parts).
 * In practice, many Tier 2 shops operate with inclusive pricing, but we track GST
 * separately for compliance when the shop is GST-registered.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class InvoiceService {

	private final OrderRepository orderRepository;
	private final UdhaarService udhaarService;

	private static final BigDecimal GST_RATE = new BigDecimal("0.18");
	private static final DateTimeFormatter INVOICE_NUMBER_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	/**
	 * Generate an invoice for a specific order.
	 *
	 * @param orderId UUID of the order
	 * @return InvoiceData record with all invoice details
	 */
	public InvoiceData generateInvoice(UUID orderId) {
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new EntityNotFoundException("Order", orderId));

		log.info("Generating invoice for order: {}", order.getOrderNumber());

		// Build line items
		AtomicInteger serialCounter = new AtomicInteger(1);
		List<InvoiceData.InvoiceLineItem> lineItems = order.getOrderItems().stream()
			.sorted(Comparator.comparing(item -> item.getSparePart().getName()))
			.map(item -> new InvoiceData.InvoiceLineItem(
				serialCounter.getAndIncrement(),
				item.getSparePart().getPartNumber(),
				item.getSparePart().getName(),
				item.getSparePart().getBrand(),
				item.getQuantity(),
				item.getUnitPrice(),
				item.getLineTotal()
			))
			.collect(Collectors.toList());

		// Calculate GST (inclusive — extract from total)
		BigDecimal totalAmount = order.getTotalAmount();
		BigDecimal subtotal = totalAmount.divide(BigDecimal.ONE.add(GST_RATE), 2, RoundingMode.HALF_UP);
		BigDecimal gstAmount = totalAmount.subtract(subtotal);

		// Get outstanding balance if on credit
		BigDecimal outstandingBalance = null;
		if (order.getCreditUsed()) {
			outstandingBalance = udhaarService.getCurrentBalance(
				order.getTechnician().getId(), order.getShop().getId());
		}

		// Generate invoice number: INV-YYYYMMDD-OrderSuffix
		String invoiceNumber = "INV-" +
			order.getCreatedAt().format(INVOICE_NUMBER_FORMAT) + "-" +
			order.getOrderNumber().substring(order.getOrderNumber().lastIndexOf('-') + 1);

		return new InvoiceData(
			invoiceNumber,
			order.getCreatedAt(),
			order.getOrderNumber(),
			order.getId(),
			order.getShop().getShopName(),
			order.getShop().getAddress(),
			order.getShop().getCity(),
			order.getShop().getPhone(),
			order.getShop().getGstNumber(),
			order.getTechnician().getFullName(),
			order.getTechnician().getPhone(),
			order.getTechnician().getCity(),
			lineItems,
			subtotal,
			gstAmount,
			totalAmount,
			order.getCreditUsed(),
			outstandingBalance,
			order.getPaymentDueDate(),
			order.getNotes()
		);
	}

	/**
	 * Generate WhatsApp-ready text for an order invoice.
	 * This is the most common use case — shop owner sends parchi via WhatsApp.
	 *
	 * @param orderId UUID of the order
	 * @return WhatsApp-formatted text string
	 */
	public String generateWhatsAppInvoice(UUID orderId) {
		InvoiceData invoice = generateInvoice(orderId);
		return invoice.toWhatsAppText();
	}
}
