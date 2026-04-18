package com.partlinq.core.repository;

import com.partlinq.core.model.entity.IdempotencyRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface IdempotencyRecordRepository extends JpaRepository<IdempotencyRecord, UUID> {

	Optional<IdempotencyRecord> findByIdempotencyKeyAndEndpoint(String key, String endpoint);

	@Modifying
	@Query("DELETE FROM IdempotencyRecord r WHERE r.createdAt < :cutoff")
	int deleteOlderThan(@Param("cutoff") LocalDateTime cutoff);
}
