package com.jmeterwebinsightreport.core.util;

import java.text.DecimalFormat;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Formatting utilities for report rendering — durations, numbers, timestamps.
 */
public final class FormatUtil {

    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.##");
    private static final DecimalFormat PERCENTAGE_FORMAT = new DecimalFormat("#0.00");
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private FormatUtil() {
        // utility class
    }

    /**
     * Format a duration in milliseconds to a human-readable string (e.g., "2m 30s", "150ms").
     */
    public static String formatDuration(long millis) {
        if (millis < 1000) {
            return millis + "ms";
        }
        Duration duration = Duration.ofMillis(millis);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        long seconds = duration.toSecondsPart();

        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%d.%ds", seconds, duration.toMillisPart() / 100);
        }
    }

    /**
     * Format a decimal number with grouping (e.g., 1,234.56).
     */
    public static String formatNumber(double value) {
        return DECIMAL_FORMAT.format(value);
    }

    /**
     * Format a percentage value (e.g., 95.50).
     */
    public static String formatPercentage(double value) {
        return PERCENTAGE_FORMAT.format(value) + "%";
    }

    /**
     * Format an Instant to a readable timestamp string.
     */
    public static String formatTimestamp(Instant instant) {
        return TIMESTAMP_FORMAT.format(instant);
    }

    /**
     * Format bytes to human-readable size (KB, MB, GB).
     */
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return DECIMAL_FORMAT.format(bytes / 1024.0) + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return DECIMAL_FORMAT.format(bytes / (1024.0 * 1024.0)) + " MB";
        } else {
            return DECIMAL_FORMAT.format(bytes / (1024.0 * 1024.0 * 1024.0)) + " GB";
        }
    }
}
