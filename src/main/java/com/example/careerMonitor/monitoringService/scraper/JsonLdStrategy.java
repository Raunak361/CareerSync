package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * schema.org JobPosting markup (required by Google for Jobs, so many sites ship it).
 * Handles plain objects, arrays, @graph and ItemList wrappers.
 */
@Component
@Order(2)
public class JsonLdStrategy implements JobExtractionStrategy {

    @Override public String name() { return "json-ld"; }

    @Override
    public List<ScrapedJob> extract(ScrapeContext ctx) {
        if (ctx.document == null) return Collections.emptyList();
        Map<String, ScrapedJob> out = new LinkedHashMap<>();
        for (Element s : ctx.document.select("script[type=application/ld+json]")) {
            String raw = s.data();
            if (raw == null || raw.trim().isEmpty()) continue;
            try {
                ctx.mapper.getFactory().enable(JsonParser.Feature.ALLOW_UNQUOTED_CONTROL_CHARS);
                walk(ctx.mapper.readTree(raw), ctx.baseUrl(), out, 0);
            } catch (Exception ignored) {
                // malformed JSON-LD is common; skip that block
            }
        }
        return new ArrayList<>(out.values());
    }

    private void walk(JsonNode n, String base, Map<String, ScrapedJob> out, int depth) {
        if (n == null || depth > 8) return;
        if (n.isArray()) { for (JsonNode c : n) walk(c, base, out, depth + 1); return; }
        if (!n.isObject()) return;

        if (isJobPosting(n)) {
            String title = firstText(n, "title", "name");
            String url = UrlUtils.resolve(base, firstText(n, "url", "@id", "sameAs"));
            String canon = UrlUtils.canonicalize(url);
            if (title != null && canon != null) {
                out.putIfAbsent(canon, new ScrapedJob(title.trim(), canon, location(n))
                        .withHints(employmentType(n), experienceHint(n), TextUtils.toPlain(firstText(n, "description"))));
            }
            return;
        }
        for (String key : new String[]{"@graph", "itemListElement", "item", "mainEntity", "hasPart"}) {
            if (n.has(key)) walk(n.get(key), base, out, depth + 1);
        }
    }

    /** schema.org employmentType: "INTERN", "FULL_TIME", or an array of those. */
    private static String employmentType(JsonNode n) {
        JsonNode t = n.get("employmentType");
        if (t == null) return null;
        if (t.isArray()) {
            StringBuilder sb = new StringBuilder();
            for (JsonNode x : t) sb.append(x.asText()).append(' ');
            return sb.toString().trim();
        }
        return t.asText(null);
    }

    /** experienceRequirements: free text, or OccupationalExperienceRequirements{monthsOfExperience}. */
    static String experienceHint(JsonNode n) {
        JsonNode e = n.get("experienceRequirements");
        if (e == null || e.isNull()) return null;
        if (e.isTextual()) return e.asText();
        if (e.isObject()) {
            if (e.has("monthsOfExperience")) {
                int months = e.path("monthsOfExperience").asInt(-1);
                if (months == 0) return "fresher";
                if (months > 0) return months % 12 == 0 ? (months / 12 == 1 ? "1 year" : (months / 12) + " years") : months + " months";
            }
            return e.path("description").asText(null);
        }
        return null;
    }

    /** First JobPosting node anywhere in a JSON-LD tree (used when reading a single job's detail page). */
    public static JsonNode firstJobPosting(JsonNode n, int depth) {
        if (n == null || depth > 8) return null;
        if (n.isArray()) { for (JsonNode c : n) { JsonNode f = firstJobPosting(c, depth + 1); if (f != null) return f; } return null; }
        if (!n.isObject()) return null;
        if (isJobPosting(n)) return n;
        for (String key : new String[]{"@graph", "mainEntity", "itemListElement", "item"}) {
            if (n.has(key)) { JsonNode f = firstJobPosting(n.get(key), depth + 1); if (f != null) return f; }
        }
        return null;
    }

    /** Text worth scanning for experience: explicit requirement first, then the description. */
    public static String detailText(JsonNode jp) {
        String exp = experienceHint(jp);
        String desc = TextUtils.toPlain(firstText(jp, "description"));
        return (exp == null ? "" : "Experience required: " + exp + ". ") + TextUtils.nz(desc);
    }

    private static boolean isJobPosting(JsonNode n) {
        JsonNode t = n.get("@type");
        if (t == null) return false;
        if (t.isArray()) { for (JsonNode x : t) if ("JobPosting".equalsIgnoreCase(x.asText())) return true; return false; }
        return "JobPosting".equalsIgnoreCase(t.asText());
    }

    private static String location(JsonNode n) {
        JsonNode loc = n.get("jobLocation");
        if (loc == null) return null;
        if (loc.isArray() && loc.size() > 0) loc = loc.get(0);
        JsonNode a = loc.path("address");
        String city = a.path("addressLocality").asText("");
        String region = a.path("addressRegion").asText("");
        String s = (city + " " + region).trim();
        return s.isEmpty() ? null : s;
    }

    private static String firstText(JsonNode n, String... fields) {
        for (String f : fields) {
            JsonNode v = n.get(f);
            if (v != null && v.isTextual() && !v.asText().trim().isEmpty()) return v.asText();
        }
        return null;
    }
}
