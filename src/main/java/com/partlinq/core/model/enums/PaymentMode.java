package com.partlinq.core.model.enums;

/**
 * How a payment was made.
 * Reflects actual payment methods used in Indian Tier 2 cities.
 */
public enum PaymentMode {
	/** Cash payment (most common in Tier 2) */
	CASH,
	/** UPI payment (PhonePe, Google Pay, Paytm) */
	UPI,
	/** Bank transfer / NEFT / RTGS */
	BANK_TRANSFER,
	/** Cheque */
	CHEQUE,
	/** Adjustment / write-off by shop owner */
	ADJUSTMENT
}
