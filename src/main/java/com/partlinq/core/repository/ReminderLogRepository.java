package com.partlinq.core.repository;

import com.partlinq.core.model.entity.ReminderLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface ReminderLogRepository extends JpaRepository<ReminderLog, UUID> {

	/**
	 * Fetch the single reminder-log row for a (technician, shop) pair.
	 * Unique constraint guarantees at-most-one.
	 */
	Optional<ReminderLog> findByTechnicianIdAndShopId(UUID technicianId, UUID shopId);
}
