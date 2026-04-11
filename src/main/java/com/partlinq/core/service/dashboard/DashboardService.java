package com.partlinq.core.service.dashboard;

import com.partlinq.core.engine.inventory.ConcurrentInventoryEngine;
import com.partlinq.core.exception.EntityNotFoundException;
import com.partlinq.core.model.dto.DashboardResponse;
import com.partlinq.core.model.entity.Order;
import com.partlinq.core.model.entity.PartsShop;
import com.partlinq.core.model.entity.SparePart;
import com.partlinq.core.repository.OrderRepository;
import com.partlinq.core.repository.PartsShopRepository;
import com.partlinq.core.repository.SparePartRepository;
import com.partlinq.core.service.inventory.InventoryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for building shop owner dashboards.
 * Provides today's order summary, low stock alerts, and top technician metrics.
 */
@Service
@Slf4j
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class DashboardService {

	private final OrderRepository orderRepository;
	private final PartsShopRepository partsShopRepository;
	private final SparePartRepository sparePartRepository;
	private final InventoryService inventoryService;

	/**
	 * Build dashboard for a shop with today's metrics and alerts.
	 *
	 * @param shopId UUID of the shop
	 * @return DashboardResponse with orders, alerts, and top technicians
	 * @throws EntityNotFoundException if shop not found
	 */
	public DashboardResponse getShopDashboard(UUID shopId) {
		log.info("Building dashboard for shop: {}", shopId);

		PartsShop shop = partsShopRepository.findById(shopId)
			.orElseThrow(() -> new EntityNotFoundException("Shop", shopId));

		// Get today's date range
		LocalDateTime startOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MIN);
		LocalDateTime endOfToday = LocalDateTime.of(LocalDate.now(), LocalTime.MAX);

		// Query today's orders
		List<Order> todayOrders = orderRepository.findByShopAndDateRange(shopId, startOfToday, endOfToday);

		// Build daily order summaries
		List<DashboardResponse.DailyOrderSummary> orderSummaries = todayOrders.stream()
			.map(order -> new DashboardResponse.DailyOrderSummary(
				order.getId(),
				order.getOrderNumber(),
				order.getTechnician().getFullName(),
				order.getStatus(),
				order.getTotalAmount(),
				order.getCreatedAt()
			))
			.collect(Collectors.toList());

		// Calculate today's revenue
		BigDecimal todayRevenue = todayOrders.stream()
			.map(Order::getTotalAmount)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		// Count pending orders
		long pendingCount = todayOrders.stream()
			.filter(order -> !order.getStatus().name().equals("COMPLETED") &&
				!order.getStatus().name().equals("CANCELLED"))
			.count();

		// Get low stock alerts
		List<ConcurrentInventoryEngine.StockAlert> lowStockAlerts = inventoryService.getLowStockAlerts(shopId);

		List<DashboardResponse.LowStockAlert> alertSummaries = lowStockAlerts.stream()
			.map(alert -> {
				// Resolve part name and number from repository
				String partName = "Unknown";
				String partNumber = "N/A";
				Optional<SparePart> partOpt = sparePartRepository.findById(alert.partId);
				if (partOpt.isPresent()) {
					partName = partOpt.get().getName();
					partNumber = partOpt.get().getPartNumber();
				}
				return new DashboardResponse.LowStockAlert(
					alert.partId,
					partName,
					partNumber,
					alert.currentQuantity,
					alert.minLevel,
					alert.minLevel - alert.currentQuantity
				);
			})
			.collect(Collectors.toList());

		// Get top technicians by order count (top 10)
		List<DashboardResponse.TopTechnician> topTechnicians = getTopTechniciansForShop(shopId, 10);

		log.info("Dashboard built for shop {}: {} orders today, revenue={}, {} alerts",
			shopId, todayOrders.size(), todayRevenue, alertSummaries.size());

		return new DashboardResponse(
			shopId,
			shop.getShopName(),
			(long) todayOrders.size(),
			todayRevenue,
			pendingCount,
			orderSummaries,
			alertSummaries,
			topTechnicians
		);
	}

	/**
	 * Get top technicians by order count for a shop.
	 *
	 * @param shopId Shop UUID
	 * @param limit  Number of top technicians to return
	 * @return List of TopTechnician records
	 */
	private List<DashboardResponse.TopTechnician> getTopTechniciansForShop(UUID shopId, int limit) {
		List<Order> allOrders = orderRepository.findByShopIdOrderByCreatedAtDesc(shopId);

		Map<UUID, Long> technicianOrderCounts = new HashMap<>();
		Map<UUID, String> technicianNames = new HashMap<>();
		Map<UUID, Double> technicianTrustScores = new HashMap<>();

		for (Order order : allOrders) {
			UUID techId = order.getTechnician().getId();
			technicianOrderCounts.merge(techId, 1L, Long::sum);
			if (!technicianNames.containsKey(techId)) {
				technicianNames.put(techId, order.getTechnician().getFullName());
				technicianTrustScores.put(techId, order.getTechnician().getTrustScore());
			}
		}

		return technicianOrderCounts.entrySet().stream()
			.sorted((e1, e2) -> Long.compare(e2.getValue(), e1.getValue()))
			.limit(limit)
			.map(entry -> new DashboardResponse.TopTechnician(
				entry.getKey(),
				technicianNames.get(entry.getKey()),
				entry.getValue(),
				technicianTrustScores.get(entry.getKey())
			))
			.collect(Collectors.toList());
	}
}
