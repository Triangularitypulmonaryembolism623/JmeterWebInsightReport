package com.jmeterwebinsightreport.report.annotation;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * Loads user-defined annotations from a companion JSON file
 * for display on report charts and the Notes tab.
 */
public class AnnotationLoader {

    private static final Logger log = LoggerFactory.getLogger(AnnotationLoader.class);
    private final ObjectMapper objectMapper;

    public AnnotationLoader() {
        this.objectMapper = new ObjectMapper();
        objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Load annotations from the given JSON file.
     *
     * @param annotationFile path to the report-annotations.json file
     * @return parsed annotations, or empty annotations if file doesn't exist or fails
     */
    public ReportAnnotations load(File annotationFile) {
        if (annotationFile == null || !annotationFile.exists()) {
            log.debug("No annotation file found, using empty annotations");
            return new ReportAnnotations();
        }

        try {
            log.info("Loading annotations from: {}", annotationFile.getAbsolutePath());
            return objectMapper.readValue(annotationFile, ReportAnnotations.class);
        } catch (Exception e) {
            log.warn("Failed to load annotations from {}: {}", annotationFile, e.getMessage());
            return new ReportAnnotations();
        }
    }
}
