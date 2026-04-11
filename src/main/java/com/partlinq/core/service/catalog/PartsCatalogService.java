package com.partlinq.core.service.catalog;

import com.partlinq.core.engine.trie.PartsTrie;
import com.partlinq.core.model.entity.SparePart;
import com.partlinq.core.repository.SparePartRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring service that wraps the PartsTrie with JPA persistence.
 * Provides search, filtering, and catalog management for spare parts.
 *
 * The in-memory Trie index is initialized from the database on startup
 * and provides O(k) prefix search, fuzzy matching, and autocomplete.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PartsCatalogService {

    private final SparePartRepository sparePartRepository;
    private final PartsTrie partsTrie = new PartsTrie();
    private volatile boolean initialized = false;

    /**
     * Initialize the catalog by loading all SpareParts from the database
     * and inserting them into the trie by name, part number, and brand.
     * Runs once at application startup via @PostConstruct.
     */
    //@PostConstruct
    public void initializeCatalog() {
        if (initialized) {
            return;
        }

        synchronized (this) {
            if (initialized) {
                return;
            }

            log.info("Initializing parts catalog trie...");
            partsTrie.clear();

            List<SparePart> allParts = sparePartRepository.findAll();
            int count = 0;

            for (SparePart part : allParts) {
                // Index by part name
                partsTrie.insert(part.getName(), part.getId());

                // Index by part number
                partsTrie.insert(part.getPartNumber(), part.getId());

                // Index by brand
                partsTrie.insert(part.getBrand(), part.getId());

                count++;
            }

            initialized = true;
            log.info("Catalog initialization complete. Indexed {} parts across name, part number, and brand.",
                count);
        }
    }

    /**
     * Ensure catalog is initialized before any search operation.
     * Called automatically by search methods.
     */
    private void ensureInitialized() {
        if (!initialized) {
            initializeCatalog();
        }
    }

    /**
     * Search for parts by prefix/query string.
     * Returns full SparePart entities sorted by search results.
     *
     * @param query       Search query (part name, part number, or brand)
     * @param maxResults  Maximum number of results to return
     * @return List of matching SparePart entities
     */
    @Cacheable(value = "partSearch", key = "'prefix:' + #query + ':' + #maxResults")
    public List<SparePart> searchParts(String query, int maxResults) {
        ensureInitialized();

        List<UUID> partIds = partsTrie.search(query, maxResults);
        return partIds.stream()
            .map(sparePartRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    /**
     * Fuzzy search for parts with Levenshtein distance matching.
     * Handles typos and partial matches.
     *
     * @param query       Search query with possible typos
     * @param maxResults  Maximum number of results to return
     * @return List of matching SparePart entities
     */
    @Cacheable(value = "partSearch", key = "'fuzzy:' + #query + ':' + #maxResults")
    public List<SparePart> fuzzySearchParts(String query, int maxResults) {
        ensureInitialized();

        // Allow up to 2 character edits for fuzzy matching
        List<UUID> partIds = partsTrie.fuzzySearch(query, 2, maxResults);
        return partIds.stream()
            .map(sparePartRepository::findById)
            .filter(Optional::isPresent)
            .map(Optional::get)
            .collect(Collectors.toList());
    }

    /**
     * Get autocomplete suggestions for a search prefix.
     * Useful for search bar dropdown suggestions.
     *
     * @param prefix            Search prefix
     * @param maxSuggestions    Maximum number of suggestions
     * @return List of suggested search terms
     */
    @Cacheable(value = "partAutocomplete", key = "'suggest:' + #prefix + ':' + #maxSuggestions")
    public List<String> autocompleteParts(String prefix, int maxSuggestions) {
        ensureInitialized();
        return partsTrie.autocomplete(prefix, maxSuggestions);
    }

    /**
     * Add a new SparePart to the database and catalog.
     * Inserts into database first, then updates the trie.
     *
     * @param part SparePart entity to add
     * @return Saved SparePart with generated UUID
     */
    public SparePart addPart(SparePart part) {
        ensureInitialized();

        // Save to database
        SparePart saved = sparePartRepository.save(part);
        log.info("Added spare part: {} ({})", saved.getName(), saved.getId());

        // Update trie
        partsTrie.insert(saved.getName(), saved.getId());
        partsTrie.insert(saved.getPartNumber(), saved.getId());
        partsTrie.insert(saved.getBrand(), saved.getId());

        return saved;
    }

    /**
     * Remove a SparePart from the database and catalog.
     * Removes from trie and database.
     *
     * @param partId UUID of the part to remove
     */
    public void removePart(UUID partId) {
        ensureInitialized();

        Optional<SparePart> partOpt = sparePartRepository.findById(partId);
        if (partOpt.isPresent()) {
            SparePart part = partOpt.get();

            // Remove from trie
            partsTrie.remove(part.getName(), partId);
            partsTrie.remove(part.getPartNumber(), partId);
            partsTrie.remove(part.getBrand(), partId);

            // Remove from database
            sparePartRepository.delete(part);
            log.info("Removed spare part: {} ({})", part.getName(), partId);
        }
    }

    /**
     * Record a search query for popularity ranking.
     * Increments the frequency counter for the searched prefix.
     *
     * @param query Search query that was performed
     */
    public void recordSearch(String query) {
        partsTrie.incrementFrequency(query);
    }

    /**
     * Get the total number of parts in the catalog.
     *
     * @return Total part count in trie
     */
    public int getCatalogSize() {
        ensureInitialized();
        return partsTrie.size();
    }

    /**
     * Clear the catalog (for testing or reset).
     */
    public void clear() {
        partsTrie.clear();
        initialized = false;
    }
}
