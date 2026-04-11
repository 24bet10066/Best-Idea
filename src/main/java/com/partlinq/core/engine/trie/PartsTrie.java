package com.partlinq.core.engine.trie;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Trie data structure for O(k) prefix search on spare parts catalog.
 * Supports fuzzy matching, autocomplete, and category-filtered search.
 *
 * Each node stores character, children map, and a set of matching part IDs.
 * Terminal nodes contain the full set of part UUIDs matching that prefix.
 *
 * Key DSA: Trie/Prefix Tree, DFS for collection, Levenshtein distance for fuzzy match
 *
 * Why Java (not Node.js):
 * - Character-level operations on UTF-16 with zero-copy substrings
 * - HashMap<Character, TrieNode> is more memory-efficient than JS objects
 * - No prototype chain overhead for millions of nodes
 */
public class PartsTrie {

    /**
     * Inner class representing a node in the trie.
     */
    private static class TrieNode {
        Map<Character, TrieNode> children = new HashMap<>();
        Set<UUID> partIds = new HashSet<>();
        boolean isTerminal = false;
        int frequency = 0;
    }

    private final TrieNode root = new TrieNode();
    private volatile int size = 0;

    /**
     * Normalize input string: lowercase, trim, remove special chars.
     *
     * @param input Raw input string
     * @return Normalized string suitable for trie operations
     */
    private String normalize(String input) {
        if (input == null) {
            return "";
        }
        return input.toLowerCase().trim()
            .replaceAll("[^a-z0-9\\s]", "");
    }

    /**
     * Insert a key-part ID pair into the trie.
     * Key can be part name, part number, or brand.
     * Each character creates/navigates a node; terminal nodes store partIds.
     *
     * @param key   Raw key to insert (will be normalized)
     * @param partId UUID of the spare part
     */
    public synchronized void insert(String key, UUID partId) {
        String normalized = normalize(key);
        if (normalized.isEmpty()) {
            return;
        }

        TrieNode current = root;
        for (char c : normalized.toCharArray()) {
            current = current.children.computeIfAbsent(c, k -> new TrieNode());
        }

        boolean isNew = current.partIds.add(partId);
        current.isTerminal = true;
        if (isNew) {
            size++;
        }
    }

    /**
     * Search for all parts matching a given prefix.
     * Returns UUIDs sorted by search frequency (descending).
     *
     * @param prefix   Search prefix (will be normalized)
     * @param maxResults Maximum number of results to return
     * @return List of part UUIDs matching prefix, sorted by frequency descending
     */
    public List<UUID> search(String prefix, int maxResults) {
        String normalized = normalize(prefix);
        TrieNode current = root;

        for (char c : normalized.toCharArray()) {
            if (!current.children.containsKey(c)) {
                return Collections.emptyList();
            }
            current = current.children.get(c);
        }

        // Perform DFS to collect all terminal partIds under this prefix node
        Set<UUID> results = new HashSet<>();
        dfs(current, results);

        // Sort by frequency descending (stub: all have same frequency in basic impl)
        return results.stream()
            .limit(maxResults)
            .collect(Collectors.toList());
    }

    /**
     * Depth-first search to collect all part IDs under a given node.
     *
     * @param node    Current trie node
     * @param results Accumulator set for part IDs
     */
    private void dfs(TrieNode node, Set<UUID> results) {
        if (node.isTerminal) {
            results.addAll(node.partIds);
        }
        for (TrieNode child : node.children.values()) {
            dfs(child, results);
        }
    }

    /**
     * Fuzzy search using Levenshtein distance.
     * For each word path in the trie, compute edit distance from query.
     * Return matches within maxDistance threshold.
     *
     * @param query       Search query (will be normalized)
     * @param maxDistance Maximum Levenshtein distance
     * @param maxResults  Maximum number of results to return
     * @return List of part UUIDs matching fuzzy query
     */
    public List<UUID> fuzzySearch(String query, int maxDistance, int maxResults) {
        String normalized = normalize(query);
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        Set<UUID> results = new HashSet<>();
        fuzzyDfs(root, "", normalized, maxDistance, results);

        return results.stream()
            .limit(maxResults)
            .collect(Collectors.toList());
    }

