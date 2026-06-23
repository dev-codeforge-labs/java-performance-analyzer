package com.devmanchego.performanceanalyzer.model;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AnalysisResultDto(
        UUID sessionId,
        Instant analyzedAt,
        List<String> fileNames,
        List<String> dumpTypes,
        Integer snapshotCount,
        Integer totalThreads,
        CallTreeNodeDto callTree,
        CallTreeNodeDto invertedCallTree,
        List<HotspotDto> hotspots,
        List<Diagnosis> diagnoses,
        HealthScoreDto healthScore,
        TimelineDto timeline,
        GcLogResult gcSummary,
        List<ParseWarning> warnings
) {}
