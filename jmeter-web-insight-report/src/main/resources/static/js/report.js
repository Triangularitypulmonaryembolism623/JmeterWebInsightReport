/**
 * JMeter Web Insight Report — Interactive report UI
 * Uses Apache ECharts for chart rendering.
 *
 * TABLE OF CONTENTS
 * =================
 *  1. Globals & Settings .............. (line ~7)
 *  2. Chart Helpers ................... (showNoData, createChart, disposeChartIn, zipTimeSeries)
 *  3. Tab Navigation .................. (initTabs, switchTab)
 *  4. Sampler Filter Bar .............. (initFilterBar, color picker, palette/thickness/metric toggles)
 *  5. Chart Refresh & Total Row ....... (refreshAllCharts, updateTotalRow)
 *  6. Chart Init & Series Builder ..... (initCharts, buildPerSamplerSeries, ramp-up, dataZoom)
 *  7. Timeline Charts ................. (summary, responseTime, throughput, errorRate, bytes, connectLatency, responseCodes, percentileBar, scatter)
 *  8. Timeline Markers & SLA Lines .... (getTimelineMarkLines, getSlaMarkLines)
 *  9. Error Tab Charts ................ (errorByType, errorBySampler, errorTimeline)
 * 10. Chart Utilities ................. (tooltip, formatTime, toolbox, fullscreen, visibility, drag-drop, sync)
 * 11. Dark Mode ....................... (toggleDarkMode, getChartTheme)
 * 12. Sampler Table ................... (search, column toggle, sorting, column drag-drop)
 * 13. Transaction Hierarchy ........... (initTransactionHierarchy)
 * 14. Per-Sampler Detail View ......... (detail chart registry, slot management, 7 chart types)
 * 15. Error Detail Rows ............... (toggleErrorDetail)
 * 16. Filter Presets .................. (save/load/delete)
 * 17. Time Range Selector ............. (sync with chart dataZoom)
 * 18. Copy & Export ................... (copySummaryAsHtml)
 * 19. Column Drag-Drop ................ (reorder sampler table columns)
 * 20. Comparison Tab .................. (baseline overlay chart + diff table)
 * 21. Markdown & Notes ................ (renderMarkdown, notes editor, annotations)
 * 22. Row Hide/Show ................... (hideRow, showAllRows)
 * 23. Generic Table Utilities ......... (column toggle, filter, row hide for error tables)
 * 24. Bootstrap ....................... (DOMContentLoaded)
 */

var chartInstances = [];
var pendingChartInits = [];
var globalChartData = null;

/* Time display mode: 'clock' (wall-clock) or 'timer' (elapsed) */
var timeDisplayMode = 'clock';
var visibleTimeRangeMs = 0; /* updated on dataZoom events */

/* Virtual "Total" sampler — aggregate across all real samplers */
var TOTAL_SAMPLER_NAME = '\u03A3 Total';

/* Transaction Controller children visibility — false = hide children (like JMeter native) */
var showTransactionChildren = false;

/* Chart granularity in ms — 1000 = 1 second (raw), higher = aggregated */
var chartGranularity = 1000;

/* Set of child sampler names (populated on init from transactionHierarchy) */
var transactionChildNames = {};

function isTotalVisible() {
    return samplerVisibility[TOTAL_SAMPLER_NAME] === true;
}

function isTransactionChild(name) {
    return transactionChildNames[name] !== undefined;
}

function hasTransactionHierarchy() {
    return globalChartData && globalChartData.transactionHierarchy
        && Object.keys(globalChartData.transactionHierarchy).length > 0;
}

function getTotalColor() {
    if (typeof customColors !== 'undefined' && customColors[TOTAL_SAMPLER_NAME]) return customColors[TOTAL_SAMPLER_NAME];
    return document.body.classList.contains('dark-mode') ? '#e2e8f0' : '#1a1a1a';
}

/* Apdex CSS class helper: returns the badge class name for a given apdex score */
function getApdexClass(score) {
    if (score > 0.94) return 'apdex-excellent';
    if (score > 0.85) return 'apdex-good';
    if (score > 0.70) return 'apdex-fair';
    if (score > 0.50) return 'apdex-poor';
    return 'apdex-unacceptable';
}

/* Debounce utility: delays fn execution until delay ms after last call */
function debounce(fn, delay) {
    var timer = null;
    return function () {
        if (timer) clearTimeout(timer);
        timer = setTimeout(fn, delay);
    };
}

/* Debounced chart refresh to avoid N redraws when toggling multiple filters */
var debouncedRefreshAllCharts = debounce(function () {
    refreshAllCharts();
}, 150);

/* Color palettes */
var PALETTES = {
    'default': ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de',
                '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc', '#5c7bd9'],
    'okabe-ito': ['#E69F00', '#56B4E9', '#009E73', '#F0E442', '#0072B2',
                  '#D55E00', '#CC79A7', '#999999', '#000000', '#6699CC'],
    'high-contrast': ['#0000FF', '#FF0000', '#00AA00', '#FF8800', '#8800CC',
                      '#00CCCC', '#CC0066', '#666600', '#004400', '#880000']
};

var currentPalette = 'default';
var CHART_COLORS = PALETTES['default'];
var customColors = {}; // samplerName -> color override
var lineThickness = 2;

/* Track which samplers are visible */
var samplerVisibility = {};

/* Current selected metric for response time charts */
var currentMetric = 'responseTime';

/* localStorage keys */
var LS_PALETTE = 'wir_palette';
var LS_CUSTOM_COLORS = 'wir_customColors';
var LS_LINE_THICKNESS = 'wir_lineThickness';
var LS_PRESETS = 'wir_filterPresets';
var LS_COL_ORDER = 'wir_columnOrder';
var LS_FILTER_ROWS = 'wir_filterRowsState';

/* Column order for drag-and-drop reorder */
var columnOrder = null; // null means default order

function loadSettings() {
    try {
        var p = localStorage.getItem(LS_PALETTE);
        if (p && PALETTES[p]) {
            currentPalette = p;
            CHART_COLORS = PALETTES[p];
        }
        var cc = localStorage.getItem(LS_CUSTOM_COLORS);
        if (cc) customColors = JSON.parse(cc);
        var lt = localStorage.getItem(LS_LINE_THICKNESS);
        if (lt) lineThickness = parseInt(lt, 10) || 2;
        var co = localStorage.getItem(LS_COL_ORDER);
        if (co) columnOrder = JSON.parse(co);
        var tc = localStorage.getItem('wir_showTransactionChildren');
        if (tc === 'true') showTransactionChildren = true;
    } catch (e) { /* ignore */ }
}

function saveSettings() {
    try {
        localStorage.setItem(LS_PALETTE, currentPalette);
        localStorage.setItem(LS_CUSTOM_COLORS, JSON.stringify(customColors));
        localStorage.setItem(LS_LINE_THICKNESS, String(lineThickness));
        if (columnOrder) localStorage.setItem(LS_COL_ORDER, JSON.stringify(columnOrder));
    } catch (e) { /* ignore */ }
}

function getColorForSampler(name, idx) {
    if (customColors[name]) return customColors[name];
    return CHART_COLORS[idx % CHART_COLORS.length];
}

/* ============================================================
 *  Filter Rows Hide/Show
 * ============================================================ */

function toggleFilterMenu() {
    var menu = document.getElementById('filterBarMenu');
    if (menu) menu.classList.toggle('open');
}

function toggleFilterRowNum(rowNum, visible) {
    var row = document.getElementById('filterRow' + rowNum);
    if (!row) return;
    if (visible) row.classList.remove('hidden-row');
    else row.classList.add('hidden-row');
    saveFilterRowState();
}

function saveFilterRowState() {
    try {
        var state = {};
        for (var i = 1; i <= 3; i++) {
            var row = document.getElementById('filterRow' + i);
            if (row) state[i] = !row.classList.contains('hidden-row');
        }
        localStorage.setItem(LS_FILTER_ROWS, JSON.stringify(state));
    } catch (e) {}
}

function restoreFilterRowState() {
    try {
        var raw = localStorage.getItem(LS_FILTER_ROWS);
        if (!raw) return;
        var state = JSON.parse(raw);
        var menu = document.getElementById('filterBarMenu');
        var checkboxes = menu ? menu.querySelectorAll('input[type="checkbox"]') : [];
        for (var i = 1; i <= 3; i++) {
            if (state[i] === false) {
                var row = document.getElementById('filterRow' + i);
                if (row) row.classList.add('hidden-row');
                if (checkboxes[i - 1]) checkboxes[i - 1].checked = false;
            }
        }
    } catch (e) {}
}

/* ============================================================
 *  Chart Helpers — shared utilities for all chart init functions
 * ============================================================ */

/** Show a centered "no data" placeholder inside a chart container */
function showNoData(el, msg, small) {
    var fs = small ? 'font-size:0.8rem;' : '';
    el.innerHTML = '<div style="display:flex;align-items:center;justify-content:center;height:100%;color:#64748b;' + fs + '">' + (msg || 'No data') + '</div>';
}

/** Create an ECharts instance, register it for lifecycle management, and return it */
function createChart(el) {
    var chart = echarts.init(el, getChartTheme());
    chart.group = chartGroup;
    chartInstances.push(chart);
    return chart;
}

/** Dispose a chart in a DOM element and unregister from chartInstances */
function disposeChartIn(el) {
    var inst = echarts.getInstanceByDom(el);
    if (inst) {
        chartInstances = chartInstances.filter(function (c) {
            if (c === inst) { c.dispose(); return false; }
            return true;
        });
    }
}

/** Dispose the most recently created chart (used when chart has no data after init) */
function disposeLastChart(chart) {
    chart.dispose();
    chartInstances.pop();
}

/** Zip timestamps + values into [t, v] pairs, skipping nulls */
function zipTimeSeries(timestamps, values) {
    if (chartGranularity <= 1000) {
        var result = [];
        for (var i = 0; i < timestamps.length; i++) {
            if (values[i] != null) result.push([timestamps[i], values[i]]);
        }
        return result;
    }
    return aggregateTimeSeries(timestamps, values, chartGranularity);
}

/**
 * Aggregate time-series data into larger buckets by averaging values.
 */
function aggregateTimeSeries(timestamps, values, granularityMs) {
    if (!timestamps || timestamps.length === 0) return [];
    var result = [];
    var bucketStart = timestamps[0];
    var sum = 0, count = 0;

    for (var i = 0; i < timestamps.length; i++) {
        if (values[i] == null) continue;
        if (timestamps[i] - bucketStart >= granularityMs && count > 0) {
            result.push([bucketStart + Math.floor(granularityMs / 2), sum / count]);
            bucketStart = timestamps[i];
            sum = 0;
            count = 0;
        }
        sum += values[i];
        count++;
    }
    if (count > 0) {
        result.push([bucketStart + Math.floor(granularityMs / 2), sum / count]);
    }
    return result;
}

function changeGranularity(value) {
    chartGranularity = parseInt(value, 10) || 1000;
    debouncedRefreshAllCharts();
}

function autoDetectGranularity() {
    if (!globalChartData || !globalChartData.timestamps || globalChartData.timestamps.length < 2) return;
    var duration = globalChartData.timestamps[globalChartData.timestamps.length - 1] - globalChartData.timestamps[0];
    var granularity = 1000;
    if (duration > 7200000) granularity = 600000;      // > 2 hours → 10min
    else if (duration > 3600000) granularity = 300000;  // > 1 hour → 5min
    else if (duration > 1800000) granularity = 60000;   // > 30 min → 1min
    else if (duration > 900000) granularity = 30000;    // > 15 min → 30s
    else if (duration > 300000) granularity = 10000;    // > 5 min → 10s

    if (granularity > 1000) {
        chartGranularity = granularity;
        var select = document.getElementById('granularitySelect');
        if (select) select.value = String(granularity);
    }
}

/* ============================================================
 *  Tab Navigation
 * ============================================================ */

function initTabs() {
    var nav = document.getElementById('tabNav');
    if (!nav) return;

    var buttons = nav.querySelectorAll('.tab-btn');
    buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            switchTab(btn.getAttribute('data-tab'));
        });
    });
}

function switchTab(tabId) {
    var buttons = document.querySelectorAll('.tab-btn');
    var panels = document.querySelectorAll('.tab-panel');

    buttons.forEach(function (b) { b.classList.remove('active'); });
    panels.forEach(function (p) { p.classList.remove('active'); });

    var btn = document.querySelector('.tab-btn[data-tab="' + tabId + '"]');
    var panel = document.getElementById('tab-' + tabId);

    if (btn) btn.classList.add('active');
    if (panel) panel.classList.add('active');

    setTimeout(function () {
        chartInstances.forEach(function (c) { c.resize(); });
    }, 50);

    runPendingInits(tabId);
}

function runPendingInits(tabId) {
    var remaining = [];
    pendingChartInits.forEach(function (entry) {
        if (entry.tab === tabId) {
            entry.fn();
        } else {
            remaining.push(entry);
        }
    });
    pendingChartInits = remaining;
    connectCharts();
}

/* ============================================================
 *  Sampler Filter Bar
 * ============================================================ */

function initFilterBar(data) {
    var container = document.getElementById('samplerFilterList');
    if (!container || !data.samplers) return;

    container.innerHTML = '';
    var samplerNames = data.samplers.map(function (s) { return s.name; });

    /* Update sampler count */
    var countEl = document.getElementById('samplerCount');
    if (countEl) countEl.textContent = samplerNames.length;

    /* Auto-hide samplers from hiddenSamplers array */
    var autoHidden = (data.hiddenSamplers && Array.isArray(data.hiddenSamplers)) ? data.hiddenSamplers : [];

    /* Transaction hierarchy lookup */
    var hierarchy = data.transactionHierarchy || {};
    var childSet = {};
    transactionChildNames = {};
    for (var parentName in hierarchy) {
        var children = hierarchy[parentName];
        if (Array.isArray(children)) {
            children.forEach(function (c) { childSet[c] = parentName; transactionChildNames[c] = parentName; });
        }
    }

    /* ---- Virtual "Total" sampler entry ---- */
    if (samplerVisibility[TOTAL_SAMPLER_NAME] === undefined) {
        samplerVisibility[TOTAL_SAMPLER_NAME] = false;
    }
    (function () {
        var totalLabel = document.createElement('label');
        totalLabel.setAttribute('data-sampler-name', TOTAL_SAMPLER_NAME.toLowerCase());
        totalLabel.style.fontWeight = '700';
        if (samplerVisibility[TOTAL_SAMPLER_NAME]) totalLabel.classList.add('checked');

        var checkBox = document.createElement('span');
        checkBox.className = 'sampler-checkbox-box';
        checkBox.textContent = '\u2713';

        var dotWrapper = document.createElement('span');
        dotWrapper.style.position = 'relative';
        dotWrapper.style.display = 'inline-block';
        var dot = document.createElement('span');
        dot.className = 'sampler-color-dot';
        dot.style.background = getTotalColor();
        dotWrapper.appendChild(dot);

        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = samplerVisibility[TOTAL_SAMPLER_NAME];
        cb.value = TOTAL_SAMPLER_NAME;
        cb.addEventListener('change', function () {
            samplerVisibility[TOTAL_SAMPLER_NAME] = cb.checked;
            totalLabel.classList.toggle('checked', cb.checked);
            debouncedRefreshAllCharts();
        });

        var nameSpan = document.createElement('span');
        nameSpan.className = 'sampler-name';
        nameSpan.textContent = TOTAL_SAMPLER_NAME;

        totalLabel.appendChild(checkBox);
        totalLabel.appendChild(dotWrapper);
        totalLabel.appendChild(cb);
        totalLabel.appendChild(nameSpan);
        container.appendChild(totalLabel);

        var sep = document.createElement('span');
        sep.className = 'sampler-total-separator';
        container.appendChild(sep);
    })();

    samplerNames.forEach(function (name, idx) {
        if (samplerVisibility[name] === undefined) {
            samplerVisibility[name] = autoHidden.indexOf(name) < 0;
        }

        var label = document.createElement('label');
        label.setAttribute('data-sampler-name', name.toLowerCase());
        if (samplerVisibility[name]) label.classList.add('checked');

        /* Indent child samplers of transaction controllers */
        if (childSet[name]) {
            label.classList.add('transaction-child-filter');
            label.style.paddingLeft = '24px';
            if (!showTransactionChildren) label.style.display = 'none';
        }
        if (hierarchy[name]) {
            label.classList.add('transaction-parent-filter');
            label.style.fontWeight = '600';
        }

        /* Visible checkbox box */
        var checkBox = document.createElement('span');
        checkBox.className = 'sampler-checkbox-box';
        checkBox.textContent = '\u2713';

        var dotWrapper = document.createElement('span');
        dotWrapper.style.position = 'relative';
        dotWrapper.style.display = 'inline-block';

        var dot = document.createElement('span');
        dot.className = 'sampler-color-dot';
        dot.style.background = getColorForSampler(name, idx);
        dot.setAttribute('data-sampler', name);
        dot.setAttribute('data-idx', idx);
        dot.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            openColorPicker(dotWrapper, name, idx, dot);
        });

        dotWrapper.appendChild(dot);

        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = samplerVisibility[name];
        cb.value = name;
        cb.addEventListener('change', function () {
            samplerVisibility[name] = cb.checked;
            label.classList.toggle('checked', cb.checked);
            debouncedRefreshAllCharts();
        });

        var nameSpan = document.createElement('span');
        nameSpan.className = 'sampler-name';
        nameSpan.textContent = name;
        nameSpan.title = name;

        label.appendChild(cb);
        label.appendChild(checkBox);
        label.appendChild(dotWrapper);
        label.appendChild(nameSpan);
        container.appendChild(label);
    });

}

function openColorPicker(parent, samplerName, samplerIdx, dotEl) {
    // Close any open pickers
    document.querySelectorAll('.color-picker-popup.open').forEach(function (p) {
        p.classList.remove('open');
    });

    var existing = parent.querySelector('.color-picker-popup');
    if (existing) {
        existing.classList.add('open');
        return;
    }

    var popup = document.createElement('div');
    popup.className = 'color-picker-popup open';

    // Build swatches from all palettes
    var allColors = [];
    for (var key in PALETTES) {
        allColors = allColors.concat(PALETTES[key]);
    }
    // Add extra colors
    allColors = allColors.concat([
        '#000000', '#333333', '#666666', '#999999', '#CCCCCC',
        '#FF0000', '#FF4444', '#FF8800', '#FFCC00', '#00FF00',
        '#00CC66', '#0088FF', '#0000FF', '#8800FF', '#FF00FF'
    ]);
    // Deduplicate
    var seen = {};
    allColors = allColors.filter(function (c) {
        var k = c.toUpperCase();
        if (seen[k]) return false;
        seen[k] = true;
        return true;
    });

    allColors.forEach(function (color) {
        var swatch = document.createElement('div');
        swatch.className = 'color-swatch';
        swatch.style.background = color;
        if (getColorForSampler(samplerName, samplerIdx).toUpperCase() === color.toUpperCase()) {
            swatch.classList.add('selected');
        }
        swatch.addEventListener('click', function (e) {
            e.preventDefault();
            e.stopPropagation();
            applyCustomColor(samplerName, color, dotEl, popup);
        });
        popup.appendChild(swatch);
    });

    // Native color input for full custom
    var customRow = document.createElement('div');
    customRow.style.cssText = 'grid-column: 1 / -1; display: flex; align-items: center; gap: 6px; margin-top: 4px;';
    var colorInput = document.createElement('input');
    colorInput.type = 'color';
    colorInput.value = getColorForSampler(samplerName, samplerIdx);
    colorInput.style.cssText = 'width: 28px; height: 28px; border: none; cursor: pointer; padding: 0;';
    colorInput.addEventListener('input', function () {
        applyCustomColor(samplerName, colorInput.value, dotEl, popup);
    });
    var customLabel = document.createElement('span');
    customLabel.textContent = 'Custom...';
    customLabel.style.cssText = 'font-size: 0.75rem; color: #64748b;';
    customRow.appendChild(colorInput);
    customRow.appendChild(customLabel);
    popup.appendChild(customRow);

    parent.appendChild(popup);

    // Close on outside click
    setTimeout(function () {
        document.addEventListener('click', function closeHandler(e) {
            if (!popup.contains(e.target) && e.target !== dotEl) {
                popup.classList.remove('open');
                document.removeEventListener('click', closeHandler);
            }
        });
    }, 10);
}

function applyCustomColor(samplerName, color, dotEl, popup) {
    customColors[samplerName] = color;
    dotEl.style.background = color;
    popup.classList.remove('open');
    saveSettings();
    refreshAllCharts();
}

function initPaletteToggle() {
    var toggle = document.getElementById('paletteToggle');
    if (!toggle) return;

    var buttons = toggle.querySelectorAll('.filter-toggle-btn');

    // Restore saved palette state
    buttons.forEach(function (btn) {
        btn.classList.toggle('active', btn.getAttribute('data-palette') === currentPalette);
    });

    buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            buttons.forEach(function (b) { b.classList.remove('active'); });
            btn.classList.add('active');
            currentPalette = btn.getAttribute('data-palette');
            CHART_COLORS = PALETTES[currentPalette] || PALETTES['default'];
            customColors = {}; // Reset custom colors on palette change
            saveSettings();
            // Rebuild filter bar dots with new colors
            if (globalChartData) initFilterBar(globalChartData);
            refreshAllCharts();
        });
    });
}

function initLineThicknessToggle() {
    var toggle = document.getElementById('lineThicknessToggle');
    if (!toggle) return;

    var buttons = toggle.querySelectorAll('.filter-toggle-btn');

    // Restore saved thickness state
    buttons.forEach(function (btn) {
        btn.classList.toggle('active', parseInt(btn.getAttribute('data-thickness'), 10) === lineThickness);
    });

    buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            buttons.forEach(function (b) { b.classList.remove('active'); });
            btn.classList.add('active');
            lineThickness = parseInt(btn.getAttribute('data-thickness'), 10) || 2;
            saveSettings();
            refreshAllCharts();
        });
    });
}

function initPercentileToggle() {
    var toggle = document.getElementById('percentileToggle');
    if (!toggle) return;

    var buttons = toggle.querySelectorAll('.filter-toggle-btn');
    buttons.forEach(function (btn) {
        btn.addEventListener('click', function () {
            buttons.forEach(function (b) { b.classList.remove('active'); });
            btn.classList.add('active');
            currentMetric = btn.getAttribute('data-metric');
            refreshAllCharts();
        });
    });
}

