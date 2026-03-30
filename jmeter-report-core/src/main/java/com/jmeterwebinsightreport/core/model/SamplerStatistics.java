package com.jmeterwebinsightreport.core.model;

/**
 * Aggregated statistics for a single sampler (request type).
 */
public class SamplerStatistics {

    private String samplerName;
    private long sampleCount;
    private long errorCount;
    private double errorRate;
    private double meanResponseTime;
    private double medianResponseTime;
    private double percentile90;
    private double percentile95;
    private double percentile99;
    private double minResponseTime;
    private double maxResponseTime;
    private double standardDeviation;
    private double throughput;
    private double receivedBytesPerSec;
    private double sentBytesPerSec;
    private double meanConnectTime;
    private double meanLatency;
    private double totalReceivedBytes;
    private double totalSentBytes;
    private boolean transactionController;
    private String parentSamplerName;
    private java.util.List<String> childSamplerNames;
    private double apdexScore;

    public SamplerStatistics() {
    }

    // TODO: Build from JMeter's MapResultData or from live aggregation

    public String getSamplerName() {
        return samplerName;
    }

    public void setSamplerName(String samplerName) {
        this.samplerName = samplerName;
    }

    public long getSampleCount() {
        return sampleCount;
    }

    public void setSampleCount(long sampleCount) {
        this.sampleCount = sampleCount;
    }

    public long getErrorCount() {
        return errorCount;
    }

    public void setErrorCount(long errorCount) {
        this.errorCount = errorCount;
    }

    public double getErrorRate() {
        return errorRate;
    }

    public void setErrorRate(double errorRate) {
        this.errorRate = errorRate;
    }

    public double getMeanResponseTime() {
        return meanResponseTime;
    }

    public void setMeanResponseTime(double meanResponseTime) {
        this.meanResponseTime = meanResponseTime;
    }

    public double getMedianResponseTime() {
        return medianResponseTime;
    }

    public void setMedianResponseTime(double medianResponseTime) {
        this.medianResponseTime = medianResponseTime;
    }

    public double getPercentile90() {
        return percentile90;
    }

    public void setPercentile90(double percentile90) {
        this.percentile90 = percentile90;
    }

    public double getPercentile95() {
        return percentile95;
    }

    public void setPercentile95(double percentile95) {
        this.percentile95 = percentile95;
    }

    public double getPercentile99() {
        return percentile99;
    }

    public void setPercentile99(double percentile99) {
        this.percentile99 = percentile99;
    }

    public double getMinResponseTime() {
        return minResponseTime;
    }

    public void setMinResponseTime(double minResponseTime) {
        this.minResponseTime = minResponseTime;
    }

    public double getMaxResponseTime() {
        return maxResponseTime;
    }

    public void setMaxResponseTime(double maxResponseTime) {
        this.maxResponseTime = maxResponseTime;
    }

    public double getStandardDeviation() {
        return standardDeviation;
    }

    public void setStandardDeviation(double standardDeviation) {
        this.standardDeviation = standardDeviation;
    }

    public double getThroughput() {
        return throughput;
    }

    public void setThroughput(double throughput) {
        this.throughput = throughput;
    }

    public double getReceivedBytesPerSec() {
        return receivedBytesPerSec;
    }

    public void setReceivedBytesPerSec(double receivedBytesPerSec) {
        this.receivedBytesPerSec = receivedBytesPerSec;
    }

    public double getSentBytesPerSec() {
        return sentBytesPerSec;
    }

    public void setSentBytesPerSec(double sentBytesPerSec) {
        this.sentBytesPerSec = sentBytesPerSec;
    }

    public double getMeanConnectTime() {
        return meanConnectTime;
    }

    public void setMeanConnectTime(double meanConnectTime) {
        this.meanConnectTime = meanConnectTime;
    }

    public double getMeanLatency() {
        return meanLatency;
    }

    public void setMeanLatency(double meanLatency) {
        this.meanLatency = meanLatency;
    }

    public double getTotalReceivedBytes() {
        return totalReceivedBytes;
    }

    public void setTotalReceivedBytes(double totalReceivedBytes) {
        this.totalReceivedBytes = totalReceivedBytes;
    }

    public double getTotalSentBytes() {
        return totalSentBytes;
    }

    public void setTotalSentBytes(double totalSentBytes) {
        this.totalSentBytes = totalSentBytes;
    }

    public boolean isTransactionController() {
        return transactionController;
    }

    public void setTransactionController(boolean transactionController) {
        this.transactionController = transactionController;
    }

    public String getParentSamplerName() {
        return parentSamplerName;
    }

    public void setParentSamplerName(String parentSamplerName) {
        this.parentSamplerName = parentSamplerName;
    }

    public java.util.List<String> getChildSamplerNames() {
        return childSamplerNames;
    }

    public void setChildSamplerNames(java.util.List<String> childSamplerNames) {
        this.childSamplerNames = childSamplerNames;
    }

    public double getApdexScore() {
        return apdexScore;
    }

    public void setApdexScore(double apdexScore) {
        this.apdexScore = apdexScore;
    }
}
