package com.partlinq.core.model.enums;

/**
 * Enumeration of trust event types.
 * Represents different types of events that affect a technician's trust score.
 */
public enum TrustEventType {
	/** Event triggered when an order is successfully completed */
	ORDER_COMPLETED,
	/** Event triggered when payment is received on time */
	PAYMENT_ON_TIME,
	/** Event triggered when payment is received late */
	PAYMENT_LATE,
	/** Event triggered when a technician receives an endorsement */
	ENDORSEMENT_RECEIVED,
	/** Event triggered when a technician gives an endorsement */
	ENDORSEMENT_GIVEN,
	/** Event triggered by customer feedback submission */
	CUSTOMER_FEEDBACK,
	/** Event triggered by inactivity decay calculation */
	INACTIVITY_DECAY,
	/** Event triggered when fraudulent behavior is flagged */
	FRAUD_FLAG,
	/** Event triggered by manual adjustment */
	MANUAL_ADJUSTMENT
}
