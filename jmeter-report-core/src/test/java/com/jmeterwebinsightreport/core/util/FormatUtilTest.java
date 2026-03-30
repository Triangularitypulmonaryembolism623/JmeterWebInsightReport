package com.jmeterwebinsightreport.core.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FormatUtilTest {

    @Test
    void shouldFormatMilliseconds() {
        assertEquals("500ms", FormatUtil.formatDuration(500));
    }

    @Test
    void shouldFormatSeconds() {
        String result = FormatUtil.formatDuration(2500);
        assertTrue(result.contains("2"));
    }

    @Test
    void shouldFormatPercentage() {
        assertEquals("95.50%", FormatUtil.formatPercentage(95.5));
    }

    @Test
    void shouldFormatBytes() {
        assertEquals("0 B", FormatUtil.formatBytes(0));
        assertTrue(FormatUtil.formatBytes(1024).contains("KB"));
        assertTrue(FormatUtil.formatBytes(1024 * 1024).contains("MB"));
    }

    @Test
    void shouldFormatDuration_minutes() {
        // 150000ms = 2m 30s
        assertEquals("2m 30s", FormatUtil.formatDuration(150000));
    }

    @Test
    void shouldFormatDuration_hours() {
        // 3661000ms = 1h 1m 1s
        assertEquals("1h 1m 1s", FormatUtil.formatDuration(3661000));
    }

    @Test
    void shouldFormatDuration_zero() {
        assertEquals("0ms", FormatUtil.formatDuration(0));
    }

    @Test
    void shouldFormatBytes_gigabytes() {
        // 1 GB = 1073741824 bytes
        assertTrue(FormatUtil.formatBytes(1073741824L).contains("GB"));
    }
}
