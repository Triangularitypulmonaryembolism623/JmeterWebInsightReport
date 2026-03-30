package com.jmeterwebinsightreport.core.stats;

import com.jmeterwebinsightreport.core.model.TimeSeriesBucket;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Time-bucket aggregator for 1-second granularity time-series.
 * Thread-safe via synchronized methods.
 */
class TimeBucketAggregator {

    private final long timestampMs;
    private long sampleCount;
    private long errorCount;
    private double sumResponseTime;
    private int maxActiveThreads;
    private final Map<String, SamplerBucketAccumulator> perSamplerAccum = new LinkedHashMap<>();

    TimeBucketAggregator(long timestampMs) {
        this.timestampMs = timestampMs;
    }

    synchronized void addSample(long responseTime, boolean isSuccess, String samplerName,
                                 long receivedBytes, long sentBytes,
                                 long connectTime, long latency) {
        sampleCount++;
        if (!isSuccess) {
            errorCount++;
        }
        sumResponseTime += responseTime;

        perSamplerAccum.computeIfAbsent(samplerName, k -> new SamplerBucketAccumulator())
                .add(responseTime, isSuccess, receivedBytes, sentBytes, connectTime, latency);
    }

    synchronized void updateActiveThreads(int activeThreads) {
        this.maxActiveThreads = Math.max(this.maxActiveThreads, activeThreads);
    }

    synchronized TimeSeriesBucket toBucket() {
        TimeSeriesBucket bucket = new TimeSeriesBucket();
        bucket.setTimestamp(Instant.ofEpochMilli(timestampMs));
        bucket.setSampleCount(sampleCount);
        bucket.setMeanResponseTime(sampleCount > 0 ? sumResponseTime / sampleCount : 0);
        bucket.setThroughput(sampleCount); // samples per second (1s bucket)
        bucket.setErrorRate(sampleCount > 0 ? (errorCount * 100.0) / sampleCount : 0);
        bucket.setActiveThreads(maxActiveThreads);

        Map<String, TimeSeriesBucket.SamplerBucketData> perSampler = new LinkedHashMap<>();
        for (Map.Entry<String, SamplerBucketAccumulator> entry : perSamplerAccum.entrySet()) {
            perSampler.put(entry.getKey(), entry.getValue().toBucketData());
        }
        bucket.setPerSamplerData(perSampler);

        return bucket;
    }
}
