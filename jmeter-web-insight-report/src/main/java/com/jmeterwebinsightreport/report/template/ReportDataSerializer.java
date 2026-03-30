package com.jmeterwebinsightreport.report.template;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.jmeterwebinsightreport.core.comparison.ComparisonResult;
import com.jmeterwebinsightreport.core.comparison.ComparisonThresholds;
import com.jmeterwebinsightreport.core.model.*;
import com.jmeterwebinsightreport.core.model.TimeSeriesBucket.SamplerBucketData;
import com.jmeterwebinsightreport.core.sla.SlaEvaluator;
import com.jmeterwebinsightreport.core.sla.SlaStatus;
import com.jmeterwebinsightreport.report.annotation.ReportAnnotations;

import java.io.IOException;
import java.time.Instant;
import java.util.*;

/**
 * Serializes ReportData to JSON for embedding in the HTML report (consumed by ECharts).
 */
public class ReportDataSerializer {

    private final ObjectMapper objectMapper;

    public ReportDataSerializer() {
        this.objectMapper = new ObjectMapper();

        // Register Instant serializer → epoch millis for JavaScript
        SimpleModule module = new SimpleModule();
        module.addSerializer(Instant.class, new JsonSerializer<Instant>() {
            @Override
            public void serialize(Instant value, JsonGenerator gen, SerializerProvider provider) throws IOException {
                gen.writeNumber(value.toEpochMilli());
            }
        });
        objectMapper.registerModule(module);
    }

    /**
     * Serialize report data to JSON string for embedding in the template.
     */
    public String serializeToJson(ReportData reportData) throws JsonProcessingException {
        return objectMapper.writeValueAsString(reportData);
    }

    /**
     * Serialize chart data with annotations and comparison.
     */
    public String serializeChartData(ReportData reportData, ReportAnnotations annotations,
                                      ComparisonResult comparison) throws JsonProcessingException {
        Map<String, Object> chartData = buildChartDataMap(reportData);
        addAnnotations(chartData, annotations);
        addComparison(chartData, comparison);
        return objectMapper.writeValueAsString(chartData);
    }

    /**
     * Serialize chart data with annotations.
     */
    public String serializeChartData(ReportData reportData, ReportAnnotations annotations) throws JsonProcessingException {
        Map<String, Object> chartData = buildChartDataMap(reportData);
        addAnnotations(chartData, annotations);
        return objectMapper.writeValueAsString(chartData);
    }

    /**
     * Serialize chart-specific data subset to JSON — compact format for ECharts.
     */
    public String serializeChartData(ReportData reportData) throws JsonProcessingException {
        return objectMapper.writeValueAsString(buildChartDataMap(reportData));
    }

    /**
     * Build chart data map to file (avoids intermediate String for external data mode).
     */
    public void writeChartDataToFile(ReportData reportData, ReportAnnotations annotations,
                                      ComparisonResult comparison, java.io.File outputFile) throws IOException {
        Map<String, Object> chartData = buildChartDataMap(reportData);
        addAnnotations(chartData, annotations);
        addComparison(chartData, comparison);
        objectMapper.writeValue(outputFile, chartData);
    }

    private void addAnnotations(Map<String, Object> chartData, ReportAnnotations annotations) {
        if (annotations == null) {
            return;
        }

        List<Map<String, Object>> markers = new ArrayList<>();
        for (ReportAnnotations.TimelineMarker m : annotations.getTimelineMarkers() != null ? annotations.getTimelineMarkers() : java.util.Collections.<ReportAnnotations.TimelineMarker>emptyList()) {
            Map<String, Object> marker = new LinkedHashMap<>();
            marker.put("timestamp", m.getTimestamp());
            marker.put("label", m.getLabel());
            marker.put("type", m.getType());
            marker.put("description", m.getDescription());
            marker.put("color", m.getColor());
            markers.add(marker);
        }
        chartData.put("timelineMarkers", markers);

        // SLA thresholds for chart marklines
        if (annotations.getSlaThresholds() != null && !annotations.getSlaThresholds().isEmpty()) {
            Map<String, Object> slaData = new LinkedHashMap<>(annotations.getSlaThresholds().size());
            for (Map.Entry<String, ReportAnnotations.SlaThresholdConfig> entry : annotations.getSlaThresholds().entrySet()) {
                Map<String, Object> th = new LinkedHashMap<>();
                if (entry.getValue().getP95() != null) th.put("p95", entry.getValue().getP95());
                if (entry.getValue().getP99() != null) th.put("p99", entry.getValue().getP99());
                if (entry.getValue().getErrorRate() != null) th.put("errorRate", entry.getValue().getErrorRate());
                if (entry.getValue().getMeanResponseTime() != null) th.put("mean", entry.getValue().getMeanResponseTime());
                slaData.put(entry.getKey(), th);
            }
            chartData.put("slaThresholds", slaData);
        }
    }

