package com.example.careerMonitor.monitoringService.scraper;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Last line of defence against non-job links. Every strategy's output goes through {@link #clean}.
 *
 * Rules are deliberately specific (whole-title matches / exact phrases) so real titles such as
 * "Public Policy Manager", "Contact Center Agent" or "Registered Nurse" are not rejected.
 *
 * Per-deployment extras, no code change needed (single regex each, use | to combine):
 *   career.monitor.blocked-title-regex=(?i)^(rti|tenders?)$
 *   career.monitor.blocked-url-regex=(?i)stpi\.in/(wim|icc|en/director)
 */
@Component
public class JobFilter {

    private static final Set<String> EXACT_TITLES = new HashSet<>(Arrays.asList(
            "skip to main content", "skip to content", "skip to main", "back to top", "close", "careers", "career", "jobs", "job",
            "vacancies", "vacancy", "internship", "internships", "sign in", "log in", "login", "logout", "help", "home", "about",
            "about us", "contact", "contact us", "privacy", "privacy policy", "cookie policy", "cookies", "terms", "terms of use",
            "terms of service", "accessibility", "sitemap", "site map", "saved jobs", "job alerts", "how we hire", "how we work",
            "your career", "students", "faq", "faqs", "apply", "apply now", "learn more", "read more", "menu", "search", "english",
            "hindi", "हिन्दी", "français", "deutsch", "español", "next", "previous", "more", "share", "print", "download"));

    private static final Pattern TITLE_NOISE = Pattern.compile(
            "^skip to\\b|\\bback to top\\b|\\b(privacy|cookie|refund|return)\\s+polic|terms\\s+(of\\s+(use|service)|and\\s+conditions|&\\s*conditions)"
                    + "|\\bcomplaints?\\s+(committee|cell|redress)|\\bgrievance|equal\\s+opportunity|\\beeo\\b|know your rights|workplace discrimination"
                    + "|^(sign|log)[ -]?(in|out|up)\\b|\\b(job|career)\\s+alerts?\\b|^about( us| [a-z]+)?$|^contact( us)?$|follow us|subscribe|newsletter"
                    + "|^archives?\\b|\\barchives?$|\\bsite ?map\\b|accessib\\w+\\s+(statement|options)|^(view|see|browse|show)\\s+(all|more)\\b"
                    + "|^(learn|read) more$|^(back|return)\\s+to\\b",
            Pattern.CASE_INSENSITIVE);

    private static final Pattern PREFIX = Pattern.compile(
            "^(learn more about|read more about|view job details for|view details for|view job|view role|apply for|apply to|job:)\\s+",
            Pattern.CASE_INSENSITIVE);

    /** Material-icon ligature text leaking out of the DOM, e.g. "work_outline Jobs", "open_in_new". Never in real titles. */
    private static final Pattern ICON_LIGATURE = Pattern.compile("[A-Za-z]+_[A-Za-z]+");

