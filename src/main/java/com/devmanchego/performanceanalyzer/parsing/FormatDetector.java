package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.DumpType;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Detects the dump format from file content — not just extension.
 * Reads a probe of the first N lines to identify the format before routing.
 */
@Component
public class FormatDetector {

    private static final int PROBE_LINES = 20;

    // Thread dump signals
    private static final Pattern THREAD_DUMP_SIGNAL = Pattern.compile(
            "Full thread dump|java\\.lang\\.Thread\\.State:|\".*\" #\\d+"
    );

    // GC log signals (unified or legacy)
    private static final Pattern GC_LOG_SIGNAL = Pattern.compile(
            "\\[\\d+\\.\\d+s]\\[.*gc.*]|" +          // unified
            "\\d+\\.\\d+: \\[(?:Full )?GC|" +         // legacy
            "-verbose:gc|-Xlog:gc|GC Heap"
    );

    // JFR: binary files start with magic bytes "FLR\0" (0x464C5200)
    private static final byte[] JFR_MAGIC = {0x46, 0x4C, 0x52, 0x00};

    // HPROF: binary files start with "JAVA PROFILE"
    private static final String HPROF_MAGIC = "JAVA PROFILE";

    public DumpType detect(InputStream input, String filename) throws IOException {
        byte[] header = input.readNBytes(12);

        if (isJfr(header)) return DumpType.JFR_RECORDING;
        if (isHprof(header)) return DumpType.HEAP_DUMP;

        // Text-based: read probe lines
        String headerText = new String(header, StandardCharsets.UTF_8);
        List<String> probeLines = readProbeLines(input, headerText);

        for (String line : probeLines) {
            if (THREAD_DUMP_SIGNAL.matcher(line).find()) return DumpType.THREAD_DUMP;
            if (GC_LOG_SIGNAL.matcher(line).find()) return DumpType.GC_LOG;
        }

        // Extension fallback
        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".hprof")) return DumpType.HEAP_DUMP;
            if (lower.endsWith(".jfr")) return DumpType.JFR_RECORDING;
        }

        // Default: assume thread dump for .txt/.log
        return DumpType.THREAD_DUMP;
    }

    private boolean isJfr(byte[] header) {
        if (header.length < 4) return false;
        return header[0] == JFR_MAGIC[0] && header[1] == JFR_MAGIC[1]
                && header[2] == JFR_MAGIC[2] && header[3] == JFR_MAGIC[3];
    }

    private boolean isHprof(byte[] header) {
        if (header.length < HPROF_MAGIC.length()) return false;
        return new String(header, 0, HPROF_MAGIC.length(), StandardCharsets.US_ASCII)
                .startsWith(HPROF_MAGIC);
    }

    private List<String> readProbeLines(InputStream remaining, String headerText) throws IOException {
        List<String> lines = new ArrayList<>();
        // Include the header bytes as the first line
        String firstLine = headerText.lines().findFirst().orElse("");
        if (!firstLine.isBlank()) lines.add(firstLine);

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(remaining, StandardCharsets.UTF_8))) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < PROBE_LINES) {
                lines.add(line);
                count++;
            }
        }
        return lines;
    }
}
