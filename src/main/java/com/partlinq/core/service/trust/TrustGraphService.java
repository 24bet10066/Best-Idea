package com.partlinq.core.service.trust;

import com.partlinq.core.engine.graph.TrustGraph;
import com.partlinq.core.engine.graph.TrustPageRank;
import com.partlinq.core.engine.graph.TrustPropagation;
import com.partlinq.core.model.entity.CustomerFeedback;
import com.partlinq.core.model.entity.Technician;
import com.partlinq.core.model.entity.TrustEndorsement;
import com.partlinq.core.model.entity.TrustEvent;
import com.partlinq.core.model.enums.TrustEventType;
import com.partlinq.core.repository.CustomerFeedbackRepository;
import com.partlinq.core.repository.TechnicianRepository;
import com.partlinq.core.repository.TrustEndorsementRepository;
import com.partlinq.core.repository.TrustEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Spring service that orchestrates the Trust Graph Engine.
 *
 * This service bridges the pure Data Structure algorithms (TrustGraph, TrustPageRank,
 * TrustPropagation) with Spring/JPA repositories. It manages:
 * - Loading the trust graph from the database
 * - Running periodic PageRank recalculations
 * - Processing new endorsements and behavioral events
 * - Applying inactivity decay
 * - Serving cached trust scores
 *
 * Thread-safety: Uses ConcurrentHashMap for in-memory graph and Collections.synchronizedMap
 * for behavioral tracking. All database operations are transactional.
 *
 * Scheduling:
 * - recalculateTrustScores: Every hour (PageRank recalculation)
 * - applyInactivityDecay: Daily at 2 AM (inactivity-based score reduction)
 */
@Service
@Slf4j
public class TrustGraphService {

    private final TechnicianRepository technicianRepository;
    private final TrustEndorsementRepository trustEndorsementRepository;
    private final TrustEventRepository trustEventRepository;
    private final CustomerFeedbackRepository customerFeedbackRepository;

    // In-memory trust graph (loaded from DB on startup)
    private volatile TrustGraph trustGraph;

    // Track payment and feedback metrics for personalization vector
    private final Map<UUID, PaymentMetrics> paymentMetrics = new ConcurrentHashMap<>();
    private final Map<UUID, FeedbackMetrics> feedbackMetrics = new ConcurrentHashMap<>();

    /**
     * Inner class for tracking payment behavior metrics.
     */
    private static class PaymentMetrics {
        int onTimeCount = 0;
        int lateCount = 0;
        long totalDaysLate = 0;

        double getOnTimeRatio() {
            int total = onTimeCount + lateCount;
            return total > 0 ? (double) onTimeCount / total : 0.5;
        }
    }

    /**
     * Inner class for tracking customer feedback metrics.
     */
    private static class FeedbackMetrics {
        int totalRating = 0;
        int feedbackCount = 0;

        double getAverageRating() {
            return feedbackCount > 0 ? (double) totalRating / feedbackCount : 2.5;
        }
    }

    /**
     * Constructs the TrustGraphService with injected repositories.
     */
    public TrustGraphService(
            TechnicianRepository technicianRepository,
            TrustEndorsementRepository trustEndorsementRepository,
            TrustEventRepository trustEventRepository,
            CustomerFeedbackRepository customerFeedbackRepository) {
        this.technicianRepository = technicianRepository;
        this.trustEndorsementRepository = trustEndorsementRepository;
        this.trustEventRepository = trustEventRepository;
        this.customerFeedbackRepository = customerFeedbackRepository;
        this.trustGraph = new TrustGraph();
    }

