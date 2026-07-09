package com.devmanchego.performanceanalyzer.diagnostic.rules;

import com.devmanchego.performanceanalyzer.diagnostic.PerformanceRule;
import com.devmanchego.performanceanalyzer.model.AnalysisContext;
import com.devmanchego.performanceanalyzer.model.Diagnosis;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class DeadlockRule implements PerformanceRule {

    @Override
    public List<Diagnosis> analyze(AnalysisContext ctx) {
        Map<String, String> lockGraph = ctx.lockGraph();
        if (lockGraph.isEmpty()) return List.of();

        List<String> cycles = detectCycles(lockGraph);
        if (cycles.isEmpty()) return List.of();

        return List.of(Diagnosis.critical(
                "DEADLOCK_DETECTED",
                "Deadlock detected involving threads: " + String.join(", ", cycles),
                null, null
        ));
    }

    /**
     * Each blocked thread waits on at most one other thread, so the graph has
     * out-degree ≤ 1. We follow each chain and, when it revisits a node already
     * on the current path, report exactly the threads forming that cycle —
     * excluding threads that merely queue behind it.
     */
    private List<String> detectCycles(Map<String, String> graph) {
        Set<String> globallyVisited = new HashSet<>();

        for (String start : graph.keySet()) {
            if (globallyVisited.contains(start)) continue;

            List<String> path = new ArrayList<>();
            Set<String> inPath = new HashSet<>();
            String node = start;

            while (node != null && !globallyVisited.contains(node)) {
                if (inPath.contains(node)) {
                    return new ArrayList<>(path.subList(path.indexOf(node), path.size()));
                }
                inPath.add(node);
                path.add(node);
                node = graph.get(node);
            }
            globallyVisited.addAll(path);
        }
        return List.of();
    }
}
