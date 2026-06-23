package com.devmanchego.performanceanalyzer.metrics;

import com.devmanchego.performanceanalyzer.model.AnalysisResultDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@Service
public class WebhookService {

    private static final Logger log = LoggerFactory.getLogger(WebhookService.class);

    @Value("${performanceanalyzer.integrations.webhook.url:}")
    private String webhookUrl;

    @Value("${performanceanalyzer.integrations.webhook.timeout-ms:5000}")
    private int timeoutMs;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    public void sendAsync(AnalysisResultDto result) {
        if (webhookUrl == null || webhookUrl.isBlank()) return;

        Thread.ofVirtual().start(() -> {
            try {
                Map<String, Object> payload = buildPayload(result);
                String body = mapper.writeValueAsString(payload);
                HttpRequest req = HttpRequest.newBuilder()
                        .uri(URI.create(webhookUrl))
                        .timeout(Duration.ofMillis(timeoutMs))
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body))
                        .build();
                httpClient.send(req, HttpResponse.BodyHandlers.discarding());
            } catch (Exception e) {
                log.warn("Webhook delivery failed: {}", e.getMessage());
            }
        });
    }

    private Map<String, Object> buildPayload(AnalysisResultDto r) {
        var hs = r.healthScore();
        var topHotspot = r.hotspots() != null && !r.hotspots().isEmpty() ? r.hotspots().get(0) : null;
        long criticalCount = r.diagnoses() != null
                ? r.diagnoses().stream().filter(d -> "CRITICAL".equals(d.severity())).count() : 0;
        long warningCount = r.diagnoses() != null
                ? r.diagnoses().stream().filter(d -> "WARNING".equals(d.severity())).count() : 0;

        return Map.of(
                "schemaVersion", "1.0",
                "timestamp", Instant.now().toString(),
                "analysisId", r.sessionId().toString(),
                "source", "performance-analyzer",
                "healthScore", hs != null ? Map.of(
                        "value", hs.score(),
                        "classification", hs.classification(),
                        "dominantFactor", hs.dominantFactor()) : Map.of(),
                "summary", Map.of(
                        "totalThreads", r.totalThreads() != null ? r.totalThreads() : 0,
                        "criticalDiagnosesCount", criticalCount,
                        "warningDiagnosesCount", warningCount),
                "topHotspot", topHotspot != null ? Map.of(
                        "methodSignature", topHotspot.methodSignature(),
                        "selfTimePercent", topHotspot.selfTimePercent(),
                        "layer", topHotspot.layerCategory() != null ? topHotspot.layerCategory() : "") : Map.of(),
                "dumpTypes", r.dumpTypes(),
                "fileNames", r.fileNames()
        );
    }
}
