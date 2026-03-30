package com.jmeterwebinsightreport.core.sla;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration holder for SLA thresholds — loaded from user properties or config file.
 */
public class SlaConfiguration {

    private List<SlaThreshold> thresholds = new ArrayList<>();
    private boolean enabled;

    public SlaConfiguration() {
    }

    // TODO: Load from JMeter properties or external config file

    public List<SlaThreshold> getThresholds() {
        return thresholds;
    }

    public void setThresholds(List<SlaThreshold> thresholds) {
        this.thresholds = thresholds;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public void addThreshold(SlaThreshold threshold) {
        this.thresholds.add(threshold);
    }
}
