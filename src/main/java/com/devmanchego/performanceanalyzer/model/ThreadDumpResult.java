package com.devmanchego.performanceanalyzer.model;

import java.util.List;

public record ThreadDumpResult(
        List<SnapshotResult> snapshots,
        int totalSnapshotsExpected,
        List<ParseWarning> globalWarnings
) {
    public boolean isPartial() {
        return snapshots.size() < totalSnapshotsExpected;
    }

    public int recoveredSnapshots() {
        return snapshots.size();
    }
}
