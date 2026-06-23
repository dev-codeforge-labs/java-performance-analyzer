package com.devmanchego.performanceanalyzer.ingestion;

import com.devmanchego.performanceanalyzer.model.DumpType;
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

        // Second pass: parse each file
        for (int i = 0; i < files.size(); i++) {
            DumpType type = dumpTypes.get(i);
            byte[] bytes = contents.get(i);

            switch (type) {
                case THREAD_DUMP -> {
                    var result = threadDumpParser.parse(new ByteArrayInputStream(bytes));
                    session.setThreadDumpResult(result);
                }
                case GC_LOG -> {
                    var result = gcLogParser.parse(new ByteArrayInputStream(bytes));
                    session.setGcLogResult(result);
                }
                case JFR_RECORDING -> {
                    var result = jfrParser.parse(new ByteArrayInputStream(bytes));
                    session.setThreadDumpResult(result);
                }
                case HEAP_DUMP -> {
                    // Module 4 — Eclipse MAT not on Maven Central, placeholder
                }
            }
        }

        sessionStore.put(session);
        return session;
    }
}
