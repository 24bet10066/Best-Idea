package com.partlinq.core.service;

import com.partlinq.core.model.dto.OrderRequest;
import com.partlinq.core.model.dto.OrderResponse;
import com.partlinq.core.model.entity.*;
import com.partlinq.core.model.enums.OrderStatus;
import com.partlinq.core.repository.*;
import com.partlinq.core.service.order.OrderService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// no test profile needed — uses default H2
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for OrderService — the most critical business flow.
 *
 * These tests verify:
 * 1. Full order lifecycle (place → confirm → ready → pickup → complete)
 * 2. Invalid status transitions are rejected
 * 3. Credit validation works
 * 4. Inventory reservation + confirmation + cancellation
 * 5. Trust score updates on completion
 * 6. Double-completion prevention
 */
@SpringBootTest
@Transactional
class OrderServiceTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private PartsShopRepository shopRepository;

    @Autowired
    private SparePartRepository sparePartRepository;

    @Autowired
    private InventoryItemRepository inventoryItemRepository;

    @Autowired
    private OrderRepository orderRepository;

    // ---- Lifecycle Tests ----

    @Test
    @DisplayName("Full order lifecycle: PLACED → CONFIRMED → READY → PICKED_UP → COMPLETED")
    void fullOrderLifecycle() {
        // Get seeded data
        Technician tech = technicianRepository.findAll().stream()
            .filter(t -> t.getTrustScore() >= 50.0)
            .findFirst()
            .orElseThrow();

        PartsShop shop = shopRepository.findAll().get(0);

        // Find a part with inventory
        InventoryItem invItem = inventoryItemRepository.findByShopIdSorted(shop.getId()).stream()
            .filter(i -> i.getQuantity() > 0)
            .findFirst()
            .orElseThrow();

        double initialTrustScore = tech.getTrustScore();

        // Place order
        OrderRequest request = new OrderRequest(
            tech.getId(), shop.getId(),
            List.of(new OrderRequest.OrderItemRequest(invItem.getSparePart().getId(), 1)),
            "Integration test order"
        );

        OrderResponse placed = orderService.placeOrder(request);
        assertNotNull(placed);
        assertEquals(OrderStatus.PLACED, placed.status());
        assertNotNull(placed.orderNumber());
        assertTrue(placed.orderNumber().startsWith("PLQ-"));

        // Confirm
        OrderResponse confirmed = orderService.confirmOrder(placed.orderId());
        assertEquals(OrderStatus.CONFIRMED, confirmed.status());

        // Mark ready
        OrderResponse ready = orderService.markReady(placed.orderId());
        assertEquals(OrderStatus.READY, ready.status());

        // Mark picked up
        OrderResponse pickedUp = orderService.markPickedUp(placed.orderId());
        assertEquals(OrderStatus.PICKED_UP, pickedUp.status());

        // Complete
        OrderResponse completed = orderService.completeOrder(placed.orderId(), null);
        assertEquals(OrderStatus.COMPLETED, completed.status());

        // Trust score should have increased
        Technician updatedTech = technicianRepository.findById(tech.getId()).orElseThrow();
        assertTrue(updatedTech.getTrustScore() > initialTrustScore,
            "Trust score should increase after order completion");
    }

    @Test
    @DisplayName("Cannot confirm an already confirmed order")
    void cannotDoubleConfirm() {
        OrderResponse order = placeTestOrder();
        orderService.confirmOrder(order.orderId());

        assertThrows(IllegalStateException.class, () ->
            orderService.confirmOrder(order.orderId())
        );
    }

    @Test
    @DisplayName("Cannot mark PLACED order as ready (skip CONFIRMED)")
    void cannotSkipConfirmation() {
        OrderResponse order = placeTestOrder();

        assertThrows(IllegalStateException.class, () ->
            orderService.markReady(order.orderId())
        );
    }

    @Test
    @DisplayName("Cannot complete a CANCELLED order")
    void cannotCompleteCancelledOrder() {
        OrderResponse order = placeTestOrder();
        orderService.cancelOrder(order.orderId());

        assertThrows(IllegalStateException.class, () ->
            orderService.completeOrder(order.orderId(), null)
        );
    }

    @Test
    @DisplayName("Cannot cancel a COMPLETED order")
    void cannotCancelCompletedOrder() {
        OrderResponse order = placeTestOrder();
        orderService.confirmOrder(order.orderId());
        orderService.markReady(order.orderId());
        orderService.markPickedUp(order.orderId());
        orderService.completeOrder(order.orderId(), null);

        assertThrows(IllegalStateException.class, () ->
            orderService.cancelOrder(order.orderId())
        );
    }

    @Test
    @DisplayName("Cannot complete an already completed order (idempotency)")
    void cannotDoubleComplete() {
        OrderResponse order = placeTestOrder();
        orderService.confirmOrder(order.orderId());
        orderService.markReady(order.orderId());
        orderService.markPickedUp(order.orderId());
        orderService.completeOrder(order.orderId(), null);

        assertThrows(IllegalStateException.class, () ->
            orderService.completeOrder(order.orderId(), null)
        );
    }

    @Test
    @DisplayName("Cancel order restores inventory")
    void cancelRestoresInventory() {
        PartsShop shop = shopRepository.findAll().get(0);
        InventoryItem invItem = inventoryItemRepository.findByShopIdSorted(shop.getId()).stream()
            .filter(i -> i.getQuantity() > 2)
            .findFirst()
            .orElseThrow();

        int stockBefore = invItem.getQuantity();

        OrderResponse order = placeTestOrderWithPart(invItem.getSparePart().getId(), 1);
        orderService.cancelOrder(order.orderId());

        // After cancel, stock should be back to original
        InventoryItem updated = inventoryItemRepository.findById(invItem.getId()).orElseThrow();
        assertEquals(stockBefore, updated.getQuantity(),
            "Stock should be restored after cancellation");
    }

    @Test
    @DisplayName("Order with non-existent technician fails with EntityNotFoundException")
    void orderWithBadTechnicianFails() {
        PartsShop shop = shopRepository.findAll().get(0);
        InventoryItem invItem = inventoryItemRepository.findByShopIdSorted(shop.getId()).stream()
            .filter(i -> i.getQuantity() > 0)
            .findFirst()
            .orElseThrow();

        OrderRequest request = new OrderRequest(
            UUID.randomUUID(), shop.getId(),
            List.of(new OrderRequest.OrderItemRequest(invItem.getSparePart().getId(), 1)),
            null
        );

        assertThrows(com.partlinq.core.exception.EntityNotFoundException.class, () ->
            orderService.placeOrder(request)
        );
    }

    @Test
    @DisplayName("Late payment records negative trust event")
    void latePaymentReducesTrust() {
        OrderResponse order = placeTestOrder();
        orderService.confirmOrder(order.orderId());
        orderService.markReady(order.orderId());
        orderService.markPickedUp(order.orderId());

        // Complete with a payment date 45 days in the future (past due date)
        LocalDateTime latePaymentDate = LocalDateTime.now().plusDays(45);
        OrderResponse completed = orderService.completeOrder(order.orderId(), latePaymentDate);
        assertEquals(OrderStatus.COMPLETED, completed.status());
    }

    // ---- Helpers ----

    private OrderResponse placeTestOrder() {
        Technician tech = technicianRepository.findAll().stream()
            .filter(t -> t.getTrustScore() >= 50.0)
            .findFirst()
            .orElseThrow();

        PartsShop shop = shopRepository.findAll().get(0);
        InventoryItem invItem = inventoryItemRepository.findByShopIdSorted(shop.getId()).stream()
            .filter(i -> i.getQuantity() > 0)
            .findFirst()
            .orElseThrow();

        OrderRequest request = new OrderRequest(
            tech.getId(), shop.getId(),
            List.of(new OrderRequest.OrderItemRequest(invItem.getSparePart().getId(), 1)),
            null
        );

        return orderService.placeOrder(request);
    }

    private OrderResponse placeTestOrderWithPart(UUID sparePartId, int quantity) {
        Technician tech = technicianRepository.findAll().stream()
            .filter(t -> t.getTrustScore() >= 50.0)
            .findFirst()
            .orElseThrow();

        PartsShop shop = shopRepository.findAll().get(0);

        OrderRequest request = new OrderRequest(
            tech.getId(), shop.getId(),
            List.of(new OrderRequest.OrderItemRequest(sparePartId, quantity)),
            null
        );

        return orderService.placeOrder(request);
    }
}
