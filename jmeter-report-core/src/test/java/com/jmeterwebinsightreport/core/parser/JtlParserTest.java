package com.jmeterwebinsightreport.core.parser;

import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.model.SamplerStatistics;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class JtlParserTest {

    private File getResourceFile(String name) {
        URL url = getClass().getClassLoader().getResource("jtl/" + name);
        assertNotNull(url, "Test fixture file not found: " + name);
        return new File(url.getFile());
    }

    private SamplerStatistics findSampler(ReportData data, String name) {
        return data.getSamplerStatistics().stream()
                .filter(s -> s.getSamplerName().equals(name))
                .findFirst()
                .orElse(null);
    }

    @Test
    void parseValidJtl_returnsCorrectSampleCount() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("valid-small.jtl"), "TestRun");

        int totalSamples = data.getSamplerStatistics().stream()
                .mapToInt(s -> (int) s.getSampleCount())
                .sum();
        assertEquals(50, totalSamples);
    }

    @Test
    void parseValidJtl_returnsCorrectSamplerNames() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("valid-small.jtl"), "TestRun");

        Set<String> names = data.getSamplerStatistics().stream()
                .map(SamplerStatistics::getSamplerName)
                .collect(Collectors.toSet());

        assertEquals(3, names.size());
        assertTrue(names.contains("GET /api/users"));
        assertTrue(names.contains("POST /api/orders"));
        assertTrue(names.contains("GET /api/products"));
    }

    @Test
    void parseValidJtl_calculatesCorrectErrorCount() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("valid-small.jtl"), "TestRun");

        long totalErrors = data.getSamplerStatistics().stream()
                .mapToLong(SamplerStatistics::getErrorCount)
                .sum();
        assertEquals(3, totalErrors);
    }

    @Test
    void parseValidJtl_calculatesCorrectErrorRate() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("valid-small.jtl"), "TestRun");

        // GET /api/users: 1 error out of 20 = 5%
        SamplerStatistics users = findSampler(data, "GET /api/users");
        assertNotNull(users);
        assertEquals(5.0, users.getErrorRate(), 0.01);

        // POST /api/orders: 2 errors out of 15 = 13.33%
        SamplerStatistics orders = findSampler(data, "POST /api/orders");
        assertNotNull(orders);
        assertEquals(13.33, orders.getErrorRate(), 0.01);

        // GET /api/products: 0 errors out of 15 = 0%
        SamplerStatistics products = findSampler(data, "GET /api/products");
        assertNotNull(products);
        assertEquals(0.0, products.getErrorRate(), 0.01);
    }

    @Test
    void parseValidJtl_capturesResponseTimes() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("valid-small.jtl"), "TestRun");

        // GET /api/products: values are 50,60,70,80,90,100,110,120,130,140,150,55,65,75,85
        // min=50, max=150, mean = sum/15 = 1380/15 = 92.0
        SamplerStatistics products = findSampler(data, "GET /api/products");
        assertNotNull(products);
        assertEquals(50.0, products.getMinResponseTime(), 0.01);
        assertEquals(150.0, products.getMaxResponseTime(), 0.01);
        assertEquals(92.0, products.getMeanResponseTime(), 0.01);
    }

    @Test
    void parseValidJtl_capturesBytesMetrics() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("valid-small.jtl"), "TestRun");

        // GET /api/users: 20 samples, each 500 bytes received, 100 bytes sent
        SamplerStatistics users = findSampler(data, "GET /api/users");
        assertNotNull(users);
        assertEquals(20 * 500.0, users.getTotalReceivedBytes(), 0.01);
        assertEquals(20 * 100.0, users.getTotalSentBytes(), 0.01);
    }

    @Test
    void parseValidJtl_capturesConnectAndLatency() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("valid-small.jtl"), "TestRun");

        // GET /api/users: all Connect=5, Latency=10
        SamplerStatistics users = findSampler(data, "GET /api/users");
        assertNotNull(users);
        assertEquals(5.0, users.getMeanConnectTime(), 0.01);
        assertEquals(10.0, users.getMeanLatency(), 0.01);

        // POST /api/orders: all Connect=10, Latency=20
        SamplerStatistics orders = findSampler(data, "POST /api/orders");
        assertNotNull(orders);
        assertEquals(10.0, orders.getMeanConnectTime(), 0.01);
        assertEquals(20.0, orders.getMeanLatency(), 0.01);
    }

    @Test
    void parseEmptyJtl_returnsEmptyReportData() throws IOException {
        JtlParser parser = new JtlParser();
        // header-only.jtl has only the header line and no data rows.
        // The parser creates a LiveStatisticsEngine and calls buildReportData even with 0 samples,
        // resulting in a ReportData with empty sampler statistics.
        ReportData data = parser.parse(getResourceFile("header-only.jtl"), "EmptyTest");
        assertNotNull(data);
        assertTrue(data.getSamplerStatistics().isEmpty());
        assertEquals(0, data.getMetadata().getTotalSamples());
    }

    @Test
    void parseMissingOptionalColumns_stillWorks() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("missing-optional-columns.jtl"), "MinimalTest");

        assertNotNull(data);
        int totalSamples = data.getSamplerStatistics().stream()
                .mapToInt(s -> (int) s.getSampleCount())
                .sum();
        assertEquals(10, totalSamples);
    }

    @Test
    void parseQuotedFields_handlesCorrectly() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("quoted-fields.jtl"), "QuotedTest");

        assertNotNull(data);
        Set<String> names = data.getSamplerStatistics().stream()
                .map(SamplerStatistics::getSamplerName)
                .collect(Collectors.toSet());

        // The file has 3 unique samplers with commas in the names:
        // "GET /api/search?q=hello,world", "POST /api/data,upload", "GET /api/report?fields=a,b,c"
        assertEquals(3, names.size());
        assertTrue(names.contains("GET /api/search?q=hello,world"));
        assertTrue(names.contains("POST /api/data,upload"));
        assertTrue(names.contains("GET /api/report?fields=a,b,c"));
    }

    @Test
    void parseMalformedRows_skipsAndContinues() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("malformed-rows.jtl"), "MalformedTest");

        assertNotNull(data);
        int totalSamples = data.getSamplerStatistics().stream()
                .mapToInt(s -> (int) s.getSampleCount())
                .sum();
        assertEquals(7, totalSamples);
    }

    @Test
    void parseSingleSampler_works() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("single-sampler.jtl"), "SingleTest");

        assertNotNull(data);
        assertEquals(1, data.getSamplerStatistics().size());

        SamplerStatistics stat = data.getSamplerStatistics().get(0);
        assertEquals("GET /api/health", stat.getSamplerName());
        assertEquals(100, stat.getSampleCount());
        assertEquals(0, stat.getErrorCount());
    }

    @Test
    void parseJtl_buildsTimeBuckets() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("valid-small.jtl"), "TestRun");

        assertNotNull(data.getTimeSeries());
        assertFalse(data.getTimeSeries().isEmpty());
        // The data spans from 1700000000000 to 1700000005000 (5 seconds),
        // so there should be multiple 1-second buckets
        assertTrue(data.getTimeSeries().size() >= 2);
    }

    @Test
    void parseTransactionControllers_detectsHierarchy() throws IOException {
        JtlParser parser = new JtlParser();
        ReportData data = parser.parse(getResourceFile("transaction-controllers.jtl"), "TCTest");

        assertNotNull(data.getTransactionHierarchy());
        assertFalse(data.getTransactionHierarchy().isEmpty());

        // "TC Browse" should be detected as parent of "TC Browse-0" and "TC Browse-1"
        Map<String, List<String>> hierarchy = data.getTransactionHierarchy();
        assertTrue(hierarchy.containsKey("TC Browse"));
        List<String> children = hierarchy.get("TC Browse");
        assertEquals(2, children.size());
        assertTrue(children.contains("TC Browse-0"));
        assertTrue(children.contains("TC Browse-1"));

        // Verify the parent is marked as transaction controller
        SamplerStatistics parent = findSampler(data, "TC Browse");
        assertNotNull(parent);
        assertTrue(parent.isTransactionController());

        // Verify children have parent set
        SamplerStatistics child0 = findSampler(data, "TC Browse-0");
        assertNotNull(child0);
        assertEquals("TC Browse", child0.getParentSamplerName());
    }
}
