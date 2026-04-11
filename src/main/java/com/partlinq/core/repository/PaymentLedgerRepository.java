package com.partlinq.core.repository;

import com.partlinq.core.model.entity.PaymentLedger;
import com.partlinq.core.model.enums.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository for PaymentLedger.
 * Provides queries for udhaar tracking, balance lookups, and payment history.
 */
@Repository
public interface PaymentLedgerRepository extends JpaRepository<PaymentLedger, UUID> {

	/**
	 * Get full ledger history for a technician at a specific shop, newest first.
	 */
	List<PaymentLedger> findByTechnicianIdAndShopIdOrderByCreatedAtDesc(UUID technicianId, UUID shopId);

	/**
	 * Get full ledger history for a technician across all shops, newest first.
	 */
	List<PaymentLedger> findByTechnicianIdOrderByCreatedAtDesc(UUID technicianId);

	/**
	 * Get all ledger entries for a shop, newest first.
	 */
	List<PaymentLedger> findByShopIdOrderByCreatedAtDesc(UUID shopId);

	/**
	 * Get the most recent ledger entry for a technician-shop pair (has the current balance).
	 */
	Optional<PaymentLedger> findFirstByTechnicianIdAndShopIdOrderByCreatedAtDesc(UUID technicianId, UUID shopId);

	/**
	 * Get all entries for a specific order.
	 */
	List<PaymentLedger> findByOrderId(UUID orderId);

	/**
	 * Count entries by type for a technician.
	 */
	Long countByTechnicianIdAndEntryType(UUID technicianId, LedgerEntryType entryType);

	/**
	 * Get total credit amount for a technician at a shop (sum of all CREDIT entries).
	 */
	@Query("SELECT COALESCE(SUM(pl.amount), 0) FROM PaymentLedger pl " +
		   "WHERE pl.technician.id = :techId AND pl.shop.id = :shopId AND pl.entryType = 'CREDIT'")
	BigDecimal getTotalCreditByTechnicianAndShop(@Param("techId") UUID techId, @Param("shopId") UUID shopId);

	/**
	 * Get total payment amount for a technician at a shop (sum of all PAYMENT entries).
	 */
	@Query("SELECT COALESCE(SUM(pl.amount), 0) FROM PaymentLedger pl " +
		   "WHERE pl.technician.id = :techId AND pl.shop.id = :shopId AND pl.entryType = 'PAYMENT'")
	BigDecimal getTotalPaymentByTechnicianAndShop(@Param("techId") UUID techId, @Param("shopId") UUID shopId);

	/**
	 * Find the last payment date for a technician at a shop.
	 */
	@Query("SELECT MAX(pl.createdAt) FROM PaymentLedger pl " +
		   "WHERE pl.technician.id = :techId AND pl.shop.id = :shopId AND pl.entryType = 'PAYMENT'")
	Optional<LocalDateTime> getLastPaymentDate(@Param("techId") UUID techId, @Param("shopId") UUID shopId);

	/**
	 * Get distinct technician IDs who owe money to a shop (balance > 0).
	 * Uses the latest ledger entry per technician to determine current balance.
	 */
	@Query("SELECT DISTINCT pl.technician.id FROM PaymentLedger pl " +
		   "WHERE pl.shop.id = :shopId AND pl.balanceAfter > 0 " +
		   "AND pl.createdAt = (SELECT MAX(pl2.createdAt) FROM PaymentLedger pl2 " +
		   "WHERE pl2.technician.id = pl.technician.id AND pl2.shop.id = :shopId)")
	List<UUID> findTechnicianIdsWithOutstandingBalance(@Param("shopId") UUID shopId);

	/**
	 * Get ledger entries in a date range for a shop (for daily/weekly reports).
	 */
	List<PaymentLedger> findByShopIdAndCreatedAtBetweenOrderByCreatedAtDesc(
		UUID shopId, LocalDateTime startDate, LocalDateTime endDate);

	/**
	 * Get all payment entries for a technician in a date range.
	 */
	@Query("SELECT pl FROM PaymentLedger pl WHERE pl.technician.id = :techId " +
		   "AND pl.entryType = 'PAYMENT' AND pl.createdAt BETWEEN :start AND :end " +
		   "ORDER BY pl.createdAt DESC")
	List<PaymentLedger> findPaymentsByTechnicianInRange(
		@Param("techId") UUID techId,
		@Param("start") LocalDateTime start,
		@Param("end") LocalDateTime end);
}
