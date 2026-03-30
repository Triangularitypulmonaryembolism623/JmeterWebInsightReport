# JMeter Web Insight Report — Feature Guide

A single self-contained web report generated from JMeter test results. No server, no CDN, no internet required — just open in any browser.

---

## Report Structure

The report is organized into **6 tabs**:

| Tab | Purpose |
|-----|---------|
| **Summary** | At-a-glance metrics, response time chart, top 5 slowest samplers, engineer notes |
| **Timeline** | Full time-series charts — response time, throughput, error rate, bytes, connect/latency, response codes, percentile comparison, scatter plot |
| **Samplers** | Detailed statistics table with sorting, filtering, column control, inline detail expansion |
| **Errors** | Error breakdown charts, error timeline, error detail records with expandable response bodies |
| **Compare** | Baseline vs current run overlay and regression detection |
| **Notes** | Test notes (Markdown), timeline annotations, verdict |

---

## Interactive Charts (Apache ECharts)

All charts support:
- **Hover tooltips** — exact values at any data point
- **Zoom** — click-drag or mouse wheel to zoom into time ranges
- **Pan** — shift+drag to scroll the zoomed view without losing zoom level
- **Time range slider** — draggable slider at bottom of each chart
- **Legend toggle** — click legend items to show/hide individual series
- **Legend truncation** — long sampler names truncated at 35 characters in chart legends; hover tooltip shows full name
- **Hide/show charts** — hide individual charts via "Hide" button on any tab (Timeline, Errors, Compare); "Show N Hidden Charts" restores them
- **Drag-and-drop reorder** — drag charts by grip handle to rearrange on any tab; layout persisted per tab in localStorage
- **Cross-chart synchronization** — hover/zoom one chart, all others follow via echarts.connect()
- **Timer/Clock toggle** — switch between wall-clock time (14:30:50) and elapsed timer (1:20:15) on all timeline charts. Adaptive precision: seconds shown when zoomed in, hidden for long tests. Time Range selector always shows full date+time.
- **Smart Y-axis units** — response time axes auto-adapt: ms for fast responses, sec/min/hr for slow ones. Unit determined by the max value in the chart. Tooltips also show human-readable durations (e.g., `5m 50s` instead of `350000 ms`).
- **PNG export** — download button (camera icon) on each chart
- **SVG export** — download button next to PNG for vector graphics
- **CSV export** — download button on every data table (see [Table CSV Export](#table-csv-export))
- **Fullscreen** — expand any chart to full viewport
- **Ramp-up marking** — semi-transparent overlay on the ramp-up period (when configured)

### Available Charts

**Timeline tab:**
- Response Time Over Time (per sampler, with active threads overlay)
- Throughput Over Time (per sampler)
- Error Rate Over Time (per sampler)
- Bytes Throughput Over Time (received/sent bytes per sampler)
- Connect Time vs Latency vs Processing (stacked area breakdown)
- Response Codes Over Time (stacked bar by HTTP status code)
- Response Time Percentiles Comparison (grouped bar: p50/p90/p95/p99/max per sampler)
- Response Time Scatter Plot (individual data points for pattern/outlier detection)
- Response Time Heatmap (response time distribution over time — see below)

**Error tab:**
- Error Breakdown — pie chart by HTTP status code
- Error Breakdown — bar chart by sampler
- Error Timeline — scatter plot of individual errors over time

**Compare tab:**
- Baseline Comparison — current vs baseline overlay

**Per-sampler detail view (inline in Samplers tab, configurable via dropdown):**
- Response Time Histogram (distribution bins)
- Per-Sampler Response Time Over Time
- Per-Sampler Bytes (received/sent)
- Throughput Over Time
- Error Rate Over Time
- Connect Time & Latency
- Percentiles Over Time (P50/P90/P95/P99)

---

## Sampler Filtering

A persistent **filter bar** appears across all tabs:

- **Sampler checkboxes** — toggle each sampler on/off; affects all charts, tables, and error views globally
- **Select All / None** — quick toggle buttons
- **Deselected samplers** appear dimmed (45% opacity) with hidden checkmark
- **Auto-hide utility samplers** — JSR223, Debug, BeanShell, and JMeter internal samplers (\_\_*) are unchecked by default
- **Auto-hide 0-request samplers** — samplers with no requests are hidden automatically
- **Sampler name truncation** — long names truncated with ellipsis + hover tooltip in filter bar

When samplers are deselected:
- Their lines disappear from all time-series charts
- Their errors disappear from error breakdown, timeline, and tables
- Summary metrics and charts update accordingly

### Filter Presets
Save and load filter configurations:
- **Save Preset** — saves current sampler visibility, metric, palette, line thickness, and hidden columns to localStorage
- **Load Preset** — select from saved presets via dropdown
- **Delete Preset** — remove saved presets
- **Default preset** — resets all settings (visibility, metric, palette, line thickness, columns) to initial state

### Time Range Selector
A time range filter in the filter bar shows the current zoomed range:
- Automatically syncs with chart zoom/pan state
- **Reset** button restores all charts to full time range

### Collapsible Filter Rows
A **"Filters ▾"** dropdown in the tab navigation bar with per-row visibility toggles:
- ☑ Palette / Line / Metric / Granularity
- ☑ Time Range / Presets
- ☑ Samplers

Uncheck any row to fully collapse it and reclaim vertical screen space. Each row is independently toggleable. State persists in localStorage. Hidden rows don't affect active settings — all filters remain applied.

---

## Color Customization

### Palette Selection
Three built-in palettes available via toggle buttons:
- **Default** — standard ECharts palette
- **Okabe-Ito** — colorblind-friendly palette (deuteranopia/protanopia safe)
- **High Contrast** — maximum visual separation

### Per-Sampler Color Override
Click the **color dot** next to any sampler name in the filter bar to open a color picker:
- Grid of swatches from all palettes
- Native color input (`<input type="color">`) for any custom color
- Custom colors persist in **localStorage** across page reloads

### Line Thickness
Toggle between **Thin** (1px), **Medium** (2px), and **Thick** (3px) line widths for all chart lines.

All color and thickness settings persist in localStorage.

---

## Percentile Toggle

Switch the response time charts between metrics:
- **Mean** — average response time
- **P50** — 50th percentile (median)
- **P90** — 90th percentile
- **P95** — 95th percentile
- **P99** — 99th percentile
- **Max** — maximum response time

The selected metric applies to both Summary and Timeline response time charts. Per-sampler percentiles are computed per 1-second time bucket.

### Granularity Control

A dropdown in the filter bar lets you adjust the time-series aggregation level: **1s, 5s, 10s, 30s, 1min, 5min, 10min**. Higher granularity smooths charts by averaging data points into larger buckets — useful for long tests where 1-second resolution produces too many points.

**Auto-detection:** The default granularity adapts to test duration:
- < 5 minutes → 1s (raw)
- 5-15 minutes → 10s
- 15-30 minutes → 30s
- 30-60 minutes → 1min
- 1-2 hours → 5min
- 2+ hours → 10min

Changing granularity instantly refreshes all timeline charts. The underlying data remains at 1-second resolution — only the display aggregation changes.

---

## Response Time Heatmap

A heatmap visualization at the bottom of the Timeline tab showing response time distribution over time:
- **X-axis:** time (same as other timeline charts)
- **Y-axis:** response time buckets — 0-50ms, 50-100ms, 100-200ms, 200-500ms, 500-1K, 1K-2K, 2K-5K, 5K+
- **Color intensity:** number of samples in each bucket (light blue = few, red = many)

Reveals patterns that line charts miss:
- Bimodal distributions (fast cache hits + slow DB queries in different buckets)
- Response time shifts over time (e.g., requests gradually moving to a higher bucket)
- Outlier bursts (a cluster of slow requests appearing in a higher bucket)

**Per-sampler tooltip:** When multiple samplers are visible, hovering a cell shows the per-sampler breakdown (which samplers contributed how many samples to that bucket).

**Sampler filter integration:** Respects the sampler filter bar — deselecting a sampler removes its data from the heatmap. Transaction children excluded when toggle is off.

All standard chart features: drag-to-reorder, hide/show, fullscreen, PNG/SVG export, zoom slider, dark/light mode.

---

## Samplers Tab

### Statistics Table
Full table with columns: Sampler, Samples, Errors, Error %, Mean, Median, P90, P95, P99, Min, Max, Throughput, Recv KB/s.

- **Sortable** — click any column header (ascending/descending)
- **Searchable** — text filter box above the table
- **Column visibility** — "Columns" dropdown to show/hide any column
- **Drag-and-drop column reorder** — drag column headers to rearrange; order persisted in localStorage
- **Row hiding** — click the X button on any row to hide it; "Show N Hidden Rows" button to restore
- **SLA badges** — PASS/WARN/FAIL text badge next to each sampler name (when SLA configured)
- **Sampler notes** — blue (i) icon with tooltip when notes are defined in annotations

### Virtual "Σ Total" Sampler
A synthetic aggregate row always appears at the top of the statistics table:
- Shows combined metrics across all visible samplers (weighted averages for response times, summed counts, overall error rate)
- Can be toggled on/off via the filter bar like any sampler
- Included in time-series charts when visible (dashed line)

### Click-to-Expand Detail View
- **Expand indicator** — chevron (▼) on each row hints at expandable detail; flips ▲ and turns blue when expanded
- **Long name truncation** — sampler names in table truncated at 350px with ellipsis; hover tooltip shows full name
- **Compact name cell** — expand icon, sampler name, and hide button merged into a single cell

Click any sampler row to expand an inline detail panel with **configurable mini-charts**:
- **7 chart types** available via dropdown selector on each slot:
  - Response Time Distribution (histogram)
  - Response Time Over Time
  - Bytes (Recv / Sent)
  - Throughput Over Time
  - Error Rate Over Time
  - Connect Time & Latency (3-line: connect, latency, response time)
  - Percentiles Over Time (4-line: P50, P90, P95, P99)
- **3 default slots** — histogram, response time, bytes
- **+ Add Chart / × Remove Chart** buttons in the toolbar — adds or removes a 4th slot across all expanded rows
- **Global sync** — changing a dropdown or adding/removing a slot applies to all currently expanded sampler rows
- **Persistent** — chart configuration saved in localStorage, survives page refresh

### Transaction Controller Hierarchy
Transaction Controllers are auto-detected from sampler naming patterns:
- **Default view:** Only parent transactions and standalone samplers are shown — matches JMeter's native Aggregate Report. Child samplers are hidden from the table, filter bar, and charts to avoid double-counting.
- **"Transaction Details" toggle:** Button in the Samplers tab toolbar. When activated, child samplers appear indented under their parent with full statistics and mini-charts. The toggle also reveals child sampler checkboxes in the filter bar.
- **Σ Total always correct:** The total row always sums parents + standalone samplers only, regardless of toggle state. Parents already aggregate their children, so children are never double-counted.
- **Parent indicators:** Transaction parents display a ▶ triangle icon
- **Child indentation:** Children are visually indented in both the table and filter bar
- **Hierarchy detection:** Uses sampler name prefix matching (e.g., "Login Flow" parent contains "Login Flow - Authenticate", "Login Flow - Get Profile")

---

## Error Deep-Dive

The Errors tab provides four views:

1. **Error Breakdown** — side-by-side:
   - Pie/donut chart by HTTP status code (fixed color per code: 500=red, 401=blue, etc.)
   - Bar chart by sampler (uses sampler palette colors)
2. **Error Timeline** — scatter plot showing individual error occurrences over time, colored by sampler
3. **Error Summary Table** — grouped by sampler + response code, with count and percentages
   - Search filter, column toggle, row hide
   - Fixed-width columns with ellipsis truncation and native title tooltips
4. **Error Details Table** — individual error records with timestamp, sampler, code, message, thread
   - Search filter, column toggle, row hide
   - Fixed-width columns with ellipsis truncation and native title tooltips

### Expandable Error Rows
Click any error detail row to expand and see:
- **Request URL** — the URL that failed
- **Request Headers** — captured headers (truncated to 2KB)
- **Response Body** — the error response (truncated to 4KB), with JSON auto-detection

Note: URL and response body are captured in listener mode. When parsing JTL files, URL is captured if the `URL` column is present; response body is not available in JTL format.

All error views respect the sampler filter — deselecting a sampler hides its errors everywhere.

---

## SLA / Pass-Fail

### Configuration
SLA thresholds are defined in `report-annotations.json`:

```json
{
  "slaThresholds": {
    "default": { "p95": 1000, "errorRate": 5.0 },
    "POST /api/checkout": { "p95": 2000, "errorRate": 3.0 }
  }
}
```

Supports per-sampler overrides with a `"default"` fallback. Metrics: `p95`, `p99`, `errorRate`, `meanResponseTime`.

### Evaluation
- **PASS** — metric under 80% of threshold
- **WARN** — metric between 80% and 100% of threshold
- **FAIL** — metric exceeds threshold

### Display
- **Header badge** — overall SLA status (SLA PASS / SLA WARNING / SLA FAIL)
- **Sampler table** — per-sampler PASS/WARN/FAIL text badge
- **Summary cards** — SLA badge next to Avg P95 and Avg P99 values
- **Chart threshold lines** — horizontal red dashed line at SLA limit on response time and error rate charts

### Apdex Score

The industry-standard Apdex (Application Performance Index) is computed per sampler and overall:

**Apdex = (Satisfied + Tolerating/2) / Total**
- Satisfied: response time < T
- Tolerating: response time >= T and < 4T
- Frustrated: response time >= 4T

Score from 0 to 1, color-coded:
- \> 0.94 = Excellent (green)
- \> 0.85 = Good (blue)
- \> 0.70 = Fair (amber)
- \> 0.50 = Poor (orange)
- <= 0.50 = Unacceptable (red)

**Configuration:** Default T = 500ms. Override via `-Jwebinsight.apdex.threshold=300` or `slaThresholds.apdexThreshold` in annotations JSON.

**Display:** Apdex card in Summary tab + Apdex column in Samplers table + `apdex` field in CI/CD JSON.

---

## Annotations & Notes

### Setup
Place a `report-annotations.json` file next to the report output (auto-detected in JMeter's bin/ directory):

```json
{
  "version": "1.0",
  "testNotes": "## Test Objective\nValidate checkout under 500 users...",
  "verdict": "CONDITIONAL_PASS",
  "timelineMarkers": [
    { "timestamp": 1773702727000, "label": "Test Start", "type": "info", "description": "Ramp-up begins" },
    { "timestamp": 1773702740000, "label": "DB Failover", "type": "warning", "description": "Primary DB switched" }
  ],
  "samplerNotes": {
    "POST /api/checkout": "Includes 2s payment processing delay"
  },
  "slaThresholds": { ... }
}
```

### Features
- **Test Notes** — rendered as Markdown (headers, bold, italic, lists, code blocks)
- **Engineer Notes on Summary** — when notes exist, a compact view appears on the Summary tab
- **Verdict badge** — PASS/WARN/FAIL/CONDITIONAL_PASS displayed prominently
- **Timeline markers** — vertical dashed lines on all charts at annotated timestamps; color-coded by type (info=blue, warning=amber, error=red, deployment=blue, custom=purple)
- **Sampler notes** — info icon next to sampler names in the table, tooltip on hover
- **In-browser editor** — when no notes exist, a Markdown editor with live preview and "Save Annotations" button that downloads a JSON file for future runs

---

## Run Comparison

### Setup
Place a previous run's JTL as `baseline.jtl` in the output directory. The plugin auto-detects it on the next run.

### Comparison Charts
- **Response Time Comparison** — per-sampler current (solid) vs baseline (dashed) lines, aligned by elapsed time starting at 0. Respects sampler filter and metric selector (Mean/P50/P90/P95/P99).
- **Throughput Comparison** — same per-sampler current vs baseline layout for throughput.
- Both charts support fullscreen, hide/show, zoom, cross-chart sync, and dark mode.

### Custom Regression Thresholds
Regression detection thresholds are configurable instead of hardcoded. Default: P95 increase >10% or error rate increase >2%.

**Configuration via `report-annotations.json`:**
```json
{
  "comparisonThresholds": {
    "p95PctChange": 15,
    "errorRateChange": 3,
    "meanPctChange": 20,
    "p99PctChange": -1,
    "throughputPctChange": -1
  }
}
```

**Configuration via JMeter properties:**
```bash
-Jwebinsight.compare.p95.threshold=15
-Jwebinsight.compare.errorrate.threshold=3
-Jwebinsight.compare.mean.threshold=20
```

**Supported metrics** (set to -1 to disable):
- `p95PctChange` — P95 response time % increase (default: 10)
- `errorRateChange` — absolute error rate increase (default: 2)
- `meanPctChange` — mean response time % increase (default: disabled)
- `p99PctChange` — P99 response time % increase (default: disabled)
- `throughputPctChange` — throughput % decrease (default: disabled)

**Live UI controls** in the Compare tab — adjust thresholds and click "Apply" to re-evaluate regression flags client-side without regenerating the report. Useful for exploring what thresholds make sense for your application.

### Sampler Comparison Table
- All sampler statistics columns: Samples, Errors, Error %, Mean, Median, P90, P95, P99, Min, Max, Throughput, Recv KB/s
- Current values with inline superscript deltas (red/green, or blue/orange in colorblind mode)
- Sortable columns, drag-to-reorder, column toggle, row hide, search filter
- Status badges: OK, REGRESSION, NEW — based on configurable thresholds

### "What Changed" Summary
Auto-generated text summary in the **Notes tab** when baseline comparison exists. Shows:
- Overall P95 and error rate trend direction
- List of regressed samplers (with % change)
- List of improved samplers (with % change)
- New samplers not in baseline

Updates live when regression thresholds are adjusted via the Compare tab controls.

---

## Smart Defaults

The report auto-detects and handles common patterns:
- **Auto-hide utility samplers** — samplers matching JSR223*, Debug*, BeanShell*, __* are hidden by default (can be toggled back on)
- **Auto-hide empty samplers** — samplers with 0 requests are hidden
- **Transaction Controller detection** — parent/child relationships detected from naming patterns; children collapsed by default
- **Ramp-up period marking** — when configured, a semi-transparent overlay marks the ramp-up phase on all timeline charts

---

## CI/CD Integration

### Machine-Readable JSON Output
Every report generation produces a `web-insight-report.json` alongside the web report:

```json
{
  "testName": "Checkout Flow Load Test",
  "status": "PASS",
  "duration": 75000,
  "totalSamples": 847293,
  "totalErrors": 1017,
  "errorRate": 0.12,
  "samplers": [
    { "name": "POST /checkout", "sampleCount": 42364, "mean": 891, "p95": 2340, "errorRate": 0.8, "slaStatus": "FAIL" }
  ],
  "slaViolations": [
    { "sampler": "POST /checkout", "status": "FAIL" }
  ]
}
```

Use this JSON in Jenkins/GitLab/GitHub Actions pipelines for automated pass/fail decisions.

### JUnit XML Output

Optional JUnit XML report for native CI/CD test result integration. Disabled by default.

**Enable via:**
- GUI checkbox: "Generate JUnit XML" in listener settings
- CLI property: `-Jwebinsight.report.junit=true`

**Output:** `web-insight-report.xml` (or matching the HTML filename). Each sampler = test case, SLA violations = test failures with metric details. If no SLA is configured, all test cases pass.

Works with Jenkins, GitHub Actions, Azure DevOps, GitLab CI, and any tool that consumes JUnit XML.

### Table CSV Export

Every data table in the report has a **CSV download icon** (↓) in the top-right corner of its section header, matching the chart toolbox icon pattern:

| Table | Tab | CSV Filename |
|-------|-----|-------------|
| Top 5 Slowest Samplers (by P95) | Summary | `top5-slowest-samplers.csv` |
| Sampler Statistics | Samplers | `sampler-statistics.csv` |
| Error Summary | Errors | `error-summary.csv` |
| Error Details | Errors | `error-details.csv` |
| Sampler Comparison | Compare | `comparison.csv` |

**Key behavior:**
- Exports **all data** from the underlying data model — hidden rows, hidden columns, filtered samplers, and collapsed children are all included
- CSV files include UTF-8 BOM for correct Excel handling
- Hover tooltip: "Download as CSV"
- Icon styled for both light and dark modes

### Copy Summary for Confluence/SharePoint
The "Copy Summary" button on the Summary tab copies a clean HTML table to your clipboard, formatted for pasting into:
- Confluence pages
- SharePoint documents
- Email reports
- Wiki pages

---

## External Data Mode

For very large tests (data exceeding ~10MB), the report automatically switches to external data mode:
- Chart data is written to a separate `web-insight-data.json` file
- The report loads data via `fetch()` from the companion file
- This keeps the HTML file lightweight while preserving all interactivity
- Configurable via `externalDataMode` and `externalDataThreshold` properties

---

## Dark Mode

Toggle via the crescent/sun icon in the header. Full dark theme for all components:
- Charts reinitialize with ECharts dark theme
- All UI elements, tables, cards, badges, detail panels adapt
- SLA badges use dark-appropriate colors
- Persists in localStorage

---

## Colorblind Mode

Toggle via the eye icon in the header. Swaps all semantic colors from green/red to blue/orange:
- Pass/OK badges → blue, Fail/Regression badges → orange
- Comparison table deltas → blue (improvement) / orange (regression)
- Error cards, error codes → orange tones
- Works independently with dark mode (4 combinations: light, dark, light+cb, dark+cb)
- Persists in localStorage
- Chart line palettes not affected (use Okabe-Ito palette option separately for colorblind-friendly chart colors)

---

## JMeter Integration

### Two Report Generation Paths

| Path | How | JMX change? | Error bodies? |
|------|-----|-------------|---------------|
| **Listener** | Add Web Insight Report listener to JMX | Yes (one-time) | Full (response body, headers, URL) |
| **CLI `-g -o`** | `jmeter -g results.jtl -o report/` | None | Partial (no response body — not in JTL CSV) |

Both paths produce the same interactive Web Insight Report. The listener path captures richer error detail because it has access to the live `SampleResult` object during test execution. The CLI path is ideal for CI/CD pipelines and Docker environments where you can't or don't want to modify the test plan.

### CLI Report Generation (`-g -o`)

Generate a Web Insight Report from an existing JTL file — no test plan changes required:

```bash
jmeter -g results.jtl -o report-dir/
```

**Setup:** Add one line to `$JMETER_HOME/bin/reportgenerator.properties`:
```properties
jmeter.reportgenerator.exporter.webinsight.classname=com.jmeterwebinsightreport.report.exporter.WebInsightDataExporter
```

The Web Insight Report will be generated alongside JMeter's built-in HTML dashboard in the same output directory. Our report is named `web-insight-report.html` (distinct from JMeter's `index.html`).

**With `-J` properties:**
```bash
jmeter -g results.jtl -o report-dir/ \
  -Jwebinsight.report.title="My Load Test" \
  -Jwebinsight.report.filename=report_${timestamp}.html \
  -Jwebinsight.report.baseline=/path/to/baseline.jtl \
  -Jwebinsight.report.annotations=/path/to/report-annotations.json \
  -Jwebinsight.report.rampup=30000
```

**Collision detection:** If a Web Insight Report already exists in the output directory (generated by the listener during the test), the CLI exporter skips generation to avoid overwriting the richer listener report.

**Note on error details:** When generating from JTL, error records will have the response code, message, thread, and URL — but not the response body or request headers (these are not stored in JTL CSV format). The report shows an informational message when expanding error details that lack this data.

### As a Listener
Add **Web Insight Report** listener to your test plan (GUI or non-GUI mode). Auto-detects `report-annotations.json` and `baseline.jtl` in the output directory. Works with `-n` (non-GUI) mode for CI/CD — the listener fires at test end and generates the full report with complete error details.

### GUI Configuration

**Output Directory field:**

| Value | Resolves to |
|-------|-------------|
| *(empty)* | Working directory (where jmeter was launched from) |
| `D:\reports` | `D:\reports` (absolute path) |
| `reports` | `{working dir}\reports` (relative) |
| `..\reports` | Parent of working directory |

**Report Filename field:**

| Value | Produces |
|-------|---------|
| *(empty)* | `web-insight-report.html` + `.json` |
| `my-report.html` | `my-report.html` + `my-report.json` |
| `report_${timestamp}.html` | `report_20260324_1850.html` + `.json` |

**Report Title field:**

| Value | Result |
|-------|--------|
| *(empty)* | `JMeter Web Insight Report` (default) |
| `Checkout Load Test` | Custom title in report header |

### CLI Properties

All settings can be overridden via JMeter `-J` properties. CLI properties take precedence over GUI fields.

**Report output:**

| Property | Default |
|----------|---------|
| `webinsight.report.output` | Working directory |
| `webinsight.report.filename` | `web-insight-report.html` |
| `webinsight.report.title` | `JMeter Web Insight Report` |
| `webinsight.report.baseline` | Auto-detect `baseline.jtl` |
| `webinsight.report.annotations` | Auto-detect `report-annotations.json` |
| `webinsight.report.rampup` | `0` (ms, overlay on charts) |
| `webinsight.report.junit` | `false` |

**Metrics & thresholds:**

| Property | Default |
|----------|---------|
| `webinsight.apdex.threshold` | `500` (ms) |
| `webinsight.error.body.maxsize` | `16384` (bytes) |

**Comparison thresholds** (set to -1 to disable):

| Property | Default |
|----------|---------|
| `webinsight.compare.p95.threshold` | `10` (%) |
| `webinsight.compare.errorrate.threshold` | `2` (%) |
| `webinsight.compare.mean.threshold` | `-1` (disabled) |
| `webinsight.compare.p99.threshold` | `-1` (disabled) |
| `webinsight.compare.throughput.threshold` | `-1` (disabled) |

**Full CLI example:**
```bash
jmeter -n -t test.jmx \
  -Jwebinsight.report.output=/results/reports \
  -Jwebinsight.report.filename=report_${timestamp}.html \
  -Jwebinsight.report.title=CheckoutLoadTest \
  -Jwebinsight.apdex.threshold=300 \
  -Jwebinsight.report.junit=true \
  -Jwebinsight.compare.p95.threshold=15
```

**Path resolution** follows JMeter conventions: relative paths resolve from the working directory (where `jmeter` was launched from), not from the JMX file location. Directories are auto-created if they don't exist.

### Data Captured in Listener Mode
The listener captures additional data not available in JTL parsing:
- **Connect time** — TCP connection establishment time
- **Latency** — time to first byte
- **Request URL** — for error records
- **Response body** — for error records (truncated to 16KB by default, configurable via `webinsight.error.body.maxsize`)

### Output Files
| File | Description |
|------|-------------|
| `web-insight-report.html` | Self-contained interactive Web Insight Report |
| `web-insight-report.json` | CI/CD machine-readable summary |
| `web-insight-data.json` | External chart data (only in external data mode) |

---

## Technology

| Component | Technology |
|-----------|-----------|
| Plugin | Java (JMeter listener) |
| Percentiles | T-Digest (streaming) |
| Charts | Apache ECharts 5.5.0 |
| Templating | Apache Thymeleaf |
| Annotations | Jackson JSON |
| Output | Self-contained HTML |
