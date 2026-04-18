package com.partlinq.core.controller;

import com.partlinq.core.model.dto.PaymentRequest;
import com.partlinq.core.model.dto.UdhaarSummary;
import com.partlinq.core.security.ShopAccessGuard;
import com.partlinq.core.service.idempotency.IdempotencyService;
import com.partlinq.core.service.udhaar.UdhaarService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for Udhaar (credit/debt) management.
 *
 * This is the MOST USED controller in the system.
 * Shop owners will open this screen multiple times a day to:
 * 1. Check who owes them money
 * 2. Record payments received
 * 3. View a specific technician's account
 *
 * Every endpoint is designed for ONE-TAP actions on mobile.
 */
@RestController
@RequestMapping("/v1/udhaar")
@RequiredArgsConstructor
@Tag(name = "Udhaar (Credit/Debt)", description = "Track udhaar, record payments, view balances")
public class UdhaarController {

	private final UdhaarService udhaarService;
	private final IdempotencyService idempotencyService;
	private final ShopAccessGuard shopAccessGuard;

	/**
	 * Get udhaar summary for a technician at a specific shop.
	 * Primary view: "Raju owes ₹3,200. Last paid 12 days ago."
	 */
	@GetMapping("/summary")
	@Operation(summary = "Get udhaar summary for technician-shop pair")
	public ResponseEntity<UdhaarSummary> getUdhaarSummary(
		@RequestParam UUID technicianId,
		@RequestParam UUID shopId
	) {
		shopAccessGuard.assertCanAccess(shopId);
		UdhaarSummary summary = udhaarService.getUdhaarSummary(technicianId, shopId);
		return ResponseEntity.ok(summary);
	}

	/**
	 * Get ALL outstanding balances for a shop.
	 * Shop owner's morning view: "Who owes me money?"
	 * Sorted by amount owed (highest first).
	 */
	@GetMapping("/shop/{shopId}/outstanding")
	@Operation(summary = "Get all outstanding balances for a shop — the daily udhaar board")
	public ResponseEntity<OutstandingResponse> getOutstandingForShop(@PathVariable UUID shopId) {
		shopAccessGuard.assertCanAccess(shopId);
		List<UdhaarSummary> summaries = udhaarService.getOutstandingForShop(shopId);

		BigDecimal totalOutstanding = summaries.stream()
			.map(UdhaarSummary::currentBalance)
			.reduce(BigDecimal.ZERO, BigDecimal::add);

		int overdueCount = (int) summaries.stream()
			.filter(s -> "OVERDUE".equals(s.riskTag()) || "AT_RISK".equals(s.riskTag()))
			.count();

		return ResponseEntity.ok(new OutstandingResponse(
			shopId,
			summaries.size(),
			totalOutstanding,
			overdueCount,
			summaries
		));
	}

	/**
	 * Record a payment from a technician.
	 * The shop owner's primary action: "Raju just paid ₹2,000 cash."
	 *
	 * Send an Idempotency-Key header (UUID, generated when the form opens).
	 * Retries with the same key return the same response — no duplicate ledger entry.
	 */
	@PostMapping("/payment")
	@Operation(summary = "Record a payment from a technician")
	public ResponseEntity<UdhaarSummary> recordPayment(
		@Valid @RequestBody PaymentRequest request,
		@Parameter(description = "Optional UUID to make retries safe")
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
	) {
		shopAccessGuard.assertCanAccess(request.shopId());
		return idempotencyService.executeOrReplay(
			idempotencyKey,
			"/v1/udhaar/payment",
			UdhaarSummary.class,
			() -> udhaarService.recordPayment(request)
		);
	}

	/**
	 * Record a manual adjustment (discount, write-off, correction).
	 * Idempotent via Idempotency-Key header — same reason as payment.
	 */
	@PostMapping("/adjustment")
	@Operation(summary = "Record a manual adjustment to a technician's balance")
	public ResponseEntity<AdjustmentResponse> recordAdjustment(
		@Valid @RequestBody AdjustmentRequest request,
		@Parameter(description = "Optional UUID to make retries safe")
		@RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey
	) {
		shopAccessGuard.assertCanAccess(request.shopId());
		return idempotencyService.executeOrReplay(
			idempotencyKey,
			"/v1/udhaar/adjustment",
			AdjustmentResponse.class,
			() -> {
				BigDecimal newBalance = udhaarService.recordAdjustment(
					request.technicianId(), request.shopId(),
					request.amount(), request.notes(), request.recordedBy());
				return new AdjustmentResponse(
					request.technicianId(), request.shopId(), newBalance, request.notes());
			}
		);
	}

	/**
	 * Get full ledger history for a technician at a shop.
	 * Detailed transaction-by-transaction view.
	 */
	@GetMapping("/history")
	@Operation(summary = "Get complete ledger history for a technician-shop pair")
	public ResponseEntity<List<UdhaarSummary.LedgerEntry>> getLedgerHistory(
		@RequestParam UUID technicianId,
		@RequestParam UUID shopId
	) {
		shopAccessGuard.assertCanAccess(shopId);
		List<UdhaarSummary.LedgerEntry> history = udhaarService.getLedgerHistory(technicianId, shopId);
		return ResponseEntity.ok(history);
	}

	/**
	 * Get current balance for a technician at a shop.
	 * Quick check: "How much does Raju owe?"
	 */
	@GetMapping("/balance")
	@Operation(summary = "Quick balance check for a technician at a shop")
	public ResponseEntity<BalanceResponse> getBalance(
		@RequestParam UUID technicianId,
		@RequestParam UUID shopId
	) {
		shopAccessGuard.assertCanAccess(shopId);
		BigDecimal balance = udhaarService.getCurrentBalance(technicianId, shopId);
		return ResponseEntity.ok(new BalanceResponse(technicianId, shopId, balance));
	}

	/**
	 * Get overdue accounts for a shop (for payment reminder view).
	 */
	@GetMapping("/shop/{shopId}/overdue")
	@Operation(summary = "Get overdue accounts — technicians who haven't paid in 15+ days")
	public ResponseEntity<List<UdhaarSummary>> getOverdueForShop(@PathVariable UUID shopId) {
		shopAccessGuard.assertCanAccess(shopId);
		List<UdhaarSummary> overdue = udhaarService.getOverdueForShop(shopId);
		return ResponseEntity.ok(overdue);
	}

	// ---- Response DTOs ----

	public record OutstandingResponse(
		UUID shopId,
		int totalTechnicians,
		BigDecimal totalOutstanding,
		int overdueCount,
		List<UdhaarSummary> accounts
	) {}

	public record BalanceResponse(
		UUID technicianId,
		UUID shopId,
		BigDecimal currentBalance
	) {}

	public record AdjustmentRequest(
		@jakarta.validation.constraints.NotNull(message = "Technician ID is required")
		UUID technicianId,
		@jakarta.validation.constraints.NotNull(message = "Shop ID is required")
		UUID shopId,
		@jakarta.validation.constraints.NotNull(message = "Adjustment amount is required")
		BigDecimal amount,
		@jakarta.validation.constraints.NotBlank(message = "Adjustment reason/notes are required")
		String notes,
		String recordedBy
	) {}

	public record AdjustmentResponse(
		UUID technicianId,
		UUID shopId,
		BigDecimal newBalance,
		String notes
	) {}
}
