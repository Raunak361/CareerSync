# Career page scraper - what changed and how to use it

Drop `monitoringService/` over your existing package. `MonitoringServiceInterface` is unchanged;
`CompanyDetails`, `Opportunity`, repos and `HashUtils` are used exactly as before (no entity/schema changes).

## Company-specific profiles (NEW)
Put `resources/company-scrapers.json` at `src/main/resources/company-scrapers.json`.
A company whose name (or URL host) matches a profile is scraped *only* the way its profile says; everything else still
uses the generic pipeline below. Profile types:
- `greenhouse | lever | ashby | smartrecruiters | workable | recruitee | bamboohr | workday` + `token`
- `json-api`  - the company's own JSON endpoint (found in browser DevTools > Network > Fetch/XHR)
- `html`      - CSS selectors (+ `pageParam` or `nextSelector` pagination)
Each profile can also carry `filters`: includeTitleRegex, excludeTitleRegex, includeUrlRegex, excludeUrlRegex, locationRegex, maxJobs.
A profile that errors or returns nothing does NOT silently guess: the run is reported as failed/empty and stored state is untouched
(set `"fallbackToAuto": true` to allow the generic pipeline as a backup).

Html profiles also support `pageStep` (offset-style paging, e.g. jobOffset=0,10,20), `titleAttribute` (e.g. aria-label) and `delayMs`.

Debug: `GET /test/scrape?company=Stripe`, `GET /test/profiles`, `GET /test/profiles/reload` (edit the JSON, no restart).
Override file location: `career.scraper.profiles=file:/path/company-scrapers.json`.

## Type and experience
Every job gets `type` ("job" | "internship", never null) and `experience` ("fresher", "1 year", "2 years", "3-5 years",
"5+ years", "6 months", or null when the posting states nothing). Evidence order: provider fields / schema.org ->
title -> internship => fresher -> description (numbers only count next to the word "experience"; "founded 12 years ago" is ignored).
New jobs only have their detail page read (`JobEnricher`): `career.monitor.enrich.max-per-company=60` (0 disables), `career.monitor.enrich.delay-ms=250`.
`Opportunity` needs `setType(String)` and `setExperience(String)`. Label old rows: `GET /test/backfill-labels` then `?dryRun=false`.
Try it: `GET /test/scrape?company=Stripe&enrich=true`.

## Pipeline for companies without a profile (first confident result wins)
1. `AtsApiStrategy`        - detects Greenhouse / Lever / Ashby / SmartRecruiters / Workable / Recruitee / BambooHR / Workday
                             from the URL *or* iframes/scripts/links inside the page and calls their public JSON API.
2. `JsonLdStrategy`        - schema.org `JobPosting` markup.
3. `EmbeddedJsonStrategy`  - job arrays inside `__NEXT_DATA__`, `window.__INITIAL_STATE__`, `application/json` scripts.
4. `HtmlHeuristicStrategy` - scored link extraction (nav/footer removed, URL-shape clustering, card-title resolution, pagination).
5. `HeadlessRenderStrategy`- OPTIONAL Playwright fallback for JS-only pages (see below).
Plus: if a landing page yields nothing, one "View all jobs / Open positions" link is followed.

## Debug any site without touching the DB
`GET /test/scrape?url=https://company.com/careers` -> strategy used, jobs, and a per-strategy trace.

## Optional headless browser
pom.xml:
    <dependency>
      <groupId>com.microsoft.playwright</groupId>
      <artifactId>playwright</artifactId>
      <version>1.47.0</version>
    </dependency>
1. rename `scraper/optional/HeadlessRenderStrategy.java.optional` -> `scraper/HeadlessRenderStrategy.java`
2. application.properties: `career.scraper.headless.enabled=true`
3. one-time browser download: `mvn exec:java -e -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"`

## Config
    career.monitor.threads=6        # parallel companies

## Noise filtering (`JobFilter`)
Every strategy's output passes through `JobFilter`: drops navigation/informational links (Skip to Main Content, Careers,
Complaints Committee, sign-in, privacy/EEO, PDFs of policies, language switchers, bare homepages, icon-font junk) and tidies titles
("Learn more about X" -> "X"). Add site-specific rules without code changes:
    career.monitor.blocked-title-regex=(?i)^(rti|tenders?)$
    career.monitor.blocked-url-regex=(?i)stpi\.in/(wim|icc|en/director)

## Cleanup
The old keyword heuristic stored nav links ("Careers", "Apply") as opportunities. Preview with `GET /test/cleanup-noise`, then delete with `?dryRun=false` (needs `getId()`/`getTitle()` on `Opportunity`);
new runs dedupe on canonicalised URLs so real jobs won't be duplicated.

## Requires
Java 11+, Spring Boot 2.x/3.x, Jsoup, Jackson (already in spring-boot-starter-web), SLF4J.

## JPMorgan Chase (Oracle Recruiting Cloud)
Profile `JPMorgan Chase` in `company-scrapers.json` (json-api against `jpmc.fa.oraclecloud.com`, site `CX_1001`). Matches by company name,
`aliases`, or a company URL containing `jpmorgan`. New optional profile fields: `aliases` (extra names), `urlHost` may be comma-separated,
`api.detailUrlTemplate` + `api.detailKind` (`oracle`) to read each NEW job's description for the experience label. Nothing else changed.
Test: `GET /test/scrape?company=JPMorgan%20Chase&enrich=true`. Default filter keeps India locations; remove `filters.locationRegex` for all countries.
