package com.devmanchego.performanceanalyzer.ingestion;

import com.devmanchego.performanceanalyzer.model.DumpType;
import com.devmanchego.performanceanalyzer.model.ParseWarning;
import com.devmanchego.performanceanalyzer.model.SnapshotResult;
import com.devmanchego.performanceanalyzer.model.ThreadDumpResult;
import com.devmanchego.performanceanalyzer.parsing.FormatDetector;
import com.devmanchego.performanceanalyzer.parsing.GcLogParser;
import com.devmanchego.performanceanalyzer.parsing.JfrParser;
import com.devmanchego.performanceanalyzer.parsing.ThreadDumpParser;
import com.devmanchego.performanceanalyzer.session.AnalysisSession;
import com.devmanchego.performanceanalyzer.session.SessionStore;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class IngestionService {

    private final FormatDetector formatDetector;
    private final ThreadDumpParser threadDumpParser;
    private final GcLogParser gcLogParser;
    private final JfrParser jfrParser;
    private final SessionStore sessionStore;

    public IngestionService(FormatDetector formatDetector, ThreadDumpParser threadDumpParser,
                            GcLogParser gcLogParser, JfrParser jfrParser, SessionStore sessionStore) {
        this.formatDetector = formatDetector;
        this.threadDumpParser = threadDumpParser;
        this.gcLogParser = gcLogParser;
        this.jfrParser = jfrParser;
        this.sessionStore = sessionStore;
    }

    public AnalysisSession ingest(List<MultipartFile> files) throws IOException {
        List<String> fileNames = new ArrayList<>();
        List<DumpType> dumpTypes = new ArrayList<>();

        // First pass: detect types
        List<byte[]> contents = new ArrayList<>();
        for (MultipartFile file : files) {
            byte[] bytes = file.getBytes();
            contents.add(bytes);
            fileNames.add(file.getOriginalFilename());
            DumpType type = formatDetector.detect(
                    new ByteArrayInputStream(bytes), file.getOriginalFilename());
            dumpTypes.add(type);
        }

        AnalysisSession session = new AnalysisSession(fileNames, dumpTypes);

        // Snapshots from every thread-dump / JFR file are merged into one result
        // instead of the last file silently overwriting the previous ones.
        List<SnapshotResult> mergedSnapshots = new ArrayList<>();
        List<ParseWarning> mergedWarnings = new ArrayList<>();
        int expectedSnapshots = 0;
        boolean hasThreadSource = false;
        int gcLogCount = 0;

        // Second pass: parse each file
        for (int i = 0; i < files.size(); i++) {
            DumpType type = dumpTypes.get(i);
            byte[] bytes = contents.get(i);

            switch (type) {
                case THREAD_DUMP -> {
                    ThreadDumpResult result = threadDumpParser.parse(new ByteArrayInputStream(bytes));
                    mergedSnapshots.addAll(result.snapshots());
                    mergedWarnings.addAll(result.globalWarnings());
                    expectedSnapshots += result.totalSnapshotsExpected();
                    hasThreadSource = true;
                }
                case GC_LOG -> {
                    var result = gcLogParser.parse(new ByteArrayInputStream(bytes));
                    if (++gcLogCount > 1) {
                        mergedWarnings.add(ParseWarning.of("MULTIPLE_GC_LOGS",
                                "Multiple GC logs uploaded — only '" + fileNames.get(i)
                                        + "' is retained; earlier GC logs were replaced"));
                    }
                    session.setGcLogResult(result);
                }
                case JFR_RECORDING -> {
                    ThreadDumpResult result = jfrParser.parse(new ByteArrayInputStream(bytes));
                    mergedSnapshots.addAll(result.snapshots());
                    mergedWarnings.addAll(result.globalWarnings());
                    expectedSnapshots += result.totalSnapshotsExpected();
                    hasThreadSource = true;
                }
                case HEAP_DUMP -> {
                    // Module 4 — Eclipse MAT not on Maven Central, placeholder
                }
            }
        }

        if (hasThreadSource) {
            // Re-index snapshots sequentially so indices stay unique across merged files.
            List<SnapshotResult> reindexed = new ArrayList<>(mergedSnapshots.size());
            int idx = 0;
            for (SnapshotResult s : mergedSnapshots) {
                reindexed.add(new SnapshotResult(idx++, s.timestamp(), s.threads(), s.warnings()));
            }
            session.setThreadDumpResult(new ThreadDumpResult(reindexed, expectedSnapshots, mergedWarnings));
        }

        sessionStore.put(session);
        return session;
    }
}
