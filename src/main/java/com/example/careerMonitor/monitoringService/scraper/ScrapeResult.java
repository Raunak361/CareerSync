package com.example.careerMonitor.monitoringService.scraper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Outcome of scraping one career page. */
public final class ScrapeResult {
    private final boolean success;      // false = could not reach/parse at all (don't touch stored state)
    private final String strategy;      // which strategy produced the jobs
    private final List<ScrapedJob> jobs;
    private final List<String> trace;   // human-readable diagnostics

    private ScrapeResult(boolean success, String strategy, List<ScrapedJob> jobs, List<String> trace) {
        this.success = success;
        this.strategy = strategy;
        this.jobs = jobs;
        this.trace = trace;
    }

    public static ScrapeResult ok(String strategy, List<ScrapedJob> jobs, List<String> trace) {
        return new ScrapeResult(true, strategy, Collections.unmodifiableList(new ArrayList<>(jobs)), trace);
    }

    public static ScrapeResult failed(List<String> trace) {
        return new ScrapeResult(false, "none", Collections.<ScrapedJob>emptyList(), trace);
    }

    public boolean isSuccess() { return success; }
    public String getStrategy() { return strategy; }
    public List<ScrapedJob> getJobs() { return jobs; }
    public List<String> getTrace() { return trace; }
}
