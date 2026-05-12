package com.joblens;

import com.google.api.services.gmail.model.Message;
import com.joblens.dto.JobDTO;
import com.joblens.dto.JobPage;
import com.joblens.dto.UserProfile;
import com.joblens.service.EmailService;
import com.joblens.service.GmailService;
import com.joblens.service.JobCrawlerService;
import com.joblens.service.JobExtractionService;
import com.joblens.service.JobFilterService;
import com.joblens.service.JobMatchingService;
import com.joblens.service.ResumeParserService;
import com.joblens.service.SemanticJobParserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Main Spring Boot application for JobLens
 * 
 * Automatically fetches emails from Gmail, extracts job opportunities,
 * filters them based on skills and experience, and sends a summary email.
 */
@Slf4j
@SpringBootApplication
@EnableScheduling
public class JobLensApplication {
    
    @Autowired
    private GmailService gmailService;
    
    @Autowired
    private JobExtractionService jobExtractionService;
    
    @Autowired
    private JobFilterService jobFilterService;
    
    @Autowired
    private JobCrawlerService jobCrawlerService;

    @Autowired
    private SemanticJobParserService semanticJobParserService;

    @Autowired
    private ResumeParserService resumeParserService;

    @Autowired
    private JobMatchingService jobMatchingService;
    
    @Autowired
    private EmailService emailService;
    
    public static void main(String[] args) {
        SpringApplication.run(JobLensApplication.class, args);
    }
    
    /**
     * Scheduled task to process job opportunities
     * Runs every hour as configured in application.yml
     */
    @Scheduled(cron = "${scheduling.email-fetcher.cron}")
    public void processJobOpportunities() {
        log.info("=== Starting scheduled job processing ===");

        try {
            // Step 1: Fetch recent emails from Gmail
            log.info("Step 1: Fetching emails from Gmail...");
            List<Message> emailMessages = gmailService.fetchRecentJobEmails();

            if (emailMessages.isEmpty()) {
                log.info("No emails found matching criteria");
                emailService.sendNoJobsEmail();
                return;
            }

            // Step 2: Extract job details from emails and candidate links
            log.info("Step 2: Extracting job details from {} emails...", emailMessages.size());
            List<JobDTO> extractedJobs = jobExtractionService.extractJobsFromMessages(emailMessages);

            if (extractedJobs.isEmpty()) {
                log.info("No jobs could be extracted from emails");
                emailService.sendNoJobsEmail();
                return;
            }

            // Step 3: Normalize and deduplicate entries
            log.info("Step 3: Removing duplicate jobs...");
            List<JobDTO> uniqueJobs = jobFilterService.removeDuplicates(extractedJobs);

            if (uniqueJobs.isEmpty()) {
                log.info("No unique jobs found after deduplication");
                emailService.sendNoJobsEmail();
                return;
            }

            // Step 4: Crawl job URLs and resolve full descriptions
            log.info("Step 4: Crawling {} job pages for full content...", uniqueJobs.size());
            List<JobDTO> enrichedJobs = uniqueJobs.stream()
                .map(this::enrichJobWithSemanticData)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

            if (enrichedJobs.isEmpty()) {
                log.info("No enriched jobs are available after crawling");
                emailService.sendNoJobsEmail();
                return;
            }

            // Step 5: Load user resume profile if available
            log.info("Step 5: Loading resume profile for semantic matching...");
            UserProfile userProfile = resumeParserService.loadUserProfile();

            // Step 6: Rank jobs semantically
            log.info("Step 6: Ranking jobs by semantic relevance...");
            List<JobDTO> rankedJobs = jobMatchingService.rankJobs(enrichedJobs, userProfile);

            if (rankedJobs.isEmpty()) {
                log.info("No jobs matched the semantic relevance criteria");
                emailService.sendNoJobsEmail();
                return;
            }

            // Step 7: Send ranked summary email
            log.info("Step 7: Sending summary email with {} jobs...", rankedJobs.size());
            emailService.sendJobsEmail(rankedJobs);

            log.info("=== Job processing completed successfully ===");
            logSummary(emailMessages.size(), extractedJobs.size(), uniqueJobs.size(), rankedJobs.size());

        } catch (Exception e) {
            log.error("Error during job processing", e);
        }
    }

    private JobDTO enrichJobWithSemanticData(JobDTO job) {
        if (job == null || job.getLink() == null) {
            return job;
        }

        try {
            JobPage page = jobCrawlerService.fetchJobPage(job.getLink());
            if (page == null) {
                return job;
            }

            JobDTO parsed = semanticJobParserService.parseJob(page);
            parsed.setRawContent(job.getRawContent());
            parsed.setSource(job.getSource());
            parsed.setLink(job.getLink());
            if (parsed.getTitle() == null || parsed.getTitle().isBlank()) {
                parsed.setTitle(job.getTitle());
            }
            return parsed;
        } catch (Exception e) {
            log.warn("Failed to enrich job {}", job.getLink(), e);
            return job;
        }
    }
    
    /**
     * Log processing summary
     */
    private void logSummary(int emailsFound, int jobsExtracted, int uniqueJobs, int filteredJobs) {
        log.info("Processing Summary:");
        log.info("  Emails found: {}", emailsFound);
        log.info("  Jobs extracted: {}", jobsExtracted);
        log.info("  Unique jobs: {}", uniqueJobs);
        log.info("  Jobs after filtering: {}", filteredJobs);
        log.info("  Filtered out: {} jobs", emailsFound - filteredJobs);
    }
}
