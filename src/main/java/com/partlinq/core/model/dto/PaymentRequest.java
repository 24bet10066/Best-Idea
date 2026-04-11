package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.PaymentMode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Request to record a payment from a technician.
 * This is what the shop owner does when a technician pays their udhaar.
 */
public record PaymentRequest(
	@NotNull(message = "Technician ID is required")
	UUID technicianId,

	@NotNull(message = "Shop ID is required")
	UUID shopId,

	@NotNull(message = "Payment amount is required")
	@DecimalMin(value = "0.01", message = "Payment must be greater than zero")
	BigDecimal amount,

	@NotNull(message = "Payment mode is required")
	PaymentMode paymentMode,

	/** UPI transaction ID, cheque number, etc. */
	String referenceNumber,

	/** Optional note: "Weekly settlement", "Partial payment", etc. */
	String notes,

	/** Who recorded this: shop owner name */
	String recordedBy
) {}
