package com.example.careerMonitor.monitoringService.scraper;

public final class FetchedPage {
    public final String finalUrl;
    public final int status;
    public final String contentType;
    public final String body;

    public FetchedPage(String finalUrl, int status, String contentType, String body) {
        this.finalUrl = finalUrl;
        this.status = status;
        this.contentType = contentType == null ? "" : contentType;
        this.body = body == null ? "" : body;
    }
}
