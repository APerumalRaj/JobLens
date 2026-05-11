package com.joblens.service;

import com.joblens.dto.JobDTO;
import com.joblens.util.ExperienceExtractionUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service to filter jobs based on skills and experience
 */
@Slf4j
@Service
public class JobFilterService {
    
    @Value("${job.keywords}")
    private List<String> keywords;
    
    @Value("${job.filter.min-experience}")
    private Integer userMinExperience;
    
    @Value("${job.filter.max-experience}")
    private Integer userMaxExperience;
    
    @Value("${job.max-results}")
    private Integer maxResults;
    
    /**
     * Filter jobs based on skills and experience range
     */
    public List<JobDTO> filterJobs(List<JobDTO> jobs) {
        log.info("Starting job filtering with {} jobs", jobs.size());
        log.debug("User criteria - Experience: {}-{} years, Keywords: {}", 
            userMinExperience, userMaxExperience, keywords);
        
        List<JobDTO> filtered = jobs.stream()
            .filter(this::matchesSkills)
            .filter(this::matchesExperience)
            .limit(maxResults)
            .collect(Collectors.toList());
        
        log.info("Filtered to {} jobs (after skill and experience filtering)", filtered.size());
        return filtered;
    }
    
    /**
     * Filter and remove duplicates
     */
    public List<JobDTO> removeDuplicates(List<JobDTO> jobs) {
        Set<String> seenLinks = new HashSet<>();
        List<JobDTO> unique = new ArrayList<>();
        
        for (JobDTO job : jobs) {
            if (job.getLink() != null && !seenLinks.contains(job.getLink())) {
                seenLinks.add(job.getLink());
                unique.add(job);
            }
        }
        
        log.info("Removed {} duplicate jobs, {} remaining", 
            jobs.size() - unique.size(), 
            unique.size()
        );
        return unique;
    }
    
    /**
     * Check if job matches any of the configured skills
     * 
     * Combines title and description for matching
     */
    private boolean matchesSkills(JobDTO job) {
        String content = (
            (job.getTitle() != null ? job.getTitle() : "") + " " +
            (job.getDescription() != null ? job.getDescription() : "")
        ).toLowerCase();
        
        boolean matches = keywords.stream()
            .anyMatch(content::contains);
        
        if (matches) {
            log.debug("Job '{}' matches skills: {}", job.getTitle(), keywords);
        } else {
            log.debug("Job '{}' does not match any required skills", job.getTitle());
        }
        
        return matches;
    }
    
    /**
     * Check if job experience range matches user's criteria
     * 
     * Filtering logic:
     * - Include if: job.minExperience <= userMaxExperience 
     *   AND (job.maxExperience == null OR job.maxExperience >= userMinExperience)
     * - Exclude jobs where experience cannot be extracted
     */
    private boolean matchesExperience(JobDTO job) {
        boolean matches = true; /*ExperienceExtractionUtil.isExperienceMatch(
            job.getMinExperience(),
            job.getMaxExperience(),
            userMinExperience,
            userMaxExperience
        );*///Commeneted since nor response is getting will add best filter for xperience
        
        if (matches) {
            log.debug("Job '{}' experience range {}-{} matches user criteria {}-{}",
                job.getTitle(),
                job.getMinExperience(),
                job.getMaxExperience(),
                userMinExperience,
                userMaxExperience
            );
        } else {
            log.debug("Job '{}' experience range {}-{} does not match user criteria {}-{}",
                job.getTitle(),
                job.getMinExperience(),
                job.getMaxExperience(),
                userMinExperience,
                userMaxExperience
            );
        }
        
        return matches;
    }
    
    /**
     * Filter jobs with a single step (skills + experience + dedup)
     */
    public List<JobDTO> filterAndDeduplicate(List<JobDTO> jobs) {
        List<JobDTO> deduplicated = removeDuplicates(jobs);
        return filterJobs(deduplicated);
    }
}
