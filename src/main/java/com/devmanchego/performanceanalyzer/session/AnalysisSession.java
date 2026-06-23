package com.devmanchego.performanceanalyzer.session;

import com.devmanchego.performanceanalyzer.model.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AnalysisSession {

    private final UUID id;
    private final Instant createdAt;
    private volatile Instant lastAccessedAt;

    private final List<String> fileNames;
    private final List<DumpType> dumpTypes;

    private ThreadDumpResult threadDumpResult;
    private GcLogResult gcLogResult;
    private String jfrRaw; // placeholder for Module JFR
    private String heapRaw; // placeholder for Module 4

    public AnalysisSession(List<String> fileNames, List<DumpType> dumpTypes) {
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
        this.lastAccessedAt = this.createdAt;
        this.fileNames = List.copyOf(fileNames);
        this.dumpTypes = List.copyOf(dumpTypes);
    }

    public UUID getId() { return id; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getLastAccessedAt() { return lastAccessedAt; }
    public List<String> getFileNames() { return fileNames; }
    public List<DumpType> getDumpTypes() { return dumpTypes; }

    public ThreadDumpResult getThreadDumpResult() { return threadDumpResult; }
    public void setThreadDumpResult(ThreadDumpResult r) { this.threadDumpResult = r; touch(); }

    public GcLogResult getGcLogResult() { return gcLogResult; }
    public void setGcLogResult(GcLogResult r) { this.gcLogResult = r; touch(); }

    public void touch() { this.lastAccessedAt = Instant.now(); }
}
