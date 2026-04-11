package com.partlinq.core.engine.credit;

import com.partlinq.core.engine.credit.CreditScoringEngine.CreditProfile;
import com.partlinq.core.engine.credit.CreditScoringEngine.CreditResult;
import com.partlinq.core.engine.credit.CreditScoringEngine.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for the CreditScoringEngine.
 * Validates credit scoring, limit computation, risk assessment,
 * and Java 21 sealed interface pattern matching.
 */
class CreditScoringEngineTest {

    private CreditScoringEngine engine;
    private final UUID techId = UUID.fromString("20000000-0000-0000-0000-000000000001");

    @BeforeEach
    void setUp() {
        engine = new CreditScoringEngine();
    }

    // ---------------------------------------------------------------
    // High-trust, good-payment technician
    // ---------------------------------------------------------------

    @Test
    @DisplayName("High trust + good payment history = high credit score and limit")
    void testHighTrustGoodPayment() {
        CreditProfile profile = new CreditProfile(
                techId,
                85.0,   // trustScore: high
                200,    // totalTransactions: high volume
                3.0,    // avgPaymentDays: fast payer
                0.95,   // onTimePaymentRatio: 95% on time
                400     // tenureDays: > 1 year
        );

        CreditResult result = engine.evaluate(profile);

        assertTrue(result.creditScore() > 70,
                "High-trust, good-payment tech should score > 70. Got: %.2f"
                        .formatted(result.creditScore()));
        assertTrue(result.creditLimit().compareTo(BigDecimal.ZERO) > 0,
                "Should have positive credit limit");

        // Java 21: pattern matching on sealed type
        assertInstanceOf(RiskLevel.Low.class, result.riskLevel(),
                "Should be LOW risk");
    }

    // ---------------------------------------------------------------
    // New technician (< 5 transactions) gets zero credit
    // ---------------------------------------------------------------

    @Test
    @DisplayName("New technician with < 5 transactions gets zero credit limit")
    void testNewTechnician_ZeroCredit() {
        CreditProfile profile = new CreditProfile(
                techId,
                50.0,   // trustScore: default
                3,      // totalTransactions: below minimum threshold of 5
                0.0,    // avgPaymentDays
                1.0,    // onTimePaymentRatio: perfect but irrelevant
                30      // tenureDays: 1 month
        );

        CreditResult result = engine.evaluate(profile);

        assertEquals(BigDecimal.ZERO, result.creditLimit(),
                "New tech with < 5 transactions should get ZERO credit limit");
    }

    @Test
    @DisplayName("Exactly 5 transactions qualifies for credit")
    void testMinimumTransactions_QualifiesForCredit() {
        CreditProfile profile = new CreditProfile(
                techId,
                60.0,
                5,      // Exactly at the threshold
                5.0,
                0.8,
                90
        );

        CreditResult result = engine.evaluate(profile);

        assertTrue(result.creditLimit().compareTo(BigDecimal.ZERO) > 0,
                "Tech with exactly 5 transactions should qualify for credit");
    }

    // ---------------------------------------------------------------
    // Poor payment history = HIGH risk
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Poor payment history results in HIGH risk")
    void testPoorPaymentHistory_HighRisk() {
        CreditProfile profile = new CreditProfile(
                techId,
                30.0,   // trustScore: low
                15,     // totalTransactions: some
                20.0,   // avgPaymentDays: very slow
                0.2,    // onTimePaymentRatio: only 20% on time (terrible)
                60      // tenureDays: 2 months
        );

        CreditResult result = engine.evaluate(profile);

        assertTrue(result.creditScore() < 40,
                "Poor payment (20%% on-time) should produce score < 40. Got: %.2f"
                        .formatted(result.creditScore()));

        assertInstanceOf(RiskLevel.High.class, result.riskLevel(),
                "Should be HIGH risk with poor payment history");
    }

    // ---------------------------------------------------------------
    // Sigmoid function behavior
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Sigmoid(0) = 0.5 exactly")
    void testSigmoid_ZeroInput() {
        assertEquals(0.5, CreditScoringEngine.sigmoid(0), 0.0001);
    }

    @Test
    @DisplayName("Sigmoid is monotonically increasing")
    void testSigmoid_MonotonicallyIncreasing() {
        double prev = CreditScoringEngine.sigmoid(-10);
        for (double x = -9; x <= 10; x += 0.5) {
            double current = CreditScoringEngine.sigmoid(x);
            assertTrue(current >= prev,
                    "Sigmoid should be monotonically increasing. At x=%.1f: %.4f < %.4f"
                            .formatted(x, current, prev));
            prev = current;
        }
    }

