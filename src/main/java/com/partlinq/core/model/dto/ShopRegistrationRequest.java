package com.partlinq.core.model.dto;

import jakarta.validation.constraints.*;

/**
 * DTO for parts shop registration requests.
 * Contains validation annotations for request data validation.
 */
public record ShopRegistrationRequest(
	@NotBlank(message = "Shop name is required")
	@Size(min = 2, max = 255, message = "Shop name must be between 2 and 255 characters")
	String shopName,

	@NotBlank(message = "Owner name is required")
	@Size(min = 2, max = 255, message = "Owner name must be between 2 and 255 characters")
	String ownerName,

	@NotBlank(message = "Phone number is required")
	@Pattern(regexp = "^[0-9]{10}$", message = "Phone number must be exactly 10 digits")
	String phone,

	@NotBlank(message = "Email is required")
	@Email(message = "Email must be valid")
	String email,

	@NotBlank(message = "Address is required")
	@Size(min = 5, max = 500, message = "Address must be between 5 and 500 characters")
	String address,

	@NotBlank(message = "City is required")
	@Size(min = 2, max = 100, message = "City must be between 2 and 100 characters")
	String city,

	@NotBlank(message = "PIN code is required")
	@Pattern(regexp = "^[0-9]{6}$", message = "PIN code must be exactly 6 digits")
	String pincode,

	@NotBlank(message = "GST number is required")
	@Pattern(regexp = "^[0-9]{15}$", message = "GST number must be exactly 15 digits")
	String gstNumber
) {}
