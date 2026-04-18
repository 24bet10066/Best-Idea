package com.partlinq.core.service.notification;

import com.partlinq.core.model.dto.UdhaarSummary;
import com.partlinq.core.model.entity.PartsShop;
import com.partlinq.core.model.entity.ReminderLog;
import com.partlinq.core.model.entity.Technician;
import com.partlinq.core.repository.PartsShopRepository;
import com.partlinq.core.repository.ReminderLogRepository;
import com.partlinq.core.repository.TechnicianRepository;
import com.partlinq.core.service.udhaar.UdhaarService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * Scheduled job for payment reminders.
 *
 * Runs daily at 10 AM IST (4:30 AM UTC).
 * Why 10 AM: Technicians are awake but not yet at a job site.
 * They check WhatsApp in the morning. Polite reminder, not annoying.
 *
 * Logic:
 * 1. For each shop, find all technicians with outstanding balance
 * 2. If balance > 0 and last payment > 15 days ago → queue reminder
 * 3. If balance > 0 and last payment > 30 days ago → queue urgent reminder
 * 4. On Mondays → also send weekly summary to shop owner
 *
 * Rate limit: Max 1 reminder per technician per shop per 7 days.
 * We don't spam. Spamming destroys trust — the opposite of what we want.
 *
 * Dedup persistence:
 * The ReminderLog table survives restarts. A deploy mid-day does not cause
 * a second reminder to the same person.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PaymentReminderScheduler {

	private final UdhaarService udhaarService;
	private final NotificationService notificationService;
	private final PartsShopRepository shopRepository;
	private final TechnicianRepository technicianRepository;
	private final ReminderLogRepository reminderLogRepository;

	/** Minimum days between reminders to the same person */
	private static final int MIN_DAYS_BETWEEN_REMINDERS = 7;

	/**
	 * Daily payment reminder check.
	 * Runs at 10:00 AM IST (04:30 UTC) every day.
	 */
	@Scheduled(cron = "0 30 4 * * *") // 4:30 AM UTC = 10:00 AM IST
	@Transactional
	public void checkAndSendPaymentReminders() {
		log.info("Starting daily payment reminder check");

		List<PartsShop> shops = shopRepository.findAll();
		int remindersQueued = 0;
		int remindersSkipped = 0;

		for (PartsShop shop : shops) {
			List<UdhaarSummary> overdue = udhaarService.getOverdueForShop(shop.getId());

			for (UdhaarSummary summary : overdue) {
				if (summary.currentBalance().compareTo(BigDecimal.ZERO) <= 0
					|| summary.daysSinceLastPayment() <= 15) {
					continue;
				}

				if (!shouldSendReminder(summary.technicianId(), shop.getId())) {
					remindersSkipped++;
					continue;
				}

				Technician tech = technicianRepository.findById(summary.technicianId())
					.orElse(null);

				if (tech == null) continue;

				notificationService.queuePaymentReminder(
					tech, shop, summary.currentBalance(), summary.daysSinceLastPayment());

				recordReminderSent(tech, shop, summary.currentBalance());
				remindersQueued++;
			}
		}

		log.info("Payment reminder check complete. Queued: {}, skipped (cooldown): {}",
			remindersQueued, remindersSkipped);
	}

	/**
	 * Weekly udhaar summary for shop owners.
	 * Runs every Monday at 8:00 AM IST (02:30 UTC).
	 */
	@Scheduled(cron = "0 30 2 * * MON") // 2:30 AM UTC = 8:00 AM IST on Mondays
	@Transactional
	public void sendWeeklyUdhaarSummary() {
		log.info("Generating weekly udhaar summaries for all shops");

		List<PartsShop> shops = shopRepository.findAll();

		for (PartsShop shop : shops) {
			List<UdhaarSummary> outstanding = udhaarService.getOutstandingForShop(shop.getId());

			if (!outstanding.isEmpty()) {
				BigDecimal totalOutstanding = outstanding.stream()
					.map(UdhaarSummary::currentBalance)
					.reduce(BigDecimal.ZERO, BigDecimal::add);

				notificationService.queueWeeklyUdhaarSummary(shop, outstanding, totalOutstanding);

				log.info("Weekly summary queued for shop {}: {} technicians, \u20B9{} outstanding",
					shop.getShopName(), outstanding.size(), totalOutstanding);
			}
		}

		log.info("Weekly udhaar summary generation complete");
	}

	/**
	 * Dedup check — has this (tech, shop) pair been reminded in the last 7 days?
	 * Persisted in reminder_log table so it survives restarts.
	 */
	private boolean shouldSendReminder(java.util.UUID technicianId, java.util.UUID shopId) {
		Optional<ReminderLog> existing = reminderLogRepository
			.findByTechnicianIdAndShopId(technicianId, shopId);

		if (existing.isEmpty()) return true;

		long daysSince = ChronoUnit.DAYS.between(
			existing.get().getLastRemindedAt(), LocalDateTime.now());

		return daysSince >= MIN_DAYS_BETWEEN_REMINDERS;
	}

	/**
	 * Upsert the reminder log row for this pair.
	 */
	private void recordReminderSent(Technician tech, PartsShop shop, BigDecimal balance) {
		ReminderLog row = reminderLogRepository
			.findByTechnicianIdAndShopId(tech.getId(), shop.getId())
			.orElseGet(() -> ReminderLog.builder()
				.technician(tech)
				.shop(shop)
				.remindersSent(0)
				.build());

		row.setLastRemindedAt(LocalDateTime.now());
		row.setLastReminderBalance(balance);
		row.setRemindersSent(row.getRemindersSent() + 1);
		reminderLogRepository.save(row);
	}
}
