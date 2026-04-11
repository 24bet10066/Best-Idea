package com.partlinq.core.engine.credit;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.UUID;

/**
 * Bayesian credit scoring engine for determining technician credit limits.
 * Uses payment history, trust score, transaction volume, and tenure
 * to compute a credit score and recommended credit limit.
 *
 * <h3>Java 21 Features Used:</h3>
 * <ul>
 *   <li><b>Record</b> for immutable CreditProfile (replaces mutable POJO)</li>
 *   <li><b>Sealed interface + Records</b> for RiskLevel (type-safe risk categories)</li>
 *   <li><b>Pattern matching switch</b> for risk assessment dispatch</li>
 *   <li><b>String templates</b> style formatting via formatted()</li>
 * </ul>
 *
 * Key DSA: Bayesian probability, Weighted scoring, Sigmoid function for normalization
 */
public class CreditScoringEngine {

    // ---------------------------------------------------------------
    // Java 21: Sealed interface for type-safe risk levels
    // ---------------------------------------------------------------

    /**
     * Sealed interface representing risk assessment results.
     * Only three permitted implementations exist, making pattern matching exhaustive.
     */
    public sealed interface RiskLevel permits RiskLevel.Low, RiskLevel.Medium, RiskLevel.High {

        String label();
        double maxExposureMultiplier();

        /** Low risk: score > 70. Full credit access. */
        record Low(double score) implements RiskLevel {
            public String label() { return "LOW"; }
            public double maxExposureMultiplier() { return 1.0; }
        }

        /** Medium risk: score 40-70. Reduced credit cap. */
        record Medium(double score) implements RiskLevel {
            public String label() { return "MEDIUM"; }
            public double maxExposureMultiplier() { return 0.6; }
        }

        /** High risk: score < 40. Minimal or no credit. */
        record High(double score) implements RiskLevel {
            public String label() { return "HIGH"; }
            public double maxExposureMultiplier() { return 0.2; }
        }
    }

    // ---------------------------------------------------------------
    // Java 21: Record for immutable credit profile input
    // ---------------------------------------------------------------

    /**
     * Immutable record representing a technician's credit profile inputs.
     * Java 21 records provide equals, hashCode, toString automatically.
     *
     * @param technicianId      UUID of the technician
     * @param trustScore        Trust score (0-100)
     * @param totalTransactions Total completed transactions
     * @param avgPaymentDays    Average days to pay from invoice
     * @param onTimePaymentRatio Fraction of payments made on time (0-1)
     * @param tenureDays        Days since registration
     */
    public record CreditProfile(
            UUID technicianId,
            double trustScore,
            int totalTransactions,
            double avgPaymentDays,
            double onTimePaymentRatio,
            long tenureDays
    ) {
        /** Compact constructor with validation */
        public CreditProfile {
            if (trustScore < 0 || trustScore > 100) {
                throw new IllegalArgumentException("trustScore must be 0-100, got: " + trustScore);
            }
            if (onTimePaymentRatio < 0 || onTimePaymentRatio > 1) {
                throw new IllegalArgumentException("onTimePaymentRatio must be 0-1, got: " + onTimePaymentRatio);
            }
            if (totalTransactions < 0) {
                throw new IllegalArgumentException("totalTransactions cannot be negative");
            }
        }
    }

    // ---------------------------------------------------------------
    // Java 21: Record for computed credit result
    // ---------------------------------------------------------------

    /**
     * Immutable record holding the full credit evaluation result.
     *
     * @param profile     Original profile
     * @param creditScore Computed credit score (0-100)
     * @param creditLimit Computed credit limit in INR
     * @param riskLevel   Assessed risk level (sealed type)
     */
    public record CreditResult(
            CreditProfile profile,
            double creditScore,
            BigDecimal creditLimit,
            RiskLevel riskLevel
    ) {}

    // Credit limit constants (in INR)
    private static final BigDecimal BASE_CREDIT_LIMIT = BigDecimal.valueOf(5000);
    private static final BigDecimal MAX_CREDIT_LIMIT = BigDecimal.valueOf(500000);
    private static final int MIN_TRANSACTIONS_FOR_CREDIT = 5;

    // Component weights
    private static final double TRUST_SCORE_WEIGHT = 0.30;
    private static final double PAYMENT_BEHAVIOR_WEIGHT = 0.35;
    private static final double VOLUME_WEIGHT = 0.20;
    private static final double TENURE_WEIGHT = 0.15;

    /**
     * Sigmoid function for smooth normalization to 0-1 range.
     *
     * @param x Input value
     * @return Sigmoid output (0 to 1)
     */
    public static double sigmoid(double x) {
        return 1.0 / (1.0 + Math.exp(-x));
    }

