package com.devmanchego.performanceanalyzer.diagnostic.rules;

import com.devmanchego.performanceanalyzer.diagnostic.PerformanceRule;
import com.devmanchego.performanceanalyzer.model.AnalysisContext;
import com.devmanchego.performanceanalyzer.model.Diagnosis;
import com.devmanchego.performanceanalyzer.model.HotspotDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class AlgorithmicAlertsRule implements PerformanceRule {

    @Value("${performanceanalyzer.thresholds.min-self-time-critical:5.0}")
    private double minSelfTime;

    @Override
    public List<Diagnosis> analyze(AnalysisContext ctx) {
        List<Diagnosis> results = new ArrayList<>();
        double threshold = ctx.ruleset().threshold("min-self-time-critical", minSelfTime);

        for (HotspotDto h : ctx.hotspots()) {
            if (h.selfTimePercent() < threshold) continue;
            String sig = h.methodSignature();

            if (matches(sig, "ArrayList.indexOf", "ArrayList.contains", "LinkedList")) {
                results.add(Diagnosis.warning("LINEAR_SCAN",
                        "Linear scan detected on list: " + sig + " (" + fmt(h.selfTimePercent()) + "% self-time). Consider using a Set or Map for O(1) lookup.",
                        sig, h.layerCategory()));
            }
            if (matches(sig, "StringBuilder.append", "String.concat")) {
                results.add(Diagnosis.warning("STRING_CONCAT_LOOP",
                        "String concatenation hotspot: " + sig + " (" + fmt(h.selfTimePercent()) + "% self-time). Possible concatenation inside a loop.",
                        sig, h.layerCategory()));
            }
            if (matches(sig, "java.lang.reflect.Method.invoke")) {
                results.add(Diagnosis.warning("REFLECTION_ABUSE",
                        "Reflection hotspot: " + sig + " (" + fmt(h.selfTimePercent()) + "% self-time). Consider caching MethodHandles or avoiding reflection in hot paths.",
                        sig, h.layerCategory()));
            }
            if (matches(sig, "java.lang.ref.Finalizer")) {
                results.add(Diagnosis.warning("FINALIZER_BACKLOG",
                        "Finalizer thread is hot (" + fmt(h.selfTimePercent()) + "% self-time). Objects with finalizers are backing up — prefer try-with-resources / Cleaner API.",
                        sig, h.layerCategory()));
            }
        }
        return results;
    }

    private boolean matches(String sig, String... patterns) {
        for (String p : patterns) if (sig.contains(p)) return true;
        return false;
    }

    private String fmt(double d) { return String.format("%.1f", d); }
}
