package com.devmanchego.performanceanalyzer.export;

import com.devmanchego.performanceanalyzer.model.*;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a structured Word (.docx) report using Apache POI.
 * Each analysis section has a distinct color-coded heading and table layout.
 */
@Component
public class DocxExporter {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Section heading colours (hex, no #)
    private static final String COL_COVER    = "1E40AF";  // blue-800
    private static final String COL_EXEC     = "1E3A8A";  // blue-900
    private static final String COL_THREAD   = "6D28D9";  // violet-700
    private static final String COL_HOTSPOT  = "B91C1C";  // red-700
    private static final String COL_DIAG     = "B45309";  // amber-700
    private static final String COL_GC       = "15803D";  // green-700
    private static final String COL_TIMELINE = "0F766E";  // teal-700
    private static final String COL_WHITE    = "FFFFFF";
    private static final String COL_LIGHT    = "F8FAFC";

    public byte[] export(AnalysisResultDto result) throws Exception {
        try (XWPFDocument doc = new XWPFDocument()) {

            renderCover(doc, result);
            renderExecutiveSummary(doc, result);

            if (hasThreadData(result)) {
                renderThreadSection(doc, result);
            }
            if (result.hotspots() != null && !result.hotspots().isEmpty()) {
                renderHotspotSection(doc, result);
            }
            if (result.diagnoses() != null && !result.diagnoses().isEmpty()) {
                renderDiagnosisSection(doc, result);
            }
            if (result.gcSummary() != null) {
                renderGcSection(doc, result.gcSummary());
            }
            if (result.timeline() != null && result.timeline().snapshots() != null
                    && result.timeline().snapshots().size() > 1) {
                renderTimelineSection(doc, result.timeline());
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            doc.write(baos);
            return baos.toByteArray();
        }
    }

    // ── Cover ──────────────────────────────────────────────────────────────────

    private void renderCover(XWPFDocument doc, AnalysisResultDto result) {
        XWPFParagraph title = doc.createParagraph();
        title.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun r = title.createRun();
        r.setText("Performance Analyzer");
        r.setBold(true);
        r.setFontSize(26);
        r.setColor(COL_COVER);
        r.addBreak();

        XWPFParagraph sub = doc.createParagraph();
        sub.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun rs = sub.createRun();
        rs.setText("Executive Report");
        rs.setFontSize(14);
        rs.setColor("555555");
        rs.addBreak();
        rs.addBreak();

        // Metadata
        addMeta(doc, "Analysis date:", FMT.format(result.analyzedAt()));
        addMeta(doc, "Files:", String.join(", ", result.fileNames()));
        addMeta(doc, "Dump types:", String.join(", ", result.dumpTypes()));
        if (result.snapshotCount() != null)
            addMeta(doc, "Snapshots:", String.valueOf(result.snapshotCount()));
        if (result.totalThreads() != null)
            addMeta(doc, "Total threads:", String.valueOf(result.totalThreads()));

        blankLine(doc);
    }

    private void addMeta(XWPFDocument doc, String label, String value) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(60);
        XWPFRun rl = p.createRun();
        rl.setText(label + " ");
        rl.setBold(true);
        rl.setFontSize(10);
        XWPFRun rv = p.createRun();
        rv.setText(value);
        rv.setFontSize(10);
    }

    // ── Executive Summary ──────────────────────────────────────────────────────

    private void renderExecutiveSummary(XWPFDocument doc, AnalysisResultDto result) {
        HealthScoreDto hs = result.healthScore();
        if (hs == null) return;

        addSectionHeading(doc, "Executive Summary", COL_EXEC);

        String scoreColor = switch (hs.classification()) {
            case "CRITICAL" -> "DC2626";
            case "DEGRADED" -> "EA580C";
            default         -> "16A34A";
        };

        // Score line
        XWPFParagraph sp = doc.createParagraph();
        sp.setSpacingAfter(80);
        XWPFRun sr = sp.createRun();
        sr.setText("Health Score: ");
        sr.setBold(true);
        sr.setFontSize(13);
        XWPFRun sv = sp.createRun();
        sv.setText(hs.score() + " / 100  [" + hs.classification() + "]");
        sv.setBold(true);
        sv.setFontSize(16);
        sv.setColor(scoreColor);

        // Dominant factor
        addBodyText(doc, "Dominant factor: " + hs.dominantFactor(), "444444", 11, false);

        // Signal breakdown
        if (hs.signalBreakdown() != null && !hs.signalBreakdown().isEmpty()) {
            addBodyText(doc, "Score breakdown:", "000000", 10, true);
            for (var sb : hs.signalBreakdown()) {
                addBodyText(doc, String.format("  • %s  →  %.0f pts  (weight %.0f%%)",
                        sb.signal(), sb.points(), sb.weight() * 100), "555555", 9, false);
            }
        }

        // Top 3 critical diagnoses
        if (result.diagnoses() != null) {
            List<Diagnosis> criticals = result.diagnoses().stream()
                    .filter(d -> "CRITICAL".equals(d.severity())).limit(3).toList();
            if (!criticals.isEmpty()) {
                blankLine(doc);
                addBodyText(doc, "Critical findings:", "000000", 10, true);
                for (Diagnosis d : criticals) {
                    addBodyText(doc, "  ⚠ " + d.message(), "DC2626", 10, false);
                }
            }
        }

        blankLine(doc);
    }

    // ── Thread Analysis ────────────────────────────────────────────────────────

    private void renderThreadSection(XWPFDocument doc, AnalysisResultDto result) {
        addSectionHeading(doc, "Thread Analysis", COL_THREAD);

        if (result.timeline() != null && !result.timeline().snapshots().isEmpty()) {
            var snap = result.timeline().snapshots().get(0);
            addBodyText(doc, "Thread state distribution (first snapshot):", "000000", 10, true);

            XWPFTable t = doc.createTable(5, 2);
            t.setWidth("60%");
            styleTableHeader(t.getRow(0), new String[]{"State", "%"}, COL_THREAD);
            setRow(t, 1, "RUNNABLE",      fmt1(snap.runnablePercent()),     false);
            setRow(t, 2, "BLOCKED",       fmt1(snap.blockedPercent()),       true);
            setRow(t, 3, "WAITING",       fmt1(snap.waitingPercent()),       false);
            setRow(t, 4, "TIMED_WAITING", fmt1(snap.timedWaitingPercent()), false);
        }

        if (result.timeline() != null && result.timeline().confirmedDeadlock()) {
            blankLine(doc);
            addBodyText(doc, "⛔ CONFIRMED DEADLOCK DETECTED — thread remains blocked across all snapshots.",
                    "DC2626", 11, true);
        }

        blankLine(doc);
    }

    private void setRow(XWPFTable t, int rowIdx, String label, String value, boolean highlight) {
        XWPFTableRow row = t.getRow(rowIdx);
        row.getCell(0).setText(label);
        setBold(row.getCell(0), false);
        row.getCell(1).setText(value);
        if (highlight) {
            setCellColor(row.getCell(0), "FEF2F2");
            setCellColor(row.getCell(1), "FEF2F2");
            setRunColor(row.getCell(1), "DC2626");
        }
    }

    // ── Hotspot Analysis ───────────────────────────────────────────────────────

    private void renderHotspotSection(XWPFDocument doc, AnalysisResultDto result) {
        addSectionHeading(doc, "Hotspot Analysis  (top " +
                Math.min(result.hotspots().size(), 15) + " methods)", COL_HOTSPOT);

        List<HotspotDto> top = result.hotspots().stream().limit(15).toList();
        XWPFTable t = doc.createTable(top.size() + 1, 6);
        t.setWidth("100%");
        styleTableHeader(t.getRow(0), new String[]{"#", "Method", "Layer", "Self%", "Total%", "Diagnosis"}, COL_HOTSPOT);

        for (int i = 0; i < top.size(); i++) {
            HotspotDto h = top.get(i);
            XWPFTableRow row = t.getRow(i + 1);
            boolean alt = i % 2 == 1;
            String bg = alt ? COL_LIGHT : COL_WHITE;

            fillCell(row, 0, String.valueOf(h.rank()),              bg);
            fillCell(row, 1, shortSig(h.methodSignature()),         bg);
            fillCell(row, 2, nvl(h.layerCategory()),                bg);
            fillCell(row, 3, String.format("%.1f%%", h.selfTimePercent()),  bg);
            fillCell(row, 4, String.format("%.1f%%", h.totalTimePercent()), bg);
            fillCell(row, 5, nvl(h.diagnosis()),                    bg);

            // Color Self% cell by severity
            String sevColor = severityHex(h.severity());
            if (sevColor != null) setRunColor(row.getCell(3), sevColor);
        }

        blankLine(doc);
    }

    // ── Diagnosis Report ───────────────────────────────────────────────────────

    private void renderDiagnosisSection(XWPFDocument doc, AnalysisResultDto result) {
        addSectionHeading(doc, "Diagnosis Report", COL_DIAG);

        for (String sev : new String[]{"CRITICAL", "WARNING", "INFO"}) {
            List<Diagnosis> group = result.diagnoses().stream()
                    .filter(d -> sev.equals(d.severity())).toList();
            if (group.isEmpty()) continue;

            String color = severityHex(sev);
            addBodyText(doc, sev + "  (" + group.size() + " findings)", color, 10, true);

            XWPFTable t = doc.createTable(group.size() + 1, 3);
            t.setWidth("100%");
            styleTableHeader(t.getRow(0), new String[]{"Rule ID", "Affected Method", "Message"}, color);

            for (int i = 0; i < group.size(); i++) {
                Diagnosis d = group.get(i);
                XWPFTableRow row = t.getRow(i + 1);
                String bg = i % 2 == 1 ? COL_LIGHT : COL_WHITE;
                fillCell(row, 0, nvl(d.ruleId()),                         bg);
                fillCell(row, 1, d.affectedMethod() != null ? shortSig(d.affectedMethod()) : "", bg);
                fillCell(row, 2, d.message(),                              bg);
            }
            blankLine(doc);
        }

        blankLine(doc);
    }

    // ── GC Analysis ───────────────────────────────────────────────────────────

    private void renderGcSection(XWPFDocument doc, GcLogResult gc) {
        addSectionHeading(doc, "GC Analysis", COL_GC);

        XWPFTable meta = doc.createTable(7, 2);
        meta.setWidth("70%");
        styleTableHeader(meta.getRow(0), new String[]{"Metric", "Value"}, COL_GC);
        fillRow(meta, 1, "Algorithm",             gc.gcAlgorithm());
        fillRow(meta, 2, "GC time %",             String.format("%.1f%%", gc.gcTimePercent()));
        fillRow(meta, 3, "Max pause",             String.format("%.0f ms", gc.maxPauseMs()));
        fillRow(meta, 4, "Avg pause",             String.format("%.0f ms", gc.avgPauseMs()));
        fillRow(meta, 5, "P99 pause",             String.format("%.0f ms", gc.p99PauseMs()));
        fillRow(meta, 6, "Promotion failure",     gc.promotionFailureDetected()    ? "YES ⚠" : "No");

        if (gc.gcTimePercent() >= 10) {
            blankLine(doc);
            addBodyText(doc, String.format(
                    "⚠ GC THRASHING — application spent %.1f%% of elapsed time in GC pauses.", gc.gcTimePercent()),
                    "DC2626", 11, true);
        }

        if (gc.events() != null && !gc.events().isEmpty()) {
            blankLine(doc);
            addBodyText(doc, "GC Events (first 20):", "000000", 10, true);
            List<GcEventDto> evs = gc.events().stream().limit(20).toList();
            XWPFTable evTable = doc.createTable(evs.size() + 1, 5);
            evTable.setWidth("100%");
            styleTableHeader(evTable.getRow(0),
                    new String[]{"Timestamp", "Type", "Pause (ms)", "Heap before", "Heap after"}, COL_GC);
            for (int i = 0; i < evs.size(); i++) {
                GcEventDto ev = evs.get(i);
                String bg = i % 2 == 1 ? COL_LIGHT : COL_WHITE;
                XWPFTableRow row = evTable.getRow(i + 1);
                fillCell(row, 0, String.format("%.3f s", ev.timestamp()),   bg);
                fillCell(row, 1, nvl(ev.type()),                            bg);
                fillCell(row, 2, String.format("%.0f", ev.pauseMs()),       bg);
                fillCell(row, 3, formatBytes(ev.heapBeforeBytes()),         bg);
                fillCell(row, 4, formatBytes(ev.heapAfterBytes()),          bg);
            }
        }

        blankLine(doc);
    }

    // ── Timeline ───────────────────────────────────────────────────────────────

    private void renderTimelineSection(XWPFDocument doc, TimelineDto timeline) {
        addSectionHeading(doc, "Timeline Analysis", COL_TIMELINE);

        List<TimelineDto.SnapshotSummary> snaps = timeline.snapshots();
        addBodyText(doc, snaps.size() + " snapshots analyzed.", "000000", 10, false);

        if (timeline.confirmedDeadlock()) {
            addBodyText(doc, "⛔ CONFIRMED DEADLOCK — a thread remains blocked across all snapshots.",
                    "DC2626", 11, true);
        }

        XWPFTable t = doc.createTable(snaps.size() + 1, 6);
        t.setWidth("100%");
        styleTableHeader(t.getRow(0),
                new String[]{"Snapshot", "Timestamp", "Runnable%", "Blocked%", "Waiting%", "TimedWaiting%"},
                COL_TIMELINE);

        for (int i = 0; i < snaps.size(); i++) {
            TimelineDto.SnapshotSummary s = snaps.get(i);
            String bg = i % 2 == 1 ? COL_LIGHT : COL_WHITE;
            XWPFTableRow row = t.getRow(i + 1);
            fillCell(row, 0, "t" + s.index(),                                         bg);
            fillCell(row, 1, s.timestamp() != null ? s.timestamp() : "",              bg);
            fillCell(row, 2, fmt1(s.runnablePercent()),                               bg);
            fillCell(row, 3, fmt1(s.blockedPercent()),                                bg);
            fillCell(row, 4, fmt1(s.waitingPercent()),                                bg);
            fillCell(row, 5, fmt1(s.timedWaitingPercent()),                           bg);
            if (s.blockedPercent() > 20) setRunColor(row.getCell(3), "DC2626");
        }

        if (timeline.hotspotTrends() != null && !timeline.hotspotTrends().isEmpty()) {
            blankLine(doc);
            addBodyText(doc, "Hotspot trends across snapshots:", "000000", 10, true);
            for (var trend : timeline.hotspotTrends().stream().limit(5).toList()) {
                String values = trend.selfTimePercents().stream()
                        .map(v -> String.format("%.1f%%", v))
                        .reduce((a, b) -> a + " → " + b).orElse("");
                addBodyText(doc, "  " + shortSig(trend.methodSignature()) + ": " + values, "555555", 9, false);
            }
        }

        blankLine(doc);
    }

    // ── Formatting helpers ─────────────────────────────────────────────────────

    private void addSectionHeading(XWPFDocument doc, String title, String colorHex) {
        XWPFParagraph p = doc.createParagraph();
        p.setStyle("Heading1");
        p.setSpacingBefore(200);
        p.setSpacingAfter(100);
        XWPFRun r = p.createRun();
        r.setText(title);
        r.setBold(true);
        r.setFontSize(13);
        r.setColor(colorHex);
        // Draw a bottom border on the paragraph using OOXML CTBorder
        CTBorder border = CTBorder.Factory.newInstance();
        border.setVal(STBorder.SINGLE);
        border.setSz(BigInteger.valueOf(6));
        border.setColor(colorHex);
        p.getCTP().getPPr().getPBdr().setBottom(border);
    }

    private void styleTableHeader(XWPFTableRow row, String[] labels, String colorHex) {
        for (int i = 0; i < labels.length; i++) {
            XWPFTableCell cell = row.getCell(i);
            if (cell == null) cell = row.addNewTableCell();
            setCellColor(cell, colorHex);
            XWPFParagraph p = cell.getParagraphs().get(0);
            XWPFRun r = p.createRun();
            r.setText(labels[i]);
            r.setBold(true);
            r.setFontSize(9);
            r.setColor(COL_WHITE);
        }
    }

    private void fillCell(XWPFTableRow row, int col, String text, String bgHex) {
        XWPFTableCell cell = row.getCell(col);
        if (cell == null) cell = row.addNewTableCell();
        setCellColor(cell, bgHex);
        XWPFParagraph p = cell.getParagraphs().isEmpty()
                ? cell.addParagraph() : cell.getParagraphs().get(0);
        if (p.getRuns().isEmpty()) p.createRun();
        XWPFRun r = p.getRuns().get(0);
        r.setText(text != null ? text : "");
        r.setFontSize(9);
    }

    private void fillRow(XWPFTable t, int rowIdx, String label, String value) {
        XWPFTableRow row = t.getRow(rowIdx);
        XWPFParagraph lp = row.getCell(0).getParagraphs().get(0);
        XWPFRun lr = lp.createRun();
        lr.setText(label);
        lr.setBold(true);
        lr.setFontSize(10);
        XWPFParagraph vp = row.getCell(1).getParagraphs().get(0);
        XWPFRun vr = vp.createRun();
        vr.setText(value);
        vr.setFontSize(10);
    }

    private void addBodyText(XWPFDocument doc, String text, String colorHex, int fontSize, boolean bold) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(60);
        XWPFRun r = p.createRun();
        r.setText(text);
        r.setColor(colorHex);
        r.setFontSize(fontSize);
        r.setBold(bold);
    }

