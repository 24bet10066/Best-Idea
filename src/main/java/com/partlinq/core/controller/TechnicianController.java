package com.partlinq.core.controller;

import com.partlinq.core.exception.EntityNotFoundException;
import com.partlinq.core.model.dto.*;
import com.partlinq.core.model.entity.CustomerFeedback;
import com.partlinq.core.model.entity.Technician;
import com.partlinq.core.model.entity.TrustEndorsement;
import com.partlinq.core.model.entity.TrustEvent;
import com.partlinq.core.model.enums.ApplianceType;
import com.partlinq.core.repository.CustomerFeedbackRepository;
import com.partlinq.core.repository.TechnicianRepository;
import com.partlinq.core.repository.TrustEndorsementRepository;
import com.partlinq.core.service.trust.TrustGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for technician management.
 * Handles registration, profile retrieval, trust operations, and feedback.
 */
@RestController
@RequestMapping("/v1/technicians")
@Tag(name = "Technicians", description = "Technician registration and management")
@RequiredArgsConstructor
@Slf4j
public class TechnicianController {

	private final TechnicianRepository technicianRepository;
	private final TrustEndorsementRepository trustEndorsementRepository;
	private final CustomerFeedbackRepository customerFeedbackRepository;
	private final TrustGraphService trustGraphService;

