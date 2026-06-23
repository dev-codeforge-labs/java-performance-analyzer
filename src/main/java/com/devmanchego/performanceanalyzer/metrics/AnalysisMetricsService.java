package com.devmanchego.performanceanalyzer.metrics;

import com.devmanchego.performanceanalyzer.model.AnalysisResultDto;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicReference;

@Service
public class AnalysisMetricsService {

    private final MeterRegistry registry;

    private final AtomicReference<AnalysisResultDto> latest = new AtomicReference<>();

    public AnalysisMetricsService(MeterRegistry registry) {
        this.registry = registry;
        registerGauges();
    }

    public void update(AnalysisResultDto result) {
        latest.set(result);
    }

    public AnalysisResultDto getLatest() {
        return latest.get();
    }

    private void registerGauges() {
        Gauge.builder("pa_health_score", latest, ref -> {
            var r = ref.get();
            return r != null && r.healthScore() != null ? r.healthScore().score() : -1;
        }).description("Performance Analyzer health score (0-100)").register(registry);

        Gauge.builder("pa_threads_total", latest, ref -> {
            var r = ref.get();
            return r != null && r.totalThreads() != null ? r.totalThreads() : 0;
        }).register(registry);

        Gauge.builder("pa_threads_blocked_percent", latest, ref -> {
            var r = ref.get();
            if (r == null || r.healthScore() == null) return 0.0;
            return r.healthScore().signalBreakdown().stream()
                    .filter(b -> b.signal().contains("Blocked"))
                    .mapToDouble(b -> b.rawValue()).findFirst().orElse(0.0);
        }).register(registry);

        Gauge.builder("pa_hotspot_top_self_time_percent", latest, ref -> {
            var r = ref.get();
            if (r == null || r.hotspots() == null || r.hotspots().isEmpty()) return 0.0;
            return r.hotspots().get(0).selfTimePercent();
        }).tag("method", "top").register(registry);

        Gauge.builder("pa_diagnoses_critical_count", latest, ref -> {
            var r = ref.get();
            if (r == null || r.diagnoses() == null) return 0;
            return r.diagnoses().stream().filter(d -> "CRITICAL".equals(d.severity())).count();
        }).register(registry);

        Gauge.builder("pa_diagnoses_warning_count", latest, ref -> {
            var r = ref.get();
            if (r == null || r.diagnoses() == null) return 0;
            return r.diagnoses().stream().filter(d -> "WARNING".equals(d.severity())).count();
        }).register(registry);
    }
}
