package com.sakec.smartspace.util;

import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes CSV files that open cleanly in Excel and cannot trigger spreadsheet formula injection. */
public final class CsvExporter {
    private CsvExporter() { }

    public static void write(Path file, List<String> header, List<List<String>> rows) {
        try (Writer w = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            w.write('\uFEFF'); // BOM so Excel reads UTF-8
            w.write(line(header));
            for (List<String> r : rows) w.write(line(r));
        } catch (IOException e) {
            Log.error("CSV export failed", e);
            throw new AppException("Could not write the file. Check the location and try again.");
        }
    }

    private static String line(List<String> cells) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < cells.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append(escape(cells.get(i)));
        }
        return sb.append("\r\n").toString();
    }

    static String escape(String v) {
        String s = v == null ? "" : v;
        if (!s.isEmpty() && "=+-@\t\r".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        if (s.contains(",") || s.contains("\"") || s.contains("\n") || s.contains("\r")) {
            s = "\"" + s.replace("\"", "\"\"") + "\"";
        }
        return s;
    }
}
