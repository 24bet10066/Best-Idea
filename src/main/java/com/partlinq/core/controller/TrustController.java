package com.partlinq.core.controller;

import com.partlinq.core.engine.credit.CreditScoringEngine;
import com.partlinq.core.model.dto.TrustScoreResponse;
import com.partlinq.core.model.entity.Technician;
import com.partlinq.core.repository.TechnicianRepository;
import com.partlinq.core.service.credit.CreditService;
import com.partlinq.core.service.trust.TrustGraphService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for trust graph and credit scoring.
 * Exposes the Trust PageRank engine, credit evaluations, and graph analytics.
 *
 * <h3>Core DSA:</h3>
 * <ul>
 *   <li>Trust PageRank — modified weighted graph algorithm</li>
 *   <li>BFS trust propagation with exponential decay</li>
 *   <li>Bayesian credit scoring with sealed risk levels (Java 21)</li>
 * </ul>
 */
@RestController
@RequestMapping("/v1/trust")
@Tag(name = "Trust & Credit", description = "Trust graph analytics, credit scoring, and risk evaluation")
@RequiredArgsConstructor
@Slf4j
public class TrustController {

    private final TrustGraphService trustGraphService;
    private final CreditService creditService;
    private final TechnicianRepository technicianRepository;

    /**
     * Get trust score for a technician.
     * Returns current PageRank score, rank, endorsements, and recent events.
     */
    @GetMapping("/{technicianId}/score")
    @Operation(summary = "Get trust score", description = "Retrieve trust score, rank, endorsements, and recent trust events")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Trust score retrieved"),
        @ApiResponse(responseCode = "404", description = "Technician not found")
    })
    public ResponseEntity<ScoreResponse> getTrustScore(
            @Parameter(description = "Technician ID") @PathVariable UUID technicianId) {
        Technician tech = technicianRepository.findById(technicianId)
                .orElse(null);
        if (tech == null) {
            return ResponseEntity.notFound().build();
        }

        double score = trustGraphService.getTrustScore(technicianId);
        return ResponseEntity.ok(new ScoreResponse(technicianId, tech.getFullName(), score));
    }

    /**
     * Simple trust score response.
     */
    public record ScoreResponse(UUID technicianId, String fullName, double trustScore) {}

    /**
     * Get trust leaderboard — top technicians ranked by trust score.
     * Returns simplified leaderboard entries (not full TrustScoreResponse).
     */
    @GetMapping("/leaderboard")
    @Operation(summary = "Trust leaderboard", description = "Top technicians by trust score")
    @ApiResponse(responseCode = "200", description = "Leaderboard")
    public ResponseEntity<List<LeaderboardEntry>> getLeaderboard(
            @Parameter(description = "Number of top entries") @RequestParam(defaultValue = "20") int limit) {

        List<Technician> allTechs = technicianRepository.findAll();
        allTechs.sort((a, b) -> Double.compare(b.getTrustScore(), a.getTrustScore()));

        List<LeaderboardEntry> leaderboard = new java.util.ArrayList<>();
        int rank = 1;
        for (Technician tech : allTechs) {
            if (rank > limit) break;
            leaderboard.add(new LeaderboardEntry(
                    tech.getId(),
                    tech.getFullName(),
                    tech.getTrustScore(),
                    rank,
                    tech.getCity(),
                    tech.getTotalTransactions(),
                    tech.getIsVerified()
            ));
            rank++;
        }

        return ResponseEntity.ok(leaderboard);
    }

    /**
     * Leaderboard entry DTO.
     */
    public record LeaderboardEntry(
            UUID technicianId,
            String fullName,
            Double trustScore,
            Integer rank,
            String city,
            Integer totalTransactions,
            Boolean isVerified
    ) {}

    /**
     * Evaluate credit for a technician.
     * Returns credit score, limit, and sealed RiskLevel (Java 21).
     */
    @GetMapping("/{technicianId}/credit")
    @Operation(summary = "Evaluate credit", description = "Compute credit score, limit, and risk level using Bayesian scoring")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Credit evaluation"),
        @ApiResponse(responseCode = "404", description = "Technician not found")
    })
    public ResponseEntity<CreditResponse> evaluateCredit(
            @Parameter(description = "Technician ID") @PathVariable UUID technicianId) {
        CreditScoringEngine.CreditResult result = creditService.evaluateCredit(technicianId);
        if (result == null) {
            return ResponseEntity.notFound().build();
        }

        // Java 21: pattern matching on sealed RiskLevel
        String riskLabel = switch (result.riskLevel()) {
            case CreditScoringEngine.RiskLevel.Low low -> "LOW";
            case CreditScoringEngine.RiskLevel.Medium med -> "MEDIUM";
            case CreditScoringEngine.RiskLevel.High high -> "HIGH";
        };

        double riskScore = switch (result.riskLevel()) {
            case CreditScoringEngine.RiskLevel.Low low -> low.score();
            case CreditScoringEngine.RiskLevel.Medium med -> med.score();
            case CreditScoringEngine.RiskLevel.High high -> high.score();
        };

        CreditResponse response = new CreditResponse(
                result.profile().technicianId(),
                result.creditScore(),
                result.creditLimit(),
                riskLabel,
                riskScore,
                result.profile().trustScore(),
                result.profile().totalTransactions(),
                result.profile().avgPaymentDays(),
                result.profile().onTimePaymentRatio(),
                result.profile().tenureDays()
        );

        return ResponseEntity.ok(response);
    }

    /**
     * Check if a technician can use credit for a given amount.
     */
    @GetMapping("/{technicianId}/credit/check")
    @Operation(summary = "Credit check", description = "Check if a technician has sufficient credit for an order amount")
    @ApiResponse(responseCode = "200", description = "Credit check result")
    public ResponseEntity<CreditCheckResponse> checkCredit(
            @Parameter(description = "Technician ID") @PathVariable UUID technicianId,
            @Parameter(description = "Order amount in ₹") @RequestParam BigDecimal amount) {
        boolean approved = creditService.canExtendCredit(technicianId, amount);
        return ResponseEntity.ok(new CreditCheckResponse(technicianId, amount, approved));
    }

    /**
     * Force recalculation of all trust scores (admin endpoint).
     */
    @PostMapping("/recalculate")
    @Operation(summary = "Recalculate trust scores", description = "Force PageRank recalculation across the entire trust graph")
    @ApiResponse(responseCode = "200", description = "Recalculation triggered")
    public ResponseEntity<Void> recalculateTrustScores() {
        log.info("Manual trust score recalculation triggered");
        trustGraphService.recalculateTrustScores();
        return ResponseEntity.ok().build();
    }

    /**
     * Credit evaluation response DTO.
     */
    public record CreditResponse(
            UUID technicianId,
            double creditScore,
            BigDecimal creditLimit,
            String riskLevel,
            double riskScore,
            double trustScore,
            int totalTransactions,
            double avgPaymentDays,
            double onTimePaymentRatio,
            long tenureDays
    ) {}

    /**
     * Credit check response DTO.
     */
    public record CreditCheckResponse(
            UUID technicianId,
            BigDecimal requestedAmount,
            boolean approved
    ) {}
}
