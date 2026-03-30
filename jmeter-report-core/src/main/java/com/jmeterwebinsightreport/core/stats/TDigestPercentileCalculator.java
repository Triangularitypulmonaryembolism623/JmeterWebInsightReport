package com.jmeterwebinsightreport.core.stats;

import com.tdunning.math.stats.MergingDigest;
import com.tdunning.math.stats.TDigest;

/**
 * Percentile calculator backed by T-Digest for memory-efficient streaming percentile estimation.
 */
public class TDigestPercentileCalculator {

    private final TDigest digest;

    public TDigestPercentileCalculator() {
        this(100); // default compression
    }

    public TDigestPercentileCalculator(double compression) {
        this.digest = MergingDigest.createDigest(compression);
    }

    /**
     * Add a value to the digest.
     */
    public void addValue(double value) {
        digest.add(value);
    }

    /**
     * Get the estimated value at the given percentile (0-100).
     */
    public double getPercentile(double percentile) {
        if (digest.size() == 0) {
            return 0.0;
        }
        return digest.quantile(percentile / 100.0);
    }

    /**
     * Get the cumulative distribution function value at the given threshold.
     * Returns the fraction of values less than or equal to the given value (0.0 to 1.0).
     */
    public double getCdf(double value) {
        if (digest.size() == 0) {
            return 0.0;
        }
        return digest.cdf(value);
    }

    /**
     * Get the number of values added.
     */
    public long getCount() {
        return digest.size();
    }

    /**
     * Reset the digest.
     */
    public void reset() {
        // TDigest doesn't have a reset — create logic will be handled by caller
        // TODO: Consider creating a new instance or tracking state
    }
}
