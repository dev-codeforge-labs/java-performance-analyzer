# JVM Performance Analyzer — Usage Guide

## Quick Start

### Prerequisites

- Java 21+
- Maven 3.8+ (for building from source)
- Docker & Docker Compose (optional)
- Node.js 18+ (only for frontend development)

---

## Building & Running

### Option 1 — Full Maven Build (backend + frontend)

```bash
# Build the fat JAR (downloads Node 18 automatically, compiles Angular, packages everything)
mvn package -DskipTests

# Run the application
java -jar target/performance-analyzer-1.0.0.jar
```

Application available at: **http://localhost:8080**

---

### Option 2 — Skip frontend build (backend only)

```bash
mvn package -DskipTests -P skip-frontend

java -jar target/performance-analyzer-1.0.0.jar
```

---

### Option 3 — Docker Compose (recommended for production)

```bash
# Build the JAR first
mvn package -DskipTests

# Start with Docker Compose
docker-compose up --build

# Run in background
docker-compose up -d --build

# Stop
docker-compose down
```

Application available at: **http://localhost:8080**

---

### Option 4 — Docker only

```bash
# Build image
docker build -t performance-analyzer .

# Run container
docker run -p 8080:8080 -m 4g performance-analyzer
```

---

## Development Commands

### Backend

```bash
# Compile only
mvn compile

# Run tests
mvn test

# Run specific test class
mvn test -Dtest=ThreadDumpParserTest

# Start backend dev server (hot-reload via Spring DevTools)
mvn spring-boot:run

# Build without tests
mvn package -DskipTests
```

### Frontend (Angular 17)

```bash
cd frontend

# Install dependencies
npm install

# Start dev server (proxy to backend at localhost:8080)
npm start
# or
ng serve

# Production build
npm run build

# Build and watch
ng build --watch

# Run Angular tests
ng test
```

Frontend dev server: **http://localhost:4200**

---

## API Reference

Base URL: `http://localhost:8080/api/v1`

| Method | Endpoint | Description |
|--------|----------|-------------|
| `POST` | `/upload` | Upload dump files (multipart, max 500 MB each) |
| `DELETE` | `/session/{id}` | Delete a session |
| `DELETE` | `/session/clear` | Clear all sessions |
| `POST` | `/analyze/{sessionId}` | Run full analysis (body: RulesetDto JSON) |
| `GET` | `/analyze/{sessionId}` | Get cached analysis result |
| `POST` | `/diff` | Compare two sessions (`?sessionIdA=&sessionIdB=&deltaThreshold=`) |
| `GET` | `/export/pdf/{sessionId}` | Export PDF report |
| `GET` | `/export/json/{sessionId}` | Export JSON result |
| `GET` | `/export/csv/{sessionId}` | Export hotspots CSV |
| `GET` | `/status/latest` | Latest analysis metrics (Prometheus-compatible) |
| `GET` | `/actuator/health` | Health check |
| `GET` | `/actuator/prometheus` | Prometheus metrics endpoint |

### Upload Example (curl)

```bash
# Upload a thread dump
curl -X POST http://localhost:8080/api/v1/upload \
  -F "files=@/path/to/threaddump.txt" \
  -F "files=@/path/to/gc.log"

# Response: { "sessionId": "...", "fileNames": [...], "dumpTypes": [...] }
SESSION_ID="<uuid from response>"

# Run analysis
curl -X POST http://localhost:8080/api/v1/analyze/$SESSION_ID \
  -H "Content-Type: application/json" \
  -d '{"version":"1.0","customSignatures":[],"thresholds":{}}'
```

---

## User Manual

### Dashboard

The entry point for all analysis work.

**Uploading dumps:**
- Drag and drop one or more dump files onto the upload area, or click to open a file picker
- Supported formats: thread dumps (jstack output), GC logs (`-Xlog:gc*` unified or legacy `-verbose:gc`), JFR recordings (`.jfr`), heap dumps (`.hprof`)
- Maximum file size: 500 MB per file
- Multiple files from the same JVM process can be uploaded together (e.g., a thread dump + GC log)

**After upload:**
- The session is stored server-side for up to 120 minutes of inactivity
- Click **Analyze** to trigger the full analysis pipeline
- The **Health Score** widget shows a 0–100 score with a classification (Healthy / Warning / Critical) and breaks down the four contributing signals: blocked threads, deadlocks, critical diagnoses, and hotspot concentration
- **Stat cards** summarize thread count, blocked percentage, total snapshots, and GC pause P99
- **Critical diagnoses** are listed below the cards
- Use the export buttons to download a PDF report, raw JSON, or hotspot CSV

**Session history** at the bottom of the dashboard lets you reload previous analyses from the current browser session.

---

### Hotspot Panel

Shows the top CPU-consuming methods ranked by self-time percentage.

**Table columns:**
- **#** — Rank
- **Method** — Shortened method signature (hover for full name)
- **Layer** — Detected layer category: `Database / Network`, `ORM`, `JSON`, `Cryptography`, `HTTP / Web`, `JDK Core`, `Business Logic`
- **Self%** — Percentage of samples where this method was at the top of the stack (heat-colored: red ≥ 20%, orange ≥ 10%, amber ≥ 5%)
- **Total%** — Percentage of samples where this method appeared anywhere in the stack
- **Severity** — Diagnostic severity badge (CRITICAL / WARNING / INFO)

