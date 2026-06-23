package com.devmanchego.performanceanalyzer.diagnostic.rules;

import com.devmanchego.performanceanalyzer.diagnostic.PerformanceRule;
import com.devmanchego.performanceanalyzer.model.AnalysisContext;
import com.devmanchego.performanceanalyzer.model.Diagnosis;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class BlockedThreadsRule implements PerformanceRule {

    @Value("${performanceanalyzer.thresholds.blocked-threads-alert:20.0}")
    private double defaultThreshold;

    @Override
    public List<Diagnosis> analyze(AnalysisContext ctx) {
        double threshold = ctx.ruleset().threshold("blocked-threads-alert", defaultThreshold);
        double pct = ctx.blockedPercent();
        if (pct >= threshold) {
            String severity = pct >= 50.0 ? "CRITICAL" : "WARNING";
            return List.of(new Diagnosis(
                    "BLOCKED_THREADS",
                    severity,
                    String.format("%.1f%% of threads are BLOCKED — possible lock contention or deadlock.", pct),
                    null, null, "Built-in"
            ));
        }
        return List.of();
    }
}
