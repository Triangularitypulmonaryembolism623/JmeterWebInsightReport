package com.jmeterwebinsightreport.core.stats;

import com.jmeterwebinsightreport.core.model.*;
import com.jmeterwebinsightreport.core.util.FormatUtil;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Aggregation engine for listener mode — receives individual sample data and maintains
 * running statistics per sampler, time-series buckets, and error records. Thread-safe.
 */
public class LiveStatisticsEngine {

    private static final int MAX_ERROR_RECORDS = 500;
    private static final int DEFAULT_MAX_BODY_LENGTH = 16384; // 16KB
    private static final int MAX_HEADERS_LENGTH = 2048;
    private int maxResponseBodyLength = DEFAULT_MAX_BODY_LENGTH;

    /** Patterns for utility samplers that should be auto-hidden. */
    private static final List<Pattern> UTILITY_SAMPLER_PATTERNS = Arrays.asList(
            Pattern.compile("^JSR223.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^Debug.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^BeanShell.*", Pattern.CASE_INSENSITIVE),
            Pattern.compile("^__.*")  // JMeter internal functions
    );

    private final Map<String, SamplerAggregator> aggregators = new ConcurrentHashMap<>();
    private final Map<Long, TimeBucketAggregator> timeBuckets = new ConcurrentHashMap<>();
    private final List<ErrorRecord> errorRecords = new ArrayList<>();

    private final AtomicLong totalSamples = new AtomicLong();
    private final AtomicLong totalErrors = new AtomicLong();
    private final AtomicInteger maxActiveThreads = new AtomicInteger();
    private final AtomicLong firstTimestamp = new AtomicLong(Long.MAX_VALUE);
    private final AtomicLong lastTimestamp = new AtomicLong(Long.MIN_VALUE);

    private long rampUpDurationMillis;
    private double apdexThreshold = 500;

    /**
     * Record a single sample result (backward-compatible overload).
     */
    public void recordSample(String samplerName, long responseTime, boolean isSuccess,
                             long bytesReceived, long bytesSent, long timestamp) {
        recordSample(samplerName, responseTime, isSuccess, bytesReceived, bytesSent,
                timestamp, -1, -1);
    }

    /**
     * Record a single sample result with connect time and latency.
     */
    public void recordSample(String samplerName, long responseTime, boolean isSuccess,
                             long bytesReceived, long bytesSent, long timestamp,
                             long connectTime, long latency) {
        // Per-sampler aggregation
        aggregators.computeIfAbsent(samplerName, k -> new SamplerAggregator(k))
                .addSample(responseTime, isSuccess, bytesReceived, bytesSent, timestamp,
                        connectTime, latency);

        // Time-series bucketing (1-second granularity)
        long bucketKey = timestamp / 1000 * 1000;
        timeBuckets.computeIfAbsent(bucketKey, k -> new TimeBucketAggregator(k))
                .addSample(responseTime, isSuccess, samplerName, bytesReceived, bytesSent,
                        connectTime, latency);

        // Global counters
        totalSamples.incrementAndGet();
        if (!isSuccess) {
            totalErrors.incrementAndGet();
        }
        updateMinLong(firstTimestamp, timestamp);
        updateMaxLong(lastTimestamp, timestamp);
    }

    /**
     * Record an error with details (backward-compatible overload).
     */
    public void recordError(String samplerName, String responseCode, String responseMessage,
                            long timestamp, String threadName) {
        recordError(samplerName, responseCode, responseMessage, timestamp, threadName,
                null, null, null);
    }

    /**
     * Record an error with full details including URL, headers, and response body.
     */
    public void recordError(String samplerName, String responseCode, String responseMessage,
                            long timestamp, String threadName,
                            String requestUrl, String requestHeaders, String responseBody) {
        synchronized (errorRecords) {
            if (errorRecords.size() >= MAX_ERROR_RECORDS) {
                return;
            }
            ErrorRecord record = new ErrorRecord();
            record.setSamplerName(samplerName);
            record.setResponseCode(responseCode);
            record.setResponseMessage(responseMessage);
            record.setTimestamp(timestamp);
            record.setThreadName(threadName);
            if (requestUrl != null) {
                record.setRequestUrl(requestUrl);
            }
            if (requestHeaders != null) {
                record.setRequestHeaders(requestHeaders.length() > MAX_HEADERS_LENGTH
                        ? requestHeaders.substring(0, MAX_HEADERS_LENGTH) + "..." : requestHeaders);
            }
            if (responseBody != null) {
                record.setResponseBody(responseBody.length() > maxResponseBodyLength
                        ? responseBody.substring(0, maxResponseBodyLength) + "..." : responseBody);
            }
            errorRecords.add(record);
        }
    }

