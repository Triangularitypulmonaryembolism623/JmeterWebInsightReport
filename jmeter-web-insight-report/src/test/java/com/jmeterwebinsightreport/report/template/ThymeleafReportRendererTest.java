package com.jmeterwebinsightreport.report.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.jmeterwebinsightreport.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class ThymeleafReportRendererTest {

    private ThymeleafReportRenderer renderer;
    private ReportDataSerializer serializer;

    @BeforeEach
    void setUp() {
        renderer = new ThymeleafReportRenderer();
        serializer = new ReportDataSerializer();
    }

    // --- Helper methods ---

    private static ReportData createReportData(String testName) {
        ReportData data = new ReportData();
        TestMetadata meta = new TestMetadata();
        meta.setTestName(testName);
        meta.setDurationMillis(120000);
        meta.setTotalSamples(1000);
        meta.setTotalErrors(5);
        meta.setReportGeneratedAt("2024-01-15 10:30:00");
        meta.setJmeterVersion("5.6.3");
        data.setMetadata(meta);
        return data;
    }

    private static ReportData createReportDataWithSamplers() {
        ReportData data = createReportData("Performance Test");

        List<SamplerStatistics> stats = new ArrayList<>();
        SamplerStatistics s1 = new SamplerStatistics();
        s1.setSamplerName("GET /api/users");
        s1.setSampleCount(500);
        s1.setMeanResponseTime(150.0);
        s1.setMedianResponseTime(130.0);
        s1.setPercentile90(280.0);
        s1.setPercentile95(300.0);
        s1.setPercentile99(500.0);
        s1.setMinResponseTime(20.0);
        s1.setMaxResponseTime(800.0);
        s1.setErrorRate(0.5);
        s1.setErrorCount(3);
        s1.setThroughput(8.33);
        s1.setStandardDeviation(75.0);
        s1.setReceivedBytesPerSec(2048.0);
        s1.setSentBytesPerSec(512.0);
        s1.setMeanConnectTime(5.0);
        s1.setMeanLatency(120.0);
        stats.add(s1);

        SamplerStatistics s2 = new SamplerStatistics();
        s2.setSamplerName("POST /api/login");
        s2.setSampleCount(500);
        s2.setMeanResponseTime(200.0);
        s2.setMedianResponseTime(180.0);
        s2.setPercentile90(380.0);
        s2.setPercentile95(400.0);
        s2.setPercentile99(600.0);
        s2.setMinResponseTime(30.0);
        s2.setMaxResponseTime(1200.0);
        s2.setErrorRate(0.4);
        s2.setErrorCount(2);
        s2.setThroughput(8.33);
        s2.setStandardDeviation(90.0);
        s2.setReceivedBytesPerSec(4096.0);
        s2.setSentBytesPerSec(1024.0);
        s2.setMeanConnectTime(8.0);
        s2.setMeanLatency(160.0);
        stats.add(s2);

        data.setSamplerStatistics(stats);

        // Time series with per-sampler data
        Instant base = Instant.ofEpochMilli(1700000000000L);
        List<TimeSeriesBucket> timeSeries = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            TimeSeriesBucket bucket = new TimeSeriesBucket();
            bucket.setTimestamp(base.plusSeconds(i));
            bucket.setSampleCount(20);
            bucket.setMeanResponseTime(150.0 + i * 10);
            bucket.setThroughput(10.0);
            bucket.setErrorRate(0.5);
            bucket.setActiveThreads(5);

            Map<String, TimeSeriesBucket.SamplerBucketData> perSampler = new LinkedHashMap<>();
            TimeSeriesBucket.SamplerBucketData sd1 = new TimeSeriesBucket.SamplerBucketData();
            sd1.setSampleCount(10);
            sd1.setMeanResponseTime(120.0 + i * 10);
            sd1.setThroughput(5.0);
            sd1.setErrorRate(0.0);
            perSampler.put("GET /api/users", sd1);

            TimeSeriesBucket.SamplerBucketData sd2 = new TimeSeriesBucket.SamplerBucketData();
            sd2.setSampleCount(10);
            sd2.setMeanResponseTime(180.0 + i * 10);
            sd2.setThroughput(5.0);
            sd2.setErrorRate(1.0);
            perSampler.put("POST /api/login", sd2);

            bucket.setPerSamplerData(perSampler);
            timeSeries.add(bucket);
        }
        data.setTimeSeries(timeSeries);

        return data;
    }

    private String renderWithData(ReportData data) throws JsonProcessingException {
        String chartDataJson = serializer.serializeChartData(data);
        return renderer.render(data, chartDataJson);
    }

    // --- Tests ---

    @Test
    void render_producesHtmlOutput() throws JsonProcessingException {
        ReportData data = createReportDataWithSamplers();

        String html = renderWithData(data);

        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>") || html.contains("<html"),
                "Output should be HTML");
    }

    @Test
    void render_containsTestTitle() throws JsonProcessingException {
        ReportData data = createReportDataWithSamplers();

        String html = renderWithData(data);

        assertTrue(html.contains("Performance Test"),
                "HTML should contain the test name");
    }

    @Test
    void render_containsSamplerNames() throws JsonProcessingException {
        ReportData data = createReportDataWithSamplers();

        String html = renderWithData(data);

        assertTrue(html.contains("GET /api/users"),
                "HTML should contain sampler name 'GET /api/users'");
        assertTrue(html.contains("POST /api/login"),
                "HTML should contain sampler name 'POST /api/login'");
    }

    @Test
    void render_containsChartData() throws JsonProcessingException {
        ReportData data = createReportDataWithSamplers();
        String chartDataJson = serializer.serializeChartData(data);

        String html = renderer.render(data, chartDataJson);

        // The chart data JSON is embedded in the HTML for ECharts
        assertTrue(html.contains("timestamps") || html.contains("meanResponseTimes"),
                "HTML should contain embedded chart data");
    }

    @Test
    void render_handlesEmptyReportData() throws JsonProcessingException {
        ReportData data = new ReportData();
        TestMetadata meta = new TestMetadata();
        meta.setTestName("Empty Test");
        data.setMetadata(meta);
        data.setSamplerStatistics(new ArrayList<>());
        data.setTimeSeries(new ArrayList<>());

        String html = renderWithData(data);

        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>") || html.contains("<html"),
                "Empty data should still render valid HTML");
    }

    @Test
    void render_includesInlinedCss() throws JsonProcessingException {
        ReportData data = createReportDataWithSamplers();
        String chartDataJson = serializer.serializeChartData(data);
        String cssContent = "body { font-family: sans-serif; }";

        String html = renderer.render(data, chartDataJson, cssContent, "");

        assertTrue(html.contains("<style>") || html.contains("<style "),
                "HTML should contain a <style> tag");
    }

    @Test
    void render_includesInlinedJs() throws JsonProcessingException {
        ReportData data = createReportDataWithSamplers();
        String chartDataJson = serializer.serializeChartData(data);
        String jsContent = "function initCharts() { console.log('init'); }";

        String html = renderer.render(data, chartDataJson, "", jsContent);

        assertTrue(html.contains("<script>") || html.contains("<script "),
                "HTML should contain a <script> tag");
    }

    @Test
    void render_containsGeneratedTimestamp() throws JsonProcessingException {
        ReportData data = createReportDataWithSamplers();

        String html = renderWithData(data);

        assertTrue(html.contains("2024-01-15 10:30:00"),
                "HTML should contain the report generation timestamp");
    }

    @Test
    void render_twoArgOverloadProducesHtml() throws JsonProcessingException {
        ReportData data = createReportDataWithSamplers();
        String chartDataJson = serializer.serializeChartData(data);

        String html = renderer.render(data, chartDataJson);

        assertNotNull(html);
        assertTrue(html.contains("<!DOCTYPE html>"));
    }
}
