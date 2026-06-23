# JVM Performance Analyzer — Functional Specification

**Version:** 1.0  
**Date:** June 2026  
**Status:** Released

---

## Table of Contents

1. [Overview](#1-overview)
2. [Target Users](#2-target-users)
3. [Supported Input Formats](#3-supported-input-formats)
4. [Core Workflows](#4-core-workflows)
5. [Dashboard](#5-dashboard)
6. [Hotspot Panel](#6-hotspot-panel)
7. [Call Tree](#7-call-tree)
8. [Timeline Analyzer](#8-timeline-analyzer)
9. [Diff Engine](#9-diff-engine)
10. [GC Log Analyzer](#10-gc-log-analyzer)
11. [Heap Dump Viewer](#11-heap-dump-viewer)
12. [Settings & Custom Rules](#12-settings--custom-rules)
13. [Export & Reports](#13-export--reports)
14. [Internationalization](#14-internationalization)
15. [Health Score](#15-health-score)
16. [Integration & Metrics](#16-integration--metrics)
17. [Non-Functional Requirements](#17-non-functional-requirements)

---

## 1. Overview

The **JVM Performance Analyzer** is a self-hosted web application that enables software engineers, SRE teams, and performance specialists to analyze JVM diagnostic artifacts — thread dumps, GC logs, JFR recordings, and heap dumps — without sending data to external services.

### Problem Statement

JVM performance issues (deadlocks, CPU hotspots, GC pressure, memory leaks) are notoriously difficult to diagnose from raw dump files. Existing tools are either heavyweight IDEs, cloud-only services, or command-line utilities that produce output requiring expert interpretation.

### Solution

Upload one or more dump files, receive an interactive visual analysis in seconds, and export professional reports in PDF and DOCX formats. All processing happens on-premise; no data leaves the server.

### Key Capabilities

| Capability | Description |
|------------|-------------|
| Multi-format ingestion | Thread dumps, GC logs, JFR recordings, heap dumps in one upload |
| CPU hotspot analysis | Aggregated call tree with self-time and total-time percentages |
| Deadlock detection | Lock graph cycle detection with monitor address tracking |
| GC pressure analysis | STW pause statistics, algorithm detection, promotion failure alerts |
| JFR CPU profiling | Real execution sample analysis via JDK built-in API |
| Session diff | Side-by-side comparison of two analyses to detect regressions |
| Timeline view | Multi-snapshot evolution of thread states and hotspots |
| Health score | Single 0–100 composite score with traffic-light classification |
| Multilingual UI | English, Spanish, French, German, Portuguese, Italian |
| Export | PDF executive report, DOCX document, JSON data, CSV hotspots |

---

## 2. Target Users

### Developer
Investigates CPU hotspots and identifies inefficient code paths in a specific service. Wants to see which method is consuming the most CPU and trace it back to their own code.

**Primary views:** Hotspot Panel, Call Tree, Solution Inspector

### SRE / Operations Engineer
Responds to a production incident. Needs an at-a-glance severity signal and specific thread state information to decide whether to restart or scale.

**Primary views:** Dashboard (Health Score), Timeline, GC Analyzer

### Team Lead / Engineering Manager
Reviews a performance report to understand systemic issues before a release. Does not need technical details — needs a clear summary exportable to a document.

**Primary views:** Dashboard, Export (PDF / DOCX)

### Performance Specialist
Compares two releases to validate that a performance fix actually improved the system.

**Primary views:** Diff Engine, Hotspot Panel

---

## 3. Supported Input Formats

| Format | Extension(s) | Source | Detection |
|--------|-------------|--------|-----------|
| Thread Dump | `.txt`, `.log` | `jstack`, `kill -3`, VisualVM | Content pattern matching (`Full thread dump`, `java.lang.Thread.State`) |
| GC Log (Unified) | `.log`, `.txt` | `-Xlog:gc*:file=gc.log` (JDK 9+) | Pattern: `[0.000s][info][gc]` |
| GC Log (Legacy) | `.log`, `.txt` | `-verbose:gc` (JDK 8) | Pattern: `[GC`, `[Full GC` |
| JFR Recording | `.jfr` | `jcmd JFR.start`, `-XX:StartFlightRecording` | Binary magic bytes `FLR\0` |
| Heap Dump | `.hprof` | `jmap`, `-XX:+HeapDumpOnOutOfMemoryError` | Binary magic string `JAVA PROFILE` |

### Format Auto-Detection

The system reads the first 12 bytes plus 20 text lines to determine the format. The file extension is used only as a fallback. A `.log` file that contains GC log patterns is correctly classified as a GC log even if the user named it `threaddump.log`.

### Multi-File Upload

Multiple files from the same JVM process can be uploaded in a single operation (e.g., a thread dump + a GC log). The system parses each file independently and merges results into one analysis session.

### File Size Limit

Maximum **500 MB** per file. Files exceeding this limit are rejected before upload with a client-side error message. Large heap dumps approaching the limit use streaming parsing mode to avoid memory exhaustion.

---

## 4. Core Workflows

### Workflow 1 — Single Dump Analysis

```
Upload file(s) → Auto-detect format → Parse → Analyze → View results → Export
```

1. User drags one or more files onto the Dashboard upload zone.
2. System auto-detects format and parses all files.
3. Analysis runs automatically: call tree is built, hotspots extracted, diagnostic rules fired, health score computed.
4. Dashboard shows the health score, stat cards, and critical diagnoses.
5. User navigates to Hotspot Panel, Call Tree, Timeline, or GC views.
6. User exports PDF or DOCX report.

### Workflow 2 — Regression Comparison (Diff)

```
Upload session A → Analyze → Upload session B → Analyze → Open Diff → Compare
```

1. Upload and analyze the baseline dump (e.g., before a deployment). It appears in the Recent Sessions list.
2. Upload and analyze the comparison dump (e.g., after deployment).
3. Navigate to Diff. Assign session A and session B via the slot radio buttons.
4. Click **Compare**. The diff shows regressed methods, new diagnoses, and health score delta.

### Workflow 3 — JFR Profiling Analysis

```
Record JFR with CPU profiling → Upload .jfr → Analyze → Hotspot Panel / Call Tree
```

JFR files must be recorded with CPU profiling enabled:
```bash
java -XX:StartFlightRecording=duration=60s,filename=recording.jfr,settings=profile -jar app.jar
# or
jcmd <PID> JFR.start duration=60s filename=recording.jfr settings=profile
```

The JFR parser groups `jdk.ExecutionSample` events into 1-second windows (synthetic snapshots), then passes them through the same call tree and hotspot pipeline as thread dumps.

### Workflow 4 — GC Pressure Diagnosis

```
Collect GC log → Upload → Analyze → GC Analyzer view
```

Enable GC logging before collecting:
```bash
# JDK 11+
java -Xlog:gc*:file=gc.log:time,uptime,level -jar app.jar
# JDK 8
java -verbose:gc -XX:+PrintGCDateStamps -XX:+PrintGCDetails -Xloggc:gc.log -jar app.jar
```

---

## 5. Dashboard

The Dashboard is the entry point for all analysis activity.

### Upload Zone

- Drag-and-drop area accepting all supported formats.
- "Choose Files" button opens the OS file picker.
- Client-side validation rejects files > 500 MB before upload.
- Files are uploaded as `multipart/form-data`.
- Upon upload completion, analysis runs automatically with the current ruleset.

### Health Score Widget

Displays the composite 0–100 score computed from four signals:
- Blocked thread percentage
- Confirmed deadlock presence
- Number of CRITICAL diagnoses
- Hotspot concentration (top method self-time)

The widget shows: numeric score, traffic-light badge (Healthy / Degraded / Critical), dominant factor explanation, and a signal breakdown grid.

### Stat Cards

Four cards shown after a successful analysis:
- **Total Threads** — total thread count across all snapshots
- **Blocked %** — percentage of threads in BLOCKED state (most recent snapshot)
- **Runnable %** — percentage of threads in RUNNABLE state
- **Snapshots** — number of snapshots parsed (> 1 for multi-snapshot dumps)

### Critical Issues List

If any diagnoses with severity `CRITICAL` were generated, they are listed below the stat cards in a red-bordered panel. This allows the user to immediately identify the most pressing issue without navigating away.

### Export Buttons

- **⬇ PDF** — downloads the full analysis as a PDF executive report.
- **⬇ DOCX** — downloads the full analysis as a Microsoft Word document.
- **⬇ JSON** — downloads the raw analysis result as structured JSON.
- **⬇ CSV** — downloads the hotspot table as a CSV file.

### Session History

The most recent sessions from the current browser session are listed at the bottom. Clicking a session name switches the active analysis to that session without re-uploading.

---

## 6. Hotspot Panel

Shows the top CPU-consuming methods ranked by self-time percentage.

### Hotspot Table

| Column | Description |
|--------|-------------|
| # | Rank (1 = highest self-time) |
| Method | Shortened method signature (hover for full name) |
| Layer | Ecosystem layer: `Database / Network`, `ORM`, `JSON`, `Cryptography`, `HTTP / Web`, `JDK Core`, `Business Logic` |
| Self% | % of samples where this method was at the top of the stack (heat-colored) |
| Total% | % of samples where this method appeared anywhere in the stack |
| Severity | Diagnostic severity badge: `CRITICAL`, `WARNING`, or blank |

**Heat coloring for Self%:**
- ≥ 20%: Red
- ≥ 10%: Orange
- ≥ 5%: Amber
- < 5%: Gray

**Sorting:** Click any column header to sort ascending/descending.

### Solution Inspector

Clicking a hotspot row opens the Solution Inspector drawer on the right:
- Full method signature
- Layer badge
- Self-time and total-time values
- Diagnosis message (from built-in rules or custom signatures)
- Severity badge with source label ("Built-in" or "Custom")
- Top caller method

### All Diagnoses

Below the table, all diagnoses generated by the rule engine are listed with their severity and message. This includes diagnoses not directly tied to a hotspot (e.g., synchronization contention, finalizer backlog).

### Export Buttons

PDF, DOCX, and CSV export buttons appear in the header when analysis data is loaded.

---

## 7. Call Tree

Visualizes the aggregated call tree built from all thread stack traces.

### Tree Structure

The call tree merges identical stack frames across all sampled threads. A node at depth N represents a method called from its parent. The root node `[root]` aggregates all entry points.

- **Total%** (shown on each node): % of all samples where this method appeared anywhere in the stack.
- **Self%** (shown on leaf-like nodes): % where this method was at the top of the stack.
- Heat coloring: nodes with total-time ≥ 20% are red, ≥ 10% orange, ≥ 5% amber.

### Controls

- **Search box**: filters visible nodes by method name substring. Matching nodes are highlighted; non-matching branches are dimmed.
- **Inverted Tree** toggle: switches between top-down (entry points at root) and bottom-up (hottest methods at root) view. Useful for finding which business callers drive the same hot leaf method.
- **Expand / Collapse**: clicking a node toggles its children.

### Export Buttons

PDF and DOCX export buttons appear in the header.

---

## 8. Timeline Analyzer

The Timeline view is relevant only for multi-snapshot dumps (thread dumps with more than one snapshot, or JFR recordings that produce multiple 1-second windows).

### Snapshot Table

Lists each snapshot with:
- Snapshot index and timestamp (if available)
- Thread count in each state (RUNNABLE, BLOCKED, WAITING, TIMED_WAITING)
- Inline bar chart of state distribution

### Hotspot Trends

Grid showing the top methods and how their stack-frequency evolved across snapshots. An up arrow (↑) means the method's presence increased; a down arrow (↓) means it decreased. Useful for identifying a single misbehaving scheduled job that fires only in snapshot t3.

### Thread Swim Lanes

Color-coded per-thread state timeline for the first 20 threads:
- **Green**: RUNNABLE
- **Red**: BLOCKED
- **Yellow**: WAITING / TIMED_WAITING
- **Gray**: other states

### Confirmed Deadlock Badge

If a thread remains BLOCKED on the same monitor address across **all** snapshots, it is classified as a confirmed deadlock (not merely suspected). A red pulsing badge appears at the top of the view.

---

## 9. Diff Engine

Compares two analysis sessions to identify regressions or improvements between two points in time (e.g., before and after a deployment).

### Session Slot Assignment

- **Slot A (Baseline)**: the older or reference analysis.
- **Slot B (Comparison)**: the newer or modified analysis.

Sessions are assigned via radio buttons in the slot selector. Any previously analyzed session from the current browser session can be used.

### Compare Button

Clicking **Compare** sends both session IDs to the backend `POST /api/v1/diff` endpoint and renders the diff result in three tabs.

### Call Tree Diff Tab

Methods are displayed with a change status:
- **ADDED** (green): appears in B but not in A — new code path or regression
- **REMOVED** (gray): appeared in A but not in B — improvement or code removal
- **REGRESSED** (red): in both, but self-time increased by more than the delta threshold (default 5%)
- **IMPROVED** (blue): in both, but self-time decreased significantly
- **UNCHANGED**: no meaningful change

### Hotspot Diff Tab

Unified table with columns: rank in A, rank in B, method, self-time delta, trend indicator (UP / DOWN / NEW / RESOLVED / STABLE).

### Diagnostics Diff Tab

Three columns:
- **Resolved**: diagnoses in A that are absent in B (fixed issues)
- **New**: diagnoses in B that were absent in A (new problems)
- **Persisting**: diagnoses present in both (ongoing issues)

### Health Score Comparison

Both health scores (A and B) are shown side by side with a delta badge:
- Green: improvement (B > A)
- Red: regression (B < A)

---

## 10. GC Log Analyzer

Analyzes GC log files to surface garbage collection behavior and alert on critical patterns.

### Summary Cards

| Card | Value |
|------|-------|
| GC Algorithm | Detected algorithm: G1GC, ZGC, Shenandoah, ParallelGC, SerialGC, or Unknown |
| GC Time % | Total elapsed time spent in GC pauses |
| Max Pause | Longest single stop-the-world pause (ms) |
| p99 Pause | 99th percentile STW pause duration (ms) |

### Alerts

| Alert | Trigger |
|-------|---------|
| Promotion Failure | `promotion failed` event detected in log |
| Concurrent Mode Failure | `concurrent mode failure` event detected (CMS/G1) |
| GC Thrashing | GC time % exceeds configured threshold (default 10%) |

### Algorithm Recommendation

When a suboptimal algorithm is detected for the apparent workload, a recommendation is displayed. Example: "You are using Parallel GC on a large heap. Consider G1GC for lower and more predictable pause times."

### STW Events Table

Scrollable list of all stop-the-world events with:
- Timestamp (relative to JVM start)
- Event type (Minor GC, Major GC, Full GC, Mixed GC)
- Pause duration (ms)
- Heap size before and after the event

---

## 11. Heap Dump Viewer

The heap dump analyzer requires the Eclipse Memory Analyzer Tool (MAT) API (`org.eclipse.mat`), which is not available on Maven Central and is not bundled in this release.

**Current behavior:** when a `.hprof` file is uploaded, the session stores the raw bytes and displays an informational message explaining how to analyze the dump using standalone Eclipse MAT.

**Planned scope (future release):**
- Dominator tree view
- Class histogram
- Memory leak candidates
- Retained heap size per class

---

## 12. Settings & Custom Rules

The Settings view allows users to customize the analysis rule engine without modifying backend configuration or redeploying the application.

### Interface Language

Select from six available languages:

| Code | Language |
|------|----------|
| `en` | English |
| `es` | Español |
| `fr` | Français |
| `de` | Deutsch |
| `pt` | Português |
| `it` | Italiano |

The preference is saved in `localStorage` under `pa.lang`. The entire UI updates reactively without a page reload.

### Custom Package Signatures

Users can define custom package-to-layer mappings that take priority over the built-in signature dictionary:

| Field | Description |
|-------|-------------|
| Package Prefix | e.g., `com.mycompany.payments` |
| Layer | e.g., `Payment Processing` (free text or one of the standard layers) |
| Diagnosis Message | Custom text shown in the Solution Inspector when this package is a hotspot |
| Severity | `WARNING`, `CRITICAL`, or `INFO` |

### Threshold Configuration

| Threshold | Default | Description |
|-----------|---------|-------------|
| Min Self% for critical hotspot | 5% | Self-time above which a hotspot is flagged |
| Blocked threads % alert | 20% | % of BLOCKED threads that triggers a CRITICAL diagnosis |
| GC time % alert | 10% | GC time percentage that triggers a GC Thrashing warning |
| Call tree delta threshold | 5% | Minimum self-time change to show in a diff |

### Ruleset Export / Import

- **Export Ruleset**: downloads the current configuration as `ruleset.json`.
- **Import Ruleset**: loads a previously exported file, replacing the current configuration.
- **Reset to Defaults**: clears all custom signatures and restores default thresholds.

Teams can version-control a shared `ruleset.json` and distribute it to all team members via the Import button.

### Persistence

All settings are stored in `localStorage` under `pa.ruleset` (JSON). They survive browser tab closure and are automatically restored on the next visit. The ruleset is sent with every `/analyze` request; it is not persisted server-side.

---

## 13. Export & Reports

### PDF Export

Server-generated PDF executive report (`application/pdf`) containing:
1. Cover page with analysis metadata (date, file names, dump types)
2. Health Score with classification and dominant factor
3. Top 10 hotspot table (rank, method, layer, self%, total%)
4. Full diagnoses list with severity
5. GC summary (if a GC log was included)

### DOCX Export

Server-generated Microsoft Word document (`.docx`) with identical content structure to the PDF. Suitable for editing, embedding in existing documentation, or attaching to JIRA tickets.

### JSON Export

Full analysis result serialized as structured JSON. Contains:
- All hotspots with full metadata
- Complete call tree
- All diagnoses
- Health score with signal breakdown
- Timeline data
- GC summary

Intended for integration with external dashboards, data pipelines, or archival.

### CSV Export (Hotspots)

Flat CSV with one row per hotspot:
```
rank,methodSignature,className,packagePrefix,layer,selfTimePercent,totalTimePercent,diagnosis,severity
```

Suitable for import into spreadsheets or data visualization tools.

---

## 14. Internationalization

The application UI is available in six languages, selectable at runtime in Settings.

Language scope covers:
- Navigation bar
- View titles and section headings
- Empty-state messages
- Export button labels
- Settings labels and descriptions
- Alert and badge text

Language preference is persisted in `localStorage` (`pa.lang`) and survives browser restart. Changing the language updates all visible text reactively without a page reload.

Translations not yet covered by the i18n layer (e.g., diagnosis messages, algorithm recommendations) remain in English in this release.

---

## 15. Health Score

### Composite Score (0–100)

The health score aggregates four independent signals into a single numeric value:

| Signal | Weight | Perfect Score | Zero Score |
|--------|--------|--------------|------------|
| Blocked thread % | 30% | 0% blocked | ≥ 50% blocked |
| Confirmed deadlock | 25% | No deadlock | Deadlock confirmed |
| CRITICAL diagnoses count | 25% | 0 critical | ≥ 5 critical |
| Top hotspot self-time % | 20% | < 10% | ≥ 50% |

**Formula:**  
Each signal is normalized to [0, 100] linearly. Final score = weighted average, rounded to integer.

### Traffic-Light Classification

| Score Range | Classification | Color |
|-------------|---------------|-------|
| 80 – 100 | Healthy | Green |
| 50 – 79 | Degraded | Yellow |
| 0 – 49 | Critical | Red |

### Dominant Factor

The signal with the highest penalty contribution is reported as the dominant factor. Example:  
`"Score reduced primarily by: 38% of threads are BLOCKED"`

### Signal Weights

Weights are configurable in `application.yml` and must sum to 1.0:
```yaml
performanceanalyzer:
  healthscore:
    weights:
      blocked-threads: 0.30
      deadlock: 0.25
      critical-diagnoses: 0.25
      hotspot-concentration: 0.20
```

---

## 16. Integration & Metrics

### Prometheus Endpoint

After any analysis completes, `GET /actuator/prometheus` exposes:

| Metric | Type | Description |
|--------|------|-------------|
| `pa_health_score` | Gauge | Composite health score (0–100) |
| `pa_threads_total` | Gauge | Total thread count |
| `pa_threads_blocked_percent` | Gauge | % of blocked threads |
| `pa_hotspot_top_self_time_percent` | Gauge | Top hotspot self-time % (labeled with method) |
| `pa_diagnoses_critical_count` | Gauge | Number of CRITICAL diagnoses |
| `pa_diagnoses_warning_count` | Gauge | Number of WARNING diagnoses |

### JSON Status Endpoint

`GET /api/v1/status/latest` returns a JSON summary of the most recent analysis. Useful for Datadog HTTP checks or shell script monitoring.

### Webhook (Optional)

If `performanceanalyzer.integrations.webhook.url` is configured, a POST request is sent to that URL after each analysis completes. Payload schema:

```json
{
  "schemaVersion": "1.0",
  "timestamp": "2026-06-14T10:00:00Z",
  "analysisId": "<uuid>",
  "healthScore": { "value": 42, "classification": "CRITICAL", "dominantFactor": "..." },
  "summary": { "totalThreads": 250, "blockedThreadsPercent": 38.0, "confirmedDeadlock": true },
  "topHotspot": { "methodSignature": "...", "selfTimePercent": 42.5 },
  "dumpTypes": ["THREAD_DUMP"],
  "fileNames": ["production.log"]
}
```

Delivery is fire-and-forget with a 5-second timeout. No retry logic.

### Health Check

`GET /actuator/health` — returns Spring Boot standard health response. Returns `200 OK` when the application is ready to serve requests.

---

## 17. Non-Functional Requirements

### Performance

| Operation | Target |
|-----------|--------|
| Thread dump parsing (1,000 threads) | < 2 seconds |
| JFR analysis (60s recording, ~6,000 samples) | < 5 seconds |
| PDF/DOCX generation | < 3 seconds |
| UI initial load (cold, fat JAR) | < 3 seconds |
| UI navigation between views | < 200 ms |

### Capacity

- Sessions retained in memory for up to 120 minutes of inactivity.
- Up to 10 concurrent sessions supported in a 4 GB container (configurable).
- No persistent storage: all data is in-memory and lost on server restart.

### Security

- No authentication required (local / internal deployment model).
- No data leaves the server unless a webhook URL is explicitly configured.
- Uploaded files are stored only in the JVM temporary directory and discarded after parsing.
- All API endpoints are on the same origin as the frontend; no CORS exposure in production.

### Browser Support

| Browser | Minimum Version |
|---------|----------------|
| Chrome | 110+ |
| Firefox | 110+ |
| Edge | 110+ |
| Safari | 16+ |

Minimum viewport: 1280 px wide. Mobile layouts are not supported in v1.
