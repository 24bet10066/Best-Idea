package com.partlinq.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * InventoryItem entity representing a spare part held in stock by a shop.
 * Tracks quantity, selling price, and availability status for each part-shop combination.
 */
@Entity
@Table(name = "inventory_items", uniqueConstraints = {
	@UniqueConstraint(columnNames = {"spare_part_id", "shop_id"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"sparePart", "shop"})
public class InventoryItem {

	/**
	 * Unique identifier for the inventory item
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * Reference to the spare part
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "spare_part_id", nullable = false)
	private SparePart sparePart;

	/**
	 * Reference to the shop stocking this part
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shop_id", nullable = false)
	private PartsShop shop;

	/**
	 * Current quantity in stock
	 */
	@Column(nullable = false)
	private Integer quantity;

	/**
	 * Selling price per unit at this shop
	 */
	@Column(nullable = false)
	private BigDecimal sellingPrice;

	/**
	 * Minimum stock level before restocking is needed
	 */
	@Column(nullable = false)
	private Integer minStockLevel;

	/**
	 * When the inventory was last restocked
	 */
	@UpdateTimestamp
	private LocalDateTime lastRestockedAt;

	/**
	 * Whether this item is available for purchase
	 */
	@Column(nullable = false)
	@Builder.Default
	private Boolean isAvailable = true;

	/**
	 * Version for optimistic locking
	 */
	@Version
	private Long version;

}
