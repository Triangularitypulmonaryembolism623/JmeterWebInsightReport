# How to Run All Tests

The plugin has 420+ tests across 5 layers. This page describes how to run each layer.

## Prerequisites

- Java 17+ (`C:\Program Files\Java\jdk-17.0.5` or equivalent)
- Maven 3.8+
- Node.js 18+ (for Playwright)
- Docker (for container tests)
- Python 3 (for data integrity + large JTL generation)

## 1. Build & Deploy Plugin

Before running JMeter execution tests, build the plugin and deploy to local JMeter:

```bash
# Windows (WSL2)
cmd.exe /c "D:\IdeaProjects\JmeterWebInsightReport\scripts\build-and-deploy.bat"

# Or manually
mvn clean package -DskipTests
cp jmeter-web-insight-report/target/jmeter-web-insight-report-*.jar $JMETER_HOME/lib/ext/
```

Only `jmeter-web-insight-report-*.jar` needs deployment — it's a shaded fat JAR containing all dependencies.

## 2. Java Unit/Integration Tests (156 tests)

```bash
mvn test
```

Or by module:

```bash
# Core only (93 tests)
mvn test -pl jmeter-report-core

# Report module only (63 tests — install core first)
mvn install -N -q && mvn install -pl jmeter-report-core -DskipTests -q
mvn test -pl jmeter-web-insight-report

# Specific test class
mvn test -pl jmeter-report-core -Dtest=JtlParserTest
```

**Test classes:**

| Module | Class | Tests | Covers |
|--------|-------|-------|--------|
| core | JtlParserTest | 14 | JTL parsing, edge cases, transaction controllers |
| core | LiveStatisticsEngineTest | 22 | Aggregation, Apdex, body truncation, thread safety |
| core | ComparisonResultTest | 11 | Regression detection, custom thresholds |
| core | ComparisonThresholdsTest | 6 | Threshold defaults, isEnabled() |
| core | SlaEvaluatorTest | 17 | PASS/WARN/FAIL, per-sampler, convertThresholds |
| core | TDigestPercentileCalculatorTest | 11 | Percentiles, CDF for Apdex |
| core | FormatUtilTest | 8 | Duration, bytes, percentage formatting |
| core | SamplerFilterTest | 4 | Include/exclude patterns |
| report | AnnotationLoaderTest | 11 | JSON loading, validation, thresholds |
| report | ChartDataBuilderTest | 11 | Histogram, response codes, heatmap |
| report | CiCdJsonSerializerTest | 12 | JSON output, Apdex, SLA |
| report | ReportDataSerializerTest | 12 | Chart data serialization |
| report | ThymeleafReportRendererTest | 9 | HTML rendering |
| report | JunitXmlWriterTest | 8 | XML structure, SLA failures |

## 3. Playwright Browser Tests (212 tests)

```bash
cd test-plans/playwright
npm install          # First time only
npm test             # Run all tests (headless)
npx playwright test --ui    # Interactive UI mode
```

**Requirements:** JMeter execution tests must run first to generate the HTML reports that Playwright tests against.

**Spec files:**

| File | Tests | What it tests |
|------|-------|---------------|
| header-global | 13 | Dark mode, colorblind, badges, persistence |
| tab-navigation | 9 | Tab switching, Filters dropdown, row collapse |
| summary-tab | 16 | Metric cards, Apdex, chart, Top 5, Copy Summary |
| timeline-tab | 8 | 9 charts, hide/show, fullscreen |
| samplers-tab | 18 | Sort, search, columns, rows, detail expand, TC toggle |
| errors-tab | 15 | Error charts, tables, expand, sampler filter |
| filter-controls | 28 | Palette, line, metric, granularity, sampler filter, color picker |
| sla-apdex | 15 | SLA badges, Apdex display, annotations |
| sla-chart-lines | 16 | SLA markLine per metric, label text |
| csv-export | 11 | CSV download content, hidden data included |
| compare-tab | 15 | Compare charts, table, badges, metric/filter interaction |
| flow-investigate | 1 | E2E: slow sampler investigation |
| flow-customize | 2 | E2E: presentation setup + sampler filtering |
| ui-interactions | 18 | localStorage, echarts, TC hierarchy, notes, fullscreen |
| advanced-interactions | 10 | Drag-drop, zoom, legend, sync, Y-axis units |
| remaining-gaps | 17 | Compare thresholds, error JSON formatting, annotations |

**Screenshots & Traces:** Every test captures a screenshot (pass or fail). View the Playwright HTML report:

```bash
npx playwright show-report ../../test-results/playwright-report
```

