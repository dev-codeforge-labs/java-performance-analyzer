package com.devmanchego.performanceanalyzer.model;

import java.util.List;
import java.util.Map;

public record RulesetDto(
        String version,
        List<CustomSignature> customSignatures,
        Map<String, Double> thresholds
) {
    public record CustomSignature(
            String prefix,
            String layer,
            String diagnosisMessage,
            String severity
    ) {}

    public static RulesetDto empty() {
        return new RulesetDto("1.0", List.of(), Map.of());
    }

    public double threshold(String key, double defaultValue) {
        if (thresholds == null) return defaultValue;
        return thresholds.getOrDefault(key, defaultValue);
    }
}
