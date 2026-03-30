package com.jmeterwebinsightreport.report.generator;

import com.jmeterwebinsightreport.core.comparison.ComparisonResult;
import com.jmeterwebinsightreport.core.comparison.ComparisonThresholds;
import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.parser.JtlParser;
import com.jmeterwebinsightreport.core.sla.SlaEvaluator;
import com.jmeterwebinsightreport.core.sla.SlaConfiguration;
import com.jmeterwebinsightreport.core.sla.SlaStatus;
import com.jmeterwebinsightreport.report.annotation.AnnotationLoader;
import com.jmeterwebinsightreport.report.annotation.ReportAnnotations;
import com.jmeterwebinsightreport.report.template.JunitXmlWriter;
import com.jmeterwebinsightreport.report.template.ReportDataSerializer;
import com.jmeterwebinsightreport.report.template.ThymeleafReportRenderer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/**
 * Orchestrates report generation — takes ReportData, serializes chart data,
 * renders via Thymeleaf with inlined CSS/JS, and writes a single self-contained HTML file.
 */
public class WebInsightReportGenerator {

    private static final Logger log = LoggerFactory.getLogger(WebInsightReportGenerator.class);
    private static final String REPORT_FILENAME = "web-insight-report.html";
    private static final String CICD_JSON_FILENAME = "web-insight-report.json";
    private static final String EXTERNAL_DATA_FILENAME = "web-insight-data.json";

    // Cached classpath resources (immutable after first load, safe for concurrent access)
    private static volatile String cachedCss;
    private static volatile String cachedJs;
    private static volatile String cachedEchartsJs;

    private final ReportConfiguration configuration;
    private final ThymeleafReportRenderer renderer;
    private final ReportDataSerializer serializer;
    private final AnnotationLoader annotationLoader;

    public WebInsightReportGenerator(ReportConfiguration configuration) {
        this.configuration = configuration;
        this.renderer = new ThymeleafReportRenderer();
        this.serializer = new ReportDataSerializer();
        this.annotationLoader = new AnnotationLoader();
    }

