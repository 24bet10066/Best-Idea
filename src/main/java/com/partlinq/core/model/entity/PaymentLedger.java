package com.partlinq.core.model.entity;

import com.partlinq.core.model.enums.PaymentMode;
import com.partlinq.core.model.enums.LedgerEntryType;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * PaymentLedger tracks every credit (udhaar) and payment event per technician per shop.
 *
 * This is the CORE of how Indian spare parts businesses actually work:
 * - Technician picks parts on credit (CREDIT entry)
 * - Technician pays back partially or fully (PAYMENT entry)
 * - Running balance = sum of all entries for that technician-shop pair
 *
 * Ground reality: Most technicians settle weekly or when they visit next.
 * Some settle monthly. A few never settle on time. This ledger tracks ALL of it.
 */
@Entity
@Table(name = "payment_ledger", indexes = {
	@Index(name = "idx_ledger_tech_shop", columnList = "technician_id,shop_id"),
	@Index(name = "idx_ledger_tech_created", columnList = "technician_id,created_at"),
	@Index(name = "idx_ledger_order", columnList = "order_id")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@ToString(exclude = {"technician", "shop", "order"})
public class PaymentLedger {

	@Id
	@GeneratedValue(strategy = GenerationType.UUID)
	@EqualsAndHashCode.Include
	private UUID id;

	/**
	 * The technician involved in this transaction
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "technician_id", nullable = false)
	private Technician technician;

	/**
	 * The shop involved in this transaction
	 */
	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shop_id", nullable = false)
	private PartsShop shop;

	/**
	 * The order this entry relates to (null for direct payments)
	 */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "order_id")
	private Order order;

	/**
	 * CREDIT = technician took parts on udhaar (increases balance owed)
	 * PAYMENT = technician paid back (decreases balance owed)
	 * ADJUSTMENT = manual correction by shop owner
	 */
	@Column(nullable = false)
	@Enumerated(EnumType.STRING)
	private LedgerEntryType entryType;

	/**
	 * Amount of this transaction. Always positive.
	 * For CREDIT: amount owed increases.
	 * For PAYMENT: amount owed decreases.
	 */
	@Column(nullable = false)
	private BigDecimal amount;

	/**
	 * Running balance AFTER this entry (what technician owes to this shop)
	 */
	@Column(nullable = false)
	private BigDecimal balanceAfter;

	/**
	 * How payment was made (only for PAYMENT entries)
	 */
	@Column
	@Enumerated(EnumType.STRING)
	private PaymentMode paymentMode;

	/**
	 * Reference number (UPI txn ID, cheque number, etc.)
	 */
	@Column(length = 100)
	private String referenceNumber;

	/**
	 * Human-readable note: "Paid via UPI", "2 AC compressors on udhaar", etc.
	 */
	@Column(length = 500)
	private String notes;

	/**
	 * Who recorded this entry (shop owner name or "system")
	 */
	@Column(nullable = false, length = 100)
	@Builder.Default
	private String recordedBy = "system";

	@CreationTimestamp
	@Column(nullable = false, updatable = false)
	private LocalDateTime createdAt;

}