**Clicking a row** opens the **Solution Inspector** drawer on the right, showing:
- Full method signature
- Self-time and total-time values
- Built-in diagnosis message (if any)
- Top caller method

**Sorting:** Click any column header to sort ascending/descending.

**All Diagnoses** section below the table lists every diagnosis generated by the rule engine.

---

### Call Tree

Visualizes the aggregated call tree built from all stack traces.

**Controls:**
- **Search** — Filter visible nodes by method name (highlights matches)
- **Inverted** toggle — Switch between top-down (entry points at root) and bottom-up (hot methods at root) views
- **Expand / Collapse** — Click any node to expand or collapse its children

Each node shows the method name, self-time percentage, and total-time percentage. Deeper nodes are indented hierarchically.

---

### Timeline

Shows how thread states and hotspots evolved across multiple snapshots in a single thread dump file.

**Snapshot table** — lists each snapshot with timestamp, thread counts by state (RUNNABLE, BLOCKED, WAITING), and an inline bar chart for the state distribution.

**Hotspot Trends** — grid of the top methods and how their stack frequency changed snapshot by snapshot (up arrow = increasing, down arrow = decreasing).

**Thread Swim Lanes** — color-coded per-thread state timeline for the first 20 threads (green = RUNNABLE, red = BLOCKED, yellow = WAITING/TIMED_WAITING, gray = other).

A **Confirmed Deadlock** badge appears at the top if the same monitor appears in a circular wait across all snapshots.

---

### Diff

Compares two analysis sessions side-by-side to identify regressions or improvements.

**Session slots:**
- Use the **A** and **B** radio buttons to assign the active session to slot A or B
- Previously analyzed sessions can be assigned by switching the active session in the Dashboard and clicking the slot radio button

**Tabs:**
- **Call Tree Diff** — shows methods with status: ADDED (green), REMOVED (gray), REGRESSED (red, self-time increased), IMPROVED (green, self-time decreased), UNCHANGED
- **Hotspot Diff** — table of hotspot changes with trend arrows (UP / DOWN / NEW / RESOLVED / STABLE) and delta values
- **Diagnostics Diff** — new vs resolved vs common diagnoses between the two sessions

**Health Score Comparison** — shows score A vs score B with a delta badge (green = improvement, red = regression).

---

### GC Analyzer

Analyzes GC log files to surface garbage collection behavior.

**Summary cards:**
- Algorithm detected (G1GC, ZGC, Shenandoah, ParallelGC, SerialGC, or unknown)
- Maximum STW pause (ms)
- Average STW pause (ms)
- P99 STW pause (ms)
- Total GC time percentage

**Alerts:**
- **Promotion Failure** — old generation cannot accommodate promoted objects; indicates heap sizing or object lifetime issue
- **Concurrent Mode Failure** (CMS only) — concurrent collection could not finish before old gen filled up
- **GC Thrashing** — GC time exceeds the configured threshold (default 10%); suggests heap too small or allocation rate too high

**Algorithm Recommendation** — displayed when a suboptimal algorithm is detected for the workload.

**STW Events Table** — scrollable list of all stop-the-world events with timestamp, pause duration, heap before/after/total, and pause reason.

---

### Heap Dump (Placeholder)

Heap dump analysis requires the Eclipse Memory Analyzer Tool (MAT) API, which is not available on Maven Central. The session stores the raw heap dump bytes, and this view shows an informational message explaining how to open the dump in standalone Eclipse MAT for object retention, dominator tree, and leak suspect analysis.

---

### Settings / Custom Rules

Configure the analysis rule engine without redeploying.

**Custom Signatures:**
- Add package prefixes (e.g., `com.mycompany.db`) to map matching methods to a custom layer category
- Click **Add Signature**, fill in the package prefix, and assign a layer label
- Custom signatures take priority over built-in ones
- Click the trash icon to remove a signature

**Thresholds:**
- `blockedThreadsWarningPercent` (default 20) — Percentage of blocked threads that triggers a WARNING diagnosis
- `gcThrashingPercent` (default 10) — GC time percentage that triggers a GC Thrashing warning
- `hotspotDeltaThreshold` (default 5) — Minimum self-time change (%) to be reported in a diff

**Ruleset actions:**
- **Export** — Downloads the current ruleset as a `ruleset.json` file
- **Import** — Loads a previously exported ruleset JSON
- **Reset** — Restores all thresholds and custom signatures to defaults

The ruleset is persisted in `localStorage` under the key `pa.ruleset` and is sent with every `/analyze` request.

---

### Dark Mode

Click the moon/sun icon in the top navigation bar to toggle between light and dark themes. The preference is saved in `localStorage` under `pa.theme`.

---

## Configuration

Key settings in `src/main/resources/application.yml`:

```yaml
# File upload limits
spring.servlet.multipart.max-file-size: 500MB
spring.servlet.multipart.max-request-size: 550MB

# Session idle timeout
app.session.idle-minutes: 120

# Health score weights (must sum to 1.0)
app.health.weight.blocked: 0.30
app.health.weight.deadlock: 0.25
app.health.weight.diagnoses: 0.25
app.health.weight.hotspot: 0.20

# Webhook (optional)
app.webhook.url: ""        # POST JSON analysis results here

# PDF export
app.export.pdf.enabled: true
```

### JVM Tuning (Docker)

The Docker setup uses ZGC with `MaxRAMPercentage=75.0`. For large heap dumps, increase the container memory limit in `docker-compose.yml`:

```yaml
mem_limit: 8g  # increase from default 4g for very large files
```