    /**
     * Generate the HTML report from the given data.
     *
     * @param reportData the aggregated report data
     * @return the output HTML file
     * @throws IOException if writing fails
     */
    public File generateReport(ReportData reportData) throws IOException {
        log.info("Generating Web Insight Report...");

        // 1. Load annotations if configured
        ReportAnnotations annotations = annotationLoader.load(configuration.getAnnotationsFile());

        // 2. Load baseline for comparison if configured
        ComparisonResult comparison = null;
        if (configuration.getBaselineJtlFile() != null && configuration.getBaselineJtlFile().exists()) {
            try {
                JtlParser parser = new JtlParser();
                ReportData baselineData = parser.parse(configuration.getBaselineJtlFile(), "Baseline");
                ComparisonThresholds thresholds = buildComparisonThresholds(annotations);
                comparison = new ComparisonResult(baselineData, reportData, thresholds);
                log.info("Baseline comparison loaded from: {}", configuration.getBaselineJtlFile());
            } catch (Exception e) {
                log.warn("Failed to load baseline JTL: {}", e.getMessage());
            }
        }

        // 3. Read CSS and JS from classpath for inlining (cached across invocations)
        String cssContent = getCachedCss();
        String jsContent = getCachedJs();
        String echartsJs = getCachedEchartsJs();

        // 4. Ensure output directory exists
        File outputDir = configuration.getOutputDirectory();
        if (outputDir != null && !outputDir.exists()) {
            Files.createDirectories(outputDir.toPath());
        }

        // 5. Determine external data mode and serialize chart data
        boolean useExternalData = configuration.isExternalDataMode();
        String chartDataJson;

        if (useExternalData) {
            // External data mode forced: write JSON directly to file (no intermediate String)
            File dataFile = new File(outputDir, EXTERNAL_DATA_FILENAME);
            serializer.writeChartDataToFile(reportData, annotations, comparison, dataFile);
            log.info("External data written to: {}", dataFile.getAbsolutePath());
            chartDataJson = "{}"; // Placeholder for template
        } else {
            // Serialize to string for inline embedding
            chartDataJson = serializer.serializeChartData(reportData, annotations, comparison);

            // Check if size exceeds threshold — switch to external data mode
            if (chartDataJson.length() > configuration.getExternalDataThreshold()) {
                useExternalData = true;
                File dataFile = new File(outputDir, EXTERNAL_DATA_FILENAME);
                Files.write(dataFile.toPath(), chartDataJson.getBytes(StandardCharsets.UTF_8));
                log.info("External data written to: {}", dataFile.getAbsolutePath());
            }
        }

        // 6. Render Thymeleaf template with inlined resources, annotations, and comparison
        String html = renderer.render(reportData, chartDataJson, cssContent, jsContent,
                echartsJs, annotations, comparison, useExternalData);

        // 7. Write output HTML file
        String htmlFilename = resolveFilename(configuration.getReportFilename(), REPORT_FILENAME);
        String jsonFilename = htmlFilename.replaceFirst("\\.html$", ".json");
        File outputFile = new File(outputDir, htmlFilename);
        Files.write(outputFile.toPath(), html.getBytes(StandardCharsets.UTF_8));
        log.info("Report written to: {}", outputFile.getAbsolutePath());

        // 9. Evaluate SLA (shared by CI/CD JSON and JUnit XML)
        Map<String, SlaEvaluator.SlaResult> slaResults = evaluateSla(reportData, annotations);
        SlaStatus overallStatus = null;
        if (slaResults != null && !slaResults.isEmpty()) {
            SlaEvaluator evaluator = new SlaEvaluator(new SlaConfiguration());
            overallStatus = evaluator.getOverallStatus(slaResults);
        }

        // 10. Write CI/CD JSON summary
        writeCiCdJson(reportData, slaResults, overallStatus, outputDir, jsonFilename);

        // 11. Write JUnit XML if enabled
        if (configuration.isGenerateJunitXml()) {
            writeJunitXml(reportData, slaResults, htmlFilename, outputDir);
        }

        return outputFile;
    }

    private String resolveFilename(String configured, String defaultName) {
        if (configured == null || configured.isEmpty()) {
            return defaultName;
        }
        String resolved = configured;
        if (resolved.contains("${timestamp}")) {
            String ts = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmm"));
            resolved = resolved.replace("${timestamp}", ts);
        }
        if (!resolved.endsWith(".html")) {
            resolved = resolved + ".html";
        }
        return resolved;
    }

    /**
     * Build ComparisonThresholds from annotations JSON and/or JMeter -J properties.
     * JMeter properties override annotation values.
     */
    private ComparisonThresholds buildComparisonThresholds(ReportAnnotations annotations) {
        ComparisonThresholds t = new ComparisonThresholds(); // defaults: p95>10%, errorRate>2%

        // Layer 1: from annotations JSON
        if (annotations != null && annotations.getComparisonThresholds() != null) {
            ReportAnnotations.ComparisonThresholdConfig c = annotations.getComparisonThresholds();
            if (c.getP95PctChange() != null) t.setP95PctChangeThreshold(c.getP95PctChange());
            if (c.getErrorRateChange() != null) t.setErrorRateChangeThreshold(c.getErrorRateChange());
            if (c.getMeanPctChange() != null) t.setMeanPctChangeThreshold(c.getMeanPctChange());
            if (c.getP99PctChange() != null) t.setP99PctChangeThreshold(c.getP99PctChange());
            if (c.getThroughputPctChange() != null) t.setThroughputPctChangeThreshold(c.getThroughputPctChange());
        }

        // Layer 2: JMeter -J properties override (only if set)
        try {
            String p95 = org.apache.jmeter.util.JMeterUtils.getProperty("webinsight.compare.p95.threshold");
            if (p95 != null) t.setP95PctChangeThreshold(Double.parseDouble(p95));
            String err = org.apache.jmeter.util.JMeterUtils.getProperty("webinsight.compare.errorrate.threshold");
            if (err != null) t.setErrorRateChangeThreshold(Double.parseDouble(err));
            String mean = org.apache.jmeter.util.JMeterUtils.getProperty("webinsight.compare.mean.threshold");
            if (mean != null) t.setMeanPctChangeThreshold(Double.parseDouble(mean));
            String p99 = org.apache.jmeter.util.JMeterUtils.getProperty("webinsight.compare.p99.threshold");
            if (p99 != null) t.setP99PctChangeThreshold(Double.parseDouble(p99));
            String tp = org.apache.jmeter.util.JMeterUtils.getProperty("webinsight.compare.throughput.threshold");
            if (tp != null) t.setThroughputPctChangeThreshold(Double.parseDouble(tp));
        } catch (Exception e) {
            // JMeterUtils may not be available (standalone mode)
        }

        return t;
    }

