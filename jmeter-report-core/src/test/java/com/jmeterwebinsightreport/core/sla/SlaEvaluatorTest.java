package com.jmeterwebinsightreport.core.sla;

import com.jmeterwebinsightreport.core.model.SamplerStatistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class SlaEvaluatorTest {

    private SlaEvaluator evaluator;

    @BeforeEach
    void setUp() {
        SlaConfiguration config = new SlaConfiguration();
        config.setEnabled(true);
        evaluator = new SlaEvaluator(config);
    }

    private SamplerStatistics createStats(String name, double mean, double p95, double p99,
                                          double errorRate) {
        SamplerStatistics s = new SamplerStatistics();
        s.setSamplerName(name);
        s.setMeanResponseTime(mean);
        s.setPercentile95(p95);
        s.setPercentile99(p99);
        s.setErrorRate(errorRate);
        return s;
    }

    private SlaEvaluator.SlaThresholdValues createThreshold(Double p95, Double p99,
                                                             Double errorRate, Double mean) {
        SlaEvaluator.SlaThresholdValues tv = new SlaEvaluator.SlaThresholdValues();
        tv.setP95(p95);
        tv.setP99(p99);
        tv.setErrorRate(errorRate);
        tv.setMeanResponseTime(mean);
        return tv;
    }

    @Test
    void evaluate_passWhenBelowThreshold() {
        // P95=100, threshold=500 → 100 < 400 (80% of 500) → PASS
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 50, 100, 150, 0.5));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(500.0, null, null, null));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(1, results.size());
        assertEquals(SlaStatus.PASS, results.get("API").getOverallStatus());
    }

    @Test
    void evaluate_warnWhenNearThreshold() {
        // P95=420, threshold=500 → 420 > 400 (80%) but < 500 → WARN
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 200, 420, 450, 0.5));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(500.0, null, null, null));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(SlaStatus.WARN, results.get("API").getOverallStatus());
    }

    @Test
    void evaluate_failWhenAboveThreshold() {
        // P95=600, threshold=500 → 600 > 500 → FAIL
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 300, 600, 700, 1.0));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(500.0, null, null, null));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(SlaStatus.FAIL, results.get("API").getOverallStatus());
    }

    @Test
    void evaluate_perSamplerThresholds() {
        // Different thresholds per sampler
        List<SamplerStatistics> stats = Arrays.asList(
                createStats("Login", 100, 200, 300, 1.0),
                createStats("Search", 500, 800, 900, 2.0));

        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("Login", createThreshold(500.0, null, null, null));  // 200 < 400 → PASS
        thresholds.put("Search", createThreshold(500.0, null, null, null)); // 800 > 500 → FAIL

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(SlaStatus.PASS, results.get("Login").getOverallStatus());
        assertEquals(SlaStatus.FAIL, results.get("Search").getOverallStatus());
    }

    @Test
    void evaluate_defaultFallback() {
        // No sampler-specific threshold, uses "default"
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("UnknownAPI", 50, 100, 150, 0.5));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("default", createThreshold(500.0, null, null, null));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(1, results.size());
        assertEquals(SlaStatus.PASS, results.get("UnknownAPI").getOverallStatus());
    }

    @Test
    void evaluate_p95Threshold() {
        // Specific P95 check at boundary
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 100, 250, 300, 0.0));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(300.0, null, null, null));
        // 250 > 240 (80% of 300) but < 300 → WARN

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(SlaStatus.WARN, results.get("API").getOverallStatus());
        assertEquals(SlaStatus.WARN, results.get("API").getMetricStatuses().get("p95"));
    }

    @Test
    void evaluate_errorRateThreshold() {
        // Error rate above threshold → FAIL
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 100, 200, 300, 6.0));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(null, null, 5.0, null));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(SlaStatus.FAIL, results.get("API").getOverallStatus());
        assertEquals(SlaStatus.FAIL, results.get("API").getMetricStatuses().get("errorRate"));
    }

    @Test
    void evaluate_meanResponseTimeThreshold() {
        // Mean=600, threshold=500 → FAIL
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 600, 800, 1000, 0.0));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(null, null, null, 500.0));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(SlaStatus.FAIL, results.get("API").getOverallStatus());
        assertEquals(SlaStatus.FAIL, results.get("API").getMetricStatuses().get("mean"));
    }

    @Test
    void evaluate_mixedResults() {
        // P95 passes, error rate fails → overall FAIL
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 100, 200, 300, 10.0));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(500.0, null, 5.0, null));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(SlaStatus.FAIL, results.get("API").getOverallStatus());
        assertEquals(SlaStatus.PASS, results.get("API").getMetricStatuses().get("p95"));
        assertEquals(SlaStatus.FAIL, results.get("API").getMetricStatuses().get("errorRate"));
    }

    @Test
    void evaluate_emptyThresholds() {
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 100, 200, 300, 1.0));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, new LinkedHashMap<>());

        assertTrue(results.isEmpty());
    }

    @Test
    void getOverallStatus_worstAcrossSamplers() {
        // Build a results map with one PASS and one FAIL
        List<SamplerStatistics> stats = Arrays.asList(
                createStats("LoginAPI", 50, 100, 150, 0.5),
                createStats("SearchAPI", 500, 800, 1000, 10.0));

        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("LoginAPI", createThreshold(500.0, null, null, null));  // PASS
        thresholds.put("SearchAPI", createThreshold(500.0, null, 5.0, null)); // FAIL (both p95 and errorRate)

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);
        SlaStatus overall = evaluator.getOverallStatus(results);

        assertEquals(SlaStatus.FAIL, overall);
    }

    @Test
    void evaluate_allFourMetrics_perMetricStatuses() {
        // All 4 metrics configured, each at different status
        SamplerStatistics stats1 = createStats("API", 600, 200, 1500, 6.0);
        // p95=200, threshold=500 → PASS (200 < 400)
        // p99=1500, threshold=1000 → FAIL (1500 > 1000)
        // errorRate=6.0, threshold=5.0 → FAIL (6 > 5)
        // mean=600, threshold=500 → FAIL (600 > 500)
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(500.0, 1000.0, 5.0, 500.0));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(
                Collections.singletonList(stats1), thresholds);

        SlaEvaluator.SlaResult result = results.get("API");
        assertEquals(SlaStatus.PASS, result.getMetricStatuses().get("p95"));
        assertEquals(SlaStatus.FAIL, result.getMetricStatuses().get("p99"));
        assertEquals(SlaStatus.FAIL, result.getMetricStatuses().get("errorRate"));
        assertEquals(SlaStatus.FAIL, result.getMetricStatuses().get("mean"));
        assertEquals(SlaStatus.FAIL, result.getOverallStatus());
    }

    @Test
    void getOverallStatus_warnIsWorstWhenNoFail() {
        // One PASS, one WARN → overall WARN
        List<SamplerStatistics> stats = Arrays.asList(
                createStats("FastAPI", 50, 100, 150, 0.5),   // well below threshold → PASS
                createStats("SlowAPI", 200, 420, 500, 1.0)); // p95=420 in WARN zone (80-100% of 500)

        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("default", createThreshold(500.0, null, null, null));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);
        SlaStatus overall = evaluator.getOverallStatus(results);

        assertEquals(SlaStatus.PASS, results.get("FastAPI").getOverallStatus());
        assertEquals(SlaStatus.WARN, results.get("SlowAPI").getOverallStatus());
        assertEquals(SlaStatus.WARN, overall);
    }

    @Test
    void convertThresholds_convertsAllFields() {
        // Create a ThresholdConfig implementation
        SlaEvaluator.SlaThresholdValues source = new SlaEvaluator.SlaThresholdValues();
        source.setP95(500.0);
        source.setP99(1000.0);
        source.setErrorRate(5.0);
        source.setMeanResponseTime(300.0);

        Map<String, SlaEvaluator.SlaThresholdValues> input = new LinkedHashMap<>();
        input.put("default", source);
        input.put("LoginAPI", source);

        Map<String, SlaEvaluator.SlaThresholdValues> result = SlaEvaluator.convertThresholds(input);

        assertEquals(2, result.size());
        assertEquals(500.0, result.get("default").getP95());
        assertEquals(1000.0, result.get("default").getP99());
        assertEquals(5.0, result.get("default").getErrorRate());
        assertEquals(300.0, result.get("default").getMeanResponseTime());
    }

    @Test
    void convertThresholds_handlesNullFields() {
        SlaEvaluator.SlaThresholdValues source = new SlaEvaluator.SlaThresholdValues();
        source.setP95(500.0);
        // p99, errorRate, mean are null

        Map<String, SlaEvaluator.SlaThresholdValues> input = new LinkedHashMap<>();
        input.put("default", source);

        Map<String, SlaEvaluator.SlaThresholdValues> result = SlaEvaluator.convertThresholds(input);

        assertEquals(500.0, result.get("default").getP95());
        assertNull(result.get("default").getP99());
        assertNull(result.get("default").getErrorRate());
        assertNull(result.get("default").getMeanResponseTime());
    }

    @Test
    void evaluate_nullThresholdMetric_skipsCheck() {
        // Only p95 configured (others null) → only p95 checked
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("API", 900, 100, 5000, 50.0));
        // mean=900, p99=5000, errorRate=50 are all terrible, but not configured
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("API", createThreshold(500.0, null, null, null));

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        SlaEvaluator.SlaResult result = results.get("API");
        assertEquals(SlaStatus.PASS, result.getOverallStatus()); // only p95=100 checked, passes
        assertEquals(1, result.getMetricStatuses().size()); // only p95 evaluated
        assertTrue(result.getMetricStatuses().containsKey("p95"));
    }

    @Test
    void evaluate_samplerSpecificOverridesDefault() {
        // Default is strict (p95=100), but sampler-specific is lenient (p95=1000)
        List<SamplerStatistics> stats = Collections.singletonList(
                createStats("SlowAPI", 300, 500, 700, 2.0));
        Map<String, SlaEvaluator.SlaThresholdValues> thresholds = new LinkedHashMap<>();
        thresholds.put("default", createThreshold(100.0, null, null, null)); // would FAIL
        thresholds.put("SlowAPI", createThreshold(1000.0, null, null, null)); // PASS

        Map<String, SlaEvaluator.SlaResult> results = evaluator.evaluateAll(stats, thresholds);

        assertEquals(SlaStatus.PASS, results.get("SlowAPI").getOverallStatus());
    }
}
