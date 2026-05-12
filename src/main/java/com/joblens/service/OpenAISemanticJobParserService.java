package com.joblens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblens.dto.JobDTO;
import com.joblens.dto.JobPage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class OpenAISemanticJobParserService implements SemanticJobParserService {

    @Autowired
    private OpenAIProviderClient openAIProviderClient;

    @Value("${openai.max-tokens:700}")
    private Integer maxTokens;

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JobDTO> semanticCache = new ConcurrentHashMap<>();

    @Override
    public JobDTO parseJob(JobPage jobPage) {
        if (jobPage == null) {
            return null;
        }

        if (semanticCache.containsKey(jobPage.getUrl())) {
            log.debug("Returning cached semantic job for {}", jobPage.getUrl());
            return semanticCache.get(jobPage.getUrl());
        }

        JobDTO job = createBaseFromPage(jobPage);
        if (!openAIProviderClient.isEnabled()) {
            JobDTO fallback = fallbackParse(jobPage);
            semanticCache.put(jobPage.getUrl(), fallback);
            return fallback;
        }

        try {
            String prompt = buildSemanticPrompt(jobPage);
            String aiResponse = openAIProviderClient.complete(prompt, maxTokens);
            JobDTO enriched = mergeStructuredJob(job, aiResponse);
            semanticCache.put(jobPage.getUrl(), enriched);
            return enriched;
        } catch (Exception e) {
            log.error("AI semantic parsing failed for {}", jobPage.getUrl(), e);
            JobDTO fallback = fallbackParse(jobPage);
            semanticCache.put(jobPage.getUrl(), fallback);
            return fallback;
        }
    }

    private JobDTO createBaseFromPage(JobPage page) {
        return JobDTO.builder()
            .title(page.getTitle())
            .company(page.getCompany())
            .location(page.getLocation())
            .link(page.getUrl())
            .description(page.getDescription())
            .rawContent(page.getRawHtml())
            .skills(page.getSkills())
            .source(page.getUrl())
            .build();
    }

    private String buildSemanticPrompt(JobPage page) {
        String example = "{\n  \"role\": \"Backend Engineer\",\n  \"company\": \"Dozti\",\n  \"minExperience\": 2,\n  \"maxExperience\": 5,\n  \"skills\": [\"Java\", \"Spring Boot\", \"REST APIs\"],\n  \"location\": \"Remote\",\n  \"seniority\": \"Mid-Level\",\n  \"employmentType\": \"Full-Time\"\n}";

        return "You are a semantic job intelligence parser. "
            + "Extract structured job metadata from the full job description and return only valid JSON with these fields: "
            + "role, company, minExperience, maxExperience, skills, location, seniority, employmentType, description, summary. "
            + "Do not include any explanation or markdown. "
            + "If a value cannot be determined, use null or empty values. "
            + "Example output: " + example + "\n\n"
            + "Job page content follows:\n" + page.getFullText();
    }

    private JobDTO mergeStructuredJob(JobDTO base, String aiResponse) {
        try {
            JsonNode parsed = objectMapper.readTree(aiResponse);
            JobDTO.Builder builder = base.toBuilder();

            if (parsed.hasNonNull("role")) {
                builder.title(parsed.get("role").asText(base.getTitle()));
            }
            if (parsed.hasNonNull("company")) {
                builder.company(parsed.get("company").asText(base.getCompany()));
            }
            if (parsed.hasNonNull("location")) {
                builder.location(parsed.get("location").asText(null));
            }
            if (parsed.hasNonNull("minExperience")) {
                builder.minExperience(parsed.get("minExperience").asInt());
            }
            if (parsed.hasNonNull("maxExperience")) {
                builder.maxExperience(parsed.get("maxExperience").asInt());
            }
            if (parsed.hasNonNull("employmentType")) {
                builder.employmentType(parsed.get("employmentType").asText(null));
            }
            if (parsed.hasNonNull("seniority")) {
                builder.seniority(parsed.get("seniority").asText(null));
            }
            if (parsed.hasNonNull("description")) {
                builder.description(parsed.get("description").asText(base.getDescription()));
            }
            if (parsed.hasNonNull("summary")) {
                builder.semanticSummary(parsed.get("summary").asText(null));
            }
            if (parsed.hasNonNull("skills") && parsed.get("skills").isArray()) {
                List<String> parsedSkills = new ArrayList<>();
                parsed.get("skills").forEach(node -> parsedSkills.add(node.asText()));
                builder.skills(parsedSkills);
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Unable to parse AI response, returning base job DTO", e);
            return base;
        }
    }

    private JobDTO fallbackParse(JobPage page) {
        return JobDTO.builder()
            .title(page.getTitle())
            .company(page.getCompany())
            .location(page.getLocation())
            .link(page.getUrl())
            .description(page.getDescription())
            .rawContent(page.getRawHtml())
            .skills(page.getSkills())
            .source(page.getUrl())
            .seniority(page.getSeniority())
            .employmentType(page.getEmploymentType())
            .semanticSummary(buildFallbackSummary(page))
            .build();
    }

    private String buildFallbackSummary(JobPage page) {
        if (page.getDescription() != null && !page.getDescription().isBlank()) {
            return page.getDescription().length() > 220 ? page.getDescription().substring(0, 220) + "..." : page.getDescription();
        }
        return "Job page parsed from " + page.getUrl();
    }
}
