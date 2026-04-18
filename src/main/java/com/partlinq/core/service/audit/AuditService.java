package com.partlinq.core.service.audit;

import com.partlinq.core.model.entity.AuditEvent;
import com.partlinq.core.repository.AuditEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * AuditService — fire-and-forget recorder for sensitive operations.
 *
 * Design notes:
 *   - REQUIRES_NEW propagation so an audit-log failure NEVER rolls back the
 *     business transaction. We'd rather lose an audit row than silently fail
 *     a payment.
 *   - All write methods accept structured args, build the AuditEvent, save.
 *   - Reads go straight through the repository — no service indirection needed.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AuditService {

	private final AuditEventRepository repository;

	public static final class Action {
		public static final String PAYMENT_RECORDED      = "PAYMENT_RECORDED";
		public static final String ADJUSTMENT_MADE       = "ADJUSTMENT_MADE";
		public static final String CREDIT_LIMIT_CHANGED  = "CREDIT_LIMIT_CHANGED";
		public static final String ORDER_CANCELLED       = "ORDER_CANCELLED";
		public static final String TRUST_SCORE_OVERRIDE  = "TRUST_SCORE_OVERRIDE";
		public static final String SHOP_REGISTERED       = "SHOP_REGISTERED";
		public static final String TECHNICIAN_REGISTERED = "TECHNICIAN_REGISTERED";
		private Action() {}
	}

	public static final class Subject {
		public static final String TECHNICIAN     = "TECHNICIAN";
		public static final String SHOP           = "SHOP";
		public static final String ORDER          = "ORDER";
		public static final String LEDGER         = "LEDGER";
		public static final String CREDIT_PROFILE = "CREDIT_PROFILE";
		private Subject() {}
	}

	/**
	 * Record an audit event. Never throws — failures are logged and swallowed.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(
		String actor,
		String action,
		String subjectType,
		UUID subjectId,
		UUID shopId,
		String delta
	) {
		try {
			AuditEvent event = AuditEvent.builder()
				.actor(actor != null ? actor : "system")
				.action(action)
				.subjectType(subjectType)
				.subjectId(subjectId)
				.shopId(shopId)
				.delta(delta)
				.build();
			repository.save(event);
		} catch (Exception e) {
			// Audit failure must NEVER block business flow. Log loudly, move on.
			log.error("Failed to record audit event: actor={}, action={}, subject={}/{}: {}",
				actor, action, subjectType, subjectId, e.getMessage());
		}
	}

	/** Convenience overload without shop scope. */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public void record(String actor, String action, String subjectType, UUID subjectId, String delta) {
		record(actor, action, subjectType, subjectId, null, delta);
	}
}
