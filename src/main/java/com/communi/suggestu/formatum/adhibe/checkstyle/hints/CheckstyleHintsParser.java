package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public final class CheckstyleHintsParser {
    public CheckstyleHintsFile parse(Path hintsFile) {
        try {
            String yamlText = Files.readString(hintsFile);
            Yaml yaml = new Yaml();
            Object loaded = yaml.load(yamlText);
            if (!(loaded instanceof Map<?, ?> root)) {
                return new CheckstyleHintsFile(List.of());
            }

            Object hintsRaw = root.get("hints");
            if (!(hintsRaw instanceof List<?> hintEntries)) {
                return new CheckstyleHintsFile(List.of());
            }

            List<CheckstyleHint> hints = new ArrayList<>();
            for (Object entry : hintEntries) {
                if (!(entry instanceof Map<?, ?> hintMap)) {
                    continue;
                }
                hints.add(parseHint(hintMap));
            }
            return new CheckstyleHintsFile(List.copyOf(hints));
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read hints file: " + hintsFile, e);
        }
    }

    private CheckstyleHint parseHint(Map<?, ?> map) {
        String id = asString(map.get("id"), "");
        String find = asString(map.get("find"), "");
        if (find.isBlank()) {
            throw new IllegalArgumentException("Hint 'find' must be present and non-empty.");
        }

        return new CheckstyleHint(
                id,
                asNullableString(map.get("modulePath")),
                asNullableString(map.get("moduleName")),
                asNullableString(map.get("messageContains")),
                find,
                asString(map.get("replace"), ""),
                asBoolean(map.get("multiline"), true),
                asBoolean(map.get("dotall"), false),
                parseMode(asString(map.get("mode"), "SAFE"))
        );
    }

    private static String asString(Object value, String defaultValue) {
        return value == null ? defaultValue : String.valueOf(value);
    }

    private static String asNullableString(Object value) {
        String parsed = asString(value, "").trim();
        return parsed.isEmpty() ? null : parsed;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private static FixMode parseMode(String mode) {
        return FixMode.valueOf(mode.trim().toUpperCase());
    }
}

