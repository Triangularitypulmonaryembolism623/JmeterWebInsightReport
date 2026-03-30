package com.jmeterwebinsightreport.core.stats;

import com.jmeterwebinsightreport.core.model.SamplerStatistics;

/**
 * Per-sampler aggregator that maintains running statistics.
 * Thread-safe via synchronized methods.
 */
class SamplerAggregator {

    private final String samplerName;
    private final TDigestPercentileCalculator percentileCalculator = new TDigestPercentileCalculator();
    private long sampleCount;
    private long errorCount;
    private double sumResponseTime;
    private double sumResponseTimeSquared;
    private double minResponseTime = Double.MAX_VALUE;
    private double maxResponseTime = Double.MIN_VALUE;
    private long sumBytesReceived;
    private long sumBytesSent;
    private long sumConnectTime;
    private long sumLatency;
    private long connectTimeCount;
    private long latencyCount;
    private long firstTimestamp = Long.MAX_VALUE;
    private long lastTimestamp = Long.MIN_VALUE;

    SamplerAggregator(String samplerName) {
        this.samplerName = samplerName;
    }

    synchronized void addSample(long responseTime, boolean isSuccess,
                                 long bytesReceived, long bytesSent, long timestamp,
                                 long connectTime, long latency) {
        sampleCount++;
        if (!isSuccess) {
            errorCount++;
        }
        sumResponseTime += responseTime;
        sumResponseTimeSquared += (double) responseTime * responseTime;
        minResponseTime = Math.min(minResponseTime, responseTime);
        maxResponseTime = Math.max(maxResponseTime, responseTime);
        sumBytesReceived += bytesReceived;
        sumBytesSent += bytesSent;
        firstTimestamp = Math.min(firstTimestamp, timestamp);
        lastTimestamp = Math.max(lastTimestamp, timestamp);
        percentileCalculator.addValue(responseTime);

        if (connectTime >= 0) {
            sumConnectTime += connectTime;
            connectTimeCount++;
        }
        if (latency >= 0) {
            sumLatency += latency;
            latencyCount++;
        }
    }

    synchronized SamplerStatistics toStatistics() {
        return toStatistics(500);
    }

    synchronized SamplerStatistics toStatistics(double apdexThreshold) {
        SamplerStatistics stats = new SamplerStatistics();
        stats.setSamplerName(samplerName);
        stats.setSampleCount(sampleCount);
        stats.setErrorCount(errorCount);
        stats.setErrorRate(sampleCount > 0 ? (errorCount * 100.0) / sampleCount : 0);
        stats.setMeanResponseTime(sampleCount > 0 ? sumResponseTime / sampleCount : 0);
        stats.setMinResponseTime(sampleCount > 0 ? minResponseTime : 0);
        stats.setMaxResponseTime(sampleCount > 0 ? maxResponseTime : 0);

        // Standard deviation
        if (sampleCount > 1) {
            double mean = sumResponseTime / sampleCount;
            double variance = (sumResponseTimeSquared / sampleCount) - (mean * mean);
            stats.setStandardDeviation(Math.sqrt(Math.max(0, variance)));
        }

        // Percentiles from T-Digest
        if (sampleCount > 0) {
            stats.setMedianResponseTime(percentileCalculator.getPercentile(50));
            stats.setPercentile90(percentileCalculator.getPercentile(90));
            stats.setPercentile95(percentileCalculator.getPercentile(95));
            stats.setPercentile99(percentileCalculator.getPercentile(99));
        }

        // Throughput (requests per second)
        long durationMs = lastTimestamp - firstTimestamp;
        if (durationMs > 0) {
            double durationSec = durationMs / 1000.0;
            stats.setThroughput(sampleCount / durationSec);
            stats.setReceivedBytesPerSec(sumBytesReceived / durationSec);
            stats.setSentBytesPerSec(sumBytesSent / durationSec);
        } else if (sampleCount > 0) {
            stats.setThroughput(sampleCount);
            stats.setReceivedBytesPerSec(sumBytesReceived);
            stats.setSentBytesPerSec(sumBytesSent);
        }

        // Connect time and latency averages
        if (connectTimeCount > 0) {
            stats.setMeanConnectTime((double) sumConnectTime / connectTimeCount);
        }
        if (latencyCount > 0) {
            stats.setMeanLatency((double) sumLatency / latencyCount);
        }
        stats.setTotalReceivedBytes(sumBytesReceived);
        stats.setTotalSentBytes(sumBytesSent);

        // Apdex computation
        if (sampleCount > 0) {
            double apdexT = apdexThreshold > 0 ? apdexThreshold : 500;
            double satisfiedFraction = percentileCalculator.getCdf(apdexT);
            double toleratingFraction = percentileCalculator.getCdf(apdexT * 4) - satisfiedFraction;
            double apdex = satisfiedFraction + toleratingFraction / 2.0;
            stats.setApdexScore(Math.round(apdex * 100.0) / 100.0);
        }

        return stats;
    }
}
