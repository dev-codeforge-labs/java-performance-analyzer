package com.devmanchego.performanceanalyzer.diagnostic;

import com.devmanchego.performanceanalyzer.model.AnalysisContext;
import com.devmanchego.performanceanalyzer.model.Diagnosis;

import java.util.List;

public interface PerformanceRule {
    List<Diagnosis> analyze(AnalysisContext ctx);
}