## 4. JMeter Execution Tests (18 scenarios)

```bash
# Full suite
cmd.exe /c "D:\IdeaProjects\JmeterWebInsightReport\test-plans\scripts\run-all-tests.bat"

# Single test
jmeter -n -t test-plans/quick-smoke-test.jmx \
  -Jthreads_browse=5 -Jduration=30 -Jrampup=5 \
  -Jwebinsight.report.output=test-results/E01 \
  -Jwebinsight.report.filename=E01-report.html
```

**Test scenarios:**

| ID | Scenario | JMX | Duration |
|----|----------|-----|----------|
| E01 | Happy path smoke | quick-smoke-test | 30s |
| E02 | Full load (50 threads) | full-load-test | 120s |
| E03 | Error grouping | error-grouping-test | 30s |
| E04 | Single sampler | single-sampler-test | 30s |
| E05 | Many samplers (20) | many-samplers-test | 30s |
| E06 | Zero errors | zero-errors-test | 30s |
| E07 | 100% errors | all-errors-test | 30s |
| E08 | Long names | long-names-test | 20s |
| E09 | CLI properties | quick-smoke-test | 30s |
| E10 | SLA pass | sla-scenario-test + sla-pass.json | 30s |
| E11 | SLA fail | sla-scenario-test + sla-strict.json | 30s |
| E12 | Full annotations | quick-smoke-test + full-annotations.json | 30s |
| E13 | Markers only | quick-smoke-test + markers-only.json | 30s |
| E15 | Response codes | response-code-grouping-test | 20s |
| E16 | Docker | docker-test (in container) | 30s |
| E19 | Many samplers stress | many-samplers-test (20 threads) | 60s |
| E22 | Report stress | report-stress-test | 60s |
| E24 | Minimal data | single-sampler-test (1 thread, 5s) | 5s |

## 5. Configuration & Runtime Tests

```bash
# Config tests (defaults, paths, JUnit XML, Apdex, ramp-up)
cmd.exe /c "D:\IdeaProjects\JmeterWebInsightReport\test-plans\scripts\run-config-tests.bat"

# Error handling tests
bash test-plans/scripts/run-error-tests.sh
```

**Config tests (C01-C15):** Default output dir, default filename, default title, timestamp placeholder, relative paths, auto-create dirs, special chars, JUnit XML, custom Apdex, ramp-up marking, path separators.

**Error handling (N01-N07):** Zero samples, invalid annotations, missing baseline, long title, empty sampler name, report overwrite, empty title property.

## 6. `-g -o` CLI Pipeline Tests

Tests generating Web Insight Report from existing JTL files (no listener):

```bash
# Basic
jmeter -g results.jtl -o report-dir/

# With properties
jmeter -g results.jtl -o report-dir/ \
  -Jwebinsight.report.title="From JTL" \
  -Jwebinsight.report.annotations=/path/to/annotations.json
```

**Scenarios:** Basic generation, custom title/filename, annotations via -J, baseline via -J, JUnit XML, collision detection (existing report skipped).

## 7. Data Integrity Verification

Cross-validates plugin output against JMeter's native statistics:

```bash
# Generate native report from JTL
jmeter -g test-results/E01/results.jtl -o test-results/E01/native-report

# Compare
python3 test-plans/scripts/verify-data-integrity.py \
  test-results/E01/E01-report.json \
  test-results/E01/native-report/statistics.json
```

**Tolerances:** Sample count (exact), mean (±1ms), P95/P99 (±5%), throughput (±2%).

## 8. Docker Tests

```bash
# Build image (first time)
docker build -t jmeter-webinsight-test:5.6.3 -f docker/Dockerfile.jmeter docker/

# Run
cmd.exe /c "D:\IdeaProjects\JmeterWebInsightReport\test-plans\scripts\run-docker-tests.bat"
```

## Run Everything

To run all tests in sequence:

```bash
# 1. Build
mvn clean package -DskipTests

# 2. Deploy
cp jmeter-web-insight-report/target/jmeter-web-insight-report-*.jar $JMETER_HOME/lib/ext/

# 3. JUnit
mvn test

# 4. JMeter execution tests (generates reports for Playwright)
test-plans/scripts/run-all-tests.bat

# 5. Playwright (needs reports from step 4)
cd test-plans/playwright && npm test

# 6. Config + error handling tests
test-plans/scripts/run-config-tests.bat
bash test-plans/scripts/run-error-tests.sh
```

## Cleaning Test Data

```bash
rm -rf test-results/ test-plans/runs/ test-plans/playwright/test-results/
```

All test output is gitignored and regenerated by running the tests.
