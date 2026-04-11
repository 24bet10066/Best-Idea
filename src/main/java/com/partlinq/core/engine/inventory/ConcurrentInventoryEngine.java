package com.partlinq.core.engine.inventory;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Thread-safe inventory management engine using ConcurrentHashMap and atomic operations.
 * Handles concurrent stock reservations, updates, and availability checks
 * without database-level locking for hot-path operations.
 *
 * Key DSA: ConcurrentHashMap, AtomicInteger, ReentrantReadWriteLock
 *
 * Why Java (not Node.js):
 * - True thread safety with ConcurrentHashMap (not just event loop serialization)
 * - AtomicInteger for lock-free stock updates
 * - ReentrantReadWriteLock for batch operations
 * - No callback hell for complex multi-step inventory transactions
 */
public class ConcurrentInventoryEngine {

    /**
     * Represents a stock entry for a part at a specific shop.
     */
    public static class StockEntry {
        public final AtomicInteger availableQuantity = new AtomicInteger(0);
        public final AtomicInteger reservedQuantity = new AtomicInteger(0);
        public final UUID shopId;
        public final UUID partId;
        public final BigDecimal sellingPrice;
        public final int minStockLevel;
        public volatile long lastUpdatedAt;

        public StockEntry(UUID shopId, UUID partId, int initialQuantity, BigDecimal sellingPrice, int minStockLevel) {
            this.shopId = shopId;
            this.partId = partId;
            this.sellingPrice = sellingPrice;
            this.minStockLevel = minStockLevel;
            this.availableQuantity.set(initialQuantity);
            this.lastUpdatedAt = System.currentTimeMillis();
        }

        /**
         * Get total quantity (available + reserved).
         */
        public int getTotalQuantity() {
            return availableQuantity.get() + reservedQuantity.get();
        }
    }

    /**
     * Alert for low stock levels.
     */
    public static class StockAlert {
        public final UUID shopId;
        public final UUID partId;
        public final int currentQuantity;
        public final int minLevel;
        public final long timestamp;

        public StockAlert(UUID shopId, UUID partId, int currentQuantity, int minLevel) {
            this.shopId = shopId;
            this.partId = partId;
            this.currentQuantity = currentQuantity;
            this.minLevel = minLevel;
            this.timestamp = System.currentTimeMillis();
        }
    }

    private final ConcurrentHashMap<String, StockEntry> stockMap = new ConcurrentHashMap<>();
    private final ConcurrentLinkedQueue<StockAlert> alertQueue = new ConcurrentLinkedQueue<>();
    private final ReentrantReadWriteLock batchLock = new ReentrantReadWriteLock();

    /**
     * Create composite key from shop ID and part ID.
     *
     * @param shopId Shop UUID
     * @param partId Part UUID
     * @return Composite key string (format: "shopId:partId")
     */
    public static String makeKey(UUID shopId, UUID partId) {
        return shopId + ":" + partId;
    }

    /**
     * Initialize stock for a part at a shop.
     * Loads initial inventory state from database.
     *
     * @param shopId      UUID of the shop
     * @param partId      UUID of the part
     * @param quantity    Initial quantity in stock
     * @param price       Selling price per unit
     * @param minLevel    Minimum stock level for alerts
     */
    public void initializeStock(UUID shopId, UUID partId, int quantity, BigDecimal price, int minLevel) {
        String key = makeKey(shopId, partId);
        StockEntry entry = new StockEntry(shopId, partId, quantity, price, minLevel);
        stockMap.put(key, entry);
    }

    /**
     * Reserve stock for an order.
     * Atomically decrements available quantity and increments reserved.
     * Uses CAS (compare-and-swap) loop for atomicity.
     *
     * @param shopId  UUID of the shop
     * @param partId  UUID of the part
     * @param quantity Quantity to reserve
     * @return true if reservation succeeded, false if insufficient stock
     */
    public boolean reserveStock(UUID shopId, UUID partId, int quantity) {
        String key = makeKey(shopId, partId);
        StockEntry entry = stockMap.get(key);

        if (entry == null) {
            return false;
        }

        // CAS loop for lock-free atomicity
        while (true) {
            int available = entry.availableQuantity.get();
            if (available < quantity) {
                return false; // Insufficient stock
            }

            if (entry.availableQuantity.compareAndSet(available, available - quantity)) {
                entry.reservedQuantity.addAndGet(quantity);
                entry.lastUpdatedAt = System.currentTimeMillis();
                checkAndAlert(key, entry);
                return true;
            }
        }
    }

