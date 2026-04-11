package com.partlinq.core.repository;

import com.partlinq.core.model.entity.Order;
import com.partlinq.core.model.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA Repository for Order entities.
 * Provides query methods for order management and retrieval.
 */
@Repository
public interface OrderRepository extends JpaRepository<Order, UUID> {

	/**
	 * Find an order by order number
	 */
	Optional<Order> findByOrderNumber(String orderNumber);

	/**
	 * Find all orders for a technician, ordered by most recent first
	 */
	List<Order> findByTechnicianIdOrderByCreatedAtDesc(UUID technicianId);

	/**
	 * Find all orders for a shop, ordered by most recent first
	 */
	List<Order> findByShopIdOrderByCreatedAtDesc(UUID shopId);

	/**
	 * Find all orders with a specific status
	 */
	List<Order> findByStatus(OrderStatus status);

	/**
	 * Find all orders with a specific status for a technician
	 */
	List<Order> findByTechnicianIdAndStatus(UUID technicianId, OrderStatus status);

	/**
	 * Find all orders with a specific status for a shop
	 */
	List<Order> findByShopIdAndStatus(UUID shopId, OrderStatus status);

	/**
	 * Find overdue payment orders
	 */
	@Query("SELECT o FROM Order o WHERE o.status = com.partlinq.core.model.enums.OrderStatus.PAYMENT_OVERDUE " +
		   "AND o.paymentDueDate < :currentDate ORDER BY o.paymentDueDate ASC")
	List<Order> findOverduePaymentOrders(@Param("currentDate") LocalDateTime currentDate);

	/**
	 * Count orders for a technician with a specific status
	 */
	Long countByTechnicianIdAndStatus(UUID technicianId, OrderStatus status);

	/**
	 * Count orders for a shop with a specific status
	 */
	Long countByShopIdAndStatus(UUID shopId, OrderStatus status);

	/**
	 * Find unpaid orders for a technician
	 */
	@Query("SELECT o FROM Order o WHERE o.technician.id = :technicianId AND o.paidAt IS NULL " +
		   "ORDER BY o.createdAt DESC")
	List<Order> findUnpaidOrdersByTechnicianId(@Param("technicianId") UUID technicianId);

	/**
	 * Find orders created within a time range
	 */
	List<Order> findByCreatedAtBetween(LocalDateTime startDate, LocalDateTime endDate);

	/**
	 * Find orders within a date range for a specific shop
	 */
	@Query("SELECT o FROM Order o WHERE o.shop.id = :shopId AND o.createdAt BETWEEN :startDate AND :endDate " +
		   "ORDER BY o.createdAt DESC")
	List<Order> findByShopAndDateRange(
		@Param("shopId") UUID shopId,
		@Param("startDate") LocalDateTime startDate,
		@Param("endDate") LocalDateTime endDate
	);

}
