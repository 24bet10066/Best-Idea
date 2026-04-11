package com.partlinq.core.repository;

import com.partlinq.core.model.entity.TrustEndorsement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for TrustEndorsement entities.
 * Provides query methods for managing trust endorsements between technicians.
 */
@Repository
public interface TrustEndorsementRepository extends JpaRepository<TrustEndorsement, UUID> {

	/**
	 * Find all active endorsements received by a technician
	 */
	List<TrustEndorsement> findByEndorseeIdAndIsActiveTrue(UUID endorseeId);

	/**
	 * Find all endorsements received by a technician (active and inactive)
	 */
	List<TrustEndorsement> findByEndorseeId(UUID endorseeId);

	/**
	 * Find all endorsements given by a technician
	 */
	List<TrustEndorsement> findByEndorserId(UUID endorserId);

	/**
	 * Find endorsement between two specific technicians (if exists)
	 */
	Optional<TrustEndorsement> findByEndorserIdAndEndorseeId(UUID endorserId, UUID endorseeId);

	/**
	 * Count active endorsements received by a technician
	 */
	Long countByEndorseeIdAndIsActiveTrue(UUID endorseeId);

	/**
	 * Count total endorsements received by a technician
	 */
	Long countByEndorseeId(UUID endorseeId);

	/**
	 * Count endorsements given by a technician
	 */
	Long countByEndorserId(UUID endorserId);

	/**
	 * Get average endorsement weight for a technician
	 */
	@Query("SELECT AVG(e.weight) FROM TrustEndorsement e WHERE e.endorsee.id = :endorseeId AND e.isActive = true")
	Double getAverageEndorsementWeightForTechnician(@Param("endorseeId") UUID endorseeId);

	/**
	 * Find high-weight endorsements (weight >= 0.7) received by a technician
	 */
	@Query("SELECT e FROM TrustEndorsement e WHERE e.endorsee.id = :endorseeId AND e.weight >= 0.7 AND e.isActive = true")
	List<TrustEndorsement> findHighWeightEndorsements(@Param("endorseeId") UUID endorseeId);

	/**
	 * Get weighted endorsement sum for a technician
	 */
	@Query("SELECT SUM(e.weight) FROM TrustEndorsement e WHERE e.endorsee.id = :endorseeId AND e.isActive = true")
	Double getTotalEndorsementWeight(@Param("endorseeId") UUID endorseeId);

}
