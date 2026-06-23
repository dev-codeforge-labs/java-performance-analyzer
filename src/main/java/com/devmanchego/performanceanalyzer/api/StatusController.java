package com.devmanchego.performanceanalyzer.api;

import com.devmanchego.performanceanalyzer.metrics.AnalysisMetricsService;
import com.devmanchego.performanceanalyzer.model.AnalysisResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/status")
public class StatusController {

    private final AnalysisMetricsService metricsService;

    public StatusController(AnalysisMetricsService metricsService) {
        this.metricsService = metricsService;
    }

    @GetMapping("/latest")
    public ResponseEntity<Map<String, Object>> latest() {
        AnalysisResultDto result = metricsService.getLatest();
        if (result == null) {
            return ResponseEntity.ok(Map.of("status", "NO_DATA"));
        }
        var hs = result.healthScore();
        return ResponseEntity.ok(Map.of(
                "sessionId", result.sessionId(),
                "analyzedAt", result.analyzedAt(),
                "healthScore", hs != null ? hs.score() : -1,
                "classification", hs != null ? hs.classification() : "UNKNOWN",
                "dominantFactor", hs != null ? hs.dominantFactor() : "",
                "totalThreads", result.totalThreads() != null ? result.totalThreads() : 0,
                "criticalDiagnoses", result.diagnoses() != null
                        ? result.diagnoses().stream().filter(d -> "CRITICAL".equals(d.severity())).count() : 0,
                "warningDiagnoses", result.diagnoses() != null
                        ? result.diagnoses().stream().filter(d -> "WARNING".equals(d.severity())).count() : 0
        ));
    }
}
