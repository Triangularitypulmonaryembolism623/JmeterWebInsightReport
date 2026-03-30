# Export & CI/CD Integration

## Table CSV Export

Every data table in the report has a **CSV download button** (↓ icon):

| Table | Tab | Filename |
|-------|-----|----------|
| Top 5 Slowest Samplers | Summary | `top5-slowest-samplers.csv` |
| Sampler Statistics | Samplers | `sampler-statistics.csv` |
| Error Summary | Errors | `error-summary.csv` |
| Error Details | Errors | `error-details.csv` |
| Sampler Comparison | Compare | `comparison.csv` |

**Key behavior:**
- Exports **all data** — hidden rows, hidden columns, filtered samplers all included
- CSV files include UTF-8 BOM for correct Excel/Google Sheets handling
- Hover tooltip: "Download as CSV"

## Copy Summary

The **"Copy Summary"** button on the Summary tab copies a clean HTML table to the clipboard. Paste directly into:
- Confluence pages
- SharePoint documents
- Email (Outlook, Gmail)
- Wiki pages

The copied table includes: all metric cards, sampler statistics, and SLA status.

## Chart PNG/SVG Export

Every chart has export buttons in its toolbox (top-right corner):
- **PNG** (camera icon) — raster image, good for documents
- **SVG** (image icon) — vector image, good for scaling/printing

## Machine-Readable JSON

Every report generates `web-insight-report.json`:

```json
{
  "testName": "Checkout Load Test",
  "status": "PASS",
  "duration": 75000,
  "totalSamples": 847293,
  "totalErrors": 1017,
  "errorRate": 0.12,
  "samplers": [
    {
      "name": "POST /checkout",
      "sampleCount": 42364,
      "mean": 891,
      "p95": 2340,
      "errorRate": 0.8,
      "apdex": 0.72,
      "slaStatus": "FAIL"
    }
  ],
  "slaViolations": [
    { "sampler": "POST /checkout", "status": "FAIL" }
  ],
  "generatedAt": "2026-03-28 14:30:00"
}
```

**CI/CD usage:**
```bash
# Jenkins pipeline
STATUS=$(jq -r '.status' web-insight-report.json)
if [ "$STATUS" = "FAIL" ]; then exit 1; fi

# GitHub Actions
- run: |
    STATUS=$(jq -r '.status' web-insight-report.json)
    echo "Test status: $STATUS"
    [ "$STATUS" != "FAIL" ]
```

## JUnit XML Output

Optional JUnit XML for native CI/CD integration. Disabled by default.

**Enable:** `-Jwebinsight.report.junit=true` or GUI checkbox.

**Output:** `web-insight-report.xml` (filename matches HTML)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<testsuites>
  <testsuite name="Load Test" tests="5" failures="1" time="75.0">
    <testcase name="GET /api/products" classname="webinsight" time="0.234"/>
    <testcase name="POST /checkout" classname="webinsight" time="2.340">
      <failure message="SLA FAIL: P95 2340ms > 1000ms threshold"
               type="SlaViolation">
        P95: 2340.00 ms (threshold: 1000.00 ms)
        Error Rate: 0.80% (threshold: 5.00%)
      </failure>
    </testcase>
  </testsuite>
</testsuites>
```

Works with: Jenkins, GitHub Actions, Azure DevOps, GitLab CI, CircleCI, and any tool consuming JUnit XML.

## External Data Mode

For very large tests (chart data > 10MB):
- Chart data is written to a separate `web-insight-data.json` file
- The report loads data via `fetch()` from the companion file
- HTML file stays lightweight while preserving all interactivity
- Configured via `externalDataMode` and `externalDataThreshold` properties

## Two Report Generation Paths

| Path | Command | Error Detail | JMX Change |
|------|---------|-------------|------------|
| **Listener** | Add listener to test plan | Full (URL, headers, response body) | One-time |
| **CLI `-g -o`** | `jmeter -g results.jtl -o report/` | Partial (no response body) | None |

Both produce the same interactive report. The listener captures richer error detail during live execution.
