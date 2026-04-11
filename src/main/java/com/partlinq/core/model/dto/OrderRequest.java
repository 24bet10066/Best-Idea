package com.partlinq.core.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.util.List;
import java.util.UUID;

/**
 * DTO for order creation requests.
 * Contains validation annotations for request data validation.
 */
public record OrderRequest(
	@NotNull(message = "Technician ID is required")
	UUID technicianId,

	@NotNull(message = "Shop ID is required")
	UUID shopId,

	@NotEmpty(message = "Order must contain at least one item")
	@Valid
	List<OrderItemRequest> items,

	@Size(max = 1000, message = "Notes must not exceed 1000 characters")
	String notes
) {

	/**
	 * Nested record for order items in the request
	 */
	public record OrderItemRequest(
		@NotNull(message = "Spare part ID is required")
		UUID sparePartId,

		@NotNull(message = "Quantity is required")
		@Min(value = 1, message = "Quantity must be at least 1")
		Integer quantity
	) {}
}
