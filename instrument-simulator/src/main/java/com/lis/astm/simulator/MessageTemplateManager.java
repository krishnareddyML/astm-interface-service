package com.lis.astm.simulator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

/**
 * A utility class for loading dynamic test cases from a JSON configuration file.
 * This replaces the old file-based template system with a more flexible JSON approach.
 */
public class MessageTemplateManager {
    
    // The path to the JSON configuration within the classpath.
   // private static final String TEST_CASES_RESOURCE_PATH = "/test-cases.json";
    
   private static final String TEST_CASES_RESOURCE_PATH = "/ortho-vision-test-cases.json";
    
    /**
     * Loads the list of test cases from the test-cases.json file in the resources.
     * @return A list of TestCase objects, or an empty list if the file cannot be loaded.
     */
    public static List<TestCase> loadTestCases() {
        ObjectMapper objectMapper = new ObjectMapper();
        
        try (InputStream is = MessageTemplateManager.class.getResourceAsStream(TEST_CASES_RESOURCE_PATH)) {
            
            if (is == null) {
                System.err.println("FATAL ERROR: Could not find 'test-cases.json' in the resources folder.");
                return Collections.emptyList();
            }
            
            // Use Jackson ObjectMapper to parse the JSON array into a List of TestCase objects.
            return objectMapper.readValue(is, new TypeReference<List<TestCase>>(){});
            
        } catch (IOException e) {
            System.err.println("Error loading or parsing 'test-cases.json': " + e.getMessage());
            return Collections.emptyList();
        }
    }
}