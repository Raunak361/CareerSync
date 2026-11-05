package com.example.careerMonitor.monitoringService.scraper;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Last-resort strategy for plain server-rendered pages. Unlike the old keyword match this one:
 *  - ignores nav/footer/aside,
 *  - scores each link on URL shape (detail-page path, IDs), title, and list-like container,
 *  - rejects generic labels ("Careers", "Apply now") and resolves card links to the card's heading,
 *  - clusters links by URL pattern: a real job list is many links sharing one pattern,
 *  - follows rel=next / "Next" pagination.
 */
@Component
@Order(4)
public class HtmlHeuristicStrategy implements JobExtractionStrategy {

    private static final Logger log = LoggerFactory.getLogger(HtmlHeuristicStrategy.class);
    private static final int MAX_PAGES = 8;
    private static final int MAX_JOBS = 1000;

    private static final Pattern DETAIL_PATH = Pattern.compile(
            "/(jobs?|careers?|positions?|openings?|vacanc(?:y|ies)|postings?|opportunit(?:y|ies)|requisitions?|roles?|job-?details?|job-?openings?|j)/[^/?#]+",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_LIKE = Pattern.compile(
            "(\\d{4,})|([0-9a-f]{8}-[0-9a-f]{4}-)|([?&](jid|jobid|job_id|gh_jid|reqid|requisition|positionid|vacancyid|opening_id|id)=)",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ATS_HOST = Pattern.compile(
            "greenhouse\\.io|lever\\.co|myworkdayjobs\\.com|ashbyhq\\.com|smartrecruiters\\.com|workable\\.com|recruitee\\.com|"
                    + "bamboohr\\.com|icims\\.com|taleo\\.net|jobvite\\.com|successfactors|breezy\\.hr|teamtailor\\.com|personio|"
                    + "join\\.com|pinpointhq\\.com|oraclecloud\\.com", Pattern.CASE_INSENSITIVE);
    private static final Pattern TITLE_WORD = Pattern.compile(
            "\\b(engineer|developer|manager|analyst|designer|intern(ship)?|associate|director|coordinator|specialist|consultant|"
                    + "scientist|architect|administrator|officer|executive|lead|head of|technician|assistant|representative|"
                    + "recruiter|trainee|apprentice|writer|editor|accountant|nurse|teacher|driver|operator|advisor|researcher|"
                    + "devops|sre|qa|tester|programmer|product|marketing|sales|support)\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LISTY_CLASS = Pattern.compile(
            "job|position|opening|vacanc|posting|career|listing|result|card|role", Pattern.CASE_INSENSITIVE);
    private static final Pattern FILE_EXT = Pattern.compile(
            "\\.(jpe?g|png|gif|svg|webp|ico|css|js|zip|mp4|mp3|woff2?|xml)(\\?.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> GENERIC_PATH_SEGMENTS = new HashSet<>(Arrays.asList(
            "careers", "career", "jobs", "job", "openings", "positions", "about", "about-us", "contact", "contact-us", "blog",
            "news", "press", "login", "signin", "sign-in", "search", "faq", "privacy", "terms", "benefits", "culture",
            "teams", "locations", "life", "internships", "students", "graduates", "home", "alerts"));
    private static final Set<String> GENERIC_TITLES = new HashSet<>(Arrays.asList(
            "apply", "apply now", "apply here", "apply today", "view", "view job", "view details", "view role", "details", "learn more",
            "read more", "more", "more info", "see more", "view all", "view all jobs", "all jobs", "see all jobs", "search jobs",
            "find jobs", "careers", "career", "jobs", "job", "home", "about", "about us", "contact", "contact us", "login", "log in",
            "sign in", "sign up", "register", "privacy", "privacy policy", "terms", "terms of service", "terms & conditions",
            "cookie", "cookies", "cookie policy", "blog", "news", "press", "next", "previous", "prev", "back", "load more",
            "show more", "skip to content", "skip to main content", "menu", "search", "filter", "share", "english", "faq",
            "benefits", "our culture", "open positions", "current openings", "job alerts", "create alert", "submit", "go", "here",
            "click here", "back to jobs", "back to all jobs", "return to jobs", "view open positions"));
    private static final Set<String> SOCIAL_HOSTS = new HashSet<>(Arrays.asList(
            "facebook.com", "twitter.com", "x.com", "instagram.com", "youtube.com", "tiktok.com", "pinterest.com", "wa.me", "t.me"));

    private static final class Cand {
        final String url, title, shape; final int score;
        Cand(String url, String title, String shape, int score) { this.url = url; this.title = title; this.shape = shape; this.score = score; }
    }

    @Override public String name() { return "html-heuristic"; }
    @Override public int minConfidentResults() { return 3; }

    @Override
    public List<ScrapedJob> extract(ScrapeContext ctx) {
        if (ctx.document == null) return Collections.emptyList();
        Map<String, ScrapedJob> out = new LinkedHashMap<>();
        Set<String> visited = new HashSet<>();
        Document doc = ctx.document;
        String pageUrl = ctx.baseUrl();

        for (int page = 0; page < MAX_PAGES && out.size() < MAX_JOBS; page++) {
            visited.add(String.valueOf(UrlUtils.canonicalize(pageUrl)));
            int before = out.size();
            collect(doc, pageUrl, out);
            if (page > 0 && out.size() == before) break; // pagination yielded nothing new

            String next = findNext(doc, pageUrl, visited);
            if (next == null) break;
            try {
                FetchedPage p = ctx.fetcher.get(next);
                pageUrl = p.finalUrl;
                doc = Jsoup.parse(p.body, p.finalUrl);
            } catch (Exception e) {
                log.debug("Pagination fetch failed for {}: {}", next, e.getMessage());
                break;
            }
        }
        return new ArrayList<>(out.values());
    }

    // ------------------------------------------------------------------ extraction

    private void collect(Document source, String pageUrl, Map<String, ScrapedJob> out) {
        Document work = source.clone();
        work.select("nav, footer, aside, [role=navigation], [role=contentinfo]").remove();
        String selfUrl = UrlUtils.canonicalize(pageUrl);

        List<Cand> cands = new ArrayList<>();
        for (Element a : work.select("a[href]")) {
            String canon = UrlUtils.canonicalize(a.absUrl("href"));
            if (canon == null || canon.equals(selfUrl) || FILE_EXT.matcher(canon).find() || isSocial(canon)) continue;

            String title = titleOf(a);
            if (title == null) continue;
            int score = score(a, canon, title);
            if (score >= 2) cands.add(new Cand(canon, title, shape(canon), score));
        }

        Map<String, Integer> clusterSize = new HashMap<>();
        for (Cand c : cands) clusterSize.merge(c.shape, 1, Integer::sum);

        for (Cand c : cands) {
            boolean accept = c.score >= 4 || clusterSize.get(c.shape) >= 3;
            if (accept && out.size() < MAX_JOBS) out.putIfAbsent(c.url, new ScrapedJob(c.title, c.url, null));
        }
    }

    private int score(Element a, String canon, String title) {
        int s = 0;
        URI u = URI.create(canon);
        String path = u.getPath() == null ? "" : u.getPath();

        // Under /careers/... almost every link matches the keyword, so a detail page must also *look* like one:
        // an ID/number in the path or a long slug. Otherwise it is a section page (/careers/ai, /careers/teams).
        if (DETAIL_PATH.matcher(path).find()) s += looksLikeDetailSlug(path) ? 3 : 1;
        if (ID_LIKE.matcher(canon).find()) s += 2;
        if (ATS_HOST.matcher(String.valueOf(u.getHost())).find()) s += 1;
        if (TITLE_WORD.matcher(title).find()) s += 1;
        if (inListyContainer(a)) s += 1;

        String[] segs = path.split("/");
        String last = segs.length == 0 ? "" : segs[segs.length - 1].toLowerCase();
        if (GENERIC_PATH_SEGMENTS.contains(last)) s -= 3;
        if (path.matches("(?i).*/(category|tag|search|filter|login|signin)(/.*)?")) s -= 2;
        return s;
    }

    private static boolean looksLikeDetailSlug(String path) {
        String[] segs = path.split("/");
        String last = segs.length == 0 ? "" : segs[segs.length - 1];
        return last.matches(".*\\d{3,}.*") || last.split("-").length >= 4;
    }

    private static boolean inListyContainer(Element a) {
        Element p = a;
        for (int i = 0; i < 4 && p != null; i++, p = p.parent()) {
            String tag = p.tagName();
            if (tag.equals("li") || tag.equals("tr") || tag.equals("article")) return true;
            if (LISTY_CLASS.matcher(p.className() + " " + p.id()).find() && !tag.equals("body") && !tag.equals("html")) return true;
        }
        return false;
    }

    /** Best human title for a link: inner heading > link text > aria-label/title > card heading. */
    private String titleOf(Element a) {
        Element h = a.selectFirst("h1, h2, h3, h4, h5, h6, [class*=title]");
        String t = clean(h != null ? h.text() : a.text());
        if (usable(t)) return clip(t);

        for (String attr : new String[]{"aria-label", "title"}) {
            t = clean(a.attr(attr));
            if (usable(t)) return clip(t);
        }
        // "Apply" buttons inside a card: climb to the card, but only while it still looks like one card
        Element p = a.parent();
        for (int i = 0; i < 3 && p != null; i++, p = p.parent()) {
            if (p.select("a[href]").size() > 3) break;
            Element ch = p.selectFirst("h1, h2, h3, h4, h5, h6, [class*=title]");
            if (ch != null) {
                t = clean(ch.text());
                if (usable(t)) return clip(t);
            }
        }
        return null;
    }

    private static boolean usable(String t) {
        return t != null && t.length() >= 3 && !GENERIC_TITLES.contains(t.toLowerCase().replaceAll("[^a-z0-9 &]", "").trim())
                && !t.matches("^[\\p{Punct}\\d\\s]+$");
    }

    private static String clean(String s) { return s == null ? null : s.replaceAll("\\s+", " ").trim(); }
    private static String clip(String s) { return s.length() > 160 ? s.substring(0, 160).trim() : s; }

    private static boolean isSocial(String canon) {
        String host = UrlUtils.hostOf(canon);
        if (host == null) return true;
        host = host.replaceFirst("^www\\.", "");
        if (SOCIAL_HOSTS.contains(host)) return true;
        return host.equals("linkedin.com") && !canon.contains("/jobs/view");
    }

    /** URL "shape": host + path with the last segment dropped and numeric segments collapsed + query keys. */
    private static String shape(String canon) {
        URI u = URI.create(canon);
        String[] segs = (u.getPath() == null ? "" : u.getPath()).split("/");
        StringBuilder sb = new StringBuilder(String.valueOf(u.getHost())).append('|');
        for (int i = 0; i < segs.length - 1; i++) sb.append(segs[i].matches(".*\\d{2,}.*") ? "#" : segs[i]).append('/');
        sb.append('|');
        if (u.getQuery() != null) {
            List<String> keys = new ArrayList<>();
            for (String p : u.getQuery().split("&")) keys.add(p.split("=")[0]);
            Collections.sort(keys);
            sb.append(String.join(",", keys));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ pagination

    private String findNext(Document doc, String pageUrl, Set<String> visited) {
        String host = UrlUtils.hostOf(pageUrl);
        for (Element a : doc.select("a[href]")) {
            String text = clean(a.text()).toLowerCase();
            String aria = a.attr("aria-label").toLowerCase();
            boolean isNext = a.attr("rel").toLowerCase().contains("next")
                    || text.matches("next( page)?|next ›|›|»|>|older( posts)?")
                    || aria.matches("next( page)?");
            if (!isNext) continue;
            String canon = UrlUtils.canonicalize(a.absUrl("href"));
            if (canon == null || visited.contains(canon)) continue;
            if (host != null && !host.equalsIgnoreCase(String.valueOf(UrlUtils.hostOf(canon)))) continue;
            return a.absUrl("href");
        }
        return null;
    }
}
