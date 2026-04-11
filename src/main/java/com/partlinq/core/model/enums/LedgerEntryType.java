package com.partlinq.core.model.enums;

/**
 * Type of entry in the payment ledger.
 * Maps to how udhaar (credit) works in Indian spare parts shops.
 */
public enum LedgerEntryType {
	/** Technician took parts on credit — balance owed INCREASES */
	CREDIT,
	/** Technician made a payment — balance owed DECREASES */
	PAYMENT,
	/** Manual adjustment by shop owner (correction, discount, write-off) */
	ADJUSTMENT
}
