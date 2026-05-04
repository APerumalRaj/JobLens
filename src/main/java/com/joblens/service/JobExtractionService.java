package com.joblens.service;

import com.joblens.dto.JobDTO;
import com.joblens.util.ExperienceExtractionUtil;
import com.google.api.services.gmail.model.Message;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Service to extract job details from emails
 */
@Slf4j
@Service
public class JobExtractionService {
    
    @Autowired
    private GmailService gmailService;
    
    /**
     * Extract job details from email message
     */
    public JobDTO extractJobFromEmail(Message message) {
        try {
            Message fullMessage = gmailService.getFullMessage(message.getId());
            
            String subject = gmailService.getHeaderValue(fullMessage, "Subject");
            String from = gmailService.getHeaderValue(fullMessage, "From");
            String body = gmailService.extractMessageBody(fullMessage);
            
            log.debug("Processing email - Subject: {}, From: {}", subject, from);
            
            // Parse HTML content
            Document doc = Jsoup.parse(body);
            
            // Extract text content
            String textContent = doc.text();
            
            // Extract job title (usually in subject or first prominent text)
            String jobTitle = extractJobTitle(subject, doc);
            
            // Extract company name
            String company = extractCompanyName(doc, from);
            
            // Extract apply link
            String applyLink = extractApplyLink(doc);
            
            // Extract experience range
            Map<String, Integer> experience = ExperienceExtractionUtil.extractExperience(textContent);
            
            // Build JobDTO
            JobDTO job = JobDTO.builder()
                .title(jobTitle)
                .company(company)
                .link(applyLink)
                .description(extractDescription(doc, textContent))
                .minExperience(experience.get("min"))
                .maxExperience(experience.get("max"))
                .source(subject)
                .rawContent(body)
                .build();
            
            log.debug("Extracted job: {} at {} - Experience: {}-{}", 
                job.getTitle(), 
                job.getCompany(),
                job.getMinExperience(),
                job.getMaxExperience()
            );
            
            return job;
            
        } catch (Exception e) {
            log.error("Error extracting job from email", e);
            return null;
        }
    }
    
    /**
     * Extract job title from subject or document
     */
    private String extractJobTitle(String subject, Document doc) {
        if (subject != null && !subject.isEmpty()) {
            // Remove common prefixes like "FW:", "RE:"
            String title = subject.replaceAll("(?i)^(fwd?|re):\\s*", "");
            return title;
        }
        
        // Fallback to first heading or prominent text
        Elements headings = doc.select("h1, h2, h3");
        if (!headings.isEmpty()) {
            return headings.first().text();
        }
        
        return "Job Opportunity";
    }
    
    /**
     * Extract company name from email
     */
    private String extractCompanyName(Document doc, String from) {
        // Try to extract from sender email domain
        if (from != null && from.contains("<")) {
            try {
                String email = from.substring(from.indexOf("<") + 1, from.indexOf(">"));
                String domain = email.substring(email.indexOf("@") + 1, email.indexOf("."));
                return domain.substring(0, 1).toUpperCase() + domain.substring(1);
            } catch (Exception e) {
                // Fallback
            }
        }
        
        // Try to find company name in document
        Elements elements = doc.select("strong, b, [style*=bold]");
        for (Element el : elements) {
            String text = el.text();
            if (text.length() > 2 && text.length() < 50) {
                return text;
            }
        }
        
        return "Unknown Company";
    }
    
    /**
     * Extract apply link from email
     */
    private String extractApplyLink(Document doc) {
        // Look for links containing job-related keywords
        Elements links = doc.select("a[href]");
        
        for (Element link : links) {
            String href = link.attr("href");
            String text = link.text().toLowerCase();
            
            if (href != null && !href.isEmpty()) {
                // Check if link text or URL contains job keywords
                if (text.contains("job") || text.contains("apply") || 
                    text.contains("career") || text.contains("opportunity") ||
                    href.toLowerCase().contains("job") || 
                    href.toLowerCase().contains("apply")) {
                    
                    // Ensure it's a valid URL
                    if (href.startsWith("http://") || href.startsWith("https://") || href.startsWith("www")) {
                        return href;
                    }
                }
            }
        }
        
        // Fallback: return first non-empty href that looks like a URL
        for (Element link : links) {
            String href = link.attr("href");
            if (href != null && (href.startsWith("http") || href.startsWith("www"))) {
                return href;
            }
        }
        
        return null;
    }
    
    /**
     * Extract job description from document
     */
    private String extractDescription(Document doc, String textContent) {
        // Try to extract description from job posting content
        Elements descElements = doc.select("p, div[class*=description], div[class*=job]");
        
        StringBuilder description = new StringBuilder();
        for (Element el : descElements) {
            String text = el.text();
            if (text.length() > 20) {
                description.append(text).append(" ");
                if (description.length() > 300) {
                    break;
                }
            }
        }
        
        if (description.length() > 0) {
            return description.toString();
        }
        
        // Fallback to first 300 chars of text content
        return textContent.length() > 300 
            ? textContent.substring(0, 300) 
            : textContent;
    }
    
    /**
     * Extract multiple jobs from a list of messages
     */
    public List<JobDTO> extractJobsFromMessages(List<Message> messages) {
        List<JobDTO> jobs = new ArrayList<>();
        
        for (Message message : messages) {
            try {
                JobDTO job = extractJobFromEmail(message);
                if (job != null && job.getLink() != null) {
                    jobs.add(job);
                }
            } catch (Exception e) {
                log.error("Error processing message", e);
            }
        }
        
        log.info("Extracted {} jobs from {} messages", jobs.size(), messages.size());
        return jobs;
    }
}