function selectAllSamplers(selected) {
    for (var name in samplerVisibility) {
        samplerVisibility[name] = selected;
    }
    // Update checkboxes and label styles
    var container = document.getElementById('samplerFilterList');
    if (container) {
        var labels = container.querySelectorAll('label');
        labels.forEach(function (label) {
            /* Only affect visible labels (respects search filter) */
            if (label.style.display === 'none') return;
            var cb = label.querySelector('input[type="checkbox"]');
            if (cb) {
                cb.checked = selected;
                samplerVisibility[cb.value] = selected;
                label.classList.toggle('checked', selected);
            }
        });
    }
    refreshAllCharts();
}

function refreshAllCharts() {
    // Rebuild timeline charts with current visibility
    chartInstances.forEach(function (c) { try { c.dispose(); } catch (e) { /* already destroyed */ } });
    chartInstances = [];

    if (!globalChartData) return;

    initSummaryResponseTimeChart(globalChartData);

    // Only reinit timeline charts if the tab has been opened (no pending inits for them)
    var timelineStillPending = pendingChartInits.some(function (e) { return e.tab === 'timeline'; });
    if (!timelineStillPending) {
        initResponseTimeChart(globalChartData);
        initThroughputChart(globalChartData);
        initErrorRateChart(globalChartData);
        initBytesThroughputChart(globalChartData);
        initConnectLatencyChart(globalChartData);
        initResponseCodesChart(globalChartData);
        initPercentileBarChart(globalChartData);
        initScatterChart(globalChartData);
        initHeatmapChart(globalChartData);
    }

    var errorsStillPending = pendingChartInits.some(function (e) { return e.tab === 'errors'; });
    if (!errorsStillPending) {
        initErrorBreakdownCharts(globalChartData);
    }

    var compareStillPending = pendingChartInits.some(function (e) { return e.tab === 'compare'; });
    if (!compareStillPending && globalChartData.hasComparison) {
        initCompareRTChart(globalChartData);
        initCompareTPChart(globalChartData);
    }

    /* Re-init any open sampler detail panels */
    var openDetails = document.querySelectorAll('.sampler-detail-row');
    openDetails.forEach(function (detailRow) {
        var samplerName = detailRow.getAttribute('data-detail-for');
        if (!samplerName) return;
        var grid = detailRow.querySelector('.detail-chart-grid');
        if (!grid) return;
        /* Dispose and re-init all slots */
        grid.querySelectorAll('.detail-chart-slot').forEach(function (slot) {
            var key = slot.getAttribute('data-chart-key');
            var container = slot.querySelector('.detail-chart-container');
            if (!container) return;
            disposeChartIn(container);
            container.innerHTML = '';
            setTimeout(function () { initDetailChart(key, samplerName, container.id); }, 50);
        });
    });

    connectCharts();
    updateTotalRow();
}

function updateTotalRow() {
    var table = document.querySelector('#tab-samplers table');
    if (!table || !globalChartData) return;
    var tbody = table.tBodies[0];
    if (!tbody) return;

    /* Remove existing total row */
    var existing = tbody.querySelector('tr.total-sampler-row');
    if (existing) existing.remove();

    if (!isTotalVisible()) return;

    var samplers = globalChartData.samplers;
    if (!samplers || samplers.length === 0) return;

    var totalSamples = 0, totalErrors = 0, wMean = 0, wMedian = 0;
    var wP90 = 0, wP95 = 0, wP99 = 0, minRT = Infinity, maxRT = 0;
    var totalThroughput = 0, totalRecvKBs = 0, wApdex = 0;

    samplers.forEach(function (s) {
        /* Always skip transaction children in total — parents already aggregate them */
        if (isTransactionChild(s.name)) return;
        var count = s.sampleCount || s.count || 0;
        totalSamples += count;
        totalErrors += s.errorCount || Math.round((s.errorRate || 0) * count / 100) || 0;
        wMean += (s.mean || 0) * count;
        wMedian += (s.median || 0) * count;
        wP90 += (s.p90 || 0) * count;
        wP95 += (s.p95 || 0) * count;
        wP99 += (s.p99 || 0) * count;
        if ((s.min != null) && s.min < minRT) minRT = s.min;
        if ((s.max || 0) > maxRT) maxRT = s.max;
        totalThroughput += s.throughput || 0;
        totalRecvKBs += (s.receivedBytesPerSec || 0) / 1024.0;
        wApdex += (s.apdex || 0) * count;
    });

    var errorPct = totalSamples > 0 ? (totalErrors / totalSamples * 100) : 0;
    if (totalSamples > 0) { wMean /= totalSamples; wMedian /= totalSamples; wP90 /= totalSamples; wP95 /= totalSamples; wP99 /= totalSamples; wApdex /= totalSamples; }
    wApdex = Math.round(wApdex * 100) / 100;
    if (minRT === Infinity) minRT = 0;

    var fmt = function (v) { return v == null || isNaN(v) ? '0' : Number(v).toLocaleString(undefined, { maximumFractionDigits: 2 }); };

    var tr = document.createElement('tr');
    tr.className = 'total-sampler-row';

    var cells = [
        TOTAL_SAMPLER_NAME,
        fmt(totalSamples),
        fmt(totalErrors),
        errorPct.toFixed(2) + '%',
        fmt(wMean),
        fmt(wMedian),
        fmt(wP90),
        fmt(wP95),
        fmt(wP99),
        fmt(minRT),
        fmt(maxRT),
        fmt(totalThroughput) + '/s',
        fmt(totalRecvKBs),
        '__apdex__' + wApdex
    ];

    /* Match column count of existing rows (may include extra action columns) */
    var existingRow = tbody.querySelector('tr');
    var colCount = existingRow ? existingRow.cells.length : cells.length;
    while (cells.length < colCount) cells.push('');

    cells.forEach(function (val, idx) {
        var td = document.createElement('td');
        if (idx === 0) {
            var wrapper = document.createElement('div');
            wrapper.className = 'sampler-name-cell';
            var nameSpan = document.createElement('span');
            nameSpan.className = 'sampler-name-text';
            nameSpan.textContent = val;
            nameSpan.style.fontWeight = '700';
            wrapper.appendChild(nameSpan);
            td.appendChild(wrapper);
        } else if (typeof val === 'string' && val.indexOf('__apdex__') === 0) {
            var apdexVal = parseFloat(val.replace('__apdex__', ''));
            var badge = document.createElement('span');
            badge.className = 'apdex-badge ' + getApdexClass(apdexVal);
            badge.textContent = isNaN(apdexVal) ? '0' : apdexVal.toFixed(2);
            td.appendChild(badge);
        } else {
            td.textContent = val;
        }
        if (idx === 3 && errorPct > 0) td.style.color = '#ee6666';
        tr.appendChild(td);
    });

    tbody.insertBefore(tr, tbody.firstChild);
}

function getVisibleSamplerNames() {
    var names = [];
    for (var name in samplerVisibility) {
        if (name === TOTAL_SAMPLER_NAME) continue;
        if (!showTransactionChildren && isTransactionChild(name)) continue;
        if (samplerVisibility[name]) names.push(name);
    }
    return names;
}

/* ============================================================
 *  Chart Initialization
 * ============================================================ */

function initCharts(data) {
    if (!data || typeof echarts === 'undefined') return;

    globalChartData = data;

    autoDetectGranularity();
    initFilterBar(data);
    initSummaryResponseTimeChart(data);

    pendingChartInits.push({ tab: 'timeline', fn: function () { initResponseTimeChart(data); } });
    pendingChartInits.push({ tab: 'timeline', fn: function () { initThroughputChart(data); } });
    pendingChartInits.push({ tab: 'timeline', fn: function () { initErrorRateChart(data); } });
    pendingChartInits.push({ tab: 'timeline', fn: function () { initBytesThroughputChart(data); } });
    pendingChartInits.push({ tab: 'timeline', fn: function () { initConnectLatencyChart(data); } });
    pendingChartInits.push({ tab: 'timeline', fn: function () { initResponseCodesChart(data); } });
    pendingChartInits.push({ tab: 'timeline', fn: function () { initPercentileBarChart(data); } });
    pendingChartInits.push({ tab: 'timeline', fn: function () { initScatterChart(data); } });
    pendingChartInits.push({ tab: 'timeline', fn: function () { initHeatmapChart(data); } });

    // Error tab charts
    pendingChartInits.push({ tab: 'errors', fn: function () { initErrorBreakdownCharts(data); } });

    // Comparison tab
    initComparisonTab(data);

    // What Changed summary in Notes tab
    initWhatChanged(data);

    // Transaction controller hierarchy in sampler table
    initTransactionHierarchy(data);

    // Insert Total row
    updateTotalRow();

    window.addEventListener('resize', function () {
        chartInstances.forEach(function (c) { c.resize(); });
    });

    // Restore dark mode if saved
    if (localStorage.getItem('wir_darkMode') === 'true') {
        document.body.classList.add('dark-mode');
        var icon = document.getElementById('darkModeIcon');
        if (icon) icon.textContent = '\u2600';
    }

    // Restore colorblind mode if saved
    if (localStorage.getItem('wir_colorblindMode') === 'true') {
        document.body.classList.add('cb-mode');
        var cbBtn = document.getElementById('cbModeToggle');
        if (cbBtn) cbBtn.classList.add('cb-active');
        var cbIcon = document.getElementById('cbModeIcon');
        if (cbIcon) cbIcon.style.opacity = '1';
    }

    // Initialize time range display
    initTimeRangeSelector(data);

    // Populate filter presets from localStorage
    populatePresetSelect();

    // Render markdown on summary tab
    initSummaryMarkdown();
}

/* ============================================================
 *  Per-Sampler Series Builder
 * ============================================================ */

function buildPerSamplerSeries(data, metric, overrideMetric) {
    var perSampler = data.perSamplerSeries;
    if (!perSampler) return [];

    var effectiveMetric = overrideMetric || metric;
    var series = [];
    var visibleNames = getVisibleSamplerNames();
    var samplerNames = data.samplers ? data.samplers.map(function (s) { return s.name; }) : [];

    visibleNames.forEach(function (name) {
        if (!perSampler[name]) return;
        var values = perSampler[name][effectiveMetric];
        if (!values) return;

        var colorIdx = samplerNames.indexOf(name);
        if (colorIdx < 0) colorIdx = 0;

        var seriesColor = getColorForSampler(name, colorIdx);
        series.push({
            name: name,
            type: 'line',
            data: zipTimeSeries(data.timestamps, values),
            smooth: true,
            symbol: 'none',
            lineStyle: { width: lineThickness },
            itemStyle: { color: seriesColor }
        });
    });

    /* Append aggregate Total series when visible */
    if (isTotalVisible()) {
        var totalMetricMap = {
            'responseTime': data.meanResponseTimes,
            'throughput': data.throughputs,
            'errorRate': data.errorRates
        };
        var totalValues = totalMetricMap[effectiveMetric];
        /* For percentile metrics, compute weighted average across all samplers */
        if (!totalValues && perSampler) {
            var allNames = data.samplers ? data.samplers.map(function (s) { return s.name; }) : [];
            var computed = new Array(data.timestamps.length);
            for (var ti = 0; ti < data.timestamps.length; ti++) {
                var sum = 0, count = 0;
                allNames.forEach(function (n) {
                    if (perSampler[n] && perSampler[n][effectiveMetric] && perSampler[n][effectiveMetric][ti] != null) {
                        sum += perSampler[n][effectiveMetric][ti];
                        count++;
                    }
                });
                computed[ti] = count > 0 ? sum / count : null;
            }
            totalValues = computed;
        }
        if (totalValues) {
            series.push({
                name: TOTAL_SAMPLER_NAME,
                type: 'line',
                data: zipTimeSeries(data.timestamps, totalValues),
                smooth: true,
                symbol: 'none',
                lineStyle: { width: lineThickness + 1, type: 'dashed' },
                itemStyle: { color: getTotalColor() },
                z: 10
            });
        }
    }

    return series;
}

function getMetricLabel() {
    var labels = { responseTime: 'Mean', p50: 'P50', p90: 'P90', p95: 'P95', p99: 'P99', max: 'Max' };
    return labels[currentMetric] || 'Mean';
}

/* ============================================================
 *  Ramp-up Mark Area Helper
 * ============================================================ */

function getRampUpMarkArea(data) {
    if (!data || !data.rampUpEnd) return null;
    var startTs = data.timestamps && data.timestamps.length > 0 ? data.timestamps[0] : data.rampUpEnd - 60000;
    return {
        silent: true,
        data: [[
            {
                xAxis: startTs,
                itemStyle: {
                    color: 'rgba(150, 150, 150, 0.10)'
                },
                label: {
                    show: true,
                    position: 'insideTopLeft',
                    formatter: 'Ramp-up',
                    fontSize: 10,
                    color: '#94a3b8'
                }
            },
            {
                xAxis: data.rampUpEnd
            }
        ]]
    };
}

function injectRampUpMarkArea(series, data) {
    var markArea = getRampUpMarkArea(data);
    if (markArea && series.length > 0) {
        series[0].markArea = markArea;
    }
}

/* ============================================================
 *  Common dataZoom builder with Shift+drag pan support
 * ============================================================ */

function getDataZoom() {
    return [
        { type: 'inside', xAxisIndex: 0, moveOnMouseMove: true, moveOnMouseWheel: false },
        { type: 'slider', xAxisIndex: 0, bottom: 38, height: 18 }
    ];
}

function truncateLegendName(name) {
    return name.length > 30 ? name.substring(0, 27) + '...' : name;
}

/**
 * Shared setup for timeline charts: validate data, handle empty state,
 * create chart instance. Returns { chart, chartDom } or null if skipped.
 */
function initTimelineChart(domId, data) {
    var chartDom = document.getElementById(domId);
    if (!chartDom || !data.timestamps || data.timestamps.length === 0) return null;
    return { chartDom: chartDom, data: data };
}

/** Check if no samplers are selected; if so, show placeholder and return true */
function handleEmptySamplers(ctx, series) {
    if (series.length === 0 && ctx.data.perSamplerSeries && getVisibleSamplerNames().length === 0 && !isTotalVisible()) {
        ctx.chartDom.style.height = '80px';
        showNoData(ctx.chartDom, 'No samplers selected.');
        return true;
    }
    return false;
}

/** Create chart instance in a timeline container (resets height to 400px) */
function activateTimelineChart(ctx) {
    ctx.chartDom.style.height = '400px';
    ctx.chartDom.innerHTML = '';
    return createChart(ctx.chartDom);
}

/** Apply standard timeline chart options and bind time range sync */
function finalizeTimelineChart(chart, opts) {
    var defaults = {
        toolbox: getToolbox(),
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: chartTooltipFormatter },
        legend: { data: (opts.series || []).map(function (s) { return s.name; }), bottom: 10, type: 'scroll', formatter: truncateLegendName, tooltip: { show: true } },
        grid: { left: 60, right: 30, top: 40, bottom: 90 },
        xAxis: { type: 'time', axisLabel: { formatter: formatTimeAxis }, min: getTestStart() || undefined, max: getTestStart() ? getTestStart() + getTestDurationMs() : undefined },
        yAxis: { type: 'value', min: 0 },
        dataZoom: getDataZoom()
    };
    /* Merge: opts overrides defaults */
    for (var key in opts) { defaults[key] = opts[key]; }
    chart.setOption(defaults);
    bindDataZoomToTimeRange(chart);
}

/* ============================================================
 *  Summary Response Time Chart
 * ============================================================ */

function initSummaryResponseTimeChart(data) {
    var chartDom = document.getElementById('summary-response-time-chart');
    if (!chartDom || !data.timestamps || data.timestamps.length === 0) return;

    var chart = createChart(chartDom);

    var series = buildPerSamplerSeries(data, 'responseTime', currentMetric);

    if (series.length === 0) {
        series.push({
            name: getMetricLabel() + ' Response Time',
            type: 'line',
            data: zipTimeSeries(data.timestamps, data.meanResponseTimes),
            smooth: true,
            symbol: 'none',
            lineStyle: { width: 2 },
            itemStyle: { color: '#5470c6' },
            areaStyle: { color: 'rgba(84, 112, 198, 0.1)' }
        });
    }

    var hasThreads = data.activeThreads && data.activeThreads.some(function (v) { return v > 0; });
    if (hasThreads) {
        series.push({
            name: 'Active Threads',
            type: 'line',
            yAxisIndex: 1,
            data: zipTimeSeries(data.timestamps, data.activeThreads),
            smooth: true,
            symbol: 'none',
            lineStyle: { width: 1, type: 'dashed', color: '#91cc75' },
            itemStyle: { color: '#91cc75' }
        });
    }

    var rtSeries = series.filter(function (s) { return s.name !== 'Active Threads'; });
    var maxRT = getMaxFromSeries(rtSeries);
    var yAxes = [responseTimeYAxis(maxRT, 'Response Time')];
    if (hasThreads) {
        yAxes.push({ type: 'value', name: 'Threads', min: 0, splitLine: { show: false } });
    }

    injectMarkLines(series, data, 'responseTime');
    injectRampUpMarkArea(series, data);

    chart.setOption({
        toolbox: getToolbox(),
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'cross' },
            formatter: chartTooltipFormatter
        },
        legend: { data: series.map(function (s) { return s.name; }), bottom: 10, type: 'scroll', formatter: truncateLegendName, tooltip: { show: true } },
        grid: { left: 60, right: hasThreads ? 80 : 30, top: 40, bottom: 90 },
        xAxis: { type: 'time', axisLabel: { formatter: formatTimeAxis }, min: getTestStart() || undefined, max: getTestStart() ? getTestStart() + getTestDurationMs() : undefined },
        yAxis: yAxes,
        series: series,
        dataZoom: getDataZoom()
    });

    bindDataZoomToTimeRange(chart);
}

/* ============================================================
 *  Response Time Chart (Timeline tab)
 * ============================================================ */

function initResponseTimeChart(data) {
    var ctx = initTimelineChart('response-time-chart', data);
    if (!ctx) return;

    var series = buildPerSamplerSeries(data, 'responseTime', currentMetric);
    if (handleEmptySamplers(ctx, series)) return;
    var chart = activateTimelineChart(ctx);

    if (series.length === 0) {
        series.push({ name: getMetricLabel() + ' Response Time', type: 'line', data: zipTimeSeries(data.timestamps, data.meanResponseTimes), smooth: true, symbol: 'none', lineStyle: { width: 2 }, itemStyle: { color: '#5470c6' }, areaStyle: { color: 'rgba(84, 112, 198, 0.1)' } });
    }

    var hasThreads = data.activeThreads && data.activeThreads.some(function (v) { return v > 0; });
    if (hasThreads) {
        series.push({
            name: 'Active Threads',
            type: 'line',
            yAxisIndex: 1,
            data: zipTimeSeries(data.timestamps, data.activeThreads),
            smooth: true,
            symbol: 'none',
            lineStyle: { width: 1, type: 'dashed', color: '#91cc75' },
            itemStyle: { color: '#91cc75' }
        });
    }

    var rtSeries = series.filter(function (s) { return s.name !== 'Active Threads'; });
    var maxRT = getMaxFromSeries(rtSeries);
    var yAxes = [responseTimeYAxis(maxRT, 'Response Time')];
    if (hasThreads) {
        yAxes.push({ type: 'value', name: 'Threads', min: 0, splitLine: { show: false } });
    }

    injectMarkLines(series, data, 'responseTime');
    injectRampUpMarkArea(series, data);

    finalizeTimelineChart(chart, {
        grid: { left: 60, right: hasThreads ? 80 : 30, top: 40, bottom: 90 },
        yAxis: yAxes,
        series: series
    });
}

/* ============================================================
 *  Response Time Heatmap
 * ============================================================ */

function initHeatmapChart(data) {
    var chartDom = document.getElementById('heatmap-chart');
    if (!chartDom || !data.heatmapData) return;

    disposeChartIn(chartDom);
    chartDom.style.height = '350px';
    chartDom.innerHTML = '';
    var chart = createChart(chartDom);

    var hm = data.heatmapData;
    var bins = hm.bins || [];
    var timestamps = hm.timestamps || [];
    var perSampler = hm.perSampler || {};

    // Aggregate visible samplers + build per-sampler breakdown for tooltip
    var visibleNames = getVisibleSamplerNames();
    var numBins = bins.length;
    var heatData = [];
    var maxVal = 0;
    var cellBreakdown = {}; // key: "ti,b" → { samplerName: count, ... }

    for (var ti = 0; ti < timestamps.length; ti++) {
        var aggregated = new Array(numBins);
        for (var b = 0; b < numBins; b++) aggregated[b] = 0;

        visibleNames.forEach(function (name) {
            var samplerBins = perSampler[name];
            if (samplerBins && samplerBins[ti]) {
                for (var b = 0; b < numBins && b < samplerBins[ti].length; b++) {
                    if (samplerBins[ti][b] > 0) {
                        aggregated[b] += samplerBins[ti][b];
                        var key = ti + ',' + b;
                        if (!cellBreakdown[key]) cellBreakdown[key] = {};
                        cellBreakdown[key][name] = samplerBins[ti][b];
                    }
                }
            }
        });

        for (var b = 0; b < numBins; b++) {
            if (aggregated[b] > 0) {
                heatData.push([ti, b, aggregated[b]]);
                if (aggregated[b] > maxVal) maxVal = aggregated[b];
            }
        }
    }

    if (heatData.length === 0) {
        disposeLastChart(chart);
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No heatmap data for selected samplers.');
        return;
    }

    // Build time labels for x-axis
    var timeLabels = timestamps.map(function (t) {
        return formatTimeLabel(t);
    });

    chart.setOption({
        toolbox: getToolbox(),
        tooltip: {
            position: 'top',
            formatter: function (params) {
                var tIdx = params.value[0];
                var bIdx = params.value[1];
                var count = params.value[2];
                var time = tIdx < timeLabels.length ? timeLabels[tIdx] : '';
                var bin = bIdx < bins.length ? bins[bIdx] : '';
                var html = time + '<br/><strong>' + bin + ' ms</strong>: ' + count + ' samples';
                var key = tIdx + ',' + bIdx;
                var bd = cellBreakdown[key];
                if (bd && Object.keys(bd).length > 1) {
                    html += '<br/><span style="font-size:0.8em;color:#888;">──────</span>';
                    for (var name in bd) {
                        var short = name.length > 35 ? name.substring(0, 32) + '...' : name;
                        html += '<br/><span style="font-size:0.85em;">' + short + ': ' + bd[name] + '</span>';
                    }
                }
                return html;
            }
        },
        grid: { left: 80, right: 30, top: 20, bottom: 80 },
        xAxis: {
            type: 'category',
            data: timeLabels,
            splitArea: { show: false },
            splitLine: { show: false },
            axisLabel: {
                interval: Math.max(1, Math.floor(timeLabels.length / 15)),
                rotate: 30,
                fontSize: 10
            }
        },
        yAxis: {
            type: 'category',
            data: bins,
            name: 'Response Time',
            nameLocation: 'middle',
            nameGap: 60,
            splitArea: { show: false },
            splitLine: { show: false }
        },
        visualMap: {
            min: 1,
            max: maxVal || 1,
            calculable: true,
            orient: 'horizontal',
            left: 'center',
            bottom: 0,
            inRange: {
                color: document.body.classList.contains('dark-mode')
                    ? ['#1e40af', '#3b82f6', '#f59e0b', '#ef4444']
                    : ['#93c5fd', '#3b82f6', '#f59e0b', '#ef4444']
            }
        },
        series: [{
            name: 'Response Time Distribution',
            type: 'heatmap',
            data: heatData,
            emphasis: {
                itemStyle: { shadowBlur: 10, shadowColor: 'rgba(0, 0, 0, 0.5)' }
            }
        }],
        dataZoom: [
            { type: 'inside', xAxisIndex: 0, moveOnMouseMove: true, moveOnMouseWheel: false },
            { type: 'slider', xAxisIndex: 0, bottom: 30, height: 18 }
        ]
    });

    bindDataZoomToTimeRange(chart);
}

