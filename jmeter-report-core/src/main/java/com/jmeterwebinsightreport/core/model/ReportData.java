package com.jmeterwebinsightreport.core.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Top-level data model that holds everything needed to render the Web Insight Report.
 */
public class ReportData {

    private TestMetadata metadata;
    private List<SamplerStatistics> samplerStatistics = new ArrayList<>();
    private List<TimeSeriesBucket> timeSeries = new ArrayList<>();
    private List<ErrorSummary> errorSummaries = new ArrayList<>();
    private List<ErrorRecord> errorRecords = new ArrayList<>();
    private List<String> hiddenSamplers = new ArrayList<>();
    private java.util.Map<String, List<String>> transactionHierarchy = new java.util.LinkedHashMap<>();

    public ReportData() {
    }

    // TODO: Builder methods to populate from JMeter's SampleContext data

    public TestMetadata getMetadata() {
        return metadata;
    }

    public void setMetadata(TestMetadata metadata) {
        this.metadata = metadata;
    }

    public List<SamplerStatistics> getSamplerStatistics() {
        return samplerStatistics;
    }

    public void setSamplerStatistics(List<SamplerStatistics> samplerStatistics) {
        this.samplerStatistics = samplerStatistics;
    }

    public List<TimeSeriesBucket> getTimeSeries() {
        return timeSeries;
    }

    public void setTimeSeries(List<TimeSeriesBucket> timeSeries) {
        this.timeSeries = timeSeries;
    }

    public List<ErrorSummary> getErrorSummaries() {
        return errorSummaries;
    }

    public void setErrorSummaries(List<ErrorSummary> errorSummaries) {
        this.errorSummaries = errorSummaries;
    }

    public List<ErrorRecord> getErrorRecords() {
        return errorRecords;
    }

    public void setErrorRecords(List<ErrorRecord> errorRecords) {
        this.errorRecords = errorRecords;
    }

    public List<String> getHiddenSamplers() {
        return hiddenSamplers;
    }

    public void setHiddenSamplers(List<String> hiddenSamplers) {
        this.hiddenSamplers = hiddenSamplers;
    }

    public java.util.Map<String, List<String>> getTransactionHierarchy() {
        return transactionHierarchy;
    }

    public void setTransactionHierarchy(java.util.Map<String, List<String>> transactionHierarchy) {
        this.transactionHierarchy = transactionHierarchy;
    }
}