    /**
     * Update the max active threads seen.
     */
    public void updateActiveThreads(int activeThreads, long timestamp) {
        maxActiveThreads.updateAndGet(current -> Math.max(current, activeThreads));
        long bucketKey = timestamp / 1000 * 1000;
        TimeBucketAggregator bucket = timeBuckets.get(bucketKey);
        if (bucket != null) {
            bucket.updateActiveThreads(activeThreads);
        }
    }

    public void setRampUpDurationMillis(long rampUpDurationMillis) {
        this.rampUpDurationMillis = rampUpDurationMillis;
    }

    public void setMaxResponseBodyLength(int maxResponseBodyLength) {
        this.maxResponseBodyLength = maxResponseBodyLength;
    }

    public void setApdexThreshold(double apdexThreshold) {
        this.apdexThreshold = apdexThreshold;
    }

    public double getApdexThreshold() {
        return apdexThreshold;
    }

    /**
     * Get current statistics snapshot for all samplers.
     */
    public List<SamplerStatistics> getStatisticsSnapshot() {
        final double threshold = this.apdexThreshold;
        return aggregators.values().stream()
                .map(agg -> agg.toStatistics(threshold))
                .sorted(Comparator.comparing(SamplerStatistics::getSamplerName))
                .collect(Collectors.toList());
    }