function formatTimeLabel(ts) {
    var d = new Date(ts);
    if (timeDisplayMode === 'timer' && globalChartData && globalChartData.timestamps && globalChartData.timestamps.length > 0) {
        var elapsed = ts - globalChartData.timestamps[0];
        var s = Math.floor(elapsed / 1000);
        var m = Math.floor(s / 60);
        s = s % 60;
        return m + ':' + (s < 10 ? '0' : '') + s;
    }
    var h = d.getHours(), min = d.getMinutes(), sec = d.getSeconds();
    return (h < 10 ? '0' : '') + h + ':' + (min < 10 ? '0' : '') + min + ':' + (sec < 10 ? '0' : '') + sec;
}

/* ============================================================
 *  Throughput Chart
 * ============================================================ */

function initThroughputChart(data) {
    var ctx = initTimelineChart('throughput-chart', data);
    if (!ctx) return;

    var series = buildPerSamplerSeries(data, 'throughput');
    if (handleEmptySamplers(ctx, series)) return;
    var chart = activateTimelineChart(ctx);

    if (series.length === 0) {
        series.push({ name: 'Throughput', type: 'line', data: zipTimeSeries(data.timestamps, data.throughputs), smooth: true, symbol: 'none', lineStyle: { width: 2 }, itemStyle: { color: '#91cc75' }, areaStyle: { color: 'rgba(145, 204, 117, 0.15)' } });
    }

    injectMarkLines(series, data, 'throughput');
    injectRampUpMarkArea(series, data);

    finalizeTimelineChart(chart, {
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: function (params) {
            var str = formatTime(new Date(params[0].value[0])) + '<br/>';
            params.forEach(function (p) { str += p.marker + ' ' + p.seriesName + ': <b>' + p.value[1].toFixed(1) + '/s</b><br/>'; });
            return str;
        }},
        yAxis: { type: 'value', name: 'Requests/sec', min: 0 },
        series: series
    });
}

/* ============================================================
 *  Error Rate Chart
 * ============================================================ */

function initErrorRateChart(data) {
    var ctx = initTimelineChart('error-rate-chart', data);
    if (!ctx) return;

    var hasErrors = data.errorRates && data.errorRates.some(function (v) { return v > 0; });
    if (!hasErrors) { ctx.chartDom.style.height = '80px'; showNoData(ctx.chartDom, 'No errors recorded during the test.'); return; }

    var series = buildPerSamplerSeries(data, 'errorRate');
    if (handleEmptySamplers(ctx, series)) return;
    var chart = activateTimelineChart(ctx);

    if (series.length === 0) {
        series.push({ name: 'Error Rate', type: 'line', data: zipTimeSeries(data.timestamps, data.errorRates), smooth: true, symbol: 'none', lineStyle: { width: 2 }, itemStyle: { color: '#ee6666' }, areaStyle: { color: 'rgba(238, 102, 102, 0.15)' } });
    }

    injectMarkLines(series, data, 'errorRate');
    injectRampUpMarkArea(series, data);

    finalizeTimelineChart(chart, {
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: function (params) {
            var str = formatTime(new Date(params[0].value[0])) + '<br/>';
            params.forEach(function (p) { str += p.marker + ' ' + p.seriesName + ': <b>' + p.value[1].toFixed(2) + '%</b><br/>'; });
            return str;
        }},
        yAxis: { type: 'value', name: 'Error %', min: 0, max: 100 },
        series: series
    });
}

/* ============================================================
 *  Bytes Throughput Over Time
 * ============================================================ */

function initBytesThroughputChart(data) {
    var ctx = initTimelineChart('bytes-throughput-chart', data);
    if (!ctx) return;

    var perSampler = data.perSamplerSeries;
    if (!perSampler) {
        ctx.chartDom.style.height = '80px';
        showNoData(ctx.chartDom, 'No bytes throughput data available.');
        return;
    }

    var chart = activateTimelineChart(ctx);
    var chartDom = ctx.chartDom;

    var series = [];
    var visibleNames = getVisibleSamplerNames();
    var samplerNames = data.samplers ? data.samplers.map(function (s) { return s.name; }) : [];

    visibleNames.forEach(function (name) {
        if (!perSampler[name]) return;
        var colorIdx = samplerNames.indexOf(name);
        if (colorIdx < 0) colorIdx = 0;
        var seriesColor = getColorForSampler(name, colorIdx);

        var recvData = perSampler[name].receivedBytes;
        if (recvData) {
            series.push({
                name: name + ' (Recv)',
                type: 'line',
                data: zipTimeSeries(data.timestamps, recvData),
                smooth: true,
                symbol: 'none',
                lineStyle: { width: lineThickness },
                itemStyle: { color: seriesColor }
            });
        }

        var sentData = perSampler[name].sentBytes;
        if (sentData) {
            series.push({
                name: name + ' (Sent)',
                type: 'line',
                data: zipTimeSeries(data.timestamps, sentData),
                smooth: true,
                symbol: 'none',
                lineStyle: { width: lineThickness, type: 'dashed' },
                itemStyle: { color: seriesColor }
            });
        }
    });

    /* Total aggregate bytes series */
    if (isTotalVisible()) {
        var allNames = data.samplers ? data.samplers.map(function (s) { return s.name; }) : [];
        var totalRecv = new Array(data.timestamps.length).fill(0);
        var totalSent = new Array(data.timestamps.length).fill(0);
        var hasRecv = false, hasSent = false;
        allNames.forEach(function (n) {
            if (!perSampler[n]) return;
            if (perSampler[n].receivedBytes) { hasRecv = true; perSampler[n].receivedBytes.forEach(function (v, i) { if (v != null) totalRecv[i] += v; }); }
            if (perSampler[n].sentBytes) { hasSent = true; perSampler[n].sentBytes.forEach(function (v, i) { if (v != null) totalSent[i] += v; }); }
        });
        if (hasRecv) series.push({ name: TOTAL_SAMPLER_NAME + ' (Recv)', type: 'line', data: zipTimeSeries(data.timestamps, totalRecv), smooth: true, symbol: 'none', lineStyle: { width: lineThickness + 1, type: 'dashed' }, itemStyle: { color: getTotalColor() }, z: 10 });
        if (hasSent) series.push({ name: TOTAL_SAMPLER_NAME + ' (Sent)', type: 'line', data: zipTimeSeries(data.timestamps, totalSent), smooth: true, symbol: 'none', lineStyle: { width: lineThickness + 1, type: 'dashed', opacity: 0.6 }, itemStyle: { color: getTotalColor() }, z: 10 });
    }

    if (series.length === 0) {
        disposeLastChart(chart);
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No bytes throughput data for selected samplers.');
        return;
    }

    injectRampUpMarkArea(series, data);

    var bytesFormatter = function (v) {
        if (v >= 1048576) return (v / 1048576).toFixed(1) + ' MB';
        if (v >= 1024) return (v / 1024).toFixed(1) + ' KB';
        return v + ' B';
    };

    finalizeTimelineChart(chart, {
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: function (params) {
            var str = formatTime(new Date(params[0].value[0])) + '<br/>';
            params.forEach(function (p) {
                var val = p.value[1], unit = 'B/s';
                if (val > 1048576) { val /= 1048576; unit = 'MB/s'; }
                else if (val > 1024) { val /= 1024; unit = 'KB/s'; }
                str += p.marker + ' ' + p.seriesName + ': <b>' + val.toFixed(1) + ' ' + unit + '</b><br/>';
            });
            return str;
        }},
        yAxis: { type: 'value', name: 'Bytes/sec', min: 0, axisLabel: { formatter: bytesFormatter } },
        series: series
    });
}

/* ============================================================
 *  Connect Time vs Latency vs Processing (Stacked Area)
 * ============================================================ */

function initConnectLatencyChart(data) {
    var ctx = initTimelineChart('connect-latency-chart', data);
    if (!ctx) return;

    var perSampler = data.perSamplerSeries;
    if (!perSampler) {
        ctx.chartDom.style.height = '80px';
        showNoData(ctx.chartDom, 'No connect/latency data available.');
        return;
    }

    var chart = activateTimelineChart(ctx);
    var chartDom = ctx.chartDom;

    var series = [];
    var visibleNames = getVisibleSamplerNames();
    /* When Total is checked, aggregate across ALL samplers */
    if (isTotalVisible()) visibleNames = data.samplers ? data.samplers.map(function (s) { return s.name; }) : [];
    var samplerNames = data.samplers ? data.samplers.map(function (s) { return s.name; }) : [];

    /* For a cleaner view, aggregate across visible samplers */
    var connectAgg = new Array(data.timestamps.length);
    var latencyAgg = new Array(data.timestamps.length);
    var processingAgg = new Array(data.timestamps.length);
    var countAgg = new Array(data.timestamps.length);
    for (var ti = 0; ti < data.timestamps.length; ti++) {
        connectAgg[ti] = 0; latencyAgg[ti] = 0; processingAgg[ti] = 0; countAgg[ti] = 0;
    }

    var hasData = false;
    visibleNames.forEach(function (name) {
        if (!perSampler[name]) return;
        var ct = perSampler[name].connectTime;
        var lat = perSampler[name].latency;
        var elapsed = perSampler[name].responseTime || perSampler[name].elapsed;
        if (!ct && !lat) return;
        hasData = true;

        for (var i = 0; i < data.timestamps.length; i++) {
            var c = (ct && ct[i] != null) ? ct[i] : 0;
            var l = (lat && lat[i] != null) ? lat[i] : 0;
            var e = (elapsed && elapsed[i] != null) ? elapsed[i] : 0;
            var latMinusConnect = Math.max(0, l - c);
            var processing = Math.max(0, e - l);
            connectAgg[i] += c;
            latencyAgg[i] += latMinusConnect;
            processingAgg[i] += processing;
            countAgg[i]++;
        }
    });

    if (!hasData) {
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No connect/latency breakdown data for selected samplers.');
        return;
    }

    /* Average across samplers */
    for (var j = 0; j < data.timestamps.length; j++) {
        var cnt = countAgg[j] || 1;
        connectAgg[j] = connectAgg[j] / cnt;
        latencyAgg[j] = latencyAgg[j] / cnt;
        processingAgg[j] = processingAgg[j] / cnt;
    }

    series.push({
        name: 'Connect Time',
        type: 'line',
        stack: 'breakdown',
        areaStyle: { opacity: 0.6 },
        data: zipTimeSeries(data.timestamps, connectAgg),
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1 },
        itemStyle: { color: '#5470c6' }
    });
    series.push({
        name: 'Latency (excl. Connect)',
        type: 'line',
        stack: 'breakdown',
        areaStyle: { opacity: 0.6 },
        data: zipTimeSeries(data.timestamps, latencyAgg),
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1 },
        itemStyle: { color: '#fac858' }
    });
    series.push({
        name: 'Processing (Response)',
        type: 'line',
        stack: 'breakdown',
        areaStyle: { opacity: 0.6 },
        data: zipTimeSeries(data.timestamps, processingAgg),
        smooth: true,
        symbol: 'none',
        lineStyle: { width: 1 },
        itemStyle: { color: '#91cc75' }
    });

    injectRampUpMarkArea(series, data);

    finalizeTimelineChart(chart, {
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: function (params) {
            var str = formatTime(new Date(params[0].value[0])) + '<br/>';
            params.forEach(function (p) { str += p.marker + ' ' + p.seriesName + ': <b>' + formatResponseTime(p.value[1]) + '</b><br/>'; });
            return str;
        }},
        yAxis: responseTimeYAxis(getMaxFromSeries(series), 'Time'),
        series: series
    });
}

/* ============================================================
 *  Response Codes Over Time (Stacked Bar)
 * ============================================================ */

function initResponseCodesChart(data) {
    var chartDom = document.getElementById('response-codes-chart');
    if (!chartDom) return;

    if (data.perSamplerSeries && getVisibleSamplerNames().length === 0 && !isTotalVisible()) {
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No samplers selected.');
        return;
    }

    /* Build codes data from error records filtered by visible samplers */
    var filteredRecords = getFilteredErrorRecords(data);
    if (!filteredRecords || filteredRecords.length === 0) {
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No response code data available.');
        return;
    }

    /* Group by 1-second bucket and response code */
    var bucketCodes = {};
    var allCodes = {};
    filteredRecords.forEach(function (er) {
        var bucketKey = Math.floor(er.timestamp / 1000) * 1000;
        if (!bucketCodes[bucketKey]) bucketCodes[bucketKey] = {};
        bucketCodes[bucketKey][er.code] = (bucketCodes[bucketKey][er.code] || 0) + 1;
        allCodes[er.code] = true;
    });

    var timestamps = Object.keys(bucketCodes).map(Number).sort(function (a, b) { return a - b; });
    var codes = Object.keys(allCodes);

    if (timestamps.length === 0 || codes.length === 0) {
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No response code data available.');
        return;
    }

    chartDom.style.height = '400px';
    chartDom.innerHTML = '';
    var chart = createChart(chartDom);

    var series = [];
    codes.forEach(function (code, idx) {
        var color = ERROR_CODE_COLORS[code] || CHART_COLORS[idx % CHART_COLORS.length];
        if (code.charAt(0) === '2') color = '#91cc75';
        if (code.charAt(0) === '3') color = '#73c0de';

        series.push({
            name: code,
            type: 'bar',
            stack: 'codes',
            data: timestamps.map(function (t) {
                return [t, (bucketCodes[t] && bucketCodes[t][code]) || 0];
            }),
            itemStyle: { color: color },
            barMaxWidth: 20
        });
    });

    injectRampUpMarkArea(series, data);

    finalizeTimelineChart(chart, {
        tooltip: { trigger: 'axis', axisPointer: { type: 'shadow' } },
        legend: { data: codes, bottom: 10, type: 'scroll' },
        yAxis: { type: 'value', name: 'Count', min: 0 },
        series: series
    });
}

/* ============================================================
 *  Percentile Bar Comparison
 * ============================================================ */

function initPercentileBarChart(data) {
    var chartDom = document.getElementById('percentile-bar-chart');
    if (!chartDom || !data.samplers || data.samplers.length === 0) return;

    var visibleNames = getVisibleSamplerNames();
    var visibleSamplers = data.samplers.filter(function (s) {
        return visibleNames.indexOf(s.name) >= 0;
    });

    if (visibleSamplers.length === 0 && !isTotalVisible()) {
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No samplers selected.');
        return;
    }

    chartDom.style.height = '400px';
    chartDom.innerHTML = '';
    var chart = createChart(chartDom);

    var names = visibleSamplers.map(function (s) { return s.name; });

    /* Add Total bar group */
    if (isTotalVisible() && data.samplers && data.samplers.length > 0) {
        var allSamplers = data.samplers;
        var totalCount = allSamplers.reduce(function (s, x) { return s + (x.sampleCount || x.count || 0); }, 0);
        var totalStats = { name: TOTAL_SAMPLER_NAME };
        ['median', 'p90', 'p95', 'p99'].forEach(function (field) {
            var weightedSum = allSamplers.reduce(function (s, x) { return s + ((x[field] || 0) * (x.sampleCount || x.count || 0)); }, 0);
            totalStats[field] = totalCount > 0 ? weightedSum / totalCount : 0;
        });
        totalStats.max = allSamplers.reduce(function (m, x) { return Math.max(m, x.max || 0); }, 0);
        names.push(TOTAL_SAMPLER_NAME);
        visibleSamplers.push(totalStats);
    }

    var metricDefs = [
        { key: 'p50', label: 'P50', field: 'median', color: '#5470c6' },
        { key: 'p90', label: 'P90', field: 'p90', color: '#91cc75' },
        { key: 'p95', label: 'P95', field: 'p95', color: '#fac858' },
        { key: 'p99', label: 'P99', field: 'p99', color: '#ee6666' },
        { key: 'max', label: 'Max', field: 'max', color: '#73c0de' }
    ];

    var series = metricDefs.map(function (m) {
        return {
            name: m.label,
            type: 'bar',
            data: visibleSamplers.map(function (s) { return s[m.field] || 0; }),
            itemStyle: { color: m.color },
            barMaxWidth: 30
        };
    });

    chart.setOption({
        toolbox: getToolbox(),
        tooltip: {
            trigger: 'axis',
            axisPointer: { type: 'shadow' },
            formatter: function (params) {
                var str = params[0].axisValueLabel + '<br/>';
                params.forEach(function (p) {
                    str += p.marker + ' ' + p.seriesName + ': <b>' + formatResponseTime(p.value) + '</b><br/>';
                });
                return str;
            }
        },
        legend: { data: metricDefs.map(function (m) { return m.label; }), bottom: 0, type: 'scroll' },
        grid: { left: 10, right: 20, top: 30, bottom: 80, containLabel: true },
        xAxis: { type: 'category', data: names, axisLabel: { rotate: 30, fontSize: 10, formatter: function(value) { return value.length > 25 ? value.substring(0, 22) + '...' : value; } } },
        yAxis: responseTimeYAxis(getMaxFromSeries(series), 'Response Time'),
        dataZoom: [
            { type: 'inside', xAxisIndex: 0 },
            { type: 'slider', xAxisIndex: 0, bottom: 28, height: 18 }
        ],
        series: series
    });
}

/* ============================================================
 *  Response Time Scatter Plot
 * ============================================================ */

function initScatterChart(data) {
    var ctx = initTimelineChart('scatter-chart', data);
    if (!ctx) return;

    var perSampler = data.perSamplerSeries;
    if (!perSampler) {
        ctx.chartDom.style.height = '80px';
        showNoData(ctx.chartDom, 'No scatter data available.');
        return;
    }

    var chart = activateTimelineChart(ctx);
    var chartDom = ctx.chartDom;

    var series = [];
    var visibleNames = getVisibleSamplerNames();
    var samplerNames = data.samplers ? data.samplers.map(function (s) { return s.name; }) : [];

    visibleNames.forEach(function (name) {
        if (!perSampler[name]) return;
        var rtData = perSampler[name].responseTime || perSampler[name][currentMetric];
        if (!rtData) return;

        var colorIdx = samplerNames.indexOf(name);
        if (colorIdx < 0) colorIdx = 0;

        series.push({
            name: name,
            type: 'scatter',
            data: zipTimeSeries(data.timestamps, rtData),
            symbolSize: 4,
            itemStyle: {
                color: getColorForSampler(name, colorIdx),
                opacity: 0.6
            }
        });
    });

    /* Total scatter points */
    if (isTotalVisible() && data.meanResponseTimes) {
        series.push({
            name: TOTAL_SAMPLER_NAME,
            type: 'scatter',
            data: zipTimeSeries(data.timestamps, data.meanResponseTimes),
            symbolSize: 6,
            itemStyle: { color: getTotalColor(), opacity: 0.8 }
        });
    }

    if (series.length === 0) {
        disposeLastChart(chart);
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No scatter data for selected samplers.');
        return;
    }

    injectRampUpMarkArea(series, data);

    var maxRT = getMaxFromSeries(series);
    finalizeTimelineChart(chart, {
        tooltip: { trigger: 'item', formatter: function (params) {
            return formatTime(new Date(params.value[0])) + '<br/>' +
                params.marker + ' ' + params.seriesName + ': <b>' + formatResponseTime(params.value[1]) + '</b>';
        }},
        yAxis: responseTimeYAxis(maxRT, 'Response Time'),
        series: series
    });
}

/* ============================================================
 *  Timeline Annotation Markers (ECharts markLine)
 * ============================================================ */

function getTimelineMarkLines(data) {
    if (!data || !data.timelineMarkers || data.timelineMarkers.length === 0) return {};

    var markerColors = {
        'warning': '#f59e0b',
        'error': '#ef4444',
        'incident': '#ef4444',
        'deployment': '#3b82f6',
        'info': '#3b82f6',
        'custom': '#8b5cf6'
    };

    var lines = data.timelineMarkers.map(function (m) {
        var color = m.color || markerColors[m.type] || '#94a3b8';
        return {
            xAxis: m.timestamp,
            label: {
                formatter: m.label || '',
                position: 'insideStartTop',
                fontSize: 10,
                color: color
            },
            lineStyle: {
                color: color,
                type: 'dashed',
                width: 2
            },
            name: m.description || m.label || ''
        };
    });

    return {
        silent: false,
        symbol: 'none',
        data: lines
    };
}

function injectMarkLines(series, data, chartType) {
    var ml = getTimelineMarkLines(data);
    var slaLines = getSlaMarkLines(data, chartType);

    var allLines = [];
    if (ml.data) allLines = allLines.concat(ml.data);
    if (slaLines.length > 0) allLines = allLines.concat(slaLines);

    if (allLines.length > 0 && series.length > 0) {
        series[0].markLine = {
            silent: false,
            symbol: 'none',
            data: allLines
        };
    }
}

function getSlaMarkLines(data, chartType) {
    if (!data || !data.slaThresholds) return [];

    var lines = [];
    var defaults = data.slaThresholds['default'];
    if (!defaults) return [];

    // Map currentMetric to SLA key
    var metricMap = { responseTime: 'mean', p50: 'p50', p90: 'p90', p95: 'p95', p99: 'p99', max: 'max' };
    var slaKey = metricMap[currentMetric] || 'p95';

    if (chartType === 'responseTime' && defaults[slaKey]) {
        lines.push({
            yAxis: defaults[slaKey],
            label: {
                formatter: 'SLA ' + slaKey.toUpperCase() + ': ' + defaults[slaKey] + 'ms',
                position: 'insideEndTop',
                fontSize: 10,
                color: '#ef4444'
            },
            lineStyle: {
                color: '#ef4444',
                type: 'dashed',
                width: 1.5
            }
        });
    }

    if (chartType === 'errorRate' && defaults.errorRate) {
        lines.push({
            yAxis: defaults.errorRate,
            label: {
                formatter: 'SLA Error: ' + defaults.errorRate + '%',
                position: 'insideEndTop',
                fontSize: 10,
                color: '#ef4444'
            },
            lineStyle: {
                color: '#ef4444',
                type: 'dashed',
                width: 1.5
            }
        });
    }

    return lines;
}

