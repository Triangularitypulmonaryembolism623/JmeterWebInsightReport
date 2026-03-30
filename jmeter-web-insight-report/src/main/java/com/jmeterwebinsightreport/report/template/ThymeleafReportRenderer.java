package com.jmeterwebinsightreport.report.template;

import com.jmeterwebinsightreport.core.comparison.ComparisonResult;
import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.model.SamplerStatistics;
import com.jmeterwebinsightreport.core.sla.SlaConfiguration;
import com.jmeterwebinsightreport.core.sla.SlaEvaluator;
import com.jmeterwebinsightreport.core.sla.SlaStatus;
import com.jmeterwebinsightreport.report.annotation.ReportAnnotations;
import com.jmeterwebinsightreport.core.util.ColorPalette;
import com.jmeterwebinsightreport.core.util.FormatUtil;

import java.util.Comparator;
import java.util.Map;
import java.util.List;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.ClassLoaderTemplateResolver;

/**
 * Renders the HTML report using Thymeleaf template engine.
 */
public class ThymeleafReportRenderer {

    private static final Logger log = LoggerFactory.getLogger(ThymeleafReportRenderer.class);

    private final TemplateEngine templateEngine;

    public ThymeleafReportRenderer() {
        ClassLoaderTemplateResolver resolver = new ClassLoaderTemplateResolver();
        resolver.setPrefix("templates/");
        resolver.setSuffix(".html");
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCharacterEncoding("UTF-8");

        this.templateEngine = new TemplateEngine();
        this.templateEngine.setTemplateResolver(resolver);
    }

    /**
     * Render the report template with the given data and inline resources.
     */
    public String render(ReportData reportData, String chartDataJson,
                         String cssContent, String jsContent) {
        return render(reportData, chartDataJson, cssContent, jsContent, "", null, null, false);
    }

    /**
     * Backward-compatible render without inline resources.
     */
    public String render(ReportData reportData, String chartDataJson) {
        return render(reportData, chartDataJson, "", "");
    }

    /**
     * Render with annotations only.
     */
    public String render(ReportData reportData, String chartDataJson,
                         String cssContent, String jsContent,
                         ReportAnnotations annotations) {
        return render(reportData, chartDataJson, cssContent, jsContent, "", annotations, null, false);
    }

    /**
     * Render with annotations and comparison (legacy overload without echartsJs).
     */
    public String render(ReportData reportData, String chartDataJson,
                         String cssContent, String jsContent,
                         ReportAnnotations annotations, ComparisonResult comparison) {
        return render(reportData, chartDataJson, cssContent, jsContent, "", annotations, comparison, false);
    }

    /**
     * Full render with all options including inlined ECharts and external data mode.
     */
    public String render(ReportData reportData, String chartDataJson,
                         String cssContent, String jsContent,
                         String echartsJs,
                         ReportAnnotations annotations, ComparisonResult comparison,
                         boolean externalDataMode) {
        Context context = buildFullContext(reportData, chartDataJson, cssContent, jsContent,
                echartsJs, annotations, externalDataMode);

        if (comparison != null) {
            context.setVariable("comparison", comparison);
            context.setVariable("hasComparison", true);
            context.setVariable("comparisonDiffs", comparison.getSamplerDiffs());
            context.setVariable("hasRegressions", comparison.hasRegressions());
        }

        log.debug("Rendering report template");
        return templateEngine.process("report", context);
    }

