package com.jmeterwebinsightreport.report.listener;

import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.stats.LiveStatisticsEngine;
import com.jmeterwebinsightreport.report.generator.ReportConfiguration;
import com.jmeterwebinsightreport.report.generator.WebInsightReportGenerator;
import org.apache.jmeter.reporters.ResultCollector;
import org.apache.jmeter.samplers.SampleEvent;
import org.apache.jmeter.samplers.SampleResult;
import org.apache.jmeter.util.JMeterUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * JMeter Listener that collects sample results during test execution and generates
 * the Web Insight Report at test end.
 */
public class WebInsightReportListener extends ResultCollector {

    private static final long serialVersionUID = 1L;
    private static final Logger log = LoggerFactory.getLogger(WebInsightReportListener.class);

    public static final String REPORT_TITLE = "WebInsightReport.reportTitle";
    public static final String REPORT_OUTPUT_DIR = "WebInsightReport.outputDirectory";
    public static final String REPORT_FILENAME = "WebInsightReport.reportFilename";
    public static final String GENERATE_JUNIT_XML = "WebInsightReport.generateJunitXml";

    private transient LiveStatisticsEngine statsEngine;
    private transient ReportConfiguration configuration;
    private transient long testStartTime;

    public WebInsightReportListener() {
        super();
    }

    @Override
    public void testStarted() {
        testStarted("");
    }

    @Override
    public void testStarted(String host) {
        super.testStarted(host);
        log.info("Web Insight Report Listener: test started");
        testStartTime = System.currentTimeMillis();
        statsEngine = new LiveStatisticsEngine();
        configuration = new ReportConfiguration();

        // Configurable response body truncation limit
        String maxBodyStr = getJMeterProperty("webinsight.error.body.maxsize");
        if (maxBodyStr != null && !maxBodyStr.isEmpty()) {
            try {
                statsEngine.setMaxResponseBodyLength(Integer.parseInt(maxBodyStr));
            } catch (NumberFormatException e) {
                log.warn("Invalid webinsight.error.body.maxsize: {}", maxBodyStr);
            }
        }

        // Apdex threshold (default 500ms)
        String apdexStr = getJMeterProperty("webinsight.apdex.threshold");
        if (apdexStr != null && !apdexStr.isEmpty()) {
            try {
                statsEngine.setApdexThreshold(Double.parseDouble(apdexStr));
            } catch (NumberFormatException e) {
                log.warn("Invalid webinsight.apdex.threshold: {}", apdexStr);
            }
        }

        // CLI properties (-J) take precedence over GUI fields
        String title = getJMeterProperty("webinsight.report.title");
        if (title == null || title.isEmpty()) {
            title = getPropertyAsString(REPORT_TITLE);
        }
        if (title == null || title.isEmpty()) {
            title = "JMeter Web Insight Report";
        }
        configuration.setReportTitle(title);

        String outputDir = getJMeterProperty("webinsight.report.output");
        if (outputDir == null || outputDir.isEmpty()) {
            outputDir = getPropertyAsString(REPORT_OUTPUT_DIR);
        }
        if (outputDir != null && !outputDir.isEmpty()) {
            configuration.setOutputDirectory(new File(outputDir));
        }
        // else: null means use working directory (resolved at generation time)

        String filename = getJMeterProperty("webinsight.report.filename");
        if (filename == null || filename.isEmpty()) {
            filename = getPropertyAsString(REPORT_FILENAME);
        }
        if (filename != null && !filename.isEmpty()) {
            configuration.setReportFilename(filename);
        }

        // JUnit XML generation — CLI property takes precedence over GUI checkbox
        String junitProp = getJMeterProperty("webinsight.report.junit");
        if ("true".equalsIgnoreCase(junitProp)) {
            configuration.setGenerateJunitXml(true);
        } else if (junitProp == null || junitProp.isEmpty()) {
            // Fall back to GUI checkbox value
            configuration.setGenerateJunitXml(
                    getPropertyAsBoolean(GENERATE_JUNIT_XML, false));
        }
    }

