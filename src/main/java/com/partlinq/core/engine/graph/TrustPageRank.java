package com.partlinq.core.engine.graph;

import java.util.*;

/**
 * Modified PageRank algorithm for computing trust scores in the technician network.
 *
 * Unlike standard PageRank which distributes rank equally to all outgoing links,
 * this variant uses weighted edges (endorsement strength) and incorporates
 * behavioral signals (payment history, feedback) as personalization vectors.
 *
 * The algorithm computes the steady-state distribution of trust through
 * iterative power iteration, where each iteration updates scores based on:
 * - Incoming weighted endorsements from other technicians
 * - Personalization vector (behavioral metrics like payment history)
 * - Damping factor to prevent cyclic dependencies
 *
 * Key DSA: PageRank with Personalization Vector, Power Iteration, Convergence Detection
 *
 * Why Java (not Node.js):
 * - Millions of floating-point operations per iteration requiring high precision
 * - Memory-efficient double[] arrays vs JS Number objects
 * - ConcurrentHashMap for parallel score updates without GC pauses
 * - Deterministic floating-point behavior critical for financial scoring
 */
public class TrustPageRank {

    /**
     * Damping factor for PageRank. Represents the probability that a technician
     * will be endorsed by a random other technician (not following a specific link).
     * Standard PageRank uses 0.85. This value balances bias toward high-degree nodes.
     */
    private static final double DAMPING_FACTOR = 0.85;

    /**
     * Maximum number of iterations for the power iteration algorithm.
     * Prevents infinite loops in case convergence is not achieved.
     */
    private static final int MAX_ITERATIONS = 100;

    /**
     * Convergence threshold for the power iteration algorithm.
     * Algorithm stops when max score change is less than this value.
     * Set to 1e-6 for high precision suitable for financial scoring.
     */
    private static final double CONVERGENCE_THRESHOLD = 1e-6;

    /**
     * Computes trust scores for all technicians in the graph using modified PageRank.
     *
     * Algorithm:
     * 1. Initialize scores to 1/N (where N = number of nodes)
     * 2. For each iteration:
     *    - For each node v:
     *      - newScore[v] = (1 - d) * personalization[v]
     *                      + d * SUM(weight[u->v] / outWeightSum[u] * oldScore[u])
     *                      for all nodes u with an edge to v
     * 3. Check convergence: if max|newScore[v] - oldScore[v]| < threshold, stop
     * 4. Return final scores
     *
     * @param graph                  The trust graph to compute scores for
     * @param personalizationVector  Map of technician ID to personalization value (0-1)
     * @return Map of technician ID to final computed trust score (scaled to 0-100)
     * @throws IllegalArgumentException if graph is empty or personalization vector is invalid
     */
    public Map<UUID, Double> computeScores(
            TrustGraph graph,
            Map<UUID, Double> personalizationVector) {

        if (graph.getNodeCount() == 0) {
            throw new IllegalArgumentException("Cannot compute PageRank on empty graph");
        }

        Set<UUID> allNodes = graph.getAllNodeIds();
        int nodeCount = allNodes.size();

        // Initialize scores uniformly
        double[] oldScores = new double[nodeCount];
        double[] newScores = new double[nodeCount];
        double initialScore = 1.0 / nodeCount;
        Arrays.fill(oldScores, initialScore);

        // Create mapping from UUID to array index for efficient access
        UUID[] nodeArray = allNodes.toArray(new UUID[0]);
        Map<UUID, Integer> nodeIndex = new HashMap<>();
        for (int i = 0; i < nodeArray.length; i++) {
            nodeIndex.put(nodeArray[i], i);
        }

        // Validate and prepare personalization vector
        Map<UUID, Double> normPersonalization = normalizePersonalization(
                personalizationVector,
                allNodes
        );

        // Power iteration algorithm
        int iterations = 0;
        double maxDelta;

        do {
            iterations++;
            Arrays.fill(newScores, 0.0);

            // For each node, compute its new score
            for (UUID v : allNodes) {
                int vIndex = nodeIndex.get(v);
                double personalScore = normPersonalization.getOrDefault(v, 1.0 / nodeCount);

                // (1 - damping) * personalization component
                newScores[vIndex] = (1.0 - DAMPING_FACTOR) * personalScore;

                // Damping component: sum contributions from incoming edges
                double incomingScore = 0.0;
                for (TrustGraph.TrustEdge edge : graph.getIncomingEdges(v)) {
                    UUID u = edge.getTargetId();  // Note: reverse edge, so targetId is source
                    int uIndex = nodeIndex.get(u);
                    double weight = edge.getWeight();
                    double outWeightSum = graph.getOutWeightSum(u);

                    // Avoid division by zero for nodes with no outgoing edges
                    if (outWeightSum > 0) {
                        incomingScore += (weight / outWeightSum) * oldScores[uIndex];
                    }
                }

                newScores[vIndex] += DAMPING_FACTOR * incomingScore;
            }

            // Check convergence
            maxDelta = 0.0;
            for (int i = 0; i < nodeCount; i++) {
                maxDelta = Math.max(maxDelta, Math.abs(newScores[i] - oldScores[i]));
            }

            // Swap arrays
            double[] temp = oldScores;
            oldScores = newScores;
            newScores = temp;

        } while (maxDelta >= CONVERGENCE_THRESHOLD && iterations < MAX_ITERATIONS);

        // Convert scores to 0-100 scale
        Map<UUID, Double> result = new HashMap<>();
        for (int i = 0; i < nodeArray.length; i++) {
            // Scale from 0-1 to 0-100
            double scaledScore = oldScores[i] * 100.0;
            result.put(nodeArray[i], scaledScore);
        }

        return result;
    }

