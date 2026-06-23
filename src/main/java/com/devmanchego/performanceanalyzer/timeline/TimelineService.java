package com.devmanchego.performanceanalyzer.timeline;

import com.devmanchego.performanceanalyzer.model.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class TimelineService {

    @Value("${performanceanalyzer.thresholds.deadlock-confirmation-threshold:1.0}")
    private double deadlockThreshold;

    public TimelineDto build(List<SnapshotResult> snapshots, List<HotspotDto> topHotspots) {
        if (snapshots.isEmpty()) {
            return new TimelineDto(List.of(), List.of(), List.of(), false);
        }

        List<TimelineDto.SnapshotSummary> summaries = snapshots.stream()
                .map(this::toSummary).toList();

        boolean confirmedDeadlock = detectConfirmedDeadlock(snapshots);

        List<TimelineDto.ThreadSwimLane> swimLanes = buildSwimLanes(snapshots);

        List<TimelineDto.HotspotTrend> trends = buildHotspotTrends(snapshots, topHotspots);

        return new TimelineDto(summaries, swimLanes, trends, confirmedDeadlock);
    }

    private TimelineDto.SnapshotSummary toSummary(SnapshotResult s) {
        int total = s.threads().size();
        if (total == 0) {
            return new TimelineDto.SnapshotSummary(s.index(), ts(s), 0, 0, 0, 0, 0);
        }
        Map<ThreadState, Long> dist = s.threadStateDistribution();
        return new TimelineDto.SnapshotSummary(
                s.index(), ts(s),
                pct(dist, ThreadState.RUNNABLE, total),
                pct(dist, ThreadState.BLOCKED, total),
                pct(dist, ThreadState.WAITING, total),
                pct(dist, ThreadState.TIMED_WAITING, total),
                total
        );
    }

    private boolean detectConfirmedDeadlock(List<SnapshotResult> snapshots) {
        // A thread is "confirmed deadlocked" if it stays BLOCKED on the same monitor in ALL snapshots
        if (snapshots.size() < 2) return false;

        // Build map: threadId -> set of monitors it blocked on per snapshot
        Map<String, List<String>> threadMonitors = new HashMap<>();
        for (SnapshotResult snap : snapshots) {
            for (ThreadInfo t : snap.threads()) {
                if (t.state() == ThreadState.BLOCKED && t.waitingOnMonitor() != null) {
                    threadMonitors.computeIfAbsent(t.id(), k -> new ArrayList<>())
                            .add(t.waitingOnMonitor());
                }
            }
        }

        for (Map.Entry<String, List<String>> e : threadMonitors.entrySet()) {
            if (e.getValue().size() == snapshots.size()) {
                long uniqueMonitors = e.getValue().stream().distinct().count();
                if (uniqueMonitors == 1) return true; // same monitor all snapshots
            }
        }
        return false;
    }

    private List<TimelineDto.ThreadSwimLane> buildSwimLanes(List<SnapshotResult> snapshots) {
        // Collect all thread IDs appearing in any snapshot
        Map<String, String> threadNames = new LinkedHashMap<>();
        for (SnapshotResult snap : snapshots) {
            for (ThreadInfo t : snap.threads()) {
                threadNames.putIfAbsent(t.id(), t.name());
            }
        }

        // Build index: snapshotIndex -> threadId -> ThreadInfo
        List<Map<String, ThreadInfo>> index = snapshots.stream()
                .map(s -> s.threads().stream()
                        .collect(Collectors.toMap(ThreadInfo::id, t -> t, (a, b) -> a)))
                .toList();

        return threadNames.entrySet().stream().map(e -> {
            String tid = e.getKey();
            List<TimelineDto.CellState> cells = new ArrayList<>();
            for (int i = 0; i < snapshots.size(); i++) {
                ThreadInfo t = index.get(i).get(tid);
                String state = t != null ? t.state().name() : "ABSENT";
                String top = t != null && t.topFrame() != null ? t.topFrame().signature() : null;
                cells.add(new TimelineDto.CellState(i, state, top));
            }
            return new TimelineDto.ThreadSwimLane(tid, e.getValue(), cells);
        }).toList();
    }

    private List<TimelineDto.HotspotTrend> buildHotspotTrends(List<SnapshotResult> snapshots,
                                                                List<HotspotDto> topHotspots) {
        int take = Math.min(10, topHotspots.size());
        return topHotspots.subList(0, take).stream().map(h -> {
            List<Double> percents = snapshots.stream().map(snap -> {
                int total = snap.threads().size();
                if (total == 0) return 0.0;
                long hits = snap.threads().stream()
                        .filter(t -> t.topFrame() != null &&
                                     t.topFrame().signature().equals(h.methodSignature()))
                        .count();
                return (double) hits / total * 100.0;
            }).toList();
            return new TimelineDto.HotspotTrend(h.methodSignature(), percents);
        }).toList();
    }

    private double pct(Map<ThreadState, Long> dist, ThreadState state, int total) {
        return dist.getOrDefault(state, 0L) * 100.0 / total;
    }

    private String ts(SnapshotResult s) {
        return s.timestamp() != null ? s.timestamp().toString() : null;
    }
}
