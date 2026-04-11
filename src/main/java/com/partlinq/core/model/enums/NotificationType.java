package com.partlinq.core.model.enums;

/**
 * Types of notifications the system can generate.
 * Each type maps to a specific template and delivery channel.
 */
public enum NotificationType {
	/** Payment reminder for overdue udhaar */
	PAYMENT_REMINDER,
	/** Order placed confirmation */
	ORDER_PLACED,
	/** Order ready for pickup */
	ORDER_READY,
	/** Order completed acknowledgment */
	ORDER_COMPLETED,
	/** Low stock alert for shop owner */
	LOW_STOCK_ALERT,
	/** Credit limit updated */
	CREDIT_LIMIT_CHANGE,
	/** Trust score milestone reached */
	TRUST_MILESTONE,
	/** Weekly udhaar summary for shop owner */
	WEEKLY_UDHAAR_SUMMARY
}
