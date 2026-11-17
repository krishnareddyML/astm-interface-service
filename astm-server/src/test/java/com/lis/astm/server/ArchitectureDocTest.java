package com.lis.astm.server;

import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.model.ServerMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps docs/architecture.html (the single overview of this repository) in step with the code.
 * Fails when an instrument, port, driver, queue, exchange or routing key configured in any
 * application-*.yml, a driver class, or a status of either table is not mentioned on the page.
 * When this fails: update docs/architecture.html (see the rule in CLAUDE.md), do not weaken the test.
 */
class ArchitectureDocTest {

    private static final Path DOC = Paths.get("..", "docs", "architecture.html");
    private static final Path RESOURCES = Paths.get("src", "main", "resources");
    private static final Path DRIVERS = Paths.get("src", "main", "java", "com", "lis", "astm", "server", "driver", "impl");

    private static final List<String> INSTRUMENT_KEYS = Arrays.asList(
            "name", "port", "driverClassName", "orderQueueName", "resultQueueName",
            "exchangeName", "resultsRoutingKey", "qcRoutingKey", "queryRoutingKey");
    private static final Pattern KEY_VALUE = Pattern.compile("^\\s*-?\\s*(\\w+):\\s*(\\S.*?)\\s*(#.*)?$");
    private static final Pattern PLACEHOLDER = Pattern.compile("\\$\\{[^:}]+:([^}]*)}");

    @Test
    void architecturePageMentionsEverythingConfigured() throws IOException {
        assertTrue(Files.exists(DOC), "Missing " + DOC.toAbsolutePath());
        String doc = new String(Files.readAllBytes(DOC), StandardCharsets.UTF_8);

        Set<String> required = new LinkedHashSet<>();
        try (DirectoryStream<Path> profiles = Files.newDirectoryStream(RESOURCES, "application-*.yml")) {
            for (Path profile : profiles) {
                required.addAll(instrumentValues(Files.readAllLines(profile, StandardCharsets.UTF_8)));
            }
        }
        try (DirectoryStream<Path> drivers = Files.newDirectoryStream(DRIVERS, "*.java")) {
            for (Path driver : drivers) {
                required.add(driver.getFileName().toString().replace(".java", ""));
            }
        }
        for (ServerMessage.Status s : ServerMessage.Status.values()) required.add(s.name());
        for (OrderMessage.Status s : OrderMessage.Status.values()) required.add(s.name());

        List<String> missing = new ArrayList<>();
        for (String value : required) {
            if (!doc.contains(value)) missing.add(value);
        }
        assertTrue(missing.isEmpty(), "docs/architecture.html does not mention: " + missing
                + ". Update the page in the same change (rule in CLAUDE.md).");
    }

    /** Values of the instrument keys inside lis.instruments entries (blocks starting with "- name:"). */
    private static List<String> instrumentValues(List<String> lines) {
        List<String> values = new ArrayList<>();
        boolean inInstrument = false;
        int itemIndent = -1;
        for (String line : lines) {
            if (line.trim().isEmpty() || line.trim().startsWith("#")) continue;
            int indent = line.length() - line.replaceAll("^\\s+", "").length();
            if (line.trim().startsWith("- name:")) {
                inInstrument = true;
                itemIndent = indent;
            } else if (inInstrument && indent <= itemIndent) {
                inInstrument = false;
            }
            if (!inInstrument) continue;
            Matcher m = KEY_VALUE.matcher(line);
            if (m.matches() && INSTRUMENT_KEYS.contains(m.group(1))) {
                String value = resolve(m.group(2));
                if (m.group(1).equals("driverClassName")) value = value.substring(value.lastIndexOf('.') + 1);
                if (!value.isEmpty()) values.add(value);
            }
        }
        return values;
    }

    private static String resolve(String value) {
        Matcher m = PLACEHOLDER.matcher(value);
        return m.find() ? m.group(1).trim() : value.trim();
    }
}
