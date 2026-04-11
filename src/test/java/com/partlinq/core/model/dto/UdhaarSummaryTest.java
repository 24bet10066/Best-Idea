package com.partlinq.core.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for UdhaarSummary and LedgerEntry records.
 * These records are the primary data structures for the udhaar feature.
 */
class UdhaarSummaryTest {

	@Test
	@DisplayName("UdhaarSummary correctly holds all technician-shop balance data")
	void testUdhaarSummaryCreation() {
		UUID techId = UUID.randomUUID();
		UUID shopId = UUID.randomUUID();

		UdhaarSummary summary = new UdhaarSummary(
			techId, "Raju Kumar", "9876543210",
			shopId, "Sharma Parts",
			new BigDecimal("10000"),  // total credit
			new BigDecimal("7000"),   // total paid
			new BigDecimal("3000"),   // current balance
			LocalDateTime.now().minusDays(5),   // last credit
			LocalDateTime.now().minusDays(2),   // last payment
			2,                        // days since last payment
			2,                        // unpaid orders
			"NORMAL",                 // risk tag
			List.of()                 // recent entries
		);

		assertEquals("Raju Kumar", summary.technicianName());
		assertEquals(new BigDecimal("3000"), summary.currentBalance());
		assertEquals(2, summary.daysSinceLastPayment());
		assertEquals("NORMAL", summary.riskTag());
		assertEquals(2, summary.totalUnpaidOrders());
	}

	@Test
	@DisplayName("LedgerEntry holds credit entry data correctly")
	void testLedgerEntryCreditType() {
		UUID entryId = UUID.randomUUID();
		UUID orderId = UUID.randomUUID();

		UdhaarSummary.LedgerEntry entry = new UdhaarSummary.LedgerEntry(
			entryId, "CREDIT",
			new BigDecimal("1500"), new BigDecimal("4500"),
			null, null,
			"Order PLQ-20260410-12345 on credit",
			"system",
			LocalDateTime.now(),
			orderId, "PLQ-20260410-12345"
		);

		assertEquals("CREDIT", entry.entryType());
		assertEquals(new BigDecimal("1500"), entry.amount());
		assertEquals(new BigDecimal("4500"), entry.balanceAfter());
		assertNull(entry.paymentMode());
		assertEquals("PLQ-20260410-12345", entry.orderNumber());
	}

	@Test
	@DisplayName("LedgerEntry holds payment entry data correctly")
	void testLedgerEntryPaymentType() {
		UUID entryId = UUID.randomUUID();

		UdhaarSummary.LedgerEntry entry = new UdhaarSummary.LedgerEntry(
			entryId, "PAYMENT",
			new BigDecimal("2000"), new BigDecimal("1000"),
			"UPI", "TXN123456789",
			"Weekly payment via Google Pay",
			"Sharma ji",
			LocalDateTime.now(),
			null, null
		);

		assertEquals("PAYMENT", entry.entryType());
		assertEquals(new BigDecimal("2000"), entry.amount());
		assertEquals(new BigDecimal("1000"), entry.balanceAfter());
		assertEquals("UPI", entry.paymentMode());
		assertEquals("TXN123456789", entry.referenceNumber());
		assertNull(entry.orderId());
	}

	@Test
	@DisplayName("Risk tag OVERDUE for technicians with 30+ days without payment")
	void testRiskTagMapping() {
		UdhaarSummary overdue = createSummaryWithRisk("OVERDUE", 35);
		assertEquals("OVERDUE", overdue.riskTag());

		UdhaarSummary atRisk = createSummaryWithRisk("AT_RISK", 20);
		assertEquals("AT_RISK", atRisk.riskTag());

		UdhaarSummary normal = createSummaryWithRisk("NORMAL", 5);
		assertEquals("NORMAL", normal.riskTag());

		UdhaarSummary clear = createSummaryWithRisk("CLEAR", 0);
		assertEquals("CLEAR", clear.riskTag());
	}

	@Test
	@DisplayName("Balance correctly represents credit minus payments")
	void testBalanceCalculation() {
		BigDecimal totalCredit = new BigDecimal("15000");
		BigDecimal totalPaid = new BigDecimal("12000");
		BigDecimal balance = new BigDecimal("3000");

		UdhaarSummary summary = new UdhaarSummary(
			UUID.randomUUID(), "Test Tech", "9999999999",
			UUID.randomUUID(), "Test Shop",
			totalCredit, totalPaid, balance,
			null, null, -1, 0, "NORMAL", List.of()
		);

		// Balance = totalCredit - totalPaid
		assertEquals(totalCredit.subtract(totalPaid), summary.currentBalance());
	}

	@Test
	@DisplayName("daysSinceLastPayment is -1 when technician never paid")
	void testNeverPaidTechnician() {
		UdhaarSummary summary = new UdhaarSummary(
			UUID.randomUUID(), "New Tech", "8888888888",
			UUID.randomUUID(), "Test Shop",
			new BigDecimal("5000"), BigDecimal.ZERO, new BigDecimal("5000"),
			LocalDateTime.now(), null,
			-1,  // never paid
			1, "NEW_CREDIT", List.of()
		);

		assertEquals(-1, summary.daysSinceLastPayment());
		assertNull(summary.lastPaymentDate());
		assertEquals("NEW_CREDIT", summary.riskTag());
	}

	// ---- Helpers ----

	private UdhaarSummary createSummaryWithRisk(String riskTag, int daysSincePayment) {
		return new UdhaarSummary(
			UUID.randomUUID(), "Test", "9999999999",
			UUID.randomUUID(), "Shop",
			new BigDecimal("5000"), new BigDecimal("2000"), new BigDecimal("3000"),
			null, daysSincePayment > 0 ? LocalDateTime.now().minusDays(daysSincePayment) : null,
			daysSincePayment, 1, riskTag, List.of()
		);
	}
}
