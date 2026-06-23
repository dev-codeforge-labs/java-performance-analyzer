package com.devmanchego.performanceanalyzer.api;

import com.devmanchego.performanceanalyzer.analysis.AnalysisOrchestrator;
import com.devmanchego.performanceanalyzer.model.AnalysisResultDto;
import com.devmanchego.performanceanalyzer.model.RulesetDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class AnalysisController {

    private final AnalysisOrchestrator orchestrator;

    public AnalysisController(AnalysisOrchestrator orchestrator) {
        this.orchestrator = orchestrator;
    }

    @PostMapping("/analyze/{sessionId}")
    public ResponseEntity<AnalysisResultDto> analyze(
            @PathVariable UUID sessionId,
            @RequestBody(required = false) RulesetDto ruleset) {
        return ResponseEntity.ok(orchestrator.analyze(sessionId, ruleset));
    }

    @GetMapping("/analyze/{sessionId}")
    public ResponseEntity<AnalysisResultDto> getResult(@PathVariable UUID sessionId) {
        return orchestrator.getCached(sessionId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