/* ============================================================
 *  Error Tab Charts
 * ============================================================ */

function getFilteredErrorRecords(data) {
    if (!data.errorRecords) return [];
    if (isTotalVisible()) return data.errorRecords;
    var visible = getVisibleSamplerNames();
    if (visible.length === 0) return [];
    return data.errorRecords.filter(function (er) {
        return visible.indexOf(er.sampler) >= 0;
    });
}

function initErrorBreakdownCharts(data) {
    initErrorByTypeChart(data);
    initErrorBySamplerChart(data);
    initErrorTimelineChart(data);
    buildErrorSummaryTotalRow();
}

function buildErrorSummaryTotalRow() {
    var table = document.getElementById('errorSummaryTable');
    if (!table || !table.tBodies[0]) return;
    var tbody = table.tBodies[0];

    /* Remove existing total row */
    var existing = tbody.querySelector('.error-summary-total');
    if (existing) existing.remove();

    var rows = tbody.rows;
    if (rows.length === 0) return;

    /* Sum the Count column (index 2) */
    var totalCount = 0;
    for (var r = 0; r < rows.length; r++) {
        var cell = rows[r].cells[2];
        if (cell) totalCount += parseInt(cell.textContent.replace(/,/g, ''), 10) || 0;
    }

    /* Get total samples from metadata card */
    var totalSamples = 0;
    if (globalChartData && globalChartData.metadata) {
        totalSamples = globalChartData.metadata.totalSamples || 0;
    }
    var pctAll = totalSamples > 0 ? (totalCount * 100.0 / totalSamples).toFixed(2) + '%' : '0%';

    /* Build total row with same column count */
    var numCols = rows[0].cells.length;
    var tr = document.createElement('tr');
    tr.className = 'error-summary-total';
    tr.style.fontWeight = '700';
    tr.style.borderTop = '2px solid var(--color-border)';
    for (var c = 0; c < numCols; c++) {
        var td = document.createElement('td');
        if (c === 0) td.textContent = 'Total';
        else if (c === 2) td.textContent = totalCount.toLocaleString();
        else if (c === 3) td.textContent = '100%';
        else if (c === 4) td.textContent = pctAll;
        tr.appendChild(td);
    }
    tbody.appendChild(tr);

    /* Apply current column visibility */
    applyGenericColumnVisibility('errorSummaryTable');
}

/* Fixed color map for HTTP error codes */
var ERROR_CODE_COLORS = {
    '400': '#f59e0b', '401': '#3b82f6', '402': '#ec4899', '403': '#8b5cf6',
    '404': '#f97316', '405': '#6366f1', '408': '#64748b', '409': '#14b8a6',
    '429': '#a855f7', '500': '#ef4444', '502': '#dc2626', '503': '#06b6d4',
    '504': '#eab308'
};

function getErrorCodeColor(code, fallbackIdx) {
    return ERROR_CODE_COLORS[code] || CHART_COLORS[fallbackIdx % CHART_COLORS.length];
}

function initErrorByTypeChart(data) {
    var chartDom = document.getElementById('error-by-type-chart');
    if (!chartDom) return;

    // Recompute from filtered error records
    var filtered = getFilteredErrorRecords(data);
    if (filtered.length === 0) {
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No errors for selected samplers.');
        return;
    }
    chartDom.style.height = '300px';
    chartDom.innerHTML = '';

    // Aggregate by response code only
    var codeMap = {};
    filtered.forEach(function (er) {
        codeMap[er.code] = (codeMap[er.code] || 0) + 1;
    });

    var chart = createChart(chartDom);

    var idx = 0;
    var pieData = [];
    for (var code in codeMap) {
        pieData.push({
            name: code,
            value: codeMap[code],
            itemStyle: { color: getErrorCodeColor(code, idx) }
        });
        idx++;
    }

    chart.setOption({
        tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
        series: [{
            type: 'pie',
            radius: ['35%', '70%'],
            avoidLabelOverlap: true,
            itemStyle: { borderRadius: 4, borderColor: '#fff', borderWidth: 2 },
            label: { show: true, fontSize: 11 },
            data: pieData
        }]
    });
}

function getSamplerIndex(data, samplerName) {
    if (!data.samplers) return 0;
    for (var i = 0; i < data.samplers.length; i++) {
        if (data.samplers[i].name === samplerName) return i;
    }
    return 0;
}

function initErrorBySamplerChart(data) {
    var chartDom = document.getElementById('error-by-sampler-chart');
    if (!chartDom) return;

    // Recompute from filtered error records
    var filtered = getFilteredErrorRecords(data);
    if (filtered.length === 0) {
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No errors for selected samplers.');
        return;
    }
    chartDom.style.height = '300px';
    chartDom.innerHTML = '';

    // Aggregate by sampler
    var samplerMap = {};
    filtered.forEach(function (er) {
        samplerMap[er.sampler] = (samplerMap[er.sampler] || 0) + 1;
    });

    var chart = createChart(chartDom);

    var names = Object.keys(samplerMap);
    var barData = names.map(function (name) {
        var idx = getSamplerIndex(data, name);
        return { value: samplerMap[name], itemStyle: { color: getColorForSampler(name, idx) } };
    });

    chart.setOption({
        tooltip: {
            trigger: 'axis',
            formatter: function (params) {
                var p = params[0];
                return p.name + '<br/>Errors: <b>' + p.value + '</b>';
            }
        },
        grid: { left: 10, right: 20, top: 10, bottom: 60, containLabel: true },
        xAxis: { type: 'category', data: names, axisLabel: { rotate: 30, fontSize: 10, formatter: function(value) { return value.length > 25 ? value.substring(0, 22) + '...' : value; } } },
        yAxis: { type: 'value', name: 'Errors' },
        series: [{
            type: 'bar',
            data: barData,
            itemStyle: { borderRadius: [4, 4, 0, 0] },
            barMaxWidth: 50
        }]
    });
}

function initErrorTimelineChart(data) {
    var chartDom = document.getElementById('error-timeline-chart');
    if (!chartDom) return;

    var filtered = getFilteredErrorRecords(data);
    if (filtered.length === 0) {
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No errors for selected samplers.');
        return;
    }
    chartDom.style.height = '400px';
    chartDom.innerHTML = '';

    var chart = createChart(chartDom);

    // Group errors by sampler for colored scatter
    var samplerMap = {};
    filtered.forEach(function (er) {
        if (!samplerMap[er.sampler]) samplerMap[er.sampler] = [];
        samplerMap[er.sampler].push([er.timestamp, er.code]);
    });

    var series = [];
    for (var sampler in samplerMap) {
        var sIdx = getSamplerIndex(data, sampler);
        series.push({
            name: sampler,
            type: 'scatter',
            data: samplerMap[sampler].map(function (d) { return { value: d, code: d[1] }; }),
            symbolSize: 10,
            itemStyle: { color: getColorForSampler(sampler, sIdx) }
        });
    }

    injectRampUpMarkArea(series, data);

    chart.setOption({
        toolbox: getToolbox(),
        tooltip: {
            trigger: 'item',
            formatter: function (params) {
                var date = new Date(params.value[0]);
                return formatTime(date) + '<br/>' +
                    params.seriesName + '<br/>' +
                    'Code: <b>' + params.value[1] + '</b>';
            }
        },
        legend: { data: series.map(function (s) { return s.name; }), bottom: 10, type: 'scroll', formatter: truncateLegendName, tooltip: { show: true } },
        grid: { left: 60, right: 30, top: 20, bottom: 90 },
        xAxis: { type: 'time', axisLabel: { formatter: formatTimeAxis }, min: getTestStart() || undefined, max: getTestStart() ? getTestStart() + getTestDurationMs() : undefined },
        yAxis: { type: 'category', data: ['Error'], axisLabel: { show: false }, axisTick: { show: false } },
        series: series,
        dataZoom: getDataZoom()
    });

    bindDataZoomToTimeRange(chart);
}

/* ============================================================
 *  Tooltip formatter
 * ============================================================ */

function chartTooltipFormatter(params) {
    var ts = params[0].value[0];
    var date = new Date(ts);
    var clock = formatTime(date);
    var elapsed = formatElapsed(ts - getTestStart());
    var primary = timeDisplayMode === 'timer' ? elapsed : clock;
    var secondary = timeDisplayMode === 'timer' ? clock : elapsed;
    var str = primary + ' <span style="color:#94a3b8;">(' + secondary + ')</span><br/>';
    params.forEach(function (p) {
        str += p.marker + ' ' + p.seriesName + ': <b>' + formatValue(p.value[1], p.seriesName) + '</b><br/>';
    });
    return str;
}

/* ============================================================
 *  Utility functions
 * ============================================================ */

function formatTime(date) {
    return pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds());
}

function formatTimeRangeFull(date) {
    return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate()) +
        ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes()) + ':' + pad(date.getSeconds());
}

function getTestStart() {
    if (!globalChartData || !globalChartData.timestamps || globalChartData.timestamps.length === 0) return 0;
    return globalChartData.timestamps[0];
}

function getTestDurationMs() {
    if (!globalChartData || !globalChartData.timestamps || globalChartData.timestamps.length < 2) return 0;
    var ts = globalChartData.timestamps;
    return ts[ts.length - 1] - ts[0];
}

function testSpansMidnight() {
    if (!globalChartData || !globalChartData.timestamps || globalChartData.timestamps.length < 2) return false;
    var ts = globalChartData.timestamps;
    var startDay = new Date(ts[0]).getDate();
    var endDay = new Date(ts[ts.length - 1]).getDate();
    return startDay !== endDay;
}

function getEffectiveVisibleRange() {
    return visibleTimeRangeMs > 0 ? visibleTimeRangeMs : getTestDurationMs();
}

function formatElapsed(ms) {
    if (ms < 0) ms = 0;
    var totalSec = Math.floor(ms / 1000);
    var h = Math.floor(totalSec / 3600);
    var m = Math.floor((totalSec % 3600) / 60);
    var s = totalSec % 60;
    var range = getEffectiveVisibleRange();

    if (range < 120000) {
        /* Visible < 2 min: always show seconds */
        if (h > 0) return h + ':' + pad(m) + ':' + pad(s);
        return m + ':' + pad(s);
    } else if (range < 3600000) {
        /* Visible < 1 hour: show mm:ss */
        if (h > 0) return h + ':' + pad(m) + ':' + pad(s);
        return m + ':' + pad(s);
    } else {
        /* Visible > 1 hour: drop seconds */
        if (h > 0) return h + ':' + pad(m);
        return m + 'm';
    }
}

function formatClock(value) {
    var d = new Date(value);
    var range = getEffectiveVisibleRange();
    var showDate = testSpansMidnight();
    var prefix = showDate ? pad(d.getMonth() + 1) + '/' + pad(d.getDate()) + ' ' : '';

    if (range < 120000) {
        return prefix + pad(d.getHours()) + ':' + pad(d.getMinutes()) + ':' + pad(d.getSeconds());
    } else {
        return prefix + pad(d.getHours()) + ':' + pad(d.getMinutes());
    }
}

function formatTimeAxis(value) {
    if (timeDisplayMode === 'timer') {
        return formatElapsed(value - getTestStart());
    }
    return formatClock(value);
}

function formatMiniTimeAxis(value) {
    if (!globalChartData || !globalChartData.timestamps || globalChartData.timestamps.length < 2) {
        return formatClock(value);
    }
    var ts = globalChartData.timestamps;
    var durationSec = (ts[ts.length - 1] - ts[0]) / 1000;
    var elapsed = (value - ts[0]) / 1000;
    if (elapsed < 0) elapsed = 0;
    if (durationSec <= 120) {
        return Math.round(elapsed) + 's';
    } else if (durationSec <= 7200) {
        var m = Math.floor(elapsed / 60);
        var s = Math.round(elapsed % 60);
        return m + ':' + pad(s);
    }
    return formatClock(value);
}

function toggleTimeMode() {
    timeDisplayMode = timeDisplayMode === 'clock' ? 'timer' : 'clock';
    /* Update toggle button */
    var btn = document.getElementById('timeModeToggle');
    if (btn) {
        btn.innerHTML = timeDisplayMode === 'clock' ? '&#128339;' : '&#9201;';
        btn.title = timeDisplayMode === 'clock' ? 'Showing wall-clock time (click for elapsed)' : 'Showing elapsed time (click for wall-clock)';
    }
    /* Re-render all chart axis labels */
    chartInstances.forEach(function (c) {
        try { c.setOption({ xAxis: { axisLabel: { formatter: formatTimeAxis } } }); } catch (e) { /* ignore */ }
    });
    /* Update time range display */
    if (globalChartData) updateTimeRangeDisplay();
}

function updateTimeRangeDisplay() {
    var startInput = document.getElementById('timeRangeStart');
    var endInput = document.getElementById('timeRangeEnd');
    if (!startInput || !endInput || !globalChartData || !globalChartData.timestamps) return;
    /* Try to read from first chart's dataZoom */
    var startVal = null, endVal = null;
    for (var i = 0; i < chartInstances.length; i++) {
        try {
            var opt = chartInstances[i].getOption();
            if (opt && opt.dataZoom && opt.dataZoom.length > 0) {
                var z = opt.dataZoom[0];
                if (z.startValue != null && z.endValue != null) {
                    startVal = z.startValue;
                    endVal = z.endValue;
                    break;
                }
            }
        } catch (e) { /* ignore */ }
    }
    if (startVal == null) {
        var ts = globalChartData.timestamps;
        startVal = ts[0];
        endVal = ts[ts.length - 1];
    }
    startInput.value = formatTimeRangeFull(new Date(startVal));
    endInput.value = formatTimeRangeFull(new Date(endVal));
}

function pad(n) {
    return n < 10 ? '0' + n : '' + n;
}

function formatValue(val, seriesName) {
    if (seriesName === 'Active Threads') return Math.round(val);
    return formatResponseTime(val);
}

function formatResponseTime(ms) {
    if (ms == null || isNaN(ms)) return '0 ms';
    var abs = Math.abs(ms);
    if (abs < 1000) return ms.toFixed(1) + ' ms';
    if (abs < 60000) return (ms / 1000).toFixed(2) + 's';
    if (abs < 3600000) {
        var m = Math.floor(ms / 60000);
        var s = Math.round((ms % 60000) / 1000);
        return m + 'm ' + s + 's';
    }
    var h = Math.floor(ms / 3600000);
    var min = Math.round((ms % 3600000) / 60000);
    return h + 'h ' + min + 'm';
}

function getResponseTimeUnit(maxMs) {
    if (maxMs < 1000) return { divisor: 1, suffix: 'ms', decimals: 0 };
    if (maxMs < 60000) return { divisor: 1000, suffix: 'sec', decimals: 1 };
    if (maxMs < 3600000) return { divisor: 60000, suffix: 'min', decimals: 1 };
    return { divisor: 3600000, suffix: 'hr', decimals: 1 };
}

function makeResponseTimeAxisFormatter(maxMs) {
    var unit = getResponseTimeUnit(maxMs);
    return function (value) {
        if (value === 0) return '0';
        return (value / unit.divisor).toFixed(unit.decimals);
    };
}

function getMaxFromSeries(seriesArray) {
    var max = 0;
    for (var i = 0; i < seriesArray.length; i++) {
        var data = seriesArray[i].data;
        if (!data) continue;
        for (var j = 0; j < data.length; j++) {
            var v = Array.isArray(data[j]) ? data[j][1] : (data[j] && data[j].value ? data[j].value[1] : data[j]);
            if (typeof v === 'number' && v > max) max = v;
        }
    }
    return max;
}

function responseTimeYAxis(maxMs, name) {
    var unit = getResponseTimeUnit(maxMs);
    var axisName = name ? name + ' (' + unit.suffix + ')' : '(' + unit.suffix + ')';
    return { type: 'value', name: axisName, min: 0, axisLabel: { formatter: makeResponseTimeAxisFormatter(maxMs) } };
}

/* ============================================================
 *  Table sorting
 * ============================================================ */

function sortTable(colIndex, type) {
    var table = document.getElementById('samplerTable');
    if (!table) return;
    var tbody = table.tBodies[0];
    var rows = Array.from(tbody.rows);
    var header = table.tHead.rows[0].cells[colIndex];

    /* If column order is customized, map logical colIndex through order */
    var effectiveCol = colIndex;

    var asc = header.getAttribute('data-sort') !== 'asc';
    Array.from(table.tHead.rows[0].cells).forEach(function (th) { th.removeAttribute('data-sort'); });
    header.setAttribute('data-sort', asc ? 'asc' : 'desc');

    /* Only sort non-detail rows */
    var sortableRows = rows.filter(function (r) { return !r.classList.contains('sampler-detail-row'); });
    var detailRows = rows.filter(function (r) { return r.classList.contains('sampler-detail-row'); });

    sortableRows.sort(function (a, b) {
        var cellA = a.cells[effectiveCol], cellB = b.cells[effectiveCol];
        var nameA = cellA.querySelector('.sampler-name-text'), nameB = cellB.querySelector('.sampler-name-text');
        var va = (nameA ? nameA.textContent : cellA.textContent).replace(/[,%/s]/g, '').trim();
        var vb = (nameB ? nameB.textContent : cellB.textContent).replace(/[,%/s]/g, '').trim();
        if (type === 'num') {
            va = parseFloat(va.replace(/,/g, '')) || 0;
            vb = parseFloat(vb.replace(/,/g, '')) || 0;
        }
        if (va < vb) return asc ? -1 : 1;
        if (va > vb) return asc ? 1 : -1;
        return 0;
    });

    sortableRows.forEach(function (row) { tbody.appendChild(row); });
    /* Re-append detail rows after their parents */
    detailRows.forEach(function (dr) {
        var parentName = dr.getAttribute('data-detail-for');
        if (parentName) {
            var parentRow = tbody.querySelector('tr[data-sampler="' + parentName + '"]');
            if (parentRow && parentRow.nextSibling) {
                tbody.insertBefore(dr, parentRow.nextSibling);
            } else {
                tbody.appendChild(dr);
            }
        }
    });
}

/* ============================================================
 *  Cross-chart sync
 * ============================================================ */

var chartGroup = 'wir-linked';

function connectCharts() {
    chartInstances.forEach(function (c) {
        c.group = chartGroup;
    });
    if (typeof echarts !== 'undefined') {
        echarts.connect(chartGroup);
    }
}

/* ============================================================
 *  Chart export toolbox (common config) — includes SVG export
 * ============================================================ */

function getToolbox() {
    return {
        show: true,
        right: 4,
        top: 0,
        feature: {
            saveAsImage: { title: 'Save as PNG', pixelRatio: 2 },
            myExportSvg: {
                show: true,
                title: 'Save as SVG',
                icon: 'path://M14,3V5H17.59L7.76,14.83L9.17,16.24L19,6.41V10H21V3M19,19H5V5H12V3H5C3.89,3 3,3.9 3,5V19A2,2 0 0,0 5,21H19A2,2 0 0,0 21,19V12H19V19Z',
                onclick: function () {
                    /* Find the chart that owns this toolbox via active charts */
                    chartInstances.forEach(function (c) {
                        try {
                            var svgUrl = c.getDataURL({ type: 'svg' });
                            if (svgUrl) {
                                var a = document.createElement('a');
                                a.href = svgUrl;
                                a.download = 'chart.svg';
                                a.click();
                            }
                        } catch (e) { /* not all renderers support svg export */ }
                    });
                }
            }
        }
    };
}

/* ============================================================
 *  Fullscreen
 * ============================================================ */

function toggleFullscreen(btn) {
    var section = btn.closest('.report-section');
    if (!section) return;
    var chartEl = section.querySelector('.chart-container');
    if (!chartEl) return;

    if (section.classList.contains('chart-fullscreen')) {
        section.classList.remove('chart-fullscreen');
        btn.textContent = 'Fullscreen';
        chartEl.style.height = '400px';
    } else {
        section.classList.add('chart-fullscreen');
        btn.textContent = 'Exit Fullscreen';
        chartEl.style.height = 'calc(100vh - 60px)';
    }

    setTimeout(function () {
        chartInstances.forEach(function (c) { c.resize(); });
    }, 100);
}

/* ============================================================
 *  Chart Visibility (Hide / Show)
 * ============================================================ */

function toggleChartVisibility(btn) {
    var section = btn.closest('.timeline-chart-section');
    if (!section) return;
    section.classList.add('chart-hidden');
    btn.textContent = 'Show';
    updateHiddenChartsBtn();
    saveChartLayout();
}

function showAllCharts() {
    var sections = document.querySelectorAll('.timeline-chart-section.chart-hidden');
    sections.forEach(function (s) {
        s.classList.remove('chart-hidden');
        var hideBtn = s.querySelector('.chart-hide-btn');
        if (hideBtn) hideBtn.textContent = 'Hide';
    });
    updateHiddenChartsBtn();
    saveChartLayout();
    /* Resize charts that were hidden */
    setTimeout(function () { chartInstances.forEach(function (c) { c.resize(); }); }, 100);
}

function updateHiddenChartsBtn() {
    var hidden = document.querySelectorAll('.timeline-chart-section.chart-hidden');
    var btn = document.getElementById('showHiddenChartsBtn');
    if (btn) {
        if (hidden.length > 0) {
            btn.style.display = '';
            btn.textContent = 'Show ' + hidden.length + ' Hidden Chart' + (hidden.length > 1 ? 's' : '');
        } else {
            btn.style.display = 'none';
        }
    }
}

function toggleCompareChartVisibility(btn) {
    var section = btn.closest('.timeline-chart-section');
    if (!section) return;
    section.classList.add('chart-hidden');
    btn.textContent = 'Show';
    updateHiddenCompareChartsBtn();
}

function showAllCompareCharts() {
    var tab = document.getElementById('tab-compare');
    if (!tab) return;
    tab.querySelectorAll('.timeline-chart-section.chart-hidden').forEach(function (s) {
        s.classList.remove('chart-hidden');
        var hideBtn = s.querySelector('.chart-hide-btn');
        if (hideBtn) hideBtn.textContent = 'Hide';
    });
    updateHiddenCompareChartsBtn();
    setTimeout(function () { chartInstances.forEach(function (c) { c.resize(); }); }, 100);
}

