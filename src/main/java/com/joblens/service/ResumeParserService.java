package com.joblens.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.joblens.dto.UserProfile;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.ContentHandler;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ResumeParserService {

    @Value("${resume.file-path:}")
    private String resumeFilePath;

    @Autowired
    private OpenAIProviderClient openAIProviderClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public UserProfile loadUserProfile() {
        if (resumeFilePath == null || resumeFilePath.isBlank()) {
            log.info("No resume.file-path configured, skipping resume profile extraction");
            return null;
        }

        File resumeFile = new File(resumeFilePath);
        if (!resumeFile.exists()) {
            log.warn("Resume file not found at {}", resumeFile.getAbsolutePath());
            return null;
        }

        try {
            String text = extractText(resumeFile);
            if (text == null || text.isBlank()) {
                log.warn("Resume text extraction returned empty content");
                return null;
            }
            return parseResumeText(text);
        } catch (Exception e) {
            log.error("Failed to parse resume file", e);
            return null;
        }
    }

    public UserProfile parseResumeText(String resumeText) {
        if (openAIProviderClient.isEnabled()) {
            try {
                String prompt = buildResumePrompt(resumeText);
                String response = openAIProviderClient.complete(prompt, 600);
                return parseProfile(response);
            } catch (Exception e) {
                log.warn("OpenAI resume parsing failed, falling back to heuristics", e);
            }
        }
        return fallbackProfile(resumeText);
    }

    private String extractText(File file) throws Exception {
        try (InputStream stream = new FileInputStream(file)) {
            ContentHandler handler = new BodyContentHandler(-1);
            Metadata metadata = new Metadata();
            new AutoDetectParser().parse(stream, handler, metadata);
            return handler.toString();
        } catch (TikaException e) {
            throw new IllegalStateException("Unable to parse resume file", e);
        }
    }

    private String buildResumePrompt(String resumeText) {
        return "You are a resume understanding engine. Extract the candidate profile from this resume text and return only JSON with keys: "
            + "skills, experienceYears, preferredRoles, domains, locations, seniority, employmentType. "
            + "If a field is not available, use null or an empty array. "
            + "Resume text:\n" + resumeText;
    }

    private UserProfile parseProfile(String json) {
        try {
            JsonNode root = objectMapper.readTree(json);
            UserProfile.UserProfileBuilder builder = UserProfile.builder();

            if (root.has("skills") && root.get("skills").isArray()) {
                List<String> skills = new ArrayList<>();
                root.get("skills").forEach(node -> skills.add(node.asText()));
                builder.skills(skills);
            }
            if (root.has("experienceYears") && root.get("experienceYears").isNumber()) {
                builder.experienceYears(root.get("experienceYears").asInt());
            }
            if (root.has("preferredRoles") && root.get("preferredRoles").isArray()) {
                List<String> roles = new ArrayList<>();
                root.get("preferredRoles").forEach(node -> roles.add(node.asText()));
                builder.preferredRoles(roles);
            }
            if (root.has("domains") && root.get("domains").isArray()) {
                List<String> domains = new ArrayList<>();
                root.get("domains").forEach(node -> domains.add(node.asText()));
                builder.domains(domains);
            }
            if (root.has("locations") && root.get("locations").isArray()) {
                List<String> locations = new ArrayList<>();
                root.get("locations").forEach(node -> locations.add(node.asText()));
                builder.locations(locations);
            }
            if (root.hasNonNull("seniority")) {
                builder.seniority(root.get("seniority").asText());
            }
            if (root.hasNonNull("employmentType")) {
                builder.employmentType(root.get("employmentType").asText());
            }

            return builder.build();
        } catch (Exception e) {
            log.error("Unable to parse resume profile JSON", e);
            return fallbackProfile(null);
        }
    }

    private UserProfile fallbackProfile(String resumeText) {
        if (resumeText == null || resumeText.isBlank()) {
            return null;
        }

        UserProfile.UserProfileBuilder profileBuilder = UserProfile.builder();
        profileBuilder.skills(extractSkillCandidates(resumeText));
        profileBuilder.experienceYears(extractExperienceYears(resumeText));
        profileBuilder.preferredRoles(extractRoles(resumeText));
        profileBuilder.domains(extractDomains(resumeText));
        profileBuilder.locations(extractLocations(resumeText));
        profileBuilder.seniority(extractSeniority(resumeText));
        profileBuilder.employmentType(extractEmploymentType(resumeText));
        return profileBuilder.build();
    }

    private List<String> extractSkillCandidates(String text) {
        List<String> skills = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        String[] knownSkills = {"java", "spring", "rest", "microservices", "aws", "azure", "docker", "kubernetes", "sql", "python", "react", "node", "hibernate"};
        for (String skill : knownSkills) {
            if (lower.contains(skill) && !skills.contains(skill)) {
                skills.add(skill);
            }
        }
        return skills;
    }

    private Integer extractExperienceYears(String text) {
        if (text == null) {
            return null;
        }
        Pattern pattern = Pattern.compile("(\\d{1,2})\\+?\\s*(years|yrs)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(text);
        if (matcher.find()) {
            try {
                return Integer.valueOf(matcher.group(1));
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    private List<String> extractRoles(String text) {
        List<String> roles = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        String[] rolesToMatch = {"backend", "full stack", "software engineer", "developer", "data engineer", "devops", "architect"};
        for (String role : rolesToMatch) {
            if (lower.contains(role) && !roles.contains(role)) {
                roles.add(role);
            }
        }
        return roles;
    }

    private List<String> extractDomains(String text) {
        List<String> domains = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        String[] domainKeywords = {"finance", "healthcare", "ecommerce", "retail", "saas", "education", "insurance"};
        for (String domain : domainKeywords) {
            if (lower.contains(domain) && !domains.contains(domain)) {
                domains.add(domain);
            }
        }
        return domains;
    }

    private List<String> extractLocations(String text) {
        List<String> locations = new ArrayList<>();
        String lower = text.toLowerCase(Locale.ROOT);
        String[] locationsToMatch = {"remote", "hybrid", "new york", "san francisco", "chennai", "bengaluru", "london", "berlin"};
        for (String location : locationsToMatch) {
            if (lower.contains(location) && !locations.contains(location)) {
                locations.add(location);
            }
        }
        return locations;
    }

    private String extractSeniority(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("senior") || lower.contains("sr.") || lower.contains("lead")) {
            return "Senior";
        }
        if (lower.contains("mid-level") || lower.contains("mid level") || lower.contains("experienced")) {
            return "Mid-Level";
        }
        if (lower.contains("junior") || lower.contains("entry level") || lower.contains("fresher")) {
            return "Junior";
        }
        return null;
    }

    private String extractEmploymentType(String text) {
        if (text == null) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        if (lower.contains("full time") || lower.contains("full-time")) {
            return "Full-Time";
        }
        if (lower.contains("part time") || lower.contains("part-time")) {
            return "Part-Time";
        }
        if (lower.contains("contract")) {
            return "Contract";
        }
        if (lower.contains("intern")) {
            return "Internship";
        }
        return null;
    }
}
