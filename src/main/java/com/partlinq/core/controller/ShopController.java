package com.partlinq.core.controller;

import com.partlinq.core.exception.EntityNotFoundException;
import com.partlinq.core.model.dto.*;
import com.partlinq.core.model.entity.InventoryItem;
import com.partlinq.core.model.entity.PartsShop;
import com.partlinq.core.model.entity.SparePart;
import com.partlinq.core.repository.InventoryItemRepository;
import com.partlinq.core.repository.PartsShopRepository;
import com.partlinq.core.repository.SparePartRepository;
import com.partlinq.core.service.dashboard.DashboardService;
import com.partlinq.core.service.inventory.InventoryService;
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

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * REST controller for shop management.
 * Handles shop registration, inventory management, and dashboard retrieval.
 */
@RestController
@RequestMapping("/v1/shops")
@Tag(name = "Shops", description = "Parts shop registration, inventory, and dashboard")
@RequiredArgsConstructor
@Slf4j
public class ShopController {

    private final PartsShopRepository partsShopRepository;
    private final SparePartRepository sparePartRepository;
    private final InventoryItemRepository inventoryItemRepository;
    private final InventoryService inventoryService;
    private final DashboardService dashboardService;

    /**
     * Register a new parts shop.
     */
    @PostMapping
    @Operation(summary = "Register a new shop", description = "Create a new parts shop profile with GST and contact details")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Shop registered successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data")
    })
    public ResponseEntity<ShopResponse> registerShop(@Valid @RequestBody ShopRegistrationRequest request) {
        log.info("Registering shop: {}", request.shopName());

        PartsShop shop = PartsShop.builder()
                .shopName(request.shopName())
                .ownerName(request.ownerName())
                .phone(request.phone())
                .email(request.email())
                .address(request.address())
                .city(request.city())
                .pincode(request.pincode())
                .gstNumber(request.gstNumber())
                .isVerified(false)
                .rating(0.0)
                .totalOrdersServed(0)
                .build();

        PartsShop saved = partsShopRepository.save(shop);
        log.info("Shop registered: {}", saved.getId());
        return ResponseEntity.status(HttpStatus.CREATED).body(mapToResponse(saved));
    }

    /**
     * Get shop by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get shop by ID", description = "Retrieve a shop's profile and operational details")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shop found"),
        @ApiResponse(responseCode = "404", description = "Shop not found")
    })
    public ResponseEntity<ShopResponse> getShopById(
            @Parameter(description = "Shop ID") @PathVariable UUID id) {
        PartsShop shop = partsShopRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Shop", id));
        return ResponseEntity.ok(mapToResponse(shop));
    }

    /**
     * List all shops with optional city filter.
     */
    @GetMapping
    @Operation(summary = "List all shops", description = "Get all shops with optional city filter")
    @ApiResponse(responseCode = "200", description = "List of shops")
    public ResponseEntity<List<ShopResponse>> listShops(
            @Parameter(description = "City filter") @RequestParam(required = false) String city) {
        List<PartsShop> shops;
        if (city != null && !city.isBlank()) {
            shops = partsShopRepository.findByCity(city);
        } else {
            shops = partsShopRepository.findAll();
        }
        return ResponseEntity.ok(shops.stream().map(this::mapToResponse).collect(Collectors.toList()));
    }

    /**
     * Update shop profile.
     */
    @PutMapping("/{id}")
    @Operation(summary = "Update shop profile", description = "Update shop details like contact info and address")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Shop updated"),
        @ApiResponse(responseCode = "404", description = "Shop not found")
    })
    public ResponseEntity<ShopResponse> updateShop(
            @Parameter(description = "Shop ID") @PathVariable UUID id,
            @Valid @RequestBody ShopRegistrationRequest request) {
        log.info("Updating shop: {}", id);

        PartsShop shop = partsShopRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Shop", id));

        shop.setShopName(request.shopName());
        shop.setOwnerName(request.ownerName());
        shop.setPhone(request.phone());
        shop.setEmail(request.email());
        shop.setAddress(request.address());
        shop.setCity(request.city());
        shop.setPincode(request.pincode());
        shop.setGstNumber(request.gstNumber());

        PartsShop updated = partsShopRepository.save(shop);
        return ResponseEntity.ok(mapToResponse(updated));
    }

    /**
     * Update inventory for a shop.
     */
    @PostMapping("/{id}/inventory")
    @Operation(summary = "Update inventory", description = "Add or update an inventory item for a shop")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Inventory updated"),
        @ApiResponse(responseCode = "404", description = "Shop or part not found")
    })
    public ResponseEntity<Void> updateInventory(
            @Parameter(description = "Shop ID") @PathVariable UUID id,
            @Valid @RequestBody InventoryUpdateRequest request) {
        log.info("Updating inventory for shop {}: part={}", id, request.sparePartId());

        PartsShop shop = partsShopRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Shop", id));

        SparePart part = sparePartRepository.findById(request.sparePartId())
                .orElseThrow(() -> new EntityNotFoundException("SparePart", request.sparePartId()));

        InventoryItem item = inventoryItemRepository
                .findByShopIdAndSparePartId(id, request.sparePartId())
                .orElseGet(() -> InventoryItem.builder()
                        .shop(shop)
                        .sparePart(part)
                        .build());

        item.setQuantity(request.quantity());
        item.setSellingPrice(request.sellingPrice());
        item.setMinStockLevel(request.minStockLevel());
        item.setIsAvailable(request.isAvailable());

        inventoryItemRepository.save(item);

        // Sync to in-memory engine
        inventoryService.updateStock(id, request.sparePartId(), request.quantity());

        log.info("Inventory updated for shop {} part {}: qty={}", id, request.sparePartId(), request.quantity());
        return ResponseEntity.ok().build();
    }

    /**
     * Get inventory for a shop.
     */
    @GetMapping("/{id}/inventory")
    @Operation(summary = "Get shop inventory", description = "List all inventory items for a shop")
    @ApiResponse(responseCode = "200", description = "Inventory list")
    public ResponseEntity<List<InventoryItemResponse>> getInventory(
            @Parameter(description = "Shop ID") @PathVariable UUID id) {
        partsShopRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Shop", id));

        List<InventoryItem> items = inventoryItemRepository.findByShopIdSorted(id);
        List<InventoryItemResponse> responses = items.stream()
                .map(this::mapInventoryResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(responses);
    }

    /**
     * Get shop dashboard (today's orders, low stock alerts, top technicians).
     */
    @GetMapping("/{id}/dashboard")
    @Operation(summary = "Get shop dashboard", description = "Today's orders, revenue, low stock alerts, and top technicians")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Dashboard data"),
        @ApiResponse(responseCode = "404", description = "Shop not found")
    })
    public ResponseEntity<DashboardResponse> getShopDashboard(
            @Parameter(description = "Shop ID") @PathVariable UUID id) {
        DashboardResponse dashboard = dashboardService.getShopDashboard(id);
        return ResponseEntity.ok(dashboard);
    }

    /**
     * Map PartsShop entity to ShopResponse DTO.
     */
    private ShopResponse mapToResponse(PartsShop shop) {
        return new ShopResponse(
                shop.getId(),
                shop.getShopName(),
                shop.getOwnerName(),
                shop.getPhone(),
                shop.getEmail(),
                shop.getAddress(),
                shop.getCity(),
                shop.getPincode(),
                shop.getGstNumber(),
                shop.getIsVerified(),
                shop.getRating(),
                shop.getTotalOrdersServed(),
                shop.getRegisteredAt()
        );
    }

    /**
     * Inline response for inventory items.
     */
    public record InventoryItemResponse(
            UUID inventoryId,
            UUID sparePartId,
            String partName,
            String partNumber,
            Integer quantity,
            BigDecimal sellingPrice,
            Integer minStockLevel,
            Boolean isAvailable
    ) {}

    /**
     * Map InventoryItem entity to response.
     */
    private InventoryItemResponse mapInventoryResponse(InventoryItem item) {
        return new InventoryItemResponse(
                item.getId(),
                item.getSparePart().getId(),
                item.getSparePart().getName(),
                item.getSparePart().getPartNumber(),
                item.getQuantity(),
                item.getSellingPrice(),
                item.getMinStockLevel(),
                item.getIsAvailable()
        );
    }
}