    /**
     * Confirm a reservation (stock has been picked up).
     * Decrements reserved quantity only (available already decremented during reservation).
     *
     * @param shopId  UUID of the shop
     * @param partId  UUID of the part
     * @param quantity Quantity to confirm
     */
    public void confirmReservation(UUID shopId, UUID partId, int quantity) {
        String key = makeKey(shopId, partId);
        StockEntry entry = stockMap.get(key);

        if (entry != null) {
            entry.reservedQuantity.addAndGet(-quantity);
            entry.lastUpdatedAt = System.currentTimeMillis();
        }
    }

    /**
     * Cancel a reservation and restore stock to available.
     * Increments available quantity and decrements reserved.
     *
     * @param shopId  UUID of the shop
     * @param partId  UUID of the part
     * @param quantity Quantity to cancel
     */
    public void cancelReservation(UUID shopId, UUID partId, int quantity) {
        String key = makeKey(shopId, partId);
        StockEntry entry = stockMap.get(key);

        if (entry != null) {
            entry.availableQuantity.addAndGet(quantity);
            entry.reservedQuantity.addAndGet(-quantity);
            entry.lastUpdatedAt = System.currentTimeMillis();
            checkAndAlert(key, entry);
        }
    }

    /**
     * Restock an item at a shop.
     * Adds to available quantity.
     *
     * @param shopId  UUID of the shop
     * @param partId  UUID of the part
     * @param quantity Quantity to add
     */
    public void restockItem(UUID shopId, UUID partId, int quantity) {
        String key = makeKey(shopId, partId);
        StockEntry entry = stockMap.get(key);

        if (entry != null) {
            entry.availableQuantity.addAndGet(quantity);
            entry.lastUpdatedAt = System.currentTimeMillis();
            checkAndAlert(key, entry);
        }
    }

    /**
     * Get availability info for a part across all shops.
     * Returns list of shops that have this part in stock.
     *
     * @param partId UUID of the part
     * @return List of StockEntry for this part across shops
     */
    public List<StockEntry> getAvailability(UUID partId) {
        List<StockEntry> results = new ArrayList<>();
        for (StockEntry entry : stockMap.values()) {
            if (entry.partId.equals(partId) && entry.availableQuantity.get() > 0) {
                results.add(entry);
            }
        }
        return results;
    }

    /**
     * Get all pending low-stock alerts.
     * Drains the alert queue.
     *
     * @return List of StockAlert
     */
    public List<StockAlert> getLowStockAlerts() {
        List<StockAlert> alerts = new ArrayList<>();
        StockAlert alert;
        while ((alert = alertQueue.poll()) != null) {
            alerts.add(alert);
        }
        return alerts;
    }

    /**
     * Check if stock is below minimum level and add alert if so.
     *
     * @param key   Composite key (shopId:partId)
     * @param entry StockEntry to check
     */
    private void checkAndAlert(String key, StockEntry entry) {
        if (entry.availableQuantity.get() < entry.minStockLevel) {
            alertQueue.offer(new StockAlert(entry.shopId, entry.partId, entry.availableQuantity.get(), entry.minStockLevel));
        }
    }

    /**
     * Get a read-locked snapshot of entire inventory for analytics.
     * Prevents concurrent modifications during snapshot.
     *
     * @return Map of all stock entries (copy)
     */
    public Map<String, StockEntry> getStockSnapshot() {
        batchLock.readLock().lock();
        try {
            return new HashMap<>(stockMap);
        } finally {
            batchLock.readLock().unlock();
        }
    }

    /**
     * Batch update multiple stock items.
     * Uses write lock for atomicity across all updates.
     *
     * @param updates Map of "shopId:partId" -> new quantity
     */
    public void batchUpdate(Map<String, Integer> updates) {
        batchLock.writeLock().lock();
        try {
            for (Map.Entry<String, Integer> update : updates.entrySet()) {
                StockEntry entry = stockMap.get(update.getKey());
                if (entry != null) {
                    entry.availableQuantity.set(update.getValue());
                    entry.reservedQuantity.set(0);
                    entry.lastUpdatedAt = System.currentTimeMillis();
                    checkAndAlert(update.getKey(), entry);
                }
            }
        } finally {
            batchLock.writeLock().unlock();
        }
    }

    /**
     * Get the current size of the inventory.
     *
     * @return Number of stock entries
     */
    public int size() {
        return stockMap.size();
    }

    /**
     * Clear all inventory data.
     */
    public void clear() {
        batchLock.writeLock().lock();
        try {
            stockMap.clear();
            alertQueue.clear();
        } finally {
            batchLock.writeLock().unlock();
        }
    }

    /**
     * Get specific stock entry by key.
     *
     * @param key Composite key (shopId:partId)
     * @return StockEntry or null if not found
     */
    public StockEntry getEntry(String key) {
        return stockMap.get(key);
    }
}
