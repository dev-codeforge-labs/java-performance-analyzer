package com.devmanchego.performanceanalyzer.diagnostic;

import com.devmanchego.performanceanalyzer.model.AnalysisContext;
import com.devmanchego.performanceanalyzer.model.Diagnosis;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class DiagnosticEngine {

    private final List<PerformanceRule> rules;
    private final SignatureDictionary signatureDictionary;

    public DiagnosticEngine(List<PerformanceRule> rules, SignatureDictionary signatureDictionary) {
        this.rules = rules;
        this.signatureDictionary = signatureDictionary;
    }

    public List<Diagnosis> analyze(AnalysisContext ctx) {
        List<Diagnosis> all = new ArrayList<>();
        for (PerformanceRule rule : rules) {
            try {
                all.addAll(rule.analyze(ctx));
            } catch (Exception e) {
                // Rule failure is non-fatal
            }
        }
        return all;
    }

    public SignatureDictionary getSignatureDictionary() {
        return signatureDictionary;
    }
}
