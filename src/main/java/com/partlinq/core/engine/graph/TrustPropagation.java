package com.partlinq.core.engine.graph;

import java.util.*;

/**
 * BFS-based trust propagation algorithm for the PartLinQ trust network.
 *
 * When technician A endorses technician B, a fraction of A's trust propagates
 * through the graph up to a configurable depth. This creates a "web of trust"
 * where being endorsed by high-trust technicians is more valuable than
 * being endorsed by low-trust technicians.
 *
 * Algorithm Overview:
 * 1. When a new endorsement occurs, BFS from the endorsed technician outward
 * 2. At each BFS level, propagate trust = endorserScore * endorsementWeight * DECAY_FACTOR^level
 * 3. Return map of all affected technicians and their score deltas
 * 4. Cap propagation at MAX_PROPAGATION_DEPTH levels
 *
 * Example:
 * - Technician A (score 80) endorses Technician B (score 50) with weight 0.9
 * - B's incoming endorsement contributes: 80 * 0.9 = 72 units to propagation
 * - Anyone who B endorsed gets: 72 * 0.5 = 36 units (level 1, decay factor 0.5)
 * - Anyone who those people endorsed gets: 36 * 0.5 = 18 units (level 2)
 * - And so on, up to MAX_PROPAGATION_DEPTH levels
 *
 * Key DSA: BFS, Level-order Traversal, Exponential Decay Propagation
 */
public class TrustPropagation {

    /**
     * Maximum depth for trust propagation in BFS.
     * Limits how far trust influence can spread through the network.
     * 3 levels (depth) means endorsements can influence up to 3 hops away.
     */
    private static final int MAX_PROPAGATION_DEPTH = 3;

    /**
     * Decay factor applied at each propagation level.
     * At each hop, propagated trust is multiplied by this factor.
     * 0.5 means trust halves at each level: full -> 50% -> 25% -> 12.5% etc.
     * This prevents distant endorsements from having too much influence.
     */
    private static final double DECAY_FACTOR = 0.5;

    /**
     * Propagates an endorsement through the trust network using BFS.
     *
     * When technician A endorses technician B:
     * - B receives the direct endorsement
     * - Anyone B has endorsed receives propagated trust (decayed)
     * - Anyone those people endorsed receives further decayed trust
     * - And so on, up to MAX_PROPAGATION_DEPTH levels
     *
     * Algorithm:
     * 1. Initialize queue with endorsee (B)
     * 2. BFS through outgoing edges (people B endorsed)
     * 3. At each level k, propagate trust = endorserScore * endorsementWeight * (DECAY_FACTOR ^ k)
     * 4. Track all affected nodes and their score deltas
     * 5. Return map for batch update
     *
     * @param graph              The trust graph to propagate through
     * @param endorserId         UUID of the technician giving the endorsement (A)
     * @param endorseeId         UUID of the technician receiving the endorsement (B)
     * @param endorsementWeight  Weight of the endorsement (0-1 scale)
     * @return Map of technician ID to score delta (the amount each technician's score is affected)
     */
    public static Map<UUID, Double> propagateEndorsement(
            TrustGraph graph,
            UUID endorserId,
            UUID endorseeId,
            double endorsementWeight) {

        Map<UUID, Double> scoreDeltas = new HashMap<>();

        // Get the endorser's current score
        double endorserScore = graph.getScore(endorserId);

        // Direct propagation to endorsee
        double directDelta = endorserScore * endorsementWeight;
        scoreDeltas.put(endorseeId, directDelta);

        // BFS-based propagation through the network
        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> distances = new HashMap<>();

        queue.offer(endorseeId);
        distances.put(endorseeId, 0);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentLevel = distances.get(current);

            // Stop if we've reached max depth
            if (currentLevel >= MAX_PROPAGATION_DEPTH) {
                continue;
            }

            // Get all technicians that current endorsee has endorsed (outgoing edges)
            for (TrustGraph.TrustEdge edge : graph.getOutgoingEdges(current)) {
                UUID next = edge.getTargetId();

                // Calculate propagation at this level
                int nextLevel = currentLevel + 1;
                double decayMultiplier = Math.pow(DECAY_FACTOR, nextLevel);
                double propagatedTrust = directDelta * decayMultiplier;

                // Add to score delta (cumulative if already in map)
                scoreDeltas.merge(next, propagatedTrust, Double::sum);

                // Queue for further exploration if not already visited
                if (!distances.containsKey(next)) {
                    distances.put(next, nextLevel);
                    queue.offer(next);
                }
            }
        }

        return scoreDeltas;
    }

    /**
     * Computes trust decay due to inactivity.
     *
     * Technicians lose trust over time if they're not active in the network.
     * This encourages ongoing participation and reduces the score of dormant technicians.
     *
     * Decay Formula:
     * - For daysSinceLastActivity < 30: No decay (grace period)
     * - For daysSinceLastActivity >= 30: decayFactor = e^(-0.01 * (days - 30))
     * - Score reduction = currentScore * (1 - decayFactor)
     *
     * Example:
     * - 30 days inactive: decayFactor = 1.0, reduction = 0
     * - 60 days inactive: decayFactor = e^(-0.3) ≈ 0.741, reduction ≈ 25.9%
     * - 90 days inactive: decayFactor = e^(-0.6) ≈ 0.549, reduction ≈ 45.1%
     * - 180 days inactive: decayFactor = e^(-1.5) ≈ 0.223, reduction ≈ 77.7%
     *
     * This creates a gentle exponential decay rather than linear, allowing
     * technicians to recover their score by becoming active again.
     *
     * @param graph                The trust graph (used for getting current score)
     * @param technicianId         UUID of the technician
     * @param daysSinceLastActivity Number of days since last activity
     * @return The score reduction amount (positive value to subtract from current score)
     */
    public static double computeTrustDecay(
            TrustGraph graph,
            UUID technicianId,
            long daysSinceLastActivity) {

        // Grace period: no decay for first 30 days
        if (daysSinceLastActivity < 30) {
            return 0.0;
        }

        // Get current score
        double currentScore = graph.getScore(technicianId);

        // Exponential decay: e^(-0.01 * days)
        long daysOverGrace = daysSinceLastActivity - 30;
        double decayFactor = Math.exp(-0.01 * daysOverGrace);

        // Score reduction = score * (1 - decayFactor)
        double reduction = currentScore * (1.0 - decayFactor);

        // Cap the reduction to the current score (prevent negative scores)
        return Math.min(reduction, currentScore);
    }

    /**
     * Computes the activity score component for personalization vector.
     *
     * Uses exponential decay based on days since last activity.
     * Recent activity (< 7 days) gets near-maximum score (0.9-1.0).
     * Activity from 30+ days ago gets minimal score (0.1 or less).
     *
     * Formula: activityScore = e^(-0.05 * daysSinceLastActivity)
     *
     * Example:
     * - 0 days (today): score = 1.0
     * - 7 days ago: score ≈ 0.71
     * - 14 days ago: score ≈ 0.50
     * - 30 days ago: score ≈ 0.22
     * - 60 days ago: score ≈ 0.05
     *
     * @param daysSinceLastActivity Number of days since last activity
     * @return Activity score (0-1 scale) for use in personalization vector
     */
    public static double computeActivityScore(long daysSinceLastActivity) {
        if (daysSinceLastActivity < 0) {
            daysSinceLastActivity = 0;
        }
        return Math.exp(-0.05 * daysSinceLastActivity);
    }
}
