package com.jmeterwebinsightreport.core.comparison;

/**
 * Configurable thresholds for regression detection in baseline comparison.
 * A value of -1 means the metric is disabled (not checked).
 */
public class ComparisonThresholds {

    private double p95PctChangeThreshold = 10.0;
    private double errorRateChangeThreshold = 2.0;
    private double meanPctChangeThreshold = -1;
    private double p99PctChangeThreshold = -1;
    private double throughputPctChangeThreshold = -1;

    public ComparisonThresholds() {}

    public double getP95PctChangeThreshold() { return p95PctChangeThreshold; }
    public void setP95PctChangeThreshold(double v) { this.p95PctChangeThreshold = v; }

    public double getErrorRateChangeThreshold() { return errorRateChangeThreshold; }
    public void setErrorRateChangeThreshold(double v) { this.errorRateChangeThreshold = v; }

    public double getMeanPctChangeThreshold() { return meanPctChangeThreshold; }
    public void setMeanPctChangeThreshold(double v) { this.meanPctChangeThreshold = v; }

    public double getP99PctChangeThreshold() { return p99PctChangeThreshold; }
    public void setP99PctChangeThreshold(double v) { this.p99PctChangeThreshold = v; }

    public double getThroughputPctChangeThreshold() { return throughputPctChangeThreshold; }
    public void setThroughputPctChangeThreshold(double v) { this.throughputPctChangeThreshold = v; }

    public boolean isEnabled(double threshold) {
        return threshold >= 0;
    }
}
