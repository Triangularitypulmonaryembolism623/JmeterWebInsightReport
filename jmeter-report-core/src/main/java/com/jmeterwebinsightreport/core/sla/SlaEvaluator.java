package com.jmeterwebinsightreport.core.sla;

import com.jmeterwebinsightreport.core.model.SamplerStatistics;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Evaluates sampler statistics against configured SLA thresholds.
 * Thresholds are provided as a map: sampler name (or "default") -> metric limits.
 */
public class SlaEvaluator {

    private final SlaConfiguration configuration;

    public SlaEvaluator(SlaConfiguration configuration) {
        this.configuration = configuration;
    }

    /**
     * Evaluate SLA for each sampler. Returns map of samplerName -> SlaResult.
     */
    public Map<String, SlaResult> evaluateAll(List<SamplerStatistics> statistics,
                                               Map<String, SlaThresholdValues> thresholds) {
        Map<String, SlaResult> results = new LinkedHashMap<>();
        if (thresholds == null || thresholds.isEmpty()) {
            return results;
        }

        SlaThresholdValues defaults = thresholds.get("default");

        for (SamplerStatistics stat : statistics) {
            SlaThresholdValues th = thresholds.getOrDefault(stat.getSamplerName(), defaults);
            if (th == null) continue;

            SlaResult result = evaluate(stat, th);
            results.put(stat.getSamplerName(), result);
        }
        return results;
    }

    private SlaResult evaluate(SamplerStatistics stat, SlaThresholdValues th) {
        SlaStatus worst = SlaStatus.PASS;
        Map<String, SlaStatus> metricStatuses = new LinkedHashMap<>();

        if (th.getP95() != null) {
            SlaStatus s = checkLessThan(stat.getPercentile95(), th.getP95());
            metricStatuses.put("p95", s);
            worst = worstOf(worst, s);
        }
        if (th.getP99() != null) {
            SlaStatus s = checkLessThan(stat.getPercentile99(), th.getP99());
            metricStatuses.put("p99", s);
            worst = worstOf(worst, s);
        }
        if (th.getErrorRate() != null) {
            SlaStatus s = checkLessThan(stat.getErrorRate(), th.getErrorRate());
            metricStatuses.put("errorRate", s);
            worst = worstOf(worst, s);
        }
        if (th.getMeanResponseTime() != null) {
            SlaStatus s = checkLessThan(stat.getMeanResponseTime(), th.getMeanResponseTime());
            metricStatuses.put("mean", s);
            worst = worstOf(worst, s);
        }

        return new SlaResult(worst, metricStatuses);
    }

    /**
     * Check if actual < threshold. PASS if under 80%, WARN if 80-100%, FAIL if over.
     */
    private SlaStatus checkLessThan(double actual, double threshold) {
        if (actual > threshold) return SlaStatus.FAIL;
        if (actual > threshold * 0.8) return SlaStatus.WARN;
        return SlaStatus.PASS;
    }

    private SlaStatus worstOf(SlaStatus a, SlaStatus b) {
        if (a == SlaStatus.FAIL || b == SlaStatus.FAIL) return SlaStatus.FAIL;
        if (a == SlaStatus.WARN || b == SlaStatus.WARN) return SlaStatus.WARN;
        return SlaStatus.PASS;
    }

    /**
     * Get the overall SLA status across all samplers.
     */
    public SlaStatus getOverallStatus(Map<String, SlaResult> results) {
        SlaStatus worst = SlaStatus.PASS;
        for (SlaResult r : results.values()) {
            worst = worstOf(worst, r.getOverallStatus());
        }
        return worst;
    }

    /**
     * Result of SLA evaluation for a single sampler.
     */
    public static class SlaResult {
        private final SlaStatus overallStatus;
        private final Map<String, SlaStatus> metricStatuses;

        public SlaResult(SlaStatus overallStatus, Map<String, SlaStatus> metricStatuses) {
            this.overallStatus = overallStatus;
            this.metricStatuses = metricStatuses;
        }

        public SlaStatus getOverallStatus() { return overallStatus; }
        public Map<String, SlaStatus> getMetricStatuses() { return metricStatuses; }
    }

    /**
     * Convert annotation SLA threshold configs to SlaThresholdValues map.
     * Shared utility to avoid duplicating this conversion logic.
     */
    public static Map<String, SlaThresholdValues> convertThresholds(
            Map<String, ? extends ThresholdConfig> configs) {
        Map<String, SlaThresholdValues> thresholds = new LinkedHashMap<>(configs.size());
        for (Map.Entry<String, ? extends ThresholdConfig> entry : configs.entrySet()) {
            SlaThresholdValues tv = new SlaThresholdValues();
            tv.setP95(entry.getValue().getP95());
            tv.setP99(entry.getValue().getP99());
            tv.setErrorRate(entry.getValue().getErrorRate());
            tv.setMeanResponseTime(entry.getValue().getMeanResponseTime());
            thresholds.put(entry.getKey(), tv);
        }
        return thresholds;
    }

    /**
     * Interface for threshold config sources (allows both ReportAnnotations.SlaThresholdConfig
     * and SlaThresholdValues to be used with convertThresholds).
     */
    public interface ThresholdConfig {
        Double getP95();
        Double getP99();
        Double getErrorRate();
        Double getMeanResponseTime();
    }

    /**
     * Simple threshold values (used from annotation JSON).
     */
    public static class SlaThresholdValues implements ThresholdConfig {
        private Double p95;
        private Double p99;
        private Double errorRate;
        private Double meanResponseTime;

        public SlaThresholdValues() {}

        public Double getP95() { return p95; }
        public void setP95(Double p95) { this.p95 = p95; }
        public Double getP99() { return p99; }
        public void setP99(Double p99) { this.p99 = p99; }
        public Double getErrorRate() { return errorRate; }
        public void setErrorRate(Double errorRate) { this.errorRate = errorRate; }
        public Double getMeanResponseTime() { return meanResponseTime; }
        public void setMeanResponseTime(Double meanResponseTime) { this.meanResponseTime = meanResponseTime; }
    }
}