    /**
     * Get time-series data sorted by timestamp.
     */
    public List<TimeSeriesBucket> getTimeSeries() {
        return timeBuckets.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> e.getValue().toBucket())
                .collect(Collectors.toList());
    }

    /**
     * Get recorded errors.
     */
    public List<ErrorRecord> getErrorRecords() {
        synchronized (errorRecords) {
            return new ArrayList<>(errorRecords);
        }
    }

    /**
     * Build complete ReportData from the engine's current state.
     */
    public ReportData buildReportData(String testName) {
        ReportData data = new ReportData();

        // Metadata
        TestMetadata metadata = new TestMetadata();
        metadata.setTestName(testName);
        long first = firstTimestamp.get();
        long last = lastTimestamp.get();
        if (first != Long.MAX_VALUE) {
            metadata.setStartTime(Instant.ofEpochMilli(first));
        }
        if (last != Long.MIN_VALUE) {
            metadata.setEndTime(Instant.ofEpochMilli(last));
        }
        if (first != Long.MAX_VALUE && last != Long.MIN_VALUE) {
            metadata.setDurationMillis(last - first);
        }
        metadata.setTotalSamples((int) totalSamples.get());
        metadata.setTotalErrors((int) totalErrors.get());
        metadata.setTotalThreads(maxActiveThreads.get());
        metadata.setReportGeneratedAt(FormatUtil.formatTimestamp(Instant.now()));
        metadata.setRampUpDurationMillis(rampUpDurationMillis);
        data.setMetadata(metadata);

        // Sampler statistics
        data.setSamplerStatistics(getStatisticsSnapshot());

        // Time series
        data.setTimeSeries(getTimeSeries());

        // Error records
        data.setErrorRecords(getErrorRecords());

        // Error summaries — aggregate from error records
        data.setErrorSummaries(buildErrorSummaries());

        // Auto-hide utility samplers and 0-request samplers
        data.setHiddenSamplers(computeHiddenSamplers(data.getSamplerStatistics()));

        // Detect transaction controller hierarchy
        data.setTransactionHierarchy(detectTransactionHierarchy(data.getSamplerStatistics()));

        return data;
    }

    /**
     * Determine which samplers should be hidden by default.
     * Hides utility samplers (JSR223, Debug, BeanShell) and samplers with 0 requests.
     */
    private List<String> computeHiddenSamplers(List<SamplerStatistics> stats) {
        List<String> hidden = new ArrayList<>();
        for (SamplerStatistics stat : stats) {
            String name = stat.getSamplerName();
            // Hide 0-request samplers
            if (stat.getSampleCount() == 0) {
                hidden.add(name);
                continue;
            }
            // Hide utility samplers matching known patterns
            for (Pattern pattern : UTILITY_SAMPLER_PATTERNS) {
                if (pattern.matcher(name).matches()) {
                    hidden.add(name);
                    break;
                }
            }
        }
        return hidden;
    }

    /**
     * Detect transaction controllers by looking for samplers whose names are prefixes
     * of other sampler names (e.g., "Login Flow" containing "Login Flow-0", "Login Flow-1").
     * Also handles JMeter's default Transaction Controller naming where the parent has
     * the same name as a group prefix.
     */
    private Map<String, List<String>> detectTransactionHierarchy(List<SamplerStatistics> stats) {
        if (stats.isEmpty()) return new LinkedHashMap<>();

        // Build a map for O(1) lookup by name
        Map<String, SamplerStatistics> statsByName = new LinkedHashMap<>(stats.size());
        for (SamplerStatistics stat : stats) {
            statsByName.put(stat.getSamplerName(), stat);
        }

        // Sort names lexicographically — parents will appear before children
        List<String> sortedNames = new ArrayList<>(statsByName.keySet());
        Collections.sort(sortedNames);

        Map<String, List<String>> hierarchy = new LinkedHashMap<>();

        // For each name, check preceding names as potential parents (closest prefix match)
        for (int i = 0; i < sortedNames.size(); i++) {
            String name = sortedNames.get(i);
            // Scan backwards for the longest prefix match
            for (int j = i - 1; j >= 0; j--) {
                String candidate = sortedNames.get(j);
                if (name.startsWith(candidate)
                        && name.length() > candidate.length()
                        && isSeparatorChar(name.charAt(candidate.length()))) {
                    hierarchy.computeIfAbsent(candidate, k -> new ArrayList<>()).add(name);
                    // Mark parent/child via O(1) map lookup
                    SamplerStatistics parentStat = statsByName.get(candidate);
                    parentStat.setTransactionController(true);
                    SamplerStatistics childStat = statsByName.get(name);
                    childStat.setParentSamplerName(candidate);
                    break; // Only assign to the closest (longest) parent prefix
                }
                // Optimization: if candidate is not a prefix at all, and it's
                // lexicographically too different, we can stop searching
                if (!name.startsWith(candidate.substring(0, Math.min(candidate.length(), 1)))) {
                    break;
                }
            }
        }

        // Set child lists on parent statistics
        for (Map.Entry<String, List<String>> entry : hierarchy.entrySet()) {
            SamplerStatistics parentStat = statsByName.get(entry.getKey());
            parentStat.setChildSamplerNames(entry.getValue());
        }

        return hierarchy;
    }

    private static boolean isSeparatorChar(char c) {
        return c == '-' || c == ' ' || c == '/';
    }

    /**
     * Build error summaries by grouping error records by response code.
     */
    private List<ErrorSummary> buildErrorSummaries() {
        long total = totalSamples.get();
        long errors = totalErrors.get();
        if (errors == 0) {
            return Collections.emptyList();
        }

        // Group by sampler + response code
        List<ErrorRecord> snapshot;
        synchronized (errorRecords) {
            snapshot = new ArrayList<>(errorRecords);
        }
        Map<String, Long> counts = new LinkedHashMap<>();
        Map<String, ErrorRecord> firstRecords = new LinkedHashMap<>();
        for (ErrorRecord record : snapshot) {
            String key = record.getSamplerName() + "|" + record.getResponseCode();
            counts.merge(key, 1L, Long::sum);
            firstRecords.putIfAbsent(key, record);
        }

        List<ErrorSummary> result = new ArrayList<>(counts.size());
        for (Map.Entry<String, Long> entry : counts.entrySet()) {
            ErrorRecord rec = firstRecords.get(entry.getKey());
            ErrorSummary s = new ErrorSummary();
            s.setSamplerName(rec.getSamplerName());
            s.setResponseCode(rec.getResponseCode());
            s.setResponseMessage(rec.getResponseMessage());
            s.setErrorType(rec.getResponseCode());
            s.setOccurrenceCount(entry.getValue());
            s.setPercentageOfErrors(errors > 0 ? (entry.getValue() * 100.0) / errors : 0);
            s.setPercentageOfAllSamples(total > 0 ? (entry.getValue() * 100.0) / total : 0);
            result.add(s);
        }
        result.sort((a, b) -> Long.compare(b.getOccurrenceCount(), a.getOccurrenceCount()));
        return result;
    }

    /**
     * Reset all aggregators.
     */
    public void reset() {
        aggregators.clear();
        timeBuckets.clear();
        synchronized (errorRecords) {
            errorRecords.clear();
        }
        totalSamples.set(0);
        totalErrors.set(0);
        maxActiveThreads.set(0);
        firstTimestamp.set(Long.MAX_VALUE);
        lastTimestamp.set(Long.MIN_VALUE);
    }

    private static void updateMinLong(AtomicLong target, long value) {
        target.updateAndGet(current -> Math.min(current, value));
    }

    private static void updateMaxLong(AtomicLong target, long value) {
        target.updateAndGet(current -> Math.max(current, value));
    }
}
