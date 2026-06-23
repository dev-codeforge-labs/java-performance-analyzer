package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.*;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordedFrame;
import jdk.jfr.consumer.RecordedStackTrace;
import jdk.jfr.consumer.RecordedThread;
import jdk.jfr.consumer.RecordingFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.*;

/**
 * Parses JFR recordings using the JDK's built-in RecordingFile API (Java 14+).
 *
 * Groups jdk.ExecutionSample events into synthetic "snapshots" by time window
 * so the rest of the pipeline (call tree, hotspots, timeline) works unchanged.
 *
 * Each unique thread observed in a sampling window becomes a ThreadInfo entry
 * with its stack trace, state RUNNABLE (JFR only samples running threads by default).
 */
@Component
public class JfrParser {

    private static final Logger log = LoggerFactory.getLogger(JfrParser.class);

    // Group samples into windows of this size to create synthetic snapshots
    private static final long WINDOW_MS = 1_000;

    // Event type names
    private static final String EXECUTION_SAMPLE = "jdk.ExecutionSample";
    private static final String THREAD_PARK      = "jdk.ThreadPark";
    private static final String MONITOR_ENTER    = "jdk.JavaMonitorEnter";
    private static final String MONITOR_WAIT     = "jdk.JavaMonitorWait";

    public ThreadDumpResult parse(InputStream input) throws IOException {
        // RecordingFile requires a real file path — write to a temp file
        Path tmp = Files.createTempFile("pa-jfr-", ".jfr");
        try {
            Files.write(tmp, input.readAllBytes());
            return parseFile(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private ThreadDumpResult parseFile(Path path) throws IOException {
        // Collect all execution samples grouped by 1-second windows
        // key: window start epoch-ms (floored to WINDOW_MS)
        Map<Long, List<RecordedEvent>> windows = new TreeMap<>();
        List<ParseWarning> globalWarnings = new ArrayList<>();
        int totalEvents = 0;

        try (RecordingFile rf = new RecordingFile(path)) {
            while (rf.hasMoreEvents()) {
                RecordedEvent event = rf.readEvent();
                String name = event.getEventType().getName();

                if (EXECUTION_SAMPLE.equals(name)) {
                    long windowKey = floorToWindow(event.getStartTime().toEpochMilli());
                    windows.computeIfAbsent(windowKey, k -> new ArrayList<>()).add(event);
                    totalEvents++;
                }
                // THREAD_PARK / MONITOR_ENTER / MONITOR_WAIT sampled for BLOCKED/WAITING state
                else if (THREAD_PARK.equals(name) || MONITOR_ENTER.equals(name) || MONITOR_WAIT.equals(name)) {
                    long windowKey = floorToWindow(event.getStartTime().toEpochMilli());
                    windows.computeIfAbsent(windowKey, k -> new ArrayList<>()).add(event);
                }
            }
        } catch (Exception e) {
            log.warn("Error reading JFR file: {}", e.getMessage());
            globalWarnings.add(new ParseWarning("JFR_READ_ERROR", e.getMessage(), -1));
        }

        if (totalEvents == 0) {
            globalWarnings.add(new ParseWarning("NO_EXECUTION_SAMPLES",
                    "No jdk.ExecutionSample events found. Make sure the JFR was recorded with CPU profiling enabled (-XX:StartFlightRecording=settings=profile).",
                    -1));
        }

        // Convert each window into a SnapshotResult
        List<SnapshotResult> snapshots = new ArrayList<>();
        int idx = 0;
        for (Map.Entry<Long, List<RecordedEvent>> entry : windows.entrySet()) {
            Instant windowTime = Instant.ofEpochMilli(entry.getKey());
            SnapshotResult snap = buildSnapshot(idx++, windowTime, entry.getValue());
            snapshots.add(snap);
        }

        log.info("JFR parsed: {} execution samples → {} snapshots", totalEvents, snapshots.size());
        return new ThreadDumpResult(snapshots, snapshots.size(), globalWarnings);
    }

    private SnapshotResult buildSnapshot(int index, Instant timestamp, List<RecordedEvent> events) {
        // One ThreadInfo per unique thread in this window; keep the last (most recent) sample per thread
        Map<String, ThreadInfo> byThread = new LinkedHashMap<>();

        for (RecordedEvent event : events) {
            RecordedThread rt = resolveThread(event);
            if (rt == null) continue;

            String threadId = String.valueOf(rt.getJavaThreadId());
            String threadName = rt.getJavaName() != null ? rt.getJavaName() : "thread-" + threadId;
            ThreadState state = resolveState(event);
            List<StackFrame> frames = extractFrames(event);

            // Always update so the latest sample wins; threads without stack are still recorded
            byThread.put(threadId, new ThreadInfo(
                    threadId,
                    threadName,
                    rt.isVirtual(),   // daemon field re-used: virtual threads are flagged
                    Thread.NORM_PRIORITY,
                    "0x0",
                    state,
                    null,
                    List.of(),
                    frames
            ));
        }

        return new SnapshotResult(index, timestamp, List.copyOf(byThread.values()), List.of());
    }

    /** Tries known field names in order; falls back to event thread. */
    private RecordedThread resolveThread(RecordedEvent event) {
        for (String field : List.of("sampledThread", "eventThread", "thread")) {
            if (event.hasField(field)) {
                try {
                    RecordedThread t = event.getThread(field);
                    if (t != null) return t;
                } catch (Exception ignored) {}
            }
        }
        // Last resort: the event's own recording thread
        try { return event.getThread(); } catch (Exception ignored) {}
        return null;
    }

    private ThreadState resolveState(RecordedEvent event) {
        String name = event.getEventType().getName();
        return switch (name) {
            case MONITOR_ENTER  -> ThreadState.BLOCKED;
            case MONITOR_WAIT   -> ThreadState.WAITING;
            case THREAD_PARK    -> ThreadState.TIMED_WAITING;
            default             -> ThreadState.RUNNABLE;
        };
    }

    private List<StackFrame> extractFrames(RecordedEvent event) {
        RecordedStackTrace stack = event.getStackTrace();
        if (stack == null) return List.of();

        List<StackFrame> frames = new ArrayList<>();
        for (RecordedFrame frame : stack.getFrames()) {
            if (!frame.isJavaFrame()) continue;

            String className  = frame.getMethod().getType().getName();
            String methodName = frame.getMethod().getName();
            String sourceFile = frame.getMethod().getType().getName(); // no separate source in JFR
            int    line       = frame.getLineNumber();
            boolean synthetic = frame.getMethod().isHidden();

            frames.add(new StackFrame(className, methodName, sourceFile, line, synthetic));
        }
        return frames;
    }

    private long floorToWindow(long epochMs) {
        return (epochMs / WINDOW_MS) * WINDOW_MS;
    }
}
