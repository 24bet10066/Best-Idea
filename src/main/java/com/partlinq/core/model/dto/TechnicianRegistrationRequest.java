package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.ApplianceType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.util.Set;

/**
 * DTO for technician registration requests.
 * Contains validation annotations for request data validation.
 */
public record TechnicianRegistrationRequest(
	@NotBlank(message = "Full name is required")
	@Size(min = 2, max = 255, message = "Full name must be between 2 and 255 characters")
	String fullName,

	@NotBlank(message = "Phone number is required")
	@Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
	String phone,

	@NotBlank(message = "Email is required")
	@Email(message = "Email must be valid")
	String email,

	@NotBlank(message = "City is required")
	@Size(min = 2, max = 100, message = "City must be between 2 and 100 characters")
	String city,

	@NotBlank(message = "PIN code is required")
	@Pattern(regexp = "^[0-9]{6}$", message = "PIN code must be exactly 6 digits")
	String pincode,

	@NotEmpty(message = "At least one specialization is required")
	Set<ApplianceType> specializations,

	@NotNull(message = "Credit limit is required")
	@DecimalMin(value = "1000.0", message = "Credit limit must be at least 1000")
	BigDecimal creditLimit,

	String profileImageUrl
) {}
