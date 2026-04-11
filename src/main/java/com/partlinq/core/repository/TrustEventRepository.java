package com.partlinq.core.repository;

import com.partlinq.core.model.entity.TrustEvent;
import com.partlinq.core.model.enums.TrustEventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * JPA Repository for TrustEvent entities.
 * Provides query methods for audit logging and trust score history.
 */
@Repository
public interface TrustEventRepository extends JpaRepository<TrustEvent, UUID> {

	/**
	 * Find all trust events for a technician, ordered by most recent first
	 */
	List<TrustEvent> findByTechnicianIdOrderByCreatedAtDesc(UUID technicianId);

	/**
	 * Find trust events of a specific type for a technician
	 */
	List<TrustEvent> findByTechnicianIdAndEventTypeOrderByCreatedAtDesc(UUID technicianId, TrustEventType eventType);

	/**
	 * Find recent trust events for a technician (last N days)
	 */
	@Query("SELECT te FROM TrustEvent te WHERE te.technician.id = :technicianId " +
		   "AND te.createdAt >= :startDate ORDER BY te.createdAt DESC")
	List<TrustEvent> findRecentEventsByTechnicianId(
		@Param("technicianId") UUID technicianId,
		@Param("startDate") LocalDateTime startDate
	);

	/**
	 * Find trust events within a date range
	 */
	List<TrustEvent> findByTechnicianIdAndCreatedAtBetweenOrderByCreatedAtDesc(
		UUID technicianId,
		LocalDateTime startDate,
		LocalDateTime endDate
	);

	/**
	 * Count events of a specific type for a technician
	 */
	Long countByTechnicianIdAndEventType(UUID technicianId, TrustEventType eventType);

	/**
	 * Get total score delta for a technician over a period
	 */
	@Query("SELECT SUM(te.scoreDelta) FROM TrustEvent te WHERE te.technician.id = :technicianId " +
		   "AND te.createdAt BETWEEN :startDate AND :endDate")
	Double getTotalScoreDeltaInPeriod(
		@Param("technicianId") UUID technicianId,
		@Param("startDate") LocalDateTime startDate,
		@Param("endDate") LocalDateTime endDate
	);

	/**
	 * Get the latest trust score from events
	 */
	@Query("SELECT te.newScore FROM TrustEvent te WHERE te.technician.id = :technicianId " +
		   "ORDER BY te.createdAt DESC LIMIT 1")
	Double getLatestTrustScore(@Param("technicianId") UUID technicianId);

	/**
	 * Find fraud flag events
	 */
	@Query("SELECT te FROM TrustEvent te WHERE te.technician.id = :technicianId " +
		   "AND te.eventType = com.partlinq.core.model.enums.TrustEventType.FRAUD_FLAG " +
		   "ORDER BY te.createdAt DESC")
	List<TrustEvent> findFraudFlagEvents(@Param("technicianId") UUID technicianId);

	/**
	 * Count total fraud flag events for a technician
	 */
	@Query("SELECT COUNT(te) FROM TrustEvent te WHERE te.technician.id = :technicianId " +
		   "AND te.eventType = com.partlinq.core.model.enums.TrustEventType.FRAUD_FLAG")
	Long countFraudFlagEvents(@Param("technicianId") UUID technicianId);

}
