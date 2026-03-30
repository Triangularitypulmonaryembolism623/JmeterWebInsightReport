# JMeter Web Insight Report

A JMeter plugin that generates modern, interactive web reports from your load test results — a powerful alternative to JMeter's built-in dashboard.

## Modules

| Module | Description |
|--------|-------------|
| `jmeter-report-core` | Shared models, statistics engine, SLA evaluation |
| `jmeter-web-insight-report` | Web Insight Report generator plugin |

## Building

```bash
mvn clean package
```

The deployable plugin JAR is produced at:
```
jmeter-web-insight-report/target/jmeter-web-insight-report-1.0.0.jar
```

## Installation

Copy the shaded JARs into your JMeter installation:
```bash
cp jmeter-report-core/target/jmeter-report-core-1.0.0.jar $JMETER_HOME/lib/ext/
cp jmeter-web-insight-report/target/jmeter-web-insight-report-1.0.0.jar $JMETER_HOME/lib/ext/
```

## Report Output Configuration

### GUI

The **Web Insight Report** listener has three configurable fields:

| Field | Description | Default |
|-------|-------------|---------|
| Report Title | Title shown in the report header | `JMeter Web Insight Report` |
| Report Filename | Output filename | `web-insight-report.html` |
| Output Directory | Where report files are written | Working directory |

### CLI Properties

All settings can be controlled via JMeter properties (`-J` flags):

```bash
jmeter -n -t test.jmx \
  -Jwebinsight.report.title="Checkout Load Test" \
  -Jwebinsight.report.filename=report_${timestamp}.html \
  -Jwebinsight.report.output=/results/reports
```

| Property | Description | Default |
|----------|-------------|---------|
| `webinsight.report.title` | Report title | `JMeter Web Insight Report` |
| `webinsight.report.filename` | Report filename (JSON filename derived automatically) | `web-insight-report.html` |
| `webinsight.report.output` | Output directory path | Working directory |

### Timestamp in Filename

Use `${timestamp}` in the filename to auto-insert `yyyyMMdd_HHmm`:

```bash
-Jwebinsight.report.filename=report_${timestamp}.html
```

Produces: `report_20260321_1652.html` and `report_20260321_1652.json`

### Docker / CI Example

```bash
jmeter -n -t /tests/load-test.jmx \
  -l /results/results.jtl \
  -Jwebinsight.report.output=/results \
  -Jwebinsight.report.filename=report_${timestamp}.html
```

## Annotations & SLA

Place a `report-annotations.json` file next to the JTL file (or in JMeter's `bin/` directory). It's auto-detected.

```json
{
  "testNotes": "## Objective\nValidate checkout under 500 concurrent users.",
  "slaThresholds": {
    "default": { "p95": 1000, "errorRate": 5.0 },
    "POST /api/checkout": { "p95": 2000, "errorRate": 3.0 }
  },
  "samplerNotes": {
    "POST /api/checkout": "Includes 2s payment processing delay"
  },
  "timelineMarkers": [
    { "timestamp": 1773702727000, "label": "DB Failover", "type": "warning", "description": "Primary DB switched to replica" }
  ]
}
```

- **SLA thresholds** — per-sampler or `default` fallback. Metrics: `p95`, `p99`, `errorRate`, `meanResponseTime`. Report shows PASS/WARN/FAIL badges and threshold lines on charts.
- **Test notes** — Markdown rendered in the Notes tab and Summary tab.
- **Sampler notes** — info icon with tooltip next to sampler names in the table.
- **Timeline markers** — vertical dashed lines on all charts at annotated timestamps.

### Baseline Comparison

Rename a previous run's `results.jtl` to `baseline.jtl` in the same directory. The plugin auto-detects it and adds a **Compare** tab with:
- Current vs baseline response time overlay chart
- Per-sampler diff table with regression detection (>10% p95 increase or >2% error rate increase)

### CI/CD Integration

Every report generation produces `web-insight-report.json`:

```json
{
  "testName": "Checkout Flow Load Test",
  "status": "PASS",
  "totalSamples": 847293,
  "errorRate": 0.12,
  "samplers": [...],
  "slaViolations": [...]
}
```

Use this in Jenkins/GitLab/GitHub Actions for automated pass/fail decisions.

### Output Files

| File | Description |
|------|-------------|
| `web-insight-report.html` | Self-contained interactive Web Insight Report |
| `web-insight-report.json` | Machine-readable CI/CD summary |
| `web-insight-report.xml` | JUnit XML (when enabled via `-Jwebinsight.report.junit=true`) |
| `web-insight-data.json` | External chart data (only for large tests >10MB) |

## Documentation

See the [full documentation](https://aharon890.github.io/JmeterWebInsightReport/) with screenshots covering every tab, feature, and control.

See [FEATURES.md](docs/FEATURES.md) for the feature list.

## Requirements

- Java 11+
- Apache JMeter 5.6.3+
- Maven 3.8+

## License

Apache License 2.0 — see [LICENSE](LICENSE).