function updateHiddenCompareChartsBtn() {
    var tab = document.getElementById('tab-compare');
    if (!tab) return;
    var hidden = tab.querySelectorAll('.timeline-chart-section.chart-hidden');
    var btn = document.getElementById('showHiddenCompareChartsBtn');
    if (btn) {
        if (hidden.length > 0) {
            btn.style.display = '';
            btn.textContent = 'Show ' + hidden.length + ' Hidden Chart' + (hidden.length > 1 ? 's' : '');
        } else {
            btn.style.display = 'none';
        }
    }
}

function toggleErrorChartVisibility(btn) {
    var section = btn.closest('.timeline-chart-section');
    if (!section) return;
    section.classList.add('chart-hidden');
    btn.textContent = 'Show';
    updateHiddenErrorChartsBtn();
}

function showAllErrorCharts() {
    var tab = document.getElementById('tab-errors');
    if (!tab) return;
    tab.querySelectorAll('.timeline-chart-section.chart-hidden').forEach(function (s) {
        s.classList.remove('chart-hidden');
        var hideBtn = s.querySelector('.chart-hide-btn');
        if (hideBtn) hideBtn.textContent = 'Hide';
    });
    updateHiddenErrorChartsBtn();
    setTimeout(function () { chartInstances.forEach(function (c) { c.resize(); }); }, 100);
}

function updateHiddenErrorChartsBtn() {
    var tab = document.getElementById('tab-errors');
    if (!tab) return;
    var hidden = tab.querySelectorAll('.timeline-chart-section.chart-hidden');
    var btn = document.getElementById('showHiddenErrorChartsBtn');
    if (btn) {
        if (hidden.length > 0) {
            btn.style.display = '';
            btn.textContent = 'Show ' + hidden.length + ' Hidden Chart' + (hidden.length > 1 ? 's' : '');
        } else {
            btn.style.display = 'none';
        }
    }
}

/* ============================================================
 *  Chart Drag & Drop Reorder
 * ============================================================ */

var draggedChart = null;

function initChartDragDrop() {
    initChartDragDropForContainer('timelineChartContainer', 'wir_chartLayout');
    initChartDragDropForContainer('compareChartContainer', 'wir_compareChartLayout');
    initChartDragDropForContainer('errorChartContainer', 'wir_errorChartLayout');
}

function initChartDragDropForContainer(containerId, storageKey) {
    var container = document.getElementById(containerId);
    if (!container) return;

    /* Make sections draggable only when drag starts from the handle icon */
    container.querySelectorAll('.chart-drag-handle').forEach(function (handle) {
        handle.addEventListener('mousedown', function () {
            var section = handle.closest('.timeline-chart-section');
            if (section) section.draggable = true;
        });
    });

    /* Remove draggable after drag ends or mouse leaves handle without dragging */
    container.addEventListener('mouseup', function () {
        container.querySelectorAll('.timeline-chart-section[draggable]').forEach(function (s) {
            s.removeAttribute('draggable');
        });
    });

    container.addEventListener('dragstart', function (e) {
        var section = e.target.closest('.timeline-chart-section');
        if (!section) return;
        draggedChart = section;
        section.style.opacity = '0.4';
        e.dataTransfer.effectAllowed = 'move';
    });

    container.addEventListener('dragend', function (e) {
        if (draggedChart) {
            draggedChart.style.opacity = '';
            draggedChart.removeAttribute('draggable');
        }
        draggedChart = null;
        container.querySelectorAll('.timeline-chart-section').forEach(function (s) {
            s.classList.remove('drag-over');
        });
    });

    container.addEventListener('dragover', function (e) {
        e.preventDefault();
        e.dataTransfer.dropEffect = 'move';
        var target = e.target.closest('.timeline-chart-section');
        if (target && target !== draggedChart) {
            container.querySelectorAll('.timeline-chart-section').forEach(function (s) {
                s.classList.remove('drag-over');
            });
            target.classList.add('drag-over');
        }
    });

    container.addEventListener('dragleave', function (e) {
        var target = e.target.closest('.timeline-chart-section');
        if (target) target.classList.remove('drag-over');
    });

    container.addEventListener('drop', function (e) {
        e.preventDefault();
        var target = e.target.closest('.timeline-chart-section');
        if (!target || !draggedChart || target === draggedChart) return;
        target.classList.remove('drag-over');

        /* Determine drop position */
        var rect = target.getBoundingClientRect();
        var midY = rect.top + rect.height / 2;
        if (e.clientY < midY) {
            container.insertBefore(draggedChart, target);
        } else {
            container.insertBefore(draggedChart, target.nextSibling);
        }

        saveChartLayoutFor(container, storageKey);
        /* Resize all charts after reorder */
        setTimeout(function () { chartInstances.forEach(function (c) { c.resize(); }); }, 100);
    });
}

/* Persist chart order & visibility to localStorage */
function saveChartLayout() {
    saveChartLayoutFor(document.getElementById('timelineChartContainer'), 'wir_chartLayout');
}

function saveChartLayoutFor(container, storageKey) {
    if (!container) return;
    var sections = container.querySelectorAll('.timeline-chart-section');
    var layout = [];
    sections.forEach(function (s) {
        layout.push({
            id: s.getAttribute('data-chart-id'),
            hidden: s.classList.contains('chart-hidden')
        });
    });
    try { localStorage.setItem(storageKey, JSON.stringify(layout)); } catch (e) { /* ignore */ }
}

function restoreChartLayout() {
    restoreChartLayoutFor('timelineChartContainer', 'wir_chartLayout');
    restoreChartLayoutFor('compareChartContainer', 'wir_compareChartLayout');
    restoreChartLayoutFor('errorChartContainer', 'wir_errorChartLayout');
}

function restoreChartLayoutFor(containerId, storageKey) {
    try {
        var raw = localStorage.getItem(storageKey);
        if (!raw) return;
        var layout = JSON.parse(raw);
        var container = document.getElementById(containerId);
        if (!container) return;

        /* Restore order: move saved charts first, then append any new charts at the end */
        var savedIds = {};
        layout.forEach(function (item) {
            savedIds[item.id] = true;
            var section = container.querySelector('[data-chart-id="' + item.id + '"]');
            if (section) {
                container.appendChild(section);
                if (item.hidden) section.classList.add('chart-hidden');
            }
        });
        /* Append any charts not in the saved layout (new charts) at the end */
        container.querySelectorAll('.timeline-chart-section').forEach(function (section) {
            var id = section.getAttribute('data-chart-id');
            if (id && !savedIds[id]) {
                container.appendChild(section);
            }
        });
        updateHiddenChartsBtn();
    } catch (e) { /* ignore */ }
}

/* ============================================================
 *  Dark Mode
 * ============================================================ */

function toggleDarkMode() {
    var isDark = document.body.classList.toggle('dark-mode');
    localStorage.setItem('wir_darkMode', isDark);
    var icon = document.getElementById('darkModeIcon');
    if (icon) icon.textContent = isDark ? '\u2600' : '\u263E';

    // Reinit charts with appropriate theme
    refreshAllCharts();
}

function toggleColorblindMode() {
    var active = document.body.classList.toggle('cb-mode');
    localStorage.setItem('wir_colorblindMode', active);
    var btn = document.getElementById('cbModeToggle');
    if (btn) {
        btn.classList.toggle('cb-active', active);
        btn.title = active ? 'Colorblind mode (on)' : 'Colorblind mode (off)';
    }
    var icon = document.getElementById('cbModeIcon');
    if (icon) icon.style.opacity = active ? '1' : '0.4';
    refreshAllCharts();
}

function getChartTheme() {
    return document.body.classList.contains('dark-mode') ? 'dark' : null;
}

/* ============================================================
 *  Sampler table: search, column toggle
 * ============================================================ */

var COLUMN_NAMES = ['Sampler', 'Samples', 'Errors', 'Error %', 'Mean (ms)',
    'Median (ms)', 'P90 (ms)', 'P95 (ms)', 'P99 (ms)', 'Min (ms)', 'Max (ms)',
    'Throughput', 'Recv KB/s', 'Apdex'];

var hiddenColumns = {};

function filterSamplerTable() {
    var input = document.getElementById('samplerSearch');
    if (!input) return;
    var filter = input.value.toLowerCase();
    var table = document.getElementById('samplerTable');
    if (!table) return;
    var rows = table.tBodies[0].rows;
    for (var i = 0; i < rows.length; i++) {
        if (rows[i].classList.contains('sampler-detail-row')) continue;
        var nameEl = rows[i].querySelector('.sampler-name-text');
        var name = (nameEl ? nameEl.textContent : rows[i].cells[0].textContent).toLowerCase();
        rows[i].style.display = name.indexOf(filter) >= 0 ? '' : 'none';
    }
}

function initColumnMenu() {
    var menu = document.getElementById('columnMenu');
    if (!menu) return;
    menu.innerHTML = '';

    COLUMN_NAMES.forEach(function (name, idx) {
        var label = document.createElement('label');
        var cb = document.createElement('input');
        cb.type = 'checkbox';
        cb.checked = !hiddenColumns[idx];
        cb.addEventListener('change', function () {
            if (cb.checked) {
                delete hiddenColumns[idx];
            } else {
                hiddenColumns[idx] = true;
            }
            applyColumnVisibility();
        });
        label.appendChild(cb);
        label.appendChild(document.createTextNode(' ' + name));
        menu.appendChild(label);
    });
}

function toggleColumnMenu() {
    var menu = document.getElementById('columnMenu');
    if (menu) menu.classList.toggle('open');
}

function applyColumnVisibility() {
    var table = document.getElementById('samplerTable');
    if (!table) return;

    // Header
    var headerCells = table.tHead.rows[0].cells;
    for (var i = 0; i < headerCells.length; i++) {
        headerCells[i].style.display = hiddenColumns[i] ? 'none' : '';
    }
    // Body
    var rows = table.tBodies[0].rows;
    for (var r = 0; r < rows.length; r++) {
        if (rows[r].classList.contains('sampler-detail-row')) continue;
        var cells = rows[r].cells;
        for (var c = 0; c < cells.length; c++) {
            cells[c].style.display = hiddenColumns[c] ? 'none' : '';
        }
    }
    // Visual indicator + "Show All Columns" button
    var count = Object.keys(hiddenColumns).length;
    var menuBtn = document.getElementById('columnMenuBtn');
    if (menuBtn) menuBtn.classList.toggle('has-hidden', count > 0);
    var colBtn = document.getElementById('showAllColumnsBtn');
    if (colBtn) {
        if (count > 0) {
            colBtn.style.display = '';
            colBtn.textContent = 'Show ' + count + ' Hidden Column' + (count > 1 ? 's' : '');
        } else {
            colBtn.style.display = 'none';
        }
    }
}

function showAllColumns() {
    hiddenColumns = {};
    applyColumnVisibility();
    initColumnMenu();
}

/* ============================================================
 *  Transaction Controller Hierarchy
 * ============================================================ */

function initTransactionHierarchy(data) {
    if (!data.transactionHierarchy) return;

    var hierarchy = data.transactionHierarchy;
    var table = document.getElementById('samplerTable');
    if (!table) return;
    var tbody = table.tBodies[0];
    if (!tbody) return;

    /* Show the toggle button if hierarchy exists */
    var toggleBtn = document.getElementById('transactionChildrenBtn');
    if (toggleBtn && Object.keys(hierarchy).length > 0) {
        toggleBtn.style.display = '';
        if (showTransactionChildren) toggleBtn.classList.add('active');
    }

    /* Build a set of child sampler names */
    var childOf = {};
    for (var parentName in hierarchy) {
        var children = hierarchy[parentName];
        if (Array.isArray(children)) {
            children.forEach(function (c) { childOf[c] = parentName; });
        }
    }

    /* Add CSS class and hide child rows by default */
    var rows = Array.from(tbody.rows);
    rows.forEach(function (row) {
        var samplerName = row.getAttribute('data-sampler');
        if (!samplerName) return;

        if (childOf[samplerName]) {
            row.classList.add('transaction-child');
            if (!showTransactionChildren) row.style.display = 'none';
        }

        if (hierarchy[samplerName]) {
            row.classList.add('transaction-parent');
        }
    });

    /* Also hide children in error tables on initial load */
    if (!showTransactionChildren) {
        hideTransactionChildrenInTable('errorSummaryTable', 0);
        hideTransactionChildrenInTable('errorDetailsTable', 1);
    }
}

function toggleTransactionChildrenVisibility() {
    showTransactionChildren = !showTransactionChildren;
    localStorage.setItem('wir_showTransactionChildren', showTransactionChildren ? 'true' : 'false');

    /* Update button state */
    var btn = document.getElementById('transactionChildrenBtn');
    if (btn) {
        btn.classList.toggle('active', showTransactionChildren);
    }

    /* Show/hide child rows in samplers table */
    var table = document.getElementById('samplerTable');
    if (table && table.tBodies[0]) {
        var rows = Array.from(table.tBodies[0].rows);
        rows.forEach(function (row) {
            if (row.classList.contains('transaction-child')) {
                row.style.display = showTransactionChildren ? '' : 'none';
            }
        });
    }

    /* Show/hide child checkboxes in filter bar */
    var filterLabels = document.querySelectorAll('.transaction-child-filter');
    filterLabels.forEach(function (label) {
        label.style.display = showTransactionChildren ? '' : 'none';
    });

    /* Show/hide child rows in error tables */
    hideTransactionChildrenInTable('errorSummaryTable', 0);
    hideTransactionChildrenInTable('errorDetailsTable', 1);

    /* Recalculate total row and refresh charts */
    updateTotalRow();
    debouncedRefreshAllCharts();
}

function hideTransactionChildrenInTable(tableId, samplerColIdx) {
    var table = document.getElementById(tableId);
    if (!table || !table.tBodies[0]) return;
    var rows = table.tBodies[0].rows;
    for (var i = 0; i < rows.length; i++) {
        if (rows[i].classList.contains('error-detail-row')) continue;
        if (rows[i].classList.contains('error-summary-total')) continue;
        var cell = rows[i].cells[samplerColIdx];
        if (!cell) continue;
        var samplerName = cell.textContent.trim();
        if (isTransactionChild(samplerName)) {
            rows[i].style.display = showTransactionChildren ? '' : 'none';
        }
    }
}

/* ============================================================
 *  Per-Sampler Detail View (expandable row)
 * ============================================================ */

var DETAIL_CHART_TYPES = [
    { key: 'histogram', label: 'Response Time Distribution' },
    { key: 'responseTime', label: 'Response Time Over Time' },
    { key: 'bytes', label: 'Bytes (Recv / Sent)' },
    { key: 'throughput', label: 'Throughput Over Time' },
    { key: 'errorRate', label: 'Error Rate Over Time' },
    { key: 'connectLatency', label: 'Connect Time & Latency' },
    { key: 'percentiles', label: 'Percentiles Over Time' }
];
var DEFAULT_DETAIL_CHARTS = ['histogram', 'responseTime', 'bytes'];
var LS_DETAIL_CHARTS = 'wir_detailCharts';

function getDetailChartConfig() {
    try {
        var stored = localStorage.getItem(LS_DETAIL_CHARTS);
        if (stored) {
            var parsed = JSON.parse(stored);
            if (Array.isArray(parsed) && parsed.length >= 1 && parsed.length <= 4) return parsed;
        }
    } catch (e) { /* ignore */ }
    return DEFAULT_DETAIL_CHARTS.slice();
}

function saveDetailChartConfig(keys) {
    try { localStorage.setItem(LS_DETAIL_CHARTS, JSON.stringify(keys)); } catch (e) { /* ignore */ }
}

function readCurrentDetailConfig() {
    /* Read config from the first open detail row, or fall back to localStorage/defaults */
    var firstGrid = document.querySelector('.detail-chart-grid');
    if (firstGrid) {
        var slots = firstGrid.querySelectorAll('.detail-chart-slot');
        if (slots.length > 0) {
            var keys = [];
            slots.forEach(function (s) { keys.push(s.getAttribute('data-chart-key')); });
            return keys;
        }
    }
    return getDetailChartConfig();
}

function toggleSamplerDetail(row) {
    if (!row || !globalChartData) return;

    var samplerName = row.getAttribute('data-sampler');
    if (!samplerName) return;

    /* Check if detail row already exists */
    var existingDetail = row.nextElementSibling;
    if (existingDetail && existingDetail.classList.contains('sampler-detail-row')) {
        disposeDetailRowCharts(existingDetail);
        existingDetail.remove();
        row.classList.remove('sampler-expanded');
        updateDetailChartToolbarButtons();
        return;
    }

    row.classList.add('sampler-expanded');

    var chartKeys = getDetailChartConfig();
    var detailRow = buildSamplerDetailRow(row, samplerName, chartKeys);
    row.parentNode.insertBefore(detailRow, row.nextSibling);

    /* Initialize charts after DOM insertion */
    setTimeout(function () {
        var grid = detailRow.querySelector('.detail-chart-grid');
        if (!grid) return;
        grid.querySelectorAll('.detail-chart-slot').forEach(function (slot) {
            var key = slot.getAttribute('data-chart-key');
            var cid = slot.querySelector('.detail-chart-container').id;
            initDetailChart(key, samplerName, cid);
        });
    }, 50);
    updateDetailChartToolbarButtons();
}

function buildSamplerDetailRow(ownerRow, samplerName, chartKeys) {
    var detailRow = document.createElement('tr');
    detailRow.className = 'sampler-detail-row';
    detailRow.setAttribute('data-detail-for', samplerName);
    var colCount = ownerRow.cells.length;
    var td = document.createElement('td');
    td.setAttribute('colspan', colCount);
    td.style.padding = '16px';
    td.style.background = 'var(--color-bg)';

    /* Chart grid */
    var grid = document.createElement('div');
    grid.className = 'detail-chart-grid';
    td.appendChild(grid);

    chartKeys.forEach(function (chartKey, idx) {
        buildDetailSlot(grid, idx, chartKey, samplerName);
    });

    detailRow.appendChild(td);
    return detailRow;
}

function disposeDetailRowCharts(detailRow) {
    detailRow.querySelectorAll('.detail-chart-container').forEach(disposeChartIn);
}

function disposeSlotChart(slot) {
    var container = slot.querySelector('.detail-chart-container');
    if (container) disposeChartIn(container);
}

/* Apply a change globally: update all open detail rows and save to localStorage */
function syncDetailSlotChange(slotIndex, newKey) {
    var config = readCurrentDetailConfig();
    config[slotIndex] = newKey;
    saveDetailChartConfig(config);

    /* Update all other open detail rows */
    var allDetailRows = document.querySelectorAll('.sampler-detail-row');
    allDetailRows.forEach(function (dr) {
        var sName = dr.getAttribute('data-detail-for');
        var grid = dr.querySelector('.detail-chart-grid');
        if (!grid) return;
        var slots = grid.querySelectorAll('.detail-chart-slot');
        var slot = slots[slotIndex];
        if (!slot) return;
        if (slot.getAttribute('data-chart-key') === newKey) return;

        slot.setAttribute('data-chart-key', newKey);
        /* Update dropdown */
        var sel = slot.querySelector('.detail-chart-select');
        if (sel) sel.value = newKey;
        /* Dispose and re-init chart */
        disposeSlotChart(slot);
        var chartDiv = slot.querySelector('.detail-chart-container');
        chartDiv.innerHTML = '';
        initDetailChart(newKey, sName, chartDiv.id);
    });
}

function resizeDetailCharts() {
    document.querySelectorAll('.sampler-detail-row .detail-chart-container').forEach(function (el) {
        var inst = echarts.getInstanceByDom(el);
        if (inst) inst.resize();
    });
}

function updateDetailChartToolbarButtons() {
    var config = getDetailChartConfig();
    var addBtn = document.getElementById('addDetailChartBtn');
    var removeBtn = document.getElementById('removeDetailChartBtn');
    var hasExpanded = document.querySelectorAll('.sampler-detail-row').length > 0;
    if (addBtn) addBtn.style.display = (hasExpanded && config.length < 4) ? '' : 'none';
    if (removeBtn) removeBtn.style.display = (hasExpanded && config.length > 3) ? '' : 'none';
}

function addDetailSlotGlobal(defaultKey) {
    var config = readCurrentDetailConfig();
    if (config.length >= 4) return;
    config.push(defaultKey);
    saveDetailChartConfig(config);

    var allDetailRows = document.querySelectorAll('.sampler-detail-row');
    allDetailRows.forEach(function (dr) {
        var sName = dr.getAttribute('data-detail-for');
        var grid = dr.querySelector('.detail-chart-grid');
        if (!grid) return;
        var slotIdx = grid.querySelectorAll('.detail-chart-slot').length;
        var slot = buildDetailSlot(grid, slotIdx, defaultKey, sName);
        var cid = slot.querySelector('.detail-chart-container').id;
        initDetailChart(defaultKey, sName, cid);
    });
    updateDetailChartToolbarButtons();
    setTimeout(resizeDetailCharts, 50);
}

function removeLastDetailSlotGlobal() {
    var config = readCurrentDetailConfig();
    if (config.length <= 3) return;
    var slotIndex = config.length - 1;
    config.pop();
    saveDetailChartConfig(config);

    var allDetailRows = document.querySelectorAll('.sampler-detail-row');
    allDetailRows.forEach(function (dr) {
        var grid = dr.querySelector('.detail-chart-grid');
        if (!grid) return;
        var slots = grid.querySelectorAll('.detail-chart-slot');
        var slot = slots[slotIndex];
        if (!slot) return;
        disposeSlotChart(slot);
        slot.remove();
    });
    updateDetailChartToolbarButtons();
    setTimeout(resizeDetailCharts, 50);
}

function buildDetailSlot(grid, slotIndex, chartKey, samplerName) {
    var slot = document.createElement('div');
    slot.className = 'detail-chart-slot';
    slot.setAttribute('data-chart-key', chartKey);
    slot.setAttribute('data-slot-index', slotIndex);

    /* Header with dropdown */
    var header = document.createElement('div');
    header.className = 'detail-chart-header';

    var select = document.createElement('select');
    select.className = 'detail-chart-select';
    DETAIL_CHART_TYPES.forEach(function (ct) {
        var opt = document.createElement('option');
        opt.value = ct.key;
        opt.textContent = ct.label;
        if (ct.key === chartKey) opt.selected = true;
        select.appendChild(opt);
    });
    header.appendChild(select);

    slot.appendChild(header);

    /* Chart container */
    var containerId = 'detail-slot-' + slotIndex + '-' + CSS.escape(samplerName) + '-' + Date.now();
    var chartDiv = document.createElement('div');
    chartDiv.className = 'detail-chart-container';
    chartDiv.id = containerId;
    chartDiv.style.cssText = 'width:100%;height:200px;';
    slot.appendChild(chartDiv);

    grid.appendChild(slot);

    /* Dropdown change handler — applies globally */
    select.addEventListener('change', function () {
        syncDetailSlotChange(slotIndex, select.value);
    });

    return slot;
}

