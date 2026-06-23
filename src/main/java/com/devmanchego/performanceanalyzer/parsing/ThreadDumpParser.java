package com.devmanchego.performanceanalyzer.parsing;

import com.devmanchego.performanceanalyzer.model.ParseWarning;
import com.devmanchego.performanceanalyzer.model.SnapshotResult;
import com.devmanchego.performanceanalyzer.model.ThreadDumpResult;
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
 * Entry point for thread dump parsing.
 * Splits raw text into individual snapshot blocks, then delegates each to SnapshotParser.
 * Streams the file line by line — never loads the entire file into RAM.
 */
@Component
public class ThreadDumpParser {

    // A new snapshot starts with a timestamp or a "Full thread dump" header
    private static final Pattern SNAPSHOT_BOUNDARY = Pattern.compile(
            "^(?:\\d{4}-\\d{2}-\\d{2}|Full thread dump|" +
            "\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2})"
    );

    // jstack header line: "Full thread dump OpenJDK 64-Bit Server VM (21.0.2+13-LTS mixed mode):"
    private static final Pattern FULL_DUMP_HEADER = Pattern.compile(
            "^Full thread dump"
    );

    private final SnapshotParser snapshotParser;

    public ThreadDumpParser(SnapshotParser snapshotParser) {
        this.snapshotParser = snapshotParser;
    }

    public ThreadDumpResult parse(InputStream input) throws IOException {
        List<List<String>> blocks = splitIntoBlocks(input);
        List<ParseWarning> globalWarnings = new ArrayList<>();

        if (blocks.isEmpty()) {
            globalWarnings.add(ParseWarning.of("EMPTY_FILE", "No thread dump snapshots found in file"));
            return new ThreadDumpResult(List.of(), 0, globalWarnings);
        }

        int expectedCount = blocks.size();
        List<SnapshotResult> snapshots = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            List<String> block = blocks.get(i);
            try {
                SnapshotResult snapshot = snapshotParser.parse(i, block);
                if (!snapshot.threads().isEmpty()) {
                    snapshots.add(snapshot);
                } else {
                    globalWarnings.add(ParseWarning.of("EMPTY_SNAPSHOT",
                            "Snapshot " + i + " contained no parseable threads — skipped"));
                }
            } catch (Exception e) {
                globalWarnings.add(ParseWarning.of("SNAPSHOT_PARSE_ERROR",
                        "Failed to parse snapshot " + i + ": " + e.getMessage()));
            }
        }

        if (snapshots.size() < expectedCount) {
            globalWarnings.add(ParseWarning.of("PARTIAL_FILE",
                    "Recovered " + snapshots.size() + " of " + expectedCount + " snapshots"));
        }

        return new ThreadDumpResult(snapshots, expectedCount, globalWarnings);
    }

    /**
     * Splits the input stream into logical snapshot blocks.
     * A new block begins when a snapshot boundary line is detected.
     */
    private List<List<String>> splitIntoBlocks(InputStream input) throws IOException {
        List<List<String>> blocks = new ArrayList<>();
        List<String> currentBlock = new ArrayList<>();
        boolean inDump = false;

        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {

            String line;
            while ((line = reader.readLine()) != null) {
                if (FULL_DUMP_HEADER.matcher(line).find()) {
                    if (inDump && !currentBlock.isEmpty()) {
                        blocks.add(new ArrayList<>(currentBlock));
                        currentBlock.clear();
                    }
                    inDump = true;
                    currentBlock.add(line);
                } else if (inDump) {
                    currentBlock.add(line);
                } else if (SNAPSHOT_BOUNDARY.matcher(line).find()) {
                    // Snapshot starting with a timestamp but no "Full thread dump" header
                    if (!currentBlock.isEmpty()) {
                        blocks.add(new ArrayList<>(currentBlock));
                        currentBlock.clear();
                    }
                    inDump = true;
                    currentBlock.add(line);
                }
            }
        }

        if (!currentBlock.isEmpty()) {
            blocks.add(currentBlock);
        }

        // If no boundaries found, treat entire file as a single snapshot
        if (blocks.isEmpty() && !currentBlock.isEmpty()) {
            blocks.add(currentBlock);
        }

        return blocks;
    }
}
