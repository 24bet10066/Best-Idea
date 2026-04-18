package com.partlinq.core.controller;

import com.partlinq.core.model.dto.OrderRequest;
import com.partlinq.core.model.dto.OrderResponse;
import com.partlinq.core.security.ShopAccessGuard;
import com.partlinq.core.service.idempotency.IdempotencyService;
import com.partlinq.core.service.order.OrderService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * REST controller for order lifecycle management.
 * Handles order placement, status transitions, and retrieval.
 *
 * <h3>Order Flow:</h3>
 * PLACED → CONFIRMED → READY → PICKED_UP → COMPLETED
 *                                          → CANCELLED (from any pre-completed state)
 */
@RestController
@RequestMapping("/v1/orders")
@Tag(name = "Orders", description = "Order placement, status management, and history")
@RequiredArgsConstructor
@Slf4j
public class OrderController {

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;
    private final ShopAccessGuard shopAccessGuard;

    /**
     * Place a new order.
     * Validates credit, reserves inventory, and creates the order.
     */
    @PostMapping
    @Operation(summary = "Place an order", description = "Place a new order with credit validation and inventory reservation")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Order placed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "409", description = "Insufficient credit or inventory")
    })
    public ResponseEntity<OrderResponse> placeOrder(
            @Valid @RequestBody OrderRequest request,
            @Parameter(description = "Optional UUID to make retries safe")
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        shopAccessGuard.assertCanAccess(request.shopId());
        log.info("Placing order: technician={}, shop={}, items={}",
                request.technicianId(), request.shopId(), request.items().size());
        ResponseEntity<OrderResponse> response = idempotencyService.executeOrReplay(
            idempotencyKey,
            "/v1/orders",
            OrderResponse.class,
            () -> orderService.placeOrder(request)
        );
        // If this was a fresh execution, flip 200 → 201 to preserve "Created" semantics.
        // Replays keep the original stored status.
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return ResponseEntity.status(HttpStatus.CREATED).body(response.getBody());
        }
        return response;
    }

    /**
     * Get order by ID.
     */
    @GetMapping("/{id}")
    @Operation(summary = "Get order by ID", description = "Retrieve order details including items and status")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order found"),
        @ApiResponse(responseCode = "404", description = "Order not found")
    })
    public ResponseEntity<OrderResponse> getOrderById(
            @Parameter(description = "Order ID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.getOrderById(id));
    }

    /**
     * Get all orders for a technician.
     */
    @GetMapping("/technician/{technicianId}")
    @Operation(summary = "Get orders by technician", description = "List all orders for a specific technician")
    @ApiResponse(responseCode = "200", description = "Order list")
    public ResponseEntity<List<OrderResponse>> getOrdersByTechnician(
            @Parameter(description = "Technician ID") @PathVariable UUID technicianId) {
        return ResponseEntity.ok(orderService.getOrdersByTechnician(technicianId));
    }

    /**
     * Get all orders for a shop.
     */
    @GetMapping("/shop/{shopId}")
    @Operation(summary = "Get orders by shop", description = "List all orders for a specific shop")
    @ApiResponse(responseCode = "200", description = "Order list")
    public ResponseEntity<List<OrderResponse>> getOrdersByShop(
            @Parameter(description = "Shop ID") @PathVariable UUID shopId) {
        shopAccessGuard.assertCanAccess(shopId);
        return ResponseEntity.ok(orderService.getOrdersByShop(shopId));
    }

    /**
     * Confirm an order (shop confirms receipt).
     * PLACED → CONFIRMED
     */
    @PatchMapping("/{id}/confirm")
    @Operation(summary = "Confirm order", description = "Shop confirms the order (PLACED → CONFIRMED)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order confirmed"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "Invalid status transition")
    })
    public ResponseEntity<OrderResponse> confirmOrder(
            @Parameter(description = "Order ID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.confirmOrder(id));
    }

    /**
     * Mark order as ready for pickup.
     * CONFIRMED → READY
     */
    @PatchMapping("/{id}/ready")
    @Operation(summary = "Mark order ready", description = "Shop marks items ready for pickup (CONFIRMED → READY)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order marked ready"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "Invalid status transition")
    })
    public ResponseEntity<OrderResponse> markReady(
            @Parameter(description = "Order ID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.markReady(id));
    }

    /**
     * Mark order as picked up.
     * READY → PICKED_UP. Confirms inventory reservation.
     */
    @PatchMapping("/{id}/pickup")
    @Operation(summary = "Mark order picked up", description = "Technician picks up items (READY → PICKED_UP)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order picked up"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "Invalid status transition")
    })
    public ResponseEntity<OrderResponse> markPickedUp(
            @Parameter(description = "Order ID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.markPickedUp(id));
    }

    /**
     * Complete order (payment received).
     * Records trust events for payment timing.
     */
    @PatchMapping("/{id}/complete")
    @Operation(summary = "Complete order", description = "Mark order as completed with payment — records trust events")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order completed"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "Invalid status transition")
    })
    public ResponseEntity<OrderResponse> completeOrder(
            @Parameter(description = "Order ID") @PathVariable UUID id,
            @Parameter(description = "Payment date (ISO-8601, defaults to now)") @RequestParam(required = false) LocalDateTime paymentDate) {
        return ResponseEntity.ok(orderService.completeOrder(id, paymentDate));
    }

    /**
     * Cancel an order. Restores reserved inventory.
     */
    @PatchMapping("/{id}/cancel")
    @Operation(summary = "Cancel order", description = "Cancel an order and restore reserved inventory")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Order cancelled"),
        @ApiResponse(responseCode = "404", description = "Order not found"),
        @ApiResponse(responseCode = "409", description = "Invalid status transition")
    })
    public ResponseEntity<OrderResponse> cancelOrder(
            @Parameter(description = "Order ID") @PathVariable UUID id) {
        return ResponseEntity.ok(orderService.cancelOrder(id));
    }
}
