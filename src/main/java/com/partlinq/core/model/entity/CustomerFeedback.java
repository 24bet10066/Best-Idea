package com.partlinq.core.model.entity;

import com.partlinq.core.model.enums.ApplianceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * CustomerFeedback entity representing feedback provided by customers about a technician.
 * Feedback includes ratings and comments that influence trust score calculations.
 */
@Entity
@Table(name = "customer_feedbacks")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"technician"})
public class CustomerFeedback {

	/**
	 * Unique identifier for the feedback
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * The technician being reviewed
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "technician_id", nullable = false)
	private Technician technician;

	/**
	 * Name of the customer providing feedback
	 */
	@Column(nullable = false, length = 255)
	private String customerName;

	/**
	 * Contact phone number of the customer
	 */
	@Column(length = 20)
	private String customerPhone;

	/**
	 * Rating given by the customer (1-5 scale)
	 */
	@Column(nullable = false)
	private Integer rating;

	/**
	 * Detailed comment from the customer
	 */
	@Column(length = 1000)
	private String comment;

	/**
	 * Type of appliance the service was for
	 */
	@Column
	@Enumerated(EnumType.STRING)
	private ApplianceType serviceType;

	/**
	 * Whether the customer's identity is verified
	 */
	@Column(nullable = false)
	@Builder.Default
	private Boolean isVerified = false;

	/**
	 * When the feedback was submitted
	 */
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

}
