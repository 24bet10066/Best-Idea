package com.partlinq.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * IdempotencyRecord — caches the response of a mutating endpoint by Idempotency-Key.
 *
 * Real problem this solves:
 *   Shop owner taps "Save Payment" on a flaky 3G connection. Spinner spins. He
 *   taps again. The second request hits the API and a duplicate ledger entry
 *   gets created. The technician's balance is now wrong by ₹2,000. Trust gone.
 *
 * Mechanism:
 *   Client sends Idempotency-Key header (UUID generated at form time).
 *   Server checks this table.
 *     - Hit       → return cached response, do nothing
 *     - Miss      → process request, store (key, response) for 24h
 *   Stripe and PayPal use this exact pattern — the simplest reliable defense.
 *
 * Scope: keyed by (idempotency_key, endpoint) — same key on /payments and /orders
 * is safe; only same key + same endpoint replays the cached response.
 */
@Entity
@Table(name = "idempotency_record",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_idempotency_key_endpoint",
		columnNames = {"idempotency_key", "endpoint"}
	),
	indexes = @Index(name = "idx_idempotency_created", columnList = "created_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class IdempotencyRecord {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/** Client-supplied UUID. Should be regenerated per logical action, not per retry. */
	@Column(name = "idempotency_key", nullable = false, length = 80)
	private String idempotencyKey;

	/** Endpoint path — scopes the key so /payments and /orders don't collide. */
	@Column(name = "endpoint", nullable = false, length = 80)
	private String endpoint;

	/** HTTP status of the original response (so retries replay the same code). */
	@Column(name = "response_status", nullable = false)
	private Integer responseStatus;

	/** Serialized JSON response body. Stored as TEXT — payloads are small (<2KB). */
	@Lob
	@Column(name = "response_body", columnDefinition = "TEXT")
	private String responseBody;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
