package com.partlinq.core.model.dto;

import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * DTO for inventory item update requests.
 * Contains validation annotations for request data validation.
 */
public record InventoryUpdateRequest(
	@NotNull(message = "Shop ID is required")
	UUID shopId,

	@NotNull(message = "Spare part ID is required")
	UUID sparePartId,

	@NotNull(message = "Quantity is required")
	@Min(value = 0, message = "Quantity cannot be negative")
	Integer quantity,

	@NotNull(message = "Selling price is required")
	@DecimalMin(value = "0.01", message = "Selling price must be greater than 0")
	BigDecimal sellingPrice,

	@NotNull(message = "Minimum stock level is required")
	@Min(value = 0, message = "Minimum stock level cannot be negative")
	Integer minStockLevel,

	@NotNull(message = "Availability status is required")
	Boolean isAvailable
) {}
