package com.partlinq.core.model.enums;

/**
 * Enumeration of order statuses in the PartLinQ system.
 * Represents the lifecycle states of an order.
 */
public enum OrderStatus {
	/** Order has been placed by technician */
	PLACED,
	/** Order has been confirmed by shop */
	CONFIRMED,
	/** Order items are ready for pickup */
	READY,
	/** Order has been picked up by technician */
	PICKED_UP,
	/** Order has been completed */
	COMPLETED,
	/** Order has been cancelled */
	CANCELLED,
	/** Order is awaiting payment */
	PAYMENT_PENDING,
	/** Payment for order is overdue */
	PAYMENT_OVERDUE
}
