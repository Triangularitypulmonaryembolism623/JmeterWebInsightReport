package com.jmeterwebinsightreport.report.template;

import com.jmeterwebinsightreport.core.model.ErrorRecord;
import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.model.TimeSeriesBucket;
import com.jmeterwebinsightreport.core.model.TimeSeriesBucket.SamplerBucketData;

import java.util.*;

/**
 * Builds specialized chart data structures from ReportData.
 * Extracted from ReportDataSerializer to keep each builder focused.
 */
class ChartDataBuilder {

    /**
     * Build response time distribution histogram data per sampler.
     * Creates bins: 0-50, 50-100, 100-200, 200-500, 500-1000, 1000-2000, 2000-5000, 5000+
     */
    static Map<String, Object> buildResponseTimeDistribution(List<TimeSeriesBucket> timeSeries,
                                                              Set<String> samplerNames) {
        int[] binEdges = {0, 50, 100, 200, 500, 1000, 2000, 5000, Integer.MAX_VALUE};
        String[] binLabels = {"0-50", "50-100", "100-200", "200-500", "500-1K", "1K-2K", "2K-5K", "5K+"};

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("binLabels", Arrays.asList(binLabels));

        // Per-sampler distribution based on mean response times per bucket
        Map<String, List<Integer>> perSampler = new LinkedHashMap<>(samplerNames.size());
        for (String name : samplerNames) {
            int[] bins = new int[binLabels.length];
            for (TimeSeriesBucket bucket : timeSeries) {
                SamplerBucketData sd = bucket.getPerSamplerData().get(name);
                if (sd != null && sd.getSampleCount() > 0) {
                    double rt = sd.getMeanResponseTime();
                    for (int i = 0; i < binEdges.length - 1; i++) {
                        if (rt >= binEdges[i] && rt < binEdges[i + 1]) {
                            bins[i] += (int) sd.getSampleCount();
                            break;
                        }
                    }
                }
            }
            List<Integer> binList = new ArrayList<>();
            for (int b : bins) binList.add(b);
            perSampler.put(name, binList);
        }
        result.put("perSampler", perSampler);
        return result;
    }

    /**
     * Build heatmap data: per-sampler histogram bins over time.
     * Output format: { bins: [...labels], timestamps: [...], perSampler: { name: [[bin0,bin1,...], ...] } }
     */
    static Map<String, Object> buildHeatmapData(List<TimeSeriesBucket> timeSeries, Set<String> samplerNames) {
        String[] binLabels = {"0-50", "50-100", "100-200", "200-500", "500-1K", "1K-2K", "2K-5K", "5K+"};
        int numBins = binLabels.length;

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bins", Arrays.asList(binLabels));

        List<Long> timestamps = new ArrayList<>(timeSeries.size());
        for (TimeSeriesBucket bucket : timeSeries) {
            timestamps.add(bucket.getTimestamp().toEpochMilli());
        }
        result.put("timestamps", timestamps);

        // Per-sampler: array of histogram arrays per time bucket
        Map<String, List<int[]>> perSampler = new LinkedHashMap<>();
        for (String name : samplerNames) {
            List<int[]> samplerBins = new ArrayList<>(timeSeries.size());
            for (TimeSeriesBucket bucket : timeSeries) {
                SamplerBucketData sd = bucket.getPerSamplerData().get(name);
                if (sd != null && sd.getHistogramBins() != null) {
                    samplerBins.add(sd.getHistogramBins());
                } else {
                    samplerBins.add(new int[numBins]);
                }
            }
            perSampler.put(name, samplerBins);
        }
        result.put("perSampler", perSampler);
        return result;
    }

    /**
     * Build response codes over time from error records.
     */
    static Map<String, Object> buildResponseCodesOverTime(ReportData reportData) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (reportData.getErrorRecords() == null || reportData.getErrorRecords().isEmpty()) {
            return result;
        }

        // Group error records by 1-second bucket and response code
        Map<Long, Map<String, Integer>> bucketCodes = new TreeMap<>();
        Set<String> allCodes = new LinkedHashSet<>();
        for (ErrorRecord er : reportData.getErrorRecords()) {
            long bucketKey = er.getTimestamp() / 1000 * 1000;
            bucketCodes.computeIfAbsent(bucketKey, k -> new LinkedHashMap<>())
                    .merge(er.getResponseCode(), 1, Integer::sum);
            allCodes.add(er.getResponseCode());
        }

        // Also add success codes from time-series (total - errors)
        allCodes.add("2xx");

        List<Long> ts = new ArrayList<>(bucketCodes.keySet());
        Map<String, List<Integer>> codeSeries = new LinkedHashMap<>();
        for (String code : allCodes) {
            if ("2xx".equals(code)) continue; // handle separately
            List<Integer> values = new ArrayList<>();
            for (Long t : ts) {
                Map<String, Integer> codes = bucketCodes.get(t);
                values.add(codes != null ? codes.getOrDefault(code, 0) : 0);
            }
            codeSeries.put(code, values);
        }

        result.put("timestamps", ts);
        result.put("codes", codeSeries);
        return result;
    }
}
