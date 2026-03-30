package com.jmeterwebinsightreport.report.annotation;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.net.URL;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationLoaderTest {

    private AnnotationLoader loader;

    @BeforeEach
    void setUp() {
        loader = new AnnotationLoader();
    }

    private File getResourceFile(String name) {
        URL url = getClass().getClassLoader().getResource("annotations/" + name);
        assertNotNull(url, "Test resource not found: annotations/" + name);
        return new File(url.getFile());
    }

    @Test
    void loadFromFile_parsesCompleteAnnotations() {
        ReportAnnotations annotations = loader.load(getResourceFile("full-annotations.json"));

        assertNotNull(annotations);
        assertEquals("1.0", annotations.getVersion());
        assertEquals("PASS", annotations.getVerdict());
        assertNotNull(annotations.getTestNotes());
        assertFalse(annotations.getTestNotes().isEmpty());
        assertNotNull(annotations.getSlaThresholds());
        assertFalse(annotations.getSlaThresholds().isEmpty());
        assertNotNull(annotations.getSamplerNotes());
        assertFalse(annotations.getSamplerNotes().isEmpty());
        assertNotNull(annotations.getTimelineMarkers());
        assertFalse(annotations.getTimelineMarkers().isEmpty());
    }

    @Test
    void loadFromFile_parsesMinimalAnnotations() {
        ReportAnnotations annotations = loader.load(getResourceFile("minimal-annotations.json"));

        assertNotNull(annotations);
        assertEquals("1.0", annotations.getVersion());
        // Minimal file has only version; other fields should be defaults
        assertTrue(annotations.getTimelineMarkers().isEmpty());
        assertTrue(annotations.getSamplerNotes().isEmpty());
        assertTrue(annotations.getSlaThresholds().isEmpty());
    }

    @Test
    void loadFromFile_parsesSlaThresholds() {
        ReportAnnotations annotations = loader.load(getResourceFile("sla-only-annotations.json"));

        assertNotNull(annotations);
        Map<String, ReportAnnotations.SlaThresholdConfig> thresholds = annotations.getSlaThresholds();
        assertNotNull(thresholds);
        assertTrue(thresholds.containsKey("default"));

        ReportAnnotations.SlaThresholdConfig defaultThreshold = thresholds.get("default");
        assertEquals(500.0, defaultThreshold.getP95());
        assertEquals(1000.0, defaultThreshold.getP99());
        assertEquals(5.0, defaultThreshold.getErrorRate());
        assertEquals(300.0, defaultThreshold.getMeanResponseTime());
    }

    @Test
    void loadFromFile_handlesMissingFile() {
        // AnnotationLoader.load returns empty annotations for non-existent file
        ReportAnnotations annotations = loader.load(new File("/nonexistent/path/annotations.json"));

        assertNotNull(annotations, "Should return empty annotations, not null");
        assertEquals("1.0", annotations.getVersion());
        assertTrue(annotations.getTimelineMarkers().isEmpty());
    }

    @Test
    void loadFromFile_handlesNullFile() {
        ReportAnnotations annotations = loader.load(null);

        assertNotNull(annotations, "Should return empty annotations for null file");
        assertEquals("1.0", annotations.getVersion());
    }

    @Test
    void loadFromFile_handlesInvalidJson() {
        // AnnotationLoader.load returns empty annotations on parse failure (does not throw)
        ReportAnnotations annotations = loader.load(getResourceFile("invalid-annotations.json"));

        assertNotNull(annotations, "Should return empty annotations for invalid JSON");
        assertEquals("1.0", annotations.getVersion());
    }

    @Test
    void loadFromFile_parsesTimelineMarkers() {
        ReportAnnotations annotations = loader.load(getResourceFile("full-annotations.json"));

        List<ReportAnnotations.TimelineMarker> markers = annotations.getTimelineMarkers();
        assertNotNull(markers);
        assertEquals(2, markers.size());

        ReportAnnotations.TimelineMarker first = markers.get(0);
        assertEquals(1700000002000L, first.getTimestamp());
        assertEquals("DB Failover", first.getLabel());
        assertEquals("warning", first.getType());
        assertEquals("Primary DB switched to replica", first.getDescription());
        assertEquals("#ff9800", first.getColor());

        ReportAnnotations.TimelineMarker second = markers.get(1);
        assertEquals(1700000004000L, second.getTimestamp());
        assertEquals("Recovery", second.getLabel());
        assertEquals("info", second.getType());
    }

    @Test
    void loadFromFile_parsesSamplerNotes() {
        ReportAnnotations annotations = loader.load(getResourceFile("full-annotations.json"));

        Map<String, String> notes = annotations.getSamplerNotes();
        assertNotNull(notes);
        assertEquals(2, notes.size());
        assertEquals("Includes 2s payment processing delay", notes.get("POST /api/checkout"));
        assertEquals("Cached response, should be fast", notes.get("GET /api/products"));
    }

    @Test
    void loadFromFile_parsesTestNotes() {
        ReportAnnotations annotations = loader.load(getResourceFile("full-annotations.json"));

        String testNotes = annotations.getTestNotes();
        assertNotNull(testNotes);
        assertTrue(testNotes.contains("Load Test Results"));
        assertTrue(testNotes.contains("checkout flow"));
    }

    @Test
    void loadFromFile_parsesSamplerSpecificSlaThresholds() {
        ReportAnnotations annotations = loader.load(getResourceFile("full-annotations.json"));

        Map<String, ReportAnnotations.SlaThresholdConfig> thresholds = annotations.getSlaThresholds();
        assertTrue(thresholds.containsKey("default"));
        assertTrue(thresholds.containsKey("POST /api/checkout"));

        ReportAnnotations.SlaThresholdConfig checkout = thresholds.get("POST /api/checkout");
        assertEquals(2000.0, checkout.getP95());
        assertEquals(3.0, checkout.getErrorRate());
        assertNull(checkout.getP99(), "Sampler-specific threshold should not have p99 if not set");
    }

    @Test
    void loadFromFile_parsesComparisonThresholds() {
        ReportAnnotations annotations = loader.load(getResourceFile("full-annotations.json"));

        ReportAnnotations.ComparisonThresholdConfig ct = annotations.getComparisonThresholds();
        assertNotNull(ct, "comparisonThresholds should be parsed from full-annotations.json");
        assertEquals(15.0, ct.getP95PctChange());
        assertEquals(3.0, ct.getErrorRateChange());
        assertEquals(20.0, ct.getMeanPctChange());
        assertEquals(25.0, ct.getP99PctChange());
        assertEquals(10.0, ct.getThroughputPctChange());
    }
}
