package com.example.careerMonitor.monitoringService.scraper;

import java.util.List;

/**
 * One way of turning a career page URL into a list of jobs.
 * Strategies are tried in Spring {@code @Order}; the first one that returns at least
 * {@link #minConfidentResults()} jobs wins. Implementations must never throw for
 * "nothing found" - return an empty list.
 */
public interface JobExtractionStrategy {

    String name();

    List<ScrapedJob> extract(ScrapeContext ctx) throws Exception;

    /** Below this many results the answer is treated as "weak" and later strategies still get a try. */
    default int minConfidentResults() { return 1; }
}
