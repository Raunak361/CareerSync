package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Listing pages rarely say how much experience a job needs; the detail page does. This fetches the detail of
 * NEW jobs only (the monitor calls it just before saving), reads the description, and fills in
 * "experience". Capped per company per run so the first run on a huge board stays polite and fast.
 *
 *   career.monitor.enrich.max-per-company=60   (0 disables enrichment)
 *   career.monitor.enrich.delay-ms=250
 * Jobs beyond the cap are saved with whatever the title / listing already revealed (experience may be null).
 */
@Component
public class JobEnricher {

    private static final Logger log = LoggerFactory.getLogger(JobEnricher.class);

    private final PageFetcher fetcher;
    private final ObjectMapper mapper;
    private final int maxPerCompany;
    private final long delayMs;

    public JobEnricher(PageFetcher fetcher, ObjectMapper mapper,
                       @Value("${career.monitor.enrich.max-per-company:60}") int maxPerCompany,
                       @Value("${career.monitor.enrich.delay-ms:250}") long delayMs) {
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.maxPerCompany = maxPerCompany;
        this.delayMs = delayMs;
    }

    public List<ScrapedJob> enrich(List<ScrapedJob> jobs) {
        List<ScrapedJob> out = new ArrayList<>(jobs.size());
        int budget = maxPerCompany;
        for (ScrapedJob j : jobs) {
            boolean needs = j.getExperience() == null && !JobClassifier.TYPE_INTERNSHIP.equals(j.getType());
            if (!needs || budget <= 0) { out.add(j); continue; }
            budget--;
            try {
                String text = detailText(j);
                out.add(text == null ? j : JobClassifier.complete(j.withDescription(text)));
                Thread.sleep(delayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                out.add(j);
                budget = 0;
            } catch (Exception e) {
                log.debug("Enrichment failed for {}: {}", j.getUrl(), e.getMessage());
                out.add(j);
            }
        }
        return out;
    }

    private String detailText(ScrapedJob j) throws Exception {
        String kind = j.getDetailKind();
        if (kind != null && j.getDetailUrl() != null) {
            JsonNode root = mapper.readTree(fetcher.getJson(j.getDetailUrl()).body);
            switch (kind) {
                case "greenhouse":
                    return TextUtils.toPlain(root.path("content").asText(null));
                case "workday":
                    return TextUtils.toPlain(root.path("jobPostingInfo").path("jobDescription").asText(null));
                case "smartrecruiters": {
                    StringBuilder sb = new StringBuilder();
                    JsonNode sections = root.path("jobAd").path("sections");
                    for (String k : new String[]{"qualifications", "jobDescription", "additionalInformation"}) {
                        sb.append(TextUtils.nz(TextUtils.toPlain(sections.path(k).path("text").asText(null)))).append(' ');
                    }
                    return sb.toString().trim().isEmpty() ? null : sb.toString();
                }
                case "oracle": { // Oracle Recruiting Cloud (JPMorgan Chase): items[0].External*Str / ShortDescriptionStr
                    JsonNode it = root.path("items").path(0);
                    StringBuilder sb = new StringBuilder();
                    java.util.Iterator<java.util.Map.Entry<String, JsonNode>> fields = it.fields();
                    while (fields.hasNext()) {
                        java.util.Map.Entry<String, JsonNode> f = fields.next();
                        String n = f.getKey();
                        if (f.getValue().isTextual() && (n.startsWith("External") && n.endsWith("Str") || n.equals("ShortDescriptionStr")))
                            sb.append(TextUtils.nz(TextUtils.toPlain(f.getValue().asText()))).append(' ');
                    }
                    return sb.toString().trim().isEmpty() ? null : sb.toString();
                }
                default:
                    break;
            }
        }
        return fromPage(j.getUrl());
    }

    /** Generic: schema.org JobPosting on the job page if present, else the visible page text. */
    private String fromPage(String url) throws Exception {
        FetchedPage page = fetcher.get(url);
        Document doc = Jsoup.parse(page.body, page.finalUrl);
        for (Element s : doc.select("script[type=application/ld+json]")) {
            try {
                JsonNode jp = JsonLdStrategy.firstJobPosting(mapper.readTree(s.data()), 0);
                if (jp != null) {
                    String t = JsonLdStrategy.detailText(jp);
                    if (!t.trim().isEmpty()) return t;
                }
            } catch (Exception ignored) { }
        }
        doc.select("nav, footer, header, script, style, noscript").remove();
        String body = TextUtils.toPlain(doc.body() == null ? null : doc.body().text());
        return body == null ? null : (body.length() > 8000 ? body.substring(0, 8000) : body);
    }
}
