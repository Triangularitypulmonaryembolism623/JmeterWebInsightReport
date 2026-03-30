package com.jmeterwebinsightreport.core.filter;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration for filtering which samplers are included/excluded from the report.
 */
public class SamplerFilterConfig {

    private List<String> includePatterns = new ArrayList<>();
    private List<String> excludePatterns = new ArrayList<>();
    private boolean excludeTransactionControllers;

    public SamplerFilterConfig() {
    }

    public List<String> getIncludePatterns() {
        return includePatterns;
    }

    public void setIncludePatterns(List<String> includePatterns) {
        this.includePatterns = includePatterns;
    }

    public List<String> getExcludePatterns() {
        return excludePatterns;
    }

    public void setExcludePatterns(List<String> excludePatterns) {
        this.excludePatterns = excludePatterns;
    }

    public boolean isExcludeTransactionControllers() {
        return excludeTransactionControllers;
    }

    public void setExcludeTransactionControllers(boolean excludeTransactionControllers) {
        this.excludeTransactionControllers = excludeTransactionControllers;
    }
}
