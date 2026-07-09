package com.devmanchego.performanceanalyzer.health;

import com.devmanchego.performanceanalyzer.model.Diagnosis;
import com.devmanchego.performanceanalyzer.model.HealthScoreDto;
import com.devmanchego.performanceanalyzer.model.HealthScoreDto.SignalBreakdown;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class HealthScoreService {

    @Value("${performanceanalyzer.healthscore.weights.blocked-threads:0.30}")
    private double wBlocked;
    @Value("${performanceanalyzer.healthscore.weights.deadlock:0.25}")
    private double wDeadlock;
    @Value("${performanceanalyzer.healthscore.weights.critical-diagnoses:0.25}")
    private double wCritical;
    @Value("${performanceanalyzer.healthscore.weights.hotspot-concentration:0.20}")
    private double wConcentration;

    public HealthScoreDto compute(double blockedPercent,
                                  boolean confirmedDeadlock,
                                  List<Diagnosis> diagnoses,
                                  double topHotspotSelfTimePercent) {

        // Signal scores (0-100 each)
        double blockedScore = clamp(100.0 - (blockedPercent / 50.0) * 100.0);
        double deadlockScore = confirmedDeadlock ? 0.0 : 100.0;

        long criticalCount = diagnoses.stream().filter(d -> "CRITICAL".equals(d.severity())).count();
        double criticalScore = clamp(100.0 - (criticalCount / 5.0) * 100.0);

        double concentrationScore = topHotspotSelfTimePercent <= 10.0 ? 100.0
                : topHotspotSelfTimePercent >= 50.0 ? 0.0
                : clamp(100.0 - ((topHotspotSelfTimePercent - 10.0) / 40.0) * 100.0);

        double weighted = blockedScore * wBlocked
                + deadlockScore * wDeadlock
                + criticalScore * wCritical
                + concentrationScore * wConcentration;

        int score = (int) Math.round(weighted);
        String classification = score >= 80 ? "GREEN" : score >= 50 ? "YELLOW" : "RED";

        List<SignalBreakdown> breakdown = List.of(
                new SignalBreakdown("Blocked threads %", blockedPercent, blockedScore, wBlocked),
                new SignalBreakdown("Confirmed deadlock", confirmedDeadlock ? 1 : 0, deadlockScore, wDeadlock),
                new SignalBreakdown("Critical diagnoses", criticalCount, criticalScore, wCritical),
                new SignalBreakdown("Hotspot concentration %", topHotspotSelfTimePercent, concentrationScore, wConcentration)
        );

        // The dominant factor is the signal that removed the most weighted points
        // from a perfect score, i.e. max of (100 - points) * weight.
        String dominantFactor = breakdown.stream()
                .max(Comparator.comparingDouble(b -> (100.0 - b.points()) * b.weight()))
                .filter(b -> b.points() < 100.0)
                .map(b -> "Score reduced primarily by: " + b.signal() + " = " + fmt(b.rawValue()))
                .orElse("No significant score reduction");

        return new HealthScoreDto(score, classification, dominantFactor, breakdown);
    }

    private double clamp(double v) { return Math.max(0.0, Math.min(100.0, v)); }
    private String fmt(double v) { return v == Math.floor(v) ? String.valueOf((long) v) : String.format("%.1f", v); }
}
