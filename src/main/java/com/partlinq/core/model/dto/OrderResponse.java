package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.OrderStatus;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for order response data.
 * Contains complete order information with items and status.
 */
public record OrderResponse(
	UUID orderId,
	String orderNumber,
	UUID technicianId,
	String technicianName,
	UUID shopId,
	String shopName,
	OrderStatus status,
	BigDecimal totalAmount,
	Boolean creditUsed,
	LocalDateTime paymentDueDate,
	LocalDateTime paidAt,
	LocalDateTime createdAt,
	LocalDateTime updatedAt,
	String notes,
	List<OrderItemResponse> items
) implements Serializable {

	/**
	 * Nested record for order items in the response
	 */
	public record OrderItemResponse(
		UUID itemId,
		UUID sparePartId,
		String partName,
		String partNumber,
		Integer quantity,
		BigDecimal unitPrice,
		BigDecimal lineTotal
	) implements Serializable {}
}
