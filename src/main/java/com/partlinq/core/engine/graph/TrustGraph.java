package com.partlinq.core.engine.graph;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Weighted directed graph representing trust relationships between technicians.
 * Uses adjacency list representation with ConcurrentHashMap for thread safety.
 *
 * This is a pure Data Structure implementation (no Spring dependencies).
 * It maintains the trust network as an in-memory graph with weighted directed edges
 * representing endorsements between technicians.
 *
 * Key DSA: Adjacency List, Weighted Edges, Thread-Safe Graph Operations
 *
 * Thread-safety: All operations use ConcurrentHashMap and synchronization where needed
 * to support concurrent reads and writes from multiple threads.
 */
public class TrustGraph {

    /**
     * Internal class representing a weighted directed edge in the graph.
     * Stores the target technician ID, weight (strength of endorsement), and timestamp.
     */
    static class TrustEdge {
        private final UUID targetId;
        private double weight;
        private final long timestamp;

        /**
         * Creates a new trust edge.
         *
         * @param targetId ID of the technician being endorsed
         * @param weight   Weight/strength of the endorsement (0-1 scale)
         * @param timestamp When the endorsement was created (epoch milliseconds)
         */
        TrustEdge(UUID targetId, double weight, long timestamp) {
            this.targetId = targetId;
            this.weight = weight;
            this.timestamp = timestamp;
        }

        public UUID getTargetId() {
            return targetId;
        }

        public double getWeight() {
            return weight;
        }

        public void setWeight(double weight) {
            this.weight = weight;
        }

        public long getTimestamp() {
            return timestamp;
        }
    }

    // Adjacency list: technicianId -> list of outgoing endorsement edges
    private final ConcurrentHashMap<UUID, List<TrustEdge>> adjacencyList;

    // Reverse adjacency list: technicianId -> list of incoming endorsement edges
    private final ConcurrentHashMap<UUID, List<TrustEdge>> reverseAdjacencyList;

    // Trust scores: technicianId -> current trust score (0-100)
    private final ConcurrentHashMap<UUID, Double> nodeScores;

    /**
     * Creates a new empty TrustGraph.
     */
    public TrustGraph() {
        this.adjacencyList = new ConcurrentHashMap<>();
        this.reverseAdjacencyList = new ConcurrentHashMap<>();
        this.nodeScores = new ConcurrentHashMap<>();
    }

    /**
     * Adds a node (technician) to the graph with an initial trust score.
     * If the node already exists, the initial score is ignored.
     *
     * @param technicianId The ID of the technician to add
     * @param initialScore The initial trust score (typically 0-100 range)
     */
    public void addNode(UUID technicianId, double initialScore) {
        nodeScores.putIfAbsent(technicianId, initialScore);
        adjacencyList.putIfAbsent(technicianId, Collections.synchronizedList(new ArrayList<>()));
        reverseAdjacencyList.putIfAbsent(technicianId, Collections.synchronizedList(new ArrayList<>()));
    }

    /**
     * Adds a weighted directed edge from one technician to another.
     * This represents an endorsement from the 'from' technician to the 'to' technician.
     *
     * If an edge already exists, the weight is updated.
     * Both nodes are automatically added if they don't exist.
     *
     * @param from   ID of the endorsing technician
     * @param to     ID of the endorsed technician
     * @param weight Weight of the endorsement (0-1 scale, where 1 is strongest)
     */
    public void addEdge(UUID from, UUID to, double weight) {
        // Ensure both nodes exist
        addNode(from, 50.0);
        addNode(to, 50.0);

        // Get or create the adjacency lists for outgoing and incoming edges
        List<TrustEdge> outgoing = adjacencyList.get(from);
        List<TrustEdge> incoming = reverseAdjacencyList.get(to);

        // Synchronized access to check and update edges
        synchronized (outgoing) {
            // Check if edge already exists
            TrustEdge existingEdge = outgoing.stream()
                    .filter(edge -> edge.getTargetId().equals(to))
                    .findFirst()
                    .orElse(null);

            if (existingEdge != null) {
                existingEdge.setWeight(weight);
            } else {
                outgoing.add(new TrustEdge(to, weight, System.currentTimeMillis()));
            }
        }

        // Add to reverse adjacency list
        synchronized (incoming) {
            TrustEdge existingReverseEdge = incoming.stream()
                    .filter(edge -> edge.getTargetId().equals(from))
                    .findFirst()
                    .orElse(null);

            if (existingReverseEdge != null) {
                existingReverseEdge.setWeight(weight);
            } else {
                incoming.add(new TrustEdge(from, weight, System.currentTimeMillis()));
            }
        }
    }

    /**
     * Removes a directed edge from one technician to another.
     *
     * @param from ID of the endorsing technician
     * @param to   ID of the endorsed technician
     * @return true if an edge was removed, false if no edge existed
     */
    public boolean removeEdge(UUID from, UUID to) {
        List<TrustEdge> outgoing = adjacencyList.getOrDefault(from, Collections.emptyList());
        List<TrustEdge> incoming = reverseAdjacencyList.getOrDefault(to, Collections.emptyList());

        boolean removed = false;

        synchronized (outgoing) {
            removed = outgoing.removeIf(edge -> edge.getTargetId().equals(to));
        }

        synchronized (incoming) {
            incoming.removeIf(edge -> edge.getTargetId().equals(from));
        }

        return removed;
    }

