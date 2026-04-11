package com.partlinq.core.model.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * OrderItem entity representing a line item within an order.
 * Each order item specifies a quantity of a particular spare part at a given unit price.
 */
@Entity
@Table(name = "order_items")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"order", "sparePart"})
public class OrderItem {

	/**
	 * Unique identifier for the order item
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * Reference to the parent order
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "order_id", nullable = false)
	private Order order;

	/**
	 * Reference to the spare part being ordered
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "spare_part_id", nullable = false)
	private SparePart sparePart;

	/**
	 * Quantity of the spare part ordered
	 */
	@Column(nullable = false)
	private Integer quantity;

	/**
	 * Unit price of the spare part at the time of order
	 */
	@Column(nullable = false)
	private BigDecimal unitPrice;

	/**
	 * Total price for this line item (quantity * unitPrice)
	 */
	@Column(nullable = false)
	private BigDecimal lineTotal;

}
