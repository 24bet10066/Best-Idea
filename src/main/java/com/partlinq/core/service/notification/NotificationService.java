package com.partlinq.core.service.notification;

import com.partlinq.core.model.dto.UdhaarSummary;
import com.partlinq.core.model.entity.Notification;
import com.partlinq.core.model.entity.Order;
import com.partlinq.core.model.entity.PartsShop;
import com.partlinq.core.model.entity.Technician;
import com.partlinq.core.model.enums.NotificationType;
import com.partlinq.core.repository.NotificationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Notification service — generates and queues notifications.
 *
 * Architecture decision: Notifications are QUEUED, not sent immediately.
 * A separate scheduled job picks up unsent notifications and delivers them.
 * This decouples business logic from delivery infrastructure.
 *
 * Why: WhatsApp Business API has rate limits. SMS costs money.
 * Batching and retry logic belong in the delivery layer, not here.
 *
 * Current delivery: WhatsApp Business API (to be integrated).
 * Fallback: SMS via provider like MSG91 or Twilio.
 * Future: In-app push notifications when mobile app launches.
 */
@Service
@Slf4j
@Transactional
@RequiredArgsConstructor
public class NotificationService {

	private final NotificationRepository notificationRepository;

	/**
	 * Queue a payment reminder notification for a technician.
	 * Called by the PaymentReminderScheduler.
	 */
	public Notification queuePaymentReminder(Technician tech, PartsShop shop,
											  BigDecimal balance, int daysSinceLastPayment) {
		String title = "Payment Reminder — " + shop.getShopName();
		String body = buildPaymentReminderMessage(tech, shop, balance, daysSinceLastPayment);

		Notification notif = Notification.builder()
			.notificationType(NotificationType.PAYMENT_REMINDER)
			.recipientPhone(tech.getPhone())
			.recipientName(tech.getFullName())
			.technician(tech)
			.shop(shop)
			.title(title)
			.messageBody(body)
			.channel("WHATSAPP")
			.build();

		Notification saved = notificationRepository.save(notif);
		log.info("Payment reminder queued for {} (₹{} owed to {})",
			tech.getFullName(), balance, shop.getShopName());
		return saved;
	}

	/**
	 * Queue an order confirmation notification for a technician.
	 */
	public Notification queueOrderPlaced(Order order) {
		String title = "Order Confirmed — " + order.getOrderNumber();
		String body = "*Order Placed*\n" +
			"Order: " + order.getOrderNumber() + "\n" +
			"Shop: " + order.getShop().getShopName() + "\n" +
			"Amount: ₹" + order.getTotalAmount().toPlainString() + "\n" +
			(order.getCreditUsed() ? "_On credit (udhaar)_\n" : "_Paid_\n") +
			"\nThank you for using PartLinQ.";

		Notification notif = Notification.builder()
			.notificationType(NotificationType.ORDER_PLACED)
			.recipientPhone(order.getTechnician().getPhone())
			.recipientName(order.getTechnician().getFullName())
			.technician(order.getTechnician())
			.shop(order.getShop())
			.title(title)
			.messageBody(body)
			.channel("WHATSAPP")
			.build();

		return notificationRepository.save(notif);
	}

	/**
	 * Queue an "order ready" notification for a technician.
	 */
	public Notification queueOrderReady(Order order) {
		String title = "Order Ready — " + order.getOrderNumber();
		String body = "*Your order is ready for pickup!*\n" +
			"Order: " + order.getOrderNumber() + "\n" +
			"Shop: " + order.getShop().getShopName() + "\n" +
			"Address: " + order.getShop().getAddress() + "\n" +
			"\nPlease visit to collect your parts.";

		Notification notif = Notification.builder()
			.notificationType(NotificationType.ORDER_READY)
			.recipientPhone(order.getTechnician().getPhone())
			.recipientName(order.getTechnician().getFullName())
			.technician(order.getTechnician())
			.shop(order.getShop())
			.title(title)
			.messageBody(body)
			.channel("WHATSAPP")
			.build();

		return notificationRepository.save(notif);
	}

