package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.DumpType;
import org.springframework.stereotype.Component;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

/**
 * Detects the dump format from file content — not just extension.
 * Reads a probe of the first N lines to identify the format before routing.
 */
@Component
public class FormatDetector {

    private static final int PROBE_LINES = 20;
    private static final int PROBE_BYTES = 64 * 1024;

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
        BufferedInputStream in = input instanceof BufferedInputStream b
                ? b : new BufferedInputStream(input);

        in.mark(PROBE_BYTES + 16);
        byte[] header = in.readNBytes(12);

        if (isJfr(header)) return DumpType.JFR_RECORDING;
        if (isHprof(header)) return DumpType.HEAP_DUMP;

        // Text-based: rewind and read the probe from the very first byte so a
        // signal spanning the 12-byte header boundary is not missed.
        in.reset();
        byte[] probe = in.readNBytes(PROBE_BYTES);
        String probeText = new String(probe, StandardCharsets.UTF_8);

        int count = 0;
        for (String line : (Iterable<String>) probeText.lines()::iterator) {
            if (count++ >= PROBE_LINES) break;
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
}
