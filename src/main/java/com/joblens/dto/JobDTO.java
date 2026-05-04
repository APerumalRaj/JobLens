package com.joblens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Data Transfer Object for Job opportunities
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobDTO {
    
    private String title;
    private String company;
    private String link;
    private String description;
    private Integer minExperience;  // null if not specified
    private Integer maxExperience;  // null if open-ended (e.g., "2+ years")
    private String source;          // Email subject or source info
    private String rawContent;      // Full email content for reference
    
    /**
     * Get human-readable experience range
     */
    public String getExperienceRange() {
        if (minExperience == null && maxExperience == null) {
            return "Not specified";
        }
        if (minExperience != null && maxExperience == null) {
            return minExperience + "+ years";
        }
        if (minExperience != null && maxExperience != null) {
            return minExperience + "-" + maxExperience + " years";
        }
        return "Unknown";
    }
    
    /**
     * Get shortened description (first 150 chars)
     */
    public String getShortDescription() {
        if (description == null || description.isEmpty()) {
            return "No description";
        }
        return description.length() > 150 
            ? description.substring(0, 150) + "..." 
            : description;
    }
}
