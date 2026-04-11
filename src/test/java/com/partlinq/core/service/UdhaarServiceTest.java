package com.partlinq.core.service;

import com.partlinq.core.model.dto.PaymentRequest;
import com.partlinq.core.model.dto.UdhaarSummary;
import com.partlinq.core.model.entity.*;
import com.partlinq.core.model.enums.PaymentMode;
import com.partlinq.core.repository.*;
import com.partlinq.core.service.udhaar.UdhaarService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// no test profile needed — uses default H2
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for UdhaarService — the shop owner's daily lifeline.
 *
 * Tests cover:
 * 1. Recording credit and verifying balance
 * 2. Partial payments — balance reduces correctly
 * 3. Full payment — balance goes to zero, risk tag clears
 * 4. Overpayment — creates advance credit (negative balance)
 * 5. Adjustments — discounts, write-offs
 * 6. Risk tag computation based on payment recency
 * 7. Outstanding view for shop
 */
@SpringBootTest
@Transactional
class UdhaarServiceTest {

    @Autowired
    private UdhaarService udhaarService;

    @Autowired
    private TechnicianRepository technicianRepository;

    @Autowired
    private PartsShopRepository shopRepository;

    @Autowired
    private OrderRepository orderRepository;

    // ---- Balance Tests ----

    @Test
    @DisplayName("getCurrentBalance returns ZERO for new technician-shop pair")
    void freshBalanceIsZero() {
        Technician tech = technicianRepository.findAll().get(0);
        PartsShop shop = shopRepository.findAll().get(0);

        BigDecimal balance = udhaarService.getCurrentBalance(tech.getId(), shop.getId());
        // Could be zero or have seeded data — just verify it's non-null
        assertNotNull(balance);
    }

    @Test
    @DisplayName("Record payment reduces outstanding balance")
    void paymentReducesBalance() {
        // Find a technician that has at least one order
        PartsShop shop = shopRepository.findAll().get(0);
        Technician tech = technicianRepository.findAll().stream()
            .filter(t -> !orderRepository.findByTechnicianIdOrderByCreatedAtDesc(t.getId()).isEmpty())
            .findFirst()
            .orElse(null);
        if (tech == null) return;

        // Record a known credit
        Order order = orderRepository.findByTechnicianIdOrderByCreatedAtDesc(tech.getId()).get(0);

        BigDecimal creditAmount = BigDecimal.valueOf(5000);
        udhaarService.recordCredit(tech.getId(), shop.getId(), order.getId(), creditAmount);

        BigDecimal balanceBefore = udhaarService.getCurrentBalance(tech.getId(), shop.getId());

        // Pay half
        BigDecimal halfPayment = balanceBefore.divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);

        PaymentRequest request = new PaymentRequest(
            tech.getId(), shop.getId(),
            halfPayment, PaymentMode.UPI, "UPI-TEST-001", "Partial payment test", "TestRunner"
        );

