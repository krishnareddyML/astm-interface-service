package com.lis.astm.simulator;

import java.io.*;
import java.nio.file.*;
import java.util.*;

/**
 * Utility class for managing ASTM message templates
 * Provides methods to load, save, and validate message templates
 */
public class MessageTemplateManager {
    
    private static final String TEMPLATE_DIR = "src/main/resources/test-messages/";
    
    /**
     * Load all available message templates from files
     */
    public static Map<String, String> loadAllTemplates() {
        Map<String, String> templates = new HashMap<>();
        
        File templateDir = new File(TEMPLATE_DIR);
        if (!templateDir.exists()) {
            templateDir.mkdirs();
        }
        
        File[] files = templateDir.listFiles((dir, name) -> name.endsWith(".astm"));
        if (files != null) {
            for (File file : files) {
                try {
                    String content = new String(Files.readAllBytes(file.toPath()));
                    String templateName = file.getName().replace(".astm", "").toUpperCase();
                    templates.put(templateName, content);
                } catch (IOException e) {
                    System.err.println("Error loading template: " + file.getName() + " - " + e.getMessage());
                }
            }
        }
        
        return templates;
    }
    
    /**
     * Save a message template to file
     */
    public static void saveTemplate(String templateName, String content) throws IOException {
        File templateFile = new File(TEMPLATE_DIR + templateName.toLowerCase() + ".astm");
        templateFile.getParentFile().mkdirs();
        Files.write(templateFile.toPath(), content.getBytes());
    }
    
    /**
     * Validate ASTM message format
     */
    public static boolean validateAstmMessage(String message) {
        if (message == null || message.trim().isEmpty()) {
            return false;
        }
        
        String[] lines = message.split("\\r\\n|\\r|\\n");
        boolean hasHeader = false;
        boolean hasTerminator = false;
        
        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) continue;
            
            if (line.startsWith("H|")) {
                hasHeader = true;
            } else if (line.startsWith("L|")) {
                hasTerminator = true;
            }
        }
        
        return hasHeader && hasTerminator;
    }
    
    /**
     * List all available template files
     */
    public static List<String> listAvailableTemplates() {
        List<String> templates = new ArrayList<>();
        
        File templateDir = new File(TEMPLATE_DIR);
        if (templateDir.exists()) {
            File[] files = templateDir.listFiles((dir, name) -> name.endsWith(".astm"));
            if (files != null) {
                for (File file : files) {
                    templates.add(file.getName().replace(".astm", ""));
                }
            }
        }
        
        return templates;
    }
    
    /**
     * Create a template from raw ASTM message
     */
    public static String createTemplate(String rawMessage, Map<String, String> variableMapping) {
        String template = rawMessage;
        
        // Replace values with variables based on mapping
        for (Map.Entry<String, String> entry : variableMapping.entrySet()) {
            template = template.replace(entry.getKey(), "${" + entry.getValue() + "}");
        }
        
        return template;
    }
    
    /**
     * Generate variable mapping suggestions for a raw message
     */
    public static Map<String, String> suggestVariables(String rawMessage) {
        Map<String, String> suggestions = new HashMap<>();
        
        // Common patterns to replace with variables
        if (rawMessage.contains("20")) {
            // Timestamp patterns
            String timestampPattern = "\\d{14}"; // YYYYMMDDHHMMSS
            suggestions.put(timestampPattern, "timestamp");
        }
        
        // Patient ID patterns
        if (rawMessage.contains("P") && rawMessage.matches(".*P\\d+.*")) {
            suggestions.put("P\\d+", "patientId");
        }
        
        // Specimen ID patterns
        if (rawMessage.contains("S") && rawMessage.matches(".*S\\d+.*")) {
            suggestions.put("S\\d+", "specimenId");
        }
        
        return suggestions;
    }
    
    public static void main(String[] args) {
        // Demo usage
        System.out.println("Available templates:");
        listAvailableTemplates().forEach(System.out::println);
        
        System.out.println("\nLoaded templates:");
        loadAllTemplates().forEach((name, content) -> {
            System.out.println(name + ": " + content.length() + " characters");
        });
    }
}
