package com.devmanchego.performanceanalyzer.diff;

import com.devmanchego.performanceanalyzer.model.*;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class DiffService {

    public DiffResultDto diff(AnalysisResultDto a, AnalysisResultDto b,
                               double deltaThreshold, UUID diffId) {
        List<DiffResultDto.CallTreeDiffNode> treeDiff = diffCallTree(a.callTree(), b.callTree(), deltaThreshold);
        List<DiffResultDto.HotspotDiffEntry> hotspotDiff = diffHotspots(a.hotspots(), b.hotspots());
        DiffResultDto.DiagnosticDiff diagDiff = diffDiagnostics(a.diagnoses(), b.diagnoses());
        Map<String, Double> stateA = threadStateMap(a);
        Map<String, Double> stateB = threadStateMap(b);

        return new DiffResultDto(diffId, a.sessionId(), b.sessionId(), deltaThreshold,
                treeDiff, hotspotDiff, stateA, stateB, diagDiff,
                a.healthScore(), b.healthScore());
    }

    private List<DiffResultDto.CallTreeDiffNode> diffCallTree(
            CallTreeNodeDto a, CallTreeNodeDto b, double threshold) {
        if (a == null && b == null) return List.of();
        if (a == null) return List.of(asDiffNode(b, "ADDED", threshold));
        if (b == null) return List.of(asDiffNode(a, "REMOVED", threshold));

        // Match children by signature
        Map<String, CallTreeNodeDto> aChildren = indexBySignature(a.children());
        Map<String, CallTreeNodeDto> bChildren = indexBySignature(b.children());
        Set<String> all = new LinkedHashSet<>();
        all.addAll(aChildren.keySet());
        all.addAll(bChildren.keySet());

        List<DiffResultDto.CallTreeDiffNode> children = all.stream().map(sig -> {
            CallTreeNodeDto ac = aChildren.get(sig);
            CallTreeNodeDto bc = bChildren.get(sig);
            return buildDiffNode(sig, ac, bc, threshold);
        }).toList();

        return children;
    }

    private DiffResultDto.CallTreeDiffNode buildDiffNode(
            String sig, CallTreeNodeDto ac, CallTreeNodeDto bc, double threshold) {
        if (ac == null) return new DiffResultDto.CallTreeDiffNode(sig, "ADDED", 0, bc.totalTimePercent(), bc.totalTimePercent(), List.of());
        if (bc == null) return new DiffResultDto.CallTreeDiffNode(sig, "REMOVED", ac.totalTimePercent(), 0, -ac.totalTimePercent(), List.of());

        double delta = bc.totalTimePercent() - ac.totalTimePercent();
        String status;
        if (Math.abs(delta) < threshold) status = "UNCHANGED";
        else if (delta > 0) status = "REGRESSED";
        else status = "IMPROVED";

        List<DiffResultDto.CallTreeDiffNode> subChildren = diffCallTree(ac, bc, threshold);
        return new DiffResultDto.CallTreeDiffNode(sig, status, ac.totalTimePercent(), bc.totalTimePercent(), delta, subChildren);
    }

    private DiffResultDto.CallTreeDiffNode asDiffNode(CallTreeNodeDto n, String status, double threshold) {
        return new DiffResultDto.CallTreeDiffNode(n.methodSignature(), status,
                status.equals("ADDED") ? 0 : n.totalTimePercent(),
                status.equals("ADDED") ? n.totalTimePercent() : 0,
                status.equals("ADDED") ? n.totalTimePercent() : -n.totalTimePercent(),
                List.of());
    }

    private List<DiffResultDto.HotspotDiffEntry> diffHotspots(List<HotspotDto> a, List<HotspotDto> b) {
        if (a == null) a = List.of();
        if (b == null) b = List.of();
        Map<String, HotspotDto> aMap = a.stream().collect(Collectors.toMap(HotspotDto::methodSignature, h -> h));
        Map<String, HotspotDto> bMap = b.stream().collect(Collectors.toMap(HotspotDto::methodSignature, h -> h));
        Set<String> all = new LinkedHashSet<>();
        a.forEach(h -> all.add(h.methodSignature()));
        b.forEach(h -> all.add(h.methodSignature()));

        return all.stream().map(sig -> {
            HotspotDto ha = aMap.get(sig);
            HotspotDto hb = bMap.get(sig);
            double selfA = ha != null ? ha.selfTimePercent() : 0;
            double selfB = hb != null ? hb.selfTimePercent() : 0;
            double delta = selfB - selfA;
            String trend = ha == null ? "NEW" : hb == null ? "RESOLVED"
                    : delta > 0.5 ? "UP" : delta < -0.5 ? "DOWN" : "STABLE";
            return new DiffResultDto.HotspotDiffEntry(sig,
                    ha != null ? ha.rank() : null,
                    hb != null ? hb.rank() : null,
                    selfA, selfB, delta, trend);
        }).toList();
    }

    private DiffResultDto.DiagnosticDiff diffDiagnostics(List<Diagnosis> a, List<Diagnosis> b) {
        if (a == null) a = List.of();
        if (b == null) b = List.of();
        Set<String> aIds = a.stream().map(Diagnosis::ruleId).collect(Collectors.toSet());
        Set<String> bIds = b.stream().map(Diagnosis::ruleId).collect(Collectors.toSet());

        List<Diagnosis> resolved = a.stream().filter(d -> !bIds.contains(d.ruleId())).toList();
        List<Diagnosis> added = b.stream().filter(d -> !aIds.contains(d.ruleId())).toList();
        List<Diagnosis> persisting = a.stream().filter(d -> bIds.contains(d.ruleId())).toList();
        return new DiffResultDto.DiagnosticDiff(resolved, added, persisting);
    }

    private Map<String, Double> threadStateMap(AnalysisResultDto r) {
        if (r.timeline() == null || r.timeline().snapshots().isEmpty()) return Map.of();
        var last = r.timeline().snapshots().get(r.timeline().snapshots().size() - 1);
        return Map.of(
                "RUNNABLE", last.runnablePercent(),
                "BLOCKED", last.blockedPercent(),
                "WAITING", last.waitingPercent(),
                "TIMED_WAITING", last.timedWaitingPercent()
        );
    }

    private Map<String, CallTreeNodeDto> indexBySignature(List<CallTreeNodeDto> nodes) {
        if (nodes == null) return Map.of();
        return nodes.stream().collect(Collectors.toMap(CallTreeNodeDto::methodSignature, n -> n, (a, b) -> a));
    }
}
