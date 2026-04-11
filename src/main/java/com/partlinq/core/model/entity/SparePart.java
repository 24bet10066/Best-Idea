package com.partlinq.core.model.entity;

import com.partlinq.core.model.enums.ApplianceType;
import com.partlinq.core.model.enums.PartCategory;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * SparePart entity representing a spare part that can be purchased through PartLinQ.
 * Each part has a part number, category, appliance type, and pricing information.
 */
@Entity
@Table(name = "spare_parts", uniqueConstraints = {
	@UniqueConstraint(columnNames = "partNumber")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"inventoryItems", "orderItems"})
public class SparePart {

	/**
	 * Unique identifier for the spare part
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * Unique part number for identification
	 */
	@Column(nullable = false, length = 100)
	private String partNumber;

	/**
	 * Name of the spare part
	 */
	@Column(nullable = false, length = 255)
	private String name;

	/**
	 * Description of the spare part
	 */
	@Column(length = 1000)
	private String description;

	/**
	 * Category of the spare part
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private PartCategory category;

	/**
	 * Type of appliance this part is compatible with
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private ApplianceType applianceType;

	/**
	 * Brand of the spare part
	 */
	@Column(nullable = false, length = 100)
	private String brand;

	/**
	 * Comma-separated list of compatible appliance models
	 */
	@Column(length = 1000)
	private String modelCompatibility;

	/**
	 * Maximum Retail Price (MRP) of the part
	 */
	@Column(nullable = false)
	private BigDecimal mrp;

	/**
	 * Whether this is an original equipment manufacturer (OEM) part
	 */
	@Column(nullable = false)
	@Builder.Default
	private Boolean isOem = true;

	/**
	 * When the part was first added to the catalog
	 */
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/**
	 * Inventory records for this part across different shops
	 */
	@OneToMany(mappedBy = "sparePart", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<InventoryItem> inventoryItems = new HashSet<>();

	/**
	 * Order items containing this spare part
	 */
	@OneToMany(mappedBy = "sparePart", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<OrderItem> orderItems = new HashSet<>();

}
