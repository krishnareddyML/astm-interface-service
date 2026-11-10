package com.lis.astm.simulator;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.List;

/**
 * Loads simulator test cases from a JSON array of {@link TestCase}.
 *
 * <p>The source can be a bundled resource name (for example {@code ortho-vision-test-cases.json}
 * or {@code sysmex-xn-test-cases.json}) or a path to a file on disk, so site-specific captures can
 * be replayed without rebuilding the simulator.
 */
public class MessageTemplateManager {

    public static final String DEFAULT_TEST_CASES = "ortho-vision-test-cases.json";

    public static List<TestCase> loadTestCases() {
        return loadTestCases(DEFAULT_TEST_CASES);
    }

    public static List<TestCase> loadTestCases(String source) {
        ObjectMapper objectMapper = new ObjectMapper();
        Path file = Paths.get(source);
        try {
            if (Files.isRegularFile(file)) {
                try (InputStream is = Files.newInputStream(file)) {
                    return objectMapper.readValue(is, new TypeReference<List<TestCase>>() {});
                }
            }
            String resource = source.startsWith("/") ? source : "/" + source;
            try (InputStream is = MessageTemplateManager.class.getResourceAsStream(resource)) {
                if (is == null) {
                    System.err.println("FATAL ERROR: test case source '" + source + "' is neither a file nor a bundled resource.");
                    return Collections.emptyList();
                }
                return objectMapper.readValue(is, new TypeReference<List<TestCase>>() {});
            }
        } catch (IOException e) {
            System.err.println("Error loading or parsing '" + source + "': " + e.getMessage());
            return Collections.emptyList();
        }
    }
}
