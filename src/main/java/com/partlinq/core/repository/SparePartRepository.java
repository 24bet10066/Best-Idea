package com.partlinq.core.repository;

import com.partlinq.core.model.entity.SparePart;
import com.partlinq.core.model.enums.ApplianceType;
import com.partlinq.core.model.enums.PartCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for SparePart entities.
 * Provides query methods for searching and filtering spare parts.
 */
@Repository
public interface SparePartRepository extends JpaRepository<SparePart, UUID> {

	/**
	 * Find a spare part by unique part number
	 */
	Optional<SparePart> findByPartNumber(String partNumber);

	/**
	 * Find spare parts by name (partial, case-insensitive)
	 */
	List<SparePart> findByNameContainingIgnoreCase(String name);

	/**
	 * Find spare parts by appliance type and category
	 */
	List<SparePart> findByApplianceTypeAndCategory(ApplianceType applianceType, PartCategory category);

	/**
	 * Find spare parts by appliance type
	 */
	List<SparePart> findByApplianceType(ApplianceType applianceType);

	/**
	 * Find spare parts by category
	 */
	List<SparePart> findByCategory(PartCategory category);

	/**
	 * Find spare parts by brand (case-insensitive)
	 */
	List<SparePart> findByBrandIgnoreCase(String brand);

	/**
	 * Find OEM (Original Equipment Manufacturer) parts only
	 */
	List<SparePart> findByIsOemTrue();

	/**
	 * Find non-OEM parts (aftermarket)
	 */
	List<SparePart> findByIsOemFalse();

	/**
	 * Search for parts by appliance type, category, and brand
	 */
	@Query("SELECT p FROM SparePart p WHERE p.applianceType = :applianceType " +
		   "AND p.category = :category AND LOWER(p.brand) = LOWER(:brand)")
	List<SparePart> searchParts(
		@Param("applianceType") ApplianceType applianceType,
		@Param("category") PartCategory category,
		@Param("brand") String brand
	);

}
