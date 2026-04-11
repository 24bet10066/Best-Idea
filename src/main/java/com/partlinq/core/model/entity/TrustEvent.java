package com.partlinq.core.model.entity;

import com.partlinq.core.model.enums.TrustEventType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TrustEvent entity representing an event that impacts a technician's trust score.
 * Serves as an audit log for all trust score changes and their reasons.
 */
@Entity
@Table(name = "trust_events", indexes = {
	@Index(name = "idx_technician_created", columnList = "technician_id,created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"technician"})
public class TrustEvent {

	/**
	 * Unique identifier for the event
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * The technician affected by this event
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "technician_id", nullable = false)
	private Technician technician;

	/**
	 * Type of event that occurred
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private TrustEventType eventType;

	/**
	 * Delta applied to the trust score
	 */
	@Column(nullable = false)
	private Double scoreDelta;

	/**
	 * Trust score before this event
	 */
	@Column(nullable = false)
	private Double previousScore;

	/**
	 * Trust score after this event
	 */
	@Column(nullable = false)
	private Double newScore;

	/**
	 * Description of the reason for this event
	 */
	@Column(nullable = false, length = 500)
	private String reason;

	/**
	 * When this event was recorded
	 */
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

}
