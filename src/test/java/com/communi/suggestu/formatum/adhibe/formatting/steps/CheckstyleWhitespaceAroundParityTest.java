package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Differential tests: the step must find exactly the violations Checkstyle's own WhitespaceAround check reports, and
 * once formatted Checkstyle must be satisfied. Set {@code -DwhitespaceAround.corpus=<dir>} to additionally run over
 * an external source tree.
 */
class CheckstyleWhitespaceAroundParityTest {
    private static final Set<WhitespaceAroundToken> PROJECT_TOKENS = WhitespaceAroundToken.resolve(List.of(
            "ASSIGN", "BAND", "BAND_ASSIGN", "BOR", "BOR_ASSIGN", "BSR", "BSR_ASSIGN", "BXOR", "BXOR_ASSIGN", "COLON",
            "DIV_ASSIGN", "DO_WHILE", "EQUAL", "GE", "GT", "LAMBDA", "LAND", "LCURLY", "LE", "LITERAL_CATCH", "LITERAL_DO",
            "LITERAL_ELSE", "LITERAL_FINALLY", "LITERAL_FOR", "LITERAL_IF", "LITERAL_RETURN", "LITERAL_SWITCH",
            "LITERAL_SYNCHRONIZED", "LITERAL_TRY", "LITERAL_WHILE", "LOR", "LT", "MINUS_ASSIGN", "MOD", "MOD_ASSIGN",
            "NOT_EQUAL", "PLUS_ASSIGN", "QUESTION", "RCURLY", "SL", "SLIST", "SL_ASSIGN", "SR", "SR_ASSIGN", "STAR",
            "STAR_ASSIGN", "LITERAL_ASSERT", "TYPE_EXTENSION_AND"));

    private static final WhitespaceAroundAnalyzer.Options STRICT =
            new WhitespaceAroundAnalyzer.Options(true, true, true, true, true, true, true, false);

    private record Config(String name, Set<WhitespaceAroundToken> tokens, boolean explicit, WhitespaceAroundAnalyzer.Options options) {
    }

    private static List<Config> configs() {
        Set<WhitespaceAroundToken> all = EnumSet.allOf(WhitespaceAroundToken.class);
        return List.of(
                new Config("defaults", WhitespaceAroundToken.DEFAULTS, false, WhitespaceAroundAnalyzer.Options.defaults()),
                new Config("all-tokens", all, true, WhitespaceAroundAnalyzer.Options.defaults()),
                new Config("project", PROJECT_TOKENS, true, WhitespaceAroundAnalyzer.Options.defaults()),
                new Config("all-tokens-strict", all, true, STRICT),
                new Config("defaults-strict", WhitespaceAroundToken.DEFAULTS, false, STRICT));
    }

    @Test
    void kitchenSinkMatchesCheckstyle() throws Exception {
        String source = Files.readString(Paths.get("src/test/resources/whitespace-around/KitchenSink.java.txt"));
        verify(Map.of("KitchenSink", source));
    }

    @Test
    void mutatedKitchenSinkMatchesCheckstyle() throws Exception {
        String source = Files.readString(Paths.get("src/test/resources/whitespace-around/KitchenSink.java.txt"));
        Map<String, String> sources = new LinkedHashMap<>();
        for (int seed = 0; seed < 8; seed++) {
            sources.put("stripped-0.5-" + seed, WhitespaceAroundOracle.stripSpaces(source, seed, 0.5));
        }
        sources.put("stripped-1.0", WhitespaceAroundOracle.stripSpaces(source, 0, 1.0));
        verify(sources);
    }

    @Test
    void projectSourcesMatchCheckstyle() throws Exception {
        verify(readTree(Paths.get("src")));
    }

    @Test
    void mutatedProjectSourcesMatchCheckstyle() throws Exception {
        Map<String, String> sources = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : readTree(Paths.get("src/main")).entrySet()) {
            sources.put(entry.getKey() + "#0.5", WhitespaceAroundOracle.stripSpaces(entry.getValue(), 1, 0.5));
            sources.put(entry.getKey() + "#1.0", WhitespaceAroundOracle.stripSpaces(entry.getValue(), 2, 1.0));
        }
        verify(sources);
    }

    @Test
    void externalCorpusMatchesCheckstyle() throws Exception {
        String corpus = System.getProperty("whitespaceAround.corpus");
        if (corpus == null || corpus.isBlank()) {
            return;
        }
        Map<String, String> sources = readTree(Paths.get(corpus));
        Map<String, String> mutated = new LinkedHashMap<>();
        sources.forEach((name, text) -> {
            mutated.put(name, text);
            mutated.put(name + "#0.5", WhitespaceAroundOracle.stripSpaces(text, name.hashCode(), 0.5));
        });
        verify(mutated);
    }

    private static Map<String, String> readTree(Path root) throws IOException {
        Map<String, String> sources = new LinkedHashMap<>();
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.filter(p -> p.toString().endsWith(".java")).sorted().toList()) {
                sources.put(path.toString(), Files.readString(path, StandardCharsets.UTF_8));
            }
        }
        return sources;
    }

    private static void verify(Map<String, String> sources) throws Exception {
        List<String> problems = new ArrayList<>();
        int compared = 0;
        for (Config config : configs()) {
            try (WhitespaceAroundOracle oracle = new WhitespaceAroundOracle(config.tokens(), config.explicit(), config.options())) {
                for (Map.Entry<String, String> entry : sources.entrySet()) {
                    String source = entry.getValue();
                    Set<String> expected = oracle.check(source);
                    if (expected == null) {
                        continue;
                    }
                    compared++;
                    String label = config.name() + " " + entry.getKey();
                    Set<String> actual = WhitespaceAroundOracle.predict(source, config.tokens(), config.options());
                    if (!expected.equals(actual)) {
                        problems.add(describe(label, source, expected, actual));
                        continue;
                    }
                    String formatted = CheckstyleWhitespaceAroundStep.apply(source, config.tokens(), config.options());
                    Set<String> remaining = oracle.check(formatted);
                    if (remaining != null && !remaining.isEmpty()) {
                        problems.add(label + ": violations remain after formatting: " + remaining.stream().limit(5).toList());
                    }
                    if (!formatted.equals(CheckstyleWhitespaceAroundStep.apply(formatted, config.tokens(), config.options()))) {
                        problems.add(label + ": formatting is not idempotent");
                    }
                }
            }
        }
        assertTrue(compared > 0, "no source could be compared");
        assertEquals(List.of(), problems.stream().limit(15).toList(), problems.size() + " mismatches in total");
    }

    private static String describe(String label, String source, Set<String> expected, Set<String> actual) {
        Set<String> missing = new TreeSet<>(expected);
        missing.removeAll(actual);
        Set<String> extra = new TreeSet<>(actual);
        extra.removeAll(expected);
        String[] lines = source.split("\n", -1);
        return label + ":\n  missing " + render(missing, lines) + "\n  extra   " + render(extra, lines);
    }

    private static String render(Set<String> violations, String[] lines) {
        return violations.stream().limit(5).map(v -> {
            int line = Integer.parseInt(v.substring(0, v.indexOf(':')));
            return v + " | " + lines[line - 1].strip();
        }).collect(Collectors.joining("; ", "[", "]"));
    }
}
