package com.partlinq.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * TrustEndorsement entity representing an endorsement from one technician to another.
 * Endorsements contribute to the trust score calculation in the trust graph.
 */
@Entity
@Table(name = "trust_endorsements", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"endorser_id", "endorsee_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"endorser", "endorsee"})
public class TrustEndorsement {

	/**
	 * Unique identifier for the endorsement
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * The technician giving the endorsement
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "endorser_id", nullable = false)
	private Technician endorser;

	/**
	 * The technician receiving the endorsement
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "endorsee_id", nullable = false)
	private Technician endorsee;

	/**
	 * Weight of the endorsement (0-1 scale, higher = stronger endorsement)
	 */
	@Column(nullable = false)
	private Double weight;

	/**
	 * Optional message accompanying the endorsement
	 */
	@Column(length = 500)
	private String message;

	/**
	 * When the endorsement was created
	 */
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/**
	 * Whether the endorsement is currently active
	 */
	@Column(nullable = false)
	@Builder.Default
	private Boolean isActive = true;

}
