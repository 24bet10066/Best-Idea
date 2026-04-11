package com.partlinq.core.model.dto;

import jakarta.validation.constraints.*;

import java.util.UUID;

/**
 * DTO for trust endorsement creation requests.
 * Contains validation annotations for request data validation.
 */
public record EndorsementRequest(
	@NotNull(message = "Endorsee ID is required")
	UUID endorseeId,

	@NotNull(message = "Weight is required")
	@DecimalMin(value = "0.0", message = "Weight must be between 0 and 1")
	@DecimalMax(value = "1.0", message = "Weight must be between 0 and 1")
	Double weight,

	@Size(max = 500, message = "Message must not exceed 500 characters")
	String message
) {}
