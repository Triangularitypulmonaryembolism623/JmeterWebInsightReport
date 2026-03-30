package com.jmeterwebinsightreport.core.stats;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TDigestPercentileCalculatorTest {

    @Test
    void shouldReturnZeroForEmptyDigest() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        assertEquals(0.0, calc.getPercentile(95));
        assertEquals(0, calc.getCount());
    }

    @Test
    void shouldCalculatePercentilesForKnownData() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 1; i <= 100; i++) {
            calc.addValue(i);
        }
        assertEquals(100, calc.getCount());
        // T-Digest is approximate — allow some tolerance
        assertEquals(50, calc.getPercentile(50), 2.0);
        assertEquals(95, calc.getPercentile(95), 2.0);
    }

    @Test
    void shouldCalculateP90_withinTolerance() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 1; i <= 1000; i++) {
            calc.addValue(i);
        }
        assertEquals(1000, calc.getCount());
        assertEquals(900, calc.getPercentile(90), 20.0);
    }

    @Test
    void shouldCalculateP99_withinTolerance() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 1; i <= 1000; i++) {
            calc.addValue(i);
        }
        assertEquals(1000, calc.getCount());
        assertEquals(990, calc.getPercentile(99), 20.0);
    }

    @Test
    void shouldHandleRepeatedValues() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 0; i < 100; i++) {
            calc.addValue(42);
        }
        assertEquals(100, calc.getCount());
        assertEquals(42.0, calc.getPercentile(50), 0.01);
        assertEquals(42.0, calc.getPercentile(90), 0.01);
        assertEquals(42.0, calc.getPercentile(95), 0.01);
        assertEquals(42.0, calc.getPercentile(99), 0.01);
    }

    @Test
    void shouldHandleExtremeSkew() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 0; i < 99; i++) {
            calc.addValue(10);
        }
        calc.addValue(10000);
        assertEquals(100, calc.getCount());
        // P50 should be close to 10 (the majority value)
        assertEquals(10.0, calc.getPercentile(50), 5.0);
        // P99 should capture the outlier
        assertTrue(calc.getPercentile(99) > 100,
                "P99 should reflect the outlier value for extremely skewed data");
    }

    @Test
    void shouldCalculateCdf_atMedian() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 1; i <= 100; i++) {
            calc.addValue(i);
        }
        assertEquals(0.50, calc.getCdf(50), 0.05);
    }

    @Test
    void shouldCalculateCdf_atP95() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 1; i <= 100; i++) {
            calc.addValue(i);
        }
        assertEquals(0.95, calc.getCdf(95), 0.05);
    }

    @Test
    void shouldCalculateCdf_belowMin() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 1; i <= 100; i++) {
            calc.addValue(i);
        }
        assertEquals(0.0, calc.getCdf(0), 0.01);
    }

    @Test
    void shouldCalculateCdf_aboveMax() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        for (int i = 1; i <= 100; i++) {
            calc.addValue(i);
        }
        assertEquals(1.0, calc.getCdf(200), 0.01);
    }

    @Test
    void shouldCalculateCdf_emptyDigest() {
        TDigestPercentileCalculator calc = new TDigestPercentileCalculator();
        assertEquals(0.0, calc.getCdf(50));
    }
}
