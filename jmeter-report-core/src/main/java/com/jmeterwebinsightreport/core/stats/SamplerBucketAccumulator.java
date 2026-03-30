package com.jmeterwebinsightreport.core.stats;

import com.jmeterwebinsightreport.core.model.TimeSeriesBucket;

import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Accumulates per-sampler data within a single time bucket.
 * Uses reservoir sampling (max 128 values) to bound memory while providing
 * approximate percentiles for 1-second time windows.
 */
class SamplerBucketAccumulator {
    private static final int RESERVOIR_SIZE = 128;

    private long sampleCount;
    private long errorCount;
    private long sumResponseTime;
    private long sumConnectTime;
    private long sumLatency;
    private long sumReceivedBytes;
    private long sumSentBytes;
    private boolean hasConnectTime;
    private boolean hasLatency;
    private long maxResponseTime = Long.MIN_VALUE;
    private final long[] reservoir = new long[RESERVOIR_SIZE];
    private int reservoirCount;

    void add(long responseTime, boolean isSuccess, long receivedBytes, long sentBytes,
             long connectTime, long latency) {
        sampleCount++;
        if (!isSuccess) errorCount++;
        sumResponseTime += responseTime;
        sumReceivedBytes += receivedBytes;
        sumSentBytes += sentBytes;
        if (responseTime > maxResponseTime) {
            maxResponseTime = responseTime;
        }

        // Reservoir sampling: keep first RESERVOIR_SIZE values directly,
        // then replace with decreasing probability
        if (reservoirCount < RESERVOIR_SIZE) {
            reservoir[reservoirCount++] = responseTime;
        } else {
            long j = ThreadLocalRandom.current().nextLong(sampleCount);
            if (j < RESERVOIR_SIZE) {
                reservoir[(int) j] = responseTime;
            }
        }

        if (connectTime >= 0) {
            sumConnectTime += connectTime;
            hasConnectTime = true;
        }
        if (latency >= 0) {
            sumLatency += latency;
            hasLatency = true;
        }
    }

    TimeSeriesBucket.SamplerBucketData toBucketData() {
        TimeSeriesBucket.SamplerBucketData data = new TimeSeriesBucket.SamplerBucketData();
        data.setSampleCount(sampleCount);
        data.setMeanResponseTime(sampleCount > 0 ? (double) sumResponseTime / sampleCount : 0);
        data.setErrorRate(sampleCount > 0 ? (errorCount * 100.0) / sampleCount : 0);
        data.setThroughput(sampleCount);
        data.setReceivedBytes(sumReceivedBytes);
        data.setSentBytes(sumSentBytes);

        if (hasConnectTime && sampleCount > 0) {
            data.setConnectTime((double) sumConnectTime / sampleCount);
        }
        if (hasLatency && sampleCount > 0) {
            data.setLatency((double) sumLatency / sampleCount);
        }

        // Compute percentiles and histogram from reservoir sample
        if (reservoirCount > 0) {
            long[] sorted = Arrays.copyOf(reservoir, reservoirCount);
            Arrays.sort(sorted);
            data.setP50(percentile(sorted, 50));
            data.setP90(percentile(sorted, 90));
            data.setP95(percentile(sorted, 95));
            data.setP99(percentile(sorted, 99));
            data.setMax(maxResponseTime);

            // Histogram bins: [0-50, 50-100, 100-200, 200-500, 500-1000, 1000-2000, 2000-5000, 5000+]
            long[] boundaries = TimeSeriesBucket.SamplerBucketData.BIN_BOUNDARIES;
            int[] bins = new int[boundaries.length + 1];
            for (int i = 0; i < reservoirCount; i++) {
                long v = reservoir[i];
                int binIdx = boundaries.length; // default: last bin (5000+)
                for (int b = 0; b < boundaries.length; b++) {
                    if (v < boundaries[b]) { binIdx = b; break; }
                }
                bins[binIdx]++;
            }
            data.setHistogramBins(bins);
        }
        return data;
    }

    private static double percentile(long[] sorted, int p) {
        if (sorted.length == 0) return 0;
        double rank = (p / 100.0) * (sorted.length - 1);
        int lower = (int) Math.floor(rank);
        int upper = Math.min(lower + 1, sorted.length - 1);
        double frac = rank - lower;
        return sorted[lower] * (1 - frac) + sorted[upper] * frac;
    }
}
