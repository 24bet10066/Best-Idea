package com.partlinq.core.repository;

import com.partlinq.core.model.entity.Technician;
import com.partlinq.core.model.enums.ApplianceType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for Technician entities.
 * Provides query methods for searching and filtering technicians.
 */
@Repository
public interface TechnicianRepository extends JpaRepository<Technician, UUID> {

	/**
	 * Find a technician by phone number
	 */
	Optional<Technician> findByPhone(String phone);

	/**
	 * Find a technician by email address
	 */
	Optional<Technician> findByEmail(String email);

	/**
	 * Find all technicians operating in a specific city
	 */
	List<Technician> findByCity(String city);

	/**
	 * Find all technicians in a city with a minimum trust score
	 */
	@Query("SELECT t FROM Technician t WHERE t.city = :city AND t.trustScore >= :minScore ORDER BY t.trustScore DESC")
	List<Technician> findByCity(@Param("city") String city, @Param("minScore") Double minScore);

	/**
	 * Find technicians by PIN code
	 */
	List<Technician> findByPincode(String pincode);

	/**
	 * Find all technicians with a trust score greater than or equal to a threshold, ordered by score descending
	 */
	List<Technician> findByTrustScoreGreaterThanOrderByTrustScoreDesc(Double trustScore);

	/**
	 * Find all technicians specializing in a particular appliance type
	 */
	@Query("SELECT t FROM Technician t WHERE :applianceType MEMBER OF t.specializations ORDER BY t.trustScore DESC")
	List<Technician> findBySpecializationsContaining(@Param("applianceType") ApplianceType applianceType);

	/**
	 * Find verified technicians in a city with a minimum trust score, specializing in an appliance type
	 */
	@Query("SELECT t FROM Technician t WHERE t.city = :city AND t.isVerified = true AND t.trustScore >= :minScore " +
		   "AND :applianceType MEMBER OF t.specializations ORDER BY t.trustScore DESC")
	List<Technician> findByCityAndVerifiedAndSpecialization(
		@Param("city") String city,
		@Param("minScore") Double minScore,
		@Param("applianceType") ApplianceType applianceType
	);

	/**
	 * Count total technicians by verification status
	 */
	Long countByIsVerified(Boolean isVerified);

}
