package com.devmanchego.performanceanalyzer.model;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public record SnapshotResult(
        int index,
        Instant timestamp,
        List<ThreadInfo> threads,
        List<ParseWarning> warnings
) {
    public Map<ThreadState, Long> threadStateDistribution() {
        return threads.stream()
                .collect(Collectors.groupingBy(ThreadInfo::state, Collectors.counting()));
    }

    public long blockedCount() {
        return threads.stream().filter(ThreadInfo::isBlocked).count();
    }

    public double blockedPercent() {
        if (threads.isEmpty()) return 0.0;
        return (double) blockedCount() / threads.size() * 100.0;
    }
}
