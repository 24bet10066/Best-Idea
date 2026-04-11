package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.OrderStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for shop owner dashboard response data.
 * Contains summary of today's operations, alerts, and key metrics.
 */
public record DashboardResponse(
	UUID shopId,
	String shopName,
	Long todayOrderCount,
	BigDecimal todayRevenue,
	Long pendingOrdersCount,
	List<DailyOrderSummary> todayOrders,
	List<LowStockAlert> lowStockAlerts,
	List<TopTechnician> topTechnicians
) implements Serializable {

	/**
	 * Nested record for daily order summaries
	 */
	public record DailyOrderSummary(
		UUID orderId,
		String orderNumber,
		String technicianName,
		OrderStatus status,
		BigDecimal amount,
		LocalDateTime createdAt
	) implements Serializable {}

	/**
	 * Nested record for low stock alerts
	 */
	public record LowStockAlert(
		UUID inventoryId,
		String partName,
		String partNumber,
		Integer currentQuantity,
		Integer minimumLevel,
		Integer shortfallCount
	) implements Serializable {}

	/**
	 * Nested record for top technicians by order count
	 */
	public record TopTechnician(
		UUID technicianId,
		String technicianName,
		Long orderCount,
		Double trustScore
	) implements Serializable {}
}
