package com.jmeterwebinsightreport.core.comparison;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ComparisonThresholdsTest {

    @Test
    void defaultValues_areCorrect() {
        ComparisonThresholds thresholds = new ComparisonThresholds();

        assertEquals(10.0, thresholds.getP95PctChangeThreshold());
        assertEquals(2.0, thresholds.getErrorRateChangeThreshold());
        assertEquals(-1, thresholds.getMeanPctChangeThreshold());
        assertEquals(-1, thresholds.getP99PctChangeThreshold());
        assertEquals(-1, thresholds.getThroughputPctChangeThreshold());
    }

    @Test
    void isEnabled_positiveValue_returnsTrue() {
        ComparisonThresholds thresholds = new ComparisonThresholds();

        assertTrue(thresholds.isEnabled(10.0));
    }

    @Test
    void isEnabled_zeroValue_returnsTrue() {
        ComparisonThresholds thresholds = new ComparisonThresholds();

        assertTrue(thresholds.isEnabled(0.0));
    }

    @Test
    void isEnabled_negativeOne_returnsFalse() {
        ComparisonThresholds thresholds = new ComparisonThresholds();

        assertFalse(thresholds.isEnabled(-1.0));
    }

    @Test
    void isEnabled_negativeValue_returnsFalse() {
        ComparisonThresholds thresholds = new ComparisonThresholds();

        assertFalse(thresholds.isEnabled(-5.0));
    }

    @Test
    void setAndGet_allFields() {
        ComparisonThresholds thresholds = new ComparisonThresholds();

        thresholds.setP95PctChangeThreshold(15.0);
        assertEquals(15.0, thresholds.getP95PctChangeThreshold());

        thresholds.setErrorRateChangeThreshold(3.5);
        assertEquals(3.5, thresholds.getErrorRateChangeThreshold());

        thresholds.setMeanPctChangeThreshold(20.0);
        assertEquals(20.0, thresholds.getMeanPctChangeThreshold());

        thresholds.setP99PctChangeThreshold(25.0);
        assertEquals(25.0, thresholds.getP99PctChangeThreshold());

        thresholds.setThroughputPctChangeThreshold(10.0);
        assertEquals(10.0, thresholds.getThroughputPctChangeThreshold());
    }
}
