package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.*;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses a single snapshot block (one jstack output) into a SnapshotResult.
 */
@Component
public class SnapshotParser {

    // "thread-name" #id [daemon] prio=N os_prio=N tid=0x... nid=0x... STATE [0x...]
    private static final Pattern THREAD_HEADER = Pattern.compile(
            "^\"(.+?)\"\\s+" +
            "(#(\\d+)\\s+)?" +
            "(daemon\\s+)?" +
            "prio=(\\d+)\\s+" +
            "(?:os_prio=\\d+\\s+)?" +
            "(?:cpu=\\S+\\s+)?(?:elapsed=\\S+\\s+)?" +
            "tid=(\\S+)\\s+" +
            "nid=(\\S+)\\s+" +
            "(.+?)(?:\\s+\\[.*])?$"
    );

    private static final Pattern THREAD_STATE_LINE = Pattern.compile(
            "^\\s+java\\.lang\\.Thread\\.State:\\s+(\\S+)(?:\\s+.*)?$"
    );

    // at com.example.Foo.bar(Foo.java:42)
    private static final Pattern STACK_FRAME_LINE = Pattern.compile(
            "^\\s+at\\s+([\\w.$<>]+)\\.([\\w$<>]+)\\((.+?)(?::(\\d+))?\\)$"
    );

    // - waiting to lock <0xabc> (a com.example.Foo)
    private static final Pattern WAITING_ON_MONITOR = Pattern.compile(
            "^\\s+- waiting (?:to lock|on) <(0x[0-9a-fA-F]+)>"
    );

    // - locked <0xabc> (a com.example.Foo)
    private static final Pattern LOCKED_MONITOR = Pattern.compile(
            "^\\s+- locked <(0x[0-9a-fA-F]+)>"
    );

    private static final Pattern SNAPSHOT_TIMESTAMP = Pattern.compile(
            "^(\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2})"
    );

    private final StackTraceSanitizer sanitizer;

    public SnapshotParser(StackTraceSanitizer sanitizer) {
        this.sanitizer = sanitizer;
    }

    public SnapshotResult parse(int index, List<String> lines) {
        Instant timestamp = extractTimestamp(lines);
        List<ThreadInfo> threads = new ArrayList<>();
        List<ParseWarning> warnings = new ArrayList<>();

        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            Matcher headerMatcher = THREAD_HEADER.matcher(line);
            if (headerMatcher.matches()) {
                ParsedThread parsed = parseThread(lines, i, warnings);
                threads.add(parsed.thread());
                i = parsed.nextIndex();
            } else {
                i++;
            }
        }

        return new SnapshotResult(index, timestamp, threads, warnings);
    }

    private Instant extractTimestamp(List<String> lines) {
        for (String line : lines) {
            Matcher m = SNAPSHOT_TIMESTAMP.matcher(line.trim());
            if (m.find()) {
                try {
                    return Instant.parse(m.group(1) + "Z");
                } catch (DateTimeParseException ignored) {
                    // fall through
                }
            }
        }
        return null;
    }

    private ParsedThread parseThread(List<String> lines, int startIndex, List<ParseWarning> warnings) {
        String headerLine = lines.get(startIndex);
        Matcher m = THREAD_HEADER.matcher(headerLine);
        m.matches();

        String name = m.group(1);
        String id = m.group(3) != null ? m.group(3) : "?";
        boolean daemon = m.group(4) != null;
        int priority = Integer.parseInt(m.group(5));
        String tid = m.group(6);
        String nid = m.group(7);
        String rawState = m.group(8).trim().toUpperCase();

        ThreadState state = ThreadState.UNKNOWN;
        String waitingOnMonitor = null;
        List<String> lockedMonitors = new ArrayList<>();
        List<StackFrame> frames = new ArrayList<>();

        int i = startIndex + 1;
        while (i < lines.size()) {
            String line = lines.get(i);

            if (line.isBlank()) {
                break;
            }

            Matcher stateMatcher = THREAD_STATE_LINE.matcher(line);
            if (stateMatcher.matches()) {
                state = parseState(stateMatcher.group(1));
                i++;
                continue;
            }

            Matcher frameMatcher = STACK_FRAME_LINE.matcher(line);
            if (frameMatcher.matches()) {
                String className = frameMatcher.group(1);
                String methodName = frameMatcher.group(2);
                String sourceFile = frameMatcher.group(3);
                String lineNum = frameMatcher.group(4);
                int srcLine = lineNum != null ? Integer.parseInt(lineNum) : -1;
                frames.add(new StackFrame(className, methodName, sourceFile, srcLine, false));
                i++;
                continue;
            }

            Matcher waitMatcher = WAITING_ON_MONITOR.matcher(line);
            if (waitMatcher.find()) {
                waitingOnMonitor = waitMatcher.group(1);
                i++;
                continue;
            }

            Matcher lockMatcher = LOCKED_MONITOR.matcher(line);
            if (lockMatcher.find()) {
                lockedMonitors.add(lockMatcher.group(1));
                i++;
                continue;
            }

            i++;
        }

        // If state still UNKNOWN but raw state available, try again
        if (state == ThreadState.UNKNOWN && !rawState.isBlank()) {
            state = parseState(rawState);
        }

        List<StackFrame> sanitized = sanitizer.sanitize(frames);

        ThreadInfo thread = new ThreadInfo(id, name, daemon, priority, nid, state,
                waitingOnMonitor, List.copyOf(lockedMonitors), sanitized);

        return new ParsedThread(thread, i);
    }

    private ThreadState parseState(String raw) {
        return switch (raw.toUpperCase().split("\\s+")[0]) {
            case "RUNNABLE"      -> ThreadState.RUNNABLE;
            case "BLOCKED"       -> ThreadState.BLOCKED;
            case "WAITING"       -> ThreadState.WAITING;
            case "TIMED_WAITING" -> ThreadState.TIMED_WAITING;
            case "NEW"           -> ThreadState.NEW;
            case "TERMINATED"    -> ThreadState.TERMINATED;
            default              -> ThreadState.UNKNOWN;
        };
    }

    private record ParsedThread(ThreadInfo thread, int nextIndex) {}
}
