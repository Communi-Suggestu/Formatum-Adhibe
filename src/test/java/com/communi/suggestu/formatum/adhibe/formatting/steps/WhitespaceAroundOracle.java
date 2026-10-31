package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import com.puppycrawl.tools.checkstyle.api.AuditEvent;
import com.puppycrawl.tools.checkstyle.api.AuditListener;
import com.puppycrawl.tools.checkstyle.api.CheckstyleException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Runs Checkstyle's real {@code WhitespaceAround} check as the reference for the re-implementation, and predicts the
 * violations the re-implementation would repair so both can be compared one to one.
 */
final class WhitespaceAroundOracle implements AutoCloseable {
    private final Checker checker;
    private final Path file;
    private final List<String> violations = new ArrayList<>();
    private boolean failed;

    WhitespaceAroundOracle(Set<WhitespaceAroundToken> tokens, boolean explicitTokens, WhitespaceAroundAnalyzer.Options options)
            throws CheckstyleException, IOException {
        DefaultConfiguration check = new DefaultConfiguration("com.puppycrawl.tools.checkstyle.checks.whitespace.WhitespaceAroundCheck");
        if (explicitTokens) {
            check.addProperty("tokens", tokens.stream().map(Enum::name).sorted().collect(Collectors.joining(",")));
        }
        check.addProperty("allowEmptyConstructors", String.valueOf(options.allowEmptyConstructors()));
        check.addProperty("allowEmptyMethods", String.valueOf(options.allowEmptyMethods()));
        check.addProperty("allowEmptyTypes", String.valueOf(options.allowEmptyTypes()));
        check.addProperty("allowEmptyLoops", String.valueOf(options.allowEmptyLoops()));
        check.addProperty("allowEmptyLambdas", String.valueOf(options.allowEmptyLambdas()));
        check.addProperty("allowEmptyCatches", String.valueOf(options.allowEmptyCatches()));
        check.addProperty("allowEmptySwitchBlockStatements", String.valueOf(options.allowEmptySwitchBlockStatements()));
        check.addProperty("ignoreEnhancedForColon", String.valueOf(options.ignoreEnhancedForColon()));

        DefaultConfiguration walker = new DefaultConfiguration("com.puppycrawl.tools.checkstyle.TreeWalker");
        walker.addChild(check);
        DefaultConfiguration root = new DefaultConfiguration("Checker");
        root.addProperty("tabWidth", "1");
        root.addChild(walker);

        checker = new Checker();
        checker.setModuleClassLoader(Checker.class.getClassLoader());
        checker.configure(root);
        checker.addListener(new Collector());
        file = Files.createTempFile("WhitespaceAroundOracle", ".java");
    }

    /** Violations Checkstyle reports as {@code line:column:preceded|followed}, or {@code null} if it cannot parse the source. */
    Set<String> check(String source) {
        violations.clear();
        failed = false;
        try {
            Files.writeString(file, source, StandardCharsets.UTF_8);
            checker.process(List.of(file.toFile()));
        } catch (CheckstyleException | IOException e) {
            return null;
        }
        return failed ? null : new TreeSet<>(violations);
    }

    @Override
    public void close() throws IOException {
        checker.destroy();
        Files.deleteIfExists(file);
    }

    /** The violations the step considers present, in the same format as {@link #check(String)}. */
    static Set<String> predict(String source, Set<WhitespaceAroundToken> tokens, WhitespaceAroundAnalyzer.Options options) {
        List<Integer> lineStarts = new ArrayList<>();
        lineStarts.add(0);
        for (int i = 0; i < source.length(); i++) {
            if (source.charAt(i) == '\n') {
                lineStarts.add(i + 1);
            }
        }
        Set<String> predicted = new TreeSet<>();
        for (WhitespaceAroundAnalyzer.Site site : WhitespaceAroundAnalyzer.analyze(source, options)) {
            if (!tokens.contains(site.token())) {
                continue;
            }
            int line = 0;
            while (line + 1 < lineStarts.size() && lineStarts.get(line + 1) <= site.start()) {
                line++;
            }
            String position = (line + 1) + ":" + (site.start() - lineStarts.get(line) + 1);
            if (!site.skipBefore() && site.start() > 0 && !Character.isWhitespace(source.charAt(site.start() - 1))) {
                predicted.add(position + ":preceded");
            }
            if (!site.skipAfter() && site.end() < source.length() && !Character.isWhitespace(source.charAt(site.end()))) {
                predicted.add(position + ":followed");
            }
        }
        return predicted;
    }

    /**
     * Removes the spaces between neighbouring tokens of the same line with the given probability, wherever doing so
     * keeps the token stream intact. Produces plenty of violations out of well formatted code.
     */
    static String stripSpaces(String source, long seed, double probability) {
        Random random = new Random(seed);
        List<JavaLexer.Token> tokens = JavaLexer.lex(source);
        StringBuilder out = new StringBuilder(source.length());
        int last = 0;
        for (int i = 0; i + 1 < tokens.size(); i++) {
            JavaLexer.Token a = tokens.get(i);
            JavaLexer.Token b = tokens.get(i + 1);
            String gap = source.substring(a.end(), b.start());
            if (gap.isEmpty() || !gap.chars().allMatch(c -> c == ' ') || random.nextDouble() >= probability) {
                continue;
            }
            List<JavaLexer.Token> relexed = JavaLexer.lex(a.text() + b.text());
            if (relexed.size() != 2 || !relexed.get(0).text().equals(a.text()) || !relexed.get(1).text().equals(b.text())) {
                continue;
            }
            out.append(source, last, a.end());
            last = b.start();
        }
        return out.append(source, last, source.length()).toString();
    }

    private final class Collector implements AuditListener {
        @Override
        public void auditStarted(AuditEvent event) {
        }

        @Override
        public void auditFinished(AuditEvent event) {
        }

        @Override
        public void fileStarted(AuditEvent event) {
        }

        @Override
        public void fileFinished(AuditEvent event) {
        }

        @Override
        public void addError(AuditEvent event) {
            String message = event.getMessage();
            String kind = message.contains("not preceded") ? "preceded" : message.contains("not followed") ? "followed" : null;
            if (kind == null) {
                failed = true;
                return;
            }
            violations.add(event.getLine() + ":" + event.getColumn() + ":" + kind);
        }

        @Override
        public void addException(AuditEvent event, Throwable throwable) {
            failed = true;
        }
    }
}
