package com.jmeterwebinsightreport.report.template;

import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.model.SamplerStatistics;
import com.jmeterwebinsightreport.core.sla.SlaEvaluator;
import com.jmeterwebinsightreport.core.sla.SlaStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Writes JUnit XML output from report data and SLA evaluation results.
 * Each top-level sampler becomes a test case; SLA violations become test failures.
 * If no SLA is configured, all test cases pass.
 */
public class JunitXmlWriter {

    private static final Logger log = LoggerFactory.getLogger(JunitXmlWriter.class);

    /**
     * Write JUnit XML report to the given output file.
     *
     * @param reportData  the aggregated report data
     * @param slaResults  SLA evaluation results per sampler (may be null or empty if no SLA configured)
     * @param testName    the test name for the top-level testsuites element
     * @param outputFile  the target XML file
     * @throws IOException if writing fails
     */
    public void write(ReportData reportData, Map<String, SlaEvaluator.SlaResult> slaResults,
                      String testName, File outputFile) throws IOException {

        // Filter to top-level samplers only (exclude transaction controller children)
        List<SamplerStatistics> topLevelSamplers = reportData.getSamplerStatistics().stream()
                .filter(s -> s.getParentSamplerName() == null || s.getParentSamplerName().isEmpty())
                .collect(Collectors.toList());

        int totalTests = topLevelSamplers.size();
        int totalFailures = 0;

        if (slaResults != null) {
            for (SamplerStatistics stat : topLevelSamplers) {
                SlaEvaluator.SlaResult result = slaResults.get(stat.getSamplerName());
                if (result != null && result.getOverallStatus() == SlaStatus.FAIL) {
                    totalFailures++;
                }
            }
        }

        double totalTimeSeconds = reportData.getMetadata().getDurationMillis() / 1000.0;
        String escapedTestName = escapeXml(testName);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(Locale.US,
                "<testsuites name=\"%s\" tests=\"%d\" failures=\"%d\" time=\"%.1f\">\n",
                escapedTestName, totalTests, totalFailures, totalTimeSeconds));
        sb.append(String.format(Locale.US,
                "  <testsuite name=\"%s\" tests=\"%d\" failures=\"%d\" time=\"%.1f\">\n",
                escapedTestName, totalTests, totalFailures, totalTimeSeconds));

        for (SamplerStatistics stat : topLevelSamplers) {
            double timeSeconds = stat.getMeanResponseTime() / 1000.0;
            String escapedName = escapeXml(stat.getSamplerName());

            SlaEvaluator.SlaResult result = slaResults != null ? slaResults.get(stat.getSamplerName()) : null;
            boolean hasFailed = result != null && result.getOverallStatus() == SlaStatus.FAIL;

            sb.append(String.format(Locale.US,
                    "    <testcase name=\"%s\" time=\"%.3f\" classname=\"performance\">\n",
                    escapedName, timeSeconds));

            if (hasFailed) {
                String failureMessage = buildFailureMessage(stat, result);
                String failureDetails = buildFailureDetails(stat, result);
                sb.append(String.format("      <failure message=\"%s\">\n",
                        escapeXml(failureMessage)));
                sb.append(escapeXml(failureDetails));
                sb.append("\n      </failure>\n");
            }

            sb.append("    </testcase>\n");
        }

        sb.append("  </testsuite>\n");
        sb.append("</testsuites>\n");

        Files.write(outputFile.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8));
        log.info("JUnit XML written to: {}", outputFile.getAbsolutePath());
    }

    /**
     * Build the failure message attribute (single line summary).
     */
    private String buildFailureMessage(SamplerStatistics stat, SlaEvaluator.SlaResult result) {
        StringBuilder msg = new StringBuilder("SLA FAIL:");
        Map<String, SlaStatus> metricStatuses = result.getMetricStatuses();

        if (metricStatuses.get("p95") == SlaStatus.FAIL) {
            msg.append(String.format(Locale.US, " P95 %.0fms exceeds threshold;", stat.getPercentile95()));
        }
        if (metricStatuses.get("p99") == SlaStatus.FAIL) {
            msg.append(String.format(Locale.US, " P99 %.0fms exceeds threshold;", stat.getPercentile99()));
        }
        if (metricStatuses.get("mean") == SlaStatus.FAIL) {
            msg.append(String.format(Locale.US, " Mean %.0fms exceeds threshold;", stat.getMeanResponseTime()));
        }
        if (metricStatuses.get("errorRate") == SlaStatus.FAIL) {
            msg.append(String.format(Locale.US, " Error Rate %.1f%% exceeds threshold;", stat.getErrorRate()));
        }

        // Remove trailing semicolon
        String result_ = msg.toString();
        if (result_.endsWith(";")) {
            result_ = result_.substring(0, result_.length() - 1);
        }
        return result_;
    }

    /**
     * Build the failure details body (multi-line details with actual vs threshold info).
     */
    private String buildFailureDetails(SamplerStatistics stat, SlaEvaluator.SlaResult result) {
        StringBuilder details = new StringBuilder();
        Map<String, SlaStatus> metricStatuses = result.getMetricStatuses();

        if (metricStatuses.containsKey("p95")) {
            details.append(String.format(Locale.US, "P95: %.0fms (%s)\n",
                    stat.getPercentile95(), metricStatuses.get("p95")));
        }
        if (metricStatuses.containsKey("p99")) {
            details.append(String.format(Locale.US, "P99: %.0fms (%s)\n",
                    stat.getPercentile99(), metricStatuses.get("p99")));
        }
        if (metricStatuses.containsKey("mean")) {
            details.append(String.format(Locale.US, "Mean: %.0fms (%s)\n",
                    stat.getMeanResponseTime(), metricStatuses.get("mean")));
        }
        if (metricStatuses.containsKey("errorRate")) {
            details.append(String.format(Locale.US, "Error Rate: %.1f%% (%s)\n",
                    stat.getErrorRate(), metricStatuses.get("errorRate")));
        }

        return details.toString().trim();
    }

    /**
     * Escape XML special characters in text content and attribute values.
     */
    private String escapeXml(String text) {
        if (text == null) return "";
        return text.replace("&", "&amp;")
                   .replace("<", "&lt;")
                   .replace(">", "&gt;")
                   .replace("\"", "&quot;")
                   .replace("'", "&apos;");
    }
}
