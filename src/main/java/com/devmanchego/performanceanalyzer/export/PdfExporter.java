package com.devmanchego.performanceanalyzer.export;

import com.devmanchego.performanceanalyzer.model.*;
import com.itextpdf.kernel.colors.DeviceRgb;
import com.itextpdf.kernel.font.PdfFont;
import com.itextpdf.kernel.font.PdfFontFactory;
import com.itextpdf.kernel.pdf.PdfDocument;
import com.itextpdf.kernel.pdf.PdfWriter;
import com.itextpdf.layout.Document;
import com.itextpdf.layout.element.Cell;
import com.itextpdf.layout.element.Paragraph;
import com.itextpdf.layout.element.Table;
import com.itextpdf.layout.properties.TextAlignment;
import com.itextpdf.layout.properties.UnitValue;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Generates a structured PDF report using iText 8.
 * Each analysis section (hotspots, thread state, GC, diagnoses) has its own
 * visual identity with color-coded headers.
 */
@Component
public class PdfExporter {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    // Section accent colours
    private static final DeviceRgb COL_COVER    = new DeviceRgb(30,  64, 175);  // blue-800
    private static final DeviceRgb COL_THREAD   = new DeviceRgb(109, 40, 217);  // violet-700
    private static final DeviceRgb COL_HOTSPOT  = new DeviceRgb(185, 28,  28);  // red-700
    private static final DeviceRgb COL_DIAG     = new DeviceRgb(180, 83,   9);  // amber-700
    private static final DeviceRgb COL_GC       = new DeviceRgb(21, 128,  61);  // green-700
    private static final DeviceRgb COL_TIMELINE = new DeviceRgb(15, 118, 110);  // teal-700
    private static final DeviceRgb COL_WHITE    = new DeviceRgb(255, 255, 255);
    private static final DeviceRgb COL_LIGHT    = new DeviceRgb(248, 250, 252);
    private static final DeviceRgb COL_TEXT     = new DeviceRgb(15,  23,  42);

    // Severity colours
    private static final DeviceRgb SEV_CRITICAL = new DeviceRgb(220, 38,  38);
    private static final DeviceRgb SEV_WARNING  = new DeviceRgb(234, 88,  12);
    private static final DeviceRgb SEV_INFO     = new DeviceRgb(37,  99, 235);

    public byte[] export(AnalysisResultDto result) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();

        try (PdfDocument pdf = new PdfDocument(new PdfWriter(baos));
             Document doc = new Document(pdf)) {

            PdfFont bold   = PdfFontFactory.createFont("Helvetica-Bold");
            PdfFont normal = PdfFontFactory.createFont("Helvetica");

            renderCover(doc, result, bold, normal);
            renderExecutiveSummary(doc, result, bold, normal);

            if (hasThreadData(result)) {
                renderThreadSection(doc, result, bold, normal);
            }
            if (result.hotspots() != null && !result.hotspots().isEmpty()) {
                renderHotspotSection(doc, result, bold, normal);
            }
            if (result.diagnoses() != null && !result.diagnoses().isEmpty()) {
                renderDiagnosisSection(doc, result, bold, normal);
            }
            if (result.gcSummary() != null) {
                renderGcSection(doc, result.gcSummary(), bold, normal);
            }
            if (result.timeline() != null && result.timeline().snapshots() != null
                    && result.timeline().snapshots().size() > 1) {
                renderTimelineSection(doc, result.timeline(), bold, normal);
            }
        }

