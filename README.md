# JVM Performance Analyzer

<p align="center">
  <img src="https://img.shields.io/badge/Java-21-orange?logo=openjdk&logoColor=white" alt="Java 21"/>
  <img src="https://img.shields.io/badge/Spring%20Boot-3.3.0-6DB33F?logo=springboot&logoColor=white" alt="Spring Boot 3.3"/>
  <img src="https://img.shields.io/badge/Angular-17-DD0031?logo=angular&logoColor=white" alt="Angular 17"/>
  <img src="https://img.shields.io/badge/Tailwind%20CSS-3-38BDF8?logo=tailwindcss&logoColor=white" alt="Tailwind CSS"/>
  <img src="https://img.shields.io/badge/Docker-ready-2496ED?logo=docker&logoColor=white" alt="Docker"/>
  <img src="https://img.shields.io/badge/version-1.0.0-blue" alt="Version 1.0.0"/>
  <img src="https://img.shields.io/badge/license-MIT-green" alt="License MIT"/>
</p>

<p align="center">
  <strong>Self-hosted JVM diagnostic analysis tool.</strong><br/>
  Upload thread dumps, JFR recordings, GC logs, and heap dumps — get instant visual analysis, a health score, and professional reports. All on-premise, zero cloud dependency.
</p>

---

## Table of Contents