    /**
     * Evaluate SLA thresholds from annotations against sampler statistics.
     * Returns null if no SLA thresholds are configured.
     */
    private Map<String, SlaEvaluator.SlaResult> evaluateSla(ReportData reportData, ReportAnnotations annotations) {
        if (annotations == null || annotations.getSlaThresholds() == null
                || annotations.getSlaThresholds().isEmpty()) {
            return null;
        }

        Map<String, SlaEvaluator.SlaThresholdValues> thresholds =
                SlaEvaluator.convertThresholds(annotations.getSlaThresholds());
        SlaEvaluator evaluator = new SlaEvaluator(new SlaConfiguration());
        return evaluator.evaluateAll(reportData.getSamplerStatistics(), thresholds);
    }

    private void writeCiCdJson(ReportData reportData, Map<String, SlaEvaluator.SlaResult> slaResults,
                               SlaStatus overallStatus, File outputDir, String jsonFilename) {
        try {
            String cicdJson = serializer.serializeCiCdJson(reportData, slaResults, overallStatus);
            File cicdFile = new File(outputDir, jsonFilename);
            Files.write(cicdFile.toPath(), cicdJson.getBytes(StandardCharsets.UTF_8));
            log.info("CI/CD JSON written to: {}", cicdFile.getAbsolutePath());
        } catch (Exception e) {
            log.warn("Failed to write CI/CD JSON: {}", e.getMessage());
        }
    }

    private void writeJunitXml(ReportData reportData, Map<String, SlaEvaluator.SlaResult> slaResults,
                               String htmlFilename, File outputDir) {
        try {
            String xmlFilename = htmlFilename.replaceFirst("\\.html$", ".xml");
            File xmlFile = new File(outputDir, xmlFilename);
            String testName = configuration.getReportTitle();

            // If slaResults is null (no SLA configured), pass empty map so all tests pass
            Map<String, SlaEvaluator.SlaResult> results = slaResults != null
                    ? slaResults : java.util.Collections.emptyMap();

            JunitXmlWriter writer = new JunitXmlWriter();
            writer.write(reportData, results, testName, xmlFile);
        } catch (Exception e) {
            log.warn("Failed to write JUnit XML: {}", e.getMessage());
        }
    }

    private String getCachedCss() {
        String result = cachedCss;
        if (result == null) {
            result = readClasspathResource("static/css/report.css");
            cachedCss = result;
        }
        return result;
    }

    private String getCachedJs() {
        String result = cachedJs;
        if (result == null) {
            result = readClasspathResource("static/js/report.js");
            cachedJs = result;
        }
        return result;
    }

    private String getCachedEchartsJs() {
        String result = cachedEchartsJs;
        if (result == null) {
            result = readClasspathResource("static/js/echarts.min.js");
            cachedEchartsJs = result;
        }
        return result;
    }

    private String readClasspathResource(String path) {
        try (InputStream is = getClass().getClassLoader().getResourceAsStream(path)) {
            if (is == null) {
                log.warn("Classpath resource not found: {}", path);
                return "";
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.warn("Failed to read classpath resource: {}", path, e);
            return "";
        }
    }
}
