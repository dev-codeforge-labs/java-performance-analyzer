package com.devmanchego.performanceanalyzer.model;

import java.util.List;

public record GcLogResult(
        String gcAlgorithm,
        List<GcEventDto> events,
        double maxPauseMs,
        double avgPauseMs,
        double p99PauseMs,
        double totalElapsedMs,
        double gcTimePercent,
        boolean promotionFailureDetected,
        boolean concurrentModeFailureDetected,
        List<ParseWarning> warnings
) {}
