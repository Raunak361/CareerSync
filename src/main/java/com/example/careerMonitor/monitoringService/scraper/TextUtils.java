package com.example.careerMonitor.monitoringService.scraper;

import org.jsoup.Jsoup;
import org.jsoup.parser.Parser;

public final class TextUtils {
    private TextUtils() {}

    /** HTML (possibly entity-escaped, as Greenhouse returns it) -> single-line plain text, capped. */
    public static String toPlain(String s) {
        if (s == null || s.trim().isEmpty()) return null;
        String t = s;
        if (t.contains("&lt;")) t = Parser.unescapeEntities(t, false);
        if (t.indexOf('<') >= 0) t = Jsoup.parse(t).text();
        t = t.replaceAll("\\s+", " ").trim();
        if (t.length() > 20000) t = t.substring(0, 20000);
        return t.isEmpty() ? null : t;
    }

    public static String nz(String s) { return s == null ? "" : s; }
}
