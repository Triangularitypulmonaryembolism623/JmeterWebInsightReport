package com.jmeterwebinsightreport.report.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmeterwebinsightreport.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ReportDataSerializerTest {

    private ReportDataSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        serializer = new ReportDataSerializer();
        objectMapper = new ObjectMapper();
    }

    // --- Helper methods ---

    private static ReportData createMinimalReportData() {
        ReportData data = new ReportData();
        TestMetadata meta = new TestMetadata();
        meta.setTestName("Test Plan");
        meta.setDurationMillis(60000);
        meta.setTotalSamples(100);
        meta.setTotalErrors(0);
        data.setMetadata(meta);
        return data;
    }

    private static SamplerStatistics createSamplerStats(String name, long count, double mean,
                                                         double p95, double errorRate) {
        SamplerStatistics stat = new SamplerStatistics();
        stat.setSamplerName(name);
        stat.setSampleCount(count);
        stat.setMeanResponseTime(mean);
        stat.setMedianResponseTime(mean * 0.9);
        stat.setPercentile90(p95 * 0.9);
        stat.setPercentile95(p95);
        stat.setPercentile99(p95 * 1.2);
        stat.setMinResponseTime(mean * 0.3);
        stat.setMaxResponseTime(p95 * 2.0);
        stat.setErrorRate(errorRate);
        stat.setErrorCount((long) (count * errorRate / 100.0));
        stat.setThroughput(count / 60.0);
        stat.setStandardDeviation(mean * 0.5);
        stat.setReceivedBytesPerSec(1024.0);
        stat.setSentBytesPerSec(512.0);
        stat.setMeanConnectTime(5.0);
        stat.setMeanLatency(mean * 0.8);
        return stat;
    }

    private static TimeSeriesBucket createBucket(Instant timestamp, String samplerName,
                                                  double meanRT, double throughput,
                                                  double errorRate, int threads) {
        TimeSeriesBucket bucket = new TimeSeriesBucket();
        bucket.setTimestamp(timestamp);
        bucket.setSampleCount(10);
        bucket.setMeanResponseTime(meanRT);
        bucket.setThroughput(throughput);
        bucket.setErrorRate(errorRate);
        bucket.setActiveThreads(threads);

        TimeSeriesBucket.SamplerBucketData sd = new TimeSeriesBucket.SamplerBucketData();
        sd.setMeanResponseTime(meanRT);
        sd.setSampleCount(10);
        sd.setThroughput(throughput);
        sd.setErrorRate(errorRate);

        Map<String, TimeSeriesBucket.SamplerBucketData> perSampler = new LinkedHashMap<>();
        perSampler.put(samplerName, sd);
        bucket.setPerSamplerData(perSampler);

        return bucket;
    }

    private static ReportData createFullReportData() {
        ReportData data = createMinimalReportData();

        // Sampler statistics
        List<SamplerStatistics> stats = new ArrayList<>();
        stats.add(createSamplerStats("GET /api/users", 500, 150.0, 300.0, 1.0));
        stats.add(createSamplerStats("POST /api/login", 300, 200.0, 400.0, 2.0));
        data.setSamplerStatistics(stats);

        // Time series
        Instant base = Instant.ofEpochMilli(1700000000000L);
        List<TimeSeriesBucket> timeSeries = new ArrayList<>();
        timeSeries.add(createBucket(base, "GET /api/users", 120.0, 10.0, 0.0, 5));
        timeSeries.add(createBucket(base.plusSeconds(1), "GET /api/users", 150.0, 12.0, 1.0, 5));
        timeSeries.add(createBucket(base.plusSeconds(2), "GET /api/users", 180.0, 8.0, 0.5, 5));
        data.setTimeSeries(timeSeries);

        // Error summaries
        List<ErrorSummary> errorSummaries = new ArrayList<>();
        ErrorSummary es = new ErrorSummary();
        es.setResponseCode("500");
        es.setResponseMessage("Internal Server Error");
        es.setOccurrenceCount(5);
        es.setPercentageOfErrors(100.0);
        errorSummaries.add(es);
        data.setErrorSummaries(errorSummaries);

        // Error records
        List<ErrorRecord> errorRecords = new ArrayList<>();
        ErrorRecord er = new ErrorRecord();
        er.setTimestamp(1700000001000L);
        er.setSamplerName("GET /api/users");
        er.setResponseCode("500");
        er.setResponseMessage("Internal Server Error");
        er.setThreadName("Thread-1");
        errorRecords.add(er);
        data.setErrorRecords(errorRecords);

        // Hidden samplers
        data.setHiddenSamplers(Arrays.asList("Debug Sampler", "BeanShell Sampler"));

        // Transaction hierarchy
        Map<String, List<String>> hierarchy = new LinkedHashMap<>();
        hierarchy.put("Login Flow", Arrays.asList("GET /api/login-page", "POST /api/login"));
        data.setTransactionHierarchy(hierarchy);

        return data;
    }

    // --- serializeToJson tests ---

    @Test
    void serialize_producesValidJson() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeToJson(data);

        assertNotNull(json);
        assertDoesNotThrow(() -> objectMapper.readTree(json));
    }

    // --- serializeChartData tests ---

    @Test
    void serialize_includesTimeSeriesArrays() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("timestamps"));
        assertTrue(root.has("meanResponseTimes"));
        assertTrue(root.has("throughputs"));
        assertTrue(root.has("errorRates"));
        assertTrue(root.has("activeThreads"));

        assertTrue(root.get("timestamps").isArray());
        assertEquals(3, root.get("timestamps").size());
        assertEquals(3, root.get("meanResponseTimes").size());
    }

    @Test
    void serialize_includesPerSamplerSeries() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("perSamplerSeries"));
        JsonNode perSampler = root.get("perSamplerSeries");
        assertTrue(perSampler.has("GET /api/users"));

        JsonNode samplerSeries = perSampler.get("GET /api/users");
        assertTrue(samplerSeries.has("responseTime"));
        assertTrue(samplerSeries.has("throughput"));
        assertTrue(samplerSeries.has("errorRate"));
    }

    @Test
    void serialize_includesSamplerSummary() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("samplers"));
        JsonNode samplers = root.get("samplers");
        assertTrue(samplers.isArray());
        assertEquals(2, samplers.size());

        JsonNode first = samplers.get(0);
        assertEquals("GET /api/users", first.get("name").asText());
        assertTrue(first.has("count"));
        assertTrue(first.has("mean"));
        assertTrue(first.has("p95"));
        assertTrue(first.has("p99"));
        assertTrue(first.has("errorRate"));
        assertTrue(first.has("throughput"));
    }

    @Test
    void serialize_includesErrorSummaries() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("errorsByType"));
        JsonNode errorsByType = root.get("errorsByType");
        assertTrue(errorsByType.isArray());
        assertEquals(1, errorsByType.size());
        assertEquals("500", errorsByType.get(0).get("code").asText());
        assertEquals("Internal Server Error", errorsByType.get(0).get("message").asText());
    }

    @Test
    void serialize_includesErrorRecords() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("errorRecords"));
        JsonNode errorRecords = root.get("errorRecords");
        assertTrue(errorRecords.isArray());
        assertEquals(1, errorRecords.size());

        JsonNode record = errorRecords.get(0);
        assertEquals("GET /api/users", record.get("sampler").asText());
        assertEquals("500", record.get("code").asText());
        assertEquals("Thread-1", record.get("thread").asText());
    }

    @Test
    void serialize_includesHiddenSamplers() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("hiddenSamplers"));
        JsonNode hidden = root.get("hiddenSamplers");
        assertTrue(hidden.isArray());
        assertEquals(2, hidden.size());
        assertEquals("Debug Sampler", hidden.get(0).asText());
    }

    @Test
    void serialize_includesTransactionHierarchy() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("transactionHierarchy"));
        JsonNode hierarchy = root.get("transactionHierarchy");
        assertTrue(hierarchy.has("Login Flow"));
        assertEquals(2, hierarchy.get("Login Flow").size());
    }

    @Test
    void serialize_handlesEmptyData() throws JsonProcessingException {
        ReportData data = new ReportData();
        data.setMetadata(new TestMetadata());

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertNotNull(root);
        // Should have timestamp/series arrays (empty)
        assertTrue(root.has("timestamps"));
        assertEquals(0, root.get("timestamps").size());
        assertTrue(root.has("samplers"));
        assertEquals(0, root.get("samplers").size());
    }

    @Test
    void serialize_includesResponseTimeDistribution() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("responseTimeDistribution"));
        JsonNode dist = root.get("responseTimeDistribution");
        assertTrue(dist.has("binLabels"));
        assertTrue(dist.has("perSampler"));
    }

    @Test
    void serialize_includesResponseCodesOverTime() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("responseCodesOverTime"));
    }

    @Test
    void serialize_includesErrorsBySampler() throws JsonProcessingException {
        ReportData data = createFullReportData();

        String json = serializer.serializeChartData(data);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("errorsBySampler"));
        JsonNode errorsBySampler = root.get("errorsBySampler");
        assertTrue(errorsBySampler.isArray());
        // At least one sampler has errors
        assertTrue(errorsBySampler.size() > 0);
    }
}
