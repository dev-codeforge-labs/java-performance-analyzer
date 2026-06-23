package com.devmanchego.performanceanalyzer.model;

import java.util.List;
import java.util.Map;

public record TimelineDto(
        List<SnapshotSummary> snapshots,
        List<ThreadSwimLane> swimLanes,
        List<HotspotTrend> hotspotTrends,
        boolean confirmedDeadlock
) {
    public record SnapshotSummary(
            int index,
            String timestamp,
            double runnablePercent,
            double blockedPercent,
            double waitingPercent,
            double timedWaitingPercent,
            int totalThreads
    ) {}

    public record ThreadSwimLane(
            String threadId,
            String threadName,
            List<CellState> cells
    ) {}

    public record CellState(int snapshotIndex, String state, String topFrame) {}

    public record HotspotTrend(
            String methodSignature,
            List<Double> selfTimePercents
    ) {}
}
