package com.partlinq.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * ReminderLog tracks when we last sent a reminder to a (technician, shop) pair.
 *
 * Why persisted, not in-memory:
 *   The PaymentReminderScheduler's dedup needs to survive restarts. An in-memory
 *   map loses state on every deploy. With two deploys in one day, a shop owner
 *   could get the same reminder twice — straight path to the "turn off notifications"
 *   button. Trust-destroyer.
 *
 * Why (technician, shop) and not just technician:
 *   A technician may hold udhaar at multiple shops. Each relationship has its own
 *   reminder cadence. Dedup must scope to the pair.
 *
 * Schema keeps it small:
 *   - lastRemindedAt — when we last sent any reminder to this pair
 *   - lastReminderBalance — balance at the time (audit only, not for logic)
 *   - remindersSent — running count, useful for escalation logic later
 */
@Entity
@Table(name = "reminder_log",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_reminder_tech_shop",
		columnNames = {"technician_id", "shop_id"}
	),
	indexes = @Index(name = "idx_reminder_last_sent", columnList = "last_reminded_at"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"technician", "shop"})
public class ReminderLog {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "technician_id", nullable = false)
	private Technician technician;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shop_id", nullable = false)
	private PartsShop shop;

	@Column(name = "last_reminded_at", nullable = false)
	private LocalDateTime lastRemindedAt;

	@Column(name = "last_reminder_balance", precision = 12, scale = 2)
	private java.math.BigDecimal lastReminderBalance;

	@Column(name = "reminders_sent", nullable = false)
	@Builder.Default
	private Integer remindersSent = 0;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
