package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Most career pages are just a front for an Applicant Tracking System that exposes a public JSON API.
 * Detecting the ATS (from the URL, or from iframes/links/scripts inside the page) and calling that API
 * is by far the most reliable method: no HTML parsing, no JavaScript rendering, full pagination.
 *
 * Supported: Greenhouse, Lever, Ashby, SmartRecruiters, Workable, Recruitee, BambooHR, Workday.
 */
@Component
@Order(1)
public class AtsApiStrategy implements JobExtractionStrategy {

    private static final Logger log = LoggerFactory.getLogger(AtsApiStrategy.class);
    private static final int MAX_JOBS = 2000;

    private static final Pattern GREENHOUSE_EMBED = Pattern.compile("greenhouse\\.io/embed/job_board(?:/js)?\\?for=([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern GREENHOUSE = Pattern.compile("(?:boards|job-boards)(?:\\.eu)?\\.greenhouse\\.io/([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern LEVER = Pattern.compile("jobs\\.(eu\\.)?lever\\.co/([\\w.-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ASHBY = Pattern.compile("jobs\\.ashbyhq\\.com/([\\w.%-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SMARTRECRUITERS = Pattern.compile("(?:careers|jobs)\\.smartrecruiters\\.com/([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORKABLE = Pattern.compile("apply\\.workable\\.com/([\\w-]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern RECRUITEE = Pattern.compile("([\\w-]+)\\.recruitee\\.com", Pattern.CASE_INSENSITIVE);
    private static final Pattern BAMBOO = Pattern.compile("([\\w-]+)\\.bamboohr\\.com", Pattern.CASE_INSENSITIVE);
    private static final Pattern WORKDAY = Pattern.compile("([\\w-]+)\\.(wd\\d+)\\.myworkdayjobs\\.com/(?:[a-z]{2}-[A-Za-z]{2}/)?([\\w-]+)", Pattern.CASE_INSENSITIVE);

    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "embed", "api", "static", "assets", "www", "widget", "cdn", "js", "css", "images", "img"));

    @Override public String name() { return "ats-api"; }

    private final ThreadLocal<String> lastError = new ThreadLocal<>();

    /**
     * Company-specific entry point: skip detection and call one provider with a known token.
     * provider: greenhouse | lever | ashby | smartrecruiters | workable | recruitee | bamboohr | workday
     * token: board slug; lever EU accounts use "eu:slug"; workday uses "tenant/wdN/site" (e.g. nvidia/wd5/NVIDIAExternalCareerSite).
     * Unlike extract(), failures are thrown so a broken profile is visible instead of looking like "0 jobs".
     */
    public List<ScrapedJob> fetch(String provider, String token, ScrapeContext ctx) throws java.io.IOException {
        lastError.remove();
        List<ScrapedJob> jobs;
        switch (provider.toLowerCase()) {
            case "greenhouse":      jobs = greenhouse(ctx, token); break;
            case "lever":
                boolean eu = token.startsWith("eu:");
                jobs = lever(ctx, eu ? token.substring(3) : token, eu); break;
            case "ashby":           jobs = ashby(ctx, token); break;
            case "smartrecruiters": jobs = smartRecruiters(ctx, token); break;
            case "workable":        jobs = workable(ctx, token); break;
            case "recruitee":       jobs = recruitee(ctx, token); break;
            case "bamboohr":        jobs = bamboo(ctx, token); break;
            case "workday":
                String[] parts = token.split("/");
                if (parts.length != 3) throw new IllegalArgumentException("workday token must be tenant/wdN/site, got: " + token);
                jobs = workday(ctx, parts[0], parts[1], parts[2]); break;
            default: throw new IllegalArgumentException("Unknown ATS provider: " + provider);
        }
        if (jobs.isEmpty() && lastError.get() != null) {
            throw new java.io.IOException(provider + ":" + token + " failed: " + lastError.get());
        }
        return jobs;
    }

    @Override
    public List<ScrapedJob> extract(ScrapeContext ctx) {
        // URL first (strongest signal), then anything embedded in the page
        StringBuilder hay = new StringBuilder(ctx.url).append('\n');
        if (ctx.document != null) {
            ctx.document.select("a[href], iframe[src], script[src], link[href]").forEach(e -> {
                hay.append(e.hasAttr("href") ? e.attr("href") : e.attr("src")).append('\n');
            });
            hay.append(ctx.document.html()); // catches inline loader scripts, e.g. Greenhouse embed
        }
        String h = hay.toString();

        List<ScrapedJob> jobs;
        for (String t : tokens(GREENHOUSE_EMBED, h, 1)) if (!(jobs = greenhouse(ctx, t)).isEmpty()) return jobs;
        for (String t : tokens(GREENHOUSE, h, 1)) if (!(jobs = greenhouse(ctx, t)).isEmpty()) return jobs;

        Matcher m = LEVER.matcher(h);
        Set<String> seen = new LinkedHashSet<>();
        while (m.find() && seen.size() < 3) {
            String key = (m.group(1) != null ? "eu:" : "us:") + m.group(2);
            if (!seen.add(key)) continue;
            if (!(jobs = lever(ctx, m.group(2), m.group(1) != null)).isEmpty()) return jobs;
        }
        for (String t : tokens(ASHBY, h, 1)) if (!(jobs = ashby(ctx, t)).isEmpty()) return jobs;
        for (String t : tokens(SMARTRECRUITERS, h, 1)) if (!(jobs = smartRecruiters(ctx, t)).isEmpty()) return jobs;
        for (String t : tokens(WORKABLE, h, 1)) if (!(jobs = workable(ctx, t)).isEmpty()) return jobs;
        for (String t : tokens(RECRUITEE, h, 1)) if (!(jobs = recruitee(ctx, t)).isEmpty()) return jobs;
        for (String t : tokens(BAMBOO, h, 1)) if (!(jobs = bamboo(ctx, t)).isEmpty()) return jobs;

        Matcher w = WORKDAY.matcher(h);
        Set<String> wseen = new LinkedHashSet<>();
        while (w.find() && wseen.size() < 3) {
            String key = w.group(1) + "|" + w.group(2) + "|" + w.group(3);
            if (!wseen.add(key)) continue;
            if (!(jobs = workday(ctx, w.group(1), w.group(2), w.group(3))).isEmpty()) return jobs;
        }
        return Collections.emptyList();
    }

    private static List<String> tokens(Pattern p, String hay, int group) {
        Set<String> out = new LinkedHashSet<>();
        Matcher m = p.matcher(hay);
        while (m.find() && out.size() < 3) {
            String t = m.group(group);
            if (t != null && !RESERVED.contains(t.toLowerCase())) out.add(t);
        }
        return new ArrayList<>(out);
    }

    // ---------------------------------------------------------------- providers

    private List<ScrapedJob> greenhouse(ScrapeContext ctx, String token) {
        return call("greenhouse:" + token, () -> {
            JsonNode root = json(ctx, "https://boards-api.greenhouse.io/v1/boards/" + token + "/jobs");
            List<ScrapedJob> out = new ArrayList<>();
            for (JsonNode j : root.path("jobs")) {
                out.add(new ScrapedJob(text(j, "title"), text(j, "absolute_url"), j.path("location").path("name").asText(null))
                        .withDetail("https://boards-api.greenhouse.io/v1/boards/" + token + "/jobs/" + text(j, "id"), "greenhouse"));
            }
            return out;
        });
    }

    private List<ScrapedJob> lever(ScrapeContext ctx, String company, boolean eu) {
        return call("lever:" + company, () -> {
            String host = eu ? "api.eu.lever.co" : "api.lever.co";
            JsonNode root = json(ctx, "https://" + host + "/v0/postings/" + company + "?mode=json");
            List<ScrapedJob> out = new ArrayList<>();
            for (JsonNode j : root) {
                StringBuilder desc = new StringBuilder(TextUtils.nz(text(j, "descriptionPlain")));
                for (JsonNode l : j.path("lists")) {
                    desc.append(' ').append(TextUtils.nz(text(l, "text"))).append(": ").append(TextUtils.nz(TextUtils.toPlain(text(l, "content"))));
                }
                out.add(new ScrapedJob(text(j, "text"), text(j, "hostedUrl"), j.path("categories").path("location").asText(null))
                        .withHints(j.path("categories").path("commitment").asText(null), null, desc.toString()));
            }
            return out;
        });
    }

    private List<ScrapedJob> ashby(ScrapeContext ctx, String name) {
        return call("ashby:" + name, () -> {
            JsonNode root = json(ctx, "https://api.ashbyhq.com/posting-api/job-board/" + name);
            List<ScrapedJob> out = new ArrayList<>();
            for (JsonNode j : root.path("jobs")) {
                if (j.path("isListed").isBoolean() && !j.path("isListed").asBoolean()) continue;
                out.add(new ScrapedJob(text(j, "title"), text(j, "jobUrl"), j.path("location").asText(null))
                        .withHints(text(j, "employmentType"), null, text(j, "descriptionPlain")));
            }
            return out;
        });
    }

    private List<ScrapedJob> smartRecruiters(ScrapeContext ctx, String id) {
        return call("smartrecruiters:" + id, () -> {
            List<ScrapedJob> out = new ArrayList<>();
            int offset = 0, total = Integer.MAX_VALUE;
            while (offset < total && out.size() < MAX_JOBS) {
                JsonNode root = json(ctx, "https://api.smartrecruiters.com/v1/companies/" + id + "/postings?limit=100&offset=" + offset);
                total = root.path("totalFound").asInt(0);
                JsonNode content = root.path("content");
                if (!content.isArray() || content.size() == 0) break;
                for (JsonNode j : content) {
                    String loc = j.path("location").path("city").asText("");
                    String country = j.path("location").path("country").asText("");
                    out.add(new ScrapedJob(text(j, "name"),
                            "https://jobs.smartrecruiters.com/" + id + "/" + text(j, "id"),
                            (loc + " " + country).trim().isEmpty() ? null : (loc + " " + country).trim())
                            .withHints(j.path("typeOfEmployment").path("label").asText(null),
                                       j.path("experienceLevel").path("label").asText(null), null)
                            .withDetail("https://api.smartrecruiters.com/v1/companies/" + id + "/postings/" + text(j, "id"), "smartrecruiters"));
                }
                offset += content.size();
            }
            return out;
        });
    }

    private List<ScrapedJob> workable(ScrapeContext ctx, String account) {
        return call("workable:" + account, () -> {
            JsonNode root = json(ctx, "https://apply.workable.com/api/v1/widget/accounts/" + account + "?details=true");
            List<ScrapedJob> out = new ArrayList<>();
            for (JsonNode j : root.path("jobs")) {
                String url = text(j, "url");
                if (url == null && j.hasNonNull("shortcode"))
                    url = "https://apply.workable.com/" + account + "/j/" + j.get("shortcode").asText() + "/";
                out.add(new ScrapedJob(text(j, "title"), url, j.path("city").asText(null))
                        .withHints(text(j, "employment_type"), null, TextUtils.toPlain(text(j, "description"))));
            }
            return out;
        });
    }

    private List<ScrapedJob> recruitee(ScrapeContext ctx, String company) {
        if (RESERVED.contains(company.toLowerCase())) return Collections.emptyList();
        return call("recruitee:" + company, () -> {
            JsonNode root = json(ctx, "https://" + company + ".recruitee.com/api/offers/");
            List<ScrapedJob> out = new ArrayList<>();
            for (JsonNode j : root.path("offers")) {
                out.add(new ScrapedJob(text(j, "title"), text(j, "careers_url"), j.path("location").asText(null))
                        .withHints(text(j, "employment_type_code"), null,
                                TextUtils.toPlain(TextUtils.nz(text(j, "description")) + " " + TextUtils.nz(text(j, "requirements")))));
            }
            return out;
        });
    }

    private List<ScrapedJob> bamboo(ScrapeContext ctx, String company) {
        if (RESERVED.contains(company.toLowerCase())) return Collections.emptyList();
        return call("bamboohr:" + company, () -> {
            JsonNode root = json(ctx, "https://" + company + ".bamboohr.com/careers/list");
            List<ScrapedJob> out = new ArrayList<>();
            for (JsonNode j : root.path("result")) {
                out.add(new ScrapedJob(text(j, "jobOpeningName"),
                        "https://" + company + ".bamboohr.com/careers/" + text(j, "id"),
                        j.path("location").path("city").asText(null)));
            }
            return out;
        });
    }

    private List<ScrapedJob> workday(ScrapeContext ctx, String tenant, String wd, String site) {
        return call("workday:" + tenant + "/" + site, () -> {
            String base = "https://" + tenant + "." + wd + ".myworkdayjobs.com";
            String api = base + "/wday/cxs/" + tenant + "/" + site + "/jobs";
            List<ScrapedJob> out = new ArrayList<>();
            int offset = 0, total = Integer.MAX_VALUE;
            while (offset < total && out.size() < MAX_JOBS) {
                String body = "{\"appliedFacets\":{},\"limit\":20,\"offset\":" + offset + ",\"searchText\":\"\"}";
                JsonNode root = ctx.mapper.readTree(ctx.fetcher.postJson(api, body).body);
                if (offset == 0) total = root.path("total").asInt(0);
                JsonNode postings = root.path("jobPostings");
                if (!postings.isArray() || postings.size() == 0) break;
                for (JsonNode j : postings) {
                    String path = text(j, "externalPath");
                    if (path == null) continue;
                    out.add(new ScrapedJob(text(j, "title"), base + "/" + site + path, j.path("locationsText").asText(null))
                            .withDetail(base + "/wday/cxs/" + tenant + "/" + site + path, "workday"));
                }
                offset += postings.size();
            }
            return out;
        });
    }

    // ---------------------------------------------------------------- helpers

    private interface Call { List<ScrapedJob> run() throws Exception; }

    private List<ScrapedJob> call(String label, Call c) {
        try {
            List<ScrapedJob> raw = c.run();
            List<ScrapedJob> clean = new ArrayList<>();
            for (ScrapedJob j : raw) {
                if (j.getTitle() == null || j.getTitle().trim().isEmpty() || UrlUtils.canonicalize(j.getUrl()) == null) continue;
                clean.add(j);
            }
            if (!clean.isEmpty()) log.debug("ATS {} returned {} jobs", label, clean.size());
            return clean;
        } catch (Exception e) {
            lastError.set(e.getMessage());
            log.debug("ATS probe {} failed: {}", label, e.getMessage());
            return Collections.emptyList();
        }
    }

    private static JsonNode json(ScrapeContext ctx, String url) throws Exception {
        return ctx.mapper.readTree(ctx.fetcher.getJson(url).body);
    }

    private static String text(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