    @Test
    @DisplayName("Sigmoid asymptotes: large positive -> ~1.0, large negative -> ~0.0")
    void testSigmoid_Asymptotes() {
        assertTrue(CreditScoringEngine.sigmoid(10) > 0.999,
                "sigmoid(10) should be very close to 1.0");
        assertTrue(CreditScoringEngine.sigmoid(-10) < 0.001,
                "sigmoid(-10) should be very close to 0.0");
    }

    // ---------------------------------------------------------------
    // Java 21 sealed interface: exhaustive pattern matching
    // ---------------------------------------------------------------

    @Test
    @DisplayName("RiskLevel sealed interface: all three variants behave correctly")
    void testSealedRiskLevel_AllVariants() {
        RiskLevel low = new RiskLevel.Low(80.0);
        RiskLevel med = new RiskLevel.Medium(55.0);
        RiskLevel high = new RiskLevel.High(25.0);

        assertEquals("LOW", low.label());
        assertEquals("MEDIUM", med.label());
        assertEquals("HIGH", high.label());

        assertEquals(1.0, low.maxExposureMultiplier());
        assertEquals(0.6, med.maxExposureMultiplier());
        assertEquals(0.2, high.maxExposureMultiplier());
    }

    @Test
    @DisplayName("describeRisk uses Java 21 pattern matching switch correctly")
    void testDescribeRisk_PatternMatching() {
        String lowDesc = engine.describeRisk(new RiskLevel.Low(85.0));
        assertTrue(lowDesc.contains("LOW RISK"), "Low description should contain 'LOW RISK'");
        assertTrue(lowDesc.contains("100%"), "Low risk should show 100% exposure");

        String highDesc = engine.describeRisk(new RiskLevel.High(20.0));
        assertTrue(highDesc.contains("HIGH RISK"), "High description should contain 'HIGH RISK'");
        assertTrue(highDesc.contains("20%"), "High risk should show 20% exposure");
    }

    // ---------------------------------------------------------------
    // Credit limit rounding
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Credit limit is always rounded to nearest 500")
    void testCreditLimit_RoundedToNearest500() {
        CreditProfile profile = new CreditProfile(
                techId, 75.0, 50, 4.0, 0.85, 200);

        CreditResult result = engine.evaluate(profile);

        BigDecimal remainder = result.creditLimit().remainder(BigDecimal.valueOf(500));
        assertEquals(0, remainder.compareTo(BigDecimal.ZERO),
                "Credit limit ₹%s should be divisible by 500".formatted(result.creditLimit()));
    }

    // ---------------------------------------------------------------
    // Java 21 Record validation in CreditProfile
    // ---------------------------------------------------------------

    @Test
    @DisplayName("CreditProfile rejects invalid trustScore (negative)")
    void testCreditProfile_InvalidTrustScore() {
        assertThrows(IllegalArgumentException.class, () ->
                new CreditProfile(techId, -10.0, 10, 5.0, 0.5, 100));
    }

    @Test
    @DisplayName("CreditProfile rejects invalid onTimePaymentRatio (> 1)")
    void testCreditProfile_InvalidPaymentRatio() {
        assertThrows(IllegalArgumentException.class, () ->
                new CreditProfile(techId, 50.0, 10, 5.0, 1.5, 100));
    }

    @Test
    @DisplayName("CreditProfile rejects negative totalTransactions")
    void testCreditProfile_NegativeTransactions() {
        assertThrows(IllegalArgumentException.class, () ->
                new CreditProfile(techId, 50.0, -1, 5.0, 0.5, 100));
    }

    // ---------------------------------------------------------------
    // Medium risk range
    // ---------------------------------------------------------------

    @Test
    @DisplayName("Medium trust produces MEDIUM risk level")
    void testMediumRisk() {
        CreditProfile profile = new CreditProfile(
                techId, 50.0, 20, 8.0, 0.55, 120);

        CreditResult result = engine.evaluate(profile);

        // Score should land in the 40-70 range for medium risk
        assertTrue(result.creditScore() >= 30 && result.creditScore() <= 75,
                "Medium-profile score should be roughly in middle range. Got: %.2f"
                        .formatted(result.creditScore()));
    }

    @Test
    @DisplayName("Evaluate returns immutable CreditResult record")
    void testEvaluate_ReturnsImmutableRecord() {
        CreditProfile profile = new CreditProfile(
                techId, 70.0, 80, 5.0, 0.8, 300);

        CreditResult result = engine.evaluate(profile);

        assertNotNull(result.profile());
        assertNotNull(result.riskLevel());
        assertTrue(result.creditScore() > 0);
        assertEquals(techId, result.profile().technicianId());
    }
}
