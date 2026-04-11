package com.partlinq.core.model.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * PartsShop entity representing a parts supplier shop in the PartLinQ ecosystem.
 * Shops maintain inventory of spare parts and fulfill orders from technicians.
 */
@Entity
@Table(name = "parts_shops", uniqueConstraints = {
	@UniqueConstraint(columnNames = "email"),
	@UniqueConstraint(columnNames = "phone"),
	@UniqueConstraint(columnNames = "gstNumber")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"inventoryItems", "orders"})
public class PartsShop {

	/**
	 * Unique identifier for the shop
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * Name of the shop
	 */
	@Column(nullable = false, length = 255)
	private String shopName;

	/**
	 * Name of the shop owner
	 */
	@Column(nullable = false, length = 255)
	private String ownerName;

	/**
	 * Contact phone number for the shop
	 */
	@Column(nullable = false, length = 20)
	private String phone;

	/**
	 * Email address of the shop
	 */
	@Column(nullable = false, length = 255)
	private String email;

	/**
	 * Full address of the shop
	 */
	@Column(nullable = false, length = 500)
	private String address;

	/**
	 * City where the shop is located
	 */
	@Column(nullable = false, length = 100)
	private String city;

	/**
	 * PIN code of the shop's location
	 */
	@Column(nullable = false, length = 10)
	private String pincode;

	/**
	 * GST (Goods and Services Tax) registration number
	 */
	@Column(nullable = false, length = 50)
	private String gstNumber;

	/**
	 * Whether the shop's registration is verified
	 */
	@Column(nullable = false)
	@Builder.Default
	private Boolean isVerified = false;

	/**
	 * Rating of the shop (0-5)
	 */
	@Column(nullable = false)
	@Builder.Default
	private Double rating = 0.0;

	/**
	 * Total number of orders served by the shop
	 */
	@Column(nullable = false)
	@Builder.Default
	private Integer totalOrdersServed = 0;

	/**
	 * When the shop was registered in the system
	 */
	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime registeredAt;

	/**
	 * Inventory items stocked by this shop
	 */
	@OneToMany(mappedBy = "shop", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<InventoryItem> inventoryItems = new HashSet<>();

	/**
	 * Orders received by this shop
	 */
	@OneToMany(mappedBy = "shop", fetch = FetchType.LAZY, cascade = CascadeType.PERSIST)
	private Set<Order> orders = new HashSet<>();

}
