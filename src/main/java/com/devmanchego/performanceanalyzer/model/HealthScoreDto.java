package com.devmanchego.performanceanalyzer.model;

import java.util.List;

public record HealthScoreDto(
        int score,
        String classification,
        String dominantFactor,
        List<SignalBreakdown> signalBreakdown
) {
    public record SignalBreakdown(String signal, double rawValue, double points, double weight) {}
}
