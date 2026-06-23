package com.devmanchego.performanceanalyzer.api;

import com.devmanchego.performanceanalyzer.analysis.AnalysisOrchestrator;
import com.devmanchego.performanceanalyzer.export.CsvExporter;
import com.devmanchego.performanceanalyzer.export.DocxExporter;
import com.devmanchego.performanceanalyzer.export.PdfExporter;
import com.devmanchego.performanceanalyzer.model.AnalysisResultDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/export")
public class ExportController {

    private final AnalysisOrchestrator orchestrator;
    private final PdfExporter pdfExporter;
    private final DocxExporter docxExporter;
    private final CsvExporter csvExporter;
    private final ObjectMapper objectMapper;

    public ExportController(AnalysisOrchestrator orchestrator, PdfExporter pdfExporter,
                            DocxExporter docxExporter, CsvExporter csvExporter, ObjectMapper objectMapper) {
        this.orchestrator = orchestrator;
        this.pdfExporter = pdfExporter;
        this.docxExporter = docxExporter;
        this.csvExporter = csvExporter;
        this.objectMapper = objectMapper;
    }

    @GetMapping("/pdf/{sessionId}")
    public ResponseEntity<byte[]> exportPdf(@PathVariable UUID sessionId) throws Exception {
        AnalysisResultDto result = getResult(sessionId);
        byte[] pdf = pdfExporter.export(result);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analysis-" + sessionId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    @GetMapping("/docx/{sessionId}")
    public ResponseEntity<byte[]> exportDocx(@PathVariable UUID sessionId) throws Exception {
        AnalysisResultDto result = getResult(sessionId);
        byte[] docx = docxExporter.export(result);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analysis-" + sessionId + ".docx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(docx);
    }

    @GetMapping("/json/{sessionId}")
    public ResponseEntity<byte[]> exportJson(@PathVariable UUID sessionId) throws Exception {
        AnalysisResultDto result = getResult(sessionId);
        byte[] json = objectMapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(result);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"analysis-" + sessionId + ".json\"")
                .contentType(MediaType.APPLICATION_JSON)
                .body(json);
    }

    @GetMapping("/csv/{sessionId}/hotspots")
    public ResponseEntity<byte[]> exportCsv(@PathVariable UUID sessionId) {
        AnalysisResultDto result = getResult(sessionId);
        byte[] csv = csvExporter.exportHotspots(
                result.hotspots() != null ? result.hotspots() : java.util.List.of());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"hotspots-" + sessionId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csv);
    }

    private AnalysisResultDto getResult(UUID sessionId) {
        return orchestrator.getCached(sessionId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "No analysis result found for session: " + sessionId));
    }
}
