package com.devmanchego.performanceanalyzer.api;

import com.devmanchego.performanceanalyzer.analysis.AnalysisOrchestrator;
import com.devmanchego.performanceanalyzer.diff.DiffService;
import com.devmanchego.performanceanalyzer.model.AnalysisResultDto;
import com.devmanchego.performanceanalyzer.model.DiffResultDto;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class DiffController {

    private final AnalysisOrchestrator orchestrator;
    private final DiffService diffService;

    public DiffController(AnalysisOrchestrator orchestrator, DiffService diffService) {
        this.orchestrator = orchestrator;
        this.diffService = diffService;
    }

    @PostMapping("/diff")
    public ResponseEntity<DiffResultDto> diff(
            @RequestParam UUID sessionIdA,
            @RequestParam UUID sessionIdB,
            @RequestParam(defaultValue = "5.0") double deltaThreshold) {

        AnalysisResultDto a = orchestrator.getCached(sessionIdA)
                .orElseThrow(() -> new IllegalArgumentException("Session A not found or not analyzed: " + sessionIdA));
        AnalysisResultDto b = orchestrator.getCached(sessionIdB)
                .orElseThrow(() -> new IllegalArgumentException("Session B not found or not analyzed: " + sessionIdB));

        DiffResultDto result = diffService.diff(a, b, deltaThreshold, UUID.randomUUID());
        return ResponseEntity.ok(result);
    }
}