/* ---- Detail Chart Registry ----
 * Data-driven definitions for per-sampler detail chart types.
 * Adding a new chart type = adding one entry here. */
var DETAIL_CHART_RENDERERS = {
    /* Single-metric time series charts */
    responseTime: { metrics: [{ key: 'responseTime' }], noDataMsg: 'No response time data', useSamplerColor: true, areaOpacity: 0.15 },
    throughput:    { metrics: [{ key: 'throughput' }], noDataMsg: 'No throughput data', useSamplerColor: true, areaOpacity: 0.15, yAxisName: 'req/s' },
    errorRate:     { metrics: [{ key: 'errorRate', name: 'Error Rate', color: '#ef4444' }], noDataMsg: 'No error rate data', areaOpacity: 0.15, yAxisName: '%' },

    /* Multi-metric time series charts */
    connectLatency: {
        metrics: [
            { key: 'connectTime', name: 'Connect Time', color: '#f59e0b' },
            { key: 'latency', name: 'Latency', color: '#8b5cf6' },
            { key: 'responseTime', name: 'Response Time', color: '#3b82f6' }
        ],
        noDataMsg: 'No data', showLegend: true, yAxisName: 'ms'
    },
    percentiles: {
        metrics: [
            { key: 'p50', name: 'P50', color: '#22c55e' },
            { key: 'p90', name: 'P90', color: '#f59e0b' },
            { key: 'p95', name: 'P95', color: '#f97316' },
            { key: 'p99', name: 'P99', color: '#ef4444' }
        ],
        noDataMsg: 'No percentile data', showLegend: true, yAxisName: 'ms'
    }
};

/** Generic renderer for registry-based detail charts */
function renderDetailTimeSeries(samplerName, containerId, def) {
    var chartDom = document.getElementById(containerId);
    if (!chartDom || !globalChartData) return;

    var perSampler = globalChartData.perSamplerSeries;
    if (!perSampler || !perSampler[samplerName]) {
        showNoData(chartDom, def.noDataMsg, true);
        return;
    }

    var sd = perSampler[samplerName];
    var hasAny = def.metrics.some(function (m) { return !!sd[m.key]; });
    if (!hasAny) { showNoData(chartDom, def.noDataMsg, true); return; }

    var chart = createChart(chartDom);
    var ts = globalChartData.timestamps;
    var sIdx = getSamplerIndex(globalChartData, samplerName);
    var series = [];

    def.metrics.forEach(function (m) {
        var vals = sd[m.key];
        if (!vals) return;
        var s = {
            name: m.name || samplerName,
            type: 'line',
            data: zipTimeSeries(ts, vals),
            smooth: true,
            symbol: 'none',
            lineStyle: { width: lineThickness },
            itemStyle: { color: m.color || (def.useSamplerColor ? getColorForSampler(samplerName, sIdx) : undefined) }
        };
        if (def.areaOpacity) s.areaStyle = { opacity: def.areaOpacity, color: m.color || undefined };
        series.push(s);
    });

    var isMsChart = !def.yAxisName || def.yAxisName === 'ms';
    var yAxisOpt;
    if (isMsChart) {
        var maxVal = getMaxFromSeries(series);
        var unit = getResponseTimeUnit(maxVal);
        yAxisOpt = { type: 'value', axisLabel: { fontSize: 9, formatter: makeResponseTimeAxisFormatter(maxVal) }, min: 0, name: '(' + unit.suffix + ')', nameTextStyle: { fontSize: 9 } };
    } else {
        yAxisOpt = { type: 'value', axisLabel: { fontSize: 9 }, min: 0 };
        if (def.yAxisName) { yAxisOpt.name = def.yAxisName; yAxisOpt.nameTextStyle = { fontSize: 9 }; }
    }

    var opt = {
        tooltip: { trigger: 'axis', formatter: chartTooltipFormatter },
        grid: { left: 10, right: 10, top: 10, bottom: def.showLegend ? 30 : 25, containLabel: true },
        xAxis: { type: 'time', axisLabel: { fontSize: 9, formatter: formatMiniTimeAxis } },
        yAxis: yAxisOpt,
        series: series,
        dataZoom: [{ type: 'inside', xAxisIndex: 0 }]
    };
    if (def.showLegend) { opt.legend = { data: series.map(function (s) { return s.name; }), bottom: 0, textStyle: { fontSize: 9 }, formatter: truncateLegendName, tooltip: { show: true } }; }
    chart.setOption(opt);
}

function initDetailChart(chartKey, samplerName, containerId) {
    var renderer = DETAIL_CHART_RENDERERS[chartKey];
    if (renderer) {
        renderDetailTimeSeries(samplerName, containerId, renderer);
    } else if (chartKey === 'histogram') {
        initDetailHistogram(samplerName, containerId);
    } else if (chartKey === 'bytes') {
        initDetailBytes(samplerName, containerId);
    }
}

function initDetailHistogram(samplerName, containerId) {
    var chartDom = document.getElementById(containerId);
    if (!chartDom || !globalChartData) return;

    var dist = globalChartData.responseTimeDistribution;
    var buckets = null;
    if (dist && dist.perSampler && dist.perSampler[samplerName]) {
        buckets = dist.perSampler[samplerName];
    }

    var chart = createChart(chartDom);

    if (!buckets || Object.keys(buckets).length === 0) {
        showNoData(chartDom, 'No distribution data', true);
        return;
    }

    var labels = Object.keys(buckets).sort(function (a, b) { return parseFloat(a) - parseFloat(b); });
    var values = labels.map(function (l) { return buckets[l]; });

    var sIdx = getSamplerIndex(globalChartData, samplerName);

    chart.setOption({
        tooltip: { trigger: 'axis' },
        grid: { left: 10, right: 10, top: 10, bottom: 25, containLabel: true },
        xAxis: { type: 'category', data: labels, axisLabel: { fontSize: 9 } },
        yAxis: { type: 'value', axisLabel: { fontSize: 9 } },
        series: [{
            type: 'bar',
            data: values,
            itemStyle: {
                color: getColorForSampler(samplerName, sIdx),
                borderRadius: [2, 2, 0, 0]
            },
            barMaxWidth: 30
        }]
    });
}

/* initDetailResponseTime — now handled by DETAIL_CHART_RENDERERS.responseTime */

function initDetailBytes(samplerName, containerId) {
    var chartDom = document.getElementById(containerId);
    if (!chartDom || !globalChartData) return;

    /* Show received/sent byte stats from sampler data */
    var sampler = null;
    if (globalChartData.samplers) {
        for (var i = 0; i < globalChartData.samplers.length; i++) {
            if (globalChartData.samplers[i].name === samplerName) {
                sampler = globalChartData.samplers[i];
                break;
            }
        }
    }

    var perSampler = globalChartData.perSamplerSeries;
    var hasTimeSeries = perSampler && perSampler[samplerName] &&
        (perSampler[samplerName].receivedBytes || perSampler[samplerName].sentBytes);

    if (hasTimeSeries) {
        var chart = createChart(chartDom);

        var series = [];
        var sIdx = getSamplerIndex(globalChartData, samplerName);
        var seriesColor = getColorForSampler(samplerName, sIdx);

        if (perSampler[samplerName].receivedBytes) {
            series.push({
                name: 'Received',
                type: 'line',
                data: zipTimeSeries(globalChartData.timestamps, perSampler[samplerName].receivedBytes),
                smooth: true,
                symbol: 'none',
                lineStyle: { width: lineThickness },
                itemStyle: { color: seriesColor },
                areaStyle: { opacity: 0.1 }
            });
        }

        if (perSampler[samplerName].sentBytes) {
            series.push({
                name: 'Sent',
                type: 'line',
                data: zipTimeSeries(globalChartData.timestamps, perSampler[samplerName].sentBytes),
                smooth: true,
                symbol: 'none',
                lineStyle: { width: lineThickness, type: 'dashed' },
                itemStyle: { color: seriesColor }
            });
        }

        chart.setOption({
            tooltip: { trigger: 'axis' },
            legend: { data: series.map(function (s) { return s.name; }), bottom: 0, textStyle: { fontSize: 9 }, formatter: truncateLegendName, tooltip: { show: true } },
            grid: { left: 10, right: 10, top: 10, bottom: 30, containLabel: true },
            xAxis: { type: 'time', axisLabel: { fontSize: 9, formatter: formatMiniTimeAxis } },
            yAxis: {
                type: 'value',
                axisLabel: {
                    fontSize: 9,
                    formatter: function (v) {
                        if (v >= 1048576) return (v / 1048576).toFixed(1) + 'M';
                        if (v >= 1024) return (v / 1024).toFixed(1) + 'K';
                        return v + '';
                    }
                },
                min: 0
            },
            series: series,
            dataZoom: [{ type: 'inside', xAxisIndex: 0 }]
        });
    } else if (sampler) {
        /* Fallback: show simple stats */
        var recv = sampler.receivedBytesPerSec || 0;
        var sent = sampler.sentBytesPerSec || 0;
        chartDom.innerHTML =
            '<div style="display:flex;flex-direction:column;align-items:center;justify-content:center;height:100%;gap:8px;font-size:0.85rem;">' +
                '<div>Received: <b>' + formatBytes(recv) + '/s</b></div>' +
                '<div>Sent: <b>' + formatBytes(sent) + '/s</b></div>' +
            '</div>';
    } else {
        showNoData(chartDom, 'No byte data', true);
    }
}

/* initDetailThroughput, initDetailErrorRate, initDetailConnectLatency, initDetailPercentiles
   — all now handled by DETAIL_CHART_RENDERERS + renderDetailTimeSeries() */

function formatBytes(bytes) {
    if (bytes >= 1048576) return (bytes / 1048576).toFixed(1) + ' MB';
    if (bytes >= 1024) return (bytes / 1024).toFixed(1) + ' KB';
    return bytes.toFixed(0) + ' B';
}

/* ============================================================
 *  Expandable Error Detail Rows
 * ============================================================ */

function toggleErrorDetail(row) {
    if (!row || !globalChartData) return;

    var idx = row.getAttribute('data-error-idx');
    if (idx == null) return;
    idx = parseInt(idx, 10);

    /* Check if detail row already exists */
    var existingDetail = row.nextElementSibling;
    if (existingDetail && existingDetail.classList.contains('error-detail-row')) {
        existingDetail.remove();
        return;
    }

    var errorRecords = globalChartData.errorRecords;
    if (!errorRecords || !errorRecords[idx]) return;

    var er = errorRecords[idx];

    var detailRow = document.createElement('tr');
    detailRow.className = 'error-detail-row';
    var td = document.createElement('td');
    td.setAttribute('colspan', row.cells.length);
    td.style.padding = '12px 16px';
    td.style.background = 'var(--color-bg, #f8fafc)';
    td.style.fontSize = '0.8rem';

    var html = '<div style="display:grid;grid-template-columns:1fr;gap:8px;max-width:100%;overflow:auto;">';
    if (er.url) {
        html += '<div><strong>URL:</strong> <span style="word-break:break-all;">' + escapeHtml(er.url) + '</span></div>';
    }
    if (er.headers || er.requestHeaders) {
        html += '<div><strong>Request Headers:</strong><pre style="margin:4px 0;padding:6px;background:var(--color-bg-card,#f1f5f9);border-radius:4px;font-size:0.75rem;overflow-x:auto;max-height:150px;">' + escapeHtml(er.headers || er.requestHeaders) + '</pre></div>';
    }
    if (er.responseHeaders) {
        html += '<div><strong>Response Headers:</strong><pre style="margin:4px 0;padding:6px;background:var(--color-bg-card,#f1f5f9);border-radius:4px;font-size:0.75rem;overflow-x:auto;max-height:150px;">' + escapeHtml(er.responseHeaders) + '</pre></div>';
    }
    if (er.body || er.responseBody) {
        var rawBody = er.body || er.responseBody;
        var truncated = rawBody.endsWith('...');
        var bodyId = 'error-body-' + idx;
        html += '<div class="response-body-panel">';
        html += '<div class="response-body-header"><strong>Response Body</strong>';
        if (truncated) html += ' <span class="body-truncated">(truncated)</span>';
        html += '<span class="response-body-actions">';
        html += '<button class="body-action-btn" onclick="copyResponseBody(\'' + bodyId + '\')" title="Copy to clipboard">Copy</button>';
        html += '<button class="body-action-btn" onclick="toggleBodyWrap(\'' + bodyId + '\')" title="Toggle word wrap">Wrap</button>';
        html += '<button class="body-action-btn" onclick="toggleBodyRaw(\'' + bodyId + '\')" title="Toggle raw/formatted">Raw</button>';
        html += '</span></div>';
        html += '<pre class="response-body-content" id="' + bodyId + '" data-raw="' + escapeHtml(rawBody).replace(/"/g, '&quot;') + '">' + formatAndHighlightBody(rawBody) + '</pre>';
        html += '</div>';
    }
    if (!er.url && !(er.headers || er.requestHeaders) && !(er.body || er.responseBody)) {
        html += '<div style="color:#64748b;">Response body not available (report generated from JTL file).<br/>For full error details, enable the Web Insight Report listener in your test plan.</div>';
    }
    html += '</div>';

    td.innerHTML = html;
    detailRow.appendChild(td);
    row.parentNode.insertBefore(detailRow, row.nextSibling);
}

/* ---- Response Body Viewer helpers ---- */

function formatAndHighlightBody(body) {
    if (!body) return '';
    try {
        var parsed = JSON.parse(body);
        var pretty = JSON.stringify(parsed, null, 2);
        return highlightJson(escapeHtml(pretty));
    } catch (e) {
        return escapeHtml(body);
    }
}

function highlightJson(escaped) {
    return escaped
        .replace(/(&quot;(?:[^&]|&(?!quot;))*?&quot;)\s*:/g, '<span class="json-key">$1</span>:')
        .replace(/:\s*(&quot;(?:[^&]|&(?!quot;))*?&quot;)/g, ': <span class="json-string">$1</span>')
        .replace(/:\s*(\d+\.?\d*)/g, ': <span class="json-number">$1</span>')
        .replace(/:\s*(true|false)/g, ': <span class="json-bool">$1</span>')
        .replace(/:\s*(null)/g, ': <span class="json-null">$1</span>');
}

function copyResponseBody(preId) {
    var pre = document.getElementById(preId);
    if (!pre) return;
    var raw = pre.getAttribute('data-raw');
    navigator.clipboard.writeText(raw).then(function () {
        showToast('Response body copied to clipboard');
    });
}

function toggleBodyWrap(preId) {
    var pre = document.getElementById(preId);
    if (!pre) return;
    var current = pre.style.whiteSpace;
    pre.style.whiteSpace = (current === 'pre-wrap') ? 'pre' : 'pre-wrap';
    pre.style.wordBreak = (current === 'pre-wrap') ? '' : 'break-all';
}

function toggleBodyRaw(preId) {
    var pre = document.getElementById(preId);
    if (!pre) return;
    var isRaw = pre.getAttribute('data-showing-raw') === 'true';
    if (isRaw) {
        var raw = pre.getAttribute('data-raw');
        pre.innerHTML = formatAndHighlightBody(raw);
        pre.setAttribute('data-showing-raw', 'false');
    } else {
        pre.innerHTML = escapeHtml(pre.getAttribute('data-raw'));
        pre.setAttribute('data-showing-raw', 'true');
    }
}

function escapeHtml(text) {
    if (!text) return '';
    return text.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;').replace(/"/g, '&quot;');
}

/* ============================================================
 *  Filter Presets (save/load/delete)
 * ============================================================ */

function getPresets() {
    try {
        var raw = localStorage.getItem(LS_PRESETS);
        if (raw) return JSON.parse(raw);
    } catch (e) { /* ignore */ }
    return {};
}

function setPresets(presets) {
    try {
        localStorage.setItem(LS_PRESETS, JSON.stringify(presets));
    } catch (e) { /* ignore */ }
}

function populatePresetSelect() {
    var select = document.getElementById('presetSelect');
    if (!select) return;

    /* Keep the first default option */
    while (select.options.length > 1) {
        select.remove(1);
    }

    var presets = getPresets();
    for (var name in presets) {
        var opt = document.createElement('option');
        opt.value = name;
        opt.textContent = name;
        select.appendChild(opt);
    }
}

function saveFilterPreset() {
    var name = prompt('Preset name:');
    if (!name || !name.trim()) return;
    name = name.trim();

    var preset = {
        samplerVisibility: JSON.parse(JSON.stringify(samplerVisibility)),
        currentMetric: currentMetric,
        currentPalette: currentPalette,
        lineThickness: lineThickness,
        hiddenColumns: JSON.parse(JSON.stringify(hiddenColumns))
    };

    var presets = getPresets();
    presets[name] = preset;
    setPresets(presets);
    populatePresetSelect();

    /* Select the newly saved preset */
    var select = document.getElementById('presetSelect');
    if (select) select.value = name;
}

function resetToDefaults() {
    /* Restore all samplers to visible */
    for (var s in samplerVisibility) {
        samplerVisibility[s] = true;
    }
    /* Apply default hidden samplers from chart data */
    if (globalChartData && globalChartData.hiddenSamplers) {
        globalChartData.hiddenSamplers.forEach(function (h) {
            if (samplerVisibility.hasOwnProperty(h)) {
                samplerVisibility[h] = false;
            }
        });
    }

    /* Reset metric to default */
    currentMetric = 'responseTime';
    var toggle = document.getElementById('percentileToggle');
    if (toggle) {
        toggle.querySelectorAll('.filter-toggle-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-metric') === 'responseTime');
        });
    }

    /* Reset palette to default */
    currentPalette = 'default';
    CHART_COLORS = PALETTES['default'];
    customColors = {};
    var palToggle = document.getElementById('paletteToggle');
    if (palToggle) {
        palToggle.querySelectorAll('.filter-toggle-btn').forEach(function (btn) {
            btn.classList.toggle('active', btn.getAttribute('data-palette') === 'default');
        });
    }

    /* Reset line thickness */
    lineThickness = 2;
    var ltToggle = document.getElementById('lineThicknessToggle');
    if (ltToggle) {
        ltToggle.querySelectorAll('.filter-toggle-btn').forEach(function (btn) {
            btn.classList.toggle('active', parseInt(btn.getAttribute('data-thickness'), 10) === 2);
        });
    }

    /* Reset column visibility */
    hiddenColumns = {};
    applyColumnVisibility();
    initColumnMenu();

    saveSettings();
    if (globalChartData) initFilterBar(globalChartData);
    refreshAllCharts();
}

function loadFilterPreset(name) {
    if (!name) {
        resetToDefaults();
        return;
    }

    var presets = getPresets();
    var preset = presets[name];
    if (!preset) return;

    /* Apply sampler visibility */
    if (preset.samplerVisibility) {
        samplerVisibility = JSON.parse(JSON.stringify(preset.samplerVisibility));
    }

    /* Apply metric */
    if (preset.currentMetric) {
        currentMetric = preset.currentMetric;
        var toggle = document.getElementById('percentileToggle');
        if (toggle) {
            toggle.querySelectorAll('.filter-toggle-btn').forEach(function (btn) {
                btn.classList.toggle('active', btn.getAttribute('data-metric') === currentMetric);
            });
        }
    }

    /* Apply palette */
    if (preset.currentPalette && PALETTES[preset.currentPalette]) {
        currentPalette = preset.currentPalette;
        CHART_COLORS = PALETTES[currentPalette];
        var palToggle = document.getElementById('paletteToggle');
        if (palToggle) {
            palToggle.querySelectorAll('.filter-toggle-btn').forEach(function (btn) {
                btn.classList.toggle('active', btn.getAttribute('data-palette') === currentPalette);
            });
        }
    }

    /* Apply line thickness */
    if (preset.lineThickness) {
        lineThickness = preset.lineThickness;
        var ltToggle = document.getElementById('lineThicknessToggle');
        if (ltToggle) {
            ltToggle.querySelectorAll('.filter-toggle-btn').forEach(function (btn) {
                btn.classList.toggle('active', parseInt(btn.getAttribute('data-thickness'), 10) === lineThickness);
            });
        }
    }

    /* Apply hidden columns */
    if (preset.hiddenColumns) {
        hiddenColumns = JSON.parse(JSON.stringify(preset.hiddenColumns));
        applyColumnVisibility();
        initColumnMenu(); // refresh checkboxes
    }

    saveSettings();

    /* Rebuild filter bar and refresh charts */
    if (globalChartData) initFilterBar(globalChartData);
    refreshAllCharts();
}

function deleteFilterPreset() {
    var select = document.getElementById('presetSelect');
    if (!select || !select.value) {
        alert('Select a preset to delete.');
        return;
    }

    var name = select.value;
    var presets = getPresets();
    delete presets[name];
    setPresets(presets);
    populatePresetSelect();
}

/* ============================================================
 *  Time Range Selector (sync with chart dataZoom)
 * ============================================================ */

function initTimeRangeSelector(data) {
    if (!data.timestamps || data.timestamps.length === 0) return;
    visibleTimeRangeMs = getTestDurationMs();
    updateTimeRangeDisplay();
}

function bindDataZoomToTimeRange(chart) {
    chart.on('datazoom', function (params) {
        updateTimeRangeFromChart(chart);
    });
}

function updateTimeRangeFromChart(chart) {
    try {
        var option = chart.getOption();
        if (!option || !option.dataZoom || option.dataZoom.length === 0) return;
        var zoom = option.dataZoom[0];
        if (zoom.startValue != null && zoom.endValue != null) {
            visibleTimeRangeMs = zoom.endValue - zoom.startValue;
        }
    } catch (e) { /* ignore */ }
    updateTimeRangeDisplay();
}

function resetTimeRange() {
    if (!globalChartData || !globalChartData.timestamps || globalChartData.timestamps.length === 0) return;

    visibleTimeRangeMs = getTestDurationMs();

    /* Reset all chart dataZooms */
    chartInstances.forEach(function (c) {
        try {
            c.dispatchAction({
                type: 'dataZoom',
                start: 0,
                end: 100
            });
        } catch (e) { /* ignore */ }
    });

    /* Reset input display */
    initTimeRangeSelector(globalChartData);
}

/* ============================================================
 *  Copy Summary as HTML (for Confluence/SharePoint)
 * ============================================================ */