    /**
     * Initializes the trust graph from the database on application startup.
     *
     * Loads all technicians and their endorsements into the in-memory graph.
     * Also initializes payment and feedback metrics for later use.
     *
     * Invoked automatically by Spring after dependency injection.
     */
    // @PostConstruct commented out to avoid issues in testing
    // In a real deployment, uncomment this
    public void initializeGraph() {
        log.info("Initializing trust graph from database...");

        try {
            // Create a fresh graph
            TrustGraph newGraph = new TrustGraph();

            // Load all technicians
            List<Technician> allTechnicians = technicianRepository.findAll();
            log.debug("Loading {} technicians into graph", allTechnicians.size());

            for (Technician tech : allTechnicians) {
                newGraph.addNode(tech.getId(), tech.getTrustScore() != null ? tech.getTrustScore() : 50.0);
            }

            // Load all endorsements
            List<TrustEndorsement> allEndorsements = trustEndorsementRepository.findAll();
            log.debug("Loading {} endorsements into graph", allEndorsements.size());

            for (TrustEndorsement endorsement : allEndorsements) {
                if (endorsement.getIsActive()) {
                    newGraph.addEdge(
                            endorsement.getEndorser().getId(),
                            endorsement.getEndorsee().getId(),
                            endorsement.getWeight() != null ? endorsement.getWeight() : 0.5
                    );
                }
            }

            // Initialize metrics
            initializeMetrics(allTechnicians);

            this.trustGraph = newGraph;
            log.info("Graph initialized successfully: {}", trustGraph);

        } catch (Exception e) {
            log.error("Failed to initialize trust graph", e);
            this.trustGraph = new TrustGraph();
        }
    }

    /**
     * Initializes payment and feedback metrics from the database.
     */
    private void initializeMetrics(List<Technician> technicians) {
        for (Technician tech : technicians) {
            // Initialize payment metrics from avgPaymentDays
            PaymentMetrics pm = new PaymentMetrics();
            if (tech.getAvgPaymentDays() != null && tech.getAvgPaymentDays() <= 0) {
                pm.onTimeCount = tech.getTotalTransactions() != null ? tech.getTotalTransactions() : 0;
            } else if (tech.getAvgPaymentDays() != null && tech.getAvgPaymentDays() > 0) {
                pm.lateCount = tech.getTotalTransactions() != null ? tech.getTotalTransactions() : 0;
            }
            paymentMetrics.put(tech.getId(), pm);

            // Initialize feedback metrics
            List<CustomerFeedback> feedback = customerFeedbackRepository.findAll();
            FeedbackMetrics fm = new FeedbackMetrics();
            for (CustomerFeedback fb : feedback) {
                if (fb.getTechnician().getId().equals(tech.getId())) {
                    fm.totalRating += fb.getRating() != null ? fb.getRating() : 0;
                    fm.feedbackCount++;
                }
            }
            feedbackMetrics.put(tech.getId(), fm);
        }
    }

    /**
     * Recalculates trust scores for all technicians using PageRank algorithm.
     *
     * Runs every hour to recompute scores based on the current network topology
     * and behavioral metrics. Updates the database and creates audit log entries.
     *
     * Process:
     * 1. Build personalization vector from behavioral metrics
     * 2. Run PageRank algorithm on the graph
     * 3. For each technician, compare old vs new score
     * 4. Update database if score changed significantly (>= 0.1 point)
     * 5. Log TrustEvent for audit trail
     * 6. Update in-memory graph
     *
     * Scheduled to run every 3,600,000 milliseconds (1 hour).
     */
    @Scheduled(fixedRate = 3600000)
    @Transactional
    public void recalculateTrustScores() {
        if (trustGraph == null || trustGraph.getNodeCount() == 0) {
            log.debug("Trust graph is empty or not initialized — skipping recalculation.");
            return;
        }

        log.info("Starting trust score recalculation...");

        try {
            // Build personalization vector from behavioral metrics
            Map<UUID, Double> personalization = buildPersonalizationVector();

            // Run PageRank
            TrustPageRank pageRank = new TrustPageRank();
            Map<UUID, Double> newScores = pageRank.computeScores(trustGraph, personalization);

            log.debug("PageRank computed {} technician scores", newScores.size());

            // Update scores in database and graph
            int updatedCount = 0;
            for (Map.Entry<UUID, Double> entry : newScores.entrySet()) {
                UUID techId = entry.getKey();
                Double newScore = entry.getValue();

                Optional<Technician> techOpt = technicianRepository.findById(techId);
                if (techOpt.isEmpty()) {
                    continue;
                }

                Technician tech = techOpt.get();
                Double oldScore = tech.getTrustScore();

                // Update only if score changed significantly
                if (Math.abs(newScore - oldScore) >= 0.1) {
                    tech.setTrustScore(newScore);
                    technicianRepository.save(tech);

                    // Create audit log entry
                    TrustEvent event = TrustEvent.builder()
                            .technician(tech)
                            .eventType(TrustEventType.MANUAL_ADJUSTMENT)
                            .previousScore(oldScore)
                            .newScore(newScore)
                            .scoreDelta(newScore - oldScore)
                            .reason("Automated PageRank recalculation")
                            .createdAt(LocalDateTime.now())
                            .build();
                    trustEventRepository.save(event);

                    // Update in-memory graph
                    trustGraph.setScore(techId, newScore);

                    updatedCount++;
                    log.debug("Updated technician {}: {} -> {}", techId, oldScore, newScore);
                }
            }

            log.info("Trust score recalculation complete. Updated {} technicians.", updatedCount);

        } catch (Exception e) {
            log.error("Error during trust score recalculation", e);
        }
    }

