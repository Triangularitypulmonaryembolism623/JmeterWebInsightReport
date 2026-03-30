package com.jmeterwebinsightreport.report.annotation;

import com.jmeterwebinsightreport.core.sla.SlaEvaluator;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Top-level annotation data loaded from a companion JSON file.
 * Matches the report-annotations.json structure from the design doc.
 */
public class ReportAnnotations {

    private String version = "1.0";
    private String testNotes = "";
    private String verdict = "";
    private List<TimelineMarker> timelineMarkers = new ArrayList<>();
    private Map<String, String> samplerNotes = new LinkedHashMap<>();
    private Map<String, SlaThresholdConfig> slaThresholds = new LinkedHashMap<>();
    private ComparisonThresholdConfig comparisonThresholds;

    public ReportAnnotations() {
    }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public String getTestNotes() { return testNotes; }
    public void setTestNotes(String testNotes) { this.testNotes = testNotes; }

    public String getVerdict() { return verdict; }
    public void setVerdict(String verdict) { this.verdict = verdict; }

    public List<TimelineMarker> getTimelineMarkers() { return timelineMarkers; }
    public void setTimelineMarkers(List<TimelineMarker> timelineMarkers) { this.timelineMarkers = timelineMarkers; }

    public Map<String, String> getSamplerNotes() { return samplerNotes; }
    public void setSamplerNotes(Map<String, String> samplerNotes) { this.samplerNotes = samplerNotes; }

    public Map<String, SlaThresholdConfig> getSlaThresholds() { return slaThresholds; }
    public void setSlaThresholds(Map<String, SlaThresholdConfig> slaThresholds) { this.slaThresholds = slaThresholds; }

    public ComparisonThresholdConfig getComparisonThresholds() { return comparisonThresholds; }
    public void setComparisonThresholds(ComparisonThresholdConfig comparisonThresholds) { this.comparisonThresholds = comparisonThresholds; }

    /**
     * SLA thresholds per sampler (or "default" for all).
     * Keys: sampler name or "default". Values contain metric thresholds.
     */
    public static class SlaThresholdConfig implements SlaEvaluator.ThresholdConfig {
        private Double p95;
        private Double p99;
        private Double errorRate;
        private Double meanResponseTime;
        private Double apdexThreshold;

        public SlaThresholdConfig() {}

        public Double getP95() { return p95; }
        public void setP95(Double p95) { this.p95 = p95; }

        public Double getP99() { return p99; }
        public void setP99(Double p99) { this.p99 = p99; }

        public Double getErrorRate() { return errorRate; }
        public void setErrorRate(Double errorRate) { this.errorRate = errorRate; }

        public Double getMeanResponseTime() { return meanResponseTime; }
        public void setMeanResponseTime(Double meanResponseTime) { this.meanResponseTime = meanResponseTime; }

        public Double getApdexThreshold() { return apdexThreshold; }
        public void setApdexThreshold(Double apdexThreshold) { this.apdexThreshold = apdexThreshold; }
    }

    /**
     * Comparison regression thresholds from annotations JSON.
     */
    public static class ComparisonThresholdConfig {
        private Double p95PctChange;
        private Double errorRateChange;
        private Double meanPctChange;
        private Double p99PctChange;
        private Double throughputPctChange;

        public ComparisonThresholdConfig() {}

        public Double getP95PctChange() { return p95PctChange; }
        public void setP95PctChange(Double v) { this.p95PctChange = v; }
        public Double getErrorRateChange() { return errorRateChange; }
        public void setErrorRateChange(Double v) { this.errorRateChange = v; }
        public Double getMeanPctChange() { return meanPctChange; }
        public void setMeanPctChange(Double v) { this.meanPctChange = v; }
        public Double getP99PctChange() { return p99PctChange; }
        public void setP99PctChange(Double v) { this.p99PctChange = v; }
        public Double getThroughputPctChange() { return throughputPctChange; }
        public void setThroughputPctChange(Double v) { this.throughputPctChange = v; }
    }

    /**
     * A marker on the timeline (vertical line on charts).
     */
    public static class TimelineMarker {
        private long timestamp;
        private String label;
        private String type; // "info", "warning", "error", "deployment", "custom"
        private String description;
        private String color;

        public TimelineMarker() {
        }

        public long getTimestamp() { return timestamp; }
        public void setTimestamp(long timestamp) { this.timestamp = timestamp; }

        public String getLabel() { return label; }
        public void setLabel(String label) { this.label = label; }

        public String getType() { return type; }
        public void setType(String type) { this.type = type; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
    }
}
