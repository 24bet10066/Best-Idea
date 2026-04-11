package com.partlinq.core.repository;

import com.partlinq.core.model.entity.PartsShop;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for PartsShop entities.
 * Provides query methods for searching and filtering parts shops.
 */
@Repository
public interface PartsShopRepository extends JpaRepository<PartsShop, UUID> {

	/**
	 * Find a shop by email address
	 */
	Optional<PartsShop> findByEmail(String email);

	/**
	 * Find a shop by phone number
	 */
	Optional<PartsShop> findByPhone(String phone);

	/**
	 * Find a shop by GST number
	 */
	Optional<PartsShop> findByGstNumber(String gstNumber);

	/**
	 * Find all shops in a specific city
	 */
	List<PartsShop> findByCity(String city);

	/**
	 * Find all verified shops in a city, ordered by rating
	 */
	@Query("SELECT s FROM PartsShop s WHERE s.city = :city AND s.isVerified = true ORDER BY s.rating DESC")
	List<PartsShop> findVerifiedShopsByCity(@Param("city") String city);

	/**
	 * Find all shops in a specific PIN code area
	 */
	List<PartsShop> findByPincode(String pincode);

	/**
	 * Find all verified shops in a PIN code area, ordered by rating
	 */
	@Query("SELECT s FROM PartsShop s WHERE s.pincode = :pincode AND s.isVerified = true ORDER BY s.rating DESC")
	List<PartsShop> findVerifiedShopsByPincode(@Param("pincode") String pincode);

	/**
	 * Find shops by partial name match (case-insensitive)
	 */
	List<PartsShop> findByShopNameContainingIgnoreCase(String shopName);

	/**
	 * Count total shops by verification status
	 */
	Long countByIsVerified(Boolean isVerified);

}
