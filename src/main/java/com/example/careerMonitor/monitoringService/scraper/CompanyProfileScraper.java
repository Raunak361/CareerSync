package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Runs one {@link CompanyProfile}: no guessing, no detection - it asks exactly the source the profile names,
 * then applies the company's own filters. Errors are reported (ScrapeResult.failed) rather than turned into "0 jobs".
 */
@Component
public class CompanyProfileScraper {

    private static final Set<String> ATS = new HashSet<>(Arrays.asList(
            "greenhouse", "lever", "ashby", "smartrecruiters", "workable", "recruitee", "bamboohr", "workday"));
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([^}]+)\\}");

    private final PageFetcher fetcher;
    private final ObjectMapper mapper;
    private final AtsApiStrategy ats;
    private final JobFilter filter;

    public CompanyProfileScraper(PageFetcher fetcher, ObjectMapper mapper, AtsApiStrategy ats, JobFilter filter) {
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.ats = ats;
        this.filter = filter;
    }

    public ScrapeResult scrape(CompanyProfile p, String companyUrl) {
        List<String> trace = new ArrayList<>();
        String type = p.type.toLowerCase();
        String pageUrl = companyUrl == null ? "" : companyUrl;
        try {
            List<ScrapedJob> raw;
            if (ATS.contains(type)) {
                raw = ats.fetch(type, p.token, new ScrapeContext(pageUrl, null, fetcher, mapper));
            } else if (type.equals("json-api")) {
                raw = fromApi(p.api);
            } else if (type.equals("html")) {
                raw = fromHtml(p.html, pageUrl);
            } else {
                throw new IllegalArgumentException("Unknown profile type " + p.type);
            }
            trace.add("profile " + p + ": source returned " + raw.size());

            List<ScrapedJob> out = applyFilters(raw, p, pageUrl);
            trace.add("profile filters + noise filter kept " + out.size());
            return ScrapeResult.ok("profile:" + type, out, trace);
        } catch (Exception e) {
            trace.add("profile " + p + " FAILED: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return ScrapeResult.failed(trace);
        }
    }

    // ------------------------------------------------------------------ filters

    private List<ScrapedJob> applyFilters(List<ScrapedJob> raw, CompanyProfile p, String pageUrl) {
        CompanyProfile.Filters f = p.filters == null ? new CompanyProfile.Filters() : p.filters;
        Pattern incT = re(f.includeTitleRegex), excT = re(f.excludeTitleRegex);
        Pattern incU = re(f.includeUrlRegex), excU = re(f.excludeUrlRegex), loc = re(f.locationRegex);

        Map<String, ScrapedJob> out = new LinkedHashMap<>();
        for (ScrapedJob j : raw) {
            ScrapedJob c = filter.clean(j, pageUrl);
            if (c == null) continue;
            if (incT != null && !incT.matcher(c.getTitle()).find()) continue;
            if (excT != null && excT.matcher(c.getTitle()).find()) continue;
            if (incU != null && !incU.matcher(c.getUrl()).find()) continue;
            if (excU != null && excU.matcher(c.getUrl()).find()) continue;
            if (loc != null && c.getLocation() != null && !loc.matcher(c.getLocation()).find()) continue;
            out.putIfAbsent(c.getUrl(), c);
            if (f.maxJobs > 0 && out.size() >= f.maxJobs) break;
        }
        return new ArrayList<>(out.values());
    }

    private static Pattern re(String s) { return s == null || s.isEmpty() ? null : Pattern.compile(s); }

    // ------------------------------------------------------------------ json-api

    private List<ScrapedJob> fromApi(CompanyProfile.Api a) throws Exception {
        int size = a.pageSize > 0 ? a.pageSize : 50;
        int maxPages = a.maxPages > 0 ? a.maxPages : 10;
        boolean paged = hasPaging(a.url) || hasPaging(a.body);

        Map<String, ScrapedJob> out = new LinkedHashMap<>();
        for (int i = 0; i < (paged ? maxPages : 1); i++) {
            if (i > 0 && a.delayMs > 0) Thread.sleep(a.delayMs);
            String url = fill(a.url, i, size, a.pageStart);
            String body = a.body == null ? null : fill(a.body, i, size, a.pageStart);
            if ("POST".equalsIgnoreCase(a.method) && body == null) body = "{}";

            FetchedPage page = fetcher.request(url, "POST".equalsIgnoreCase(a.method) ? body : null, a.headers);
            JsonNode root = mapper.readTree(page.body);
            JsonNode items = isEmpty(a.itemsPath) ? root : root.at("/" + a.itemsPath.replace('.', '/'));
            if (!items.isArray()) {
                if (i == 0) throw new IllegalStateException("itemsPath '" + a.itemsPath + "' is not an array in the response");
                break;
            }

            int added = 0;
            for (JsonNode item : items) {
                String title = text(item, a.titleField);
                String link = a.urlTemplate != null ? template(a.urlTemplate, item) : text(item, a.urlField);
                String abs = UrlUtils.resolve(url, link);
                if (title == null || abs == null) continue;
                String canon = UrlUtils.canonicalize(abs);
                if (canon != null && !out.containsKey(canon)) {
                    StringBuilder desc = new StringBuilder();
                    if (a.descriptionFields != null) {
                        for (String f : a.descriptionFields) {
                            String v = text(item, f);
                            if (v != null) desc.append(TextUtils.nz(TextUtils.toPlain(v))).append(' ');
                        }
                    }
                    ScrapedJob sj = new ScrapedJob(title, canon, text(item, a.locationField))
                            .withHints(text(item, a.typeField), text(item, a.experienceField),
                                       desc.length() == 0 ? null : desc.toString());
                    if (a.detailUrlTemplate != null && a.detailKind != null) {
                        String du = template(a.detailUrlTemplate, item);
                        if (du != null) sj = sj.withDetail(du, a.detailKind);
                    }
                    out.put(canon, sj);
                    added++;
                }
            }
            if (added == 0) break; // empty or repeated page: we are past the end
        }
        return new ArrayList<>(out.values());
    }

    private static boolean hasPaging(String s) {
        return s != null && (s.contains("{offset}") || s.contains("{page}"));
    }

    private static String fill(String t, int pageIndex, int size, int pageStart) {
        return t.replace("{offset}", String.valueOf(pageIndex * size))
                .replace("{page}", String.valueOf(pageStart + pageIndex))
                .replace("{limit}", String.valueOf(size));
    }

    /** "https://x.com{job_path}" / "https://x.com/jobs/{id}" -> values from the item; null if a field is missing. */
    private static String template(String tpl, JsonNode item) {
        Matcher m = PLACEHOLDER.matcher(tpl);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String v = text(item, m.group(1));
            if (v == null) return null;
            m.appendReplacement(sb, Matcher.quoteReplacement(v));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static String text(JsonNode n, String dottedPath) {
        if (n == null || isEmpty(dottedPath)) return null;
        JsonNode v = n.at("/" + dottedPath.replace('.', '/'));
        if (v.isMissingNode() || v.isNull() || v.isContainerNode()) return null;
        String s = v.asText().trim();
        return s.isEmpty() ? null : s;
    }

    private static boolean isEmpty(String s) { return s == null || s.trim().isEmpty(); }

    // ------------------------------------------------------------------ html selectors

    private List<ScrapedJob> fromHtml(CompanyProfile.Html h, String companyUrl) throws Exception {
        String start = isEmpty(h.url) ? companyUrl : h.url;
        if (isEmpty(start)) throw new IllegalStateException("html profile has no url and the company has none either");
        int maxPages = h.maxPages > 0 ? h.maxPages : 5;

        Map<String, ScrapedJob> out = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        String nextUrl = start;

        for (int i = 0; i < maxPages; i++) {
            if (i > 0 && h.delayMs > 0) Thread.sleep(h.delayMs);
            String pageUrl = !isEmpty(h.pageParam)
                    ? withParam(start, h.pageParam, h.pageStart + i * Math.max(1, h.pageStep))
                    : nextUrl;
            if (!visited.add(pageUrl)) break;

            FetchedPage page = fetcher.get(pageUrl);
            Document doc = Jsoup.parse(page.body, page.finalUrl);
            int added = 0;
            for (Element item : doc.select(h.itemSelector)) {
                Element link = !isEmpty(h.linkSelector)
                        ? (item.is(h.linkSelector) ? item : item.selectFirst(h.linkSelector))
                        : (item.is("a[href]") ? item : item.selectFirst("a[href]"));
                if (link == null) continue;
                String canon = UrlUtils.canonicalize(link.absUrl("href"));
                if (canon == null) continue;

                Element t = isEmpty(h.titleSelector) ? null : item.selectFirst(h.titleSelector);
                String fromAttr = isEmpty(h.titleAttribute) ? "" : link.attr(h.titleAttribute);
                String raw = t != null ? t.text() : (!fromAttr.trim().isEmpty() ? fromAttr : link.text());
                String title = raw.replaceAll("\\s+", " ").trim();
                Element l = isEmpty(h.locationSelector) ? null : item.selectFirst(h.locationSelector);

                if (title.isEmpty() || out.containsKey(canon)) continue;
                out.put(canon, new ScrapedJob(title, canon, l == null ? null : l.text().trim()));
                added++;
            }
            if (added == 0) {
                if (i == 0 && doc.select(h.itemSelector).isEmpty())
                    throw new IllegalStateException("itemSelector '" + h.itemSelector + "' matched nothing on " + pageUrl
                            + " (site changed, or the page needs JavaScript)");
                break;
            }
            if (isEmpty(h.pageParam)) {
                Element n = isEmpty(h.nextSelector) ? null : doc.selectFirst(h.nextSelector);
                String nu = n == null ? null : n.absUrl("href");
                if (isEmpty(nu)) break;
                nextUrl = nu;
            }
        }
        return new ArrayList<>(out.values());
    }

    static String withParam(String url, String param, int value) {
        String base = url.replaceAll("([?&])" + Pattern.quote(param) + "=[^&#]*&?", "$1");
        base = base.replaceAll("[?&]$", "");
        return base + (base.contains("?") ? "&" : "?") + param + "=" + value;
    }
}
