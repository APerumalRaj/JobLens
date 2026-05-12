package com.joblens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Represents the parsed candidate profile derived from a resume.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private List<String> skills;
    private Integer experienceYears;
    private List<String> preferredRoles;
    private List<String> domains;
    private List<String> locations;
    private String seniority;
    private String employmentType;
}
