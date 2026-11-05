package com.example.careerMonitor.monitoringService.scraper;

import java.io.IOException;

public class FetchException extends IOException {
    private final int status;
    public FetchException(String msg, int status) { super(msg); this.status = status; }
    public int getStatus() { return status; }
}