    private Map<String, Map<String, List<Object>>> buildPerSamplerSeries(List<TimeSeriesBucket> timeSeries) {
        Set<String> allSamplerNames = new LinkedHashSet<>();
        for (TimeSeriesBucket bucket : timeSeries) {
            allSamplerNames.addAll(bucket.getPerSamplerData().keySet());
        }

        int numBuckets = timeSeries.size();
        String[] metricKeys = {"responseTime", "throughput", "errorRate", "p50", "p90", "p95",
                "p99", "max", "connectTime", "latency", "receivedBytes", "sentBytes"};
        int numMetrics = metricKeys.length;

        Map<String, Object[][]> samplerArrays = new LinkedHashMap<>(allSamplerNames.size());
        for (String name : allSamplerNames) {
            samplerArrays.put(name, new Object[numMetrics][numBuckets]);
        }

        for (int bucketIdx = 0; bucketIdx < numBuckets; bucketIdx++) {
            TimeSeriesBucket bucket = timeSeries.get(bucketIdx);
            for (Map.Entry<String, SamplerBucketData> entry : bucket.getPerSamplerData().entrySet()) {
                Object[][] arrays = samplerArrays.get(entry.getKey());
                SamplerBucketData sd = entry.getValue();
                arrays[0][bucketIdx] = round2(sd.getMeanResponseTime());
                arrays[1][bucketIdx] = round2(sd.getThroughput());
                arrays[2][bucketIdx] = round2(sd.getErrorRate());
                arrays[3][bucketIdx] = round2(sd.getP50());
                arrays[4][bucketIdx] = round2(sd.getP90());
                arrays[5][bucketIdx] = round2(sd.getP95());
                arrays[6][bucketIdx] = round2(sd.getP99());
                arrays[7][bucketIdx] = round2(sd.getMax());
                arrays[8][bucketIdx] = round2(sd.getConnectTime());
                arrays[9][bucketIdx] = round2(sd.getLatency());
                arrays[10][bucketIdx] = round2(sd.getReceivedBytes());
                arrays[11][bucketIdx] = round2(sd.getSentBytes());
            }
        }

        Map<String, Map<String, List<Object>>> result = new LinkedHashMap<>(allSamplerNames.size());
        for (String name : allSamplerNames) {
            Object[][] arrays = samplerArrays.get(name);
            Map<String, List<Object>> series = new LinkedHashMap<>(numMetrics);
            for (int m = 0; m < numMetrics; m++) {
                series.put(metricKeys[m], Arrays.asList(arrays[m]));
            }
            result.put(name, series);
        }
        return result;
    }