	/**
	 * Register a new technician.
	 *
	 * @param request TechnicianRegistrationRequest with profile details
	 * @return ResponseEntity with created TechnicianResponse (201)
	 */
	@PostMapping
	@Operation(summary = "Register a new technician", description = "Create a new technician profile with specializations and credit limit")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Technician registered successfully",
			content = @Content(schema = @Schema(implementation = TechnicianResponse.class))),
		@ApiResponse(responseCode = "400", description = "Invalid request data")
	})
	public ResponseEntity<TechnicianResponse> registerTechnician(@Valid @RequestBody TechnicianRegistrationRequest request) {
		log.info("Registering technician: {}", request.fullName());

		try {
			Technician technician = Technician.builder()
				.fullName(request.fullName())
				.phone(request.phone())
				.email(request.email())
				.city(request.city())
				.pincode(request.pincode())
				.specializations(request.specializations())
				.creditLimit(request.creditLimit())
				.profileImageUrl(request.profileImageUrl())
				.trustScore(50.0)
				.isVerified(false)
				.totalTransactions(0)
				.avgPaymentDays(0.0)
				.build();

			Technician saved = technicianRepository.save(technician);
			TechnicianResponse response = mapToResponse(saved);

			log.info("Technician registered successfully: {}", saved.getId());
			return ResponseEntity.status(HttpStatus.CREATED).body(response);
		} catch (Exception e) {
			log.error("Error registering technician", e);
			throw e;
		}
	}

	/**
	 * Get technician by ID.
	 *
	 * @param id Technician UUID
	 * @return ResponseEntity with TechnicianResponse (200)
	 * @throws EntityNotFoundException if technician not found
	 */
	@GetMapping("/{id}")
	@Operation(summary = "Get technician by ID", description = "Retrieve a technician's profile details")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Technician found",
			content = @Content(schema = @Schema(implementation = TechnicianResponse.class))),
		@ApiResponse(responseCode = "404", description = "Technician not found")
	})
	public ResponseEntity<TechnicianResponse> getTechnicianById(
		@Parameter(description = "Technician ID") @PathVariable UUID id) {

		Technician technician = technicianRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Technician", id));

		return ResponseEntity.ok(mapToResponse(technician));
	}

	/**
	 * List all technicians with optional city filter.
	 *
	 * @param city Optional city filter
	 * @return ResponseEntity with list of TechnicianResponse (200)
	 */
	@GetMapping
	@Operation(summary = "List all technicians", description = "Get all technicians with optional city filter")
	@ApiResponse(responseCode = "200", description = "List of technicians")
	public ResponseEntity<List<TechnicianResponse>> listTechnicians(
		@Parameter(description = "City filter (optional)") @RequestParam(required = false) String city) {

		List<Technician> technicians;
		if (city != null && !city.isEmpty()) {
			technicians = technicianRepository.findByCity(city);
		} else {
			technicians = technicianRepository.findAll();
		}

		List<TechnicianResponse> responses = technicians.stream()
			.map(this::mapToResponse)
			.collect(Collectors.toList());

		return ResponseEntity.ok(responses);
	}

	/**
	 * Search technicians by city and specialization.
	 *
	 * @param city              City name
	 * @param applianceType Optional appliance type for filtering
	 * @return ResponseEntity with filtered TechnicianResponse list (200)
	 */
	@GetMapping("/search")
	@Operation(summary = "Search technicians", description = "Search for technicians by city and appliance specialization")
	@ApiResponse(responseCode = "200", description = "List of matching technicians")
	public ResponseEntity<List<TechnicianResponse>> searchTechnicians(
		@Parameter(description = "City name") @RequestParam String city,
		@Parameter(description = "Appliance type (optional)") @RequestParam(required = false) ApplianceType applianceType) {

		List<Technician> technicians;
		if (applianceType != null) {
			technicians = technicianRepository.findByCityAndVerifiedAndSpecialization(city, 50.0, applianceType);
		} else {
			technicians = technicianRepository.findByCity(city);
		}

		List<TechnicianResponse> responses = technicians.stream()
			.map(this::mapToResponse)
			.collect(Collectors.toList());

		return ResponseEntity.ok(responses);
	}

	/**
	 * Update technician profile.
	 *
	 * @param id      Technician UUID
	 * @param request TechnicianRegistrationRequest with updated data
	 * @return ResponseEntity with updated TechnicianResponse (200)
	 * @throws EntityNotFoundException if technician not found
	 */
	@PutMapping("/{id}")
	@Operation(summary = "Update technician profile", description = "Update technician details like contact info and specializations")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Technician updated successfully"),
		@ApiResponse(responseCode = "404", description = "Technician not found")
	})
	public ResponseEntity<TechnicianResponse> updateTechnician(
		@Parameter(description = "Technician ID") @PathVariable UUID id,
		@Valid @RequestBody TechnicianRegistrationRequest request) {

		log.info("Updating technician: {}", id);

		Technician technician = technicianRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Technician", id));

		technician.setFullName(request.fullName());
		technician.setPhone(request.phone());
		technician.setEmail(request.email());
		technician.setCity(request.city());
		technician.setPincode(request.pincode());
		technician.setSpecializations(request.specializations());
		technician.setCreditLimit(request.creditLimit());
		if (request.profileImageUrl() != null) {
			technician.setProfileImageUrl(request.profileImageUrl());
		}

		Technician updated = technicianRepository.save(technician);
		return ResponseEntity.ok(mapToResponse(updated));
	}

	/**
	 * Get trust score for a technician.
	 *
	 * @param id Technician UUID
	 * @return ResponseEntity with TrustScoreResponse (200)
	 * @throws EntityNotFoundException if technician not found
	 */
	@GetMapping("/{id}/trust")
	@Operation(summary = "Get trust score", description = "Retrieve technician's trust score and history")
	@ApiResponses({
		@ApiResponse(responseCode = "200", description = "Trust score retrieved",
			content = @Content(schema = @Schema(implementation = TrustScoreResponse.class))),
		@ApiResponse(responseCode = "404", description = "Technician not found")
	})
	public ResponseEntity<TrustScoreResponse> getTrustScore(
		@Parameter(description = "Technician ID") @PathVariable UUID id) {

		Technician technician = technicianRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Technician", id));

		double score = trustGraphService.getTrustScore(id);

		// Build endorsement stats
		long totalEndorsements = trustEndorsementRepository.findByEndorseeId(id).size();
		double avgWeight = trustEndorsementRepository.findByEndorseeId(id).stream()
			.mapToDouble(TrustEndorsement::getWeight)
			.average()
			.orElse(0.0);

		// Build feedback stats
		long positiveReviews = customerFeedbackRepository.findByTechnicianIdOrderByCreatedAtDesc(id).stream()
			.filter(f -> f.getRating() >= 4)
			.count();
		long negativeReviews = customerFeedbackRepository.findByTechnicianIdOrderByCreatedAtDesc(id).stream()
			.filter(f -> f.getRating() <= 2)
			.count();

		// Build recent trust events
		List<TrustEvent> history = trustGraphService.getTrustHistory(id);
		List<TrustScoreResponse.TrustEventResponse> recentEvents = history.stream()
			.limit(20)
			.map(e -> new TrustScoreResponse.TrustEventResponse(
				e.getId(),
				e.getEventType(),
				e.getScoreDelta(),
				e.getPreviousScore(),
				e.getNewScore(),
				e.getReason(),
				e.getCreatedAt()
			))
			.toList();

		// Compute rank (1-based, from all technicians ordered by trust score)
		List<Technician> allTechs = technicianRepository.findAll();
		allTechs.sort((a, b) -> Double.compare(b.getTrustScore(), a.getTrustScore()));
		int rank = 1;
		for (Technician t : allTechs) {
			if (t.getId().equals(id)) break;
			rank++;
		}

		TrustScoreResponse response = new TrustScoreResponse(
			id,
			technician.getFullName(),
			score,
			rank,
			totalEndorsements,
			avgWeight,
			positiveReviews,
			negativeReviews,
			recentEvents
		);

		return ResponseEntity.ok(response);
	}

	/**
	 * Create an endorsement for a technician.
	 *
	 * @param id      Endorsee technician UUID
	 * @param request EndorsementRequest with endorser and weight
	 * @return ResponseEntity with 201 status
	 * @throws EntityNotFoundException if technician not found
	 */
	@PostMapping("/{id}/endorse")
	@Operation(summary = "Create endorsement", description = "Create a trust endorsement for a technician")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Endorsement created successfully"),
		@ApiResponse(responseCode = "404", description = "Technician not found")
	})
	public ResponseEntity<Void> createEndorsement(
		@Parameter(description = "Endorsee technician ID") @PathVariable UUID id,
		@Valid @RequestBody EndorsementRequest request) {

		log.info("Creating endorsement for technician: {}", id);

		Technician endorsee = technicianRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Technician", id));

		Technician endorser = technicianRepository.findById(request.endorseeId())
			.orElseThrow(() -> new EntityNotFoundException("Technician", request.endorseeId()));

		TrustEndorsement endorsement = TrustEndorsement.builder()
			.endorser(endorser)
			.endorsee(endorsee)
			.weight(request.weight())
			.message(request.message())
			.createdAt(LocalDateTime.now())
			.build();

		trustEndorsementRepository.save(endorsement);
		log.info("Endorsement created: {} -> {}", request.endorseeId(), id);

		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	/**
	 * Submit customer feedback for a technician.
	 *
	 * @param id      Technician UUID
	 * @param request FeedbackRequest with rating and comment
	 * @return ResponseEntity with 201 status
	 * @throws EntityNotFoundException if technician not found
	 */
	@PostMapping("/{id}/feedback")
	@Operation(summary = "Submit feedback", description = "Submit customer feedback and rating for a technician")
	@ApiResponses({
		@ApiResponse(responseCode = "201", description = "Feedback submitted successfully"),
		@ApiResponse(responseCode = "404", description = "Technician not found")
	})
	public ResponseEntity<Void> submitFeedback(
		@Parameter(description = "Technician ID") @PathVariable UUID id,
		@Valid @RequestBody FeedbackRequest request) {

		log.info("Submitting feedback for technician: {}", id);

		Technician technician = technicianRepository.findById(id)
			.orElseThrow(() -> new EntityNotFoundException("Technician", id));

		CustomerFeedback feedback = CustomerFeedback.builder()
			.technician(technician)
			.customerName(request.customerName())
			.customerPhone(request.customerPhone())
			.rating(request.rating())
			.comment(request.comment())
			.serviceType(request.serviceType())
			.createdAt(LocalDateTime.now())
			.build();

		customerFeedbackRepository.save(feedback);
		log.info("Feedback submitted for technician: {}", id);

		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	/**
	 * Convert Technician entity to TechnicianResponse DTO.
	 *
	 * @param technician Technician entity
	 * @return TechnicianResponse DTO
	 */
	private TechnicianResponse mapToResponse(Technician technician) {
		return new TechnicianResponse(
			technician.getId(),
			technician.getFullName(),
			technician.getPhone(),
			technician.getEmail(),
			technician.getCity(),
			technician.getPincode(),
			technician.getSpecializations(),
			technician.getTrustScore(),
			technician.getCreditLimit(),
			technician.getTotalTransactions(),
			technician.getAvgPaymentDays(),
			technician.getIsVerified(),
			technician.getProfileImageUrl(),
			technician.getReferredById(),
			technician.getRegisteredAt(),
			technician.getLastActiveAt()
		);
	}
}