function copySummaryAsHtml() {
    var summaryCards = document.querySelectorAll('#tab-summary .summary-card');
    var rows = '';
    summaryCards.forEach(function (card) {
        var value = card.querySelector('.card-value');
        var label = card.querySelector('.card-label');
        if (value && label) {
            rows += '<tr><td style="padding:4px 12px;font-weight:bold;">' + label.textContent.trim() + '</td>' +
                    '<td style="padding:4px 12px;">' + value.textContent.trim() + '</td></tr>';
        }
    });

    /* Top 5 slowest samplers */
    var top5Rows = document.querySelectorAll('#tab-summary .top-sampler-row');
    var top5Html = '';
    if (top5Rows.length > 0) {
        top5Html = '<h3>Top 5 Slowest Samplers</h3><table border="1" cellpadding="4" cellspacing="0" style="border-collapse:collapse;">' +
            '<tr><th>#</th><th>Sampler</th><th>P95</th><th>Mean</th></tr>';
        top5Rows.forEach(function (row) {
            var rank = row.querySelector('.top-sampler-rank');
            var name = row.querySelector('.top-sampler-name');
            var value = row.querySelector('.top-sampler-value');
            var sub = row.querySelector('.top-sampler-subvalue');
            top5Html += '<tr>' +
                '<td>' + (rank ? rank.textContent.trim() : '') + '</td>' +
                '<td>' + (name ? name.textContent.trim() : '') + '</td>' +
                '<td>' + (value ? value.textContent.trim() : '') + '</td>' +
                '<td>' + (sub ? sub.textContent.trim() : '') + '</td></tr>';
        });
        top5Html += '</table>';
    }

    var html = '<h2>Test Summary</h2>' +
        '<table border="1" cellpadding="4" cellspacing="0" style="border-collapse:collapse;">' +
        rows + '</table>' + top5Html;

    if (navigator.clipboard && navigator.clipboard.write) {
        var blob = new Blob([html], { type: 'text/html' });
        var item = new ClipboardItem({ 'text/html': blob });
        navigator.clipboard.write([item]).then(function () {
            showCopyFeedback('Copied as HTML!');
        }).catch(function () {
            fallbackCopyText(html);
        });
    } else {
        fallbackCopyText(html);
    }
}

function fallbackCopyText(text) {
    var ta = document.createElement('textarea');
    ta.value = text;
    ta.style.position = 'fixed';
    ta.style.left = '-9999px';
    document.body.appendChild(ta);
    ta.select();
    try { document.execCommand('copy'); showCopyFeedback('Copied!'); }
    catch (e) { alert('Copy failed.'); }
    document.body.removeChild(ta);
}

function showCopyFeedback(message) {
    var btn = document.querySelector('#tab-summary [onclick*="copySummaryAsHtml"]');
    if (!btn) return;
    var orig = btn.textContent;
    btn.textContent = message;
    setTimeout(function () { btn.textContent = orig; }, 2000);
}

/* ============================================================
 *  Drag-and-Drop Column Reorder
 * ============================================================ */

var dragSrcColIdx = null;
var dragSrcTableId = null;

function initColumnDragDrop() {
    initColumnDragDropForTable('samplerTable', 'wir_columnOrder');
    initColumnDragDropForTable('comparisonTable', 'wir_compareColumnOrder');
}

function initColumnDragDropForTable(tableId, storageKey) {
    var table = document.getElementById(tableId);
    if (!table || !table.tHead) return;

    var savedOrder = null;
    try { var co = localStorage.getItem(storageKey); if (co) savedOrder = JSON.parse(co); } catch (e) { /* ignore */ }

    var headers = table.tHead.rows[0].cells;
    for (var i = 0; i < headers.length; i++) {
        (function (idx) {
            var th = headers[idx];
            if (!th.getAttribute('draggable')) return;

            th.addEventListener('dragstart', function (e) {
                dragSrcColIdx = idx;
                dragSrcTableId = tableId;
                e.dataTransfer.effectAllowed = 'move';
                e.dataTransfer.setData('text/plain', String(idx));
                th.style.opacity = '0.5';
            });

            th.addEventListener('dragover', function (e) {
                if (dragSrcTableId !== tableId) return;
                e.preventDefault();
                e.dataTransfer.dropEffect = 'move';
                th.style.borderLeft = '2px solid #5470c6';
            });

            th.addEventListener('dragleave', function () {
                th.style.borderLeft = '';
            });

            th.addEventListener('drop', function (e) {
                e.preventDefault();
                th.style.borderLeft = '';
                if (dragSrcTableId !== tableId) return;
                var fromIdx = dragSrcColIdx;
                var toIdx = idx;
                if (fromIdx === null || fromIdx === toIdx) return;
                reorderColumns(table, fromIdx, toIdx, storageKey);
            });

            th.addEventListener('dragend', function () {
                th.style.opacity = '';
                th.style.borderLeft = '';
            });
        })(i);
    }

    if (savedOrder && Array.isArray(savedOrder)) {
        applyColumnOrder(table, savedOrder);
    }
}

function reorderColumns(table, fromIdx, toIdx, storageKey) {
    /* Reorder header */
    var headerRow = table.tHead.rows[0];
    moveCell(headerRow, fromIdx, toIdx);

    /* Reorder body rows */
    var rows = table.tBodies[0].rows;
    for (var i = 0; i < rows.length; i++) {
        if (!rows[i].classList.contains('sampler-detail-row') && !rows[i].classList.contains('error-summary-total')) {
            moveCell(rows[i], fromIdx, toIdx);
        }
    }

    /* Track column order */
    var order = [];
    for (var c = 0; c < headerRow.cells.length; c++) order.push(c);
    // Reconstruct from saved or build fresh
    var sKey = storageKey || LS_COL_ORDER;
    try {
        var saved = localStorage.getItem(sKey);
        if (saved) order = JSON.parse(saved);
    } catch (e) { /* ignore */ }
    if (order.length !== headerRow.cells.length) {
        order = [];
        for (var cc = 0; cc < headerRow.cells.length; cc++) order.push(cc);
    }
    var item = order.splice(fromIdx, 1)[0];
    order.splice(toIdx, 0, item);

    if (sKey === LS_COL_ORDER) columnOrder = order;
    try { localStorage.setItem(sKey, JSON.stringify(order)); } catch (e) { /* ignore */ }
}

function moveCell(row, fromIdx, toIdx) {
    if (fromIdx >= row.cells.length || toIdx >= row.cells.length) return;
    var cell = row.cells[fromIdx];
    var refCell = toIdx < fromIdx ? row.cells[toIdx] : row.cells[toIdx + 1] || null;
    row.insertBefore(cell, refCell);
}

function applyColumnOrder(table, order) {
    if (!order || !Array.isArray(order)) return;
    /* Validate order array */
    var headerRow = table.tHead.rows[0];
    if (order.length !== headerRow.cells.length) return;

    /* Build new order by moving columns one at a time */
    for (var pos = 0; pos < order.length; pos++) {
        var currentCells = Array.from(headerRow.cells);
        var sourceIdx = -1;
        for (var s = 0; s < currentCells.length; s++) {
            if (currentCells[s]._origIdx === order[pos] || (!currentCells[s]._origIdx && s === order[pos])) {
                sourceIdx = s;
                break;
            }
        }
        if (sourceIdx >= 0 && sourceIdx !== pos) {
            moveCell(headerRow, sourceIdx, pos);
            var rows = table.tBodies[0].rows;
            for (var r = 0; r < rows.length; r++) {
                if (!rows[r].classList.contains('sampler-detail-row')) {
                    moveCell(rows[r], sourceIdx, pos);
                }
            }
        }
    }
}

/* ============================================================
 *  Comparison Tab
 * ============================================================ */

function initComparisonTab(data) {
    if (!data.hasComparison) return;

    var tabBtn = document.getElementById('compareTabBtn');
    if (tabBtn) tabBtn.style.display = '';

    /* Initialize threshold controls from chartData */
    var thSection = document.getElementById('comparison-thresholds-section');
    if (thSection && data.comparisonThresholds) {
        thSection.style.display = '';
        var th = data.comparisonThresholds;
        document.getElementById('thP95').value = th.p95PctChange != null ? th.p95PctChange : 10;
        document.getElementById('thErrorRate').value = th.errorRateChange != null ? th.errorRateChange : 2;
        document.getElementById('thMean').value = th.meanPctChange != null ? th.meanPctChange : -1;
        document.getElementById('thP99').value = th.p99PctChange != null ? th.p99PctChange : -1;
        document.getElementById('thThroughput').value = th.throughputPctChange != null ? th.throughputPctChange : -1;
    }

    pendingChartInits.push({ tab: 'compare', fn: function () {
        initCompareRTChart(data);
        initCompareTPChart(data);
        populateComparisonTable(data);
    }});
}

function applyComparisonThresholds() {
    if (!globalChartData || !globalChartData.comparisonDiffs) return;

    var p95Th = parseFloat(document.getElementById('thP95').value);
    var errTh = parseFloat(document.getElementById('thErrorRate').value);
    var meanTh = parseFloat(document.getElementById('thMean').value);
    var p99Th = parseFloat(document.getElementById('thP99').value);
    var tpTh = parseFloat(document.getElementById('thThroughput').value);

    /* Re-evaluate regression flags client-side */
    globalChartData.comparisonDiffs.forEach(function (diff) {
        if (diff.isNew) { diff.regression = false; return; }

        var regressed = false;

        if (p95Th >= 0 && diff.p95PctChange != null) {
            if (diff.p95PctChange > p95Th) regressed = true;
        }
        if (errTh >= 0 && diff.deltaErrorRate != null) {
            if (diff.deltaErrorRate > errTh) regressed = true;
        }
        if (meanTh >= 0 && diff.meanPctChange != null) {
            if (diff.meanPctChange > meanTh) regressed = true;
        }
        if (p99Th >= 0) {
            var p99Pct = diff.currP99 && diff.deltaP99 && (diff.currP99 - diff.deltaP99) > 0
                ? (diff.deltaP99 / (diff.currP99 - diff.deltaP99)) * 100.0 : 0;
            if (p99Pct > p99Th) regressed = true;
        }
        if (tpTh >= 0 && diff.deltaThroughput != null && diff.currThroughput != null) {
            var baseTp = diff.currThroughput - diff.deltaThroughput;
            var tpPct = baseTp > 0 ? ((baseTp - diff.currThroughput) / baseTp) * 100.0 : 0;
            if (tpPct > tpTh) regressed = true;
        }

        diff.regression = regressed;
    });

    /* Re-render comparison table and What Changed summary */
    populateComparisonTable(globalChartData);
    initWhatChanged(globalChartData);
}

/** Build elapsed-aligned [elapsed_ms, value] pairs from timestamps + values */
function zipElapsed(timestamps, values, startTs) {
    var pairs = [];
    for (var i = 0; i < timestamps.length && i < values.length; i++) {
        if (values[i] != null) pairs.push([timestamps[i] - startTs, values[i]]);
    }
    return pairs;
}

/** Build per-sampler comparison series (current=solid, baseline=dashed) */
function buildComparisonSeries(data, metric) {
    var series = [];
    var visibleNames = getVisibleSamplerNames();
    var samplerNames = data.samplers ? data.samplers.map(function (s) { return s.name; }) : [];
    var currStart = data.timestamps[0];
    var baseStart = data.baseline.timestamps[0];

    var currPerSampler = data.perSamplerSeries || {};
    var basePerSampler = data.baseline.perSamplerSeries || {};

    visibleNames.forEach(function (name) {
        var sIdx = samplerNames.indexOf(name);
        if (sIdx < 0) sIdx = 0;
        var color = getColorForSampler(name, sIdx);

        /* Current run — solid */
        if (currPerSampler[name] && currPerSampler[name][metric]) {
            series.push({
                name: name + ' (Current)',
                type: 'line',
                data: zipElapsed(data.timestamps, currPerSampler[name][metric], currStart),
                smooth: true,
                symbol: 'none',
                lineStyle: { width: lineThickness },
                itemStyle: { color: color }
            });
        }

        /* Baseline run — dashed, dimmed */
        if (basePerSampler[name] && basePerSampler[name][metric]) {
            series.push({
                name: name + ' (Baseline)',
                type: 'line',
                data: zipElapsed(data.baseline.timestamps, basePerSampler[name][metric], baseStart),
                smooth: true,
                symbol: 'none',
                lineStyle: { width: lineThickness, type: 'dashed', opacity: 0.6 },
                itemStyle: { color: color, opacity: 0.6 }
            });
        }
    });

    /* Total series */
    if (isTotalVisible()) {
        var totalColor = getTotalColor();
        var currGlobal = metric === 'throughput' ? data.throughputs : data.meanResponseTimes;
        var baseGlobal = metric === 'throughput' ? data.baseline.throughputs : data.baseline.meanResponseTimes;

        if (currGlobal) {
            series.push({
                name: TOTAL_SAMPLER_NAME + ' (Current)',
                type: 'line',
                data: zipElapsed(data.timestamps, currGlobal, currStart),
                smooth: true, symbol: 'none',
                lineStyle: { width: lineThickness + 1 },
                itemStyle: { color: totalColor }
            });
        }
        if (baseGlobal) {
            series.push({
                name: TOTAL_SAMPLER_NAME + ' (Baseline)',
                type: 'line',
                data: zipElapsed(data.baseline.timestamps, baseGlobal, baseStart),
                smooth: true, symbol: 'none',
                lineStyle: { width: lineThickness + 1, type: 'dashed', opacity: 0.6 },
                itemStyle: { color: totalColor, opacity: 0.6 }
            });
        }
    }

    return series;
}

function compareElapsedFormatter(value) {
    return formatElapsed(value);
}

function compareTooltipFormatter(params) {
    var elapsed = formatElapsed(params[0].value[0]);
    var str = elapsed + '<br/>';
    params.forEach(function (p) {
        str += p.marker + ' ' + p.seriesName + ': <b>' + formatResponseTime(p.value[1]) + '</b><br/>';
    });
    return str;
}

function compareTpTooltipFormatter(params) {
    var elapsed = formatElapsed(params[0].value[0]);
    var str = elapsed + '<br/>';
    params.forEach(function (p) {
        str += p.marker + ' ' + p.seriesName + ': <b>' + p.value[1].toFixed(1) + '/s</b><br/>';
    });
    return str;
}

function getCompareElapsedMax(data) {
    var currDur = data.timestamps[data.timestamps.length - 1] - data.timestamps[0];
    var baseDur = data.baseline.timestamps[data.baseline.timestamps.length - 1] - data.baseline.timestamps[0];
    return Math.max(currDur, baseDur);
}

function initCompareRTChart(data) {
    var chartDom = document.getElementById('compare-rt-chart');
    if (!chartDom || !data.baseline) return;

    disposeChartIn(chartDom);
    chartDom.style.height = '400px';
    chartDom.innerHTML = '';
    var chart = createChart(chartDom);

    var metric = currentMetric || 'responseTime';
    var series = buildComparisonSeries(data, metric);

    if (series.length === 0) {
        disposeLastChart(chart);
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No comparison data for selected samplers.');
        return;
    }

    var maxElapsed = getCompareElapsedMax(data);

    chart.setOption({
        toolbox: getToolbox(),
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: compareTooltipFormatter },
        legend: { data: series.map(function (s) { return s.name; }), bottom: 10, type: 'scroll', formatter: truncateLegendName, tooltip: { show: true } },
        grid: { left: 60, right: 30, top: 40, bottom: 90 },
        xAxis: { type: 'value', name: 'Elapsed', axisLabel: { formatter: compareElapsedFormatter }, min: 0, max: maxElapsed },
        yAxis: responseTimeYAxis(getMaxFromSeries(series), 'Response Time'),
        series: series,
        dataZoom: getDataZoom()
    });
}

function initCompareTPChart(data) {
    var chartDom = document.getElementById('compare-tp-chart');
    if (!chartDom || !data.baseline) return;

    disposeChartIn(chartDom);
    chartDom.style.height = '400px';
    chartDom.innerHTML = '';
    var chart = createChart(chartDom);

    var series = buildComparisonSeries(data, 'throughput');

    if (series.length === 0) {
        disposeLastChart(chart);
        chartDom.style.height = '80px';
        showNoData(chartDom, 'No throughput comparison data for selected samplers.');
        return;
    }

    var maxElapsed = getCompareElapsedMax(data);

    chart.setOption({
        toolbox: getToolbox(),
        tooltip: { trigger: 'axis', axisPointer: { type: 'cross' }, formatter: compareTpTooltipFormatter },
        legend: { data: series.map(function (s) { return s.name; }), bottom: 10, type: 'scroll', formatter: truncateLegendName, tooltip: { show: true } },
        grid: { left: 60, right: 30, top: 40, bottom: 90 },
        xAxis: { type: 'value', name: 'Elapsed', axisLabel: { formatter: compareElapsedFormatter }, min: 0, max: maxElapsed },
        yAxis: { type: 'value', name: 'Requests/sec', min: 0 },
        series: series,
        dataZoom: getDataZoom()
    });
}

function populateComparisonTable(data) {
    var tbody = document.getElementById('comparisonTableBody');
    if (!tbody || !data.comparisonDiffs) return;

    tbody.innerHTML = '';
    data.comparisonDiffs.forEach(function (diff) {
        var tr = document.createElement('tr');

        var statusClass = diff.isNew ? 'badge-info' : (diff.regression ? 'badge-fail' : 'badge-pass');
        var statusText = diff.isNew ? 'NEW' : (diff.regression ? 'REGRESSION' : 'OK');

        var escapedName = escapeHtml(diff.name);
        tr.innerHTML =
            '<td title="' + escapedName + '">' + escapedName + '</td>' +
            '<td><span class="status-badge ' + statusClass + '" style="font-size:0.65rem;padding:2px 8px;">' + statusText + '</span></td>' +
            '<td>' + (diff.currSamples || '\u2014') + inlineDelta(diff.deltaSamples, '') + '</td>' +
            '<td>' + (diff.currErrors || 0) + inlineDelta(diff.deltaErrors, '') + '</td>' +
            '<td>' + diff.currErrorRate + '%' + inlineDelta(diff.deltaErrorRate, '%') + '</td>' +
            '<td>' + formatCellMs(diff.currMean) + inlineDelta(diff.deltaMean, ' ms') + '</td>' +
            '<td>' + formatCellMs(diff.currMedian) + inlineDelta(diff.deltaMedian, ' ms') + '</td>' +
            '<td>' + formatCellMs(diff.currP90) + inlineDelta(diff.deltaP90, ' ms') + '</td>' +
            '<td>' + formatCellMs(diff.currP95) + inlineDelta(diff.deltaP95, ' ms') + '</td>' +
            '<td>' + formatCellMs(diff.currP99) + inlineDelta(diff.deltaP99, ' ms') + '</td>' +
            '<td>' + formatCellMs(diff.currMin) + inlineDelta(diff.deltaMin, ' ms') + '</td>' +
            '<td>' + formatCellMs(diff.currMax) + inlineDelta(diff.deltaMax, ' ms') + '</td>' +
            '<td>' + (diff.currThroughput != null ? diff.currThroughput.toFixed(1) + '/s' : '\u2014') + inlineDelta(diff.deltaThroughput, '/s', true) + '</td>' +
            '<td>' + (diff.currRecvKBs != null ? diff.currRecvKBs.toFixed(1) : '\u2014') + inlineDelta(diff.deltaRecvKBs, '', true) + '</td>' +
            '<td><button class="row-hide-btn" onclick="hideTableRow(this, \'comparisonTable\'); event.stopPropagation();" title="Hide row">&times;</button></td>';

        tbody.appendChild(tr);
    });
}

function inlineDelta(val, suffix, invertColor) {
    if (val == null || val === 0) return '';
    var text;
    if (suffix === ' ms') {
        var abs = Math.abs(val);
        if (abs < 1000) text = Math.round(val);
        else if (abs < 60000) text = (val / 1000).toFixed(1) + 's';
        else { var m = Math.floor(abs / 60000); var s = Math.round((abs % 60000) / 1000); text = (val < 0 ? '-' : '') + m + 'm' + s + 's'; }
    } else if (suffix === '') {
        text = Math.round(val);
    } else {
        text = val.toFixed(1) + suffix;
    }
    if (typeof text === 'number' || !String(text).match(/^[-]/)) {
        text = (val > 0 ? '+' : '') + text;
    }
    var bad = invertColor ? (val < 0) : (val > 0);
    var cls = bad ? 'delta-bad' : 'delta-good';
    return '<sup class="' + cls + '">' + text + '</sup>';
}

function formatCellMs(val) {
    if (val == null || isNaN(val)) return '0';
    if (val < 1000) return val.toFixed(0);
    if (val < 60000) return (val / 1000).toFixed(1) + 's';
    var m = Math.floor(val / 60000);
    var s = Math.round((val % 60000) / 1000);
    return m + 'm ' + s + 's';
}

/* ============================================================
 *  What Changed (vs Baseline) — auto-generated summary
 * ============================================================ */

