package com.joblens.service;

import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Extracts candidate job links from email HTML or page content.
 */
@Slf4j
@Service
public class JobLinkExtractorService {

    private static final Set<String> JOB_HOSTS = Set.of(
        "linkedin.com",
        "naukri.com",
        "foundit.com",
        "indeed.com",
        "monster.com",
        "glassdoor.com",
        "angel.co",
        "dice.com",
        "ziprecruiter.com"
    );

    public List<String> extractCandidateJobUrls(String html, String sourceContext) {
        if (html == null || html.isBlank()) {
            return List.of();
        }

        Document document = Jsoup.parse(html);
        Elements links = document.select("a[href]");
        Set<String> candidates = new HashSet<>();

        for (Element anchor : links) {
            String href = anchor.attr("href").trim();
            String text = anchor.text().toLowerCase();

            if (href.isBlank() || href.startsWith("mailto:") || href.startsWith("javascript:")) {
                continue;
            }

            String normalized = normalizeUrl(href);
            if (normalized == null || normalized.isBlank()) {
                continue;
            }

            if (isProbableJobLink(normalized, text, sourceContext)) {
                candidates.add(normalized);
            }
        }

        log.debug("Found {} candidate job URLs", candidates.size());
        return new ArrayList<>(candidates);
    }

    private String normalizeUrl(String href) {
        try {
            if (href.contains("redirect?")) {
                int targetIndex = href.indexOf("url=");
                if (targetIndex > 0) {
                    String extracted = href.substring(targetIndex + 4);
                    return normalizeUrl(URLDecoder.decode(extracted, StandardCharsets.UTF_8));
                }
            }

            URI uri = new URI(href);
            String scheme = uri.getScheme();
            if (scheme == null) {
                uri = new URI("https://" + href);
            }

            String query = uri.getQuery();
            if (query == null || query.isBlank()) {
                return uri.toString();
            }

            String cleanedQuery = cleanTrackingQuery(query);
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), cleanedQuery, null).toString();
        } catch (Exception e) {
            log.debug("Unable to normalize URL {}: {}", href, e.getMessage());
            return href;
        }
    }

    private String cleanTrackingQuery(String query) {
        String[] pairs = query.split("&");
        StringBuilder builder = new StringBuilder();
        for (String pair : pairs) {
            String key = pair.split("=", 2)[0].toLowerCase();
            if (key.startsWith("utm_") || key.equals("fbclid") || key.equals("gclid") || key.equals("trk") || key.equals("referrer")) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(pair);
        }
        return builder.toString().isBlank() ? null : builder.toString();
    }

    private boolean isProbableJobLink(String url, String linkText, String sourceContext) {
        String normalizedUrl = url.toLowerCase();
        String normalizedText = linkText == null ? "" : linkText.toLowerCase();
        String normalizedSource = sourceContext == null ? "" : sourceContext.toLowerCase();

        if (normalizedUrl.contains("linkedin.com/jobs") || normalizedUrl.contains("naukri.com")
            || normalizedUrl.contains("foundit.com") || normalizedUrl.contains("indeed.com")
            || normalizedUrl.contains("/apply") || normalizedUrl.contains("/job/")
            || normalizedUrl.contains("/careers") || normalizedUrl.contains("/jobs/")) {
            return true;
        }

        if (JOB_HOSTS.stream().anyMatch(normalizedUrl::contains)) {
            return true;
        }

        if (normalizedText.contains("apply") || normalizedText.contains("job") || normalizedText.contains("career")
            || normalizedText.contains("opening") || normalizedText.contains("position")) {
            return true;
        }

        if (normalizedSource.contains("linkedin") || normalizedSource.contains("naukri")
            || normalizedSource.contains("indeed") || normalizedSource.contains("foundit")) {
            return normalizedUrl.startsWith("http");
        }

        return false;
    }
}
