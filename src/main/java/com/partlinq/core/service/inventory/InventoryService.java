package com.partlinq.core.service.inventory;

import com.partlinq.core.engine.inventory.ConcurrentInventoryEngine;
import com.partlinq.core.model.entity.InventoryItem;
import com.partlinq.core.model.entity.OrderItem;
import com.partlinq.core.repository.InventoryItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Spring service wrapping the ConcurrentInventoryEngine.
 * Provides transaction-safe inventory management with in-memory hot-path operations
 * synchronized periodically to the database.
 *
 * Hot-path operations (reserve, confirm, cancel) run lock-free on the engine.
 * Persistence syncs to DB every 5 minutes or on explicit update calls.
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class InventoryService {

    private final InventoryItemRepository inventoryItemRepository;
    private final ConcurrentInventoryEngine inventoryEngine = new ConcurrentInventoryEngine();
    private volatile boolean initialized = false;

    /**
     * Initialize the inventory engine from the database.
     * Loads all InventoryItems into the in-memory engine.
     */
    //@PostConstruct
    public void initializeInventory() {
        if (initialized) {
            return;
        }

        synchronized (this) {
            if (initialized) {
                return;
            }

            log.info("Initializing inventory engine...");
            inventoryEngine.clear();

            List<InventoryItem> allItems = inventoryItemRepository.findAll();
            int count = 0;

            for (InventoryItem item : allItems) {
                inventoryEngine.initializeStock(
                    item.getShop().getId(),
                    item.getSparePart().getId(),
                    item.getQuantity(),
                    item.getSellingPrice(),
                    item.getMinStockLevel()
                );
                count++;
            }

            initialized = true;
            log.info("Inventory initialization complete. Loaded {} inventory items.", count);
        }
    }

    /**
     * Ensure inventory is initialized before operations.
     */
    private void ensureInitialized() {
        if (!initialized) {
            initializeInventory();
        }
    }

    /**
     * Reserve stock for an order with all-or-nothing semantics.
     * If any item fails reservation, all are rolled back.
     *
     * @param shopId UUID of the shop
     * @param orderItems List of OrderItems to reserve
     * @return true if all items reserved successfully, false if any failed
     */
    public boolean reserveForOrder(UUID shopId, List<OrderItem> orderItems) {
        ensureInitialized();

        List<OrderItem> reserved = new ArrayList<>();

        // Try to reserve each item
        for (OrderItem item : orderItems) {
            boolean success = inventoryEngine.reserveStock(
                shopId,
                item.getSparePart().getId(),
                item.getQuantity()
            );

            if (!success) {
                // Rollback all reserved items
                for (OrderItem reserved_item : reserved) {
                    inventoryEngine.cancelReservation(
                        shopId,
                        reserved_item.getSparePart().getId(),
                        reserved_item.getQuantity()
                    );
                }
                log.warn("Stock reservation failed for order with {} items", orderItems.size());
                return false;
            }

            reserved.add(item);
        }

        log.info("Successfully reserved {} items for shop {}", orderItems.size(), shopId);
        return true;
    }

    /**
     * Confirm a reservation after order pickup.
     * Updates the inventory state and database.
     *
     * @param orderId UUID of the order (for logging)
     * @param shopId UUID of the shop
     * @param orderItems Items to confirm
     */
    public void confirmPickup(UUID orderId, UUID shopId, List<OrderItem> orderItems) {
        ensureInitialized();

        for (OrderItem item : orderItems) {
            inventoryEngine.confirmReservation(
                shopId,
                item.getSparePart().getId(),
                item.getQuantity()
            );
        }

        log.info("Confirmed pickup for order {}", orderId);
    }

    /**
     * Cancel an order and restore reserved stock.
     *
     * @param orderId UUID of the order (for logging)
     * @param shopId UUID of the shop
     * @param orderItems Items to cancel
     */
    public void cancelOrder(UUID orderId, UUID shopId, List<OrderItem> orderItems) {
        ensureInitialized();

        for (OrderItem item : orderItems) {
            inventoryEngine.cancelReservation(
                shopId,
                item.getSparePart().getId(),
                item.getQuantity()
            );
        }

        log.info("Cancelled order {} and restored stock", orderId);
    }

    /**
     * Update stock quantity for a part at a shop.
     * Updates both engine and database.
     *
     * @param shopId UUID of the shop
     * @param partId UUID of the part
     * @param newQuantity New quantity (replaces current)
     */
    public void updateStock(UUID shopId, UUID partId, int newQuantity) {
        ensureInitialized();

        String key = ConcurrentInventoryEngine.makeKey(shopId, partId);
        Map<String, Integer> updates = new HashMap<>();
        updates.put(key, newQuantity);

        inventoryEngine.batchUpdate(updates);

        // Update database
        Optional<InventoryItem> itemOpt = inventoryItemRepository.findByShopIdAndSparePartId(shopId, partId);
        if (itemOpt.isPresent()) {
            InventoryItem item = itemOpt.get();
            item.setQuantity(newQuantity);
            inventoryItemRepository.save(item);
            log.info("Updated stock for part {} at shop {} to {}", partId, shopId, newQuantity);
        }
    }

    /**
     * Find which shops have a specific part in stock.
     * Returns availability details (price, quantity, shop) for each location.
     *
     * @param partId UUID of the part
     * @return List of StockEntry with availability info
     */
    public List<ConcurrentInventoryEngine.StockEntry> findPartAvailability(UUID partId) {
        ensureInitialized();
        return inventoryEngine.getAvailability(partId);
    }

    /**
     * Get low stock alerts for a specific shop.
     *
     * @param shopId UUID of the shop
     * @return List of StockAlert for this shop
     */
    public List<ConcurrentInventoryEngine.StockAlert> getLowStockAlerts(UUID shopId) {
        ensureInitialized();

        List<ConcurrentInventoryEngine.StockAlert> allAlerts = inventoryEngine.getLowStockAlerts();

        return allAlerts.stream()
            .filter(alert -> alert.shopId.equals(shopId))
            .collect(Collectors.toList());
    }

    /**
     * Get all pending low stock alerts across all shops.
     *
     * @return List of all StockAlert
     */
    public List<ConcurrentInventoryEngine.StockAlert> getAllLowStockAlerts() {
        ensureInitialized();
        return inventoryEngine.getLowStockAlerts();
    }

    /**
     * Sync in-memory inventory state to the database.
     * Runs periodically via @Scheduled annotation.
     * Called every 5 minutes to persist hot-path changes.
     */
    @Scheduled(fixedRate = 300000) // 5 minutes
    public void syncToDatabase() {
        if (!initialized) {
            return;
        }

        log.debug("Syncing inventory engine state to database...");

        Map<String, ConcurrentInventoryEngine.StockEntry> snapshot = inventoryEngine.getStockSnapshot();

        for (ConcurrentInventoryEngine.StockEntry entry : snapshot.values()) {
            Optional<InventoryItem> itemOpt = inventoryItemRepository.findByShopIdAndSparePartId(
                entry.shopId,
                entry.partId
            );

            if (itemOpt.isPresent()) {
                InventoryItem item = itemOpt.get();
                item.setQuantity(entry.getTotalQuantity());
                inventoryItemRepository.save(item);
            }
        }

        log.debug("Inventory sync complete. Synced {} items.", snapshot.size());
    }

    /**
     * Get inventory snapshot for analytics or reporting.
     *
     * @return Map of stock entries (key: shopId:partId)
     */
    public Map<String, ConcurrentInventoryEngine.StockEntry> getSnapshot() {
        ensureInitialized();
        return inventoryEngine.getStockSnapshot();
    }

    /**
     * Get total inventory item count.
     *
     * @return Number of inventory entries
     */
    public int getInventorySize() {
        ensureInitialized();
        return inventoryEngine.size();
    }

    /**
     * Clear inventory (for testing or reset).
     */
    public void clear() {
        inventoryEngine.clear();
        initialized = false;
    }
}
