package com.jmeterwebinsightreport.core.filter;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.*;

class SamplerFilterTest {

    @Test
    void shouldAcceptAllWhenNoPatterns() {
        SamplerFilterConfig config = new SamplerFilterConfig();
        SamplerFilter filter = new SamplerFilter(config);
        assertTrue(filter.accept("HTTP Request"));
        assertTrue(filter.accept("Login API"));
    }

    @Test
    void shouldRejectNullAndEmpty() {
        SamplerFilterConfig config = new SamplerFilterConfig();
        SamplerFilter filter = new SamplerFilter(config);
        assertFalse(filter.accept(null));
        assertFalse(filter.accept(""));
    }

    @Test
    void shouldFilterByIncludePattern() {
        SamplerFilterConfig config = new SamplerFilterConfig();
        config.setIncludePatterns(Collections.singletonList("HTTP*"));
        SamplerFilter filter = new SamplerFilter(config);
        assertTrue(filter.accept("HTTP Request"));
        assertFalse(filter.accept("JDBC Request"));
    }

    @Test
    void shouldFilterByExcludePattern() {
        SamplerFilterConfig config = new SamplerFilterConfig();
        config.setExcludePatterns(Arrays.asList("Debug*", "BeanShell*"));
        SamplerFilter filter = new SamplerFilter(config);
        assertTrue(filter.accept("HTTP Request"));
        assertFalse(filter.accept("Debug Sampler"));
    }
}
