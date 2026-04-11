package com.partlinq.core.model.dto;

import com.partlinq.core.model.enums.TrustEventType;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * DTO for trust score response data.
 * Contains current trust score, rank, and recent events.
 */
public record TrustScoreResponse(
	UUID technicianId,
	String technicianName,
	Double currentScore,
	Integer rank,
	Long totalEndorsements,
	Double averageEndorsementWeight,
	Long positiveReviews,
	Long negativeReviews,
	List<TrustEventResponse> recentEvents
) implements Serializable {

	/**
	 * Nested record for trust events in the response
	 */
	public record TrustEventResponse(
		UUID eventId,
		TrustEventType eventType,
		Double scoreDelta,
		Double previousScore,
		Double newScore,
		String reason,
		LocalDateTime createdAt
	) implements Serializable {}
}
