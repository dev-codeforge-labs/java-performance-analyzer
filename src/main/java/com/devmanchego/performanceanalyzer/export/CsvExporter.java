package com.devmanchego.performanceanalyzer.export;

import com.devmanchego.performanceanalyzer.model.HotspotDto;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.util.List;

@Component
public class CsvExporter {

    public byte[] exportHotspots(List<HotspotDto> hotspots) {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (PrintWriter pw = new PrintWriter(baos)) {
            pw.println("rank,methodSignature,className,packagePrefix,layer,selfTimePercent,totalTimePercent,diagnosis,severity");
            for (HotspotDto h : hotspots) {
                pw.printf("%d,%s,%s,%s,%s,%.2f,%.2f,%s,%s%n",
                        h.rank(),
                        escape(h.methodSignature()),
                        escape(h.className()),
                        escape(h.packagePrefix()),
                        escape(h.layerCategory()),
                        h.selfTimePercent(),
                        h.totalTimePercent(),
                        escape(h.diagnosis()),
                        escape(h.severity()));
            }
        }
        return baos.toByteArray();
    }

    private String escape(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }
}
