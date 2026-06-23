package com.devmanchego.performanceanalyzer.model;

public record HotspotDto(
        int rank,
        String methodSignature,
        String className,
        String packagePrefix,
        String layerCategory,
        double selfTimePercent,
        double totalTimePercent,
        String topCallerSignature,
        String diagnosis,
        String severity
) {}
