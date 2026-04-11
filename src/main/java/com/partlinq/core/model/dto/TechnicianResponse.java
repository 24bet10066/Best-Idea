package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.ApplianceType;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Set;
import java.util.UUID;

/**
 * DTO for technician response data.
 * Contains technician profile information with trust metrics.
 */
public record TechnicianResponse(
	UUID id,
	String fullName,
	String phone,
	String email,
	String city,
	String pincode,
	Set<ApplianceType> specializations,
	Double trustScore,
	BigDecimal creditLimit,
	Integer totalTransactions,
	Double avgPaymentDays,
	Boolean isVerified,
	String profileImageUrl,
	UUID referredById,
	LocalDateTime registeredAt,
	LocalDateTime lastActiveAt
) implements Serializable {}
