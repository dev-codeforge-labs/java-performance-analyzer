# JVM Performance Analyzer — Technical Specification

**Version:** 1.0  
**Date:** June 2026  
**Status:** Released

---

## Table of Contents

1. [Architecture Overview](#1-architecture-overview)
2. [Technology Stack](#2-technology-stack)
3. [Project Structure](#3-project-structure)
4. [Backend Modules](#4-backend-modules)
5. [REST API Reference](#5-rest-api-reference)
6. [Data Models](#6-data-models)
7. [Frontend Architecture](#7-frontend-architecture)
8. [Build System](#8-build-system)
9. [Configuration Reference](#9-configuration-reference)
10. [Deployment](#10-deployment)
11. [Testing](#11-testing)
12. [Security Considerations](#12-security-considerations)
13. [Known Limitations](#13-known-limitations)

---

## 1. Architecture Overview

The application follows a **single-deployable monolith** pattern: Spring Boot serves both the REST API and the Angular SPA from the same JAR file. There is no database — all state is held in-memory for the lifetime of the session.

```
┌─────────────────────────────────────────────────────┐
│                  Fat JAR (port 8080)                 │
│                                                       │
│  ┌────────────────────┐   ┌─────────────────────┐   │
│  │   Angular SPA       │   │   Spring Boot API    │   │
│  │  (static/browser/)  │   │   (/api/v1/*)        │   │
│  └────────────────────┘   └─────────────────────┘   │
│                                     │                 │
│                            ┌────────┴────────┐        │
│                            │  In-Memory Store  │       │
│                            │  (SessionStore)   │       │
│                            └──────────────────┘       │
└─────────────────────────────────────────────────────┘
```

### Request Flow

```
Browser → GET /          → Spring serves Angular index.html
Browser → GET /api/v1/*  → Spring REST controllers
Angular  → POST /api/v1/upload → IngestionService → parsers
Angular  → POST /api/v1/analyze/{id} → AnalysisOrchestrator → pipeline
Angular  → GET  /api/v1/export/pdf/{id} → PdfExporter → binary response
```

### Session Lifecycle

1. `POST /upload` creates an `AnalysisSession` (UUID key) in `SessionStore`.
2. `POST /analyze/{id}` runs the pipeline and stores `AnalysisResultDto` in a result cache.
3. All subsequent reads (`GET /analyze`, exports, diff) retrieve from the cache.
4. A scheduled task evicts sessions idle for > 120 minutes.
5. On JVM restart all sessions are lost (no persistence layer).

---

## 2. Technology Stack

### Backend

| Component | Technology | Version |
|-----------|-----------|---------|
| Runtime | Java | 21 (LTS) |
| Framework | Spring Boot | 3.3.0 |
| Web server | Apache Tomcat (embedded) | 10.1.24 |
| JSON | Jackson Databind | 2.17.x |
| PDF generation | iText 8 Community | 8.0.4 |
| DOCX generation | Apache POI OOXML | 5.2.5 |
| JFR parsing | JDK built-in `jdk.jfr.consumer` | JDK 21 |
| Metrics | Micrometer + Prometheus registry | 1.13.x |
| Build | Maven | 3.8+ |

### Frontend

| Component | Technology | Version |
|-----------|-----------|---------|
| Framework | Angular | 17.x |
| Language | TypeScript | 5.x (strict mode) |
| Styling | Tailwind CSS | 3.x (class-based dark mode) |
| Charts | Apache ECharts via ngx-echarts | 17.x |
| State | Angular Signals | built-in |
| Node (build only) | Node.js | 18.15.0 (pinned) |
| Package manager | npm | 9.5.0 (pinned) |

### Infrastructure

| Component | Technology |
|-----------|-----------|
| Container runtime | Docker |
| Base image | `eclipse-temurin:21-jre-alpine` |
| JVM GC (container) | ZGC |
| Orchestration | Docker Compose |

---

## 3. Project Structure

```
java-performance-analyzer/
├── pom.xml                          # Maven build descriptor
├── Dockerfile                       # Container image definition
├── docker-compose.yml               # Compose stack (single service)
├── README.md                        # GitHub project page
├── .gitignore                       # Excludes target/, node_modules/, JVM dumps, IDE files
├── usage.md                         # User-facing startup guide
├── docs/
│   ├── functional-specification.md               # This document's counterpart
│   ├── technical-specification.md                # This document
│   └── JVM-Performance-Analyzer-User-Manual.docx # End-user manual (18 chapters)
├── frontend/                        # Angular 17 SPA
│   ├── package.json
│   ├── angular.json
│   ├── tailwind.config.js
│   └── src/app/
│       ├── app.component.ts         # Root component (nav + router)
│       ├── app.routes.ts            # Lazy-loaded route definitions
│       ├── models/
│       │   └── analysis.models.ts   # TypeScript interfaces (mirrors backend DTOs)
│       ├── services/
│       │   ├── api.service.ts       # All HTTP calls to /api/v1/*
│       │   ├── session-store.service.ts  # Client-side session registry
│       │   ├── ruleset.service.ts   # Custom rules persistence (localStorage)
│       │   ├── theme.service.ts     # Dark/light mode toggle (localStorage)
│       │   └── i18n.service.ts      # Multilingual UI (Signal-based, localStorage)
│       └── views/
│           ├── dashboard/           # Upload + health score + export
│           ├── hotspots/            # Hotspot table + solution inspector
│           ├── call-tree/           # Recursive tree renderer
│           ├── timeline/            # Snapshot charts + swim lanes
│           ├── diff/                # Session comparison
│           ├── gc/                  # GC log analysis
│           ├── heap/                # Heap dump placeholder
│           └── settings/            # Language + rules + thresholds
└── src/main/
    ├── java/com/devmanchego/performanceanalyzer/
    │   ├── PerformanceAnalyzerApplication.java
    │   ├── aggregation/             # Call tree building and hotspot extraction
    │   ├── analysis/                # AnalysisOrchestrator (pipeline entry point)
    │   ├── api/                     # REST controllers + GlobalExceptionHandler
    │   ├── config/                  # WebConfig (CORS) + SpaController (SPA fallback)
    │   ├── diagnostic/              # Rule engine + signature dictionary
    │   ├── diff/                    # Diff computation service
    │   ├── export/                  # PdfExporter + DocxExporter + CsvExporter
    │   ├── health/                  # HealthScoreService
    │   ├── ingestion/               # IngestionService (routes files to parsers)
    │   ├── metrics/                 # Micrometer bindings + WebhookService
    │   ├── model/                   # Java records (DTOs and domain models)
    │   ├── parsing/                 # Format detection + parsers
    │   ├── session/                 # AnalysisSession + SessionStore
    │   └── timeline/                # Timeline aggregation service
    └── resources/
        ├── application.yml          # All configuration
        ├── signatures.json          # Built-in package→layer mappings
        └── static/browser/          # Angular build output (generated by Maven build)
```

---

## 4. Backend Modules

### 4.1 Parsing Layer

**Package:** `com.devmanchego.performanceanalyzer.parsing`

#### FormatDetector

Reads the first 12 bytes (binary header check) and up to 20 text lines (pattern matching) to determine `DumpType` without relying on file extension.

```java
public DumpType detect(InputStream input, String filename) throws IOException
```

Detection order:
1. JFR magic bytes (`FLR\0` → `DumpType.JFR_RECORDING`)
2. HPROF magic string (`JAVA PROFILE` → `DumpType.HEAP_DUMP`)
3. Thread dump signal patterns (`Full thread dump`, `java.lang.Thread.State:`)
4. GC log signal patterns (`[N.NNs][...gc...]`, `[Full GC`, `[GC`)
5. Extension fallback (`.hprof`, `.jfr`)
6. Default: `THREAD_DUMP`

#### ThreadDumpParser

Splits raw text into snapshot blocks delimited by the `Full thread dump` header. Delegates each block to `SnapshotParser` which extracts individual threads.

- **Thread attributes parsed:** name, id, daemon flag, priority, `nid` (OS thread id), `java.lang.Thread.State`, waiting-on monitor address, locked monitors list, stack frames.
- **Stack trace sanitization:** `StackTraceSanitizer` strips CGLIB, Spring AOP, reflection, and lambda synthetic frames.
- Compiles `static final Pattern` constants at class load — never creates `Pattern.compile()` inside loops.

#### GcLogParser

Dual-mode parser supporting:
- **Unified format** (JDK 9+): `[N.NNNs][info][gc] GC(N) Pause Young (N) ...ms`
- **Legacy format** (JDK 8): `N.NNN: [GC (Allocation Failure) ...ms]`

Auto-detects format from the first 10 lines. Outputs `GcLogResult` containing:
- List of `GcEventDto` (timestamp, type, pauseMs, heapBefore/After/Total bytes)
- Aggregate statistics (maxPauseMs, avgPauseMs, p99PauseMs, gcTimePercent)
- Alert flags (promotionFailureDetected, concurrentModeFailureDetected)
- Detected algorithm

#### JfrParser

Uses the JDK built-in `jdk.jfr.consumer.RecordingFile` API (no external dependency).

**Algorithm:**
1. Copy the `InputStream` to a temp file (required by the API).
2. Iterate events, collecting `jdk.ExecutionSample`, `jdk.JavaMonitorEnter`, `jdk.JavaMonitorWait`, `jdk.ThreadPark`.
3. Group events into 1-second windows (`epochMs / 1000 * 1000`).
4. Each window becomes a `SnapshotResult` — one `ThreadInfo` per unique thread, using the last seen sample for that thread.
5. Thread state is derived from event type: `ExecutionSample` → RUNNABLE, `MonitorEnter` → BLOCKED, `MonitorWait` → WAITING, `ThreadPark` → TIMED_WAITING.
6. Thread field resolution uses `hasField()` guard before access to handle JDK version differences (`sampledThread`, `eventThread`, `thread`).

---

### 4.2 Aggregation Engine

**Package:** `com.devmanchego.performanceanalyzer.aggregation`

#### CallTreeBuilder

Merges all thread stack traces into a single `CallTreeNode` tree.

- Each snapshot's threads contribute their stack traces top-to-bottom.
- Tree traversal is `O(depth)` per frame using `ConcurrentHashMap<String, CallTreeNode>` children for `O(1)` lookup at each level.
- `selfSamples` is incremented only for the top-of-stack frame of each thread.
- `samples` is incremented for every frame in the path.

#### HotspotExtractor

Traverses the call tree recursively, collects all nodes where `selfSamples > 0`, computes `selfTimePercent = selfSamples / totalSamples * 100`, sorts descending, assigns rank.

#### CallTreeSerializer

Converts `CallTreeNode` to `CallTreeNodeDto` for JSON serialization. Applies a configurable max depth (default 200) to prevent stack overflow on pathological inputs. Inverted tree is built by reversing child→parent relationships.

---

### 4.3 Diagnostic Engine

**Package:** `com.devmanchego.performanceanalyzer.diagnostic`

#### Rule Architecture

Interface: `PerformanceRule`
```java
List<Diagnosis> analyze(AnalysisContext ctx);
```

Rules are Spring `@Component` beans collected via constructor injection into `DiagnosticEngine`. Adding a new rule requires only annotating a new class — no changes to the engine.

#### Built-in Rules

| Rule Class | Detection Logic |
|-----------|----------------|
| `BlockedThreadsRule` | Fires WARNING/CRITICAL when blocked thread % exceeds configured threshold |
| `DeadlockRule` | Builds lock graph (blocked→holder adjacency), runs DFS cycle detection |
| `AlgorithmicAlertsRule` | Matches hotspot signatures against inefficiency patterns (ArrayList.contains, StringBuilder.append, Method.invoke, Finalizer) |
| `GcThrashingRule` | Reads GC summary from session; fires WARNING if gcTimePercent > threshold |

#### SignatureDictionary

Loaded from `src/main/resources/signatures.json` at startup. Provides:
- `resolveLayer(packagePrefix, ruleset)` — looks up user custom signatures first, then built-in entries.
- `resolveDiagnosis(packagePrefix, ruleset)` — returns the diagnosis message for a matched signature.

User signatures (from the `RulesetDto` sent with each analyze request) are merged at runtime with higher priority. No server-side persistence of user rules.

#### Lock Graph (DeadlockRule)

```
For each snapshot:
  Pass 1: build monitorAddress → holderThreadId map (from "locked <0x...>" lines)
  Pass 2: build blockedThreadId → monitorAddress map (from "waiting to lock <0x...>")

Lock graph: blockedThreadId → holderThreadId (via monitor address)
Cycle detection: DFS with visited set and recursion stack
```

---

### 4.4 Analysis Orchestrator

**Package:** `com.devmanchego.performanceanalyzer.analysis`

`AnalysisOrchestrator.analyze(UUID sessionId, RulesetDto ruleset)` is the single entry point for the analysis pipeline:

```
Session → snapshots
         ↓
    CallTreeBuilder.build()
         ↓
    applyLayerCategories() [SignatureDictionary + user ruleset]
         ↓
    HotspotExtractor.extract()
         ↓
    enrichHotspots() [adds diagnosis text from SignatureDictionary]
         ↓
    buildLockGraph()
         ↓
    DiagnosticEngine.analyze() [all registered PerformanceRule beans]
         ↓
    HealthScoreService.compute()
         ↓
    CallTreeSerializer.serialize() + serializeInverted()
         ↓
    TimelineService.build()
         ↓
    AnalysisResultDto [cached in ConcurrentHashMap<UUID, AnalysisResultDto>]
```

The result is cached for the life of the server process. Re-analysis of the same session ID returns the cached result without re-running the pipeline.

---

### 4.5 Export Layer

**Package:** `com.devmanchego.performanceanalyzer.export`

#### PdfExporter

Uses iText 8 Community via dynamic class loading (`Class.forName()`). This allows the backend to compile without iText on the classpath if the dependency is excluded, falling back to a plain-text response. In practice, iText is included in the fat JAR.

Generates:
- Title paragraph (bold, large)
- Metadata (analyzed date, file names, dump types)
- Health score section
- Top 10 hotspot table
- Diagnoses list
- GC summary (when present)

#### DocxExporter

Uses Apache POI OOXML (`XWPFDocument`, `XWPFParagraph`, `XWPFRun`, `XWPFTable`). Generates the same content structure as the PDF in `.docx` format. The document is streamed to a `ByteArrayOutputStream` and returned as `application/vnd.openxmlformats-officedocument.wordprocessingml.document`.

#### CsvExporter

Generates a UTF-8 BOM-free CSV file with headers:
```
rank,methodSignature,className,packagePrefix,layer,selfTimePercent,totalTimePercent,diagnosis,severity
```

---

### 4.6 Health Score Engine

**Package:** `com.devmanchego.performanceanalyzer.health`

`HealthScoreService.compute()` takes four primitive inputs and applies weighted linear interpolation:

```java
double blockedScore    = clamp(1.0 - blockedPct / 50.0) * 100;
double deadlockScore   = confirmedDeadlock ? 0 : 100;
double diagScore       = clamp(1.0 - criticalCount / 5.0) * 100;
double hotspotScore    = clamp(1.0 - (topSelfTime - 10.0) / 40.0) * 100;

double finalScore = w1*blockedScore + w2*deadlockScore + w3*diagScore + w4*hotspotScore;
```

Weights are injected from `application.yml` via `@Value`. The dominant factor is the signal with the largest raw penalty (`(1 - normalizedScore) * weight`).

---

### 4.7 Session Management

**Package:** `com.devmanchego.performanceanalyzer.session`

#### SessionStore

- `ConcurrentHashMap<UUID, AnalysisSession>` — thread-safe without explicit locking.
- `@Scheduled(fixedRate = 60_000)` cleanup task evicts sessions where `lastAccessedAt` is older than the configured idle timeout (default 120 minutes).
- `get(uuid)` updates `lastAccessedAt` on each read.

#### AnalysisSession

```java
public class AnalysisSession {
    UUID id;
    List<String> fileNames;
    List<DumpType> dumpTypes;
    ThreadDumpResult threadDumpResult;   // null if no thread/JFR dump uploaded
    GcLogResult gcLogResult;             // null if no GC log uploaded
    byte[] heapRaw;                      // raw .hprof bytes, placeholder
    Instant createdAt;
    Instant lastAccessedAt;
}
```

---

### 4.8 Metrics & Integration

**Package:** `com.devmanchego.performanceanalyzer.metrics`

#### AnalysisMetricsService

After each analysis, binds results to Micrometer gauges:
- `pa_health_score`
- `pa_threads_total`
- `pa_threads_blocked_percent`
- `pa_hotspot_top_self_time_percent` (tag: `method`)
- `pa_diagnoses_critical_count`
- `pa_diagnoses_warning_count`

These are scraped by Prometheus via `/actuator/prometheus`.

#### WebhookService

Sends a fire-and-forget HTTP POST to a configured webhook URL asynchronously using a virtual thread. Timeout: 5 seconds. No retry. Failures are logged at `WARN` level.

---

## 5. REST API Reference

**Base URL:** `/api/v1`  
**Content-Type:** `application/json` for all requests and responses, except file uploads (`multipart/form-data`) and exports (binary).

### Endpoints

#### Upload

```
POST /api/v1/upload
Content-Type: multipart/form-data

Body: files=<file1>&files=<file2>...
```

Response `200 OK`:
```json
{
  "sessionId": "550e8400-e29b-41d4-a716-446655440000",
  "fileNames": ["threaddump.txt", "gc.log"],
  "detectedTypes": ["THREAD_DUMP", "GC_LOG"],
  "warnings": []
}
```

#### Analyze

```
POST /api/v1/analyze/{sessionId}
Content-Type: application/json

Body: RulesetDto (see Data Models)
```

Response `200 OK`: `AnalysisResultDto` (see Data Models)

#### Get Cached Result

```
GET /api/v1/analyze/{sessionId}
```

Response `200 OK`: `AnalysisResultDto` if cached, `404` if not yet analyzed.

#### Diff

```
POST /api/v1/diff?sessionIdA={uuid}&sessionIdB={uuid}&deltaThreshold={float}
```

Response `200 OK`: `DiffResultDto`

#### Export — PDF

```
GET /api/v1/export/pdf/{sessionId}
Response: 200 OK, Content-Type: application/pdf
Content-Disposition: attachment; filename="analysis-{id}.pdf"
```

#### Export — DOCX

```
GET /api/v1/export/docx/{sessionId}
Response: 200 OK
Content-Type: application/vnd.openxmlformats-officedocument.wordprocessingml.document
Content-Disposition: attachment; filename="analysis-{id}.docx"
```

#### Export — JSON

```
GET /api/v1/export/json/{sessionId}
Response: 200 OK, Content-Type: application/json
Content-Disposition: attachment; filename="analysis-{id}.json"
```

#### Export — CSV

```
GET /api/v1/export/csv/{sessionId}/hotspots
Response: 200 OK, Content-Type: text/csv
Content-Disposition: attachment; filename="hotspots-{id}.csv"
```

#### Delete Session

```
DELETE /api/v1/session/{sessionId}
Response: 204 No Content
```

#### Clear All Sessions

```
DELETE /api/v1/session/clear
Response: 204 No Content
```

#### Status (Latest Analysis)

```
GET /api/v1/status/latest
Response: 200 OK, application/json
```

#### Actuator

```
GET /actuator/health        → Spring Boot health check
GET /actuator/prometheus    → Prometheus scrape endpoint
GET /actuator/metrics       → Micrometer metrics JSON
```

### Error Responses

All errors return a structured JSON body:
```json
{
  "errorCode": "BAD_REQUEST | NOT_FOUND | FILE_TOO_LARGE | INTERNAL_ERROR",
  "message": "Human-readable description",
  "timestamp": "2026-06-14T10:00:00.000Z"
}
```

`NoResourceFoundException` is handled explicitly and returns `404` without stack-trace logging.

---

## 6. Data Models

### AnalysisResultDto

```java
record AnalysisResultDto(
    UUID sessionId,
    Instant analyzedAt,
    List<String> fileNames,
    List<String> dumpTypes,
    int snapshotCount,
    int totalThreads,
    CallTreeNodeDto callTree,
    CallTreeNodeDto invertedCallTree,
    List<HotspotDto> hotspots,
    List<Diagnosis> diagnoses,
    HealthScoreDto healthScore,
    TimelineDto timeline,
    GcLogResult gcSummary,
    List<ParseWarning> warnings
)
```

### HotspotDto

```java
record HotspotDto(
    int rank,
    String methodSignature,
    String className,
    String packagePrefix,
    String layerCategory,
    double selfTimePercent,
    double totalTimePercent,
    String topCallerSignature,
    String diagnosis,
    String severity
)
```

### Diagnosis

```java
record Diagnosis(
    String ruleId,
    String severity,     // "CRITICAL" | "WARNING" | "INFO"
    String message,
    String affectedMethod
)
```

### HealthScoreDto

```java
record HealthScoreDto(
    int score,
    String classification,   // "GREEN" | "YELLOW" | "RED"
    String dominantFactor,
    List<SignalBreakdown> signalBreakdown
)
```

### GcLogResult

```java
record GcLogResult(
    String gcAlgorithm,
    double gcTimePercent,
    double maxPauseMs,
    double avgPauseMs,
    double p99PauseMs,
    boolean promotionFailureDetected,
    boolean concurrentModeFailureDetected,
    List<GcEventDto> events,
    List<ParseWarning> warnings
)
```

### RulesetDto (request body for /analyze)

```java
record RulesetDto(
    String version,
    List<CustomSignature> customSignatures,
    Map<String, Double> thresholds,
    String exportedAt
)
```

### ThreadInfo

```java
record ThreadInfo(
    String id,
    String name,
    boolean daemon,
    int priority,
    String nid,
    ThreadState state,
    String waitingOnMonitor,
    List<String> lockedMonitors,
    List<StackFrame> stackTrace
)
```

### StackFrame

```java
record StackFrame(
    String className,
    String methodName,
    String sourceFile,
    int lineNumber,
    boolean synthetic
)
```

---

## 7. Frontend Architecture

### Component Architecture

All views are **standalone Angular components** — no NgModules. Each view is lazy-loaded via the router, minimizing the initial bundle.

```typescript
// app.routes.ts
const routes: Routes = [
  { path: 'dashboard', loadComponent: () => import('./views/dashboard/...') },
  { path: 'hotspots',  loadComponent: () => import('./views/hotspots/...') },
  // ...
  { path: '**', redirectTo: 'dashboard' }
];
```

### State Management

Angular Signals are used exclusively — no NgRx, no BehaviorSubject, no shared mutable state outside services.

```typescript
// Pattern used across all views
private r = computed(() => this.store.getActiveResult());
hotspots = computed(() => this.r()?.hotspots ?? []);
sessionId = computed(() => this.r()?.sessionId ?? null);
```

`SessionStoreService` maintains:
- `sessions: Signal<SessionEntry[]>` — all uploaded sessions in the current browser session
- `activeSessionId: WritableSignal<string | null>` — currently viewed session
- `slotA/slotB: WritableSignal<string | null>` — diff engine slot assignments

### i18n Service

```typescript
@Injectable({ providedIn: 'root' })
export class I18nService {
  currentLang = signal<Lang>('en');

  t(key: string): string {
    const lang = this.currentLang(); // reads signal → reactive
    return T[lang]?.[key] ?? T['en']?.[key] ?? key;
  }
}
```

Because `t()` reads the `currentLang` signal internally, any template expression containing `{{ i18n.t('key') }}` is automatically re-evaluated when the language changes — without `ChangeDetectionStrategy.OnPush` or manual subscription.

### Theme Service

```typescript
toggle(): void {
  const next = !this.isDark();
  this.isDark.set(next);
  localStorage.setItem('pa.theme', next ? 'dark' : 'light');
  document.documentElement.classList.toggle('dark', next);
}
```

Tailwind CSS is configured with `darkMode: 'class'` so all `dark:` variants respond to the `dark` class on `<html>`.

### HTTP Layer

`ApiService` wraps all backend calls. Download endpoints (PDF, DOCX, JSON, CSV) are exposed as URL string methods rather than `Observable` subscriptions, allowing `<a href>` links for native browser download behavior:

```typescript
exportPdfUrl(sessionId: string): string {
  return `/api/v1/export/pdf/${sessionId}`;
}
exportDocxUrl(sessionId: string): string {
  return `/api/v1/export/docx/${sessionId}`;
}
```

### SPA Routing

`SpaController` (Spring) forwards named Angular routes to `index.html`:

```java
@RequestMapping(value = { "/", "/dashboard", "/hotspots", "/call-tree",
                           "/timeline", "/diff", "/gc", "/heap", "/settings" })
public String forwardToIndex() { return "forward:/index.html"; }
```

Angular's `index.html` is served from `classpath:/static/browser/` (set in `application.yml` via `spring.web.resources.static-locations`).

---

## 8. Build System

### Maven Build Pipeline

```
generate-resources phase:
  1. frontend-maven-plugin installs Node 18.15.0 + npm 9.5.0 to target/
  2. npm install (frontend/node_modules/)
  3. npm run build -- --output-path=../src/main/resources/static
     → Uses development configuration by default (source maps enabled, no minification)
     → Produces frontend/dist/, copied to src/main/resources/static/browser/

compile phase:
  4. javac compiles all 56 Java source files to target/classes/

package phase:
  5. jar:jar creates target/performance-analyzer-1.0.0.jar (thin)
  6. spring-boot:repackage creates fat JAR embedding all dependencies in BOOT-INF/
```

### Key Build Commands

```bash
# Full build (Angular + Java)
mvn package -DskipTests

# Java only (skip Angular build, use cached static/)
mvn package -DskipTests -pl . -am

# Run tests
mvn test

# Run development backend (no Angular rebuild)
mvn spring-boot:run
```

### Angular Build

```bash
cd frontend

# Install (first time or after package.json changes)
npm install

# Development server (proxy to :8080)
npm start                    # → http://localhost:4200

# Production build
npm run build                # output to src/main/resources/static/browser/

# Watch mode (development iteration without Maven)
ng build --watch
```

### Node Version Pinning

Angular 17 requires Node ≥ 18 and < 22. The `frontend-maven-plugin` is configured to install Node 18.15.0 specifically to avoid Angular CLI incompatibilities with Node 22+. This version is pinned in `pom.xml`:

```xml
<nodeVersion>v18.15.0</nodeVersion>
<npmVersion>9.5.0</npmVersion>
```

---

## 9. Configuration Reference

All configuration is in `src/main/resources/application.yml`.

```yaml
spring:
  servlet:
    multipart:
      max-file-size: 500MB          # Per-file upload limit
      max-request-size: 550MB       # Total request limit (files + form metadata)
  mvc:
    async:
      request-timeout: 300000       # 5 min timeout for large file parsing
  web:
    resources:
      static-locations:             # Serve Angular from browser/ subdirectory
        - classpath:/static/browser/
        - classpath:/static/

server:
  port: 8080

performanceanalyzer:
  session:
    idle-timeout-minutes: 120       # Session eviction after this idle period

  healthscore:
    weights:
      blocked-threads: 0.30         # Weight of BLOCKED% signal (must sum to 1.0)
      deadlock: 0.25
      critical-diagnoses: 0.25
      hotspot-concentration: 0.20

  thresholds:
    min-self-time-critical: 5.0     # Self% above which hotspot is flagged
    blocked-threads-alert: 20.0     # BLOCKED% above which contention rule fires
    gc-thrash-alert: 10.0           # GC time% above which thrashing rule fires
    deadlock-confirmation-threshold: 1.0   # Fraction of snapshots where blocked = confirmed
    calltree-max-depth: 200         # Serialization depth limit
    heap-streaming-threshold-bytes: 2147483648   # 2 GB

  integrations:
    webhook:
      url: ""                       # Empty = disabled
      api-key: ""
      timeout-ms: 5000

  export:
    pdf:
      top-hotspots-count: 10        # Max hotspots in PDF/DOCX report

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,prometheus
```

---

## 10. Deployment

### Option 1 — Fat JAR

```bash
mvn package -DskipTests
java -jar target/performance-analyzer-1.0.0.jar
```

Minimum JVM flags for production:
```bash
java -XX:+UseZGC \
     -XX:MaxRAMPercentage=75.0 \
     -jar target/performance-analyzer-1.0.0.jar
```

### Option 2 — Docker

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/performance-analyzer-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:+UseZGC", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
```

```bash
# Build JAR first
mvn package -DskipTests

# Build image
docker build -t performance-analyzer .

# Run
docker run -p 8080:8080 -m 4g performance-analyzer
```

### Option 3 — Docker Compose

```bash
mvn package -DskipTests
docker-compose up --build       # Foreground
docker-compose up -d --build    # Background
docker-compose down             # Stop and remove containers
```

`docker-compose.yml` sets `mem_limit: 4g`. Increase to `8g` for dumps > 200 MB.

### Environment Variables

Override configuration without rebuilding:
```bash
docker run -p 8080:8080 -m 8g \
  -e JAVA_OPTS="-XX:+UseZGC -XX:MaxRAMPercentage=75.0" \
  -e SPRING_PERFORMANCEANALYZER_SESSION_IDLE_TIMEOUT_MINUTES=240 \
  performance-analyzer
```

### JVM Tuning Recommendations

| Scenario | Recommendation |
|----------|---------------|
| Default workload | `-XX:+UseZGC -XX:MaxRAMPercentage=75.0` |
| Very large dumps (> 200 MB) | Increase container `mem_limit` to 8g |
| High concurrency | Add `-Djava.util.concurrent.ForkJoinPool.common.parallelism=4` |
| Debug startup | Add `-verbose:class -XX:+PrintCommandLineFlags` |

---

## 11. Testing

### Backend Tests

```bash
mvn test                                      # All tests
mvn test -Dtest=ThreadDumpParserTest          # Single class
mvn test -Dtest=ThreadDumpParserTest#parse*   # Single method pattern
```

Test packages mirror the source structure under `src/test/java/`. Tests use Spring Boot's `@SpringBootTest` for integration tests and plain JUnit 5 + Mockito for unit tests.

### Frontend Tests

```bash
cd frontend
ng test                     # Unit tests (Karma/Jasmine)
ng test --coverage          # With coverage report
```

### Manual Testing Checklist

After any change to parsing or analysis:

1. Upload a **jstack thread dump** → verify hotspots appear.
2. Upload a **GC log** (unified format) → verify GC Analyzer shows algorithm + pauses.
3. Upload a **JFR file** (recorded with `settings=profile`) → verify call tree is populated.
4. Upload **both a thread dump and GC log** in one operation → verify both views populate.
5. Assign two sessions to Diff slots → verify diff computes and shows changes.
6. Export PDF and DOCX → verify downloads open correctly.
7. Change language in Settings → verify nav and view labels update.

---

## 12. Security Considerations

### No Authentication (By Design)

The application is designed for local or internal-network deployment. There is no Spring Security dependency. All endpoints are publicly accessible on the configured port.

For externally exposed deployments, place the application behind a reverse proxy (nginx, Traefik) with basic authentication or VPN restriction.

### Data Handling

- Uploaded files are read into a `byte[]` in memory and never written to disk by the application (Spring's multipart uses OS temp storage automatically).
- No user data is stored persistently. Sessions are evicted from memory after 120 minutes of inactivity.
- No data is sent to external services unless a webhook URL is explicitly configured.

### CORS

In development, CORS allows `http://localhost:4200` (Angular dev server) and `http://localhost:8080`. In production (fat JAR), the Angular frontend is served from the same origin, so CORS headers are irrelevant for browser-to-server communication.

### Input Validation

- File size is validated client-side (< 500 MB) and server-side (Spring multipart limits).
- All uploaded content is treated as opaque bytes; no user input is evaluated as code.
- `IllegalArgumentException` from parsers returns `400 Bad Request` with a safe error message.

---

## 13. Known Limitations

| Area | Limitation | Workaround / Plan |
|------|-----------|-------------------|
| Heap dump analysis | Eclipse MAT API not on Maven Central; placeholder shown | Install standalone Eclipse MAT for `.hprof` analysis |
| Session persistence | All data lost on server restart | Export JSON before shutdown for archival |
| Concurrent heavy uploads | Single JVM; large dumps (> 300 MB) block the parsing thread | Upload one file at a time; increase container memory |
| JFR allocation events | `jdk.ObjectAllocationInNewTLAB` not yet parsed | Full allocation hotspot analysis planned for v1.1 |
| Mobile layout | Minimum 1280 px viewport; mobile not supported | Not in scope for v1 |
| PDF styling | iText Community edition; limited table styling | Replace with iText AGPL-licensed version for richer formatting |
| Diagnosis i18n | Diagnosis messages and algorithm recommendations remain in English | Full i18n of backend-generated strings planned for v1.1 |
| Webhook retries | Fire-and-forget, no retry on failure | Implement exponential backoff in v1.1 |