        return baos.toByteArray();
    }

    // ── Cover ──────────────────────────────────────────────────────────────────

    private void renderCover(Document doc, AnalysisResultDto result,
                             PdfFont bold, PdfFont normal) {
        // Full-width blue banner
        Table banner = new Table(UnitValue.createPercentArray(new float[]{1})).useAllAvailableWidth();
        Cell bannerCell = new Cell()
                .setBackgroundColor(COL_COVER)
                .setPadding(20)
                .add(new Paragraph("Performance Analyzer")
                        .setFont(bold).setFontSize(24).setFontColor(COL_WHITE)
                        .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph("Executive Report")
                        .setFont(normal).setFontSize(14).setFontColor(COL_WHITE)
                        .setTextAlignment(TextAlignment.CENTER));
        banner.addCell(bannerCell.setBorder(null));
        doc.add(banner);

        doc.add(new Paragraph("\n"));

        // Metadata two-column table
        Table meta = new Table(UnitValue.createPercentArray(new float[]{35, 65})).useAllAvailableWidth();
        addMetaRow(meta, "Analysis date:", FMT.format(result.analyzedAt()), bold, normal);
        addMetaRow(meta, "Files:", String.join(", ", result.fileNames()), bold, normal);
        addMetaRow(meta, "Dump types:", String.join(", ", result.dumpTypes()), bold, normal);
        if (result.snapshotCount() != null) {
            addMetaRow(meta, "Snapshots:", String.valueOf(result.snapshotCount()), bold, normal);
        }
        if (result.totalThreads() != null) {
            addMetaRow(meta, "Total threads:", String.valueOf(result.totalThreads()), bold, normal);
        }
        doc.add(meta);
        doc.add(new Paragraph("\n"));
    }

    private void addMetaRow(Table t, String label, String value, PdfFont bold, PdfFont normal) {
        t.addCell(new Cell().setBorder(null).setPaddingBottom(4)
                .add(new Paragraph(label).setFont(bold).setFontSize(10).setFontColor(COL_TEXT)));
        t.addCell(new Cell().setBorder(null).setPaddingBottom(4)
                .add(new Paragraph(value).setFont(normal).setFontSize(10).setFontColor(COL_TEXT)));
    }

    // ── Executive Summary ──────────────────────────────────────────────────────

    private void renderExecutiveSummary(Document doc, AnalysisResultDto result,
                                        PdfFont bold, PdfFont normal) {
        HealthScoreDto hs = result.healthScore();
        if (hs == null) return;

        addSectionHeader(doc, "Executive Summary", COL_COVER, bold);

        DeviceRgb scoreColor = switch (hs.classification()) {
            case "CRITICAL" -> SEV_CRITICAL;
            case "DEGRADED" -> SEV_WARNING;
            default         -> new DeviceRgb(22, 163, 74); // green
        };

        // Health score badge
        Table scoreTable = new Table(UnitValue.createPercentArray(new float[]{30, 70})).useAllAvailableWidth();
        Cell scoreCell = new Cell()
                .setBackgroundColor(scoreColor)
                .setPadding(12)
                .add(new Paragraph(hs.score() + " / 100")
                        .setFont(bold).setFontSize(28).setFontColor(COL_WHITE)
                        .setTextAlignment(TextAlignment.CENTER))
                .add(new Paragraph(hs.classification())
                        .setFont(bold).setFontSize(13).setFontColor(COL_WHITE)
                        .setTextAlignment(TextAlignment.CENTER));
        scoreTable.addCell(scoreCell.setBorder(null));

        Cell factorCell = new Cell().setPadding(12).setBorder(null)
                .add(new Paragraph("Dominant performance factor:")
                        .setFont(bold).setFontSize(10).setFontColor(COL_TEXT))
                .add(new Paragraph(hs.dominantFactor())
                        .setFont(normal).setFontSize(11).setFontColor(COL_TEXT));

        if (hs.signalBreakdown() != null && !hs.signalBreakdown().isEmpty()) {
            factorCell.add(new Paragraph("\nScore breakdown:").setFont(bold).setFontSize(9).setFontColor(COL_TEXT));
            for (var sb : hs.signalBreakdown()) {
                factorCell.add(new Paragraph(String.format("  • %s → %.0f pts (weight %.0f%%)",
                        sb.signal(), sb.points(), sb.weight() * 100))
                        .setFont(normal).setFontSize(9).setFontColor(COL_TEXT));
            }
        }
        scoreTable.addCell(factorCell);
        doc.add(scoreTable);

        // Top 3 critical diagnoses in plain language
        if (result.diagnoses() != null) {
            List<Diagnosis> criticals = result.diagnoses().stream()
                    .filter(d -> "CRITICAL".equals(d.severity()))
                    .limit(3).toList();
            if (!criticals.isEmpty()) {
                doc.add(new Paragraph("\nTop critical findings:").setFont(bold).setFontSize(11).setFontColor(COL_TEXT));
                for (Diagnosis d : criticals) {
                    doc.add(new Paragraph("  ⚠ " + d.message())
                            .setFont(normal).setFontSize(10).setFontColor(SEV_CRITICAL));
                }
            }
        }

        doc.add(new Paragraph("\n"));
    }

    // ── Thread Analysis ────────────────────────────────────────────────────────

    private void renderThreadSection(Document doc, AnalysisResultDto result,
                                     PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "Thread Analysis", COL_THREAD, bold);

        if (result.timeline() != null && !result.timeline().snapshots().isEmpty()) {
            var snap = result.timeline().snapshots().get(0);
            doc.add(new Paragraph("Thread state distribution (first snapshot):")
                    .setFont(bold).setFontSize(10).setFontColor(COL_TEXT));

            Table stateTable = new Table(UnitValue.createPercentArray(new float[]{40, 60})).useAllAvailableWidth();
            addStateRow(stateTable, "RUNNABLE",      snap.runnablePercent(),      bold, normal);
            addStateRow(stateTable, "BLOCKED",       snap.blockedPercent(),       bold, normal);
            addStateRow(stateTable, "WAITING",       snap.waitingPercent(),       bold, normal);
            addStateRow(stateTable, "TIMED_WAITING", snap.timedWaitingPercent(),  bold, normal);
            doc.add(stateTable);

            if (result.timeline().confirmedDeadlock()) {
                doc.add(new Paragraph("\n⛔ CONFIRMED DEADLOCK DETECTED — thread remains blocked across all snapshots.")
                        .setFont(bold).setFontSize(11).setFontColor(SEV_CRITICAL));
            }
        }

        doc.add(new Paragraph("\n"));
    }

    private void addStateRow(Table t, String state, double pct, PdfFont bold, PdfFont normal) {
        DeviceRgb color = switch (state) {
            case "BLOCKED"       -> SEV_CRITICAL;
            case "WAITING",
                 "TIMED_WAITING" -> SEV_WARNING;
            default              -> new DeviceRgb(22, 163, 74);
        };
        t.addCell(new Cell().setBorder(null).setPaddingBottom(3)
                .add(new Paragraph(state).setFont(bold).setFontSize(10)));
        t.addCell(new Cell().setBorder(null).setPaddingBottom(3)
                .add(new Paragraph(String.format("%.1f%%", pct))
                        .setFont(bold).setFontSize(10).setFontColor(color)));
    }

    // ── Hotspot Analysis ───────────────────────────────────────────────────────

    private void renderHotspotSection(Document doc, AnalysisResultDto result,
                                      PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "Hotspot Analysis", COL_HOTSPOT, bold);

        Table table = new Table(UnitValue.createPercentArray(new float[]{5, 35, 18, 10, 10, 22}))
                .useAllAvailableWidth();

        // Header row
        String[] cols = {"#", "Method", "Layer", "Self%", "Total%", "Diagnosis"};
        for (String col : cols) {
            table.addHeaderCell(new Cell()
                    .setBackgroundColor(COL_HOTSPOT)
                    .add(new Paragraph(col).setFont(bold).setFontSize(9).setFontColor(COL_WHITE)));
        }

        List<HotspotDto> top = result.hotspots().stream().limit(15).toList();
        boolean alt = false;
        for (HotspotDto h : top) {
            DeviceRgb rowBg = alt ? COL_LIGHT : COL_WHITE;
            DeviceRgb sevColor = severityColor(h.severity());
            alt = !alt;

            addDataCell(table, String.valueOf(h.rank()),               rowBg, normal, 9);
            addDataCell(table, shortSig(h.methodSignature()),          rowBg, normal, 8);
            addDataCell(table, h.layerCategory() != null ? h.layerCategory() : "", rowBg, normal, 9);
            addDataCell(table, String.format("%.1f%%", h.selfTimePercent()),  rowBg, bold,   9, sevColor);
            addDataCell(table, String.format("%.1f%%", h.totalTimePercent()), rowBg, normal, 9);
            addDataCell(table, h.diagnosis() != null ? h.diagnosis() : "",    rowBg, normal, 8);
        }

        doc.add(table);
        doc.add(new Paragraph("\n"));
    }

    // ── Diagnosis Report ───────────────────────────────────────────────────────

    private void renderDiagnosisSection(Document doc, AnalysisResultDto result,
                                        PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "Diagnosis Report", COL_DIAG, bold);

        String[] severities = {"CRITICAL", "WARNING", "INFO"};
        for (String sev : severities) {
            List<Diagnosis> group = result.diagnoses().stream()
                    .filter(d -> sev.equals(d.severity())).toList();
            if (group.isEmpty()) continue;

            DeviceRgb color = severityColor(sev);
            doc.add(new Paragraph(sev + "  (" + group.size() + " findings)")
                    .setFont(bold).setFontSize(10).setFontColor(color).setMarginTop(8));

            Table t = new Table(UnitValue.createPercentArray(new float[]{20, 20, 60})).useAllAvailableWidth();
            t.addHeaderCell(headerCell("Rule ID",         color, bold));
            t.addHeaderCell(headerCell("Affected Method", color, bold));
            t.addHeaderCell(headerCell("Message",         color, bold));

            boolean alt = false;
            for (Diagnosis d : group) {
                DeviceRgb bg = alt ? COL_LIGHT : COL_WHITE;
                alt = !alt;
                addDataCell(t, d.ruleId() != null         ? d.ruleId()         : "", bg, normal, 8);
                addDataCell(t, d.affectedMethod() != null ? shortSig(d.affectedMethod()) : "", bg, normal, 8);
                addDataCell(t, d.message(),                                             bg, normal, 9);
            }
            doc.add(t);
        }

        doc.add(new Paragraph("\n"));
    }

    // ── GC Analysis ───────────────────────────────────────────────────────────

    private void renderGcSection(Document doc, GcLogResult gc, PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "GC Analysis", COL_GC, bold);

        Table meta = new Table(UnitValue.createPercentArray(new float[]{40, 60})).useAllAvailableWidth();
        addMetaRow(meta, "Algorithm:",             gc.gcAlgorithm(),                                    bold, normal);
        addMetaRow(meta, "GC time %:",             String.format("%.1f%%", gc.gcTimePercent()),         bold, normal);
        addMetaRow(meta, "Max pause:",             String.format("%.0f ms", gc.maxPauseMs()),           bold, normal);
        addMetaRow(meta, "Avg pause:",             String.format("%.0f ms", gc.avgPauseMs()),           bold, normal);
        addMetaRow(meta, "P99 pause:",             String.format("%.0f ms", gc.p99PauseMs()),           bold, normal);
        addMetaRow(meta, "Promotion failure:",     gc.promotionFailureDetected()    ? "YES ⚠" : "No",  bold, normal);
        addMetaRow(meta, "Concurrent mode failure:", gc.concurrentModeFailureDetected() ? "YES ⚠" : "No", bold, normal);
        doc.add(meta);

        if (gc.gcTimePercent() >= 10) {
            doc.add(new Paragraph(String.format(
                    "\n⚠ GC THRASHING — application spent %.1f%% of elapsed time in GC pauses (threshold: 10%%).",
                    gc.gcTimePercent()))
                    .setFont(bold).setFontSize(10).setFontColor(SEV_CRITICAL));
        }

        // GC event table (first 20)
        if (gc.events() != null && !gc.events().isEmpty()) {
            doc.add(new Paragraph("\nGC Events (first 20):").setFont(bold).setFontSize(10).setFontColor(COL_TEXT));

            Table evTable = new Table(UnitValue.createPercentArray(new float[]{25, 15, 15, 22, 23}))
                    .useAllAvailableWidth();
            evTable.addHeaderCell(headerCell("Timestamp",  COL_GC, bold));
            evTable.addHeaderCell(headerCell("Type",       COL_GC, bold));
            evTable.addHeaderCell(headerCell("Pause (ms)", COL_GC, bold));
            evTable.addHeaderCell(headerCell("Heap before", COL_GC, bold));
            evTable.addHeaderCell(headerCell("Heap after",  COL_GC, bold));

            boolean alt = false;
            for (var ev : gc.events().stream().limit(20).toList()) {
                DeviceRgb bg = alt ? COL_LIGHT : COL_WHITE;
                alt = !alt;
                addDataCell(evTable, String.format("%.3f s", ev.timestamp()), bg, normal, 8);
                addDataCell(evTable, ev.type() != null ? ev.type() : "",            bg, normal, 9);
                addDataCell(evTable, String.format("%.0f", ev.pauseMs()),           bg, normal, 9);
                addDataCell(evTable, formatBytes(ev.heapBeforeBytes()),             bg, normal, 8);
                addDataCell(evTable, formatBytes(ev.heapAfterBytes()),              bg, normal, 8);
            }
            doc.add(evTable);
        }

        doc.add(new Paragraph("\n"));
    }

    // ── Timeline ───────────────────────────────────────────────────────────────

    private void renderTimelineSection(Document doc, TimelineDto timeline, PdfFont bold, PdfFont normal) {
        addSectionHeader(doc, "Timeline Analysis", COL_TIMELINE, bold);

        List<TimelineDto.SnapshotSummary> snaps = timeline.snapshots();
        doc.add(new Paragraph(snaps.size() + " snapshots analyzed.")
                .setFont(normal).setFontSize(10).setFontColor(COL_TEXT));

        if (timeline.confirmedDeadlock()) {
            doc.add(new Paragraph("⛔ CONFIRMED DEADLOCK — a thread remains blocked across all snapshots.")
                    .setFont(bold).setFontSize(11).setFontColor(SEV_CRITICAL));
        }

        Table t = new Table(UnitValue.createPercentArray(new float[]{12, 15, 18, 18, 18, 19}))
                .useAllAvailableWidth();
        t.addHeaderCell(headerCell("Snapshot",       COL_TIMELINE, bold));
        t.addHeaderCell(headerCell("Timestamp",      COL_TIMELINE, bold));
        t.addHeaderCell(headerCell("Runnable%",      COL_TIMELINE, bold));
        t.addHeaderCell(headerCell("Blocked%",       COL_TIMELINE, bold));
        t.addHeaderCell(headerCell("Waiting%",       COL_TIMELINE, bold));
        t.addHeaderCell(headerCell("TimedWaiting%",  COL_TIMELINE, bold));

        boolean alt = false;
        for (var s : snaps) {
            DeviceRgb bg = alt ? COL_LIGHT : COL_WHITE;
            alt = !alt;
            addDataCell(t, "t" + s.index(),                            bg, normal, 9);
            addDataCell(t, s.timestamp() != null ? s.timestamp() : "", bg, normal, 8);
            addDataCell(t, fmt1(s.runnablePercent()),                  bg, normal, 9);
            addDataCell(t, fmt1(s.blockedPercent()),                   bg, bold,   9, s.blockedPercent() > 20 ? SEV_CRITICAL : COL_TEXT);
            addDataCell(t, fmt1(s.waitingPercent()),                   bg, normal, 9);
            addDataCell(t, fmt1(s.timedWaitingPercent()),              bg, normal, 9);
        }
        doc.add(t);

        // Hotspot trends
        if (timeline.hotspotTrends() != null && !timeline.hotspotTrends().isEmpty()) {
            doc.add(new Paragraph("\nHotspot trends across snapshots:").setFont(bold).setFontSize(10).setFontColor(COL_TEXT));
            for (var trend : timeline.hotspotTrends().stream().limit(5).toList()) {
                String values = trend.selfTimePercents().stream()
                        .map(v -> String.format("%.1f%%", v))
                        .reduce((a, b) -> a + " → " + b).orElse("");
                doc.add(new Paragraph("  " + shortSig(trend.methodSignature()) + ": " + values)
                        .setFont(normal).setFontSize(9).setFontColor(COL_TEXT));
            }
        }

        doc.add(new Paragraph("\n"));
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    private void addSectionHeader(Document doc, String title, DeviceRgb color, PdfFont bold) {
        Table header = new Table(UnitValue.createPercentArray(new float[]{1})).useAllAvailableWidth();
        header.addCell(new Cell()
                .setBackgroundColor(color)
                .setPaddingTop(6).setPaddingBottom(6).setPaddingLeft(10)
                .add(new Paragraph(title).setFont(bold).setFontSize(13).setFontColor(COL_WHITE))
                .setBorder(null));
        doc.add(header);
        doc.add(new Paragraph("").setMarginBottom(4));
    }

    private Cell headerCell(String text, DeviceRgb color, PdfFont bold) {
        return new Cell()
                .setBackgroundColor(color)
                .setPadding(4)
                .add(new Paragraph(text).setFont(bold).setFontSize(9).setFontColor(COL_WHITE))
                .setBorder(null);
    }

    private void addDataCell(Table t, String text, DeviceRgb bg, PdfFont font, int size) {
        addDataCell(t, text, bg, font, size, COL_TEXT);
    }

    private void addDataCell(Table t, String text, DeviceRgb bg, PdfFont font, int size, DeviceRgb textColor) {
        t.addCell(new Cell()
                .setBackgroundColor(bg)
                .setPadding(3)
                .add(new Paragraph(text != null ? text : "").setFont(font).setFontSize(size).setFontColor(textColor))
                .setBorder(null));
    }

    private DeviceRgb severityColor(String severity) {
        if (severity == null) return COL_TEXT;
        return switch (severity) {
            case "CRITICAL" -> SEV_CRITICAL;
            case "WARNING"  -> SEV_WARNING;
            default         -> SEV_INFO;
        };
    }

    private boolean hasThreadData(AnalysisResultDto result) {
        return result.totalThreads() != null && result.totalThreads() > 0
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

    private String fmt1(double v) {
        return String.format("%.1f%%", v);
    }
}
