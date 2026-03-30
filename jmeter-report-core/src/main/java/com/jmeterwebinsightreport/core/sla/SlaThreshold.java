package com.jmeterwebinsightreport.core.sla;

/**
 * A single SLA threshold definition — e.g., "p95 response time < 2000ms".
 */
public class SlaThreshold {

    private String metricName;
    private String samplerPattern;
    private double warnValue;
    private double failValue;
    private ThresholdOperator operator;

    public enum ThresholdOperator {
        LESS_THAN,
        GREATER_THAN,
        LESS_THAN_OR_EQUAL,
        GREATER_THAN_OR_EQUAL
    }

    public SlaThreshold() {
    }

    public String getMetricName() {
        return metricName;
    }

    public void setMetricName(String metricName) {
        this.metricName = metricName;
    }

    public String getSamplerPattern() {
        return samplerPattern;
    }

    public void setSamplerPattern(String samplerPattern) {
        this.samplerPattern = samplerPattern;
    }

    public double getWarnValue() {
        return warnValue;
    }

    public void setWarnValue(double warnValue) {
        this.warnValue = warnValue;
    }

    public double getFailValue() {
        return failValue;
    }

    public void setFailValue(double failValue) {
        this.failValue = failValue;
    }

    public ThresholdOperator getOperator() {
        return operator;
    }

    public void setOperator(ThresholdOperator operator) {
        this.operator = operator;
    }
}
