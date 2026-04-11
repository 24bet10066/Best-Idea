package com.partlinq.core.repository;

import com.partlinq.core.model.entity.CustomerFeedback;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * JPA Repository for CustomerFeedback entities.
 * Provides query methods for managing customer feedback and ratings.
 */
@Repository
public interface CustomerFeedbackRepository extends JpaRepository<CustomerFeedback, UUID> {

	/**
	 * Find all feedback for a technician, ordered by most recent first
	 */
	List<CustomerFeedback> findByTechnicianIdOrderByCreatedAtDesc(UUID technicianId);

	/**
	 * Find verified feedback only
	 */
	List<CustomerFeedback> findByTechnicianIdAndIsVerifiedTrue(UUID technicianId);

	/**
	 * Find feedback with a minimum rating for a technician
	 */
	List<CustomerFeedback> findByTechnicianIdAndRatingGreaterThanEqualOrderByCreatedAtDesc(UUID technicianId, Integer minRating);

	/**
	 * Find feedback with a specific rating
	 */
	List<CustomerFeedback> findByTechnicianIdAndRating(UUID technicianId, Integer rating);

	/**
	 * Get average rating for a technician
	 */
	@Query("SELECT AVG(cf.rating) FROM CustomerFeedback cf WHERE cf.technician.id = :technicianId")
	Double getAverageRatingByTechnicianId(@Param("technicianId") UUID technicianId);

	/**
	 * Get average rating for verified feedback only
	 */
	@Query("SELECT AVG(cf.rating) FROM CustomerFeedback cf WHERE cf.technician.id = :technicianId AND cf.isVerified = true")
	Double getAverageVerifiedRatingByTechnicianId(@Param("technicianId") UUID technicianId);

	/**
	 * Count feedback for a technician
	 */
	Long countByTechnicianId(UUID technicianId);

	/**
	 * Count verified feedback for a technician
	 */
	Long countByTechnicianIdAndIsVerifiedTrue(UUID technicianId);

	/**
	 * Get count of positive feedback (rating >= 4)
	 */
	@Query("SELECT COUNT(cf) FROM CustomerFeedback cf WHERE cf.technician.id = :technicianId AND cf.rating >= 4")
	Long countPositiveFeedback(@Param("technicianId") UUID technicianId);

	/**
	 * Get count of negative feedback (rating < 3)
	 */
	@Query("SELECT COUNT(cf) FROM CustomerFeedback cf WHERE cf.technician.id = :technicianId AND cf.rating < 3")
	Long countNegativeFeedback(@Param("technicianId") UUID technicianId);

}