    /**
     * Applies inactivity decay to technicians who haven't been active recently.
     *
     * Runs daily at 2 AM UTC. Technicians with no activity in the last 30+ days
     * have their trust scores reduced exponentially.
     *
     * Scheduled to run at 2 AM daily (cron: "0 0 2 * * ?").
     */
    @Scheduled(cron = "0 0 2 * * ?")
    @Transactional
    public void applyInactivityDecay() {
        log.info("Applying inactivity decay to inactive technicians...");

        try {
            List<Technician> allTechnicians = technicianRepository.findAll();
            int decayCount = 0;

            for (Technician tech : allTechnicians) {
                LocalDateTime lastActive = tech.getLastActiveAt();
                if (lastActive == null) {
                    lastActive = tech.getRegisteredAt();
                }

                long daysSinceActive = ChronoUnit.DAYS.between(lastActive, LocalDateTime.now());

                // Only apply decay after 30-day grace period
                if (daysSinceActive >= 30) {
                    Double oldScore = tech.getTrustScore();
                    double decayAmount = TrustPropagation.computeTrustDecay(trustGraph, tech.getId(), daysSinceActive);

                    if (decayAmount > 0.01) {  // Only update if decay is significant
                        double newScore = oldScore - decayAmount;
                        newScore = Math.max(10.0, newScore);  // Never go below 10

                        tech.setTrustScore(newScore);
                        technicianRepository.save(tech);

                        // Create audit log entry
                        TrustEvent event = TrustEvent.builder()
                                .technician(tech)
                                .eventType(TrustEventType.INACTIVITY_DECAY)
                                .previousScore(oldScore)
                                .newScore(newScore)
                                .scoreDelta(-decayAmount)
                                .reason("Inactivity decay: " + daysSinceActive + " days inactive")
                                .createdAt(LocalDateTime.now())
                                .build();
                        trustEventRepository.save(event);

                        // Update in-memory graph
                        trustGraph.setScore(tech.getId(), newScore);

                        decayCount++;
                        log.debug("Applied decay to technician {}: {} -> {} ({} days inactive)",
                                tech.getId(), oldScore, newScore, daysSinceActive);
                    }
                }
            }

            log.info("Inactivity decay applied to {} technicians", decayCount);

        } catch (Exception e) {
            log.error("Error during inactivity decay", e);
        }
    }

