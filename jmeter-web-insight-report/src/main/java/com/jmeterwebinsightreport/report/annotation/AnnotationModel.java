package com.jmeterwebinsightreport.report.annotation;

/**
 * Model for user-defined annotations that can be displayed on report charts
 * (e.g., deployment markers, incident timestamps).
 */
public class AnnotationModel {

    private long timestamp;
    private String label;
    private String description;
    private String color;
    private AnnotationType type;

    public enum AnnotationType {
        DEPLOYMENT,
        INCIDENT,
        CUSTOM
    }

    public AnnotationModel() {
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }

    public AnnotationType getType() {
        return type;
    }

    public void setType(AnnotationType type) {
        this.type = type;
    }
}
