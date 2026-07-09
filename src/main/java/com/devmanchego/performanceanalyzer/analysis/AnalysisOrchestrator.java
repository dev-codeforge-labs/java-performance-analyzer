package com.devmanchego.performanceanalyzer.analysis;

import com.devmanchego.performanceanalyzer.aggregation.CallTreeBuilder;
import com.devmanchego.performanceanalyzer.aggregation.CallTreeSerializer;
import com.devmanchego.performanceanalyzer.aggregation.HotspotExtractor;
import com.devmanchego.performanceanalyzer.diagnostic.DiagnosticEngine;
import com.devmanchego.performanceanalyzer.diagnostic.SignatureDictionary;
import com.devmanchego.performanceanalyzer.diagnostic.rules.GcThrashingRule;
import com.devmanchego.performanceanalyzer.health.HealthScoreService;
import com.devmanchego.performanceanalyzer.metrics.AnalysisMetricsService;
import com.devmanchego.performanceanalyzer.metrics.WebhookService;
import com.devmanchego.performanceanalyzer.model.*;
import com.devmanchego.performanceanalyzer.session.AnalysisSession;
import com.devmanchego.performanceanalyzer.session.SessionStore;
import com.devmanchego.performanceanalyzer.timeline.TimelineService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class AnalysisOrchestrator {

    private final SessionStore sessionStore;
    private final CallTreeBuilder treeBuilder;
    private final HotspotExtractor hotspotExtractor;
    private final CallTreeSerializer treeSerializer;
    private final DiagnosticEngine diagnosticEngine;
    private final SignatureDictionary signatureDictionary;
    private final HealthScoreService healthScoreService;
    private final TimelineService timelineService;
    private final AnalysisMetricsService metricsService;
    private final WebhookService webhookService;
    private final GcThrashingRule gcThrashingRule;

    // Cache of computed results by sessionId
    private final Map<UUID, AnalysisResultDto> resultCache = new java.util.concurrent.ConcurrentHashMap<>();

    public AnalysisOrchestrator(SessionStore sessionStore,
                                 CallTreeBuilder treeBuilder,
                                 HotspotExtractor hotspotExtractor,
                                 CallTreeSerializer treeSerializer,
                                 DiagnosticEngine diagnosticEngine,
                                 SignatureDictionary signatureDictionary,
                                 HealthScoreService healthScoreService,
                                 TimelineService timelineService,
                                 AnalysisMetricsService metricsService,
                                 WebhookService webhookService,
                                 GcThrashingRule gcThrashingRule) {
        this.sessionStore = sessionStore;
        this.treeBuilder = treeBuilder;
        this.hotspotExtractor = hotspotExtractor;
        this.treeSerializer = treeSerializer;
        this.diagnosticEngine = diagnosticEngine;
        this.signatureDictionary = signatureDictionary;
        this.healthScoreService = healthScoreService;
        this.timelineService = timelineService;
        this.metricsService = metricsService;
        this.webhookService = webhookService;
        this.gcThrashingRule = gcThrashingRule;
    }

    public AnalysisResultDto analyze(UUID sessionId, RulesetDto ruleset) {
        AnalysisSession session = sessionStore.get(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));

        if (ruleset == null) ruleset = RulesetDto.empty();

        List<SnapshotResult> snapshots = List.of();
        List<ParseWarning> warnings = new ArrayList<>();

        if (session.getThreadDumpResult() != null) {
            snapshots = session.getThreadDumpResult().snapshots();
            warnings.addAll(session.getThreadDumpResult().globalWarnings());
        }

        // Build call tree
        CallTreeNode root = treeBuilder.build(snapshots);
        int totalSamples = treeBuilder.totalSamples(snapshots);

        // Apply layer classification
        applyLayerCategories(root, ruleset);

        // Extract hotspots
        List<HotspotDto> hotspots = hotspotExtractor.extract(root, totalSamples);

        // Enrich hotspots with diagnosis from signature dictionary
        hotspots = enrichHotspots(hotspots, ruleset);

        // Build lock graph
        Map<String, String> lockGraph = buildLockGraph(snapshots);

        // Run diagnostic rules
        RulesetDto finalRuleset = ruleset;
        AnalysisContext ctx = new AnalysisContext(root, hotspots, snapshots, lockGraph, totalSamples, finalRuleset);
        List<Diagnosis> diagnoses = new ArrayList<>(diagnosticEngine.analyze(ctx));

        // GC diagnoses — the GC rule is not part of the AnalysisContext pipeline
        // because GC data lives on the session, so fire it explicitly here.
        GcLogResult gcResult = session.getGcLogResult();
        if (gcResult != null) {
            warnings.addAll(gcResult.warnings());
            diagnoses.addAll(gcThrashingRule.analyzeGc(gcResult.gcTimePercent(), finalRuleset::threshold));
        }

        // Health score
        boolean confirmedDeadlock = !lockGraph.isEmpty() &&
                diagnoses.stream().anyMatch(d -> "DEADLOCK_DETECTED".equals(d.ruleId()));
        double blockedPct = ctx.blockedPercent();
        double topSelfTime = hotspots.isEmpty() ? 0 : hotspots.get(0).selfTimePercent();
        HealthScoreDto healthScore = healthScoreService.compute(blockedPct, confirmedDeadlock, diagnoses, topSelfTime);

        // Serialize call tree
        CallTreeNodeDto treeDto = snapshots.isEmpty() ? null : treeSerializer.serialize(root, totalSamples);
        CallTreeNodeDto invertedDto = snapshots.isEmpty() ? null : treeSerializer.serializeInverted(root, totalSamples);

        // Timeline
        TimelineDto timeline = timelineService.build(snapshots, hotspots);

        int totalThreads = snapshots.stream().mapToInt(s -> s.threads().size()).sum();

        AnalysisResultDto result = new AnalysisResultDto(
                sessionId,
                Instant.now(),
                session.getFileNames(),
                session.getDumpTypes().stream().map(Enum::name).toList(),
                snapshots.size(),
                totalThreads,
                treeDto,
                invertedDto,
                hotspots,
                diagnoses,
                healthScore,
                timeline,
                gcResult,
                warnings
        );

        resultCache.put(sessionId, result);
        metricsService.update(result);
        webhookService.sendAsync(result);

        return result;
    }

    public Optional<AnalysisResultDto> getCached(UUID sessionId) {
        return Optional.ofNullable(resultCache.get(sessionId));
    }

    /** Drops a cached result immediately, e.g. when its session is explicitly deleted. */
    public void evict(UUID sessionId) {
        resultCache.remove(sessionId);
    }

    /** Drops every cached result, e.g. when all sessions are cleared. */
    public void evictAll() {
        resultCache.clear();
    }

    // Prune cached results whose session has been evicted from the store,
    // so the cache does not grow unbounded (each entry holds a full call tree).
    @Scheduled(fixedDelay = 900_000)
    public void evictOrphanedResults() {
        resultCache.keySet().removeIf(id -> !sessionStore.exists(id));
    }

    private void applyLayerCategories(CallTreeNode node, RulesetDto ruleset) {
        if (!"[root]".equals(node.getMethodSignature())) {
            String pkg = extractPackage(node.getMethodSignature());
            node.setLayerCategory(signatureDictionary.resolveLayer(pkg, ruleset));
        }
        node.getChildren().values().forEach(child -> applyLayerCategories(child, ruleset));
    }

    private List<HotspotDto> enrichHotspots(List<HotspotDto> hotspots, RulesetDto ruleset) {
        return hotspots.stream().map(h -> {
            String diag = signatureDictionary.resolveDiagnosis(h.packagePrefix(), ruleset);
            String layer = signatureDictionary.resolveLayer(h.packagePrefix(), ruleset);
            return new HotspotDto(h.rank(), h.methodSignature(), h.className(), h.packagePrefix(),
                    layer, h.selfTimePercent(), h.totalTimePercent(), h.topCallerSignature(),
                    diag, diag != null ? "WARNING" : null);
        }).toList();
    }

    private Map<String, String> buildLockGraph(List<SnapshotResult> snapshots) {
        if (snapshots.isEmpty()) return Map.of();

        // Build the graph from a single snapshot so every edge reflects one consistent
        // instant. Accumulating monitors/waiters across snapshots can fabricate cycles
        // between threads that were never blocked at the same time. Deadlocks are
        // permanent, so the most recent snapshot still contains any real one.
        SnapshotResult snap = snapshots.get(snapshots.size() - 1);

        Map<String, String> monitorToHolder = new HashMap<>();
        for (ThreadInfo t : snap.threads()) {
            for (String monitor : t.lockedMonitors()) {
                monitorToHolder.put(monitor, t.id());
            }
        }

        // Map: blocked thread id -> holding thread id (via monitor address)
        Map<String, String> lockGraph = new HashMap<>();
        for (ThreadInfo t : snap.threads()) {
            if (t.state() == ThreadState.BLOCKED && t.waitingOnMonitor() != null) {
                String holder = monitorToHolder.get(t.waitingOnMonitor());
                if (holder != null && !holder.equals(t.id())) {
                    lockGraph.put(t.id(), holder);
                }
            }
        }
        return lockGraph;
    }

    private String extractPackage(String sig) {
        int parenIdx = sig.indexOf('(');
        String withoutArgs = parenIdx > 0 ? sig.substring(0, parenIdx) : sig;
        int lastDot = withoutArgs.lastIndexOf('.');
        if (lastDot < 0) return sig;
        String className = withoutArgs.substring(0, lastDot);
        int pkgDot = className.lastIndexOf('.');
        return pkgDot > 0 ? className.substring(0, pkgDot) : className;
    }
}