function initWhatChanged(data) {
    var section = document.getElementById('whatChangedSection');
    var contentDiv = document.getElementById('whatChangedContent');
    if (!section || !contentDiv) return;
    if (!data.hasComparison || !data.comparisonDiffs || data.comparisonDiffs.length === 0) return;

    var diffs = data.comparisonDiffs;

    /* Categorize samplers */
    var regressions = [];
    var improvements = [];
    var newSamplers = [];
    var nonNewDiffs = [];

    diffs.forEach(function (diff) {
        if (diff.isNew) {
            newSamplers.push(diff);
            return;
        }
        nonNewDiffs.push(diff);
        if (diff.regression) {
            regressions.push(diff);
        }
        if (diff.p95PctChange != null && diff.p95PctChange < -5) {
            improvements.push(diff);
        }
    });

    /* Sort regressions: highest p95PctChange first */
    regressions.sort(function (a, b) {
        return (b.p95PctChange || 0) - (a.p95PctChange || 0);
    });

    /* Sort improvements: most negative p95PctChange first (biggest improvement) */
    improvements.sort(function (a, b) {
        return (a.p95PctChange || 0) - (b.p95PctChange || 0);
    });

    /* Compute overall average P95 change across non-new samplers */
    var p95Sum = 0;
    var p95Count = 0;
    nonNewDiffs.forEach(function (diff) {
        if (diff.p95PctChange != null) {
            p95Sum += diff.p95PctChange;
            p95Count++;
        }
    });
    var avgP95Change = p95Count > 0 ? p95Sum / p95Count : 0;

    /* Compute overall error rate change */
    var errSum = 0;
    var errCount = 0;
    nonNewDiffs.forEach(function (diff) {
        if (diff.deltaErrorRate != null) {
            errSum += diff.deltaErrorRate;
            errCount++;
        }
    });
    var avgErrorChange = errCount > 0 ? errSum / errCount : 0;

    /* Truncate sampler name if too long */
    function truncateName(name, maxLen) {
        if (!name) return '';
        maxLen = maxLen || 40;
        if (name.length <= maxLen) return name;
        return name.substring(0, maxLen - 1) + '\u2026';
    }

    /* Format a signed percentage */
    function fmtPct(val) {
        if (val == null) return '';
        var sign = val > 0 ? '+' : '';
        return sign + val.toFixed(1) + '%';
    }

    /* Build HTML */
    var html = '';

    /* Overall line */
    var p95Word = avgP95Change < -2 ? 'improved' : (avgP95Change > 2 ? 'degraded' : 'stable');
    var p95Desc = p95Word === 'stable'
        ? 'P95 stable (' + fmtPct(avgP95Change) + ')'
        : 'P95 ' + p95Word + ' ' + Math.abs(avgP95Change).toFixed(0) + '% on average';

    var errWord = Math.abs(avgErrorChange) < 0.5 ? 'stable' : (avgErrorChange > 0 ? 'increased' : 'decreased');
    var errDesc = 'Error rate ' + errWord + ' (' + fmtPct(avgErrorChange) + ')';

    html += '<div class="what-changed-line"><strong>Overall:</strong> ' + escapeHtml(p95Desc) + '. ' + escapeHtml(errDesc) + '.</div>';

    /* Regressions */
    if (regressions.length > 0) {
        html += '<div class="what-changed-line what-changed-regression">';
        html += '<span class="status-badge badge-fail" style="font-size:0.65rem;padding:2px 8px;margin-right:6px;">Regressions (' + regressions.length + ')</span>';
        var topReg = regressions.slice(0, 3);
        var regNames = topReg.map(function (d) {
            return escapeHtml(truncateName(d.name)) + ' (' + fmtPct(d.p95PctChange) + ' P95)';
        });
        html += regNames.join(', ');
        if (regressions.length > 3) html += ', \u2026and ' + (regressions.length - 3) + ' more';
        html += '</div>';
    }

    /* Improvements */
    if (improvements.length > 0) {
        html += '<div class="what-changed-line what-changed-improvement">';
        html += '<span class="status-badge badge-pass" style="font-size:0.65rem;padding:2px 8px;margin-right:6px;">Improvements (' + improvements.length + ')</span>';
        var topImp = improvements.slice(0, 3);
        var impNames = topImp.map(function (d) {
            return escapeHtml(truncateName(d.name)) + ' (' + fmtPct(d.p95PctChange) + ' P95)';
        });
        html += impNames.join(', ');
        if (improvements.length > 3) html += ', \u2026and ' + (improvements.length - 3) + ' more';
        html += '</div>';
    }

    /* New samplers */
    if (newSamplers.length > 0) {
        html += '<div class="what-changed-line what-changed-new">';
        html += '<span class="status-badge badge-info" style="font-size:0.65rem;padding:2px 8px;margin-right:6px;">New samplers (' + newSamplers.length + ')</span>';
        var topNew = newSamplers.slice(0, 3);
        var newNames = topNew.map(function (d) {
            return escapeHtml(truncateName(d.name));
        });
        html += newNames.join(', ');
        if (newSamplers.length > 3) html += ', \u2026and ' + (newSamplers.length - 3) + ' more';
        html += '</div>';
    }

    /* No regressions / no improvements edge cases */
    if (regressions.length === 0 && improvements.length === 0 && newSamplers.length === 0) {
        html += '<div class="what-changed-line" style="color:var(--color-text-secondary);">No significant changes detected.</div>';
    }

    contentDiv.innerHTML = html;
    section.style.display = '';
}

/* ============================================================
 *  Minimal Markdown Renderer
 * ============================================================ */

function renderMarkdown(text) {
    if (!text) return '';
    var html = text
        // Escape HTML
        .replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;')
        // Code blocks (``` ... ```)
        .replace(/```([\s\S]*?)```/g, '<pre><code>$1</code></pre>')
        // Inline code
        .replace(/`([^`]+)`/g, '<code>$1</code>')
        // Headers
        .replace(/^### (.+)$/gm, '<h3>$1</h3>')
        .replace(/^## (.+)$/gm, '<h2>$1</h2>')
        .replace(/^# (.+)$/gm, '<h1>$1</h1>')
        // Bold
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        // Italic
        .replace(/\*(.+?)\*/g, '<em>$1</em>')
        // Unordered lists
        .replace(/^- (.+)$/gm, '<li>$1</li>')
        // Wrap consecutive <li> in <ul>
        .replace(/((?:<li>.*<\/li>\n?)+)/g, '<ul>$1</ul>')
        // Line breaks -> paragraphs
        .replace(/\n\n/g, '</p><p>')
        .replace(/\n/g, '<br/>');

    return '<p>' + html + '</p>';
}

function initNotesTab() {
    // Render pre-loaded markdown content
    var notesDiv = document.getElementById('notesContent');
    if (notesDiv) {
        var md = notesDiv.getAttribute('data-markdown');
        if (md) {
            notesDiv.innerHTML = renderMarkdown(md);
        }
    }
}

function initSummaryMarkdown() {
    /* Render markdown in summary notes section */
    var summaryNotes = document.querySelectorAll('.engineer-notes-summary .markdown-content[data-markdown]');
    summaryNotes.forEach(function (el) {
        var md = el.getAttribute('data-markdown');
        if (md && !el.innerHTML.trim()) {
            el.innerHTML = renderMarkdown(md);
        }
    });
}

function toggleNotesPreview() {
    var textarea = document.getElementById('notesTextarea');
    var preview = document.getElementById('notesPreview');
    if (!textarea || !preview) return;

    if (preview.style.display === 'none') {
        preview.innerHTML = renderMarkdown(textarea.value);
        preview.style.display = 'block';
        textarea.style.display = 'none';
    } else {
        preview.style.display = 'none';
        textarea.style.display = 'block';
    }
}

function downloadAnnotations() {
    var textarea = document.getElementById('notesTextarea');
    var notes = textarea ? textarea.value : '';
    var annotations = {
        version: '1.0',
        testNotes: notes,
        verdict: '',
        timelineMarkers: [],
        samplerNotes: {}
    };

    var blob = new Blob([JSON.stringify(annotations, null, 2)], { type: 'application/json' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = 'report-annotations.json';
    a.click();
    URL.revokeObjectURL(a.href);
}


/* ============================================================
 *  Row hide/show
 * ============================================================ */

var hiddenRowCount = 0;

function hideRow(btn) {
    var row = btn.closest('tr');
    if (!row) return;
    /* Close any expanded detail row first */
    var next = row.nextElementSibling;
    if (next && next.classList.contains('sampler-detail-row')) {
        next.remove();
    }
    row.style.display = 'none';
    hiddenRowCount++;
    updateHiddenRowsBtn();
}

function showAllRows() {
    var table = document.getElementById('samplerTable');
    if (!table) return;
    var rows = table.tBodies[0].rows;
    for (var i = 0; i < rows.length; i++) {
        /* Don't show collapsed transaction children */
        if (rows[i].classList.contains('transaction-child')) {
            var samplerName = rows[i].getAttribute('data-sampler');
            if (samplerName && globalChartData && globalChartData.transactionHierarchy) {
                /* Check if parent is expanded */
                var parentExpanded = false;
                for (var pName in globalChartData.transactionHierarchy) {
                    var children = globalChartData.transactionHierarchy[pName];
                    if (Array.isArray(children) && children.indexOf(samplerName) >= 0) {
                        var parentRow = table.querySelector('tr[data-sampler="' + pName + '"] .tc-icon');
                        if (parentRow && parentRow.getAttribute('data-expanded') === 'true') {
                            parentExpanded = true;
                        }
                        break;
                    }
                }
                if (!parentExpanded) continue;
            }
        }
        if (!rows[i].classList.contains('sampler-detail-row')) {
            rows[i].style.display = '';
        }
    }
    hiddenRowCount = 0;
    updateHiddenRowsBtn();
}

function updateHiddenRowsBtn() {
    var btn = document.getElementById('showHiddenRowsBtn');
    if (!btn) return;
    if (hiddenRowCount > 0) {
        btn.style.display = '';
        btn.textContent = 'Show ' + hiddenRowCount + ' Hidden Row' + (hiddenRowCount > 1 ? 's' : '');
    } else {
        btn.style.display = 'none';
    }
}

/* ============================================================
 *  Generic Table Column Toggle, Row Hide & Filter
 * ============================================================ */

var genericTableState = {};

function getTableState(tableId) {
    if (!genericTableState[tableId]) {
        genericTableState[tableId] = { hiddenCols: {}, hiddenRowCount: 0 };
    }
    return genericTableState[tableId];
}

function toggleGenericColumnMenu(btn) {
    var wrapper = btn.parentElement;
    var menu = wrapper.querySelector('.column-menu');
    if (!menu) return;

    var isOpen = menu.classList.contains('open');

    /* Close all open generic column menus first */
    document.querySelectorAll('.column-menu.open').forEach(function (m) { m.classList.remove('open'); });

    if (isOpen) return;

    /* Find the associated table */
    var section = wrapper.closest('.report-section');
    if (!section) return;
    var table = section.querySelector('table.stats-table');
    if (!table) return;
    var tableId = table.id;
    var state = getTableState(tableId);

    /* Build menu from header cells (skip last action column(s)) */
    menu.innerHTML = '';
    var headerCells = table.tHead.rows[0].cells;
    for (var i = 0; i < headerCells.length; i++) {
        var text = headerCells[i].textContent.trim();
        if (!text) continue; /* skip action columns */
        (function (colIdx, colText) {
            var label = document.createElement('label');
            var cb = document.createElement('input');
            cb.type = 'checkbox';
            cb.checked = !state.hiddenCols[colIdx];
            cb.addEventListener('change', function () {
                if (cb.checked) {
                    delete state.hiddenCols[colIdx];
                } else {
                    state.hiddenCols[colIdx] = true;
                }
                applyGenericColumnVisibility(tableId);
            });
            label.appendChild(cb);
            label.appendChild(document.createTextNode(' ' + colText));
            menu.appendChild(label);
        })(i, text);
    }

    menu.classList.add('open');
}

function applyGenericColumnVisibility(tableId) {
    var table = document.getElementById(tableId);
    if (!table) return;
    var state = getTableState(tableId);

    var headerCells = table.tHead.rows[0].cells;
    for (var i = 0; i < headerCells.length; i++) {
        headerCells[i].style.display = state.hiddenCols[i] ? 'none' : '';
    }
    var rows = table.tBodies[0].rows;
    for (var r = 0; r < rows.length; r++) {
        if (rows[r].classList.contains('error-detail-row')) continue;
        var cells = rows[r].cells;
        for (var c = 0; c < cells.length; c++) {
            cells[c].style.display = state.hiddenCols[c] ? 'none' : '';
        }
    }
    // Visual indicator + "Show All Columns" button
    var count = Object.keys(state.hiddenCols).length;
    var section = table.closest('.report-section');
    if (section) {
        var menuBtn = section.querySelector('.column-toggle-wrapper .column-toggle-btn');
        if (menuBtn) menuBtn.classList.toggle('has-hidden', count > 0);
        var colBtn = document.getElementById('showAllColsBtn-' + tableId);
        if (colBtn) {
            if (count > 0) {
                colBtn.style.display = '';
                colBtn.textContent = 'Show ' + count + ' Hidden Column' + (count > 1 ? 's' : '');
            } else {
                colBtn.style.display = 'none';
            }
        }
    }
}

function showAllGenericColumns(tableId) {
    var state = getTableState(tableId);
    state.hiddenCols = {};
    applyGenericColumnVisibility(tableId);
    /* Re-check all checkboxes in the open menu */
    var table = document.getElementById(tableId);
    if (!table) return;
    var section = table.closest('.report-section');
    if (section) {
        section.querySelectorAll('.column-menu input[type="checkbox"]').forEach(function (cb) { cb.checked = true; });
    }
}

/** Sort any table by column index. type: 'text' or 'num'. Skips error-detail-row. */
function sortGenericTable(tableId, colIndex, type) {
    var table = document.getElementById(tableId);
    if (!table || !table.tBodies[0]) return;

    var th = table.tHead.rows[0].cells[colIndex];
    var currentSort = th.getAttribute('data-sort');
    var asc = currentSort !== 'asc';

    /* Clear sort indicators on all headers */
    var headers = table.tHead.rows[0].cells;
    for (var i = 0; i < headers.length; i++) {
        headers[i].removeAttribute('data-sort');
    }
    th.setAttribute('data-sort', asc ? 'asc' : 'desc');

    var rows = Array.from(table.tBodies[0].rows);
    var dataRows = rows.filter(function (r) { return !r.classList.contains('error-detail-row') && !r.classList.contains('error-summary-total'); });
    var detailRows = rows.filter(function (r) { return r.classList.contains('error-detail-row'); });
    var totalRow = rows.filter(function (r) { return r.classList.contains('error-summary-total'); });

    dataRows.sort(function (a, b) {
        var va = a.cells[colIndex] ? a.cells[colIndex].textContent.trim() : '';
        var vb = b.cells[colIndex] ? b.cells[colIndex].textContent.trim() : '';
        if (type === 'num') {
            va = parseFloat(va.replace(/[,%]/g, '')) || 0;
            vb = parseFloat(vb.replace(/[,%]/g, '')) || 0;
        }
        if (va < vb) return asc ? -1 : 1;
        if (va > vb) return asc ? 1 : -1;
        return 0;
    });

    var tbody = table.tBodies[0];
    dataRows.forEach(function (row) { tbody.appendChild(row); });
    /* Re-append detail rows after their parent data rows */
    detailRows.forEach(function (dr) {
        var idx = dr.previousElementSibling ? null : null; /* just append at end */
        tbody.appendChild(dr);
    });
    /* Keep total row at the bottom */
    totalRow.forEach(function (tr) { tbody.appendChild(tr); });
}

function filterGenericTable(tableId, filterText) {
    var table = document.getElementById(tableId);
    if (!table) return;
    var filter = filterText.toLowerCase();
    var rows = table.tBodies[0].rows;
    for (var i = 0; i < rows.length; i++) {
        if (rows[i].classList.contains('error-detail-row')) continue;
        var text = rows[i].textContent.toLowerCase();
        rows[i].style.display = text.indexOf(filter) >= 0 ? '' : 'none';
    }
}

function hideTableRow(btn, tableId) {
    var row = btn.closest('tr');
    if (!row) return;
    /* Close any expanded detail row */
    var next = row.nextElementSibling;
    if (next && next.classList.contains('error-detail-row')) {
        next.remove();
    }
    row.style.display = 'none';
    var state = getTableState(tableId);
    state.hiddenRowCount++;
    updateGenericHiddenBtn(tableId);
}

function showAllTableRows(tableId) {
    var table = document.getElementById(tableId);
    if (!table) return;
    var rows = table.tBodies[0].rows;
    for (var i = 0; i < rows.length; i++) {
        if (!rows[i].classList.contains('error-detail-row')) {
            rows[i].style.display = '';
        }
    }
    var state = getTableState(tableId);
    state.hiddenRowCount = 0;
    updateGenericHiddenBtn(tableId);
}

function updateGenericHiddenBtn(tableId) {
    var btn = document.getElementById('showHiddenBtn-' + tableId);
    if (!btn) return;
    var state = getTableState(tableId);
    if (state.hiddenRowCount > 0) {
        btn.style.display = '';
        btn.textContent = 'Show ' + state.hiddenRowCount + ' Hidden Row' + (state.hiddenRowCount > 1 ? 's' : '');
    } else {
        btn.style.display = 'none';
    }
}

/* ============================================================
 *  CSV Export for Tables
 * ============================================================ */

function csvQuote(val) {
    var s = String(val == null ? '' : val);
    return '"' + s.replace(/"/g, '""') + '"';
}

function downloadCsvFile(filename, csvString) {
    var blob = new Blob(['\ufeff' + csvString], { type: 'text/csv;charset=utf-8' });
    var a = document.createElement('a');
    a.href = URL.createObjectURL(blob);
    a.download = filename;
    a.click();
    URL.revokeObjectURL(a.href);
}

function buildCsvFromArrays(headers, rows) {
    var lines = [headers.map(csvQuote).join(',')];
    rows.forEach(function (row) {
        lines.push(row.map(csvQuote).join(','));
    });
    return lines.join('\n');
}

function fmtNum(v, decimals) {
    if (v == null || isNaN(v)) return '';
    return decimals != null ? Number(v).toFixed(decimals) : String(v);
}

function exportSamplerStatsCsv() {
    if (!globalChartData || !globalChartData.samplers) return;
    var headers = ['Sampler', 'Samples', 'Errors', 'Error %', 'Mean (ms)', 'Median (ms)',
        'P90 (ms)', 'P95 (ms)', 'P99 (ms)', 'Min (ms)', 'Max (ms)', 'Std Dev',
        'Throughput (/s)', 'Received B/s', 'Sent B/s', 'Avg Connect (ms)', 'Avg Latency (ms)', 'Apdex'];
    var rows = globalChartData.samplers.map(function (s) {
        return [s.name, s.count, s.errorCount, fmtNum(s.errorRate, 2),
            fmtNum(s.mean, 2), fmtNum(s.median, 2), fmtNum(s.p90, 2), fmtNum(s.p95, 2),
            fmtNum(s.p99, 2), fmtNum(s.min, 2), fmtNum(s.max, 2), fmtNum(s.stdDev, 2),
            fmtNum(s.throughput, 2), fmtNum(s.receivedBytesPerSec, 2),
            fmtNum(s.sentBytesPerSec, 2), fmtNum(s.meanConnectTime, 2), fmtNum(s.meanLatency, 2),
            fmtNum(s.apdex, 2)];
    });
    downloadCsvFile('sampler-statistics.csv', buildCsvFromArrays(headers, rows));
}

function exportTop5SlowestCsv() {
    if (!globalChartData || !globalChartData.samplers) return;
    var sorted = globalChartData.samplers.slice().sort(function (a, b) { return (b.p95 || 0) - (a.p95 || 0); });
    var top5 = sorted.slice(0, 5);
    var headers = ['Rank', 'Sampler', 'P95 (ms)', 'Mean (ms)', 'P99 (ms)', 'Samples', 'Error %'];
    var rows = top5.map(function (s, i) {
        return [i + 1, s.name, fmtNum(s.p95, 2), fmtNum(s.mean, 2), fmtNum(s.p99, 2),
            s.count, fmtNum(s.errorRate, 2)];
    });
    downloadCsvFile('top5-slowest-samplers.csv', buildCsvFromArrays(headers, rows));
}

function exportErrorSummaryCsv() {
    if (!globalChartData) return;
    var records = globalChartData.errorSummaries || globalChartData.errorsByType || [];
    var headers = ['Sampler', 'Response Code', 'Response Message', 'Count', '% of Errors', '% of All Samples'];
    var rows = records.map(function (e) {
        return [e.samplerName || e.sampler || '', e.responseCode || e.code || '',
            e.responseMessage || e.message || '', e.occurrenceCount || e.count || 0,
            fmtNum(e.percentageOfErrors, 2), fmtNum(e.percentageOfAllSamples, 2)];
    });
    downloadCsvFile('error-summary.csv', buildCsvFromArrays(headers, rows));
}

function exportErrorDetailsCsv() {
    if (!globalChartData || !globalChartData.errorRecords) return;
    var headers = ['Timestamp', 'Sampler', 'Response Code', 'Response Message', 'Thread', 'URL', 'Request Headers', 'Response Body'];
    var rows = globalChartData.errorRecords.map(function (e) {
        var ts = e.timestamp ? new Date(e.timestamp).toISOString() : '';
        return [ts, e.samplerName || e.sampler || '', e.responseCode || e.code || '',
            e.responseMessage || e.message || '', e.threadName || e.thread || '',
            e.requestUrl || e.url || '', e.headers || '', e.body || ''];
    });
    downloadCsvFile('error-details.csv', buildCsvFromArrays(headers, rows));
}

function exportComparisonCsv() {
    if (!globalChartData || !globalChartData.comparisonDiffs) return;
    var headers = ['Sampler', 'Status', 'Samples', 'Delta Samples', 'Errors', 'Delta Errors',
        'Error %', 'Delta Error %', 'Mean (ms)', 'Delta Mean', 'Median (ms)', 'Delta Median',
        'P90 (ms)', 'Delta P90', 'P95 (ms)', 'Delta P95', 'P99 (ms)', 'Delta P99',
        'Min (ms)', 'Delta Min', 'Max (ms)', 'Delta Max', 'Throughput', 'Delta Throughput',
        'Recv KB/s', 'Delta Recv KB/s'];
    var rows = globalChartData.comparisonDiffs.map(function (d) {
        var status = d.isNew ? 'NEW' : (d.regression ? 'REGRESSION' : 'OK');
        return [d.name, status, d.currSamples, d.deltaSamples, d.currErrors, d.deltaErrors,
            fmtNum(d.currErrorRate, 2), fmtNum(d.deltaErrorRate, 2),
            fmtNum(d.currMean, 2), fmtNum(d.deltaMean, 2),
            fmtNum(d.currMedian, 2), fmtNum(d.deltaMedian, 2),
            fmtNum(d.currP90, 2), fmtNum(d.deltaP90, 2),
            fmtNum(d.currP95, 2), fmtNum(d.deltaP95, 2),
            fmtNum(d.currP99, 2), fmtNum(d.deltaP99, 2),
            fmtNum(d.currMin, 2), fmtNum(d.deltaMin, 2),
            fmtNum(d.currMax, 2), fmtNum(d.deltaMax, 2),
            fmtNum(d.currThroughput, 2), fmtNum(d.deltaThroughput, 2),
            fmtNum(d.currRecvKBs, 2), fmtNum(d.deltaRecvKBs, 2)];
    });
    downloadCsvFile('comparison.csv', buildCsvFromArrays(headers, rows));
}

/* ============================================================
 *  Bootstrap — run when DOM is ready
 * ============================================================ */

document.addEventListener('DOMContentLoaded', function () {
    loadSettings();
    restoreFilterRowState();
    initTabs();
    initPaletteToggle();
    initLineThicknessToggle();
    initPercentileToggle();
    initColumnMenu();
    initNotesTab();
    initColumnDragDrop();
    initChartDragDrop();
    restoreChartLayout();

    // Close column menus on outside click
    document.addEventListener('click', function (e) {
        var menu = document.getElementById('columnMenu');
        var btn = document.getElementById('columnMenuBtn');
        if (menu && !menu.contains(e.target) && e.target !== btn) {
            menu.classList.remove('open');
        }
        /* Close generic column menus */
        document.querySelectorAll('.column-menu.open').forEach(function (m) {
            var wrapper = m.closest('.column-toggle-wrapper');
            if (wrapper && !wrapper.contains(e.target)) {
                m.classList.remove('open');
            }
        });
        /* Close filter bar menu */
        var fbMenu = document.getElementById('filterBarMenu');
        var fbWrapper = fbMenu ? fbMenu.closest('.filter-bar-toggle-wrapper') : null;
        if (fbMenu && fbWrapper && !fbWrapper.contains(e.target)) {
            fbMenu.classList.remove('open');
        }
    });
});
