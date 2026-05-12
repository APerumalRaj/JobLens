package com.joblens.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Representation of a fully rendered job page fetched from the web.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JobPage {

    private String url;
    private String title;
    private String company;
    private String location;
    private String experienceText;
    private List<String> skills;
    private String description;
    private String fullText;
    private String rawHtml;
    private String applyLink;
    private String seniority;
    private String employmentType;
    private String sourceHost;
}
