package com.joblens.service;

import com.joblens.dto.JobDTO;
import com.joblens.dto.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * A semantic matching engine that scores jobs against a candidate profile.
 */
@Slf4j
@Service
public class JobMatchingService {

    @Value("${job.max-results:10}")
    private Integer maxResults;

    public List<JobDTO> rankJobs(List<JobDTO> jobs, UserProfile profile) {
        if (jobs == null || jobs.isEmpty()) {
            return List.of();
        }

        for (JobDTO job : jobs) {
            int score = 0;
            List<String> reasons = new ArrayList<>();
            List<String> missingSkills = new ArrayList<>();

            if (profile != null && profile.getSkills() != null && job.getSkills() != null) {
                Set<String> candidateSkills = normalizeWords(profile.getSkills());
                Set<String> jobSkills = normalizeWords(job.getSkills());
                Set<String> overlap = new HashSet<>(candidateSkills);
                overlap.retainAll(jobSkills);

                score += Math.min(overlap.size() * 15, 40);
                if (!overlap.isEmpty()) {
                    reasons.add("Strong skills overlap: " + String.join(", ", overlap));
                }

                jobSkills.stream()
                    .filter(skill -> !candidateSkills.contains(skill))
                    .forEach(missingSkills::add);
            }

            if (profile != null && profile.getExperienceYears() != null) {
                int expScore = experienceScore(profile.getExperienceYears(), job);
                score += expScore;
                if (expScore >= 15) {
                    reasons.add("Experience aligns with target profile");
                }
            }

            if (profile != null && profile.getPreferredRoles() != null && job.getTitle() != null) {
                Set<String> roles = normalizeWords(profile.getPreferredRoles());
                String title = job.getTitle().toLowerCase(Locale.ROOT);
                long matches = roles.stream().filter(title::contains).count();
                if (matches > 0) {
                    score += 15;
                    reasons.add("Role alignment detected");
                }
            }

            if (profile != null && profile.getLocations() != null && job.getLocation() != null) {
                Set<String> locations = normalizeWords(profile.getLocations());
                String jobLocation = job.getLocation().toLowerCase(Locale.ROOT);
                if (locations.stream().anyMatch(jobLocation::contains)) {
                    score += 10;
                    reasons.add("Location preference matches");
                }
            }

            if (profile != null && profile.getDomains() != null && job.getDescription() != null) {
                Set<String> domains = normalizeWords(profile.getDomains());
                String description = job.getDescription().toLowerCase(Locale.ROOT);
                boolean domainMatch = domains.stream().anyMatch(description::contains);
                if (domainMatch) {
                    score += 10;
                    reasons.add("Domain relevance matches candidate interests");
                }
            }

            if (job.getSeniority() != null && profile != null && profile.getSeniority() != null) {
                if (job.getSeniority().equalsIgnoreCase(profile.getSeniority())) {
                    score += 10;
                    reasons.add("Seniority alignment");
                }
            }

            job.setRelevanceScore(Math.min(score, 100));
            job.setMatchReasons(reasons);
            job.setMissingSkills(missingSkills);
            if (job.getSemanticSummary() == null) {
                job.setSemanticSummary(job.getDescription() != null ? job.getDescription() : "No summary available.");
            }
        }

        List<JobDTO> sorted = jobs.stream()
            .filter(job -> job.getRelevanceScore() != null && job.getRelevanceScore() > 0)
            .sorted(Comparator.comparingInt(JobDTO::getRelevanceScore).reversed())
            .limit(maxResults)
            .collect(Collectors.toList());

        if (sorted.isEmpty()) {
            sorted = new ArrayList<>(jobs);
        }

        log.info("Scored {} jobs and selected top {} results", jobs.size(), sorted.size());
        return sorted;
    }

    private int experienceScore(Integer profileYears, JobDTO job) {
        if (job.getMinExperience() == null && job.getMaxExperience() == null) {
            return 5;
        }
        int score = 0;
        if (job.getMinExperience() != null && profileYears >= job.getMinExperience()) {
            score += 10;
        }
        if (job.getMaxExperience() != null && profileYears <= job.getMaxExperience()) {
            score += 10;
        }
        return score;
    }

    private Set<String> normalizeWords(List<String> values) {
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            normalized.add(value.trim().toLowerCase(Locale.ROOT));
        }
        return normalized;
    }
}
