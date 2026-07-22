package com.trading.benchmark;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MatchingEngineBenchmarkTest {
    @Test
    void runsVerifiedCrossingWorkload() {
        MatchingEngineBenchmark.Result result = MatchingEngineBenchmark.run(
            new MatchingEngineBenchmark.Config(20, 0, 2, null)
        );

        assertEquals(20, result.config().orders());
        assertTrue(result.ordersPerSecond() > 0);
        assertTrue(result.p50BatchNanos() > 0);
        assertTrue(result.p99BatchNanos() >= result.p50BatchNanos());
    }

    @Test
    void rejectsOddOrderCounts() {
        MatchingEngineBenchmark.Config config = new MatchingEngineBenchmark.Config(3, 0, 1, null);
        assertThrows(IllegalArgumentException.class, () -> MatchingEngineBenchmark.run(config));
    }
}
