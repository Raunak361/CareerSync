package com.example.careerMonitor.monitoringService;

import com.example.careerMonitor.Entity.Opportunity;
import com.example.careerMonitor.monitoringService.scraper.CompanyProfile;
import com.example.careerMonitor.monitoringService.scraper.CompanyProfileRegistry;
import com.example.careerMonitor.monitoringService.scraper.JobClassifier;
import com.example.careerMonitor.monitoringService.scraper.JobEnricher;
import com.example.careerMonitor.monitoringService.scraper.JobFilter;
import com.example.careerMonitor.monitoringService.scraper.ScrapedJob;
import com.example.careerMonitor.monitoringService.scraper.JobScraper;
import com.example.careerMonitor.repository.OpportunityRepo;
import com.example.careerMonitor.monitoringService.scraper.ScrapeResult;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Development-only helpers. Remove or secure before production deployment
 * (/test/scrape fetches arbitrary URLs on behalf of the caller).
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class MonitoringTestController {

    private final MonitoringServiceInterface monitoringService;
    private final JobScraper jobScraper;
    private final JobFilter jobFilter;
    private final JobEnricher jobEnricher;
    private final CompanyProfileRegistry profileRegistry;
    private final OpportunityRepo opportunityRepo;

    @GetMapping("/monitor-now")
    public String triggerMonitoring() {
        monitoringService.monitorAllCompanyPages();
        return "Monitoring triggered manually. Check logs for details.";
    }

    /**
     * Dry-run the scraper without touching the database:
     *   GET /test/scrape?company=Stripe&enrich=true                         -> uses Stripe's profile (and its url if the profile has one)
     *   GET /test/scrape?url=https://example.com/careers        -> generic auto-detect
     *   GET /test/scrape?company=Google&url=https://...         -> company profile if one exists, else auto-detect
     * Shows which strategy matched, the jobs found, and a trace.
     */
    @GetMapping("/scrape")
    public Map<String, Object> scrape(@RequestParam(required = false) String company,
                                      @RequestParam(required = false) String url,
                                      @RequestParam(defaultValue = "false") boolean enrich) {
        if (url == null || url.isEmpty()) {
            CompanyProfile p = profileRegistry.find(company, null);
            url = p == null || p.html == null || p.html.url == null ? "" : p.html.url;
        }
        ScrapeResult r = jobScraper.scrape(company, url);
        // ?enrich=true also reads the detail pages of the first 20 jobs, exactly as the monitor does for new jobs
        List<ScrapedJob> shown = r.getJobs();
        if (enrich && !shown.isEmpty()) shown = jobEnricher.enrich(new ArrayList<>(shown.subList(0, Math.min(20, shown.size()))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", r.isSuccess());
        body.put("profilesLoaded", profileRegistry.all().size());
        body.put("strategy", r.getStrategy());
        body.put("jobCount", r.getJobs().size());
        body.put("trace", r.getTrace());
        body.put("jobs", shown.stream().limit(100).map(j -> {
            Map<String, String> m = new LinkedHashMap<>();
            m.put("title", j.getTitle());
            m.put("url", j.getUrl());
            m.put("location", j.getLocation());
            m.put("type", j.getType());
            m.put("experience", j.getExperience());
            return m;
        }).collect(Collectors.toList()));
        return body;
    }

    /** Profiles currently loaded from company-scrapers.json. */
    @GetMapping("/profiles")
    public List<CompanyProfile> profiles() {
        return profileRegistry.all();
    }

    /** Re-read company-scrapers.json without restarting. */
    @GetMapping("/profiles/reload")
    public Map<String, Object> reloadProfiles() {
        profileRegistry.reload();
        return profileRegistry.describe(); // resource = where it looked, exists = found?, loaded = profile count
    }

    /**
     * One-off cleanup of navigation/informational rows saved by the old scraper.
     *   GET /test/cleanup-noise                 -> preview only (default)
     *   GET /test/cleanup-noise?dryRun=false    -> actually delete
     * Uses the same JobFilter as the live scraper, so what it removes is exactly what would no longer be saved.
     */
    @GetMapping("/cleanup-noise")
    public Map<String, Object> cleanupNoise(@RequestParam(defaultValue = "true") boolean dryRun) {
        List<Opportunity> noise = new ArrayList<>();
        List<Map<String, Object>> preview = new ArrayList<>();
        for (Opportunity o : opportunityRepo.findAll()) {
            if (jobFilter.isNoise(o.getTitle(), o.getJobUrl())) {
                noise.add(o);
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", o.getId());
                m.put("title", o.getTitle());
                m.put("url", o.getJobUrl());
                preview.add(m);
            }
        }
        if (!dryRun && !noise.isEmpty()) opportunityRepo.deleteAll(noise);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dryRun", dryRun);
        body.put("matched", noise.size());
        body.put("rows", preview);
        return body;
    }

    /**
     * Label rows saved before type/experience existed (type is null), using what the title reveals.
     *   GET /test/backfill-labels                -> preview (default)
     *   GET /test/backfill-labels?dryRun=false   -> save
     * Titles rarely state experience, so many rows get type only; new jobs get richer labels from detail pages.
     */
    @GetMapping("/backfill-labels")
    public Map<String, Object> backfillLabels(@RequestParam(defaultValue = "true") boolean dryRun) {
        List<Opportunity> changed = new ArrayList<>();
        List<Map<String, Object>> preview = new ArrayList<>();
        for (Opportunity o : opportunityRepo.findAll()) {
            if (o.getType() != null) continue;
            ScrapedJob c = JobClassifier.complete(new ScrapedJob(o.getTitle(), o.getJobUrl(), null));
            o.setType(c.getType());
            if (o.getExperience() == null) o.setExperience(c.getExperience());
            changed.add(o);
            if (preview.size() < 100) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", o.getId());
                m.put("title", o.getTitle());
                m.put("type", c.getType());
                m.put("experience", c.getExperience());
                preview.add(m);
            }
        }
        if (!dryRun && !changed.isEmpty()) opportunityRepo.saveAll(changed);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("dryRun", dryRun);
        body.put("rows", changed.size());
        body.put("preview", preview);
        return body;
    }
}
