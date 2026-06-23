package com.devmanchego.performanceanalyzer.model;

public record GcEventDto(
        double timestamp,
        String type,
        double pauseMs,
        long heapBeforeBytes,
        long heapAfterBytes,
        long heapTotalBytes,
        String gcAlgorithmHint
) {}