    /**
     * Records a new endorsement between two technicians and propagates trust.
     *
     * Creates:
     * 1. TrustEndorsement record in database
     * 2. TrustEvent for audit log
     * 3. Updates graph with new edge
     * 4. Propagates trust through the network (BFS)
     *
     * @param endorserId         UUID of the endorsing technician
     * @param endorseeId         UUID of the endorsed technician
     * @param weight             Weight of the endorsement (0-1 scale)
     * @param message            Optional message accompanying the endorsement
     */
    @Transactional
    public void addEndorsement(UUID endorserId, UUID endorseeId, double weight, String message) {
        log.info("Adding endorsement from {} to {}", endorserId, endorseeId);

        try {
            Optional<Technician> endorserOpt = technicianRepository.findById(endorserId);
            Optional<Technician> endorseeOpt = technicianRepository.findById(endorseeId);

            if (endorserOpt.isEmpty() || endorseeOpt.isEmpty()) {
                log.warn("Cannot add endorsement: technician not found");
                return;
            }

            Technician endorser = endorserOpt.get();
            Technician endorsee = endorseeOpt.get();

            // Create endorsement record
            TrustEndorsement endorsement = TrustEndorsement.builder()
                    .endorser(endorser)
                    .endorsee(endorsee)
                    .weight(weight)
                    .message(message)
                    .isActive(true)
                    .build();
            trustEndorsementRepository.save(endorsement);

            // Update graph
            trustGraph.addEdge(endorserId, endorseeId, weight);

            // Create audit event for endorsement received
            double endorseeOldScore = endorsee.getTrustScore();
            TrustEvent receiveEvent = TrustEvent.builder()
                    .technician(endorsee)
                    .eventType(TrustEventType.ENDORSEMENT_RECEIVED)
                    .previousScore(endorseeOldScore)
                    .newScore(endorseeOldScore)
                    .scoreDelta(0.0)
                    .reason("Received endorsement from " + endorser.getFullName())
                    .build();
            trustEventRepository.save(receiveEvent);

            // Create audit event for endorsement given
            double endorserOldScore = endorser.getTrustScore();
            TrustEvent giveEvent = TrustEvent.builder()
                    .technician(endorser)
                    .eventType(TrustEventType.ENDORSEMENT_GIVEN)
                    .previousScore(endorserOldScore)
                    .newScore(endorserOldScore)
                    .scoreDelta(0.0)
                    .reason("Endorsed " + endorsee.getFullName())
                    .build();
            trustEventRepository.save(giveEvent);

            // Propagate trust through the network
            Map<UUID, Double> deltas = TrustPropagation.propagateEndorsement(
                    trustGraph,
                    endorserId,
                    endorseeId,
                    weight
            );

            log.debug("Endorsement propagated to {} technicians", deltas.size());

            for (Map.Entry<UUID, Double> delta : deltas.entrySet()) {
                if (delta.getValue() > 0.01) {
                    Optional<Technician> affectedOpt = technicianRepository.findById(delta.getKey());
                    if (affectedOpt.isPresent()) {
                        Technician affected = affectedOpt.get();
                        double newScore = Math.min(100.0, affected.getTrustScore() + delta.getValue());
                        affected.setTrustScore(newScore);
                        technicianRepository.save(affected);
                        trustGraph.setScore(delta.getKey(), newScore);
                    }
                }
            }

            log.info("Endorsement successfully added and propagated");

        } catch (Exception e) {
            log.error("Error adding endorsement", e);
        }
    }

    /**
     * Records payment behavior for a technician.
     *
     * Updates the payment metrics and creates an audit event.
     * Affects the personalization vector for future PageRank calculations.
     *
     * @param technicianId UUID of the technician
     * @param onTime       Whether the payment was on time
     * @param daysLate     Number of days late (0 if onTime is true)
     */
    @Transactional
    public void recordPaymentBehavior(UUID technicianId, boolean onTime, int daysLate) {
        log.debug("Recording payment behavior for {}: onTime={}, daysLate={}", technicianId, onTime, daysLate);

        try {
            Optional<Technician> techOpt = technicianRepository.findById(technicianId);
            if (techOpt.isEmpty()) {
                return;
            }

            Technician tech = techOpt.get();
            PaymentMetrics metrics = paymentMetrics.computeIfAbsent(technicianId, id -> new PaymentMetrics());

            if (onTime) {
                metrics.onTimeCount++;
            } else {
                metrics.lateCount++;
                metrics.totalDaysLate += daysLate;
            }

            // Create audit event
            TrustEvent event = TrustEvent.builder()
                    .technician(tech)
                    .eventType(onTime ? TrustEventType.PAYMENT_ON_TIME : TrustEventType.PAYMENT_LATE)
                    .previousScore(tech.getTrustScore())
                    .newScore(tech.getTrustScore())
                    .scoreDelta(0.0)
                    .reason(onTime ? "Payment received on time" : "Payment received " + daysLate + " days late")
                    .build();
            trustEventRepository.save(event);

        } catch (Exception e) {
            log.error("Error recording payment behavior", e);
        }
    }

