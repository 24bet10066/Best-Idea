package com.partlinq.core.service.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.partlinq.core.model.entity.IdempotencyRecord;
import com.partlinq.core.repository.IdempotencyRecordRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

/**
 * IdempotencyService — gate mutating endpoints with a reusable cache.
 *
 * Typical usage in a controller:
 * <pre>
 *   return idempotencyService.executeOrReplay(
 *       idempotencyKey,
 *       "/v1/udhaar/payment",
 *       PaymentResponse.class,
 *       () -> udhaarService.recordPayment(request)
 *   );
 * </pre>
 *
 * Contract:
 *   - key null/blank  → executes normally (idempotency is opt-in)
 *   - key seen before → returns cached HTTP status + body
 *   - key is new      → runs supplier, caches response, returns it
 *
 * Race condition: two concurrent requests with the same key both miss cache →
 * one wins the unique-constraint insert, the other catches DataIntegrityViolation
 * and replays the winner's stored response.
 *
 * Retention: 24h. Cleanup runs daily at 03:00 UTC.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

	private final IdempotencyRecordRepository repository;
	private final ObjectMapper objectMapper;

	/** How long an idempotency key stays honored. */
	private static final int RETENTION_HOURS = 24;

	/**
	 * Execute the supplier, caching the response. Replays if key was seen before.
	 *
	 * @param idempotencyKey  client-supplied header value (may be null/blank)
	 * @param endpoint        endpoint name that scopes the key
	 * @param supplier        the actual work to do on cache-miss
	 * @param <T>             response body type
	 * @return ResponseEntity — either the supplier's result or the cached one
	 */
	@Transactional
	public <T> ResponseEntity<T> executeOrReplay(
		String idempotencyKey,
		String endpoint,
		Class<T> responseType,
		Supplier<T> supplier
	) {
		if (idempotencyKey == null || idempotencyKey.isBlank()) {
			return ResponseEntity.ok(supplier.get());
		}

		Optional<IdempotencyRecord> cached = repository
			.findByIdempotencyKeyAndEndpoint(idempotencyKey, endpoint);

		if (cached.isPresent()) {
			log.info("Idempotent replay: endpoint={}, key={}", endpoint, idempotencyKey);
			return replay(cached.get(), responseType);
		}

		T result = supplier.get();

		try {
			IdempotencyRecord record = IdempotencyRecord.builder()
				.idempotencyKey(idempotencyKey)
				.endpoint(endpoint)
				.responseStatus(HttpStatus.OK.value())
				.responseBody(objectMapper.writeValueAsString(result))
				.build();
			repository.save(record);
		} catch (DataIntegrityViolationException race) {
			// Another thread beat us to the unique insert. Fall through to their cached response.
			log.debug("Idempotency race detected, replaying winner's response: key={}", idempotencyKey);
			return repository.findByIdempotencyKeyAndEndpoint(idempotencyKey, endpoint)
				.map(r -> this.<T>replay(r, responseType))
				.orElseGet(() -> ResponseEntity.ok(result));
		} catch (JsonProcessingException e) {
			// Serialization failure shouldn't block the response — log and return live result.
			log.warn("Failed to cache idempotency response for {}: {}", endpoint, e.getMessage());
		}

		return ResponseEntity.ok(result);
	}

	private <T> ResponseEntity<T> replay(IdempotencyRecord record, Class<T> responseType) {
		try {
			T body = objectMapper.readValue(record.getResponseBody(), responseType);
			return ResponseEntity.status(record.getResponseStatus()).body(body);
		} catch (JsonProcessingException e) {
			// Corrupt cache — best we can do is fail loudly.
			log.error("Corrupt idempotency cache for key={}, endpoint={}",
				record.getIdempotencyKey(), record.getEndpoint(), e);
			throw new IllegalStateException("Corrupt idempotency cache", e);
		}
	}

	/** Cleanup job — purge records older than retention. Runs daily at 03:00 UTC. */
	@Scheduled(cron = "0 0 3 * * *")
	@Transactional
	public void cleanupExpired() {
		LocalDateTime cutoff = LocalDateTime.now().minusHours(RETENTION_HOURS);
		int deleted = repository.deleteOlderThan(cutoff);
		if (deleted > 0) {
			log.info("Idempotency cleanup: deleted {} records older than {}", deleted, cutoff);
		}
	}
}
