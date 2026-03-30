package com.jmeterwebinsightreport.report.generator;

import com.jmeterwebinsightreport.core.filter.SamplerFilterConfig;
import com.jmeterwebinsightreport.core.sla.SlaConfiguration;

import java.io.File;

/**
 * Configuration for the Web Insight Report — output directory, title, SLA settings, etc.
 * Populated from JMeter properties or GUI configuration panel.
 */
public class ReportConfiguration {

    private File outputDirectory;
    private String reportTitle = "JMeter Web Insight Report";
    private String reportFilename;
    private SamplerFilterConfig filterConfig = new SamplerFilterConfig();
    private SlaConfiguration slaConfiguration = new SlaConfiguration();
    private File annotationsFile;
    private File baselineJtlFile;
    private boolean embedResources = true;
    private String chartLibraryPath;
    private long rampUpDurationMillis;
    private boolean externalDataMode;
    private long externalDataThreshold = 10_000_000;
    private boolean generateJunitXml = false;

    public ReportConfiguration() {
    }

    // TODO: Load from JMeter properties (jmeter.properties / user.properties)
    //   e.g., webinsight.report.title, webinsight.report.output, etc.

    public File getOutputDirectory() {
        return outputDirectory;
    }

    public void setOutputDirectory(File outputDirectory) {
        this.outputDirectory = outputDirectory;
    }

    public String getReportTitle() {
        return reportTitle;
    }

    public void setReportTitle(String reportTitle) {
        this.reportTitle = reportTitle;
    }

    public String getReportFilename() {
        return reportFilename;
    }

    public void setReportFilename(String reportFilename) {
        this.reportFilename = reportFilename;
    }

    public SamplerFilterConfig getFilterConfig() {
        return filterConfig;
    }

    public void setFilterConfig(SamplerFilterConfig filterConfig) {
        this.filterConfig = filterConfig;
    }

    public SlaConfiguration getSlaConfiguration() {
        return slaConfiguration;
    }

    public void setSlaConfiguration(SlaConfiguration slaConfiguration) {
        this.slaConfiguration = slaConfiguration;
    }

    public File getAnnotationsFile() {
        return annotationsFile;
    }

    public void setAnnotationsFile(File annotationsFile) {
        this.annotationsFile = annotationsFile;
    }

    public File getBaselineJtlFile() {
        return baselineJtlFile;
    }

    public void setBaselineJtlFile(File baselineJtlFile) {
        this.baselineJtlFile = baselineJtlFile;
    }

    public boolean isEmbedResources() {
        return embedResources;
    }

    public void setEmbedResources(boolean embedResources) {
        this.embedResources = embedResources;
    }

    public String getChartLibraryPath() {
        return chartLibraryPath;
    }

    public void setChartLibraryPath(String chartLibraryPath) {
        this.chartLibraryPath = chartLibraryPath;
    }

    public long getRampUpDurationMillis() {
        return rampUpDurationMillis;
    }

    public void setRampUpDurationMillis(long rampUpDurationMillis) {
        this.rampUpDurationMillis = rampUpDurationMillis;
    }

    public boolean isExternalDataMode() {
        return externalDataMode;
    }

    public void setExternalDataMode(boolean externalDataMode) {
        this.externalDataMode = externalDataMode;
    }

    public long getExternalDataThreshold() {
        return externalDataThreshold;
    }

    public void setExternalDataThreshold(long externalDataThreshold) {
        this.externalDataThreshold = externalDataThreshold;
    }

    public boolean isGenerateJunitXml() {
        return generateJunitXml;
    }

    public void setGenerateJunitXml(boolean generateJunitXml) {
        this.generateJunitXml = generateJunitXml;
    }
}