	/**
	 * Queue a low stock alert for the shop owner.
	 */
	public Notification queueLowStockAlert(PartsShop shop, String partName, int currentQty, int minLevel) {
		String title = "Low Stock Alert — " + partName;
		String body = "*Low Stock Warning*\n" +
			"Part: " + partName + "\n" +
			"Current: " + currentQty + " units\n" +
			"Minimum: " + minLevel + " units\n" +
			"\nPlease reorder soon.";

		Notification notif = Notification.builder()
			.notificationType(NotificationType.LOW_STOCK_ALERT)
			.recipientPhone(shop.getPhone())
			.recipientName(shop.getOwnerName())
			.shop(shop)
			.title(title)
			.messageBody(body)
			.channel("WHATSAPP")
			.build();

		return notificationRepository.save(notif);
	}

	/**
	 * Queue a weekly udhaar summary for the shop owner.
	 */
	public Notification queueWeeklyUdhaarSummary(PartsShop shop, List<UdhaarSummary> summaries,
												  BigDecimal totalOutstanding) {
		String title = "Weekly Udhaar Summary — " + shop.getShopName();

		StringBuilder body = new StringBuilder();
		body.append("*Weekly Udhaar Report*\n");
		body.append("Shop: ").append(shop.getShopName()).append("\n");
		body.append("Total Outstanding: ₹").append(totalOutstanding.toPlainString()).append("\n");
		body.append("─────────────────\n");

		int count = 0;
		for (UdhaarSummary s : summaries) {
			if (count >= 10) {
				body.append("\n_+ ").append(summaries.size() - 10).append(" more..._\n");
				break;
			}
			body.append(s.technicianName()).append(": ₹").append(s.currentBalance().toPlainString());
			if (s.daysSinceLastPayment() > 0) {
				body.append(" (").append(s.daysSinceLastPayment()).append(" days)");
			}
			body.append("\n");
			count++;
		}

		Notification notif = Notification.builder()
			.notificationType(NotificationType.WEEKLY_UDHAAR_SUMMARY)
			.recipientPhone(shop.getPhone())
			.recipientName(shop.getOwnerName())
			.shop(shop)
			.title(title)
			.messageBody(body.toString())
			.channel("WHATSAPP")
			.build();

		return notificationRepository.save(notif);
	}

	/**
	 * Get all unsent notifications for delivery.
	 * Called by the delivery job.
	 */
	public List<Notification> getUnsentNotifications() {
		return notificationRepository.findByIsSentFalseOrderByCreatedAtAsc();
	}

	/**
	 * Mark a notification as sent.
	 */
	public void markSent(UUID notificationId) {
		notificationRepository.findById(notificationId).ifPresent(notif -> {
			notif.setIsSent(true);
			notif.setSentAt(java.time.LocalDateTime.now());
			notificationRepository.save(notif);
		});
	}

	/**
	 * Mark delivery failure with error.
	 */
	public void markFailed(UUID notificationId, String errorMessage) {
		notificationRepository.findById(notificationId).ifPresent(notif -> {
			notif.setRetryCount(notif.getRetryCount() + 1);
			notif.setErrorMessage(errorMessage);
			notificationRepository.save(notif);
		});
	}

	/**
	 * Get unread notification count for a phone number.
	 */
	public Long getUnreadCount(String phone) {
		return notificationRepository.countByRecipientPhoneAndIsReadFalse(phone);
	}

	// ---- Private helpers ----

	private String buildPaymentReminderMessage(Technician tech, PartsShop shop,
											   BigDecimal balance, int daysSinceLastPayment) {
		StringBuilder sb = new StringBuilder();
		sb.append("Namaste ").append(tech.getFullName()).append(" ji,\n\n");
		sb.append("This is a friendly reminder from *").append(shop.getShopName()).append("*.\n\n");
		sb.append("Your current outstanding balance is *₹").append(balance.toPlainString()).append("*.\n");

		if (daysSinceLastPayment > 30) {
			sb.append("It has been ").append(daysSinceLastPayment).append(" days since your last payment.\n");
			sb.append("Kindly settle at your earliest convenience.\n");
		} else if (daysSinceLastPayment > 15) {
			sb.append("Last payment was ").append(daysSinceLastPayment).append(" days ago.\n");
			sb.append("A payment would be appreciated when you visit next.\n");
		} else {
			sb.append("Please settle when convenient.\n");
		}

		sb.append("\nPay via UPI: ").append(shop.getPhone()).append("@upi\n");
		sb.append("Or visit: ").append(shop.getAddress()).append("\n");
		sb.append("\nThank you for your business!\n");
		sb.append("— ").append(shop.getOwnerName());
		return sb.toString();
	}
}