    private void addComparison(Map<String, Object> chartData, ComparisonResult comparison) {
        if (comparison == null) return;

        // Add baseline time-series data including per-sampler series
        ReportData baselineData = comparison.getBaseline();
        if (baselineData.getTimeSeries() != null && !baselineData.getTimeSeries().isEmpty()) {
            List<TimeSeriesBucket> baseTs = baselineData.getTimeSeries();
            List<Long> baseTimestamps = new ArrayList<>(baseTs.size());
            List<Double> baseMeanRT = new ArrayList<>(baseTs.size());
            List<Double> baseThroughputs = new ArrayList<>(baseTs.size());
            List<Double> baseErrorRates = new ArrayList<>(baseTs.size());
            for (TimeSeriesBucket b : baseTs) {
                baseTimestamps.add(b.getTimestamp().toEpochMilli());
                baseMeanRT.add(round2(b.getMeanResponseTime()));
                baseThroughputs.add(round2(b.getThroughput()));
                baseErrorRates.add(round2(b.getErrorRate()));
            }
            Map<String, Object> baselineChart = new LinkedHashMap<>();
            baselineChart.put("timestamps", baseTimestamps);
            baselineChart.put("meanResponseTimes", baseMeanRT);
            baselineChart.put("throughputs", baseThroughputs);
            baselineChart.put("errorRates", baseErrorRates);
            baselineChart.put("perSamplerSeries", buildPerSamplerSeries(baseTs));
            chartData.put("baseline", baselineChart);
        }

        // Add sampler diffs
        List<ComparisonResult.SamplerDiff> samplerDiffs = comparison.getSamplerDiffs();
        List<Map<String, Object>> diffs = new ArrayList<>(samplerDiffs.size());
        for (ComparisonResult.SamplerDiff diff : samplerDiffs) {
            Map<String, Object> d = new LinkedHashMap<>();
            d.put("name", diff.getSamplerName());
            d.put("isNew", diff.isNew());
            d.put("regression", diff.isRegression());

            SamplerStatistics curr = diff.getCurrent();
            d.put("currSamples", curr.getSampleCount());
            d.put("currErrors", curr.getErrorCount());
            d.put("currErrorRate", round2(curr.getErrorRate()));
            d.put("currMean", round2(curr.getMeanResponseTime()));
            d.put("currMedian", round2(curr.getMedianResponseTime()));
            d.put("currP90", round2(curr.getPercentile90()));
            d.put("currP95", round2(curr.getPercentile95()));
            d.put("currP99", round2(curr.getPercentile99()));
            d.put("currMin", round2(curr.getMinResponseTime()));
            d.put("currMax", round2(curr.getMaxResponseTime()));
            d.put("currThroughput", round2(curr.getThroughput()));
            d.put("currRecvKBs", round2(curr.getReceivedBytesPerSec() / 1024.0));

            if (!diff.isNew()) {
                SamplerStatistics base = diff.getBaseline();
                d.put("deltaSamples", curr.getSampleCount() - base.getSampleCount());
                d.put("deltaErrors", curr.getErrorCount() - base.getErrorCount());
                d.put("deltaErrorRate", round2(curr.getErrorRate() - base.getErrorRate()));
                d.put("deltaMean", round2(curr.getMeanResponseTime() - base.getMeanResponseTime()));
                d.put("deltaMedian", round2(curr.getMedianResponseTime() - base.getMedianResponseTime()));
                d.put("deltaP90", round2(curr.getPercentile90() - base.getPercentile90()));
                d.put("deltaP95", round2(curr.getPercentile95() - base.getPercentile95()));
                d.put("deltaP99", round2(curr.getPercentile99() - base.getPercentile99()));
                d.put("deltaMin", round2(curr.getMinResponseTime() - base.getMinResponseTime()));
                d.put("deltaMax", round2(curr.getMaxResponseTime() - base.getMaxResponseTime()));
                d.put("deltaThroughput", round2(curr.getThroughput() - base.getThroughput()));
                d.put("deltaRecvKBs", round2((curr.getReceivedBytesPerSec() - base.getReceivedBytesPerSec()) / 1024.0));
                d.put("meanPctChange", round2(diff.getMeanPctChange()));
                d.put("p95PctChange", round2(diff.getP95PctChange()));
            }
            diffs.add(d);
        }
        chartData.put("comparisonDiffs", diffs);
        chartData.put("hasComparison", true);

        // Serialize thresholds for frontend live adjustment
        ComparisonThresholds th = comparison.getThresholds();
        if (th != null) {
            Map<String, Object> thMap = new LinkedHashMap<>();
            thMap.put("p95PctChange", th.getP95PctChangeThreshold());
            thMap.put("errorRateChange", th.getErrorRateChangeThreshold());
            thMap.put("meanPctChange", th.getMeanPctChangeThreshold());
            thMap.put("p99PctChange", th.getP99PctChangeThreshold());
            thMap.put("throughputPctChange", th.getThroughputPctChangeThreshold());
            chartData.put("comparisonThresholds", thMap);
        }
    }

