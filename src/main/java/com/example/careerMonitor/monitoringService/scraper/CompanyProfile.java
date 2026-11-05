package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

/**
 * How to scrape ONE company. Loaded from company-scrapers.json (see README).
 * Companies without a profile use the generic auto-detect pipeline.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class CompanyProfile {

    /** Matches CompanyDetails.getName(), case-insensitive. */
    public String company;
    /** Alternative matcher: substring of the company URL's host, e.g. "metacareers.com". */
    public String urlHost;
    /** Extra company names that should use this profile (e.g. "JP Morgan Chase", "JPMorgan"). Case-insensitive, optional. */
    public java.util.List<String> aliases;
    public boolean enabled = true;

    /** greenhouse | lever | ashby | smartrecruiters | workable | recruitee | bamboohr | workday | json-api | html */
    public String type;
    /** ATS board slug (lever EU: "eu:slug"; workday: "tenant/wdN/site"). */
    public String token;
    /** If the profile finds nothing, also try the generic pipeline (default false: fail loudly instead of guessing). */
    public boolean fallbackToAuto = false;

    public Filters filters = new Filters();
    public Api api;
    public Html html;

    /** Post-filters applied to whatever the source returned. All regexes are Java regexes, matched with find(). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Filters {
        public String includeTitleRegex;   // keep only titles matching, e.g. "(?i)intern|graduate|engineer"
        public String excludeTitleRegex;   // drop titles matching, e.g. "(?i)senior|staff|principal"
        public String includeUrlRegex;
        public String excludeUrlRegex;
        public String locationRegex;       // keep only matching locations, e.g. "(?i)india|remote"; jobs with unknown location are kept
        public int maxJobs = 0;            // 0 = no cap
    }

    /** A JSON endpoint the company's own careers page calls (find it in browser DevTools > Network > Fetch/XHR). */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Api {
        public String url;                 // may contain {offset} {page} {limit}
        public String method = "GET";      // GET | POST
        public String body;                // POST body template, may contain {offset} {page} {limit}
        public Map<String, String> headers;
        public String itemsPath;           // dotted path to the job array, e.g. "data.jobs"; empty = root is the array
        public String titleField = "title";
        public String urlField;            // field holding the job link (relative links are resolved against url)
        public String urlTemplate;         // alternative: build the link from fields, e.g. "https://x.com{job_path}" or "https://x.com/jobs/{id}"
        public String locationField;       // dotted path, optional
        public String typeField;           // optional: field holding employment type (e.g. "Intern", "Full-time")
        public String experienceField;     // optional: field holding an experience statement
        public java.util.List<String> descriptionFields; // optional: fields whose text may state the required experience (requirements first)
        public String detailUrlTemplate;   // optional: API URL returning the full posting for one job, e.g. ".../details?Id={Id}" (read only for NEW jobs, to learn the experience)
        public String detailKind;          // how to read that response: currently "oracle"
        public int pageSize = 50;          // value for {limit}; offset = pageIndex * pageSize
        public int pageStart = 0;          // first value for {page}
        public int maxPages = 10;
        public int delayMs = 0;            // pause between page requests (be polite on big sites)
    }

    /** Server-rendered listing page scraped with CSS selectors. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Html {
        public String url;                 // listing page; defaults to the company URL
        public String itemSelector;        // one element per job (or the job link itself), e.g. "a[href*='/jobs/']"
        public String titleSelector;       // inside the item; default = link text
        public String titleAttribute;      // read the title from this attribute of the link (e.g. "aria-label") instead of its text
        public String linkSelector;        // inside the item; default = the item if it is an <a>, else first <a href>
        public String locationSelector;    // inside the item, optional
        public String pageParam;           // query param for numbered pages, e.g. "page"
        public int pageStart = 1;
        public int pageStep = 1;           // added per page: 1 for ?page=1,2,3 ; 10 or 25 for offset-style params (?jobOffset=0,10,20)
        public int delayMs = 0;            // pause between page requests
        public String nextSelector;        // alternative pagination: the "Next" link
        public int maxPages = 5;
    }

    @Override public String toString() { return company + " [" + type + (token != null ? ":" + token : "") + "]"; }
}
