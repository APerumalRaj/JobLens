package com.joblens.service;

import com.joblens.dto.JobDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.internet.MimeMessage;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Service to send email summaries of filtered jobs
 */
@Slf4j
@Service
public class EmailService {
    
    @Autowired
    private JavaMailSender mailSender;
    
    @Value("${job.email.send-to}")
    private String recipientEmail;
    
    @Value("${job.email.from}")
    private String senderEmail;
    
    @Value("${job.email.subject}")
    private String emailSubject;
    
    /**
     * Send filtered jobs as HTML email
     */
    public void sendJobsEmail(List<JobDTO> jobs) {
        if (jobs == null || jobs.isEmpty()) {
            log.info("No jobs to send in email");
            sendNoJobsEmail();
            return;
        }
        
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            
            helper.setFrom(senderEmail);
            helper.setTo(recipientEmail);
            helper.setSubject(emailSubject + " (" + jobs.size() + " opportunities)");
            
            String htmlContent = buildHtmlContent(jobs);
            helper.setText(htmlContent, true);
            
            mailSender.send(message);
            log.info("Email sent successfully with {} jobs to {}", jobs.size(), recipientEmail);
            
        } catch (Exception e) {
            log.error("Error sending email", e);
        }
    }
    
    /**
     * Send notification when no jobs are found
     */
    public void sendNoJobsEmail() {
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(senderEmail);
            message.setTo(recipientEmail);
            message.setSubject(emailSubject + " (No matches)");
            message.setText("No job opportunities matching your criteria were found in the last 24 hours.");
            
            mailSender.send(message);
            log.info("No-jobs notification sent to {}", recipientEmail);
            
        } catch (Exception e) {
            log.error("Error sending no-jobs email", e);
        }
    }
    
    /**
     * Build HTML email content
     */
    private String buildHtmlContent(List<JobDTO> jobs) {
        StringBuilder html = new StringBuilder();
        
        html.append("<!DOCTYPE html>");
        html.append("<html>");
        html.append("<head>");
        html.append("<style>");
        html.append("body { font-family: Arial, sans-serif; margin: 20px; background-color: #f5f5f5; }");
        html.append(".container { background-color: white; padding: 20px; border-radius: 8px; }");
        html.append(".header { color: #333; border-bottom: 3px solid #0066cc; padding-bottom: 10px; }");
        html.append(".job-card { margin: 15px 0; padding: 15px; background-color: #f9f9f9; border-left: 4px solid #0066cc; }");
        html.append(".job-title { font-size: 18px; font-weight: bold; color: #0066cc; }");
        html.append(".job-meta { color: #666; font-size: 14px; margin-top: 5px; }");
        html.append(".job-link { display: inline-block; margin-top: 10px; padding: 8px 15px; background-color: #0066cc; color: white; text-decoration: none; border-radius: 4px; }");
        html.append(".job-link:hover { background-color: #0052a3; }");
        html.append(".experience { display: inline-block; background-color: #e6f2ff; color: #0066cc; padding: 3px 8px; border-radius: 3px; font-size: 12px; margin-right: 10px; }");
        html.append(".footer { margin-top: 30px; padding-top: 20px; border-top: 1px solid #ddd; color: #999; font-size: 12px; }");
        html.append("</style>");
        html.append("</head>");
        html.append("<body>");
        html.append("<div class=\"container\">");
        
        // Header
        html.append("<div class=\"header\">");
        html.append("<h1>🎯 JobLens - Top Job Opportunities</h1>");
        html.append("<p>Found <strong>").append(jobs.size()).append("</strong> matching opportunities in the last 24 hours</p>");
        html.append("<p><em>").append(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))).append("</em></p>");
        html.append("</div>");
        
        // Job cards
        int jobNumber = 1;
        for (JobDTO job : jobs) {
            html.append("<div class=\"job-card\">");
            html.append("<div class=\"job-title\">").append(jobNumber).append(". ").append(escapeHtml(job.getTitle())).append("</div>");
            
            html.append("<div class=\"job-meta\">");
            html.append("<strong>Company:</strong> ").append(escapeHtml(job.getCompany())).append("<br>");
            html.append("<strong>Experience:</strong> <span class=\"experience\">").append(escapeHtml(job.getExperienceRange())).append("</span><br>");
            if (job.getRelevanceScore() != null) {
                html.append("<strong>Relevance:</strong> <span class=\"experience\">").append(job.getRelevanceScore()).append("% match</span><br>");
            }
            if (job.getLocation() != null && !job.getLocation().isBlank()) {
                html.append("<strong>Location:</strong> ").append(escapeHtml(job.getLocation())).append("<br>");
            }
            if (job.getSeniority() != null && !job.getSeniority().isBlank()) {
                html.append("<strong>Seniority:</strong> ").append(escapeHtml(job.getSeniority())).append("<br>");
            }
            html.append("</div>");
            
            if (job.getSemanticSummary() != null && !job.getSemanticSummary().isEmpty()) {
                html.append("<p style=\"color: #555; font-size: 13px; margin: 10px 0; font-style: italic;\">")
                    .append(escapeHtml(job.getSemanticSummary()))
                    .append("</p>");
            } else if (job.getDescription() != null && !job.getDescription().isEmpty()) {
                html.append("<p style=\"color: #555; font-size: 13px; margin: 10px 0;\">").append(escapeHtml(job.getShortDescription())).append("</p>");
            }

            if (job.getMatchReasons() != null && !job.getMatchReasons().isEmpty()) {
                html.append("<p style=\"color: #444; font-size: 12px; margin: 10px 0;\"><strong>Reasons:</strong> ")
                    .append(escapeHtml(String.join(", ", job.getMatchReasons())))
                    .append("</p>");
            }

            if (job.getMissingSkills() != null && !job.getMissingSkills().isEmpty()) {
                html.append("<p style=\"color: #777; font-size: 12px; margin: 10px 0;\"><strong>Missing skills:</strong> ")
                    .append(escapeHtml(String.join(", ", job.getMissingSkills())))
                    .append("</p>");
            }

            if (job.getLink() != null && !job.getLink().isEmpty()) {
                html.append("<a href=\"").append(escapeHtml(job.getLink())).append("\" class=\"job-link\" target=\"_blank\">View & Apply →</a>");
            }
            
            html.append("</div>");
            jobNumber++;
        }
        
        // Footer
        html.append("<div class=\"footer\">");
        html.append("<p>This email was generated by JobLens. Your configuration filters jobs by keywords and experience range.</p>");
        html.append("<p>Next check: In 1 hour</p>");
        html.append("</div>");
        
        html.append("</div>");
        html.append("</body>");
        html.append("</html>");
        
        return html.toString();
    }
    
    /**
     * Escape HTML special characters
     */
    private String escapeHtml(String text) {
        if (text == null) return "";
        return text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
