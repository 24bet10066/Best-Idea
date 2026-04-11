package com.partlinq.core.service.credit;

import com.partlinq.core.engine.credit.CreditScoringEngine;
import com.partlinq.core.engine.credit.CreditScoringEngine.CreditProfile;
import com.partlinq.core.engine.credit.CreditScoringEngine.CreditResult;
import com.partlinq.core.model.entity.Order;
import com.partlinq.core.model.entity.Technician;
import com.partlinq.core.repository.OrderRepository;
import com.partlinq.core.repository.TechnicianRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

/**
 * Spring service wrapping the CreditScoringEngine (Java 21 refactored).
 * Evaluates technician creditworthiness and manages credit limits.
 *
 * <h3>Java 21 Alignment:</h3>
 * <ul>
 *   <li>Uses {@link CreditProfile} record (immutable)</li>
 *   <li>Uses {@link CreditResult} record returned by evaluate()</li>
 *   <li>Pattern matches on sealed {@link CreditScoringEngine.RiskLevel}</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class CreditService {

    private final TechnicianRepository technicianRepository;
    private final OrderRepository orderRepository;
    private final CreditScoringEngine creditScoringEngine = new CreditScoringEngine();

    /**
     * Evaluate credit for a technician.
     * Builds an immutable CreditProfile record from database data,
     * feeds it to the engine, and returns the full CreditResult.
     *
     * @param technicianId UUID of the technician
     * @return CreditResult with score, limit, and sealed RiskLevel
     */
    @Cacheable(value = "creditScores", key = "#technicianId")
    public CreditResult evaluateCredit(UUID technicianId) {
        Optional<Technician> techOpt = technicianRepository.findById(technicianId);
        if (techOpt.isEmpty()) {
            log.warn("Technician not found for credit evaluation: {}", technicianId);
            return null;
        }

        Technician tech = techOpt.get();
        CreditProfile profile = buildProfile(tech);
        CreditResult result = creditScoringEngine.evaluate(profile);

        // Java 21: pattern matching on sealed RiskLevel
        String riskDescription = switch (result.riskLevel()) {
            case CreditScoringEngine.RiskLevel.Low low ->
                    "LOW (score: %.1f)".formatted(low.score());
            case CreditScoringEngine.RiskLevel.Medium med ->
                    "MEDIUM (score: %.1f)".formatted(med.score());
            case CreditScoringEngine.RiskLevel.High high ->
                    "HIGH (score: %.1f)".formatted(high.score());
        };

        log.info("Credit evaluated for technician {}: score={:.1f}, limit={}, risk={}",
                technicianId, result.creditScore(), result.creditLimit(), riskDescription);

        return result;
    }

    /**
     * Update technician's credit limit in the database based on latest evaluation.
     * Evicts cache to force recomputation on next query.
     *
     * @param technicianId UUID of the technician
     * @return Updated CreditResult, or null if technician not found
     */
    @CacheEvict(value = "creditScores", key = "#technicianId")
    public CreditResult updateCreditLimit(UUID technicianId) {
        CreditResult result = evaluateCredit(technicianId);
        if (result == null) {
            return null;
        }

        technicianRepository.findById(technicianId).ifPresent(tech -> {
            tech.setCreditLimit(result.creditLimit());
            technicianRepository.save(tech);
            log.info("Updated credit limit for technician {}: ₹{}", technicianId, result.creditLimit());
        });

        return result;
    }

    /**
     * Check if a technician can use credit for an order.
     * Verifies the order amount doesn't exceed remaining credit (limit - unpaid balance).
     *
     * @param technicianId UUID of the technician
     * @param orderAmount  Amount of the order
     * @return true if technician has sufficient credit
     */
    public boolean canExtendCredit(UUID technicianId, BigDecimal orderAmount) {
        Optional<Technician> techOpt = technicianRepository.findById(technicianId);
        if (techOpt.isEmpty()) {
            return false;
        }

        Technician tech = techOpt.get();
        BigDecimal creditLimit = tech.getCreditLimit();

        // Sum unpaid order amounts
        List<Order> unpaidOrders = orderRepository.findUnpaidOrdersByTechnicianId(technicianId);
        BigDecimal unpaidBalance = unpaidOrders.stream()
                .map(Order::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal availableCredit = creditLimit.subtract(unpaidBalance);
        boolean canExtend = availableCredit.compareTo(orderAmount) >= 0;

        log.info("Credit check for technician {}: limit=₹{}, unpaid=₹{}, available=₹{}, requested=₹{}, approved={}",
                technicianId, creditLimit, unpaidBalance, availableCredit, orderAmount, canExtend);

        return canExtend;
    }

    /**
     * Record a payment and recompute credit.
     * Uses exponential moving average for payment days.
     *
     * @param technicianId    UUID of the technician
     * @param orderId         UUID of the order being paid
     * @param daysFromInvoice Number of days from invoice to payment
     */
    public void recordPayment(UUID technicianId, UUID orderId, int daysFromInvoice) {
        technicianRepository.findById(technicianId).ifPresent(tech -> {
            // Exponential moving average: 70% old + 30% new
            double currentAvg = tech.getAvgPaymentDays();
            double newAvg = currentAvg * 0.7 + daysFromInvoice * 0.3;
            tech.setAvgPaymentDays(newAvg);
            technicianRepository.save(tech);

            log.info("Recorded payment for technician {}: days={}, new_avg_days={:.1f}",
                    technicianId, daysFromInvoice, newAvg);
        });

        // Mark order as paid (orderId can be null for standalone payments not tied to an order)
        if (orderId != null) {
            orderRepository.findById(orderId).ifPresent(order -> {
                order.setPaidAt(LocalDateTime.now());
                orderRepository.save(order);
            });
        }

        // Recompute credit limit
        updateCreditLimit(technicianId);
    }

    /**
     * Get all technicians' credit evaluations.
     *
     * @return List of CreditResult for every technician
     */
    public List<CreditResult> getAllCreditProfiles() {
        List<Technician> allTechs = technicianRepository.findAll();
        List<CreditResult> results = new ArrayList<>();

        for (Technician tech : allTechs) {
            CreditResult result = evaluateCredit(tech.getId());
            if (result != null) {
                results.add(result);
            }
        }

        return results;
    }

    /**
     * Filter credit profiles by risk level label.
     * Uses Java 21 sealed interface label() method for matching.
     *
     * @param riskLabel Risk label to filter ("LOW", "MEDIUM", "HIGH")
     * @return List of CreditResult matching the risk level
     */
    public List<CreditResult> getCreditProfilesByRisk(String riskLabel) {
        return getAllCreditProfiles().stream()
                .filter(result -> result.riskLevel().label().equalsIgnoreCase(riskLabel))
                .toList();
    }

    /**
     * Build an immutable CreditProfile record from a Technician entity.
     *
     * @param tech Technician entity
     * @return CreditProfile record
     */
    private CreditProfile buildProfile(Technician tech) {
        long tenureDays = ChronoUnit.DAYS.between(tech.getRegisteredAt(), LocalDateTime.now());
        double onTimeRatio = computeOnTimePaymentRatio(tech.getId());

        return new CreditProfile(
                tech.getId(),
                tech.getTrustScore(),
                tech.getTotalTransactions(),
                tech.getAvgPaymentDays(),
                onTimeRatio,
                tenureDays
        );
    }

    /**
     * Calculate on-time payment ratio from order history.
     * On-time = paid within 30 days of order creation.
     *
     * @param technicianId Technician UUID
     * @return Ratio 0.0-1.0
     */
    private double computeOnTimePaymentRatio(UUID technicianId) {
        List<Order> orders = orderRepository.findByTechnicianIdOrderByCreatedAtDesc(technicianId);
        if (orders.isEmpty()) {
            return 0.0;
        }

        long onTimeCount = orders.stream()
                .filter(order -> order.getPaidAt() != null)
                .filter(order -> ChronoUnit.DAYS.between(order.getCreatedAt(), order.getPaidAt()) <= 30)
                .count();

        return (double) onTimeCount / orders.size();
    }
}
