package com.example.careerMonitor.monitoringService.scraper;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Loads company-scrapers.json. Edit the file and call /test/profiles/reload (or restart) - no code change needed.
 * Location: classpath:company-scrapers.json by default; override with career.scraper.profiles=file:/path/to/file.json
 */
@Component
public class CompanyProfileRegistry {

    private static final Logger log = LoggerFactory.getLogger(CompanyProfileRegistry.class);
    private static final Set<String> ATS = new HashSet<>(Arrays.asList(
            "greenhouse", "lever", "ashby", "smartrecruiters", "workable", "recruitee", "bamboohr", "workday"));

    private final ObjectMapper mapper;
    private final Resource resource;
    private volatile List<CompanyProfile> profiles = Collections.emptyList();

    public CompanyProfileRegistry(ObjectMapper mapper,
                                  @Value("${career.scraper.profiles:classpath:company-scrapers.json}") Resource resource) {
        this.mapper = mapper;
        this.resource = resource;
        reload();
    }

    public synchronized int reload() {
        List<CompanyProfile> loaded = new ArrayList<>();
        try {
            if (!resource.exists()) {
                log.warn("No company profile file found at {} - all companies use auto-detection. "
                        + "Put company-scrapers.json in src/main/resources (not src/main/java) and rebuild, "
                        + "or set career.scraper.profiles=file:/absolute/path.json", resource);
                profiles = loaded;
                return 0;
            }
            try (InputStream in = resource.getInputStream()) {
                JsonNode root = mapper.readTree(in);
                for (JsonNode n : root.path("profiles")) {
                    CompanyProfile p = mapper.treeToValue(n, CompanyProfile.class);
                    String problem = validate(p);
                    if (problem != null) { log.warn("Skipping invalid profile {}: {}", p, problem); continue; }
                    if (!p.enabled) continue;
                    loaded.add(p);
                }
            }
            log.info("Loaded {} company scraping profiles", loaded.size());
        } catch (Exception e) {
            log.error("Could not read company profiles from {}: {}", resource, e.getMessage());
        }
        profiles = loaded;
        return loaded.size();
    }

    /** Name match wins over host match. */
    public CompanyProfile find(String companyName, String url) {
        List<CompanyProfile> snapshot = profiles;
        if (companyName != null) {
            for (CompanyProfile p : snapshot)
                if (p.company != null && p.company.trim().equalsIgnoreCase(companyName.trim())) return p;
            for (CompanyProfile p : snapshot)
                if (p.aliases != null) for (String a : p.aliases)
                    if (a != null && a.trim().equalsIgnoreCase(companyName.trim())) return p;
        }
        String host = UrlUtils.hostOf(url == null ? "" : url);
        if (host != null) {
            for (CompanyProfile p : snapshot) {
                if (p.urlHost == null || p.urlHost.isEmpty()) continue;
                for (String h : p.urlHost.split(",")) { // "a.com,b.com" allowed; a single value works as before
                    h = h.trim().toLowerCase();
                    if (!h.isEmpty() && host.toLowerCase().contains(h)) return p;
                }
            }
        }
        return null;
    }

    public List<CompanyProfile> all() { return profiles; }

    /** Where we looked and whether it exists - shown by /test/profiles/reload. */
    public Map<String, Object> describe() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("resource", resource.getDescription());
        m.put("exists", resource.exists());
        m.put("loaded", profiles.size());
        return m;
    }

    private static String validate(CompanyProfile p) {
        if (p.type == null) return "missing type";
        if (p.company == null && p.urlHost == null) return "needs company or urlHost";
        String t = p.type.toLowerCase();
        if (ATS.contains(t)) {
            if (p.token == null || p.token.trim().isEmpty()) return "ATS profile needs token";
            if (t.equals("workday") && p.token.split("/").length != 3) return "workday token must be tenant/wdN/site";
        } else if (t.equals("json-api")) {
            if (p.api == null || p.api.url == null) return "json-api needs api.url";
            if (p.api.urlField == null && p.api.urlTemplate == null) return "json-api needs api.urlField or api.urlTemplate";
        } else if (t.equals("html")) {
            if (p.html == null || p.html.itemSelector == null) return "html needs html.itemSelector";
        } else {
            return "unknown type '" + p.type + "'";
        }
        CompanyProfile.Filters f = p.filters;
        if (f != null) {
            for (String r : new String[]{f.includeTitleRegex, f.excludeTitleRegex, f.includeUrlRegex, f.excludeUrlRegex, f.locationRegex}) {
                if (r == null || r.isEmpty()) continue;
                try { Pattern.compile(r); } catch (Exception e) { return "bad regex '" + r + "': " + e.getMessage(); }
            }
        }
        return null;
    }
}