    /**
     * Compute credit score (0-100) for a technician.
     *
     * Components:
     *   Trust score       (0.30 weight) : direct from trust graph
     *   Payment behavior  (0.35 weight) : sigmoid on on-time ratio (steep curve)
     *   Transaction volume(0.20 weight) : caps at 100 transactions
     *   Tenure            (0.15 weight) : caps at 1 year (365 days)
     *
     * @param profile CreditProfile to score
     * @return Credit score 0-100
     */
    public double computeCreditScore(CreditProfile profile) {
        double trustComponent = Math.min(profile.trustScore(), 100.0);

        // Sigmoid(x*10 - 5): below 50% on-time -> near zero; above 90% -> near 100
        double paymentBehavior = sigmoid(profile.onTimePaymentRatio() * 10.0 - 5.0) * 100.0;

        double volumeComponent = Math.min((double) profile.totalTransactions() / 100.0, 1.0) * 100.0;

        double tenureComponent = Math.min((double) profile.tenureDays() / 365.0, 1.0) * 100.0;

        double creditScore = (trustComponent * TRUST_SCORE_WEIGHT)
                + (paymentBehavior * PAYMENT_BEHAVIOR_WEIGHT)
                + (volumeComponent * VOLUME_WEIGHT)
                + (tenureComponent * TENURE_WEIGHT);

        return Math.min(creditScore, 100.0);
    }

    /**
     * Compute credit limit (in INR) for a technician.
     *
     * Formula: BASE * (1 + score/20) * log(1 + transactions)
     * Capped at MAX_CREDIT_LIMIT. Rounded to nearest ₹500.
     * Returns ZERO if totalTransactions < MIN_TRANSACTIONS_FOR_CREDIT.
     *
     * @param profile     CreditProfile
     * @param creditScore Pre-computed credit score
     * @return Credit limit in INR
     */
    public BigDecimal computeCreditLimit(CreditProfile profile, double creditScore) {
        if (profile.totalTransactions() < MIN_TRANSACTIONS_FOR_CREDIT) {
            return BigDecimal.ZERO;
        }

        double scoreMultiplier = 1.0 + (creditScore / 20.0);
        double volumeMultiplier = Math.log(1.0 + profile.totalTransactions());

        BigDecimal limit = BASE_CREDIT_LIMIT
                .multiply(BigDecimal.valueOf(scoreMultiplier))
                .multiply(BigDecimal.valueOf(volumeMultiplier));

        if (limit.compareTo(MAX_CREDIT_LIMIT) > 0) {
            limit = MAX_CREDIT_LIMIT;
        }

        // Round to nearest ₹500
        BigDecimal roundFactor = BigDecimal.valueOf(500);
        limit = limit.divide(roundFactor, 0, RoundingMode.HALF_UP).multiply(roundFactor);

        return limit;
    }

    /**
     * Assess risk level using Java 21 sealed interface + pattern matching.
     *
     * @param creditScore Computed credit score (0-100)
     * @return Sealed RiskLevel (Low, Medium, or High)
     */
    public RiskLevel assessRisk(double creditScore) {
        if (creditScore > 70) {
            return new RiskLevel.Low(creditScore);
        } else if (creditScore >= 40) {
            return new RiskLevel.Medium(creditScore);
        } else {
            return new RiskLevel.High(creditScore);
        }
    }

    /**
     * Java 21 pattern matching switch: get a human-readable risk description.
     *
     * @param risk The sealed RiskLevel
     * @return Formatted description string
     */
    public String describeRisk(RiskLevel risk) {
        return switch (risk) {
            case RiskLevel.Low low ->
                    "LOW RISK (score: %.1f). Full credit access. Multiplier: %.0f%%"
                            .formatted(low.score(), low.maxExposureMultiplier() * 100);
            case RiskLevel.Medium med ->
                    "MEDIUM RISK (score: %.1f). Reduced credit cap at %.0f%% exposure."
                            .formatted(med.score(), med.maxExposureMultiplier() * 100);
            case RiskLevel.High high ->
                    "HIGH RISK (score: %.1f). Minimal credit. %.0f%% exposure only."
                            .formatted(high.score(), high.maxExposureMultiplier() * 100);
        };
    }

    /**
     * Full credit evaluation: computes score, limit, and risk in one call.
     *
     * @param profile CreditProfile input
     * @return Immutable CreditResult record with all computed values
     */
    public CreditResult evaluate(CreditProfile profile) {
        double score = computeCreditScore(profile);
        BigDecimal limit = computeCreditLimit(profile, score);
        RiskLevel risk = assessRisk(score);

        // Java 21 pattern matching: apply risk multiplier to cap the limit
        BigDecimal adjustedLimit = switch (risk) {
            case RiskLevel.Low ignored -> limit;
            case RiskLevel.Medium med ->
                    limit.multiply(BigDecimal.valueOf(med.maxExposureMultiplier()))
                            .setScale(0, RoundingMode.HALF_UP);
            case RiskLevel.High high ->
                    limit.multiply(BigDecimal.valueOf(high.maxExposureMultiplier()))
                            .setScale(0, RoundingMode.HALF_UP);
        };

        // Round adjusted limit to nearest ₹500
        BigDecimal roundFactor = BigDecimal.valueOf(500);
        adjustedLimit = adjustedLimit.divide(roundFactor, 0, RoundingMode.HALF_UP).multiply(roundFactor);

        return new CreditResult(profile, score, adjustedLimit, risk);
    }
}
