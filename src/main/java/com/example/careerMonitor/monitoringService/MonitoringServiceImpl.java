package com.example.careerMonitor.monitoringService;

import com.example.careerMonitor.Entity.CompanyDetails;
import com.example.careerMonitor.Entity.Opportunity;
import com.example.careerMonitor.Utils.HashUtils;
import com.example.careerMonitor.monitoringService.scraper.JobEnricher;
import com.example.careerMonitor.monitoringService.scraper.JobScraper;
import com.example.careerMonitor.monitoringService.scraper.ScrapeResult;
import com.example.careerMonitor.monitoringService.scraper.ScrapedJob;
import com.example.careerMonitor.monitoringService.scraper.UrlUtils;
import com.example.careerMonitor.repository.CompanyRepo;
import com.example.careerMonitor.repository.OpportunityRepo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

/**
 * Scheduled career-page monitor.
 *
 * What changed versus the original:
 *  - scraping is delegated to {@link JobScraper} (ATS APIs, JSON-LD, embedded JSON, scored HTML, optional headless);
 *  - change detection hashes the *set of job URLs*, not the page text, so banners/dates/ads don't trigger runs
 *    and real changes aren't missed;
 *  - a failed or empty scrape never overwrites the stored hash;
 *  - no giant @Transactional around network I/O (each repository call is its own short transaction);
 *  - companies are processed concurrently with a bounded pool, and overlapping runs are prevented.
 */
@Service
public class MonitoringServiceImpl implements MonitoringServiceInterface, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(MonitoringServiceImpl.class);

    private final CompanyRepo companyRepo;
    private final OpportunityRepo opportunityRepo;
    private final JobScraper jobScraper;
    private final JobEnricher jobEnricher;
    private final ExecutorService pool;
    private final AtomicBoolean running = new AtomicBoolean(false);

    public MonitoringServiceImpl(CompanyRepo companyRepo,
                                 OpportunityRepo opportunityRepo,
                                 JobScraper jobScraper,
                                 JobEnricher jobEnricher,
                                 @Value("${career.monitor.threads:6}") int threads) {
        this.companyRepo = companyRepo;
        this.opportunityRepo = opportunityRepo;
        this.jobScraper = jobScraper;
        this.jobEnricher = jobEnricher;
        this.pool = Executors.newFixedThreadPool(Math.max(1, threads), r -> {
            Thread t = new Thread(r, "career-monitor");
            t.setDaemon(true);
            return t;
        });
    }

    @Override
    public void destroy() { pool.shutdownNow(); }

    /** Every 6 hours. */
    @Scheduled(fixedRate = 21_600_000)
    @Override
    public void monitorAllCompanyPages() {
        if (!running.compareAndSet(false, true)) {
            log.warn("Monitoring run already in progress - skipping this trigger");
            return;
        }
        try {
            log.info("--- Starting Scheduled Career Page Monitoring ---");
            List<CompanyDetails> companies = companyRepo.findAll();

            List<Callable<Void>> tasks = new ArrayList<>();
            for (CompanyDetails company : companies) {
                tasks.add(() -> {
                    try {
                        processCompanyPage(company);
                    } catch (Exception e) {
                        log.error("Error monitoring '{}': {}", company.getName(), e.getMessage(), e);
                    }
                    return null;
                });
            }
            pool.invokeAll(tasks, 30, TimeUnit.MINUTES); // hard ceiling for the whole run
            log.info("--- Monitoring Completed ({} companies) ---", companies.size());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
        }
    }

    private void processCompanyPage(CompanyDetails company) {
        String name = company.getName();
        ScrapeResult result = jobScraper.scrape(name, company.getUrl());

        if (!result.isSuccess()) {
            log.warn("'{}': scrape FAILED, keeping previous state. Trace: {}", name, result.getTrace());
            touch(company);
            return;
        }

        List<ScrapedJob> jobs = result.getJobs();
        if (jobs.isEmpty()) {
            // Reachable page but nothing recognised: likely an unsupported site or a transient empty response.
            // Do NOT overwrite the hash, or the next successful run would look like "everything is new".
            log.warn("'{}': page reachable but 0 jobs found ({}). Trace: {}", name, company.getUrl(), result.getTrace());
            touch(company);
            return;
        }

        String fingerprint = fingerprint(jobs);
        if (fingerprint.equals(company.getLastKnownContentHash())) {
            log.info("'{}': no change ({} jobs via {})", name, jobs.size(), result.getStrategy());
            touch(company);
            return;
        }

        // Dedupe against what we already stored, comparing canonical URLs on both sides
        Set<String> existing = opportunityRepo.findByCompanyDetails(company).stream()
                .map(o -> UrlUtils.canonicalize(o.getJobUrl()))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(HashSet::new));

        // 1) which jobs are genuinely new?
        List<ScrapedJob> newJobs = new ArrayList<>();
        for (ScrapedJob j : jobs) {
            if (existing.add(j.getUrl())) newJobs.add(j);
        }

        // 2) read detail pages of the NEW ones only, to learn the required experience (capped per company)
        newJobs = jobEnricher.enrich(newJobs);

        // 3) save with type ("job" | "internship") and experience ("fresher", "1 year", "2 years", ... or null)
        Date now = new Date();
        List<Opportunity> fresh = new ArrayList<>();
        for (ScrapedJob j : newJobs) {
            Opportunity o = new Opportunity(company, j.getTitle(), j.getUrl(), now,j.getType(),j.getExperience());
            o.setType(j.getType());
            o.setExperience(j.getExperience());
            fresh.add(o);
        }
        if (!fresh.isEmpty()) opportunityRepo.saveAll(fresh);

        company.setLastKnownContentHash(fingerprint); // only after the save succeeded
        touch(company);
        log.info("'{}': {} jobs found via {}, {} new", name, jobs.size(), result.getStrategy(), fresh.size());
    }

    /** Hash of the sorted canonical job URLs: stable across cosmetic page changes. */
    private static String fingerprint(List<ScrapedJob> jobs) {
        List<String> urls = new ArrayList<>(jobs.size());
        for (ScrapedJob j : jobs) urls.add(j.getUrl());
        Collections.sort(urls);
        return HashUtils.generateSha256Hash(String.join("\n", urls));
    }

    private void touch(CompanyDetails company) {
        company.setLastCheckedTimestamp(new Date());
        companyRepo.save(company);
    }
}
