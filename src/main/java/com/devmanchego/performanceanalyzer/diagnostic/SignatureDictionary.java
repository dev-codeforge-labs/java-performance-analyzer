package com.devmanchego.performanceanalyzer.diagnostic;

import com.devmanchego.performanceanalyzer.model.RulesetDto;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class SignatureDictionary {

    private List<SignatureEntry> builtIn = List.of();
    private final ObjectMapper mapper = new ObjectMapper();

    @PostConstruct
    void load() {
        try (InputStream is = getClass().getResourceAsStream("/signatures.json")) {
            if (is != null) {
                builtIn = mapper.readValue(is, new TypeReference<>() {});
            }
        } catch (Exception e) {
            builtIn = List.of();
        }
    }

    public String resolveLayer(String packagePrefix, RulesetDto ruleset) {
        // Custom rules take priority
        if (ruleset != null && ruleset.customSignatures() != null) {
            for (RulesetDto.CustomSignature custom : ruleset.customSignatures()) {
                if (packagePrefix.startsWith(custom.prefix())) return custom.layer();
            }
        }
        for (SignatureEntry entry : builtIn) {
            if (packagePrefix.startsWith(entry.prefix())) return entry.layer();
        }
        return "Business Logic";
    }

    public String resolveDiagnosis(String packagePrefix, RulesetDto ruleset) {
        if (ruleset != null && ruleset.customSignatures() != null) {
            for (RulesetDto.CustomSignature custom : ruleset.customSignatures()) {
                if (packagePrefix.startsWith(custom.prefix())) return custom.diagnosisMessage();
            }
        }
        for (SignatureEntry entry : builtIn) {
            if (packagePrefix.startsWith(entry.prefix())) return entry.defaultDiagnosis();
        }
        return null;
    }

    public record SignatureEntry(
            String prefix,
            String layer,
            String description,
            String defaultDiagnosis
    ) {}
}
