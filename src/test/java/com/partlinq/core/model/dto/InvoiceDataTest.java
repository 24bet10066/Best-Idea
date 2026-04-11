package com.partlinq.core.model.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for InvoiceData record and WhatsApp text generation.
 * The WhatsApp text is the most critical output — it must be correct,
 * readable, and WhatsApp-formatted (*bold*, _italic_).
 */
class InvoiceDataTest {

	@Test
	@DisplayName("WhatsApp text includes shop name, invoice number, and total")
	void testWhatsAppTextBasicStructure() {
		InvoiceData invoice = createSampleInvoice(false, null);
		String text = invoice.toWhatsAppText();

		assertTrue(text.contains("*Sharma Parts*"), "Should contain shop name in bold");
		assertTrue(text.contains("INV-20260410-12345"), "Should contain invoice number");
		assertTrue(text.contains("₹2500"), "Should contain total amount");
		assertTrue(text.contains("PLQ-20260410-12345"), "Should contain order number");
	}

	@Test
	@DisplayName("WhatsApp text shows credit info when order is on udhaar")
	void testWhatsAppTextWithCredit() {
		InvoiceData invoice = createSampleInvoice(true, new BigDecimal("5000"));
		String text = invoice.toWhatsAppText();

		assertTrue(text.contains("udhaar"), "Should mention udhaar");
		assertTrue(text.contains("₹5000"), "Should show outstanding balance");
		assertTrue(text.contains("Due by"), "Should show due date");
	}

	@Test
	@DisplayName("WhatsApp text shows paid status for cash orders")
	void testWhatsAppTextPaid() {
		InvoiceData invoice = createSampleInvoice(false, null);
		String text = invoice.toWhatsAppText();

		assertTrue(text.contains("_Paid_"), "Should show paid status");
		assertFalse(text.contains("udhaar"), "Should not mention udhaar");
	}

	@Test
	@DisplayName("WhatsApp text includes all line items with quantities and prices")
	void testWhatsAppTextLineItems() {
		InvoiceData invoice = createSampleInvoice(false, null);
		String text = invoice.toWhatsAppText();

		assertTrue(text.contains("Compressor"), "Should contain part name");
		assertTrue(text.contains("Voltas"), "Should contain brand");
		assertTrue(text.contains("2 x ₹1250"), "Should show quantity x price");
	}

	@Test
	@DisplayName("WhatsApp text includes technician details")
	void testWhatsAppTextTechnicianInfo() {
		InvoiceData invoice = createSampleInvoice(false, null);
		String text = invoice.toWhatsAppText();

		assertTrue(text.contains("Raju Kumar"), "Should contain technician name");
		assertTrue(text.contains("9876543210"), "Should contain technician phone");
	}

	@Test
	@DisplayName("WhatsApp text includes GST number when present")
	void testWhatsAppTextGst() {
		InvoiceData invoice = createSampleInvoice(false, null);
		String text = invoice.toWhatsAppText();

		assertTrue(text.contains("GST: 09ABCDE1234F1Z5"), "Should contain GST number");
	}

	@Test
	@DisplayName("WhatsApp text ends with PartLinQ branding")
	void testWhatsAppTextBranding() {
		InvoiceData invoice = createSampleInvoice(false, null);
		String text = invoice.toWhatsAppText();

		assertTrue(text.contains("PartLinQ"), "Should contain PartLinQ branding");
	}

	@Test
	@DisplayName("InvoiceData record is immutable")
	void testInvoiceDataImmutability() {
		InvoiceData invoice = createSampleInvoice(false, null);

		assertNotNull(invoice.invoiceNumber());
		assertNotNull(invoice.items());
		assertEquals(1, invoice.items().size());
		assertEquals("INV-20260410-12345", invoice.invoiceNumber());
	}

	@Test
	@DisplayName("InvoiceLineItem record holds correct data")
	void testInvoiceLineItem() {
		var item = new InvoiceData.InvoiceLineItem(
			1, "VLT-CMP-001", "Compressor", "Voltas",
			2, new BigDecimal("1250"), new BigDecimal("2500")
		);

		assertEquals(1, item.serialNumber());
		assertEquals("VLT-CMP-001", item.partNumber());
		assertEquals("Compressor", item.partName());
		assertEquals("Voltas", item.brand());
		assertEquals(2, item.quantity());
		assertEquals(new BigDecimal("1250"), item.unitPrice());
		assertEquals(new BigDecimal("2500"), item.lineTotal());
	}

	// ---- Helpers ----

	private InvoiceData createSampleInvoice(boolean onCredit, BigDecimal outstandingBalance) {
		List<InvoiceData.InvoiceLineItem> items = List.of(
			new InvoiceData.InvoiceLineItem(
				1, "VLT-CMP-001", "Compressor", "Voltas",
				2, new BigDecimal("1250"), new BigDecimal("2500")
			)
		);

		return new InvoiceData(
			"INV-20260410-12345",
			LocalDateTime.of(2026, 4, 10, 10, 30),
			"PLQ-20260410-12345",
			UUID.randomUUID(),
			"Sharma Parts",
			"123 Market Road",
			"Lucknow",
			"9988776655",
			"09ABCDE1234F1Z5",
			"Raju Kumar",
			"9876543210",
			"Lucknow",
			items,
			new BigDecimal("2119"), // subtotal (excl GST)
			new BigDecimal("381"),  // GST
			new BigDecimal("2500"), // total
			onCredit,
			outstandingBalance,
			onCredit ? LocalDateTime.of(2026, 5, 10, 10, 30) : null,
			null
		);
	}
}
