package com.jmeterwebinsightreport.core.filter;

import java.util.regex.Pattern;

/**
 * Filters sampler names against configured include/exclude patterns.
 */
public class SamplerFilter {

    private final SamplerFilterConfig config;

    public SamplerFilter(SamplerFilterConfig config) {
        this.config = config;
    }

    /**
     * Check whether a sampler should be included in the report.
     *
     * @param samplerName the sampler label
     * @return true if the sampler passes the filter
     */
    public boolean accept(String samplerName) {
        if (samplerName == null || samplerName.isEmpty()) {
            return false;
        }

        // If include patterns are specified, sampler must match at least one
        if (!config.getIncludePatterns().isEmpty()) {
            boolean matchesInclude = config.getIncludePatterns().stream()
                    .anyMatch(pattern -> matchesPattern(samplerName, pattern));
            if (!matchesInclude) {
                return false;
            }
        }

        // If exclude patterns are specified, sampler must not match any
        if (!config.getExcludePatterns().isEmpty()) {
            boolean matchesExclude = config.getExcludePatterns().stream()
                    .anyMatch(pattern -> matchesPattern(samplerName, pattern));
            if (matchesExclude) {
                return false;
            }
        }

        return true;
    }

    private boolean matchesPattern(String name, String pattern) {
        // Support simple wildcard patterns (glob-style) by converting to regex
        String regex = pattern
                .replace(".", "\\.")
                .replace("*", ".*")
                .replace("?", ".");
        return Pattern.matches(regex, name);
    }
}
