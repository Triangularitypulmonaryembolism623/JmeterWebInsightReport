package com.jmeterwebinsightreport.core.stats;

import com.jmeterwebinsightreport.core.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class LiveStatisticsEngineTest {

    private LiveStatisticsEngine engine;

    @BeforeEach
    void setUp() {
        engine = new LiveStatisticsEngine();
    }

    @Test
    void recordSample_incrementsSampleCount() {
        long ts = 1700000000000L;
        for (int i = 0; i < 10; i++) {
            engine.recordSample("Sampler1", 100 + i, true, 500, 100, ts + i * 100, 5, 10);
        }

        List<SamplerStatistics> stats = engine.getStatisticsSnapshot();
        assertEquals(1, stats.size());
        assertEquals(10, stats.get(0).getSampleCount());
    }

    @Test
    void recordSample_tracksMinMaxResponseTime() {
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 50, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler1", 200, true, 500, 100, ts + 100, 5, 10);
        engine.recordSample("Sampler1", 100, true, 500, 100, ts + 200, 5, 10);

        SamplerStatistics stat = engine.getStatisticsSnapshot().get(0);
        assertEquals(50.0, stat.getMinResponseTime(), 0.01);
        assertEquals(200.0, stat.getMaxResponseTime(), 0.01);
    }

    @Test
    void recordSample_calculatesMean() {
        long ts = 1700000000000L;
        // Response times: 100, 200, 300 → mean = 200
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler1", 200, true, 500, 100, ts + 100, 5, 10);
        engine.recordSample("Sampler1", 300, true, 500, 100, ts + 200, 5, 10);

        SamplerStatistics stat = engine.getStatisticsSnapshot().get(0);
        assertEquals(200.0, stat.getMeanResponseTime(), 0.01);
    }

    @Test
    void recordSample_tracksErrorCount() {
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler1", 100, true, 500, 100, ts + 100, 5, 10);
        engine.recordSample("Sampler1", 100, true, 500, 100, ts + 200, 5, 10);
        engine.recordSample("Sampler1", 100, false, 500, 100, ts + 300, 5, 10);
        engine.recordSample("Sampler1", 100, false, 500, 100, ts + 400, 5, 10);

        SamplerStatistics stat = engine.getStatisticsSnapshot().get(0);
        assertEquals(2, stat.getErrorCount());
        assertEquals(40.0, stat.getErrorRate(), 0.01); // 2/5 = 40%
    }

    @Test
    void recordSample_tracksBytesReceivedSent() {
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler1", 100, true, 700, 200, ts + 100, 5, 10);
        engine.recordSample("Sampler1", 100, true, 300, 50, ts + 200, 5, 10);

        SamplerStatistics stat = engine.getStatisticsSnapshot().get(0);
        assertEquals(1500.0, stat.getTotalReceivedBytes(), 0.01);
        assertEquals(350.0, stat.getTotalSentBytes(), 0.01);
    }

    @Test
    void recordSample_tracksConnectTimeAndLatency() {
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 10, 20);
        engine.recordSample("Sampler1", 100, true, 500, 100, ts + 100, 30, 40);

        SamplerStatistics stat = engine.getStatisticsSnapshot().get(0);
        assertEquals(20.0, stat.getMeanConnectTime(), 0.01); // (10+30)/2
        assertEquals(30.0, stat.getMeanLatency(), 0.01); // (20+40)/2
    }

    @Test
    void recordSample_createsTimeBuckets() {
        // Samples 2 seconds apart should land in different 1-second buckets
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler1", 100, true, 500, 100, ts + 2000, 5, 10);

        List<TimeSeriesBucket> buckets = engine.getTimeSeries();
        assertTrue(buckets.size() >= 2, "Expected at least 2 time buckets for samples 2 seconds apart");
    }

    @Test
    void recordSample_multipleSamplers() {
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler2", 200, true, 600, 200, ts + 100, 5, 10);

        List<SamplerStatistics> stats = engine.getStatisticsSnapshot();
        assertEquals(2, stats.size());
    }

    @Test
    void recordSample_detectsTransactionControllers() {
        long ts = 1700000000000L;
        engine.recordSample("TC-0", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("TC-1", 120, true, 600, 120, ts + 100, 5, 10);
        engine.recordSample("TC", 220, true, 1100, 220, ts + 200, 5, 10);

        ReportData data = engine.buildReportData("test");
        Map<String, List<String>> hierarchy = data.getTransactionHierarchy();
        assertTrue(hierarchy.containsKey("TC"));
        assertTrue(hierarchy.get("TC").contains("TC-0"));
        assertTrue(hierarchy.get("TC").contains("TC-1"));
    }

    @Test
    void recordSample_hidesUtilitySamplers() {
        long ts = 1700000000000L;
        engine.recordSample("JSR223 Sampler", 10, true, 100, 50, ts, 1, 2);
        engine.recordSample("Debug Sampler", 5, true, 50, 25, ts + 100, 1, 2);
        engine.recordSample("HTTP Request", 100, true, 500, 100, ts + 200, 5, 10);

        ReportData data = engine.buildReportData("test");
        List<String> hidden = data.getHiddenSamplers();
        assertTrue(hidden.contains("JSR223 Sampler"));
        assertTrue(hidden.contains("Debug Sampler"));
        assertFalse(hidden.contains("HTTP Request"));
    }

    @Test
    void recordSample_limitsErrorRecords() {
        long ts = 1700000000000L;
        for (int i = 0; i < 600; i++) {
            engine.recordError("Sampler1", "500", "Error", ts + i, "Thread-1",
                    null, null, null);
        }

        List<ErrorRecord> errors = engine.getErrorRecords();
        assertEquals(500, errors.size());
    }

    @Test
    void recordSample_truncatesErrorResponseBody() {
        long ts = 1700000000000L;
        // DEFAULT_MAX_BODY_LENGTH = 16384 (16KB)
        StringBuilder longBody = new StringBuilder();
        for (int i = 0; i < 20000; i++) {
            longBody.append('x');
        }

        engine.recordError("Sampler1", "500", "Error", ts, "Thread-1",
                null, null, longBody.toString());

        List<ErrorRecord> errors = engine.getErrorRecords();
        assertEquals(1, errors.size());
        assertEquals(16384 + 3, errors.get(0).getResponseBody().length());
        assertTrue(errors.get(0).getResponseBody().endsWith("..."));
    }

    @Test
    void recordError_capturesResponseCodeAndMessage() {
        long ts = 1700000000000L;
        engine.recordError("LoginAPI", "503", "Service Unavailable", ts, "Thread-1",
                "http://example.com/login", null, null);

        List<ErrorRecord> errors = engine.getErrorRecords();
        assertEquals(1, errors.size());
        ErrorRecord err = errors.get(0);
        assertEquals("LoginAPI", err.getSamplerName());
        assertEquals("503", err.getResponseCode());
        assertEquals("Service Unavailable", err.getResponseMessage());
        assertEquals("Thread-1", err.getThreadName());
        assertEquals("http://example.com/login", err.getRequestUrl());
    }

    @Test
    void buildReportData_returnsCompleteData() {
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler1", 200, false, 500, 100, ts + 1000, 5, 10);
        engine.recordError("Sampler1", "500", "Internal Error", ts + 1000, "Thread-1",
                null, null, null);

        ReportData data = engine.buildReportData("MyTest");

        assertNotNull(data.getMetadata());
        assertNotNull(data.getSamplerStatistics());
        assertNotNull(data.getTimeSeries());
        assertNotNull(data.getErrorRecords());
        assertNotNull(data.getErrorSummaries());
        assertNotNull(data.getHiddenSamplers());
        assertNotNull(data.getTransactionHierarchy());
        assertFalse(data.getSamplerStatistics().isEmpty());
        assertFalse(data.getTimeSeries().isEmpty());
        assertFalse(data.getErrorRecords().isEmpty());
    }

    @Test
    void buildReportData_calculatesTestMetadata() {
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler1", 200, false, 500, 100, ts + 5000, 5, 10);

        ReportData data = engine.buildReportData("MyTest");

        TestMetadata meta = data.getMetadata();
        assertEquals("MyTest", meta.getTestName());
        assertEquals(5000, meta.getDurationMillis());
        assertEquals(2, meta.getTotalSamples());
        assertEquals(1, meta.getTotalErrors());
        assertNotNull(meta.getStartTime());
        assertNotNull(meta.getEndTime());
    }

    @Test
    void buildReportData_calculatesThroughput() {
        long ts = 1700000000000L;
        // 10 samples over 5 seconds = 2.0 req/sec
        for (int i = 0; i < 10; i++) {
            engine.recordSample("Sampler1", 100, true, 500, 100, ts + i * 555, 5, 10);
        }

        ReportData data = engine.buildReportData("ThroughputTest");
        SamplerStatistics stat = data.getSamplerStatistics().get(0);
        assertTrue(stat.getThroughput() > 0, "Throughput should be positive");
    }

    @Test
    void threadSafety_concurrentRecording() throws InterruptedException {
        int threadCount = 10;
        int samplesPerThread = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        long ts = 1700000000000L;
        for (int t = 0; t < threadCount; t++) {
            final int threadNum = t;
            executor.submit(() -> {
                try {
                    for (int i = 0; i < samplesPerThread; i++) {
                        engine.recordSample("ConcurrentSampler", 100 + i, true, 500, 100,
                                ts + threadNum * 1000 + i, 5, 10);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        assertTrue(latch.await(10, TimeUnit.SECONDS), "Timed out waiting for threads");
        executor.shutdown();

        ReportData data = engine.buildReportData("ConcurrencyTest");
        assertEquals(threadCount * samplesPerThread,
                data.getMetadata().getTotalSamples());
    }

    @Test
    void reset_clearsAllData() {
        long ts = 1700000000000L;
        engine.recordSample("Sampler1", 100, true, 500, 100, ts, 5, 10);
        engine.recordSample("Sampler1", 200, false, 500, 100, ts + 1000, 5, 10);
        engine.recordError("Sampler1", "500", "Error", ts + 1000, "Thread-1",
                null, null, null);

        // Verify data exists
        assertFalse(engine.getStatisticsSnapshot().isEmpty());
        assertFalse(engine.getTimeSeries().isEmpty());
        assertFalse(engine.getErrorRecords().isEmpty());

        // Reset
        engine.reset();

        // Verify all cleared
        assertTrue(engine.getStatisticsSnapshot().isEmpty());
        assertTrue(engine.getTimeSeries().isEmpty());
        assertTrue(engine.getErrorRecords().isEmpty());

        ReportData data = engine.buildReportData("AfterReset");
        assertEquals(0, data.getMetadata().getTotalSamples());
        assertEquals(0, data.getMetadata().getTotalErrors());
    }

    @Test
    void apdexThreshold_defaultIs500() {
        assertEquals(500, engine.getApdexThreshold());
    }

    @Test
    void apdexThreshold_setAndGet() {
        engine.setApdexThreshold(1000);
        assertEquals(1000, engine.getApdexThreshold());
    }

    @Test
    void buildReportData_includesApdexScore() {
        long ts = 1700000000000L;
        // Record samples with response times well under the default 500ms threshold
        // so apdex should be high (close to 1.0)
        for (int i = 0; i < 50; i++) {
            engine.recordSample("FastAPI", 100 + i, true, 500, 100,
                    ts + i * 100, 5, 10);
        }

        ReportData data = engine.buildReportData("ApdexTest");
        SamplerStatistics stat = data.getSamplerStatistics().get(0);

        assertTrue(stat.getApdexScore() > 0,
                "Apdex score should be positive for samples well under threshold");
    }

    @Test
    void maxResponseBodyLength_setAndGet() {
        long ts = 1700000000000L;
        engine.setMaxResponseBodyLength(8192);

        // Create a 10000-character response body
        StringBuilder longBody = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            longBody.append('x');
        }

        engine.recordError("Sampler1", "500", "Error", ts, "Thread-1",
                null, null, longBody.toString());

        List<ErrorRecord> errors = engine.getErrorRecords();
        assertEquals(1, errors.size());
        // Body should be truncated to 8192 + "..." (3 chars)
        assertEquals(8192 + 3, errors.get(0).getResponseBody().length());
        assertTrue(errors.get(0).getResponseBody().endsWith("..."));
    }
}