    private void blankLine(XWPFDocument doc) {
        XWPFParagraph p = doc.createParagraph();
        p.setSpacingAfter(100);
        p.createRun().setText("");
    }

    private void setCellColor(XWPFTableCell cell, String hexColor) {
        CTTc ctTc = cell.getCTTc();
        CTTcPr tcPr = ctTc.isSetTcPr() ? ctTc.getTcPr() : ctTc.addNewTcPr();
        CTShd shd = tcPr.isSetShd() ? tcPr.getShd() : tcPr.addNewShd();
        shd.setFill(hexColor);
        shd.setColor(hexColor);
        shd.setVal(STShd.CLEAR);
    }

    private void setRunColor(XWPFTableCell cell, String hexColor) {
        for (XWPFParagraph p : cell.getParagraphs()) {
            for (XWPFRun r : p.getRuns()) {
                r.setColor(hexColor);
                r.setBold(true);
            }
        }
    }

    private void setBold(XWPFTableCell cell, boolean bold) {
        for (XWPFParagraph p : cell.getParagraphs()) {
            for (XWPFRun r : p.getRuns()) r.setBold(bold);
        }
    }

    private String severityHex(String severity) {
        if (severity == null) return null;
        return switch (severity) {
            case "CRITICAL" -> "DC2626";
            case "WARNING"  -> "EA580C";
            case "INFO"     -> "2563EB";
            default         -> null;
        };
    }

    private boolean hasThreadData(AnalysisResultDto result) {
        return (result.totalThreads() != null && result.totalThreads() > 0)
                || (result.timeline() != null && result.timeline().snapshots() != null
                    && !result.timeline().snapshots().isEmpty());
    }

    private String shortSig(String sig) {
        if (sig == null) return "";
        int dot = sig.lastIndexOf('.');
        if (dot < 0) return sig;
        int prev = sig.lastIndexOf('.', dot - 1);
        return prev < 0 ? sig : "…" + sig.substring(prev + 1);
    }

    private String formatBytes(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private String fmt1(double v) { return String.format("%.1f%%", v); }

    private String nvl(String s) { return s != null ? s : ""; }
}
