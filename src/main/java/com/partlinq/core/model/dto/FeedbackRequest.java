package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.ApplianceType;
import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * DTO for customer feedback submission requests.
 * Contains validation annotations for request data validation.
 */
public record FeedbackRequest(
	@NotNull(message = "Technician ID is required")
	UUID technicianId,

	@NotBlank(message = "Customer name is required")
	@Size(min = 2, max = 255, message = "Customer name must be between 2 and 255 characters")
	String customerName,

	@Pattern(regexp = "^[0-9]{10}$|^$", message = "Customer phone must be 10 digits or empty")
	String customerPhone,

	@NotNull(message = "Rating is required")
	@Min(value = 1, message = "Rating must be between 1 and 5")
	@Max(value = 5, message = "Rating must be between 1 and 5")
	Integer rating,

	@Size(max = 1000, message = "Comment must not exceed 1000 characters")
	String comment,

	ApplianceType serviceType
) {}
