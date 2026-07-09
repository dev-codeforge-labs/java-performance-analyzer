package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.GcEventDto;
import com.devmanchego.performanceanalyzer.model.GcLogResult;
import com.devmanchego.performanceanalyzer.model.ParseWarning;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Dual-mode GC log parser supporting:
 * - Legacy format: -verbose:gc / -XX:+PrintGCDetails
 * - Unified format: -Xlog:gc* (JDK 9+)
 * Format is auto-detected from the first 10 lines.
 */
@Component
public class GcLogParser {

    // Unified log: [0.123s][info][gc] GC(0) Pause Young (Normal) (G1 Evacuation Pause) 512M->256M(1024M) 12.345ms
    private static final Pattern UNIFIED_STW = Pattern.compile(
            "^\\[(\\d+\\.\\d+)s].*?Pause\\s+(\\w+(?:\\s+\\w+)*).*?(\\d+)M->(\\d+)M\\((\\d+)M\\)\\s+(\\d+\\.\\d+)ms"
    );

    // Unified ZGC: [0.456s][info][gc] GC(1) Pause Mark Start 0.345ms
    private static final Pattern UNIFIED_ZGC = Pattern.compile(
            "^\\[(\\d+\\.\\d+)s].*?Pause\\s+\\w+.*?\\s+(\\d+\\.\\d+)ms"
    );

    // Legacy: 2024-01-01T00:00:01.000+0000: 1.234: [GC (Allocation Failure) [PSYoungGen: 512K->256K(1024K)] 1024K->768K(2048K), 0.012345 secs]
    private static final Pattern LEGACY_GC = Pattern.compile(
            "(?:^|\\s)(\\d+\\.\\d+):\\s+\\[((?:Full\\s+)?GC).*?(\\d+)K->(\\d+)K\\((\\d+)K\\)[^,]*,\\s+(\\d+\\.\\d+)\\s+secs]"
    );

    private static final Pattern PROMOTION_FAILURE = Pattern.compile(
            "promotion failed|concurrent mode failure",
            Pattern.CASE_INSENSITIVE
    );

    private static final Pattern GC_ALGO_HINT = Pattern.compile(
            "Using (G1|Parallel|Serial|Shenandoah|ZGC|CMS) GC",
            Pattern.CASE_INSENSITIVE
    );

    public GcLogResult parse(InputStream input) throws IOException {
        List<String> lines = readLines(input);
        List<ParseWarning> warnings = new ArrayList<>();

        if (lines.isEmpty()) {
            return emptyResult(warnings);
        }

        boolean unified = detectUnifiedFormat(lines);
        String algorithm = detectAlgorithm(lines);
        List<GcEventDto> events = new ArrayList<>();
        boolean promotionFailure = false;
        boolean concurrentModeFailure = false;

        for (String line : lines) {
            if (PROMOTION_FAILURE.matcher(line).find()) {
                if (line.toLowerCase().contains("promotion failed")) promotionFailure = true;
                if (line.toLowerCase().contains("concurrent mode failure")) concurrentModeFailure = true;
            }

            GcEventDto event = unified ? parseUnifiedLine(line, algorithm) : parseLegacyLine(line, algorithm);
            if (event != null) {
                events.add(event);
            }
        }

        return buildResult(algorithm, events, promotionFailure, concurrentModeFailure, warnings);
    }

    private boolean detectUnifiedFormat(List<String> lines) {
        int check = Math.min(10, lines.size());
        for (int i = 0; i < check; i++) {
            if (lines.get(i).startsWith("[") && lines.get(i).contains("s][")) {
                return true;
            }
        }
        return false;
    }

    private String detectAlgorithm(List<String> lines) {
        for (String line : lines) {
            Matcher m = GC_ALGO_HINT.matcher(line);
            if (m.find()) return m.group(1).toUpperCase();
            if (line.contains("G1 Evacuation")) return "G1";
            if (line.contains("PSYoungGen")) return "PARALLEL";
            if (line.contains("ZGC")) return "ZGC";
            if (line.contains("Shenandoah")) return "SHENANDOAH";
            if (line.contains("CMS")) return "CMS";
        }
        return "UNKNOWN";
    }

    private GcEventDto parseUnifiedLine(String line, String algorithm) {
        Matcher m = UNIFIED_STW.matcher(line);
        if (m.find()) {
            double ts = Double.parseDouble(m.group(1));
            String type = m.group(2);
            long before = Long.parseLong(m.group(3)) * 1024L * 1024L;
            long after = Long.parseLong(m.group(4)) * 1024L * 1024L;
            long total = Long.parseLong(m.group(5)) * 1024L * 1024L;
            double pause = Double.parseDouble(m.group(6));
            return new GcEventDto(ts, type, pause, before, after, total, algorithm);
        }
        // ZGC / Shenandoah lightweight pause
        Matcher z = UNIFIED_ZGC.matcher(line);
        if (z.find() && (algorithm.equals("ZGC") || algorithm.equals("SHENANDOAH"))) {
            double ts = Double.parseDouble(z.group(1));
            double pause = Double.parseDouble(z.group(2));
            return new GcEventDto(ts, "Pause", pause, 0, 0, 0, algorithm);
        }
        return null;
    }

    private GcEventDto parseLegacyLine(String line, String algorithm) {
        Matcher m = LEGACY_GC.matcher(line);
        if (m.find()) {
            double ts = Double.parseDouble(m.group(1));
            String type = m.group(2).trim().replaceAll("\\s+", " ");
            long before = Long.parseLong(m.group(3)) * 1024L;
            long after = Long.parseLong(m.group(4)) * 1024L;
            long total = Long.parseLong(m.group(5)) * 1024L;
            double pause = Double.parseDouble(m.group(6)) * 1000.0; // secs → ms
            return new GcEventDto(ts, type, pause, before, after, total, algorithm);
        }
        return null;
    }

    private GcLogResult buildResult(String algorithm, List<GcEventDto> events,
                                    boolean promotionFailure, boolean concurrentModeFailure,
                                    List<ParseWarning> warnings) {
        if (events.isEmpty()) {
            return new GcLogResult(algorithm, events, 0, 0, 0, 0, 0,
                    promotionFailure, concurrentModeFailure, warnings);
        }

        double maxPause = events.stream().mapToDouble(GcEventDto::pauseMs).max().orElse(0);
        double avgPause = events.stream().mapToDouble(GcEventDto::pauseMs).average().orElse(0);
        double totalPause = events.stream().mapToDouble(GcEventDto::pauseMs).sum();

        // p99
        List<Double> sorted = events.stream()
                .mapToDouble(GcEventDto::pauseMs).sorted().boxed().toList();
        double p99 = sorted.get((int) (sorted.size() * 0.99));

        double totalElapsed = events.stream().mapToDouble(GcEventDto::timestamp).max().orElse(0) * 1000.0;
        double gcPercent = totalElapsed > 0 ? (totalPause / totalElapsed * 100.0) : 0;

        return new GcLogResult(algorithm, events, maxPause, avgPause, p99,
                totalElapsed, gcPercent, promotionFailure, concurrentModeFailure, warnings);
    }

    private List<String> readLines(InputStream input) throws IOException {
        List<String> lines = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    private GcLogResult emptyResult(List<ParseWarning> warnings) {
        warnings.add(ParseWarning.of("EMPTY_GC_LOG", "No GC events found in file"));
        return new GcLogResult("UNKNOWN", List.of(), 0, 0, 0, 0, 0, false, false, warnings);
    }
}
