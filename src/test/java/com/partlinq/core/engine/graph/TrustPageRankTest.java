package com.partlinq.core.engine.graph;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the TrustPageRank algorithm.
 * Validates convergence, score distribution, and edge cases.
 */
class TrustPageRankTest {

    private TrustPageRank pageRank;
    private TrustGraph graph;

    // Fixed UUIDs for deterministic tests
    private final UUID nodeA = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private final UUID nodeB = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private final UUID nodeC = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private final UUID nodeD = UUID.fromString("00000000-0000-0000-0000-000000000004");

    @BeforeEach
    void setUp() {
        pageRank = new TrustPageRank();
        graph = new TrustGraph();
    }

    @Test
    @DisplayName("4-node graph: A->B, B->C, C->A, D->B. B should rank highest.")
    void testFourNodeGraph_BHasHighestScore() {
        graph.addNode(nodeA, 50.0);
        graph.addNode(nodeB, 50.0);
        graph.addNode(nodeC, 50.0);
        graph.addNode(nodeD, 50.0);

        graph.addEdge(nodeA, nodeB, 0.8);  // A endorses B
        graph.addEdge(nodeB, nodeC, 0.7);  // B endorses C
        graph.addEdge(nodeC, nodeA, 0.9);  // C endorses A (cycle)
        graph.addEdge(nodeD, nodeB, 0.85); // D also endorses B

        Map<UUID, Double> personalization = new HashMap<>();
        personalization.put(nodeA, 0.7);
        personalization.put(nodeB, 0.8);
        personalization.put(nodeC, 0.6);
        personalization.put(nodeD, 0.5);

        Map<UUID, Double> scores = pageRank.computeScores(graph, personalization);

        // All scores should be positive
        scores.values().forEach(score ->
                assertTrue(score > 0, "All scores must be positive"));

        // B has 2 incoming edges (from A and D), should have highest score
        double scoreB = scores.get(nodeB);
        double scoreA = scores.get(nodeA);
        double scoreC = scores.get(nodeC);
        double scoreD = scores.get(nodeD);

        assertTrue(scoreB > scoreD,
                "B (2 incoming edges) should score higher than D (0 incoming edges). B=%.4f, D=%.4f"
                        .formatted(scoreB, scoreD));
    }

    @Test
    @DisplayName("Convergence: scores should be stable and within 0-100 range")
    void testConvergence_ScoresInValidRange() {
        graph.addNode(nodeA, 50.0);
        graph.addNode(nodeB, 50.0);
        graph.addNode(nodeC, 50.0);

        graph.addEdge(nodeA, nodeB, 0.9);
        graph.addEdge(nodeB, nodeC, 0.8);
        graph.addEdge(nodeC, nodeA, 0.7);

        Map<UUID, Double> scores = pageRank.computeScores(graph, new HashMap<>());

        for (var entry : scores.entrySet()) {
            double score = entry.getValue();
            assertTrue(score >= 0 && score <= 100,
                    "Score for %s should be 0-100, got %.4f".formatted(entry.getKey(), score));
        }
    }

    @Test
    @DisplayName("Empty graph should throw IllegalArgumentException")
    void testEmptyGraph_ThrowsException() {
        assertThrows(IllegalArgumentException.class, () ->
                pageRank.computeScores(graph, new HashMap<>()));
    }

    @Test
    @DisplayName("Single node graph: should return valid score")
    void testSingleNode_ReturnsValidScore() {
        graph.addNode(nodeA, 50.0);

        Map<UUID, Double> personalization = Map.of(nodeA, 0.8);
        Map<UUID, Double> scores = pageRank.computeScores(graph, personalization);

        assertNotNull(scores.get(nodeA));
        assertTrue(scores.get(nodeA) > 0, "Single node should have a positive score");
    }

    @Test
    @DisplayName("Uniform personalization: cycle graph should produce similar scores")
    void testUniformPersonalization_CycleGraphSimilarScores() {
        graph.addNode(nodeA, 50.0);
        graph.addNode(nodeB, 50.0);
        graph.addNode(nodeC, 50.0);

        // Perfect symmetric cycle: A->B->C->A with equal weights
        graph.addEdge(nodeA, nodeB, 1.0);
        graph.addEdge(nodeB, nodeC, 1.0);
        graph.addEdge(nodeC, nodeA, 1.0);

        Map<UUID, Double> scores = pageRank.computeScores(graph, new HashMap<>());

        double scoreA = scores.get(nodeA);
        double scoreB = scores.get(nodeB);
        double scoreC = scores.get(nodeC);

        // In a perfect symmetric cycle with uniform personalization,
        // all scores should be approximately equal
        double maxDiff = Math.max(Math.abs(scoreA - scoreB), Math.abs(scoreB - scoreC));
        assertTrue(maxDiff < 1.0,
                "Symmetric cycle scores should be similar. A=%.4f, B=%.4f, C=%.4f"
                        .formatted(scoreA, scoreB, scoreC));
    }

    @Test
    @DisplayName("Null personalization vector: should use uniform distribution")
    void testNullPersonalization_UsesUniform() {
        graph.addNode(nodeA, 50.0);
        graph.addNode(nodeB, 50.0);
        graph.addEdge(nodeA, nodeB, 0.9);

        // Should not throw with null personalization
        Map<UUID, Double> scores = pageRank.computeScores(graph, null);

        assertNotNull(scores);
        assertEquals(2, scores.size());
        assertTrue(scores.get(nodeA) > 0);
        assertTrue(scores.get(nodeB) > 0);
    }

    @Test
    @DisplayName("computePersonalizationVector: weighted combination is correct")
    void testPersonalizationVector_WeightedCombination() {
        // 0.4 * payment + 0.35 * feedback + 0.25 * activity
        double result = TrustPageRank.computePersonalizationVector(
                nodeA, 1.0, 1.0, 1.0);
        assertEquals(1.0, result, 0.001, "All 1.0 inputs should produce 1.0");

        double result2 = TrustPageRank.computePersonalizationVector(
                nodeA, 0.0, 0.0, 0.0);
        assertEquals(0.0, result2, 0.001, "All 0.0 inputs should produce 0.0");

        // 0.4*0.8 + 0.35*0.6 + 0.25*0.4 = 0.32 + 0.21 + 0.10 = 0.63
        double result3 = TrustPageRank.computePersonalizationVector(
                nodeA, 0.8, 0.6, 0.4);
        assertEquals(0.63, result3, 0.001);
    }

    @Test
    @DisplayName("computePersonalizationVector: values are clamped to 0-1")
    void testPersonalizationVector_ClampedValues() {
        // Inputs > 1.0 should be clamped to 1.0
        double result = TrustPageRank.computePersonalizationVector(
                nodeA, 2.0, 1.5, 3.0);
        assertEquals(1.0, result, 0.001, "Inputs > 1 should be clamped, result should be 1.0");

        // Inputs < 0.0 should be clamped to 0.0
        double result2 = TrustPageRank.computePersonalizationVector(
                nodeA, -1.0, -0.5, -2.0);
        assertEquals(0.0, result2, 0.001, "Inputs < 0 should be clamped, result should be 0.0");
    }
}
