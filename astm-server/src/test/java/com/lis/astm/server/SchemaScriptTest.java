package com.lis.astm.server;

import com.lis.astm.server.model.OrderMessage;
import com.lis.astm.server.model.ServerMessage;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Keeps db/astm-schema.sql in step with the JDBC repositories: every table the repositories
 * use is created by the script, every column they read or write is defined in it, and every
 * status the code can store is allowed by its CHECK constraint.
 */
class SchemaScriptTest {

    private static final Path SCRIPT = Paths.get("src", "main", "resources", "db", "astm-schema.sql");
    private static final Path REPOSITORIES = Paths.get("src", "main", "java", "com", "lis", "astm", "server", "repository");

    private static final Pattern TABLE = Pattern.compile("(?:FROM|INTO|UPDATE)\\s+(\\w+)");
    private static final Pattern COLUMN = Pattern.compile("rs\\.get\\w+\\(\"(\\w+)\"\\)");

    @Test
    void schemaScriptCreatesEverythingTheRepositoriesUse() throws IOException {
        String script = new String(Files.readAllBytes(SCRIPT), StandardCharsets.UTF_8);
        String ddl = script.substring(0, script.indexOf("OPTIONAL 1")); // ignore the commented extras

        Set<String> required = new LinkedHashSet<>();
        for (String repo : new String[]{"ServerMessageRepository.java", "OrderMessageRepository.java"}) {
            String code = new String(Files.readAllBytes(REPOSITORIES.resolve(repo)), StandardCharsets.UTF_8);
            Matcher t = TABLE.matcher(code);
            while (t.find()) required.add("CREATE TABLE dbo." + t.group(1) + " (");
            // columns from the row mapper only (other rs.get calls read query aliases such as "count")
            String mapper = code.substring(code.indexOf("rowMapper = "), code.indexOf(".build();"));
            Matcher c = COLUMN.matcher(mapper);
            while (c.find()) required.add(c.group(1) + " ");
        }
        for (ServerMessage.Status s : ServerMessage.Status.values()) required.add("'" + s.name() + "'");
        for (OrderMessage.Status s : OrderMessage.Status.values()) required.add("'" + s.name() + "'");

        List<String> missing = new ArrayList<>();
        for (String value : required) {
            if (!ddl.contains(value)) missing.add(value.trim());
        }
        assertTrue(missing.isEmpty(), "db/astm-schema.sql is missing: " + missing);
    }
}