    /**
     * Gets all incoming endorsement edges for a technician.
     * These represent endorsements that the technician has received.
     *
     * @param nodeId ID of the technician
     * @return List of incoming edges (read-only copy)
     */
    public List<TrustEdge> getIncomingEdges(UUID nodeId) {
        List<TrustEdge> edges = reverseAdjacencyList.getOrDefault(nodeId, Collections.emptyList());
        return new ArrayList<>(edges);
    }

    /**
     * Gets all outgoing endorsement edges for a technician.
     * These represent endorsements that the technician has given.
     *
     * @param nodeId ID of the technician
     * @return List of outgoing edges (read-only copy)
     */
    public List<TrustEdge> getOutgoingEdges(UUID nodeId) {
        List<TrustEdge> edges = adjacencyList.getOrDefault(nodeId, Collections.emptyList());
        return new ArrayList<>(edges);
    }

    /**
     * Gets the in-degree + out-degree of a node (total endorsements involved).
     *
     * @param nodeId ID of the technician
     * @return Total number of connected endorsements (both incoming and outgoing)
     */
    public int getNeighborCount(UUID nodeId) {
        int inDegree = getIncomingEdges(nodeId).size();
        int outDegree = getOutgoingEdges(nodeId).size();
        return inDegree + outDegree;
    }

    /**
     * Gets the current trust score for a technician.
     *
     * @param nodeId ID of the technician
     * @return Current trust score, or 0.0 if node doesn't exist
     */
    public double getScore(UUID nodeId) {
        return nodeScores.getOrDefault(nodeId, 0.0);
    }

    /**
     * Sets the trust score for a technician.
     *
     * @param nodeId ID of the technician
     * @param score  The new trust score
     */
    public void setScore(UUID nodeId, double score) {
        nodeScores.put(nodeId, score);
    }

    /**
     * Gets all technician IDs currently in the graph.
     *
     * @return Set of all technician UUIDs in the graph
     */
    public Set<UUID> getAllNodeIds() {
        return new HashSet<>(nodeScores.keySet());
    }

    /**
     * Gets the total number of nodes (technicians) in the graph.
     *
     * @return Number of technicians in the graph
     */
    public int getNodeCount() {
        return nodeScores.size();
    }

    /**
     * Gets the total number of edges (endorsements) in the graph.
     *
     * @return Total count of directed edges
     */
    public int getEdgeCount() {
        return adjacencyList.values().stream()
                .mapToInt(List::size)
                .sum();
    }

    /**
     * Extracts a subgraph centered around a specific technician using BFS.
     * Useful for network visualization and analysis.
     *
     * This performs a breadth-first search starting from the center node
     * and includes all nodes within the specified depth (measured in hops).
     *
     * @param centerId ID of the center technician
     * @param depth    Maximum depth to traverse (number of hops)
     * @return A new TrustGraph containing the subgraph with only reachable nodes and edges
     */
    public TrustGraph getSubgraph(UUID centerId, int depth) {
        TrustGraph subgraph = new TrustGraph();

        if (!nodeScores.containsKey(centerId)) {
            return subgraph;
        }

        // BFS to find all nodes within depth
        Queue<UUID> queue = new LinkedList<>();
        Map<UUID, Integer> distances = new HashMap<>();
        queue.offer(centerId);
        distances.put(centerId, 0);

        while (!queue.isEmpty()) {
            UUID current = queue.poll();
            int currentDistance = distances.get(current);

            if (currentDistance > depth) {
                continue;
            }

            // Add current node to subgraph
            subgraph.addNode(current, this.getScore(current));

            // Explore outgoing edges
            if (currentDistance < depth) {
                for (TrustEdge edge : this.getOutgoingEdges(current)) {
                    UUID target = edge.getTargetId();

                    if (!distances.containsKey(target)) {
                        distances.put(target, currentDistance + 1);
                        queue.offer(target);
                    }

                    subgraph.addNode(target, this.getScore(target));
                    subgraph.addEdge(current, target, edge.getWeight());
                }
            }
        }

        return subgraph;
    }

    /**
     * Gets the sum of outgoing edge weights from a node.
     * Used in PageRank calculations to normalize edge weights.
     *
     * @param nodeId ID of the technician
     * @return Sum of all outgoing edge weights
     */
    double getOutWeightSum(UUID nodeId) {
        return getOutgoingEdges(nodeId).stream()
                .mapToDouble(TrustEdge::getWeight)
                .sum();
    }

    /**
     * Returns a string representation of the graph statistics.
     *
     * @return String with node count, edge count, and density
     */
    @Override
    public String toString() {
        int nodes = getNodeCount();
        int edges = getEdgeCount();
        double density = nodes > 0 ? (2.0 * edges) / (nodes * (nodes - 1)) : 0;
        return String.format("TrustGraph{nodes=%d, edges=%d, density=%.4f}", nodes, edges, density);
    }
}
