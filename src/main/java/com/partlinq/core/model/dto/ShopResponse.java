package com.partlinq.core.model.dto;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * DTO for parts shop response data.
 * Contains shop profile information with operational metrics.
 */
public record ShopResponse(
	UUID id,
	String shopName,
	String ownerName,
	String phone,
	String email,
	String address,
	String city,
	String pincode,
	String gstNumber,
	Boolean isVerified,
	Double rating,
	Integer totalOrdersServed,
	LocalDateTime registeredAt
) implements Serializable {}