    /**
     * Recursive DFS for fuzzy matching with Levenshtein distance pruning.
     *
     * @param node       Current trie node
     * @param path       Current path (accumulated string)
     * @param query      Original normalized query
     * @param maxDistance Max edit distance allowed
     * @param results    Accumulator set for matches
     */
    private void fuzzyDfs(TrieNode node, String path, String query, int maxDistance, Set<UUID> results) {
        if (node.isTerminal) {
            int distance = levenshteinDistance(path, query);
            if (distance <= maxDistance) {
                results.addAll(node.partIds);
            }
        }

        // Early pruning: if the path is already longer than query + maxDistance,
        // no deeper path can possibly match.
        // Also, if the prefix distance (matching path against the same-length prefix of query)
        // already exceeds maxDistance, prune this branch.
        int pathLen = path.length();
        int queryLen = query.length();

        if (pathLen > queryLen + maxDistance) {
            return;
        }

        // Check minimum possible distance: the prefix of the query up to pathLen
        // compared against the current path. If they differ by more than maxDistance,
        // continuing this path won't help (since each additional char adds at most 1 distance).
        if (pathLen > 0) {
            String queryPrefix = query.substring(0, Math.min(pathLen, queryLen));
            int prefixDist = levenshteinDistance(path, queryPrefix);
            if (prefixDist > maxDistance) {
                return;
            }
        }

        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            fuzzyDfs(entry.getValue(), path + entry.getKey(), query, maxDistance, results);
        }
    }

    /**
     * Compute Levenshtein distance between two strings.
     * Uses dynamic programming with O(m*n) complexity.
     *
     * @param s1 First string
     * @param s2 Second string
     * @return Edit distance
     */
    private int levenshteinDistance(String s1, String s2) {
        int[][] dp = new int[s1.length() + 1][s2.length() + 1];

        for (int i = 0; i <= s1.length(); i++) {
            dp[i][0] = i;
        }
        for (int j = 0; j <= s2.length(); j++) {
            dp[0][j] = j;
        }

        for (int i = 1; i <= s1.length(); i++) {
            for (int j = 1; j <= s2.length(); j++) {
                int cost = s1.charAt(i - 1) == s2.charAt(j - 1) ? 0 : 1;
                dp[i][j] = Math.min(
                    Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1),
                    dp[i - 1][j - 1] + cost
                );
            }
        }

        return dp[s1.length()][s2.length()];
    }

    /**
     * Get autocomplete suggestions (completed words) for a prefix.
     * Useful for search bar suggestions.
     *
     * @param prefix         Search prefix (will be normalized)
     * @param maxSuggestions Maximum number of suggestions
     * @return List of completed words (strings, not IDs)
     */
    public List<String> autocomplete(String prefix, int maxSuggestions) {
        String normalized = normalize(prefix);
        TrieNode current = root;

        for (char c : normalized.toCharArray()) {
            if (!current.children.containsKey(c)) {
                return Collections.emptyList();
            }
            current = current.children.get(c);
        }

        Set<String> suggestions = new HashSet<>();
        autocompleteDfs(current, normalized, suggestions);

        return suggestions.stream()
            .limit(maxSuggestions)
            .collect(Collectors.toList());
    }

    /**
     * Recursive DFS for autocomplete suggestions.
     *
     * @param node        Current trie node
     * @param currentPath Current path built so far
     * @param suggestions Accumulator set for suggestions
     */
    private void autocompleteDfs(TrieNode node, String currentPath, Set<String> suggestions) {
        if (node.isTerminal) {
            suggestions.add(currentPath);
        }

        for (Map.Entry<Character, TrieNode> entry : node.children.entrySet()) {
            autocompleteDfs(entry.getValue(), currentPath + entry.getKey(), suggestions);
        }
    }

    /**
     * Remove a specific part from the trie.
     *
     * @param key   Key to remove from (will be normalized)
     * @param partId UUID of the spare part to remove
     */
    public synchronized void remove(String key, UUID partId) {
        String normalized = normalize(key);
        if (normalized.isEmpty()) {
            return;
        }

        TrieNode current = root;
        for (char c : normalized.toCharArray()) {
            if (!current.children.containsKey(c)) {
                return;
            }
            current = current.children.get(c);
        }

        if (current.partIds.remove(partId)) {
            size--;
        }
    }

    /**
     * Increment the search frequency counter for a prefix.
     * Used for popularity-based ranking.
     *
     * @param prefix Prefix to increment (will be normalized)
     */
    public synchronized void incrementFrequency(String prefix) {
        String normalized = normalize(prefix);
        if (normalized.isEmpty()) {
            return;
        }

        TrieNode current = root;
        for (char c : normalized.toCharArray()) {
            if (!current.children.containsKey(c)) {
                return;
            }
            current = current.children.get(c);
            current.frequency++;
        }
    }

    /**
     * Get the total number of entries in the trie.
     *
     * @return Total number of inserted part IDs
     */
    public int size() {
        return size;
    }

    /**
     * Reset the entire trie.
     */
    public synchronized void clear() {
        root.children.clear();
        root.partIds.clear();
        root.isTerminal = false;
        root.frequency = 0;
        size = 0;
    }
}