    private String getJMeterProperty(String name) {
        try {
            return JMeterUtils.getProperty(name);
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void sampleOccurred(SampleEvent event) {
        super.sampleOccurred(event);
        SampleResult result = event.getResult();

        if (statsEngine != null) {
            processSampleResult(result);
        }
    }

    /**
     * Process a single SampleResult and recursively process any sub-results.
     * Transaction Controllers with parent=true wrap child samples as sub-results.
     */
    private void processSampleResult(SampleResult result) {
        statsEngine.recordSample(
                result.getSampleLabel(),
                result.getTime(),
                result.isSuccessful(),
                result.getBytesAsLong(),
                result.getSentBytes(),
                result.getTimeStamp(),
                result.getConnectTime(),
                result.getLatency()
        );

        // Track active threads
        int activeThreads = result.getGroupThreads();
        statsEngine.updateActiveThreads(activeThreads, result.getTimeStamp());

        // Record error details with URL and response body
        if (!result.isSuccessful()) {
            String url = null;
            String responseBody = null;
            try {
                url = result.getUrlAsString();
            } catch (Exception e) { /* ignore */ }
            try {
                responseBody = result.getResponseDataAsString();
            } catch (Exception e) { /* ignore */ }
            statsEngine.recordError(
                    result.getSampleLabel(),
                    result.getResponseCode(),
                    result.getResponseMessage(),
                    result.getTimeStamp(),
                    result.getThreadName(),
                    url,
                    null,
                    responseBody
            );
        }

        // Process sub-results (Transaction Controller children)
        SampleResult[] subResults = result.getSubResults();
        if (subResults != null && subResults.length > 0) {
            for (SampleResult sub : subResults) {
                processSampleResult(sub);
            }
        }
    }

    @Override
    public void testEnded() {
        testEnded("");
    }

    @Override
    public void testEnded(String host) {
        super.testEnded(host);
        log.info("Web Insight Report Listener: test ended, generating report");

        if (statsEngine == null) {
            log.warn("Statistics engine is null, skipping report generation");
            return;
        }

        try {
            String title = configuration.getReportTitle();
            ReportData reportData = statsEngine.buildReportData(title);

            // Set JMeter version
            try {
                String version = JMeterUtils.getJMeterVersion();
                if (version != null) {
                    reportData.getMetadata().setJmeterVersion(version);
                }
            } catch (Exception e) {
                // JMeterUtils may not be available in all contexts
            }

            // Resolve output directory (default: working directory, consistent with JMeter's JTL/log behavior)
            File outputDir = configuration.getOutputDirectory();
            if (outputDir == null) {
                outputDir = new File(System.getProperty("user.dir", "."));
                configuration.setOutputDirectory(outputDir);
            }

            // Auto-detect annotations file next to output directory
            if (configuration.getAnnotationsFile() == null && outputDir != null) {
                File autoAnnotations = new File(outputDir, "report-annotations.json");
                if (autoAnnotations.exists()) {
                    configuration.setAnnotationsFile(autoAnnotations);
                    log.info("Auto-detected annotations file: {}", autoAnnotations.getAbsolutePath());
                }
            }

            // Auto-detect baseline JTL for comparison
            if (configuration.getBaselineJtlFile() == null && outputDir != null) {
                File autoBaseline = new File(outputDir, "baseline.jtl");
                if (autoBaseline.exists()) {
                    configuration.setBaselineJtlFile(autoBaseline);
                    log.info("Auto-detected baseline JTL: {}", autoBaseline.getAbsolutePath());
                }
            }

            WebInsightReportGenerator generator = new WebInsightReportGenerator(configuration);
            File reportFile = generator.generateReport(reportData);
            log.info("Web Insight Report generated: {}", reportFile.getAbsolutePath());
            System.out.println("Web Insight Report generated: " + reportFile.getAbsolutePath());

        } catch (Exception e) {
            log.error("Failed to generate Web Insight Report", e);
            System.err.println("Failed to generate Web Insight Report: " + e.getMessage());
        }
    }

}
