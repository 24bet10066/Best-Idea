package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.ApplianceType;
import com.partlinq.core.model.enums.PartCategory;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

/**
 * DTO for spare part creation/update requests.
 * Contains validation annotations for request data validation.
 */
public record SparePartRequest(
	@NotBlank(message = "Part number is required")
	@Size(min = 3, max = 100, message = "Part number must be between 3 and 100 characters")
	String partNumber,

	@NotBlank(message = "Part name is required")
	@Size(min = 2, max = 255, message = "Part name must be between 2 and 255 characters")
	String name,

	@Size(max = 1000, message = "Description must not exceed 1000 characters")
	String description,

	@NotNull(message = "Part category is required")
	PartCategory category,

	@NotNull(message = "Appliance type is required")
	ApplianceType applianceType,

	@NotBlank(message = "Brand is required")
	@Size(min = 2, max = 100, message = "Brand must be between 2 and 100 characters")
	String brand,

	@Size(max = 1000, message = "Model compatibility must not exceed 1000 characters")
	String modelCompatibility,

	@NotNull(message = "MRP is required")
	@DecimalMin(value = "0.01", message = "MRP must be greater than 0")
	BigDecimal mrp,

	@NotNull(message = "OEM flag is required")
	Boolean isOem
) {}
