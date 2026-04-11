package com.partlinq.core.controller;

import com.partlinq.core.exception.EntityNotFoundException;
import com.partlinq.core.model.dto.PartSearchResponse;
import com.partlinq.core.model.dto.SparePartRequest;
import com.partlinq.core.model.entity.InventoryItem;
import com.partlinq.core.model.entity.SparePart;
import com.partlinq.core.model.enums.ApplianceType;
import com.partlinq.core.model.enums.PartCategory;
import com.partlinq.core.repository.InventoryItemRepository;
import com.partlinq.core.repository.SparePartRepository;
import com.partlinq.core.service.catalog.PartsCatalogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for spare parts catalog.
 * Handles part creation, trie-powered search, fuzzy matching, and autocomplete.
 */
@RestController
@RequestMapping("/v1/parts")
@Tag(name = "Parts", description = "Spare parts catalog, search, and autocomplete")
@RequiredArgsConstructor
@Slf4j
public class PartsController {

    private final SparePartRepository sparePartRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final PartsCatalogService partsCatalogService;

    /**
     * Add a new spare part to the catalog.
     */
    @PostMapping
    @Operation(summary = "Add a spare part", description = "Add a new spare part to the catalog and trie index")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Part added successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<PartSearchResponse> addPart(@Valid @RequestBody SparePartRequest request) {
        log.info("Adding spare part: {} ({})", request.name(), request.partNumber());

        SparePart part = SparePart.builder()
                .partNumber(request.partNumber())
                .name(request.name())
                .description(request.description())
                .category(request.category())
                .applianceType(request.applianceType())
                .brand(request.brand())
                .modelCompatibility(request.modelCompatibility())
                .mrp(request.mrp())
                .isOem(request.isOem())
                .build();

        SparePart saved = partsCatalogService.addPart(part);
        log.info("Part added: {} ({})", saved.getName(), saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(saved));
    }

    /**
     * Get a spare part by ID with availability across shops.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get part by ID", description = "Retrieve a spare part with availability across all shops")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Part found"),
        @ApiResponse(responseCode = "404", description = "Part not found")
    })
    public ResponseEntity<PartSearchResponse> getPartById(
            @Parameter(description = "Part ID") @PathVariable UUID id) {
        SparePart part = sparePartRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SparePart", id));
        return ResponseEntity.ok(mapToResponse(part));
    }

    /**
     * Search parts using trie-powered prefix search.
     * O(k) complexity where k = query length.
     */
    @GetMapping("/search")
    @Operation(summary = "Search parts (trie)", description = "Prefix-based search using trie data structure with O(k) complexity")
    @ApiResponse(responseCode = "200", description = "Search results")
    public ResponseEntity<List<PartSearchResponse>> searchParts(
            @Parameter(description = "Search query") @RequestParam String q,
            @Parameter(description = "Max results") @RequestParam(defaultValue = "20") int limit) {
        log.info("Trie search: query='{}', limit={}", q, limit);

        List<SparePart> results = partsCatalogService.searchParts(q, limit);
        partsCatalogService.recordSearch(q);

        List<PartSearchResponse> responses = results.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Fuzzy search with Levenshtein distance.
     * Handles typos — up to 2 character edits tolerated.
     */
    @GetMapping("/search/fuzzy")
    @Operation(summary = "Fuzzy search parts", description = "Levenshtein distance search — handles misspellings and typos")
    @ApiResponse(responseCode = "200", description = "Fuzzy search results")
    public ResponseEntity<List<PartSearchResponse>> fuzzySearch(
            @Parameter(description = "Search query (with possible typos)") @RequestParam String q,
            @Parameter(description = "Max results") @RequestParam(defaultValue = "20") int limit) {
        log.info("Fuzzy search: query='{}', limit={}", q, limit);

        List<SparePart> results = partsCatalogService.fuzzySearchParts(q, limit);
        List<PartSearchResponse> responses = results.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Autocomplete suggestions for search bar.
     */
    @GetMapping("/autocomplete")
    @Operation(summary = "Autocomplete parts", description = "Get type-ahead suggestions for the search bar")
    @ApiResponse(responseCode = "200", description = "Autocomplete suggestions")
    public ResponseEntity<List<String>> autocomplete(
            @Parameter(description = "Search prefix") @RequestParam String prefix,
            @Parameter(description = "Max suggestions") @RequestParam(defaultValue = "10") int limit) {
        List<String> suggestions = partsCatalogService.autocompleteParts(prefix, limit);
        return ResponseEntity.ok(suggestions);
    }

    /**
     * Filter parts by appliance type and/or category.
     */
    @GetMapping("/filter")
    @Operation(summary = "Filter parts", description = "Filter parts by appliance type and/or part category")
    @ApiResponse(responseCode = "200", description = "Filtered parts")
    public ResponseEntity<List<PartSearchResponse>> filterParts(
            @Parameter(description = "Appliance type") @RequestParam(required = false) ApplianceType applianceType,
            @Parameter(description = "Part category") @RequestParam(required = false) PartCategory category,
            @Parameter(description = "Brand") @RequestParam(required = false) String brand) {

        List<SparePart> parts;

        if (applianceType != null && category != null) {
            parts = sparePartRepository.findByApplianceTypeAndCategory(applianceType, category);
        } else if (applianceType != null) {
            parts = sparePartRepository.findByApplianceType(applianceType);
        } else if (category != null) {
            parts = sparePartRepository.findByCategory(category);
        } else if (brand != null && !brand.isBlank()) {
            parts = sparePartRepository.findByBrandIgnoreCase(brand);
        } else {
            parts = sparePartRepository.findAll();
        }

        List<PartSearchResponse> responses = parts.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Delete a spare part from the catalog.
     */
    @DeleteMapping("/{id}")
    @Operation(summary = "Remove a spare part", description = "Remove a part from catalog and trie index")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "Part removed"),
        @ApiResponse(responseCode = "404", description = "Part not found")
    })
    public ResponseEntity<Void> removePart(
            @Parameter(description = "Part ID") @PathVariable UUID id) {
        sparePartRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("SparePart", id));
        partsCatalogService.removePart(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Map SparePart entity to PartSearchResponse with shop availability.
     */
    private PartSearchResponse mapToResponse(SparePart part) {
        List<InventoryItem> inventoryItems =
                inventoryItemRepository.findAvailableBySparePartIdSortedByPrice(part.getId());

        List<PartSearchResponse.ShopAvailability> availability = inventoryItems.stream()
                .map(inv -> new PartSearchResponse.ShopAvailability(
                        inv.getShop().getId(),
                        inv.getShop().getShopName(),
                        inv.getShop().getCity(),
                        inv.getQuantity(),
                        inv.getSellingPrice(),
                        inv.getIsAvailable()
                ))
                .collect(Collectors.toList());

        return new PartSearchResponse(
                part.getId(),
                part.getPartNumber(),
                part.getName(),
                part.getDescription(),
                part.getCategory(),
                part.getApplianceType(),
                part.getBrand(),
                part.getModelCompatibility(),
                part.getMrp(),
                part.getIsOem(),
                part.getCreatedAt(),
                availability
        );
    }
}
