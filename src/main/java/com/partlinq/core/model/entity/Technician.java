package com.partlinq.core.model.entity;

import com.partlinq.core.model.enums.ApplianceType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * Technician entity representing a service technician in the PartLinQ ecosystem.
 * Technicians have trust scores, credit limits, and specializations in various appliance types.
 */
@Entity
@Table(name = "technicians", uniqueConstraints = {
	@UniqueConstraint(columnNames = "email"),
	@UniqueConstraint(columnNames = "phone")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"orders", "endorsementsGiven", "endorsementsReceived", "feedbackReceived", "trustEvents", "specializations"})
public class Technician {

	/**
	 * Unique identifier for the technician
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * Full name of the technician
	 */
	@Column(nullable = false, length = 255)
	private String fullName;

	/**
	 * Phone number of the technician
	 */
	@Column(nullable = false, length = 20)
	private String phone;

	/**
	 * Email address of the technician
	 */
	@Column(nullable = false, length = 255)
	private String email;

	/**
	 * City where the technician operates
	 */
	@Column(nullable = false, length = 100)
	private String city;

	/**
	 * PIN code of the technician's location
	 */
	@Column(nullable = false, length = 10)
	private String pincode;

	/**
	 * Set of appliance types this technician specializes in
	 */
	@ElementCollection(fetch = FetchType.LAZY)
	@CollectionTable(name = "technician_specializations", joinColumns = @JoinColumn(name = "technician_id"))
	@Column(name = "appliance_type")
	@Enumerated(EnumType.STRING)
	@Builder.Default
	private Set<ApplianceType> specializations = new HashSet<>();

	/**
	 * Trust score of the technician (0-100)
	 */
	@Column(nullable = false)
	@Builder.Default
	private Double trustScore = 50.0;

	/**
	 * Credit limit in the platform
	 */
	@Column(nullable = false)
	private BigDecimal creditLimit;

	/**
	 * Total number of transactions completed
	 */
	@Column(nullable = false)
	@Builder.Default
	private Integer totalTransactions = 0;

	/**
	 * Average payment days from invoice
	 */
	@Column(nullable = false)
	@Builder.Default
	private Double avgPaymentDays = 0.0;

	/**
	 * When the technician was registered
	 */
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime registeredAt;

	/**
	 * When the technician was last active
	 */
	@UpdateTimestamp
	private LocalDateTime lastActiveAt;

	/**
	 * Whether the technician's identity is verified
	 */
	@Column(nullable = false)
	@Builder.Default
	private Boolean isVerified = false;

	/**
	 * URL to the technician's profile image
	 */
	@Column(length = 500)
	private String profileImageUrl;

	/**
	 * ID of the technician who referred this technician (if any)
	 */
	@Column
	private UUID referredById;

	/**
	 * Orders created by this technician
	 */
	@OneToMany(mappedBy = "technician", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<Order> orders = new HashSet<>();

	/**
	 * Endorsements given by this technician
	 */
	@OneToMany(mappedBy = "endorser", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<TrustEndorsement> endorsementsGiven = new HashSet<>();

	/**
	 * Endorsements received by this technician
	 */
	@OneToMany(mappedBy = "endorsee", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<TrustEndorsement> endorsementsReceived = new HashSet<>();

	/**
	 * Feedback received from customers
	 */
	@OneToMany(mappedBy = "technician", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<CustomerFeedback> feedbackReceived = new HashSet<>();

	/**
	 * Trust events for this technician
	 */
	@OneToMany(mappedBy = "technician", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<TrustEvent> trustEvents = new HashSet<>();

}
