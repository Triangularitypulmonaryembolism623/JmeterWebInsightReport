package com.jmeterwebinsightreport.report.template;

import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.model.SamplerStatistics;
import com.jmeterwebinsightreport.core.model.TestMetadata;
import com.jmeterwebinsightreport.core.sla.SlaEvaluator;
import com.jmeterwebinsightreport.core.sla.SlaStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class JunitXmlWriterTest {

    private JunitXmlWriter writer;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        writer = new JunitXmlWriter();
    }

    // --- Helper methods ---

    private static SamplerStatistics createSampler(String name, double mean, double p95,
                                                    double p99, double errorRate) {
        SamplerStatistics stat = new SamplerStatistics();
        stat.setSamplerName(name);
        stat.setMeanResponseTime(mean);
        stat.setPercentile95(p95);
        stat.setPercentile99(p99);
        stat.setErrorRate(errorRate);
        stat.setSampleCount(100);
        return stat;
    }

    private ReportData createReportData(SamplerStatistics... stats) {
        ReportData data = new ReportData();
        TestMetadata meta = new TestMetadata();
        meta.setTestName("Test Plan");
        meta.setDurationMillis(60000);
        data.setMetadata(meta);
        data.setSamplerStatistics(Arrays.asList(stats));
        return data;
    }

    private String writeAndRead(ReportData data, Map<String, SlaEvaluator.SlaResult> slaResults,
                                String testName) throws IOException {
        File outputFile = tempDir.resolve("junit-report.xml").toFile();
        writer.write(data, slaResults, testName, outputFile);
        return new String(Files.readAllBytes(outputFile.toPath()), StandardCharsets.UTF_8);
    }

    // --- Tests ---

    @Test
    void write_producesValidXml() throws IOException {
        ReportData data = createReportData(createSampler("GET /api/users", 150, 300, 500, 1.0));

        String xml = writeAndRead(data, null, "My Test");

        assertTrue(xml.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>"));
        assertTrue(xml.contains("<testsuites"));
        assertTrue(xml.contains("<testsuite"));
        assertTrue(xml.contains("</testsuite>"));
        assertTrue(xml.contains("</testsuites>"));
    }

    @Test
    void write_includesAllSamplers() throws IOException {
        ReportData data = createReportData(
                createSampler("GET /api/users", 100, 200, 300, 0),
                createSampler("POST /api/login", 200, 400, 600, 0),
                createSampler("DELETE /api/session", 50, 100, 150, 0));

        String xml = writeAndRead(data, null, "Multi Sampler Test");

        assertTrue(xml.contains("name=\"GET /api/users\""));
        assertTrue(xml.contains("name=\"POST /api/login\""));
        assertTrue(xml.contains("name=\"DELETE /api/session\""));
    }

    @Test
    void write_noSlaFailures_noFailureElements() throws IOException {
        ReportData data = createReportData(createSampler("GET /api/users", 100, 200, 300, 0));

        Map<String, SlaEvaluator.SlaResult> slaResults = new LinkedHashMap<>();
        Map<String, SlaStatus> metricStatuses = new LinkedHashMap<>();
        metricStatuses.put("p95", SlaStatus.PASS);
        slaResults.put("GET /api/users", new SlaEvaluator.SlaResult(SlaStatus.PASS, metricStatuses));

        String xml = writeAndRead(data, slaResults, "Test");

        assertFalse(xml.contains("<failure"));
    }

    @Test
    void write_slaFailures_includeFailureElement() throws IOException {
        SamplerStatistics stat = createSampler("GET /api/slow", 800, 2000, 3000, 5.0);
        ReportData data = createReportData(stat);

        Map<String, SlaEvaluator.SlaResult> slaResults = new LinkedHashMap<>();
        Map<String, SlaStatus> metricStatuses = new LinkedHashMap<>();
        metricStatuses.put("p95", SlaStatus.FAIL);
        slaResults.put("GET /api/slow", new SlaEvaluator.SlaResult(SlaStatus.FAIL, metricStatuses));

        String xml = writeAndRead(data, slaResults, "Test");

        assertTrue(xml.contains("<failure"));
        assertTrue(xml.contains("SLA FAIL"));
    }

    @Test
    void write_correctTestCountAndFailures() throws IOException {
        ReportData data = createReportData(
                createSampler("API-A", 100, 200, 300, 0),
                createSampler("API-B", 800, 2000, 3000, 5.0),
                createSampler("API-C", 50, 100, 150, 0));

        Map<String, SlaEvaluator.SlaResult> slaResults = new LinkedHashMap<>();
        Map<String, SlaStatus> passStatuses = new LinkedHashMap<>();
        passStatuses.put("p95", SlaStatus.PASS);
        slaResults.put("API-A", new SlaEvaluator.SlaResult(SlaStatus.PASS, passStatuses));

        Map<String, SlaStatus> failStatuses = new LinkedHashMap<>();
        failStatuses.put("p95", SlaStatus.FAIL);
        slaResults.put("API-B", new SlaEvaluator.SlaResult(SlaStatus.FAIL, failStatuses));

        slaResults.put("API-C", new SlaEvaluator.SlaResult(SlaStatus.PASS, passStatuses));

        String xml = writeAndRead(data, slaResults, "Test");

        // 3 samplers, 1 failure
        assertTrue(xml.contains("tests=\"3\""));
        assertTrue(xml.contains("failures=\"1\""));
    }

    @Test
    void write_escapesXmlSpecialChars() throws IOException {
        SamplerStatistics stat = createSampler("GET /api?foo=1&bar=2", 100, 200, 300, 0);
        ReportData data = createReportData(stat);

        String xml = writeAndRead(data, null, "Test <Special> & 'Chars'");

        // The & should be escaped to &amp;
        assertTrue(xml.contains("&amp;"));
        // The < > should be escaped
        assertTrue(xml.contains("&lt;Special&gt;"));
    }

    @Test
    void write_nullSlaResults_allPass() throws IOException {
        ReportData data = createReportData(
                createSampler("API-A", 100, 200, 300, 0),
                createSampler("API-B", 150, 250, 350, 0));

        String xml = writeAndRead(data, null, "Test");

        // No SLA results means no failures
        assertFalse(xml.contains("<failure"));
        assertTrue(xml.contains("failures=\"0\""));
    }

    @Test
    void write_excludesTransactionChildren() throws IOException {
        SamplerStatistics parent = createSampler("Login Flow", 500, 1000, 1500, 0);
        SamplerStatistics child1 = createSampler("Login Flow-0", 200, 400, 600, 0);
        child1.setParentSamplerName("Login Flow");
        SamplerStatistics child2 = createSampler("Login Flow-1", 300, 600, 900, 0);
        child2.setParentSamplerName("Login Flow");
        SamplerStatistics standalone = createSampler("GET /api/health", 50, 100, 150, 0);

        ReportData data = createReportData(parent, child1, child2, standalone);

        String xml = writeAndRead(data, null, "Test");

        // Parent and standalone should be included
        assertTrue(xml.contains("name=\"Login Flow\""));
        assertTrue(xml.contains("name=\"GET /api/health\""));

        // Children should be excluded (they have parentSamplerName set)
        assertFalse(xml.contains("name=\"Login Flow-0\""));
        assertFalse(xml.contains("name=\"Login Flow-1\""));

        // Should report 2 tests (parent + standalone), not 4
        assertTrue(xml.contains("tests=\"2\""));
    }
}
