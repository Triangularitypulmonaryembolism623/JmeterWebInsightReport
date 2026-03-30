package com.jmeterwebinsightreport.report.template;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.model.SamplerStatistics;
import com.jmeterwebinsightreport.core.model.TestMetadata;
import com.jmeterwebinsightreport.core.sla.SlaEvaluator;
import com.jmeterwebinsightreport.core.sla.SlaStatus;

import java.util.*;

/**
 * Serializes a CI/CD-friendly JSON summary for pipeline integration.
 * Produces machine-readable output with test status, sampler stats, and SLA violations.
 */
public class CiCdJsonSerializer {

    private final ObjectMapper objectMapper;

    public CiCdJsonSerializer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String serialize(ReportData reportData,
                            Map<String, SlaEvaluator.SlaResult> slaResults,
                            SlaStatus overallStatus) throws JsonProcessingException {
        Map<String, Object> cicd = new LinkedHashMap<>();
        TestMetadata meta = reportData.getMetadata();

        cicd.put("testName", meta != null ? meta.getTestName() : "Unknown");
        cicd.put("status", overallStatus != null ? overallStatus.name() : "UNKNOWN");
        cicd.put("duration", meta != null ? meta.getDurationMillis() : 0);
        cicd.put("totalSamples", meta != null ? meta.getTotalSamples() : 0);
        cicd.put("totalErrors", meta != null ? meta.getTotalErrors() : 0);
        cicd.put("errorRate", meta != null && meta.getTotalSamples() > 0
                ? round2(meta.getTotalErrors() * 100.0 / meta.getTotalSamples()) : 0);

        // Per-sampler summary
        List<Map<String, Object>> samplerSummaries = new ArrayList<>();
        if (reportData.getSamplerStatistics() != null) {
            for (SamplerStatistics stat : reportData.getSamplerStatistics()) {
                Map<String, Object> s = new LinkedHashMap<>();
                s.put("name", stat.getSamplerName());
                s.put("sampleCount", stat.getSampleCount());
                s.put("mean", round2(stat.getMeanResponseTime()));
                s.put("p95", round2(stat.getPercentile95()));
                s.put("p99", round2(stat.getPercentile99()));
                s.put("errorRate", round2(stat.getErrorRate()));
                s.put("throughput", round2(stat.getThroughput()));
                s.put("apdex", stat.getApdexScore());
                if (slaResults != null && slaResults.containsKey(stat.getSamplerName())) {
                    s.put("slaStatus", slaResults.get(stat.getSamplerName()).getOverallStatus().name());
                }
                samplerSummaries.add(s);
            }
        }
        cicd.put("samplers", samplerSummaries);

        // SLA violations
        if (slaResults != null) {
            List<Map<String, Object>> violations = new ArrayList<>();
            for (Map.Entry<String, SlaEvaluator.SlaResult> entry : slaResults.entrySet()) {
                if (entry.getValue().getOverallStatus() != SlaStatus.PASS) {
                    Map<String, Object> v = new LinkedHashMap<>();
                    v.put("sampler", entry.getKey());
                    v.put("status", entry.getValue().getOverallStatus().name());
                    violations.add(v);
                }
            }
            cicd.put("slaViolations", violations);
        }

        cicd.put("generatedAt", meta != null ? meta.getReportGeneratedAt() : null);

        return objectMapper.writerWithDefaultPrettyPrinter().writeValueAsString(cicd);
    }

    private static double round2(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
