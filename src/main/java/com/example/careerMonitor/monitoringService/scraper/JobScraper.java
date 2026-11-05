package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/**
 * Orchestrates the extraction strategies for one career-page URL.
 *
 * Strategies run in {@code @Order} (ATS API -> JSON-LD -> embedded JSON -> HTML heuristic -> optional headless browser).
 * The first one that returns a confident result wins; weak results are kept as a fallback.
 * If nothing is found on a landing page, we follow one "View all jobs / Open positions" link and retry.
 */
@Service
public class JobScraper {

    private static final Logger log = LoggerFactory.getLogger(JobScraper.class);
    private static final int MAX_JOBS = 2000;
    private static final Pattern ALL_JOBS_LINK = Pattern.compile(
            "(view|see|browse|explore|search|find)\\s+(all\\s+)?(our\\s+)?(open\\s+)?(jobs|positions|roles|openings|vacancies)"
                    + "|open\\s+(positions|roles|jobs|vacancies)|current\\s+(openings|vacancies|opportunities)|all\\s+(jobs|openings|positions)",
            Pattern.CASE_INSENSITIVE);

    private final PageFetcher fetcher;
    private final ObjectMapper mapper;
    private final List<JobExtractionStrategy> strategies;
    private final JobFilter filter;
    private final CompanyProfileRegistry profiles;
    private final CompanyProfileScraper profileScraper;

    public JobScraper(PageFetcher fetcher, ObjectMapper mapper, List<JobExtractionStrategy> strategies, JobFilter filter,
                      CompanyProfileRegistry profiles, CompanyProfileScraper profileScraper) {
        this.filter = filter;
        this.profiles = profiles;
        this.profileScraper = profileScraper;
        this.fetcher = fetcher;
        this.mapper = mapper;
        this.strategies = strategies; // Spring injects in @Order
    }

    /**
     * Company-aware entry point: uses the company's own profile if one exists, otherwise the generic pipeline.
     * A profile that fails or finds nothing only falls back to auto-detection when the profile says fallbackToAuto.
     */
    public ScrapeResult scrape(String companyName, String url) {
        CompanyProfile profile = profiles.find(companyName, url);
        if (profile == null) {
            if (url == null || url.trim().isEmpty()) {
                List<String> t = new ArrayList<>();
                t.add("no URL given and no enabled profile matches company '" + companyName + "' "
                        + "(loaded profiles: " + profiles.all() + "). Check the name, that company-scrapers.json is at "
                        + "src/main/resources, or pass &url=");
                return ScrapeResult.failed(t);
            }
            return scrape(url);
        }

        ScrapeResult r = profileScraper.scrape(profile, url);
        if ((r.isSuccess() && !r.getJobs().isEmpty()) || !profile.fallbackToAuto) return r;

        List<String> trace = new ArrayList<>(r.getTrace());
        trace.add("profile gave nothing, falling back to auto-detection");
        ScrapeResult auto = scrape(url);
        trace.addAll(auto.getTrace());
        return auto.isSuccess() ? ScrapeResult.ok(auto.getStrategy(), auto.getJobs(), trace) : ScrapeResult.failed(trace);
    }

    /** Generic auto-detect pipeline (no company knowledge). */
    public ScrapeResult scrape(String url) {
        List<String> trace = new ArrayList<>();
        if (url == null || url.trim().isEmpty()) {
            trace.add("empty URL");
            return ScrapeResult.failed(trace);
        }
        Found f = scrapeOnce(url, trace, 0);
        if (f == null) return ScrapeResult.failed(trace);
        return ScrapeResult.ok(f.strategy, f.jobs, trace);
    }

    private static final class Found {
        final String strategy; final List<ScrapedJob> jobs;
        Found(String strategy, List<ScrapedJob> jobs) { this.strategy = strategy; this.jobs = jobs; }
    }

    /** @return null when nothing could be fetched or extracted by any means (i.e. a hard failure). */
    private Found scrapeOnce(String url, List<String> trace, int depth) {
        String indent = depth == 0 ? "" : "  (hop) ";
        Document doc = null;
        try {
            FetchedPage page = fetcher.get(url);
            doc = Jsoup.parse(page.body, page.finalUrl);
            trace.add(indent + "fetch: HTTP " + page.status + ", " + page.body.length() + " chars");
        } catch (Exception e) {
            trace.add(indent + "fetch failed: " + e.getMessage());
        }

        ScrapeContext ctx = new ScrapeContext(url, doc, fetcher, mapper);
        Found weak = null;

        for (JobExtractionStrategy s : strategies) {
            try {
                List<ScrapedJob> jobs = normalize(s.extract(ctx), url);
                trace.add(indent + s.name() + ": " + jobs.size() + " jobs");
                if (jobs.size() >= s.minConfidentResults()) return new Found(s.name(), jobs);
                if (!jobs.isEmpty() && (weak == null || jobs.size() > weak.jobs.size())) weak = new Found(s.name(), jobs);
            } catch (Exception e) {
                trace.add(indent + s.name() + " error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                log.debug("Strategy {} failed for {}", s.name(), url, e);
            }
        }

        // Landing page with a "View all jobs" link? Hop once.
        if (depth == 0 && doc != null) {
            String hop = findAllJobsLink(doc, url);
            if (hop != null) {
                trace.add("following 'all jobs' link: " + hop);
                Found hopped = scrapeOnce(hop, trace, 1);
                if (hopped != null && (weak == null || hopped.jobs.size() > weak.jobs.size())) return hopped;
            }
        }

        if (weak != null) return weak;
        return doc != null ? new Found("none", Collections.<ScrapedJob>emptyList()) : null;
    }

    private String findAllJobsLink(Document doc, String url) {
        String host = UrlUtils.hostOf(doc.location().isEmpty() ? url : doc.location());
        String self = UrlUtils.canonicalize(doc.location().isEmpty() ? url : doc.location());
        for (Element a : doc.select("a[href]")) {
            String text = (a.text() + " " + a.attr("aria-label")).trim();
            if (text.isEmpty() || text.length() > 60 || !ALL_JOBS_LINK.matcher(text).find()) continue;
            String canon = UrlUtils.canonicalize(a.absUrl("href"));
            if (canon == null || canon.equals(self)) continue;
            if (!sameSite(host, UrlUtils.hostOf(canon)) && !isKnownAtsHost(UrlUtils.hostOf(canon))) continue;
            return canon;
        }
        return null;
    }

    private static boolean sameSite(String a, String b) {
        if (a == null || b == null) return false;
        return registrable(a).equalsIgnoreCase(registrable(b));
    }

    private static String registrable(String host) {
        String[] p = host.split("\\.");
        return p.length <= 2 ? host : p[p.length - 2] + "." + p[p.length - 1];
    }

    private static boolean isKnownAtsHost(String h) {
        return h != null && h.matches("(?i).*(greenhouse\\.io|lever\\.co|myworkdayjobs\\.com|ashbyhq\\.com|smartrecruiters\\.com|workable\\.com|recruitee\\.com|bamboohr\\.com)$");
    }

    /** Clean titles/URLs, drop navigation/informational links, de-duplicate by canonical URL. */
    private List<ScrapedJob> normalize(List<ScrapedJob> raw, String pageUrl) {
        Map<String, ScrapedJob> out = new LinkedHashMap<>();
        if (raw == null) return new ArrayList<>();
        for (ScrapedJob j : raw) {
            ScrapedJob c = filter.clean(j, pageUrl);
            if (c == null) continue;
            out.putIfAbsent(c.getUrl(), c);
            if (out.size() >= MAX_JOBS) break;
        }
        return new ArrayList<>(out.values());
    }
}
