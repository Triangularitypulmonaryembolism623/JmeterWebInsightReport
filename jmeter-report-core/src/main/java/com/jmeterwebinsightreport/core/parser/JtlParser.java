package com.jmeterwebinsightreport.core.parser;

import com.jmeterwebinsightreport.core.model.ReportData;
import com.jmeterwebinsightreport.core.stats.LiveStatisticsEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parses JMeter JTL/CSV result files and produces ReportData via LiveStatisticsEngine.
 * Supports the standard JMeter CSV format with header row.
 */
public class JtlParser {

    private static final Logger log = LoggerFactory.getLogger(JtlParser.class);

    private long rampUpDurationMillis;
    private double apdexThreshold = 500;

    // Reusable parsing state to avoid per-line allocations
    private final StringBuilder parseSb = new StringBuilder(256);
    private int expectedColumnCount = -1;

    public void setRampUpDurationMillis(long rampUpDurationMillis) {
        this.rampUpDurationMillis = rampUpDurationMillis;
    }

    public void setApdexThreshold(double apdexThreshold) {
        this.apdexThreshold = apdexThreshold;
    }

    /**
     * Parse a JTL file and return ReportData.
     *
     * @param jtlFile the JTL/CSV result file
     * @param testName name for the report
     * @return parsed and aggregated report data
     */
    public ReportData parse(File jtlFile, String testName) throws IOException {
        LiveStatisticsEngine engine = new LiveStatisticsEngine();
        engine.setRampUpDurationMillis(rampUpDurationMillis);
        engine.setApdexThreshold(apdexThreshold);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new FileInputStream(jtlFile), StandardCharsets.UTF_8))) {

            String headerLine = reader.readLine();
            if (headerLine == null) {
                throw new IOException("Empty JTL file: " + jtlFile);
            }

            Map<String, Integer> headers = parseHeaders(headerLine);
            validateHeaders(headers);

            int colTimestamp = headers.get("timeStamp");
            int colElapsed = headers.get("elapsed");
            int colLabel = headers.get("label");
            int colCode = headers.get("responseCode");
            int colMessage = headers.getOrDefault("responseMessage", -1);
            int colThread = headers.getOrDefault("threadName", -1);
            int colSuccess = headers.get("success");
            int colBytes = headers.getOrDefault("bytes", -1);
            int colSentBytes = headers.getOrDefault("sentBytes", -1);
            int colGrpThreads = headers.getOrDefault("grpThreads", -1);
            int colConnect = headers.getOrDefault("Connect", -1);
            int colLatency = headers.getOrDefault("Latency", -1);
            int colUrl = headers.getOrDefault("URL", -1);

            String line;
            long lineNum = 1;
            while ((line = reader.readLine()) != null) {
                lineNum++;
                if (line.trim().isEmpty()) continue;

                try {
                    String[] fields = parseCsvLine(line);

                    long timestamp = Long.parseLong(fields[colTimestamp]);
                    long elapsed = Long.parseLong(fields[colElapsed]);
                    String label = fields[colLabel];
                    String responseCode = fields[colCode];
                    boolean success = Boolean.parseBoolean(fields[colSuccess]);
                    long bytes = colBytes >= 0 && colBytes < fields.length
                            ? parseLongSafe(fields[colBytes]) : 0;
                    long sentBytes = colSentBytes >= 0 && colSentBytes < fields.length
                            ? parseLongSafe(fields[colSentBytes]) : 0;
                    long connectTime = colConnect >= 0 && colConnect < fields.length
                            ? parseLongSafe(fields[colConnect]) : -1;
                    long latency = colLatency >= 0 && colLatency < fields.length
                            ? parseLongSafe(fields[colLatency]) : -1;

                    engine.recordSample(label, elapsed, success, bytes, sentBytes, timestamp,
                            connectTime, latency);

                    if (colGrpThreads >= 0 && colGrpThreads < fields.length) {
                        int threads = parseIntSafe(fields[colGrpThreads]);
                        if (threads > 0) {
                            engine.updateActiveThreads(threads, timestamp);
                        }
                    }

                    if (!success) {
                        String message = colMessage >= 0 && colMessage < fields.length
                                ? fields[colMessage] : "";
                        String thread = colThread >= 0 && colThread < fields.length
                                ? fields[colThread] : "";
                        String url = colUrl >= 0 && colUrl < fields.length
                                ? fields[colUrl] : null;
                        engine.recordError(label, responseCode, message, timestamp, thread,
                                url, null, null);
                    }
                } catch (Exception e) {
                    log.warn("Skipping malformed line {} in {}: {}", lineNum, jtlFile.getName(), e.getMessage());
                }
            }
        }

        log.info("Parsed {} from JTL file: {}", testName, jtlFile.getName());
        return engine.buildReportData(testName);
    }

    private Map<String, Integer> parseHeaders(String headerLine) {
        String[] headers = parseCsvLine(headerLine);
        Map<String, Integer> map = new LinkedHashMap<>();
        for (int i = 0; i < headers.length; i++) {
            map.put(headers[i].trim(), i);
        }
        return map;
    }

    private void validateHeaders(Map<String, Integer> headers) throws IOException {
        String[] required = {"timeStamp", "elapsed", "label", "responseCode", "success"};
        for (String h : required) {
            if (!headers.containsKey(h)) {
                throw new IOException("Missing required JTL column: " + h);
            }
        }
    }

    /**
     * Simple CSV line parser that handles quoted fields.
     * Reuses internal StringBuilder and pre-allocated array to minimize allocations.
     */
    private String[] parseCsvLine(String line) {
        // Fast path for non-quoted fields: use substring instead of StringBuilder
        if (line.indexOf('"') < 0) {
            return parseCsvLineUnquoted(line);
        }

        // Quoted path: use reusable StringBuilder
        parseSb.setLength(0);
        String[] result = expectedColumnCount > 0 ? new String[expectedColumnCount] : null;
        int fieldIdx = 0;
        boolean inQuotes = false;

        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '"') {
                inQuotes = !inQuotes;
            } else if (c == ',' && !inQuotes) {
                String field = parseSb.toString();
                if (result != null && fieldIdx < result.length) {
                    result[fieldIdx] = field;
                } else {
                    // Fallback: column count changed
                    result = growArray(result, fieldIdx, field);
                }
                fieldIdx++;
                parseSb.setLength(0);
            } else {
                parseSb.append(c);
            }
        }
        // Last field
        String field = parseSb.toString();
        if (result != null && fieldIdx < result.length) {
            result[fieldIdx] = field;
        } else {
            result = growArray(result, fieldIdx, field);
        }
        fieldIdx++;

        if (expectedColumnCount <= 0) {
            expectedColumnCount = fieldIdx;
        }
        // If array is larger than field count (shouldn't happen normally), trim
        if (result != null && fieldIdx < result.length) {
            return java.util.Arrays.copyOf(result, fieldIdx);
        }
        return result;
    }

    /**
     * Fast CSV parsing for lines with no quoted fields — uses substring to share backing array.
     */
    private String[] parseCsvLineUnquoted(String line) {
        // Count commas to pre-allocate
        int colCount = expectedColumnCount > 0 ? expectedColumnCount : countChar(line, ',') + 1;
        String[] result = new String[colCount];
        int fieldIdx = 0;
        int start = 0;

        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ',') {
                String field = line.substring(start, i);
                if (fieldIdx < result.length) {
                    result[fieldIdx] = field;
                } else {
                    result = growArray(result, fieldIdx, field);
                }
                fieldIdx++;
                start = i + 1;
            }
        }
        // Last field
        String field = line.substring(start);
        if (fieldIdx < result.length) {
            result[fieldIdx] = field;
        } else {
            result = growArray(result, fieldIdx, field);
        }
        fieldIdx++;

        if (expectedColumnCount <= 0) {
            expectedColumnCount = fieldIdx;
        }
        if (fieldIdx < result.length) {
            return java.util.Arrays.copyOf(result, fieldIdx);
        }
        return result;
    }

    private static int countChar(String s, char c) {
        int count = 0;
        for (int i = 0; i < s.length(); i++) {
            if (s.charAt(i) == c) count++;
        }
        return count;
    }

    private static String[] growArray(String[] arr, int idx, String value) {
        int newLen = arr != null ? Math.max(arr.length * 2, idx + 1) : idx + 1;
        String[] newArr = new String[newLen];
        if (arr != null) {
            System.arraycopy(arr, 0, newArr, 0, Math.min(arr.length, idx));
        }
        newArr[idx] = value;
        return newArr;
    }

    private long parseLongSafe(String s) {
        try { return Long.parseLong(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    private int parseIntSafe(String s) {
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }
}
