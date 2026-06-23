package com.devmanchego.performanceanalyzer.model;

import java.util.List;
import java.util.Map;

public record AnalysisContext(
        CallTreeNode callTree,
        List<HotspotDto> hotspots,
        List<SnapshotResult> snapshots,
        Map<String, String> lockGraph,
        int totalSamples,
        RulesetDto ruleset
) {
    public double blockedPercent() {
        long total = snapshots.stream().mapToLong(s -> s.threads().size()).sum();
        if (total == 0) return 0.0;
        long blocked = snapshots.stream()
                .flatMap(s -> s.threads().stream())
                .filter(ThreadInfo::isBlocked)
                .count();
        return (double) blocked / total * 100.0;
    }
}