- [Overview](#overview)
- [Key Features](#key-features)
- [Documentation](#documentation)
- [Quick Start](#quick-start)
- [Running Options](#running-options)
- [Application Views](#application-views)
- [Health Score](#health-score)
- [Supported File Formats](#supported-file-formats)
- [Export Formats](#export-formats)
- [REST API Reference](#rest-api-reference)
- [Configuration](#configuration)
- [Development Guide](#development-guide)
- [Project Structure](#project-structure)
- [Technology Stack](#technology-stack)
- [Architecture](#architecture)

---

## Overview

The **JVM Performance Analyzer** transforms raw JVM diagnostic artifacts into interactive, actionable insights in seconds. It is designed for software engineers, SRE teams, and performance specialists who need to diagnose production JVM problems without sending sensitive data to cloud services.

**What it solves:**

| Problem | Solution |
|---------|----------|
| Thread dumps are thousands of lines of unreadable text | Interactive call tree with heat-colored nodes, ranked hotspot table |
| Deadlocks are hard to confirm from a single snapshot | Lock-graph cycle detection + confirmed deadlock badge across multi-snapshot dumps |
| GC logs require expert knowledge to interpret | Auto-detection of GC algorithm, pause statistics, promotion failure alerts |
| JFR recordings cannot be analyzed without JMC or a cloud service | Built-in JFR parser using the JDK's own `jdk.jfr.consumer` API |
| No easy way to prove a fix improved performance | Side-by-side diff engine comparing two analysis sessions |
| Sharing results requires ad-hoc screenshots | PDF, DOCX, JSON, and CSV export in one click |

**All processing is on-premise.** No file content or analysis result is ever transmitted to an external service.

---

## Key Features

### Analysis

- **Multi-format ingestion** — Thread dumps, GC logs (unified JDK 9+ and legacy JDK 8), JFR recordings, heap dumps in a single upload
- **Aggregated call tree** — Merges all stack traces into a hierarchical tree with Self Time % and Total Time % per method
- **Hotspot panel** — Top CPU-consuming methods ranked by self-time, classified by ecosystem layer (DB/ORM/HTTP/JDK Core/Business)
- **Deadlock detection** — Lock-graph cycle detection with monitor address tracking; confirmed deadlock classification from multi-snapshot dumps
- **GC log analysis** — STW pause statistics (max, avg, p99), algorithm auto-detection, promotion failure detection, GC thrashing alert
- **JFR profiling** — Real execution sample analysis via the built-in `jdk.jfr.consumer.RecordingFile` API (no external dependency)

### Comparison & Timeline

- **Session diff engine** — Side-by-side comparison of two analyses with trend badges (NEW / RESOLVED / UP / DOWN / STABLE)
- **Timeline analyzer** — Multi-snapshot thread state evolution, swim-lane view, confirmed deadlock detection across snapshots, hotspot trend charts

### Diagnostics

- **Health score (0–100)** — Composite signal from blocked threads %, deadlock presence, CRITICAL diagnosis count, and hotspot concentration; traffic-light classification (Green / Amber / Red)
- **Heuristic rule engine** — Detects deadlocks, lock contention, linear list scans, string concatenation loops, reflection abuse, finalizer backlogs, GC thrashing
- **Solution inspector** — Diagnosis message, severity, and fix recommendation for every hotspot

### Usability

- **Custom rules** — Define your own package signatures, layer labels, and diagnostic thresholds without redeployment
- **Ruleset portability** — Export and import rule configurations as JSON for team-wide consistency
- **Multilingual UI** — English, Spanish, French, German, Portuguese, Italian
- **Dark mode** — Toggle between light and dark themes; preference persisted in `localStorage`
- **Export** — PDF (iText 8), DOCX (Apache POI), JSON, CSV
- **Prometheus metrics** — Expose analysis results as Prometheus gauges for Grafana / Datadog scraping
- **Webhook notifications** — Optional POST to Slack/PagerDuty after every analysis

---

## Documentation

Full documentation is available in the [`docs/`](docs/) folder:

| Document | Format | Description |
|----------|--------|-------------|
| [User Manual](docs/JVM-Performance-Analyzer-User-Manual.docx) | DOCX | Complete guide covering all 8 views, workflows, export formats, health score formula, configuration reference, API, and troubleshooting |
| [Functional Specification](docs/functional-specification.md) | Markdown | 17-section specification covering all modules, user workflows, and non-functional requirements |
| [Technical Specification](docs/technical-specification.md) | Markdown | 13-section engineering reference covering architecture, package structure, all backend modules, REST API schemas, frontend design, build pipeline, and deployment |
| [Usage Guide](usage.md) | Markdown | Quick-start commands, curl examples, and per-view usage notes |

> **Tip:** The DOCX User Manual is the most complete end-user reference. It includes a full glossary, diagnostic rule reference, and release notes for v1.0.

---

## Quick Start

### Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java (JDK) | **21+** | Required to run the application |
| Maven | **3.8+** | Required to build from source |
| Docker | any | Optional — for containerised deployment |
| Node.js | 18.x | Optional — downloaded automatically by Maven during build |
| Browser | Chrome/Firefox/Edge 120+ | Minimum 1280 px viewport |

### One-liner (build + run)

```bash
mvn package -DskipTests && java -jar target/performance-analyzer-1.0.0.jar
```

Open **http://localhost:8080** in your browser.

---

## Running Options

### Option 1 — Maven Build + Direct Execution

```bash
# Full build: downloads Node 18, compiles Angular, packages fat JAR
mvn package -DskipTests

# Run
java -jar target/performance-analyzer-1.0.0.jar
```

> Maven automatically installs Node 18.15.0 into `target/` during the build.
> You do **not** need Node on your `PATH`.

### Option 2 — Docker Compose *(recommended for team deployments)*

```bash
mvn package -DskipTests
docker-compose up --build          # foreground
docker-compose up -d --build       # background
docker-compose down                # stop
```

The Compose file sets `mem_limit: 4g`. Increase to `8g` for heap dumps larger than 200 MB.

### Option 3 — Docker only

```bash
mvn package -DskipTests
docker build -t performance-analyzer .
docker run -p 8080:8080 -m 4g performance-analyzer
```

### Option 4 — Backend only (skip Angular build)

Use when the frontend is already built and you are iterating on backend code:

```bash
mvn package -DskipTests -P skip-frontend
java -jar target/performance-analyzer-1.0.0.jar
```

### Environment-variable overrides

Any `application.yml` property can be overridden at runtime:

```bash
java -jar target/performance-analyzer-1.0.0.jar \
  --server.port=9090 \
  --performanceanalyzer.session.idle-timeout-minutes=240
```

Or in Docker:

```bash
docker run -p 8080:8080 -m 8g \
  -e SERVER_PORT=8080 \
  -e PERFORMANCEANALYZER_SESSION_IDLE_TIMEOUT_MINUTES=240 \
  -e PERFORMANCEANALYZER_INTEGRATIONS_WEBHOOK_URL="https://hooks.slack.com/..." \
  performance-analyzer
```

---

## Application Views

The application is a single-page Angular app with eight views accessible from the left navigation sidebar.

### Dashboard

The entry point for every analysis session.

- **Drag-and-drop upload zone** — single or multi-file; accepted formats are auto-detected from content
- **Health Score widget** — 0–100 score with Green / Amber / Red classification and dominant-factor explanation
- **Metric cards** — Total threads · Blocked % · Runnable % · GC P99 pause
- **Critical diagnoses list** — all CRITICAL and WARNING findings at a glance
- **Export buttons** — PDF, DOCX, JSON, CSV
- **Session history** — all analyses from the current browser session; click to reload without re-running

### Hotspot Panel

Ranks every method with a non-zero Self Time % descending.

| Column | Description |
|--------|-------------|
| `#` | Rank |
| Method | Shortened signature (hover for full FQDN) |
| Layer | Ecosystem layer: `Database/Network`, `ORM`, `JSON`, `Cryptography`, `HTTP/Web`, `JDK Core`, `Business Logic` |
| Self % | % of samples where this method was the top-of-stack frame; heat-colored (red ≥ 20%, orange ≥ 10%, amber ≥ 5%) |
| Total % | % of samples where this method appeared anywhere in the stack |
| Severity | `CRITICAL` · `WARNING` · `INFO` badge |

Clicking a row opens the **Solution Inspector** — a slide-in drawer with the full diagnosis, severity, top caller, and rule source (Built-in / Custom).

### Call Tree

Aggregated hierarchical view of all stack traces.

- **Top-down mode** — entry points at root; expand nodes to drill into hotspots
- **Bottom-up (inverted) mode** — hot methods at root; useful for tracing back to entry points
- **Search** — highlights and auto-expands matching nodes
- **Stack trace sanitization** — CGLIB, Spring AOP proxies, reflective lambdas, and Hibernate interceptors are automatically collapsed

### Timeline Analyzer

Available when a dump contains multiple sequential snapshots (multi-jstack files).

- **Snapshot table** — per-snapshot thread counts and inline state distribution bar
- **Thread state timeline chart** — RUNNABLE / BLOCKED / WAITING / TIMED\_WAITING over time; spikes vs sustained elevation
- **Thread swim lanes** — per-thread color-coded state grid for the first 20 threads
- **Hotspot trends** — top 10 methods and their Self % evolution snapshot-by-snapshot
- **Confirmed Deadlock badge** — displayed when the same threads remain BLOCKED on the same monitor across all snapshots

### Diff Engine

Side-by-side comparison of two analysis sessions.

1. Analyze session A (baseline)
2. Analyze session B (comparison)
3. Assign each to a slot using the radio buttons
4. Click **Compare A vs B**

**Tabs:**

| Tab | Content |
|-----|---------|
| Hotspots | Rank A vs B, Self% delta, trend badges (NEW / RESOLVED / UP / DOWN / STABLE) |
| Diagnostics | Resolved (green) · New (red) · Persisting (gray) diagnoses |
| Thread States | Side-by-side thread state distribution bars |

The **Health Score comparison** shows both scores and a colored delta (green = improvement, red = regression).

### GC Log Analyzer

Surfaces garbage collection behavior from uploaded GC log files.

- **Summary cards** — Algorithm, GC Time %, Max Pause, P99 Pause
- **Alerts** — Promotion Failure, Concurrent Mode Failure (CMS), GC Thrashing (> configurable threshold)
- **Algorithm recommendation** — specific guidance for the detected GC (Parallel → G1, CMS → migrate, ZGC → heap sizing, etc.)
- **STW Events table** — scrollable list of every stop-the-world event with pause duration heat coloring

### Heap Dump Viewer

> **v1.0 status:** The heap dump parser requires the Eclipse Memory Analyzer (MAT) API, which is not published on Maven Central. In the current release, uploading a `.hprof` file stores the raw bytes in the session and displays instructions for loading the file in standalone Eclipse MAT.

Planned for a future release: dominator tree, class histogram, and memory leak candidate detection.

### Settings & Custom Rules

- **Language selector** — switch between EN / ES / FR / DE / PT / IT without page reload
- **Theme toggle** — also accessible from the nav bar (moon / sun icon)
- **Custom package signatures** — map your own package prefixes to layer categories and diagnosis messages
- **Threshold editor** — blocked threads alert %, GC thrashing %, hotspot delta threshold, and Health Score signal weights
- **Ruleset export / import** — share rule configurations as a portable `ruleset.json`

---

## Health Score

A single 0–100 composite metric computed after every analysis, giving operations teams an instant severity signal.

### Formula

| Signal | Weight | Scoring |
|--------|--------|---------|
| Blocked Threads % | **30%** | 0% blocked = 100 pts; ≥ 50% blocked = 0 pts (linear) |
| Confirmed Deadlock | **25%** | No deadlock = 100 pts; any deadlock = 0 pts |
| CRITICAL Diagnoses | **25%** | 0 critical = 100 pts; ≥ 5 critical = 0 pts (linear) |
| Hotspot Concentration | **20%** | Top method Self% < 10% = 100 pts; ≥ 50% = 0 pts (linear) |

```
finalScore = 0.30 × blockedScore
           + 0.25 × deadlockScore
           + 0.25 × diagnosisScore
           + 0.20 × hotspotScore
```

### Classification

| Score | Classification | Recommended Action |
|-------|---------------|-------------------|
| 80–100 | 🟢 **Healthy** | No immediate action required |
| 50–79 | 🟡 **Degraded** | Investigate dominant factor; schedule fix |
| 0–49 | 🔴 **Critical** | Immediate attention required; escalate to on-call |

Signal weights are configurable in `application.yml` (`performanceanalyzer.healthscore.weights.*`).

---

## Supported File Formats

| Format | Extension(s) | How to Generate | Detection |
|--------|-------------|-----------------|-----------|
| **Thread Dump** | `.txt`, `.log` | `jstack <pid>` · `kill -3` · VisualVM | Content pattern: `Full thread dump`, `java.lang.Thread.State` |
| **GC Log (Unified)** | `.log`, `.txt` | `-Xlog:gc*:file=gc.log` (JDK 9+) | Pattern: `[0.000s][info][gc]` |
| **GC Log (Legacy)** | `.log`, `.txt` | `-verbose:gc` (JDK 8) | Pattern: `[GC`, `[Full GC` |
| **JFR Recording** | `.jfr` | `jcmd <pid> JFR.start settings=profile` | Binary magic bytes `FLR\0` |
| **Heap Dump** | `.hprof` | `jmap -dump` · `-XX:+HeapDumpOnOutOfMemoryError` | Binary magic `JAVA PROFILE` |

Format is detected from **file content**, not the extension. A `threaddump.log` that contains GC log patterns is correctly classified as a GC log.

### Multi-file upload

Multiple files from the same JVM can be uploaded in a single operation. Example: upload a thread dump + a GC log together; the application populates both the Hotspot Panel and the GC Analyzer from a single session.

### Generating dumps

```bash
# Thread dump — single snapshot
jstack <pid> > threaddump.txt

# Thread dump — 5 snapshots, 3-second interval (enables Timeline view)
for i in {1..5}; do jstack <pid> >> threaddump-multi.txt; sleep 3; done

# GC log (JDK 11+)
# Add to JVM startup flags:
-Xlog:gc*:file=gc.log:time,uptime,level,tags:filecount=5,filesize=100m

# JFR recording — 60 seconds with profiling settings
jcmd <pid> JFR.start duration=60s filename=recording.jfr settings=profile
```

---

## Export Formats

| Format | Endpoint | Content |
|--------|----------|---------|
| **PDF** | `GET /api/v1/export/pdf/{sessionId}` | Health score, top 10 hotspots table, all diagnoses, GC summary. Generated by iText 8. |
| **DOCX** | `GET /api/v1/export/docx/{sessionId}` | Same content as PDF in Word format. Generated by Apache POI OOXML. |
| **JSON** | `GET /api/v1/export/json/{sessionId}` | Full analysis result: call tree, hotspots, diagnoses, timeline, GC summary, health score breakdown. |
| **CSV** | `GET /api/v1/export/csv/{sessionId}/hotspots` | Flat hotspot list: `rank,method,class,package,layer,selfTime%,totalTime%,diagnosis,severity` |

Export buttons are available in every view (Dashboard, Hotspot Panel, Call Tree, Timeline, Diff, GC Analyzer).

---

## REST API Reference

**Base URL:** `http://localhost:8080/api/v1`

All request and response bodies are `application/json` unless noted.

### Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/upload` | Upload dump files (`multipart/form-data`, field: `files`). Returns `sessionId`. |
| `POST` | `/analyze/{sessionId}` | Run analysis pipeline. Body: `RulesetDto` JSON. Returns `AnalysisResultDto`. |
| `GET` | `/analyze/{sessionId}` | Get cached analysis result. |
| `POST` | `/diff` | Compare two sessions. Query params: `sessionIdA`, `sessionIdB`, `deltaThreshold` (default `5.0`). |
| `GET` | `/export/pdf/{sessionId}` | Download PDF report. |
| `GET` | `/export/docx/{sessionId}` | Download DOCX report. |
| `GET` | `/export/json/{sessionId}` | Download JSON result. |
| `GET` | `/export/csv/{sessionId}/hotspots` | Download CSV hotspot list. |
| `DELETE` | `/session/{sessionId}` | Delete a session. |
| `DELETE` | `/session/clear` | Delete all sessions. |
| `GET` | `/status/latest` | JSON summary of the most recent analysis. |
| `GET` | `/actuator/health` | Spring Boot health check. |
| `GET` | `/actuator/prometheus` | Prometheus scrape endpoint. |

### Upload and analyze (curl example)

```bash
# 1. Upload files
curl -X POST http://localhost:8080/api/v1/upload \
  -F "files=@threaddump.txt" \
  -F "files=@gc.log"

# Response:
# { "sessionId": "550e8400-...", "fileNames": ["threaddump.txt","gc.log"], "detectedTypes": ["THREAD_DUMP","GC_LOG"] }

SESSION_ID="550e8400-e29b-41d4-a716-446655440000"

# 2. Run analysis
curl -X POST http://localhost:8080/api/v1/analyze/$SESSION_ID \
  -H "Content-Type: application/json" \
  -d '{"version":"1.0","customSignatures":[],"thresholds":{}}'

# 3. Download PDF
curl -o report.pdf http://localhost:8080/api/v1/export/pdf/$SESSION_ID
```

### Error response format

```json
{
  "errorCode": "BAD_REQUEST | NOT_FOUND | FILE_TOO_LARGE | INTERNAL_ERROR",
  "message": "Human-readable description",
  "timestamp": "2026-06-14T10:00:00.000Z"
}
```

### Prometheus metrics (after analysis)

```
pa_health_score                       gauge  0–100 composite health score
pa_threads_total                      gauge  Total thread count
pa_threads_blocked_percent            gauge  % of threads in BLOCKED state
pa_hotspot_top_self_time_percent      gauge  Self% of rank-1 hotspot (label: method)
pa_diagnoses_critical_count           gauge  Number of CRITICAL diagnoses
pa_diagnoses_warning_count            gauge  Number of WARNING diagnoses
```

---

## Configuration

All server-side configuration is in `src/main/resources/application.yml`.

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 500MB          # Per-file upload limit
      max-request-size: 550MB       # Total request size limit
  mvc:
    async:
      request-timeout: 300000       # 5 min timeout for large file parsing

server:
  port: 8080

performanceanalyzer:
  session:
    idle-timeout-minutes: 120       # Evict sessions idle for this long

  healthscore:
    weights:                        # Must sum to 1.0
      blocked-threads: 0.30
      deadlock: 0.25
      critical-diagnoses: 0.25
      hotspot-concentration: 0.20

  thresholds:
    min-self-time-critical: 5.0     # Self% above which a hotspot is flagged
    blocked-threads-alert: 20.0     # BLOCKED% that triggers contention WARNING
    gc-thrash-alert: 10.0           # GC time% that triggers thrashing WARNING
    calltree-max-depth: 200         # Call tree serialization depth limit

  integrations:
    webhook:
      url: ""                       # POST analysis summary here (empty = disabled)
      api-key: ""
      timeout-ms: 5000

  export:
    pdf:
      top-hotspots-count: 10

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

### Webhook payload (when `webhook.url` is configured)

```json
{
  "schemaVersion": "1.0",
  "timestamp": "2026-06-14T10:30:00Z",
  "analysisId": "uuid-v4",
  "healthScore": { "value": 42, "classification": "CRITICAL", "dominantFactor": "38% of threads are BLOCKED" },
  "summary": { "totalThreads": 250, "blockedThreadsPercent": 38.0, "confirmedDeadlock": true, "criticalDiagnosesCount": 3 },
  "dumpTypes": ["THREAD_DUMP", "GC_LOG"]
}
```

---

## Development Guide

### Backend (Spring Boot)

```bash
# Run backend dev server (no Angular build; hot-reload via Spring DevTools)
mvn spring-boot:run

# Compile only
mvn compile

# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=ThreadDumpParserTest

# Build fat JAR without tests
mvn package -DskipTests
```

### Frontend (Angular 17)

```bash
cd frontend

# Install dependencies (first time or after package.json changes)
npm install

# Development server — proxies API calls to backend at :8080
npm start              # → http://localhost:4200

# Production build (outputs to src/main/resources/static/browser/)
npm run build

# Watch mode (iterative frontend development without Maven)
ng build --watch

# Run Angular unit tests
ng test
ng test --coverage
```

> **Angular dev server** at `:4200` proxies `/api/*` to `localhost:8080`, so the backend must be running separately when using `npm start`.

### Build pipeline

```
mvn package
  └─ generate-resources phase
       ├─ frontend-maven-plugin: installs Node 18.15.0 + npm 9.5.0
       ├─ npm install
       └─ npm run build → src/main/resources/static/browser/
  └─ compile phase: javac (56 Java source files)
  └─ package phase: spring-boot:repackage → fat JAR (backend + Angular)
```

---

## Project Structure

```
java-performance-analyzer/
├── pom.xml                               # Maven build descriptor
├── Dockerfile                            # Container image (eclipse-temurin:21-jre-alpine)
├── docker-compose.yml                    # Compose stack (4 GB memory limit)
├── usage.md                              # Quick-start and curl examples
│
├── docs/
│   ├── JVM-Performance-Analyzer-User-Manual.docx   # Full user manual (Word)
│   ├── functional-specification.md                  # Feature-level specification
│   └── technical-specification.md                   # Engineering reference
│
├── frontend/                             # Angular 17 SPA
│   ├── angular.json
│   ├── package.json
│   ├── tailwind.config.js
│   └── src/app/
│       ├── app.component.ts              # Root component (nav + router-outlet)
│       ├── app.routes.ts                 # Lazy-loaded route definitions
│       ├── models/
│       │   └── analysis.models.ts        # TypeScript interfaces (mirrors backend DTOs)
│       ├── services/
│       │   ├── api.service.ts            # All HTTP calls to /api/v1/*
│       │   ├── session-store.service.ts  # Client-side session registry (Signals)
│       │   ├── i18n.service.ts           # Multilingual UI (Signal-based, 6 languages)
│       │   ├── theme.service.ts          # Dark/light mode (localStorage)
│       │   └── ruleset.service.ts        # Custom rules persistence (localStorage)
│       └── views/
│           ├── dashboard/                # Upload + Health Score + session history
│           ├── hotspots/                 # Hotspot table + Solution Inspector
│           ├── call-tree/                # Recursive call tree renderer
│           ├── timeline/                 # Snapshot charts + swim lanes
│           ├── diff/                     # Session comparison
│           ├── gc/                       # GC log analysis
│           ├── heap/                     # Heap dump placeholder
│           └── settings/                 # Language + rules + thresholds
│
└── src/main/
    ├── java/com/devmanchego/performanceanalyzer/
    │   ├── PerformanceAnalyzerApplication.java
    │   ├── aggregation/                  # CallTreeBuilder, HotspotExtractor, CallTreeSerializer
    │   ├── analysis/                     # AnalysisOrchestrator (pipeline entry point)
    │   ├── api/                          # REST controllers + GlobalExceptionHandler
    │   ├── config/                       # WebConfig (CORS) + SpaController (SPA fallback)
    │   ├── diagnostic/                   # Rule engine + SignatureDictionary
    │   │   └── rules/                    # BlockedThreadsRule, DeadlockRule, AlgorithmicAlertsRule, GcThrashingRule
    │   ├── diff/                         # DiffService
    │   ├── export/                       # PdfExporter, DocxExporter, CsvExporter
    │   ├── health/                       # HealthScoreService
    │   ├── ingestion/                    # IngestionService (routes files to parsers)
    │   ├── metrics/                      # Micrometer bindings + WebhookService
    │   ├── model/                        # Java records (DTOs and domain models)
    │   ├── parsing/                      # FormatDetector, ThreadDumpParser, GcLogParser, JfrParser
    │   ├── session/                      # AnalysisSession + SessionStore
    │   └── timeline/                     # TimelineService
    └── resources/
        ├── application.yml               # All configuration
        ├── signatures.json               # Built-in package → layer mappings
        └── static/browser/               # Angular production build (generated)
```

---

## Technology Stack

### Backend

| Component | Technology | Version |
|-----------|-----------|---------|
| Runtime | Java | 21 (LTS) |
| Framework | Spring Boot | 3.3.0 |
| Web server | Apache Tomcat (embedded) | 10.1.x |
| JFR parsing | JDK built-in `jdk.jfr.consumer` | JDK 21 |
| PDF generation | iText 8 Community | 8.0.4 |
| DOCX generation | Apache POI OOXML | 5.2.5 |
| Metrics | Micrometer + Prometheus | 1.13.x |
| JSON | Jackson Databind | 2.17.x |
| Build | Maven | 3.8+ |

### Frontend

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Angular | 17 |
| Language | TypeScript | 5.x (strict) |
| Styling | Tailwind CSS | 3 (class-based dark mode) |
| Charts | Apache ECharts via ngx-echarts | 17.x |
| State | Angular Signals | built-in |
| Node (build only) | Node.js | 18.15.0 (pinned) |

### Infrastructure

| Component | Technology |
|-----------|-----------|
| Container base image | `eclipse-temurin:21-jre-alpine` |
| JVM GC (container) | ZGC (`-XX:+UseZGC`) |
| Memory sizing | `-XX:MaxRAMPercentage=75.0` |
| Orchestration | Docker Compose |

---

## Architecture

The application follows a **single-deployable monolith** pattern. Spring Boot serves both the REST API (`/api/v1/*`) and the Angular SPA (`/static/browser/`) from the same fat JAR on port 8080.

```
┌─────────────────────────────────────────────────────────┐
│                  Fat JAR  :8080                          │
│                                                          │
│  ┌───────────────────┐     ┌──────────────────────────┐ │
│  │  Angular SPA       │     │  Spring Boot REST API     │ │
│  │  /static/browser/  │     │  /api/v1/*                │ │
│  └───────────────────┘     └──────────┬───────────────┘ │
│                                        │                  │
│                             ┌──────────▼───────────┐     │
│                             │   In-Memory Store     │     │
│                             │   (SessionStore)      │     │
│                             │   ConcurrentHashMap   │     │
│                             └──────────────────────┘     │
└─────────────────────────────────────────────────────────┘
```

### Analysis pipeline

```
POST /upload
  └─ FormatDetector      → detects DumpType from file content
  └─ IngestionService    → routes each file to the correct parser
       ├─ ThreadDumpParser / SnapshotParser
       ├─ GcLogParser   (unified + legacy)
       └─ JfrParser     (jdk.jfr.consumer.RecordingFile)

POST /analyze/{id}
  └─ AnalysisOrchestrator
       ├─ CallTreeBuilder        → aggregates stack traces into tree
       ├─ HotspotExtractor       → extracts top Self% methods
       ├─ SignatureDictionary     → classifies methods into layers
       ├─ DiagnosticEngine        → runs all PerformanceRule beans
       │    ├─ BlockedThreadsRule
       │    ├─ DeadlockRule       → lock-graph DFS cycle detection
       │    ├─ AlgorithmicAlertsRule
       │    └─ GcThrashingRule
       ├─ HealthScoreService      → 0–100 weighted composite score
       ├─ TimelineService         → multi-snapshot state evolution
       └─ AnalysisResultDto       → cached in SessionStore
```

### Key design decisions

- **No database** — all state is held in `ConcurrentHashMap<UUID, AnalysisSession>` with a scheduled eviction task
- **No authentication** — designed for local / internal-network deployment; place behind a reverse proxy with auth if exposed externally
- **Rule extensibility** — `PerformanceRule` is a Spring `@Component` interface; adding a new rule requires zero changes to the orchestrator
- **Reactive i18n** — `I18nService.t(key)` reads an Angular Signal, making all template expressions that use `t()` automatically reactive on language change
- **JFR compatibility** — `JfrParser.resolveThread()` probes field names (`sampledThread` → `eventThread` → `thread`) before access to handle JDK version differences
- **SPA routing** — `SpaController` forwards all Angular client-side routes to `index.html`; static content is served from `classpath:/static/browser/`

---

## Diagnostic Rules

The rule engine runs automatically after every analysis. Built-in rules:

| Rule | Severity | Trigger |
|------|----------|---------|
| `BLOCKED_THREADS` | WARNING / CRITICAL | More than 20% of threads BLOCKED (configurable) |
| `DEADLOCK` | CRITICAL | Circular dependency in the lock graph |
| `LIST_LINEAR_SCAN` | WARNING | High Self Time on `ArrayList.indexOf`, `LinkedList` traversal |
| `STRING_CONCAT_LOOP` | WARNING | High Self Time on `StringBuilder.append` or `String.concat` |
| `REFLECTION_ABUSE` | WARNING | High Self Time on `java.lang.reflect.Method.invoke` |
| `FINALIZER_BACKLOG` | WARNING | `java.lang.ref.Finalizer` thread permanently RUNNABLE at top of stack |
| `GC_THRASHING` | WARNING / CRITICAL | GC time % exceeds configured threshold (default 10%) |

Custom rules can be added in the **Settings** view and are sent with every `/analyze` request as part of the `RulesetDto` payload.

---

## Internationalization

The UI is fully translated into six languages. The language can be changed at any time from the **Settings** view without page reload; the selection is persisted in `localStorage` under `pa.lang`.

| Code | Language |
|------|----------|
| `en` | English (default) |
| `es` | Spanish |
| `fr` | French |
| `de` | German |
| `pt` | Portuguese |
| `it` | Italian |

> **Note:** Diagnosis messages and algorithm recommendations generated by the backend are currently in English only. Full backend i18n is planned for v1.1.

---

## Session Management

- Sessions are identified by a UUID generated at upload time
- All session data is held in JVM memory — there is no database or disk write
- Sessions are automatically evicted after **120 minutes of inactivity** (configurable)
- The eviction task runs every 60 seconds
- On JVM restart, all sessions are lost; use the **JSON export** to archive results before shutdown

---

## Known Limitations (v1.0)

| Area | Limitation | Workaround |
|------|-----------|------------|
| Heap dump analysis | Eclipse MAT API is not on Maven Central; heap view shows instructions only | Use standalone Eclipse MAT |
| No authentication | All endpoints are open on the configured port | Deploy behind nginx/Traefik with basic auth or restrict to VPN |
| JFR allocation events | `jdk.ObjectAllocationInNewTLAB` events not yet parsed | Planned for v1.1 |
| Mobile layout | Minimum 1280 px viewport | Use desktop browser |
| Session persistence | Sessions lost on server restart | Export JSON before shutdown |
| Webhook retries | Fire-and-forget, no retry on delivery failure | Monitor server logs at WARN level |

---

<p align="center">
  Built with ☕ Java 21 + Angular 17 · DevManchego · June 2026
</p>