    /**
     * Build chart data as a Map — core logic shared by all serializeChartData overloads.
     */
    private Map<String, Object> buildChartDataMap(ReportData reportData) {
        Map<String, Object> chartData = new LinkedHashMap<>();

        // Time-series arrays (parallel arrays for efficient JS consumption)
        List<TimeSeriesBucket> timeSeries = reportData.getTimeSeries();
        List<Long> timestamps = new ArrayList<>(timeSeries.size());
        List<Double> meanResponseTimes = new ArrayList<>(timeSeries.size());
        List<Double> throughputs = new ArrayList<>(timeSeries.size());
        List<Double> errorRates = new ArrayList<>(timeSeries.size());
        List<Integer> activeThreads = new ArrayList<>(timeSeries.size());

        for (TimeSeriesBucket bucket : timeSeries) {
            timestamps.add(bucket.getTimestamp().toEpochMilli());
            meanResponseTimes.add(round2(bucket.getMeanResponseTime()));
            throughputs.add(round2(bucket.getThroughput()));
            errorRates.add(round2(bucket.getErrorRate()));
            activeThreads.add(bucket.getActiveThreads());
        }

        chartData.put("timestamps", timestamps);
        chartData.put("meanResponseTimes", meanResponseTimes);
        chartData.put("throughputs", throughputs);
        chartData.put("errorRates", errorRates);
        chartData.put("activeThreads", activeThreads);

        // Sampler summary for bar/comparison charts — includes p50 and max
        List<Map<String, Object>> samplers = new ArrayList<>(
                reportData.getSamplerStatistics() != null ? reportData.getSamplerStatistics().size() : 0);
        if (reportData.getSamplerStatistics() != null) {
            for (SamplerStatistics stat : reportData.getSamplerStatistics()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", stat.getSamplerName());
                s.put("count", stat.getSampleCount());
                s.put("mean", round2(stat.getMeanResponseTime()));
                s.put("median", round2(stat.getMedianResponseTime()));
                s.put("p90", round2(stat.getPercentile90()));
                s.put("p95", round2(stat.getPercentile95()));
                s.put("p99", round2(stat.getPercentile99()));
                s.put("min", round2(stat.getMinResponseTime()));
                s.put("max", round2(stat.getMaxResponseTime()));
                s.put("errorRate", round2(stat.getErrorRate()));
                s.put("errorCount", stat.getErrorCount());
                s.put("throughput", round2(stat.getThroughput()));
                s.put("stdDev", round2(stat.getStandardDeviation()));
                s.put("receivedBytesPerSec", round2(stat.getReceivedBytesPerSec()));
                s.put("sentBytesPerSec", round2(stat.getSentBytesPerSec()));
                s.put("meanConnectTime", round2(stat.getMeanConnectTime()));
                s.put("meanLatency", round2(stat.getMeanLatency()));
                s.put("apdex", stat.getApdexScore());
                s.put("isTransactionController", stat.isTransactionController());
                if (stat.getParentSamplerName() != null) {
                    s.put("parent", stat.getParentSamplerName());
                }
                if (stat.getChildSamplerNames() != null) {
                    s.put("children", stat.getChildSamplerNames());
                }
                samplers.add(s);
            }
        }
        chartData.put("samplers", samplers);

        // Per-sampler time-series for individual chart lines
        chartData.put("perSamplerSeries", buildPerSamplerSeries(timeSeries));

        // Collect sampler names for histogram
        Set<String> allSamplerNames = new LinkedHashSet<>();
        for (TimeSeriesBucket bucket : timeSeries) {
            allSamplerNames.addAll(bucket.getPerSamplerData().keySet());
        }

        // Response time distribution histogram (global and per-sampler bins)
        chartData.put("responseTimeDistribution", ChartDataBuilder.buildResponseTimeDistribution(timeSeries, allSamplerNames));

        // Heatmap data (per-sampler histogram bins over time)
        chartData.put("heatmapData", ChartDataBuilder.buildHeatmapData(timeSeries, allSamplerNames));

        // Response codes over time
        chartData.put("responseCodesOverTime", ChartDataBuilder.buildResponseCodesOverTime(reportData));

        // Error breakdown data for Error tab charts
        if (reportData.getErrorSummaries() != null && !reportData.getErrorSummaries().isEmpty()) {
            List<Map<String, Object>> errorsByType = new ArrayList<>();
            List<Map<String, Object>> errorSummaries = new ArrayList<>();
            for (ErrorSummary es : reportData.getErrorSummaries()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("code", es.getResponseCode());
                e.put("message", es.getResponseMessage());
                e.put("count", es.getOccurrenceCount());
                e.put("pctErrors", round2(es.getPercentageOfErrors()));
                errorsByType.add(e);

                // Full error summary for CSV export (includes sampler name)
                Map<String, Object> full = new LinkedHashMap<>();
                full.put("samplerName", es.getSamplerName());
                full.put("responseCode", es.getResponseCode());
                full.put("responseMessage", es.getResponseMessage());
                full.put("occurrenceCount", es.getOccurrenceCount());
                full.put("percentageOfErrors", round2(es.getPercentageOfErrors()));
                full.put("percentageOfAllSamples", round2(es.getPercentageOfAllSamples()));
                errorSummaries.add(full);
            }
            chartData.put("errorsByType", errorsByType);
            chartData.put("errorSummaries", errorSummaries);
        }

        // Errors by sampler
        if (reportData.getSamplerStatistics() != null) {
            List<Map<String, Object>> errorsBySampler = new ArrayList<>();
            for (SamplerStatistics stat : reportData.getSamplerStatistics()) {
                if (stat.getErrorCount() > 0) {
                    Map<String, Object> e = new LinkedHashMap<>();
                    e.put("name", stat.getSamplerName());
                    e.put("count", stat.getErrorCount());
                    e.put("rate", round2(stat.getErrorRate()));
                    errorsBySampler.add(e);
                }
            }
            chartData.put("errorsBySampler", errorsBySampler);
        }

        // Error records for detail expansion
        if (reportData.getErrorRecords() != null && !reportData.getErrorRecords().isEmpty()) {
            List<Map<String, Object>> errorRecords = new ArrayList<>();
            for (ErrorRecord er : reportData.getErrorRecords()) {
                Map<String, Object> e = new LinkedHashMap<>();
                e.put("timestamp", er.getTimestamp());
                e.put("sampler", er.getSamplerName());
                e.put("code", er.getResponseCode());
                e.put("message", er.getResponseMessage());
                e.put("thread", er.getThreadName());
                if (er.getRequestUrl() != null) e.put("url", er.getRequestUrl());
                if (er.getRequestHeaders() != null) e.put("headers", er.getRequestHeaders());
                if (er.getResponseBody() != null) e.put("body", er.getResponseBody());
                errorRecords.add(e);
            }
            chartData.put("errorRecords", errorRecords);
        }

        // Hidden samplers (auto-hide utility + 0-request)
        if (reportData.getHiddenSamplers() != null && !reportData.getHiddenSamplers().isEmpty()) {
            chartData.put("hiddenSamplers", reportData.getHiddenSamplers());
        }

        // Transaction hierarchy
        if (reportData.getTransactionHierarchy() != null && !reportData.getTransactionHierarchy().isEmpty()) {
            chartData.put("transactionHierarchy", reportData.getTransactionHierarchy());
        }

        // Ramp-up end timestamp
        if (reportData.getMetadata() != null && reportData.getMetadata().getRampUpDurationMillis() > 0
                && reportData.getMetadata().getStartTime() != null) {
            long rampUpEnd = reportData.getMetadata().getStartTime().toEpochMilli()
                    + reportData.getMetadata().getRampUpDurationMillis();
            chartData.put("rampUpEnd", rampUpEnd);
        }

        return chartData;
    }

    /**
     * Serialize a CI/CD-friendly JSON summary for pipeline integration.
     * Delegates to {@link CiCdJsonSerializer}.
     */
    public String serializeCiCdJson(ReportData reportData,
                                      Map<String, SlaEvaluator.SlaResult> slaResults,
                                      SlaStatus overallStatus) throws JsonProcessingException {
        return new CiCdJsonSerializer(objectMapper).serialize(reportData, slaResults, overallStatus);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
