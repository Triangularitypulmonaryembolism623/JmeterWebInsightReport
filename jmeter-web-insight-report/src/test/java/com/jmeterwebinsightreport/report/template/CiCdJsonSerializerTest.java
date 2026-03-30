package com.jmeterwebinsightreport.report.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.model.SamplerStatistics;
import com.jmeterwebinsightreport.core.model.TestMetadata;
import com.jmeterwebinsightreport.core.sla.SlaEvaluator;
import com.jmeterwebinsightreport.core.sla.SlaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class CiCdJsonSerializerTest {

    private CiCdJsonSerializer serializer;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        serializer = new CiCdJsonSerializer(objectMapper);
    }

    // --- Helper methods ---

    private static ReportData createReportData(String testName, long duration,
                                                int totalSamples, int totalErrors) {
        ReportData data = new ReportData();
        TestMetadata meta = new TestMetadata();
        meta.setTestName(testName);
        meta.setDurationMillis(duration);
        meta.setTotalSamples(totalSamples);
        meta.setTotalErrors(totalErrors);
        meta.setReportGeneratedAt("2024-01-15T10:30:00");
        data.setMetadata(meta);
        return data;
    }

    private static SamplerStatistics createSamplerStats(String name, long sampleCount,
                                                         double mean, double p95, double p99,
                                                         double errorRate, double throughput) {
        SamplerStatistics stat = new SamplerStatistics();
        stat.setSamplerName(name);
        stat.setSampleCount(sampleCount);
        stat.setMeanResponseTime(mean);
        stat.setPercentile95(p95);
        stat.setPercentile99(p99);
        stat.setErrorRate(errorRate);
        stat.setThroughput(throughput);
        return stat;
    }

    // --- Tests ---

    @Test
    void serialize_producesValidJson() throws JsonProcessingException {
        ReportData data = createReportData("Test Plan", 60000, 1000, 5);

        String json = serializer.serialize(data, null, null);

        assertNotNull(json);
        // Verify it's valid JSON by parsing it
        assertDoesNotThrow(() -> objectMapper.readTree(json));
    }

    @Test
    void serialize_includesTestName() throws JsonProcessingException {
        ReportData data = createReportData("My Performance Test", 30000, 500, 0);

        String json = serializer.serialize(data, null, null);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("My Performance Test", root.get("testName").asText());
    }

    @Test
    void serialize_includesOverallStatus() throws JsonProcessingException {
        ReportData data = createReportData("Test", 10000, 100, 0);

        String json = serializer.serialize(data, null, SlaStatus.PASS);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("PASS", root.get("status").asText());
    }

    @Test
    void serialize_includesDuration() throws JsonProcessingException {
        ReportData data = createReportData("Test", 120000, 500, 0);

        String json = serializer.serialize(data, null, null);
        JsonNode root = objectMapper.readTree(json);

        assertEquals(120000, root.get("duration").asLong());
    }

    @Test
    void serialize_includesPerSamplerSummary() throws JsonProcessingException {
        ReportData data = createReportData("Test", 60000, 1000, 10);
        List<SamplerStatistics> stats = new ArrayList<>();
        stats.add(createSamplerStats("GET /api/users", 500, 150.0, 300.0, 500.0, 1.0, 8.33));
        stats.add(createSamplerStats("POST /api/login", 500, 200.0, 400.0, 600.0, 1.0, 8.33));
        data.setSamplerStatistics(stats);

        String json = serializer.serialize(data, null, null);
        JsonNode root = objectMapper.readTree(json);

        JsonNode samplers = root.get("samplers");
        assertNotNull(samplers);
        assertTrue(samplers.isArray());
        assertEquals(2, samplers.size());

        JsonNode first = samplers.get(0);
        assertEquals("GET /api/users", first.get("name").asText());
        assertEquals(500, first.get("sampleCount").asLong());
        assertEquals(150.0, first.get("mean").asDouble(), 0.01);
        assertEquals(300.0, first.get("p95").asDouble(), 0.01);
        assertEquals(500.0, first.get("p99").asDouble(), 0.01);
    }

    @Test
    void serialize_includesSlaViolations() throws JsonProcessingException {
        ReportData data = createReportData("Test", 60000, 1000, 50);
        List<SamplerStatistics> stats = new ArrayList<>();
        stats.add(createSamplerStats("GET /api/slow", 500, 800.0, 2000.0, 3000.0, 5.0, 8.33));
        data.setSamplerStatistics(stats);

        // Create SLA results with a FAIL for the sampler
        Map<String, SlaEvaluator.SlaResult> slaResults = new LinkedHashMap<>();
        Map<String, SlaStatus> metricStatuses = new LinkedHashMap<>();
        metricStatuses.put("p95", SlaStatus.FAIL);
        slaResults.put("GET /api/slow", new SlaEvaluator.SlaResult(SlaStatus.FAIL, metricStatuses));

        String json = serializer.serialize(data, slaResults, SlaStatus.FAIL);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("FAIL", root.get("status").asText());

        JsonNode violations = root.get("slaViolations");
        assertNotNull(violations);
        assertTrue(violations.isArray());
        assertEquals(1, violations.size());
        assertEquals("GET /api/slow", violations.get(0).get("sampler").asText());
        assertEquals("FAIL", violations.get(0).get("status").asText());
    }

    @Test
    void serialize_statusUnknownWithoutSla() throws JsonProcessingException {
        ReportData data = createReportData("Test", 60000, 1000, 0);

        String json = serializer.serialize(data, null, null);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("UNKNOWN", root.get("status").asText());
    }

    @Test
    void serialize_includesTimestamp() throws JsonProcessingException {
        ReportData data = createReportData("Test", 60000, 100, 0);

        String json = serializer.serialize(data, null, null);
        JsonNode root = objectMapper.readTree(json);

        assertTrue(root.has("generatedAt"));
        assertEquals("2024-01-15T10:30:00", root.get("generatedAt").asText());
    }

    @Test
    void serialize_includesErrorRate() throws JsonProcessingException {
        ReportData data = createReportData("Test", 60000, 1000, 50);

        String json = serializer.serialize(data, null, null);
        JsonNode root = objectMapper.readTree(json);

        assertEquals(5.0, root.get("errorRate").asDouble(), 0.01);
        assertEquals(1000, root.get("totalSamples").asInt());
        assertEquals(50, root.get("totalErrors").asInt());
    }

    @Test
    void serialize_slaPassNotInViolations() throws JsonProcessingException {
        ReportData data = createReportData("Test", 60000, 1000, 0);
        List<SamplerStatistics> stats = new ArrayList<>();
        stats.add(createSamplerStats("GET /api/fast", 1000, 50.0, 100.0, 150.0, 0.0, 16.67));
        data.setSamplerStatistics(stats);

        Map<String, SlaEvaluator.SlaResult> slaResults = new LinkedHashMap<>();
        Map<String, SlaStatus> metricStatuses = new LinkedHashMap<>();
        metricStatuses.put("p95", SlaStatus.PASS);
        slaResults.put("GET /api/fast", new SlaEvaluator.SlaResult(SlaStatus.PASS, metricStatuses));

        String json = serializer.serialize(data, slaResults, SlaStatus.PASS);
        JsonNode root = objectMapper.readTree(json);

        JsonNode violations = root.get("slaViolations");
        assertNotNull(violations);
        assertEquals(0, violations.size(), "PASS samplers should not appear in violations");
    }

    @Test
    void serialize_handlesNullMetadata() throws JsonProcessingException {
        ReportData data = new ReportData();
        // metadata is null

        String json = serializer.serialize(data, null, null);
        JsonNode root = objectMapper.readTree(json);

        assertEquals("Unknown", root.get("testName").asText());
        assertEquals(0, root.get("duration").asLong());
    }

    @Test
    void serialize_includesApdexScore() throws JsonProcessingException {
        ReportData data = createReportData("Apdex Test", 60000, 1000, 0);
        SamplerStatistics stat = createSamplerStats("GET /api/users", 1000,
                100.0, 200.0, 300.0, 0.0, 16.67);
        stat.setApdexScore(0.85);
        List<SamplerStatistics> stats = new ArrayList<>();
        stats.add(stat);
        data.setSamplerStatistics(stats);

        String json = serializer.serialize(data, null, SlaStatus.PASS);
        JsonNode root = objectMapper.readTree(json);

        JsonNode samplers = root.get("samplers");
        assertNotNull(samplers);
        assertTrue(samplers.isArray());
        assertEquals(1, samplers.size());

        JsonNode sampler = samplers.get(0);
        assertTrue(sampler.has("apdex"), "Sampler JSON should contain 'apdex' field");
        assertEquals(0.85, sampler.get("apdex").asDouble(), 0.001);
    }
}
