package com.partlinq.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * AuditEvent — immutable record of every sensitive write.
 *
 * Why this exists:
 *   In a credit-tracking system, the most likely fraud vector is the shop owner
 *   himself or a trusted insider quietly editing balances. Adjustments and
 *   credit-limit changes have no business reason to be invisible. An append-only
 *   audit log is the single cheapest control that catches this.
 *
 * What we capture:
 *   - actor:   who did it (email or username from auth context, "system" otherwise)
 *   - action:  PAYMENT_RECORDED | ADJUSTMENT_MADE | CREDIT_LIMIT_CHANGED
 *              | ORDER_CANCELLED | TRUST_SCORE_OVERRIDE
 *   - subject: what entity was touched (technician, shop, order)
 *   - delta:   before → after, as plain text for grep-ability
 *   - context: free-form JSON for IP, user agent, etc. (optional)
 *
 * Schema rule: rows are NEVER updated or deleted. Append-only.
 * If an admin needs a correction, they create a new offsetting event.
 */
@Entity
@Table(name = "audit_event", indexes = {
	@Index(name = "idx_audit_actor", columnList = "actor"),
	@Index(name = "idx_audit_action", columnList = "action"),
	@Index(name = "idx_audit_subject", columnList = "subject_type,subject_id"),
	@Index(name = "idx_audit_created", columnList = "created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class AuditEvent {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/** Who performed the action — email, username, or "system". */
	@Column(name = "actor", nullable = false, length = 120)
	private String actor;

	/** Action type — keep as String for flexibility. Discrete vocabulary in AuditAction class. */
	@Column(name = "action", nullable = false, length = 60)
	private String action;

	/** Entity type that was acted on: TECHNICIAN, SHOP, ORDER, LEDGER, CREDIT_PROFILE. */
	@Column(name = "subject_type", nullable = false, length = 40)
	private String subjectType;

	@Column(name = "subject_id", nullable = false)
	private UUID subjectId;

	/** Optional shop scope for tenant filtering. Null for global ops. */
	@Column(name = "shop_id")
	private UUID shopId;

	/** Plain-text "before → after" or summary. Searchable, human-readable. */
	@Column(name = "delta", length = 1000)
	private String delta;

	/** Optional JSON for IP, UA, request-id, etc. */
	@Column(name = "context", length = 2000)
	private String context;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
