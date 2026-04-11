package com.partlinq.core.model.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Udhaar (credit) summary for a technician at a specific shop.
 * This is the primary view a shop owner uses: "Raju owes ₹3,200. Last paid 12 days ago."
 */
public record UdhaarSummary(
	UUID technicianId,
	String technicianName,
	String technicianPhone,
	UUID shopId,
	String shopName,
	BigDecimal totalCredit,
	BigDecimal totalPaid,
	BigDecimal currentBalance,
	LocalDateTime lastCreditDate,
	LocalDateTime lastPaymentDate,
	int daysSinceLastPayment,
	int totalUnpaidOrders,
	String riskTag,
	List<LedgerEntry> recentEntries
) {
	/**
	 * Individual ledger entry for display.
	 */
	public record LedgerEntry(
		UUID entryId,
		String entryType,
		BigDecimal amount,
		BigDecimal balanceAfter,
		String paymentMode,
		String referenceNumber,
		String notes,
		String recordedBy,
		LocalDateTime createdAt,
		UUID orderId,
		String orderNumber
	) {}
}
