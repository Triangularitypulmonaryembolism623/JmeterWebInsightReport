package com.jmeterwebinsightreport.core.model;

import java.time.Instant;

/**
 * Metadata about the test execution — name, start/end times, duration, thread counts, etc.
 */
public class TestMetadata {

    private String testName;
    private Instant startTime;
    private Instant endTime;
    private long durationMillis;
    private int totalThreads;
    private int totalSamples;
    private int totalErrors;
    private String jmeterVersion;
    private String reportGeneratedAt;
    private long rampUpDurationMillis;

    public TestMetadata() {
    }

    // TODO: Populate from JMeter's SampleContext / ReportGenerator data

    public String getTestName() {
        return testName;
    }

    public void setTestName(String testName) {
        this.testName = testName;
    }

    public Instant getStartTime() {
        return startTime;
    }

    public void setStartTime(Instant startTime) {
        this.startTime = startTime;
    }

    public Instant getEndTime() {
        return endTime;
    }

    public void setEndTime(Instant endTime) {
        this.endTime = endTime;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public void setDurationMillis(long durationMillis) {
        this.durationMillis = durationMillis;
    }

    public int getTotalThreads() {
        return totalThreads;
    }

    public void setTotalThreads(int totalThreads) {
        this.totalThreads = totalThreads;
    }

    public int getTotalSamples() {
        return totalSamples;
    }

    public void setTotalSamples(int totalSamples) {
        this.totalSamples = totalSamples;
    }

    public int getTotalErrors() {
        return totalErrors;
    }

    public void setTotalErrors(int totalErrors) {
        this.totalErrors = totalErrors;
    }

    public String getJmeterVersion() {
        return jmeterVersion;
    }

    public void setJmeterVersion(String jmeterVersion) {
        this.jmeterVersion = jmeterVersion;
    }

    public String getReportGeneratedAt() {
        return reportGeneratedAt;
    }

    public void setReportGeneratedAt(String reportGeneratedAt) {
        this.reportGeneratedAt = reportGeneratedAt;
    }

    public long getRampUpDurationMillis() {
        return rampUpDurationMillis;
    }

    public void setRampUpDurationMillis(long rampUpDurationMillis) {
        this.rampUpDurationMillis = rampUpDurationMillis;
    }
}