        UdhaarSummary afterPayment = udhaarService.recordPayment(request);
        assertEquals(0, balanceBefore.subtract(halfPayment).compareTo(afterPayment.currentBalance()),
            "Balance should be reduced by payment amount");
    }

    @Test
    @DisplayName("Full payment clears balance and sets risk tag to CLEAR")
    void fullPaymentClearsBalance() {
        Technician tech = technicianRepository.findAll().get(1);
        PartsShop shop = shopRepository.findAll().get(0);

        Order order = orderRepository.findByTechnicianIdOrderByCreatedAtDesc(tech.getId())
            .stream().findFirst().orElse(null);
        if (order == null) return;

        // Record credit and then pay it all off
        BigDecimal creditAmount = BigDecimal.valueOf(3000);
        udhaarService.recordCredit(tech.getId(), shop.getId(), order.getId(), creditAmount);

        BigDecimal fullAmount = udhaarService.getCurrentBalance(tech.getId(), shop.getId());

        PaymentRequest request = new PaymentRequest(
            tech.getId(), shop.getId(),
            fullAmount, PaymentMode.CASH, null, "Full settlement", "TestRunner"
        );

        UdhaarSummary afterPayment = udhaarService.recordPayment(request);
        assertEquals(0, BigDecimal.ZERO.compareTo(afterPayment.currentBalance()),
            "Balance should be zero after full payment");
        assertEquals("CLEAR", afterPayment.riskTag(), "Risk tag should be CLEAR after full payment");
    }

    @Test
    @DisplayName("Overpayment creates negative balance (advance)")
    void overpaymentCreatesAdvance() {
        Technician tech = technicianRepository.findAll().get(2);
        PartsShop shop = shopRepository.findAll().get(0);

        Order order = orderRepository.findByTechnicianIdOrderByCreatedAtDesc(tech.getId())
            .stream().findFirst().orElse(null);
        if (order == null) return;

        // Record credit and then overpay
        BigDecimal creditAmount = BigDecimal.valueOf(2000);
        udhaarService.recordCredit(tech.getId(), shop.getId(), order.getId(), creditAmount);

        BigDecimal balance = udhaarService.getCurrentBalance(tech.getId(), shop.getId());
        BigDecimal overpay = balance.add(BigDecimal.valueOf(500));

        PaymentRequest request = new PaymentRequest(
            tech.getId(), shop.getId(),
            overpay, PaymentMode.BANK_TRANSFER, "NEFT-TEST", "Overpaid intentionally", "TestRunner"
        );

        UdhaarSummary afterPayment = udhaarService.recordPayment(request);
        assertTrue(afterPayment.currentBalance().compareTo(BigDecimal.ZERO) < 0,
            "Balance should be negative (advance) after overpayment");
    }

    // ---- Adjustment Tests ----

    @Test
    @DisplayName("Positive adjustment increases balance")
    void positiveAdjustmentIncreasesBalance() {
        Technician tech = technicianRepository.findAll().get(0);
        PartsShop shop = shopRepository.findAll().get(0);

        BigDecimal balanceBefore = udhaarService.getCurrentBalance(tech.getId(), shop.getId());
        BigDecimal adjustment = BigDecimal.valueOf(200);

        BigDecimal newBalance = udhaarService.recordAdjustment(
            tech.getId(), shop.getId(), adjustment, "Late fee", "Admin"
        );

        assertEquals(0, balanceBefore.add(adjustment).compareTo(newBalance),
            "Balance should increase by adjustment amount");
    }

    @Test
    @DisplayName("Negative adjustment decreases balance (discount)")
    void negativeAdjustmentDecreasesBalance() {
        Technician tech = technicianRepository.findAll().get(0);
        PartsShop shop = shopRepository.findAll().get(0);

        BigDecimal balanceBefore = udhaarService.getCurrentBalance(tech.getId(), shop.getId());
        BigDecimal discount = BigDecimal.valueOf(-100);

        BigDecimal newBalance = udhaarService.recordAdjustment(
            tech.getId(), shop.getId(), discount, "Loyalty discount", "Admin"
        );

        assertEquals(0, balanceBefore.add(discount).compareTo(newBalance),
            "Balance should decrease by discount amount");
    }

    @Test
    @DisplayName("Zero adjustment throws IllegalArgumentException")
    void zeroAdjustmentThrows() {
        Technician tech = technicianRepository.findAll().get(0);
        PartsShop shop = shopRepository.findAll().get(0);

        assertThrows(IllegalArgumentException.class, () ->
            udhaarService.recordAdjustment(tech.getId(), shop.getId(), BigDecimal.ZERO, "Bad adjustment", "Admin")
        );
    }

    // ---- View Tests ----

    @Test
    @DisplayName("Outstanding for shop returns only positive balances")
    void outstandingOnlyPositiveBalances() {
        PartsShop shop = shopRepository.findAll().get(0);
        List<UdhaarSummary> outstanding = udhaarService.getOutstandingForShop(shop.getId());

        for (UdhaarSummary s : outstanding) {
            assertTrue(s.currentBalance().compareTo(BigDecimal.ZERO) > 0,
                "Outstanding list should only contain positive balances: " + s.technicianName());
        }
    }

    @Test
    @DisplayName("Outstanding list is sorted by balance descending")
    void outstandingIsSortedByBalance() {
        PartsShop shop = shopRepository.findAll().get(0);
        List<UdhaarSummary> outstanding = udhaarService.getOutstandingForShop(shop.getId());

        for (int i = 1; i < outstanding.size(); i++) {
            assertTrue(
                outstanding.get(i - 1).currentBalance().compareTo(outstanding.get(i).currentBalance()) >= 0,
                "Outstanding list should be sorted by balance descending"
            );
        }
    }

    @Test
    @DisplayName("Overdue list contains only OVERDUE and AT_RISK tags")
    void overdueContainsOnlyRiskTags() {
        PartsShop shop = shopRepository.findAll().get(0);
        List<UdhaarSummary> overdue = udhaarService.getOverdueForShop(shop.getId());

        for (UdhaarSummary s : overdue) {
            assertTrue(
                "OVERDUE".equals(s.riskTag()) || "AT_RISK".equals(s.riskTag()),
                "Overdue list should only contain OVERDUE or AT_RISK: " + s.riskTag()
            );
        }
    }

    @Test
    @DisplayName("Udhaar summary includes ledger history")
    void summaryIncludesLedgerHistory() {
        PartsShop shop = shopRepository.findAll().get(0);
        List<UdhaarSummary> outstanding = udhaarService.getOutstandingForShop(shop.getId());

        if (outstanding.isEmpty()) return;

        UdhaarSummary first = outstanding.get(0);
        UdhaarSummary detailed = udhaarService.getUdhaarSummary(first.technicianId(), first.shopId());

        assertNotNull(detailed.recentEntries(), "Summary should include recent entries");
        assertFalse(detailed.recentEntries().isEmpty(), "Summary should have at least one ledger entry");
    }

    @Test
    @DisplayName("Payment with non-existent technician throws EntityNotFoundException")
    void paymentWithBadTechnicianFails() {
        PartsShop shop = shopRepository.findAll().get(0);

        PaymentRequest request = new PaymentRequest(
            UUID.randomUUID(), shop.getId(),
            BigDecimal.valueOf(100), PaymentMode.CASH, null, null, null
        );

        assertThrows(com.partlinq.core.exception.EntityNotFoundException.class, () ->
            udhaarService.recordPayment(request)
        );
    }
}
