package com.example.careerMonitor.monitoringService.scraper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Single place for all HTTP. Browser-like headers, redirects, gzip, size cap,
 * retry with backoff on timeouts / 429 / 5xx, and a basic SSRF guard.
 */
@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

    public static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) "
                    + "Chrome/124.0.0.0 Safari/537.36";

    private static final int MAX_ATTEMPTS = 3;
    private static final int MAX_BODY_BYTES = 8 * 1024 * 1024;

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    public FetchedPage get(String url) throws IOException {
        return send(url, null, null);
    }

    public FetchedPage getJson(String url) throws IOException {
        return send(url, null, Map.of("Accept", "application/json, text/plain, */*"));
    }

    public FetchedPage postJson(String url, String jsonBody) throws IOException {
        return send(url, jsonBody, Map.of("Accept", "application/json", "Content-Type", "application/json"));
    }

    /** Generic request for company-specific JSON APIs: GET when postBody is null, else POST with the given headers. */
    public FetchedPage request(String url, String postBody, Map<String, String> headers) throws IOException {
        Map<String, String> h = new java.util.HashMap<>();
        h.put("Accept", "application/json, text/plain, */*");
        if (postBody != null) h.put("Content-Type", "application/json");
        if (headers != null) h.putAll(headers);
        return send(url, postBody, h);
    }

    private FetchedPage send(String url, String postBody, Map<String, String> extraHeaders) throws IOException {
        URI uri = URI.create(url);
        assertPublicHost(uri);

        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                HttpRequest.Builder rb = HttpRequest.newBuilder(uri)
                        .timeout(Duration.ofSeconds(25))
                        .header("User-Agent", USER_AGENT)
                        .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                        .header("Accept-Language", "en-US,en;q=0.9");
                if (extraHeaders != null) extraHeaders.forEach(rb::setHeader);
                if (postBody != null) rb.POST(HttpRequest.BodyPublishers.ofString(postBody));
                else rb.GET();

                HttpResponse<InputStream> resp = client.send(rb.build(), HttpResponse.BodyHandlers.ofInputStream());
                int status = resp.statusCode();

                if (status == 429 || status >= 500) {
                    resp.body().close();
                    long wait = retryAfterMillis(resp, attempt);
                    last = new FetchException("HTTP " + status + " from " + url, status);
                    if (attempt < MAX_ATTEMPTS) { sleep(wait); continue; }
                    throw last;
                }
                if (status >= 400) {
                    resp.body().close();
                    throw new FetchException("HTTP " + status + " from " + url
                            + (status == 403 ? " (likely bot protection / WAF)" : ""), status);
                }

                String ct = resp.headers().firstValue("Content-Type").orElse("");
                String enc = resp.headers().firstValue("Content-Encoding").orElse("");
                InputStream in = resp.body();
                if (enc.toLowerCase().contains("gzip")) in = new GZIPInputStream(in);
                String body = readLimited(in, charsetOf(ct));
                return new FetchedPage(resp.uri().toString(), status, ct, body);

            } catch (FetchException e) {
                throw e;
            } catch (IOException e) {
                last = e;
                if (attempt < MAX_ATTEMPTS) sleep(1000L * attempt);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while fetching " + url, e);
            }
        }
        throw last != null ? last : new IOException("Failed to fetch " + url);
    }

    private static long retryAfterMillis(HttpResponse<?> resp, int attempt) {
        long fallback = 1500L * attempt;
        try {
            String ra = resp.headers().firstValue("Retry-After").orElse(null);
            if (ra != null) return Math.min(10_000L, Long.parseLong(ra.trim()) * 1000L);
        } catch (NumberFormatException ignored) { }
        return fallback;
    }

    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }

    private static Charset charsetOf(String contentType) {
        try {
            int i = contentType.toLowerCase().indexOf("charset=");
            if (i >= 0) {
                String cs = contentType.substring(i + 8).split("[;\\s]")[0].replace("\"", "");
                return Charset.forName(cs);
            }
        } catch (Exception ignored) { }
        return StandardCharsets.UTF_8;
    }

    private static String readLimited(InputStream in, Charset cs) throws IOException {
        try (InputStream is = in) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = is.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (out.size() > MAX_BODY_BYTES) break; // truncate, still parseable
            }
            return new String(out.toByteArray(), cs);
        }
    }

    /** Refuse loopback / private / link-local targets (admin-supplied URLs only, but cheap insurance). */
    private static void assertPublicHost(URI uri) throws IOException {
        String scheme = uri.getScheme();
        if (scheme == null || !(scheme.equals("http") || scheme.equals("https")))
            throw new IOException("Unsupported URL scheme: " + uri);
        String host = uri.getHost();
        if (host == null) throw new IOException("Invalid URL: " + uri);
        for (InetAddress a : InetAddress.getAllByName(host)) {
            if (a.isAnyLocalAddress() || a.isLoopbackAddress() || a.isSiteLocalAddress() || a.isLinkLocalAddress())
                throw new IOException("Refusing to fetch non-public address: " + host);
        }
    }
}
