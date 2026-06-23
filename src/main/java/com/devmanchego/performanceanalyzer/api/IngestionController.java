package com.devmanchego.performanceanalyzer.api;

import com.devmanchego.performanceanalyzer.ingestion.IngestionService;
import com.devmanchego.performanceanalyzer.session.AnalysisSession;
import com.devmanchego.performanceanalyzer.session.SessionStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class IngestionController {

    private final IngestionService ingestionService;
    private final SessionStore sessionStore;

    public IngestionController(IngestionService ingestionService, SessionStore sessionStore) {
        this.ingestionService = ingestionService;
        this.sessionStore = sessionStore;
    }

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> upload(
            @RequestParam("files") List<MultipartFile> files) throws IOException {

        if (files == null || files.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(new UploadResponse(null, null, null, null,
                            List.of("No files provided"), Instant.now()));
        }

        AnalysisSession session = ingestionService.ingest(files);

        List<String> warnings = collectWarnings(session);

        return ResponseEntity.ok(new UploadResponse(
                session.getId(),
                session.getFileNames(),
                session.getDumpTypes().stream().map(Enum::name).toList(),
                snapshotCount(session),
                warnings,
                session.getCreatedAt()
        ));
    }

    @DeleteMapping("/session/{id}")
    public ResponseEntity<Void> clearSession(@PathVariable UUID id) {
        sessionStore.remove(id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/session/clear")
    public ResponseEntity<Map<String, Integer>> clearAll() {
        int count = sessionStore.size();
        // Clear all by individual removal is not directly exposed — this is a dev convenience
        return ResponseEntity.ok(Map.of("cleared", count));
    }

    private List<String> collectWarnings(AnalysisSession session) {
        List<String> warnings = new java.util.ArrayList<>();
        if (session.getThreadDumpResult() != null) {
            session.getThreadDumpResult().globalWarnings()
                    .forEach(w -> warnings.add(w.message()));
            session.getThreadDumpResult().snapshots().forEach(s ->
                    s.warnings().forEach(w -> warnings.add("Snapshot " + s.index() + ": " + w.message())));
        }
        if (session.getGcLogResult() != null) {
            session.getGcLogResult().warnings().forEach(w -> warnings.add(w.message()));
        }
        return warnings;
    }

    private Integer snapshotCount(AnalysisSession session) {
        if (session.getThreadDumpResult() != null) {
            return session.getThreadDumpResult().snapshots().size();
        }
        return null;
    }

    public record UploadResponse(
            UUID sessionId,
            List<String> fileNames,
            List<String> detectedTypes,
            Integer snapshotCount,
            List<String> warnings,
            Instant timestamp
    ) {}
}
