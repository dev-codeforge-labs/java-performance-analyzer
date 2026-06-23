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

    private List<String> detectCycles(Map<String, String> graph) {
        Set<String> visited = new HashSet<>();
        Set<String> inStack = new HashSet<>();
        List<String> cycleNodes = new ArrayList<>();

        for (String node : graph.keySet()) {
            if (!visited.contains(node)) {
                dfs(node, graph, visited, inStack, cycleNodes);
            }
        }
        return cycleNodes;
    }

    private boolean dfs(String node, Map<String, String> graph,
                        Set<String> visited, Set<String> inStack, List<String> cycleNodes) {
        visited.add(node);
        inStack.add(node);

        String neighbor = graph.get(node);
        if (neighbor != null) {
            if (!visited.contains(neighbor)) {
                if (dfs(neighbor, graph, visited, inStack, cycleNodes)) {
                    cycleNodes.add(node);
                    return true;
                }
            } else if (inStack.contains(neighbor)) {
                cycleNodes.add(node);
                cycleNodes.add(neighbor);
                return true;
            }
        }
        inStack.remove(node);
        return false;
    }
}
