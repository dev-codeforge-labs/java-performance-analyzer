package com.devmanchego.performanceanalyzer.model;

public record Diagnosis(
        String ruleId,
        String severity,
        String message,
        String affectedMethod,
        String layer,
        String source
) {
    public static Diagnosis critical(String ruleId, String message, String method, String layer) {
        return new Diagnosis(ruleId, "CRITICAL", message, method, layer, "Built-in");
    }

    public static Diagnosis warning(String ruleId, String message, String method, String layer) {
        return new Diagnosis(ruleId, "WARNING", message, method, layer, "Built-in");
    }

    public static Diagnosis info(String ruleId, String message, String method, String layer) {
        return new Diagnosis(ruleId, "INFO", message, method, layer, "Built-in");
    }
}
