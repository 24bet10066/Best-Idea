package com.partlinq.core;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Smoke test verifying the Spring application context loads correctly.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PartLinQApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that all beans, configurations, and dependencies wire up correctly
    }
}
