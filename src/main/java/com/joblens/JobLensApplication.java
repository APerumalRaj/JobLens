package com.joblens;

import com.google.api.services.gmail.model.Message;
import com.joblens.dto.JobDTO;
import com.joblens.service.EmailService;
import com.joblens.service.GmailService;
import com.joblens.service.JobExtractionService;
import com.joblens.service.JobFilterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import java.util.List;

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
            
            // Step 2: Extract job details from emails
            log.info("Step 2: Extracting job details from {} emails...", emailMessages.size());
            List<JobDTO> extractedJobs = jobExtractionService.extractJobsFromMessages(emailMessages);
            
            if (extractedJobs.isEmpty()) {
                log.info("No jobs could be extracted from emails");
                emailService.sendNoJobsEmail();
                return;
            }
            
            // Step 3: Remove duplicates
            log.info("Step 3: Removing duplicate jobs...");
            List<JobDTO> uniqueJobs = jobFilterService.removeDuplicates(extractedJobs);
            
            // Step 4: Apply skill and experience filtering
            log.info("Step 4: Applying skill and experience filters...");
            List<JobDTO> filteredJobs = jobFilterService.filterJobs(uniqueJobs);
            
            if (filteredJobs.isEmpty()) {
                log.info("No jobs matched the filtering criteria");
                emailService.sendNoJobsEmail();
                return;
            }
            
            // Step 5: Send summary email
            log.info("Step 5: Sending summary email with {} jobs...", filteredJobs.size());
            emailService.sendJobsEmail(filteredJobs);
            
            log.info("=== Job processing completed successfully ===");
            logSummary(emailMessages.size(), extractedJobs.size(), uniqueJobs.size(), filteredJobs.size());
            
        } catch (Exception e) {
            log.error("Error during job processing", e);
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
