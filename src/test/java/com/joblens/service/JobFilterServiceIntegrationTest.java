package com.joblens.service;

import com.joblens.dto.JobDTO;
import com.joblens.util.ExperienceExtractionUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests for JobLens services
 */
@SpringBootTest
class JobFilterServiceIntegrationTest {
    
    @Autowired
    private JobFilterService jobFilterService;
    
    private List<JobDTO> testJobs;
    
    @BeforeEach
    void setUp() {
        // Create test jobs
        testJobs = Arrays.asList(
            createTestJob("Senior Java Developer", "TechCorp", 
                "We need a senior Java developer with 4-6 years of experience in Spring Boot", 4, 6),
            
            createTestJob("Backend Engineer", "StartupXYZ",
                "Junior backend role, 1-2 years required with REST API experience", 1, 2),
            
            createTestJob("DevOps Engineer", "CloudInc",
                "Looking for DevOps with 3+ years of Kubernetes and Docker", 3, null),
            
            createTestJob("Frontend Developer", "WebAgency",
                "React developer needed, 2-3 years JavaScript experience", 2, 3),
            
            createTestJob("Data Scientist", "DataCorp",
                "5-7 years with Python and machine learning", 5, 7)
        );
    }
    
    @Test
    void testSkillFiltering() {
        List<JobDTO> filtered = jobFilterService.filterJobs(testJobs);
        
        // Should match: Java Developer, Backend Engineer, DevOps Engineer
        assertTrue(filtered.stream().anyMatch(j -> j.getTitle().contains("Java")));
        assertTrue(filtered.stream().anyMatch(j -> j.getTitle().contains("Backend")));
        
        // Should not match: Frontend, Data Science
        assertFalse(filtered.stream().anyMatch(j -> j.getTitle().contains("Frontend")));
        assertFalse(filtered.stream().anyMatch(j -> j.getTitle().contains("Data")));
    }
    
    @Test
    void testExperienceFiltering() {
        // User criteria: 1-3 years
        List<JobDTO> filtered = jobFilterService.filterJobs(testJobs);
        
        for (JobDTO job : filtered) {
            // All filtered jobs should match experience criteria
            if (job.getMinExperience() != null && job.getMaxExperience() != null) {
                assertTrue(job.getMinExperience() <= 3, "Job requires too much experience: " + job.getTitle());
                assertTrue(job.getMaxExperience() >= 1, "Job max experience less than user min: " + job.getTitle());
            }
        }
    }
    
    @Test
    void testDeduplication() {
        // Create duplicate jobs
        List<JobDTO> jobsWithDuplicates = Arrays.asList(
            createTestJob("Java Developer", "Company1", "description", 1, 3),
            createTestJob("Java Developer", "Company1", "same description", 1, 3),  // Duplicate link
            createTestJob("Python Dev", "Company2", "different", 1, 3)
        );
        
        List<JobDTO> deduplicated = jobFilterService.removeDuplicates(jobsWithDuplicates);
        
        // Should have 2 unique jobs (duplicates removed)
        assertEquals(2, deduplicated.size());
    }
    
    @Test
    void testEmptyJobList() {
        List<JobDTO> filtered = jobFilterService.filterJobs(Arrays.asList());
        assertTrue(filtered.isEmpty());
    }
    
    private JobDTO createTestJob(String title, String company, String description, 
                                  Integer minExp, Integer maxExp) {
        return JobDTO.builder()
            .title(title)
            .company(company)
            .link("https://example.com/job/" + title.replace(" ", "-").toLowerCase())
            .description(description)
            .minExperience(minExp)
            .maxExperience(maxExp)
            .source("Test Email")
            .build();
    }
}

/**
 * Unit tests for experience extraction utility
 */
class ExperienceExtractionUtilTest {
    
    @Test
    void testRangePattern() {
        Map<String, Integer> result = ExperienceExtractionUtil.extractExperience(
            "We need 3-5 years of experience"
        );
        assertEquals(3, result.get("min"));
        assertEquals(5, result.get("max"));
    }
    
    @Test
    void testPlusPattern() {
        Map<String, Integer> result = ExperienceExtractionUtil.extractExperience(
            "2+ years required"
        );
        assertEquals(2, result.get("min"));
        assertNull(result.get("max"));
    }
    
    @Test
    void testMinimumPattern() {
        Map<String, Integer> result = ExperienceExtractionUtil.extractExperience(
            "Minimum 1 year of experience"
        );
        assertEquals(1, result.get("min"));
        assertNull(result.get("max"));
    }
    
    @Test
    void testSingleValuePattern() {
        Map<String, Integer> result = ExperienceExtractionUtil.extractExperience(
            "5 years experience required"
        );
        assertEquals(5, result.get("min"));
        assertEquals(5, result.get("max"));
    }
    
    @Test
    void testNoPattern() {
        Map<String, Integer> result = ExperienceExtractionUtil.extractExperience(
            "No specific experience mentioned"
        );
        assertNull(result.get("min"));
        assertNull(result.get("max"));
    }

    @Test
    void testFresherPattern() {
        Map<String, Integer> result = ExperienceExtractionUtil.extractExperience(
            "Hiring freshers for a trainee role"
        );
        assertEquals(0, result.get("min"));
        assertEquals(1, result.get("max"));
    }

    @Test
    void testExperiencedPattern() {
        Map<String, Integer> result = ExperienceExtractionUtil.extractExperience(
            "Experienced developers preferred"
        );
        assertEquals(0, result.get("min"));
        assertNull(result.get("max"));
    }
    
    @Test
    void testExperienceMatching() {
        // Job: 3-5 years, User: 1-3 years
        // Should NOT match (job min=3 > user max=3)
        assertFalse(ExperienceExtractionUtil.isExperienceMatch(3, 5, 1, 3));
        
        // Job: 2-4 years, User: 1-3 years
        // Should match (overlap exists)
        assertTrue(ExperienceExtractionUtil.isExperienceMatch(2, 4, 1, 3));
        
        // Job: 2+ years, User: 1-3 years
        // Should match
        assertTrue(ExperienceExtractionUtil.isExperienceMatch(2, null, 1, 3));
        
        // Job: experience not specified
        // Should NOT match (as per requirements)
        assertFalse(ExperienceExtractionUtil.isExperienceMatch(null, null, 1, 3));
    }
}
