package com.partlinq.core.model.entity;

import com.partlinq.core.model.enums.OrderStatus;
import jakarta.persistence.*;
import lombok.*;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Order entity representing an order placed by a technician at a parts shop.
 * Tracks order status, total amount, payment details, and associated items.
 */
@Entity
@Table(name = "orders", uniqueConstraints = {
	@UniqueConstraint(columnNames = "orderNumber")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"orderItems", "technician", "shop"})
public class Order {

	/**
	 * Unique identifier for the order
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * Unique order number (auto-generated format: PLQ-YYYYMMDD-XXXXX)
	 */
	@Column(nullable = false, length = 50)
	private String orderNumber;

	/**
	 * The technician who placed the order
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "technician_id", nullable = false)
	private Technician technician;

	/**
	 * The shop fulfilling the order
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shop_id", nullable = false)
	private PartsShop shop;

	/**
	 * Current status of the order
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private OrderStatus status;

	/**
	 * Total amount of the order
	 */
	@Column(nullable = false)
	private BigDecimal totalAmount;

	/**
	 * Whether credit was used for this order
	 */
	@Column(nullable = false)
	@Builder.Default
	private Boolean creditUsed = false;

	/**
	 * Due date for payment
	 */
	@Column
	private LocalDateTime paymentDueDate;

	/**
	 * When payment was received (null if not yet paid)
	 */
	@Column
	private LocalDateTime paidAt;

	/**
	 * When the order was created
	 */
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/**
	 * When the order was last updated
	 */
	@UpdateTimestamp
	private LocalDateTime updatedAt;

	/**
	 * Additional notes on the order
	 */
	@Column(length = 1000)
	private String notes;

	/**
	 * Items in this order
	 */
	@OneToMany(mappedBy = "order", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<OrderItem> orderItems = new HashSet<>();

}
