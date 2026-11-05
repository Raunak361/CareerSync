package com.example.careerMonitor.monitoringService.scraper;

import java.util.Objects;

/**
 * A job found on a career page. Immutable; use the with*() methods to derive modified copies.
 * type/experience are the values that get stored; description/detailUrl/detailKind are transient helpers
 * used only to work out experience (they are never persisted).
 */
public final class ScrapedJob {
    private static final int MAX_DESCRIPTION = 6000;

    private final String title;
    private final String url;
    private final String location;   // nullable
    private final String type;       // "job" | "internship" (null until classified)
    private final String experience; // "fresher" | "1 year" | "2 years" | "3-5 years" | "5+ years" | null
    private final String description;// plain text, transient, nullable
    private final String detailUrl;  // optional API URL that returns the full posting (second request)
    private final String detailKind; // greenhouse | workday | smartrecruiters | null

    public ScrapedJob(String title, String url, String location) {
        this(title, url, location, null, null, null, null, null);
    }

    private ScrapedJob(String title, String url, String location, String type, String experience,
                       String description, String detailUrl, String detailKind) {
        this.title = title;
        this.url = url;
        this.location = location;
        this.type = type;
        this.experience = experience;
        this.description = description != null && description.length() > MAX_DESCRIPTION
                ? description.substring(0, MAX_DESCRIPTION) : description;
        this.detailUrl = detailUrl;
        this.detailKind = detailKind;
    }

    public String getTitle() { return title; }
    public String getUrl() { return url; }
    public String getLocation() { return location; }
    public String getType() { return type; }
    public String getExperience() { return experience; }
    public String getDescription() { return description; }
    public String getDetailUrl() { return detailUrl; }
    public String getDetailKind() { return detailKind; }

    public ScrapedJob withTitleAndUrl(String t, String u) { return new ScrapedJob(t, u, location, type, experience, description, detailUrl, detailKind); }
    public ScrapedJob withType(String v) { return new ScrapedJob(title, url, location, v, experience, description, detailUrl, detailKind); }
    public ScrapedJob withExperience(String v) { return new ScrapedJob(title, url, location, type, v, description, detailUrl, detailKind); }
    public ScrapedJob withDescription(String v) { return new ScrapedJob(title, url, location, type, experience, v, detailUrl, detailKind); }
    public ScrapedJob withHints(String typeHint, String experienceHint, String desc) {
        return new ScrapedJob(title, url, location, typeHint, experienceHint, desc, detailUrl, detailKind);
    }
    public ScrapedJob withDetail(String u, String kind) { return new ScrapedJob(title, url, location, type, experience, description, u, kind); }

    @Override public boolean equals(Object o) { return o instanceof ScrapedJob && ((ScrapedJob) o).url.equals(url); }
    @Override public int hashCode() { return Objects.hash(url); }
    @Override public String toString() { return title + " [" + type + ", " + experience + "] -> " + url; }
}
