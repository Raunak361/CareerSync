package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import org.jsoup.nodes.Element;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Many JS-framework career pages (Next.js, Nuxt, custom SPAs) ship the job list as JSON in the initial HTML
 * (__NEXT_DATA__, window.__INITIAL_STATE__, application/json script tags) even though the visible markup is
 * rendered client-side. We search that JSON for arrays of "job-looking" objects (a title plus a link).
 * {@link #findJobs} is public so the headless strategy can reuse it on captured XHR/fetch responses.
 */
@Component
@Order(3)
public class EmbeddedJsonStrategy implements JobExtractionStrategy {

    private static final String[] TITLE_KEYS = {"title", "jobTitle", "job_title", "positionTitle", "position", "name", "text"};
    private static final String[] URL_KEYS = {"absolute_url", "url", "jobUrl", "job_url", "applyUrl", "apply_url", "hostedUrl",
            "canonicalUrl", "detailUrl", "detail_url", "link", "href", "path", "externalPath"};
    private static final Pattern JOBISH = Pattern.compile("job|career|position|opening|vacanc|posting|role|requisition|opportunit", Pattern.CASE_INSENSITIVE);
    private static final Pattern GLOBAL_ASSIGN = Pattern.compile(
            "(?:window\\.)?(?:__[A-Za-z_]+__|INITIAL_STATE|initialState|__data|pageData)\\s*=\\s*([\\[{])");
    private static final int MAX_SCRIPT_CHARS = 5_000_000;

    @Override public String name() { return "embedded-json"; }

    @Override
    public List<ScrapedJob> extract(ScrapeContext ctx) {
        if (ctx.document == null) return Collections.emptyList();
        List<ScrapedJob> best = Collections.emptyList();

        for (Element s : ctx.document.select("script")) {
            String data = s.data();
            if (data == null || data.length() < 50 || data.length() > MAX_SCRIPT_CHARS) continue;
            String type = s.attr("type").toLowerCase();
            if (type.contains("ld+json")) continue; // JsonLdStrategy's job

            List<String> blobs = new ArrayList<>();
            if (type.contains("json") || "__NEXT_DATA__".equals(s.id())) {
                blobs.add(data);
            } else {
                Matcher m = GLOBAL_ASSIGN.matcher(data);
                while (m.find()) {
                    String blob = balanced(data, m.end() - 1);
                    if (blob != null) blobs.add(blob);
                }
            }
            for (String blob : blobs) {
                try {
                    List<ScrapedJob> found = findJobs(ctx.mapper.readTree(blob), ctx.baseUrl());
                    if (found.size() > best.size()) best = found;
                } catch (Exception ignored) { }
            }
        }
        return best;
    }

    /** Finds the largest array of job-like objects anywhere in the JSON tree. */
    public static List<ScrapedJob> findJobs(JsonNode root, String baseUrl) {
        List<List<ScrapedJob>> candidates = new ArrayList<>();
        walk(root, "", baseUrl, candidates, 0);
        List<ScrapedJob> best = Collections.emptyList();
        for (List<ScrapedJob> c : candidates) if (c.size() > best.size()) best = c;
        return best;
    }

    private static void walk(JsonNode n, String key, String base, List<List<ScrapedJob>> out, int depth) {
        if (n == null || depth > 12) return;
        if (n.isArray()) {
            if (n.size() >= 2) {
                List<ScrapedJob> jobs = new ArrayList<>();
                for (JsonNode e : n) {
                    if (!e.isObject()) continue;
                    ScrapedJob j = toJob(e, base);
                    if (j != null) jobs.add(j);
                }
                if (jobs.size() >= 2 && jobs.size() * 2 >= n.size() && plausible(jobs, key)) out.add(jobs);
            }
            for (JsonNode e : n) walk(e, key, base, out, depth + 1);
        } else if (n.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> it = n.fields();
            while (it.hasNext()) {
                Map.Entry<String, JsonNode> f = it.next();
                walk(f.getValue(), f.getKey(), base, out, depth + 1);
            }
        }
    }

    /** Reject nav menus / footers: either the key looks job-related or most URLs do. */
    private static boolean plausible(List<ScrapedJob> jobs, String key) {
        if (JOBISH.matcher(key).find()) return true;
        int hits = 0;
        for (ScrapedJob j : jobs) if (JOBISH.matcher(j.getUrl()).find()) hits++;
        return hits * 2 >= jobs.size();
    }

    private static ScrapedJob toJob(JsonNode o, String base) {
        String title = null, url = null;
        for (String k : TITLE_KEYS) {
            JsonNode v = o.get(k);
            if (v != null && v.isTextual()) { title = v.asText().trim(); break; }
        }
        for (String k : URL_KEYS) {
            JsonNode v = o.get(k);
            if (v != null && v.isTextual() && (v.asText().startsWith("http") || v.asText().startsWith("/"))) { url = v.asText(); break; }
        }
        if (title == null || title.length() < 3 || title.length() > 200 || url == null) return null;
        String abs = UrlUtils.canonicalize(UrlUtils.resolve(base, url));
        if (abs == null) return null;
        String loc = null;
        for (String k : new String[]{"location", "locationName", "city", "locationsText"}) {
            JsonNode v = o.get(k);
            if (v != null && v.isTextual()) { loc = v.asText(); break; }
            if (v != null && v.isObject() && v.hasNonNull("name")) { loc = v.get("name").asText(); break; }
        }
        return new ScrapedJob(title, abs, loc);
    }

    /** Returns the balanced {...} or [...] substring starting at {@code start}, honouring string literals. */
    static String balanced(String s, int start) {
        char open = s.charAt(start);
        char close = open == '{' ? '}' : ']';
        int depth = 0;
        boolean inStr = false, esc = false;
        for (int i = start; i < s.length(); i++) {
            char c = s.charAt(i);
            if (inStr) {
                if (esc) esc = false;
                else if (c == '\\') esc = true;
                else if (c == '"') inStr = false;
                continue;
            }
            if (c == '"') inStr = true;
            else if (c == open) depth++;
            else if (c == close && --depth == 0) return s.substring(start, i + 1);
        }
        return null;
    }
}