    private Context buildFullContext(ReportData reportData, String chartDataJson,
                                     String cssContent, String jsContent,
                                     String echartsJs,
                                     ReportAnnotations annotations,
                                     boolean externalDataMode) {
        Context context = new Context();
        context.setVariable("report", reportData);
        context.setVariable("metadata", reportData.getMetadata());
        context.setVariable("samplerStats", reportData.getSamplerStatistics());
        context.setVariable("timeSeries", reportData.getTimeSeries());
        context.setVariable("errorSummaries", reportData.getErrorSummaries());
        context.setVariable("errorRecords", reportData.getErrorRecords());
        context.setVariable("inlineCss", cssContent);
        context.setVariable("inlineJs", jsContent);
        context.setVariable("inlineEchartsJs", echartsJs != null ? echartsJs : "");
        context.setVariable("externalDataMode", externalDataMode);
        context.setVariable("fmt", new FormatUtilHelper());
        context.setVariable("colors", new ColorPaletteHelper());

        // Chart data: inline or external
        if (externalDataMode) {
            context.setVariable("chartData", "{}");
        } else {
            context.setVariable("chartData", chartDataJson);
        }

        // Annotations
        if (annotations != null) {
            context.setVariable("testNotes", annotations.getTestNotes());
            context.setVariable("verdict", annotations.getVerdict());
            context.setVariable("timelineMarkers", annotations.getTimelineMarkers());
            context.setVariable("samplerNotes", annotations.getSamplerNotes());

            if (annotations.getSlaThresholds() != null && !annotations.getSlaThresholds().isEmpty()) {
                Map<String, SlaEvaluator.SlaThresholdValues> thresholds =
                        SlaEvaluator.convertThresholds(annotations.getSlaThresholds());
                SlaEvaluator evaluator = new SlaEvaluator(new SlaConfiguration());
                Map<String, SlaEvaluator.SlaResult> slaResults = evaluator.evaluateAll(
                        reportData.getSamplerStatistics(), thresholds);
                context.setVariable("slaResults", slaResults);
                context.setVariable("overallSlaStatus", evaluator.getOverallStatus(slaResults));
                context.setVariable("slaThresholds", annotations.getSlaThresholds());
            }
        }

        // Computed summary values (exclude transaction children to avoid double-counting)
        List<SamplerStatistics> stats = reportData.getSamplerStatistics();
        if (stats != null && !stats.isEmpty()) {
            List<SamplerStatistics> topLevel = stats.stream()
                    .filter(s -> s.getParentSamplerName() == null)
                    .collect(Collectors.toList());
            long totalSamples = topLevel.stream().mapToLong(SamplerStatistics::getSampleCount).sum();
            double avgP95 = totalSamples > 0
                    ? topLevel.stream().mapToDouble(s -> s.getPercentile95() * s.getSampleCount()).sum() / totalSamples
                    : 0;
            double avgP99 = totalSamples > 0
                    ? topLevel.stream().mapToDouble(s -> s.getPercentile99() * s.getSampleCount()).sum() / totalSamples
                    : 0;
            double totalThroughput = topLevel.stream().mapToDouble(SamplerStatistics::getThroughput).sum();
            context.setVariable("avgP95", avgP95);
            context.setVariable("avgP99", avgP99);
            context.setVariable("totalThroughput", totalThroughput);
            List<SamplerStatistics> top5Slowest = stats.stream()
                    .filter(s -> s.getParentSamplerName() == null) // exclude transaction children
                    .sorted(Comparator.comparingDouble(SamplerStatistics::getPercentile95).reversed())
                    .limit(5)
                    .collect(Collectors.toList());
            context.setVariable("top5Slowest", top5Slowest);

            // Overall Apdex (weighted average by sample count across top-level samplers)
            if (totalSamples > 0) {
                double weightedApdex = topLevel.stream()
                        .mapToDouble(s -> s.getApdexScore() * s.getSampleCount())
                        .sum() / totalSamples;
                context.setVariable("overallApdex", Math.round(weightedApdex * 100.0) / 100.0);
            }
        }

        return context;
    }

    /**
     * Helper class to expose FormatUtil methods to Thymeleaf templates.
     */
    public static class FormatUtilHelper {
        public String duration(long millis) {
            return FormatUtil.formatDuration(millis);
        }

        public String number(double value) {
            return FormatUtil.formatNumber(value);
        }

        public String percentage(double value) {
            return FormatUtil.formatPercentage(value);
        }

        public String bytes(long value) {
            return FormatUtil.formatBytes(value);
        }
    }

    /**
     * Helper class to expose ColorPalette to Thymeleaf templates.
     */
    public static class ColorPaletteHelper {
        public String chartColor(int index) {
            return ColorPalette.getChartColor(index);
        }

        public String statusColor(String status) {
            return ColorPalette.getStatusColor(status);
        }

        public String success() { return ColorPalette.SUCCESS; }
        public String warning() { return ColorPalette.WARNING; }
        public String error() { return ColorPalette.ERROR; }
    }
}
