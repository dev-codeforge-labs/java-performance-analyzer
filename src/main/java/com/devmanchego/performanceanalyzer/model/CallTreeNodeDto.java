package com.devmanchego.performanceanalyzer.model;

import java.util.List;

public record CallTreeNodeDto(
        String methodSignature,
        String layerCategory,
        int samples,
        int selfSamples,
        double totalTimePercent,
        double selfTimePercent,
        List<CallTreeNodeDto> children
) {}
