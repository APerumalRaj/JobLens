package com.joblens.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class to extract experience range from text using regex patterns
 */
@Slf4j
@UtilityClass
public class ExperienceExtractionUtil {
    
    // Regex patterns for various experience formats
    private static final Pattern RANGE_PATTERN = Pattern.compile(
        "\\b(\\d{1,2})\\s*[-–]\\s*(\\d{1,2})\\s*(?:years?|yrs?|y\\.?)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern MINIMUM_PATTERN = Pattern.compile(
        "(?:minimum|min|at\\s+least)\\s+(?:of\\s+)?(\\d{1,2})\\s*(?:years?|yrs?|y\\.?)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PLUS_PATTERN = Pattern.compile(
        "(\\d{1,2})\\s*\\+\\s*(?:years?|yrs?|y\\.?)",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern SINGLE_VALUE_PATTERN = Pattern.compile(
        "(?:^|\\s)(\\d{1,2})\\s*(?:years?|yrs?|y\\.?)(?:\\s|$|[.,;])",
        Pattern.CASE_INSENSITIVE
    );
    
    private static final Pattern PREFERRED_PATTERN = Pattern.compile(
        "(?:preferred|nice\\s+to\\s+have|ideal).*?(\\d{1,2})\\s*(?:years?|yrs?|y\\.?)",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern FRESHER_PATTERN = Pattern.compile(
        "\b(?:fresher|freshers|entry\\s*level|junior)\b",
        Pattern.CASE_INSENSITIVE
    );

    private static final Pattern EXPERIENCED_PATTERN = Pattern.compile(
        "\bexperienced\b",
        Pattern.CASE_INSENSITIVE
    );
    
    /**
     * Extract experience range from text
     * 
     * @param text The text to extract experience from
     * @return Map with keys "min" and "max" (null if not found)
     */
    public static Map<String, Integer> extractExperience(String text) {
        Map<String, Integer> result = new HashMap<>();
        result.put("min", null);
        result.put("max", null);
        
        if (text == null || text.trim().isEmpty()) {
            log.debug("Empty text provided for experience extraction");
            return result;
        }
        
        String processedText = text.toLowerCase();
        
        // Pattern 1: "X-Y years" (range)
        Matcher rangeMatcher = RANGE_PATTERN.matcher(processedText);
        if (rangeMatcher.find()) {
            result.put("min", Integer.parseInt(rangeMatcher.group(1)));
            result.put("max", Integer.parseInt(rangeMatcher.group(2)));
            log.debug("Found range pattern: {}-{}", result.get("min"), result.get("max"));
            return result;
        }
        
        // Pattern 2: "X+ years" (open-ended minimum)
        Matcher plusMatcher = PLUS_PATTERN.matcher(processedText);
        if (plusMatcher.find()) {
            result.put("min", Integer.parseInt(plusMatcher.group(1)));
            result.put("max", null);  // open-ended
            log.debug("Found plus pattern: {}+", result.get("min"));
            return result;
        }
        
        // Pattern 3: "minimum X years" 
        Matcher minimumMatcher = MINIMUM_PATTERN.matcher(processedText);
        if (minimumMatcher.find()) {
            result.put("min", Integer.parseInt(minimumMatcher.group(1)));
            result.put("max", null);
            log.debug("Found minimum pattern: {}", result.get("min"));
            return result;
        }

        // Pattern 4: "fresher", "freshers", "entry level", or "junior"
        Matcher fresherMatcher = FRESHER_PATTERN.matcher(processedText);
        if (fresherMatcher.find()) {
            result.put("min", 0);
            result.put("max", 1);
            log.debug("Found fresher pattern");
            return result;
        }

        // Pattern 5: generic "experienced" hints
        Matcher experiencedMatcher = EXPERIENCED_PATTERN.matcher(processedText);
        if (experiencedMatcher.find()) {
            result.put("min", 0);
            result.put("max", null);
            log.debug("Found experienced pattern");
            return result;
        }

        // Pattern 6: "X years experience" or standalone "X years"
        Matcher singleMatcher = SINGLE_VALUE_PATTERN.matcher(processedText);
        if (singleMatcher.find()) {
            int years = Integer.parseInt(singleMatcher.group(1));
            result.put("min", years);
            result.put("max", years);
            log.debug("Found single value pattern: {}", years);
            return result;
        }

        // Pattern 7: "preferred X years" (fallback to single value)
        Matcher preferredMatcher = PREFERRED_PATTERN.matcher(processedText);
        if (preferredMatcher.find()) {
            int years = Integer.parseInt(preferredMatcher.group(1));
            result.put("min", years);
            result.put("max", years);
            log.debug("Found preferred pattern: {}", years);
            return result;
        }
        
        log.debug("No experience pattern found in text");
        return result;
    }
    
    /**
     * Check if experience range matches user's criteria
     * 
     * @param jobMinExp Job minimum experience (can be null)
     * @param jobMaxExp Job maximum experience (can be null for open-ended)
     * @param userMinExp User minimum experience requirement
     * @param userMaxExp User maximum experience requirement
     * @return true if job matches user's experience range
     */
    public static boolean isExperienceMatch(
            Integer jobMinExp, 
            Integer jobMaxExp, 
            Integer userMinExp, 
            Integer userMaxExp) {
        
        // If job experience cannot be extracted, consider it a non-match
        if (jobMinExp == null && jobMaxExp == null) {
            return false;
        }
        
        // If only min is available
        if (jobMinExp != null && jobMaxExp == null) {
            // Job requires X+ years
            // Match if user's max >= job's min
            return userMaxExp >= jobMinExp;
        }
        
        // If both are available
        if (jobMinExp != null && jobMaxExp != null) {
            // Job requires X-Y years
            // Match if: user can handle the max AND job can accept user's min
            return jobMinExp <= userMaxExp && jobMaxExp >= userMinExp;
        }
        
        // Only max is available (unusual, but handle it)
        return jobMaxExp >= userMinExp;
    }
}
