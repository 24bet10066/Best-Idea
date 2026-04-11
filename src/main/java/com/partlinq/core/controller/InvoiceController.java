package com.partlinq.core.controller;

import com.partlinq.core.model.dto.InvoiceData;
import com.partlinq.core.service.invoice.InvoiceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST controller for invoice/receipt generation.
 *
 * Two primary use cases:
 * 1. Full invoice data (for PDF rendering on frontend or printing)
 * 2. WhatsApp text (shop owner copies and sends to technician)
 *
 * The WhatsApp text endpoint is the killer feature here.
 * Shop owners already communicate via WhatsApp.
 * This just makes it structured and professional.
 */
@RestController
@RequestMapping("/v1/invoices")
@RequiredArgsConstructor
@Tag(name = "Invoices", description = "Generate invoices and receipts for orders")
public class InvoiceController {

	private final InvoiceService invoiceService;

	/**
	 * Generate full invoice data for an order.
	 * Returns structured data that frontend can render as PDF or display.
	 */
	@GetMapping("/order/{orderId}")
	@Operation(summary = "Get full invoice data for an order")
	public ResponseEntity<InvoiceData> getInvoice(@PathVariable UUID orderId) {
		InvoiceData invoice = invoiceService.generateInvoice(orderId);
		return ResponseEntity.ok(invoice);
	}

	/**
	 * Generate WhatsApp-formatted text for an order invoice.
	 * Shop owner copies this and sends to technician via WhatsApp.
	 *
	 * Returns plain text with WhatsApp markdown formatting (*bold*, _italic_).
	 */
	@GetMapping("/order/{orderId}/whatsapp")
	@Operation(summary = "Get WhatsApp-formatted invoice text — copy and send")
	public ResponseEntity<WhatsAppInvoiceResponse> getWhatsAppInvoice(@PathVariable UUID orderId) {
		String text = invoiceService.generateWhatsAppInvoice(orderId);
		return ResponseEntity.ok(new WhatsAppInvoiceResponse(orderId, text));
	}

	public record WhatsAppInvoiceResponse(UUID orderId, String whatsappText) {}
}
