package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.jsoup.nodes.Document;

/** Everything a strategy needs. {@code document} is null when the static fetch failed. */
public final class ScrapeContext {
    public final String url;
    public final Document document;      // nullable
    public final PageFetcher fetcher;
    public final ObjectMapper mapper;

    public ScrapeContext(String url, Document document, PageFetcher fetcher, ObjectMapper mapper) {
        this.url = url;
        this.document = document;
        this.fetcher = fetcher;
        this.mapper = mapper;
    }

    /** Base URL for resolving relative links. */
    public String baseUrl() {
        return document != null && !document.location().isEmpty() ? document.location() : url;
    }
}
