package com.partlinq.core.service.udhaar;

import com.partlinq.core.exception.EntityNotFoundException;
import com.partlinq.core.model.dto.PaymentRequest;
import com.partlinq.core.model.dto.UdhaarSummary;
import com.partlinq.core.model.entity.*;
import com.partlinq.core.model.enums.LedgerEntryType;
import com.partlinq.core.model.enums.TrustEventType;
import com.partlinq.core.repository.*;
import com.partlinq.core.service.audit.AuditService;
import com.partlinq.core.service.credit.CreditService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Udhaar (credit/debt) management service.
 *
 * Ground reality of Indian spare parts shops:
 * - Technicians pick parts on credit ("udhaar pe de do bhaiya")
 * - Payment happens later — same day, next visit, weekly, or monthly
 * - Shop owner tracks this mentally or in a diary
 * - This service DIGITIZES that diary
 *
 * Key operations:
 * 1. Record credit when order is placed on udhaar
 * 2. Record payment when technician pays back
 * 3. Show balance summary per technician
 * 4. Show all outstanding balances for shop owner's daily view
 * 5. Flag overdue accounts for payment reminders
 */
@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class UdhaarService {

	private final PaymentLedgerRepository ledgerRepository;
	private final TechnicianRepository technicianRepository;
	private final PartsShopRepository shopRepository;
	private final OrderRepository orderRepository;
	private final TrustEventRepository trustEventRepository;
	private final CreditService creditService;
	private final AuditService auditService;

	/** Days without payment before account is flagged as overdue */
	private static final int OVERDUE_THRESHOLD_DAYS = 30;
	/** Days without payment that triggers "at risk" warning */
	private static final int AT_RISK_THRESHOLD_DAYS = 15;

	/**
	 * Record a credit entry when a technician takes parts on udhaar.
	 * Called automatically when an order is placed with credit.
	 *
	 * @param technicianId UUID of the technician
	 * @param shopId UUID of the shop
	 * @param orderId UUID of the order
	 * @param amount Credit amount
	 * @return Updated balance after this credit
	 */
	public BigDecimal recordCredit(UUID technicianId, UUID shopId, UUID orderId, BigDecimal amount) {
		log.info("Recording credit: tech={}, shop={}, order={}, amount={}",
			technicianId, shopId, orderId, amount);

		Technician tech = technicianRepository.findById(technicianId)
			.orElseThrow(() -> new EntityNotFoundException("Technician", technicianId));
		PartsShop shop = shopRepository.findById(shopId)
			.orElseThrow(() -> new EntityNotFoundException("Shop", shopId));
		Order order = orderRepository.findById(orderId)
			.orElseThrow(() -> new EntityNotFoundException("Order", orderId));

		BigDecimal currentBalance = getCurrentBalance(technicianId, shopId);
		BigDecimal newBalance = currentBalance.add(amount);

		PaymentLedger entry = PaymentLedger.builder()
			.technician(tech)
			.shop(shop)
			.order(order)
			.entryType(LedgerEntryType.CREDIT)
			.amount(amount)
			.balanceAfter(newBalance)
			.notes("Order " + order.getOrderNumber() + " on credit")
			.recordedBy("system")
			.build();

		ledgerRepository.save(entry);

		log.info("Credit recorded. Tech {} now owes {} to shop {}", tech.getFullName(), newBalance, shop.getShopName());
		return newBalance;
	}

	/**
	 * Record a payment from a technician.
	 * This is the shop owner's main action: "Raju paid ₹2,000 in cash."
	 *
	 * @param request PaymentRequest with amount, mode, and reference
	 * @return Updated UdhaarSummary after payment
	 */
	public UdhaarSummary recordPayment(PaymentRequest request) {
		log.info("Recording payment: tech={}, shop={}, amount={}, mode={}",
			request.technicianId(), request.shopId(), request.amount(), request.paymentMode());

		Technician tech = technicianRepository.findById(request.technicianId())
			.orElseThrow(() -> new EntityNotFoundException("Technician", request.technicianId()));
		PartsShop shop = shopRepository.findById(request.shopId())
			.orElseThrow(() -> new EntityNotFoundException("Shop", request.shopId()));

		BigDecimal currentBalance = getCurrentBalance(request.technicianId(), request.shopId());

		if (request.amount().compareTo(currentBalance) > 0) {
			log.warn("Payment {} exceeds balance {} for tech {}",
				request.amount(), currentBalance, tech.getFullName());
			// Allow overpayment — creates a credit balance (advance payment)
		}

		BigDecimal newBalance = currentBalance.subtract(request.amount());

		PaymentLedger entry = PaymentLedger.builder()
			.technician(tech)
			.shop(shop)
			.entryType(LedgerEntryType.PAYMENT)
			.amount(request.amount())
			.balanceAfter(newBalance)
			.paymentMode(request.paymentMode())
			.referenceNumber(request.referenceNumber())
			.notes(request.notes() != null ? request.notes() : "Payment via " + request.paymentMode())
			.recordedBy(request.recordedBy() != null ? request.recordedBy() : shop.getOwnerName())
			.build();

		ledgerRepository.save(entry);

		// Record trust event for on-time payment behavior
		recordPaymentTrustEvent(tech, request.amount(), currentBalance);

		// Update credit service
		int daysSinceLastCredit = getDaysSinceLastCredit(request.technicianId(), request.shopId());
		creditService.recordPayment(request.technicianId(), null, daysSinceLastCredit);

		log.info("Payment recorded. Tech {} balance now {} at shop {}",
			tech.getFullName(), newBalance, shop.getShopName());

		auditService.record(
			request.recordedBy() != null ? request.recordedBy() : shop.getOwnerName(),
			AuditService.Action.PAYMENT_RECORDED,
			AuditService.Subject.LEDGER,
			entry.getId(),
			shop.getId(),
			String.format("tech=%s, amount=%s, mode=%s, balance %s -> %s, ref=%s",
				tech.getFullName(), request.amount().toPlainString(),
				request.paymentMode(), currentBalance.toPlainString(),
				newBalance.toPlainString(),
				request.referenceNumber() != null ? request.referenceNumber() : "-")
		);

		return getUdhaarSummary(request.technicianId(), request.shopId());
	}

	/**
	 * Record a manual adjustment (discount, write-off, correction).
	 *
	 * @param technicianId UUID of the technician
	 * @param shopId UUID of the shop
	 * @param amount Adjustment amount (positive = increase balance, negative = decrease)
	 * @param notes Reason for adjustment
	 * @param recordedBy Who made the adjustment
	 * @return Updated balance
	 */
	public BigDecimal recordAdjustment(UUID technicianId, UUID shopId, BigDecimal amount,
									   String notes, String recordedBy) {
		if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
			throw new IllegalArgumentException("Adjustment amount cannot be zero");
		}

		log.info("Recording adjustment: tech={}, shop={}, amount={}", technicianId, shopId, amount);

		Technician tech = technicianRepository.findById(technicianId)
			.orElseThrow(() -> new EntityNotFoundException("Technician", technicianId));
		PartsShop shop = shopRepository.findById(shopId)
			.orElseThrow(() -> new EntityNotFoundException("Shop", shopId));

		BigDecimal currentBalance = getCurrentBalance(technicianId, shopId);
		BigDecimal newBalance = currentBalance.add(amount);

		PaymentLedger entry = PaymentLedger.builder()
			.technician(tech)
			.shop(shop)
			.entryType(LedgerEntryType.ADJUSTMENT)
			.amount(amount.abs())
			.balanceAfter(newBalance)
			.notes(notes != null ? notes : "Manual adjustment")
			.recordedBy(recordedBy != null ? recordedBy : shop.getOwnerName())
			.build();

		PaymentLedger savedEntry = ledgerRepository.save(entry);

		auditService.record(
			recordedBy != null ? recordedBy : shop.getOwnerName(),
			AuditService.Action.ADJUSTMENT_MADE,
			AuditService.Subject.LEDGER,
			savedEntry.getId(),
			shop.getId(),
			String.format("tech=%s, amount=%s, balance %s -> %s, reason=%s",
				tech.getFullName(), amount.toPlainString(),
				currentBalance.toPlainString(), newBalance.toPlainString(),
				notes != null ? notes : "-")
		);

		return newBalance;
	}

	/**
	 * Get the udhaar summary for a technician at a specific shop.
	 * This is the "account statement" view.
	 */
	@Transactional(readOnly = true)
	public UdhaarSummary getUdhaarSummary(UUID technicianId, UUID shopId) {
		Technician tech = technicianRepository.findById(technicianId)
			.orElseThrow(() -> new EntityNotFoundException("Technician", technicianId));
		PartsShop shop = shopRepository.findById(shopId)
			.orElseThrow(() -> new EntityNotFoundException("Shop", shopId));

		BigDecimal totalCredit = ledgerRepository.getTotalCreditByTechnicianAndShop(technicianId, shopId);
		BigDecimal totalPaid = ledgerRepository.getTotalPaymentByTechnicianAndShop(technicianId, shopId);
		BigDecimal currentBalance = getCurrentBalance(technicianId, shopId);

		Optional<LocalDateTime> lastPaymentDate = ledgerRepository.getLastPaymentDate(technicianId, shopId);
		int daysSinceLastPayment = lastPaymentDate
			.map(date -> (int) ChronoUnit.DAYS.between(date, LocalDateTime.now()))
			.orElse(-1); // -1 means never paid

		// Get last credit date
		List<PaymentLedger> entries = ledgerRepository
			.findByTechnicianIdAndShopIdOrderByCreatedAtDesc(technicianId, shopId);

		LocalDateTime lastCreditDate = entries.stream()
			.filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
			.map(PaymentLedger::getCreatedAt)
			.findFirst()
			.orElse(null);

		// Count unpaid orders
		int totalUnpaidOrders = (int) orderRepository
			.findUnpaidOrdersByTechnicianId(technicianId).stream()
			.filter(o -> o.getShop().getId().equals(shopId))
			.count();

		// Risk tag based on payment behavior
		String riskTag = computeRiskTag(currentBalance, daysSinceLastPayment);

		// Recent entries (last 20)
		List<UdhaarSummary.LedgerEntry> recentEntries = entries.stream()
			.limit(20)
			.map(this::mapToLedgerEntry)
			.collect(Collectors.toList());

		return new UdhaarSummary(
			technicianId,
			tech.getFullName(),
			tech.getPhone(),
			shopId,
			shop.getShopName(),
			totalCredit,
			totalPaid,
			currentBalance,
			lastCreditDate,
			lastPaymentDate.orElse(null),
			daysSinceLastPayment,
			totalUnpaidOrders,
			riskTag,
			recentEntries
		);
	}

	/**
	 * Get all outstanding balances for a shop.
	 * This is the shop owner's "morning view": who owes me money?
	 */
	@Transactional(readOnly = true)
	public List<UdhaarSummary> getOutstandingForShop(UUID shopId) {
		shopRepository.findById(shopId)
			.orElseThrow(() -> new EntityNotFoundException("Shop", shopId));

		List<UUID> techIdsWithBalance = ledgerRepository.findTechnicianIdsWithOutstandingBalance(shopId);

		return techIdsWithBalance.stream()
			.map(techId -> getUdhaarSummary(techId, shopId))
			.filter(summary -> summary.currentBalance().compareTo(BigDecimal.ZERO) > 0)
			.sorted(Comparator.comparing(UdhaarSummary::currentBalance).reversed())
			.collect(Collectors.toList());
	}

	/**
	 * Get overdue accounts for a shop (balance > 0 and no payment in 30+ days).
	 * Used for payment reminder scheduling.
	 */
	@Transactional(readOnly = true)
	public List<UdhaarSummary> getOverdueForShop(UUID shopId) {
		return getOutstandingForShop(shopId).stream()
			.filter(s -> "OVERDUE".equals(s.riskTag()) || "AT_RISK".equals(s.riskTag()))
			.collect(Collectors.toList());
	}

	/**
	 * Get the current balance a technician owes to a shop.
	 */
	@Transactional(readOnly = true)
	public BigDecimal getCurrentBalance(UUID technicianId, UUID shopId) {
		return ledgerRepository
			.findFirstByTechnicianIdAndShopIdOrderByCreatedAtDesc(technicianId, shopId)
			.map(PaymentLedger::getBalanceAfter)
			.orElse(BigDecimal.ZERO);
	}

	/**
	 * Get complete ledger history for a technician at a shop.
	 */
	@Transactional(readOnly = true)
	public List<UdhaarSummary.LedgerEntry> getLedgerHistory(UUID technicianId, UUID shopId) {
		return ledgerRepository
			.findByTechnicianIdAndShopIdOrderByCreatedAtDesc(technicianId, shopId)
			.stream()
			.map(this::mapToLedgerEntry)
			.collect(Collectors.toList());
	}

	// ---- Private helpers ----

	private BigDecimal getCurrentBalanceInternal(UUID technicianId, UUID shopId) {
		return ledgerRepository
			.findFirstByTechnicianIdAndShopIdOrderByCreatedAtDesc(technicianId, shopId)
			.map(PaymentLedger::getBalanceAfter)
			.orElse(BigDecimal.ZERO);
	}

	private int getDaysSinceLastCredit(UUID technicianId, UUID shopId) {
		List<PaymentLedger> entries = ledgerRepository
			.findByTechnicianIdAndShopIdOrderByCreatedAtDesc(technicianId, shopId);

		return entries.stream()
			.filter(e -> e.getEntryType() == LedgerEntryType.CREDIT)
			.map(PaymentLedger::getCreatedAt)
			.findFirst()
			.map(date -> (int) ChronoUnit.DAYS.between(date, LocalDateTime.now()))
			.orElse(0);
	}

	/**
	 * Compute risk tag based on balance and payment recency.
	 * Simple, transparent logic — no black-box ML.
	 */
	private String computeRiskTag(BigDecimal balance, int daysSinceLastPayment) {
		if (balance.compareTo(BigDecimal.ZERO) <= 0) {
			return "CLEAR";
		}
		if (daysSinceLastPayment == -1) {
			// Never paid — check balance age
			return "NEW_CREDIT";
		}
		if (daysSinceLastPayment > OVERDUE_THRESHOLD_DAYS) {
			return "OVERDUE";
		}
		if (daysSinceLastPayment > AT_RISK_THRESHOLD_DAYS) {
			return "AT_RISK";
		}
		return "NORMAL";
	}

	/**
	 * Record a trust event when payment is received.
	 * Paying early or paying a large portion boosts trust.
	 */
	private void recordPaymentTrustEvent(Technician tech, BigDecimal paymentAmount, BigDecimal balanceBefore) {
		double currentScore = tech.getTrustScore();
		double delta;

		// Payment ratio: what fraction of balance was paid?
		double paymentRatio = balanceBefore.compareTo(BigDecimal.ZERO) > 0
			? paymentAmount.doubleValue() / balanceBefore.doubleValue()
			: 1.0;

		if (paymentRatio >= 1.0) {
			delta = 3.0; // Full settlement — strong positive signal
		} else if (paymentRatio >= 0.5) {
			delta = 2.0; // Paying half or more — good
		} else {
			delta = 1.0; // Partial payment — acknowledges debt
		}

		double newScore = Math.max(0.0, Math.min(100.0, currentScore + delta));

		TrustEvent event = TrustEvent.builder()
			.technician(tech)
			.eventType(TrustEventType.PAYMENT_ON_TIME)
			.scoreDelta(delta)
			.previousScore(currentScore)
			.newScore(newScore)
			.reason("Payment of ₹" + paymentAmount.toPlainString() +
				" (" + String.format("%.0f%%", paymentRatio * 100) + " of balance)")
			.createdAt(LocalDateTime.now())
			.build();

		trustEventRepository.save(event);

		tech.setTrustScore(newScore);
		technicianRepository.save(tech);
	}

	private UdhaarSummary.LedgerEntry mapToLedgerEntry(PaymentLedger entry) {
		return new UdhaarSummary.LedgerEntry(
			entry.getId(),
			entry.getEntryType().name(),
			entry.getAmount(),
			entry.getBalanceAfter(),
			entry.getPaymentMode() != null ? entry.getPaymentMode().name() : null,
			entry.getReferenceNumber(),
			entry.getNotes(),
			entry.getRecordedBy(),
			entry.getCreatedAt(),
			entry.getOrder() != null ? entry.getOrder().getId() : null,
			entry.getOrder() != null ? entry.getOrder().getOrderNumber() : null
		);
	}
}
