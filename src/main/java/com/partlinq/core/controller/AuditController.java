package com.partlinq.core.controller;

import com.partlinq.core.model.entity.AuditEvent;
import com.partlinq.core.repository.AuditEventRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Read-only audit trail. No POST/PUT/DELETE — append-only by design.
 *
 * Lock these endpoints behind admin auth before public launch (see ADMIN-GUIDE.md).
 */
@RestController
@RequestMapping("/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit Trail", description = "Read-only access to sensitive operation history")
public class AuditController {

	private final AuditEventRepository repository;

	/** Trail for one entity (e.g. all events touching a specific ledger row). */
	@GetMapping("/subject")
	@Operation(summary = "Get all audit events for one subject (entity)")
	public ResponseEntity<List<AuditEvent>> bySubject(
		@RequestParam String subjectType,
		@RequestParam UUID subjectId
	) {
		return ResponseEntity.ok(
			repository.findBySubjectTypeAndSubjectIdOrderByCreatedAtDesc(subjectType, subjectId));
	}

	/** All events scoped to one shop. The shop owner's "what happened in my shop" view. */
	@GetMapping("/shop/{shopId}")
	@Operation(summary = "Get all audit events for one shop")
	public ResponseEntity<List<AuditEvent>> byShop(@PathVariable UUID shopId) {
		return ResponseEntity.ok(repository.findByShopIdOrderByCreatedAtDesc(shopId));
	}

	/** All events by one actor. The "who's been editing things" view. */
	@GetMapping("/actor/{actor}")
	@Operation(summary = "Get all audit events by one actor")
	public ResponseEntity<List<AuditEvent>> byActor(@PathVariable String actor) {
		return ResponseEntity.ok(repository.findByActorOrderByCreatedAtDesc(actor));
	}

	/** All events of one action type. The "show me every adjustment ever" view. */
	@GetMapping("/action/{action}")
	@Operation(summary = "Get all audit events of one action type")
	public ResponseEntity<List<AuditEvent>> byAction(@PathVariable String action) {
		return ResponseEntity.ok(repository.findByActionOrderByCreatedAtDesc(action));
	}
}
