package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.ApplianceType;
import com.partlinq.core.model.enums.PartCategory;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for spare part search response.
 * Includes part details and availability across different shops.
 */
public record PartSearchResponse(
	UUID partId,
	String partNumber,
	String name,
	String description,
	PartCategory category,
	ApplianceType applianceType,
	String brand,
	String modelCompatibility,
	BigDecimal mrp,
	Boolean isOem,
	LocalDateTime createdAt,
	List<ShopAvailability> availabilityAcrossShops
) implements Serializable {

	/**
	 * Nested record for availability at a specific shop
	 */
	public record ShopAvailability(
		UUID shopId,
		String shopName,
		String city,
		Integer availableQuantity,
		BigDecimal sellingPrice,
		Boolean isAvailable
	) implements Serializable {}
}
