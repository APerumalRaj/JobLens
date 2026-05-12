package com.joblens.service;

import com.joblens.dto.JobPage;
import com.joblens.util.ExperienceExtractionUtil;
import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.options.LoadState;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Fetches dynamic job pages using a headless browser and extracts resulting content.
 */
@Slf4j
@Service
public class JobCrawlerService {

    public JobPage fetchJobPage(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }

        log.info("Crawling job page: {}", url);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions().setHeadless(true));
            Page page = browser.newPage();
            page.navigate(url, new Page.NavigateOptions().setWaitUntil(LoadState.NETWORKIDLE).setTimeout(30000));
            page.waitForTimeout(2000);

            String html = page.content();
            Document document = Jsoup.parse(html);

            String title = extractFirstText(document, "h1, h2, title");
            String company = extractFirstMatch(document, "[class*=company], [id*=company], .companyName, .topcard__org-name-link");
            String location = extractFirstMatch(document, "[class*=location], [id*=location], .job-location, .topcard__flavor--bullet");
            String description = extractFirstMatch(document, "[class*=description], [id*=description], .job-description, .description");
            String applyLink = extractApplyLink(document, url);
            String fullText = document.text();
            List<String> skills = extractSkills(document);
            String seniority = inferSeniority(fullText);
            String employmentType = inferEmploymentType(fullText);

            return JobPage.builder()
                .url(url)
                .title(title)
                .company(company)
                .location(location)
                .experienceText(extractExperienceText(fullText))
                .description(description)
                .fullText(fullText)
                .rawHtml(html)
                .applyLink(applyLink)
                .skills(skills)
                .seniority(seniority)
                .employmentType(employmentType)
                .sourceHost(getHost(url))
                .build();
        } catch (Exception e) {
            log.error("Failed to crawl job page {}", url, e);
            return null;
        }
    }

    private String extractFirstText(Document document, String selector) {
        if (document == null) {
            return null;
        }
        Elements elements = document.select(selector);
        return elements.isEmpty() ? null : elements.first().text().trim();
    }

    private String extractFirstMatch(Document document, String selector) {
        if (document == null) {
            return null;
        }
        Elements elements = document.select(selector);
        return elements.stream()
            .map(Element::text)
            .filter(text -> text != null && !text.isBlank())
            .findFirst()
            .orElse(null);
    }

    private String extractApplyLink(Document document, String baseUrl) {
        if (document == null) {
            return null;
        }
        for (Element link : document.select("a[href]")) {
            String href = link.attr("href").trim();
            if (href.isBlank() || href.startsWith("mailto:") || href.startsWith("javascript:")) {
                continue;
            }
            String normalized = href;
            if (!normalized.startsWith("http")) {
                normalized = URI.create(baseUrl).resolve(normalized).toString();
            }
            String lower = normalized.toLowerCase();
            if (lower.contains("apply") || lower.contains("job") || lower.contains("careers")) {
                return normalized;
            }
        }
        return baseUrl;
    }

    private List<String> extractSkills(Document document) {
        if (document == null) {
            return List.of();
        }
        List<String> skills = new ArrayList<>();
        String[] tokenSelectors = {"li", "p", "span", "div"};

        for (String selector : tokenSelectors) {
            for (Element element : document.select(selector)) {
                String text = element.text();
                if (text.length() < 300 && text.matches("(?i).*(java|spring|rest|microservices|docker|kubernetes|aws|azure|sql|python|node|react).*")) {
                    String[] tokens = text.split("[\n,••·•\\|\\(\\)]");
                    for (String token : tokens) {
                        String normalized = token.trim();
                        if (normalized.length() > 2 && normalized.length() < 30 && !skills.contains(normalized)) {
                            skills.add(normalized);
                        }
                    }
                }
            }
        }

        return skills;
    }

    private String inferSeniority(String fullText) {
        if (fullText == null) {
            return null;
        }
        String lower = fullText.toLowerCase();
        if (lower.contains("senior") || lower.contains("sr.") || lower.contains("lead")) {
            return "Senior";
        }
        if (lower.contains("mid-level") || lower.contains("mid level") || lower.contains("experienced")) {
            return "Mid-Level";
        }
        if (lower.contains("junior") || lower.contains("entry level") || lower.contains("fresher")) {
            return "Junior";
        }
        return "Not specified";
    }

    private String inferEmploymentType(String fullText) {
        if (fullText == null) {
            return null;
        }
        String lower = fullText.toLowerCase();
        if (lower.contains("full time") || lower.contains("full-time")) {
            return "Full-Time";
        }
        if (lower.contains("part time") || lower.contains("part-time")) {
            return "Part-Time";
        }
        if (lower.contains("contract")) {
            return "Contract";
        }
        if (lower.contains("intern")) {
            return "Internship";
        }
        return null;
    }

    private String extractExperienceText(String fullText) {
        if (fullText == null) {
            return null;
        }
        var experience = ExperienceExtractionUtil.extractExperience(fullText);
        if (experience.get("min") == null && experience.get("max") == null) {
            return null;
        }
        Integer min = experience.get("min");
        Integer max = experience.get("max");
        if (min != null && max != null) {
            return min + "-" + max + " years";
        }
        if (min != null) {
            return min + "+ years";
        }
        return null;
    }

    private String getHost(String url) {
        try {
            return URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }
}
