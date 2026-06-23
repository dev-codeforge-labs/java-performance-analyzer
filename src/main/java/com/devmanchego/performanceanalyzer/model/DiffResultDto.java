package com.devmanchego.performanceanalyzer.model;

import java.util.List;
import java.util.Map;
import java.util.UUID;

public record DiffResultDto(
        UUID diffId,
        UUID sessionIdA,
        UUID sessionIdB,
        double deltaThreshold,
        List<CallTreeDiffNode> callTreeDiff,
        List<HotspotDiffEntry> hotspotDiff,
        Map<String, Double> threadStateDiffA,
        Map<String, Double> threadStateDiffB,
        DiagnosticDiff diagnosticDiff,
        HealthScoreDto healthScoreA,
        HealthScoreDto healthScoreB
) {
    public record CallTreeDiffNode(
            String methodSignature,
            String status,       // ADDED, REMOVED, IMPROVED, REGRESSED, UNCHANGED
            double totalTimePctA,
            double totalTimePctB,
            double delta,
            List<CallTreeDiffNode> children
    ) {}

    public record HotspotDiffEntry(
            String methodSignature,
            Integer rankA,
            Integer rankB,
            double selfTimePctA,
            double selfTimePctB,
            double delta,
            String trend          // UP, DOWN, NEW, RESOLVED
    ) {}

    public record DiagnosticDiff(
            List<Diagnosis> resolved,
            List<Diagnosis> added,
            List<Diagnosis> persisting
    ) {}
}