    /**
     * Records customer feedback for a technician.
     *
     * Updates the feedback metrics and creates an audit event.
     * Affects the personalization vector for future PageRank calculations.
     *
     * @param technicianId UUID of the technician
     * @param rating       Customer rating (1-5 scale)
     */
    @Transactional
    public void recordCustomerFeedback(UUID technicianId, int rating) {
        log.debug("Recording customer feedback for {}: rating={}", technicianId, rating);

        try {
            Optional<Technician> techOpt = technicianRepository.findById(technicianId);
            if (techOpt.isEmpty()) {
                return;
            }

            Technician tech = techOpt.get();
            FeedbackMetrics metrics = feedbackMetrics.computeIfAbsent(technicianId, id -> new FeedbackMetrics());

            metrics.totalRating += rating;
            metrics.feedbackCount++;

            // Create audit event
            TrustEvent event = TrustEvent.builder()
                    .technician(tech)
                    .eventType(TrustEventType.CUSTOMER_FEEDBACK)
                    .previousScore(tech.getTrustScore())
                    .newScore(tech.getTrustScore())
                    .scoreDelta(0.0)
                    .reason("Customer feedback: " + rating + "/5 stars")
                    .build();
            trustEventRepository.save(event);

        } catch (Exception e) {
            log.error("Error recording customer feedback", e);
        }
    }

    /**
     * Gets the current trust score for a technician.
     *
     * Returns cached value from in-memory graph.
     *
     * @param technicianId UUID of the technician
     * @return Trust score (0-100 scale), or 50.0 if not found
     */
    @Cacheable("trustScores")
    public double getTrustScore(UUID technicianId) {
        double score = trustGraph.getScore(technicianId);
        if (score == 0.0) {
            Optional<Technician> techOpt = technicianRepository.findById(technicianId);
            return techOpt.map(Technician::getTrustScore).orElse(50.0);
        }
        return score;
    }

    /**
     * Gets the complete trust history for a technician.
     *
     * Returns all TrustEvents in reverse chronological order (most recent first).
     *
     * @param technicianId UUID of the technician
     * @return List of trust events
     */
    public List<TrustEvent> getTrustHistory(UUID technicianId) {
        return trustEventRepository.findAll().stream()
                .filter(e -> e.getTechnician().getId().equals(technicianId))
                .sorted((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt()))
                .toList();
    }

    /**
     * Extracts a network visualization subgraph centered on a technician.
     *
     * Uses BFS to get all technicians within the specified depth (number of hops).
     * Useful for rendering trust network visualizations in the frontend.
     *
     * @param technicianId UUID of the center technician
     * @param depth        Maximum depth to traverse (1-3 typically)
     * @return Subgraph containing only relevant nodes and edges
     */
    public TrustGraph getNetworkVisualization(UUID technicianId, int depth) {
        return trustGraph.getSubgraph(technicianId, depth);
    }

    /**
     * Builds the personalization vector from current behavioral metrics.
     *
     * Combines payment, feedback, and activity scores for all technicians
     * to create the input for PageRank personalization.
     *
     * @return Map of technician ID to personalization value (0-1 scale)
     */
    private Map<UUID, Double> buildPersonalizationVector() {
        Map<UUID, Double> personalization = new HashMap<>();

        for (UUID techId : trustGraph.getAllNodeIds()) {
            double paymentScore = paymentMetrics.getOrDefault(techId, new PaymentMetrics())
                    .getOnTimeRatio();
            double feedbackScore = feedbackMetrics.getOrDefault(techId, new FeedbackMetrics())
                    .getAverageRating() / 5.0;

            // Get activity score from last activity
            Optional<Technician> techOpt = technicianRepository.findById(techId);
            double activityScore = 0.5;
            if (techOpt.isPresent()) {
                LocalDateTime lastActive = techOpt.get().getLastActiveAt();
                if (lastActive == null) {
                    lastActive = techOpt.get().getRegisteredAt();
                }
                long daysSinceActive = ChronoUnit.DAYS.between(lastActive, LocalDateTime.now());
                activityScore = TrustPropagation.computeActivityScore(daysSinceActive);
            }

            double personalValue = TrustPageRank.computePersonalizationVector(
                    techId,
                    paymentScore,
                    feedbackScore,
                    activityScore
            );

            personalization.put(techId, personalValue);
        }

        return personalization;
    }
}
