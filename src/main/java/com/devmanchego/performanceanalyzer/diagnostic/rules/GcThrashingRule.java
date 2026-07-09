package com.devmanchego.performanceanalyzer.diagnostic.rules;

import com.devmanchego.performanceanalyzer.diagnostic.PerformanceRule;
import com.devmanchego.performanceanalyzer.model.AnalysisContext;
import com.devmanchego.performanceanalyzer.model.Diagnosis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class GcThrashingRule implements PerformanceRule {

    @Value("${performanceanalyzer.thresholds.gc-thrash-alert:10.0}")
    private double defaultGcThreshold;

    @Value("${performanceanalyzer.thresholds.gc-thrash-critical:30.0}")
    private double defaultGcCriticalThreshold;

    @Override
    public List<Diagnosis> analyze(AnalysisContext ctx) {
        // GC data is stored in the session; accessed via context extension in the orchestrator
        // This rule is a no-op here if no GC data — GcLogAnalysisService fires it directly
        return List.of();
    }

    public List<Diagnosis> analyzeGc(double gcTimePercent, RulesetRef ruleset) {
        double threshold = ruleset.threshold("gc-thrash-alert", defaultGcThreshold);
        double criticalThreshold = ruleset.threshold("gc-thrash-critical", defaultGcCriticalThreshold);
        if (gcTimePercent >= threshold) {
            String severity = gcTimePercent >= criticalThreshold ? "CRITICAL" : "WARNING";
            return List.of(new com.devmanchego.performanceanalyzer.model.Diagnosis(
                    "GC_THRASHING", severity,
                    String.format("GC consuming %.1f%% of elapsed time (threshold: %.0f%%). " +
                            "Consider increasing heap size or tuning GC algorithm.", gcTimePercent, threshold),
                    null, "GC", "Built-in"
            ));
        }
        return List.of();
    }

    @FunctionalInterface
    public interface RulesetRef {
        double threshold(String key, double defaultValue);
    }
}