    /** Icon-font name glued in front of a label, e.g. "googlegoogle", "handymanhandyman". */
    private static final Pattern GLUED_DOUBLE = Pattern.compile("^([A-Za-z]{3,})\\1\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LANG_PREFIX = Pattern.compile("^/[a-z]{2}(?:-[A-Za-z]{2})?(?=/|$)");

    private static final Set<String> NOISE_LAST_SEGMENTS = new HashSet<>(Arrays.asList(
            "login", "signin", "sign-in", "logout", "privacy", "privacy-policy", "terms", "terms-of-use", "cookie", "cookies",
            "cookie-policy", "sitemap", "accessibility", "eeo", "saved", "alerts", "dashboard", "how-we-hire", "how-we-work",
            "about", "about-us", "contact", "contact-us", "faq", "faqs", "archive", "archives", "icc", "policy", "policies",
            "complaints", "students", "careers", "jobs", "internship", "internships", "vacancies", "main-career"));

    private static final Pattern NOISE_HOST = Pattern.compile("^(accounts|support|help|login|auth|sso|id)\\.", Pattern.CASE_INSENSITIVE);
    private static final Pattern PDF_NOISE = Pattern.compile("polic|rights|eeo|privacy|terms|handbook|brochure|annual-report", Pattern.CASE_INSENSITIVE);

    private final Pattern extraTitle;
    private final Pattern extraUrl;

    public JobFilter(@Value("${career.monitor.blocked-title-regex:}") String extraTitleRegex,
                     @Value("${career.monitor.blocked-url-regex:}") String extraUrlRegex) {
        this.extraTitle = extraTitleRegex == null || extraTitleRegex.trim().isEmpty() ? null : Pattern.compile(extraTitleRegex);
        this.extraUrl = extraUrlRegex == null || extraUrlRegex.trim().isEmpty() ? null : Pattern.compile(extraUrlRegex);
    }

    /** @return the cleaned job (canonical URL, tidy title) or null if it is not a job. */
    public ScrapedJob clean(ScrapedJob j, String sourcePageUrl) {
        if (j == null) return null;
        String url = UrlUtils.canonicalize(j.getUrl());
        String title = cleanTitle(j.getTitle());
        if (url == null || title == null) return null;
        if (isNoise(title, url)) return null;
        String self = UrlUtils.canonicalize(sourcePageUrl);
        if (self != null && self.equals(url)) return null; // link back to the listing page itself
        if (self != null && sameAfterLanguagePrefix(self, url)) return null; // language switcher (/hi/page vs /en/page)
        return JobClassifier.complete(j.withTitleAndUrl(title, url)); // also sets type + experience
    }

    private static boolean sameAfterLanguagePrefix(String a, String b) {
        try {
            URI x = URI.create(a), y = URI.create(b);
            if (x.getHost() == null || !x.getHost().equalsIgnoreCase(y.getHost())) return false;
            String px = LANG_PREFIX.matcher(x.getPath() == null ? "" : x.getPath()).replaceFirst("").replaceAll("/{2,}", "/");
            String py = LANG_PREFIX.matcher(y.getPath() == null ? "" : y.getPath()).replaceFirst("").replaceAll("/{2,}", "/");
            return !px.isEmpty() && px.equals(py);
        } catch (Exception e) { return false; }
    }

    /** Title tidy-up; null if nothing usable is left. */
    public String cleanTitle(String raw) {
        if (raw == null) return null;
        String t = raw.replaceAll("\\s+", " ").trim();
        if (ICON_LIGATURE.matcher(t).find()) return null;               // nav item with icon text
        t = PREFIX.matcher(t).replaceFirst("");
        if (GLUED_DOUBLE.matcher(t).find()) return null;                 // "googlegoogle ...", "handymanhandyman ..."
        t = t.replaceAll("(?i)\\b(.{3,}?)(\\s+\\1\\b)+", "$1");          // "How we work How we work" -> "How we work"
        t = t.trim();
        if (t.length() < 3 || t.length() > 160) return null;
        if (!Pattern.compile("\\p{L}").matcher(t).find()) return null;   // no letters at all
        return t;
    }

    /** True if the (title, url) pair is navigation / informational rather than a job. Also used to clean old DB rows. */
    public boolean isNoise(String title, String url) {
        String t = title == null ? "" : title.replaceAll("\\s+", " ").trim();
        String tl = t.toLowerCase();
        if (EXACT_TITLES.contains(tl.replaceAll("[^\\p{L}\\p{M}\\p{N} &]", "").trim())) return true;
        if (TITLE_NOISE.matcher(t).find()) return true;
        if (ICON_LIGATURE.matcher(t).find()) return true;
        if (extraTitle != null && extraTitle.matcher(t).find()) return true;

        String canon = UrlUtils.canonicalize(url);
        if (canon == null) return true;
        if (extraUrl != null && extraUrl.matcher(canon).find()) return true;
        try {
            URI u = URI.create(canon);
            String path = u.getPath() == null ? "" : u.getPath();
            if (path.isEmpty() && u.getQuery() == null) return true;     // bare homepage (also fragment-only links)
            if (u.getHost() != null && NOISE_HOST.matcher(u.getHost()).find()) return true;

            String[] segs = path.split("/");
            String last = segs.length == 0 ? "" : segs[segs.length - 1].toLowerCase();
            if (NOISE_LAST_SEGMENTS.contains(last)) return true;
            if (last.endsWith(".pdf") && (PDF_NOISE.matcher(last).find() || TITLE_NOISE.matcher(t).find())) return true;
        } catch (Exception e) {
            return true;
        }
        return false;
    }
}
