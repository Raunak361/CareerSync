package com.example.careerMonitor.monitoringService.scraper;

import java.net.URI;
import java.util.*;

/** URL normalisation so the same job is never stored twice under different URLs. */
public final class UrlUtils {

    private UrlUtils() {}

    private static final Set<String> TRACKING_PARAMS = new HashSet<>(Arrays.asList(
            "gclid", "fbclid", "msclkid", "mc_cid", "mc_eid", "ref", "referrer", "source", "src",
            "gh_src", "lever-source", "lever-origin", "trk", "trackingid", "sessionid", "jsessionid",
            "_ga", "_gl", "cmpid", "campaign", "iis", "iisn"));

    /** Returns a canonical form of the URL, or null if it is not a usable http(s) URL. */
    public static String canonicalize(String raw) {
        if (raw == null) return null;
        raw = raw.trim();
        if (raw.isEmpty()) return null;
        try {
            URI u = new URI(raw);
            String scheme = u.getScheme();
            if (scheme == null || !(scheme.equalsIgnoreCase("http") || scheme.equalsIgnoreCase("https"))) return null;
            String host = u.getHost();
            if (host == null) return null;
            host = host.toLowerCase();
            int port = u.getPort();
            if ((port == 80 && scheme.equalsIgnoreCase("http")) || (port == 443 && scheme.equalsIgnoreCase("https"))) port = -1;

            String path = u.getRawPath() == null ? "" : u.getRawPath();
            while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
            if (path.equals("/")) path = "";

            // keep functional query params (job ids often live there), drop tracking ones, sort for stability
            List<String> kept = new ArrayList<>();
            String q = u.getRawQuery();
            if (q != null && !q.isEmpty()) {
                for (String p : q.split("&")) {
                    if (p.isEmpty()) continue;
                    String key = p.contains("=") ? p.substring(0, p.indexOf('=')) : p;
                    String lk = key.toLowerCase();
                    if (lk.startsWith("utm_") || TRACKING_PARAMS.contains(lk)) continue;
                    kept.add(p);
                }
                Collections.sort(kept);
            }
            StringBuilder sb = new StringBuilder(scheme.toLowerCase()).append("://").append(host);
            if (port != -1) sb.append(':').append(port);
            sb.append(path);
            if (!kept.isEmpty()) sb.append('?').append(String.join("&", kept));
            return sb.toString(); // fragment intentionally dropped
        } catch (Exception e) {
            return null;
        }
    }

    public static String hostOf(String url) {
        try { return new URI(url).getHost(); } catch (Exception e) { return null; }
    }

    /** Resolve a possibly-relative link against a base URL; null if not resolvable. */
    public static String resolve(String base, String link) {
        if (link == null || link.trim().isEmpty()) return null;
        try {
            if (base == null || base.isEmpty()) return new URI(link.trim()).toString();
            return new URI(base).resolve(link.trim()).toString();
        } catch (Exception e) {
            return null;
        }
    }
}
