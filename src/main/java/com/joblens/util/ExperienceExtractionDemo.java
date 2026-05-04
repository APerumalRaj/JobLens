package com.joblens.util;

import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Map;

/**
 * Demo utility to test experience extraction patterns
 * Run with: --spring.boot.run.arguments="test-extraction"
 */
@Component
public class ExperienceExtractionDemo implements CommandLineRunner {
    
    @Override
    public void run(String... args) throws Exception {
        if (args.length > 0 && "test-extraction".equals(args[0])) {
            runExtractionTests();
            System.exit(0);
        }
    }
    
    private void runExtractionTests() {
        System.out.println("\n===== Experience Extraction Test Suite =====\n");
        
        String[] testCases = {
            "3-5 years of experience required",
            "2+ years with Spring Boot",
            "0-2 yrs in Java backend development",
            "minimum 1 year experience needed",
            "We need 4 years of hands-on experience",
            "preferred: 3 years with REST APIs",
            "5+ years working with databases",
            "no experience requirement"
        };
        
        for (String testCase : testCases) {
            testExtractionCase(testCase);
        }
        
        System.out.println("\n===== Test Suite Complete =====\n");
    }
    
    private void testExtractionCase(String text) {
        Map<String, Integer> result = ExperienceExtractionUtil.extractExperience(text);
        Integer min = result.get("min");
        Integer max = result.get("max");
        
        String minStr = min != null ? min.toString() : "N/A";
        String maxStr = max != null ? max.toString() : "N/A";
        
        System.out.println("Input:  " + text);
        System.out.println("Output: min=" + minStr + ", max=" + maxStr);
        
        if (min != null || max != null) {
            System.out.println("Range:  " + formatRange(min, max));
        }
        System.out.println();
    }
    
    private String formatRange(Integer min, Integer max) {
        if (min == null && max == null) {
            return "Unknown";
        }
        if (min != null && max == null) {
            return min + "+ years";
        }
        if (min != null && max != null) {
            return min + "-" + max + " years";
        }
        return "Up to " + max + " years";
    }
}
