package com.partlinq.core.repository;

import com.partlinq.core.model.entity.InventoryItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for InventoryItem entities.
 * Provides query methods for inventory management and stock tracking.
 */
@Repository
public interface InventoryItemRepository extends JpaRepository<InventoryItem, UUID> {

	/**
	 * Find an inventory item by shop and spare part
	 */
	Optional<InventoryItem> findByShopIdAndSparePartId(UUID shopId, UUID sparePartId);

	/**
	 * Find all available inventory items for a shop
	 */
	List<InventoryItem> findByShopIdAndIsAvailableTrue(UUID shopId);

	/**
	 * Find all inventory items in a shop sorted by part name
	 */
	@Query("SELECT i FROM InventoryItem i WHERE i.shop.id = :shopId ORDER BY i.sparePart.name ASC")
	List<InventoryItem> findByShopIdSorted(@Param("shopId") UUID shopId);

	/**
	 * Find inventory items with quantity greater than zero
	 */
	List<InventoryItem> findBySparePartIdAndQuantityGreaterThan(UUID sparePartId, Integer minQuantity);

	/**
	 * Find low stock items (quantity below minimum level) in a shop
	 */
	@Query("SELECT i FROM InventoryItem i WHERE i.shop.id = :shopId AND i.quantity < i.minStockLevel")
	List<InventoryItem> findByShopIdAndQuantityLessThanMinStockLevel(@Param("shopId") UUID shopId);

	/**
	 * Find all inventory items across shops for a specific spare part
	 */
	List<InventoryItem> findBySparePartId(UUID sparePartId);

	/**
	 * Find all available inventory items across all shops for a specific spare part
	 */
	@Query("SELECT i FROM InventoryItem i WHERE i.sparePart.id = :sparePartId AND i.isAvailable = true " +
		   "AND i.quantity > 0 ORDER BY i.sellingPrice ASC")
	List<InventoryItem> findAvailableBySparePartIdSortedByPrice(@Param("sparePartId") UUID sparePartId);

	/**
	 * Find inventory items that need restocking
	 */
	@Query("SELECT i FROM InventoryItem i WHERE i.quantity <= i.minStockLevel AND i.isAvailable = true ORDER BY i.shop.shopName ASC")
	List<InventoryItem> findLowStockItems();

	/**
	 * Count total inventory items in a shop
	 */
	Long countByShopId(UUID shopId);

	/**
	 * Delete inventory by shop and spare part (for cleanup)
	 */
	void deleteByShopIdAndSparePartId(UUID shopId, UUID sparePartId);

}