    /**
     * Computes a personalization vector from behavioral metrics for a single technician.
     *
     * The personalization vector biases the PageRank algorithm toward
     * rewarding technicians with good payment history, high feedback ratings,
     * and recent activity.
     *
     * Formula:
     * personalization = 0.4 * paymentScore + 0.35 * feedbackScore + 0.25 * activityScore
     *
     * Weights justify:
     * - Payment (40%): Most important, indicates reliability
     * - Feedback (35%): Quality of work matters significantly
     * - Activity (25%): Recent engagement is also important
     *
     * @param technicianId  The technician ID (for reference only)
     * @param paymentScore  Payment on-time ratio (0-1 scale)
     * @param feedbackScore Average customer rating / 5.0 (0-1 scale)
     * @param activityScore Recency score with exponential decay (0-1 scale)
     * @return Personalization value (0-1 scale) for use in PageRank
     */
    public static double computePersonalizationVector(
            UUID technicianId,
            double paymentScore,
            double feedbackScore,
            double activityScore) {

        // Clamp all inputs to valid range
        double payment = Math.max(0.0, Math.min(1.0, paymentScore));
        double feedback = Math.max(0.0, Math.min(1.0, feedbackScore));
        double activity = Math.max(0.0, Math.min(1.0, activityScore));

        // Weighted combination
        return 0.4 * payment + 0.35 * feedback + 0.25 * activity;
    }

    /**
     * Normalizes a personalization vector to ensure valid probability distribution.
     *
     * If personalization vector is empty or null, creates uniform distribution.
     * Otherwise, sums all values and returns map for efficient lookup.
     *
     * @param personalization Map of technician ID to personalization value
     * @param allNodes        Set of all node IDs in the graph
     * @return Normalized personalization vector with value for each node
     */
    private static Map<UUID, Double> normalizePersonalization(
            Map<UUID, Double> personalization,
            Set<UUID> allNodes) {

        Map<UUID, Double> normalized = new HashMap<>();

        if (personalization == null || personalization.isEmpty()) {
            // Uniform distribution
            double uniform = 1.0 / allNodes.size();
            for (UUID node : allNodes) {
                normalized.put(node, uniform);
            }
            return normalized;
        }

        // Calculate sum of personalization values
        double sum = personalization.values().stream()
                .mapToDouble(Double::doubleValue)
                .sum();

        // Normalize and add missing nodes
        double defaultValue = 1.0 / allNodes.size();

        for (UUID node : allNodes) {
            if (personalization.containsKey(node) && sum > 0) {
                normalized.put(node, personalization.get(node) / sum);
            } else {
                normalized.put(node, defaultValue);
            }
        }

        return normalized;
    }
}
