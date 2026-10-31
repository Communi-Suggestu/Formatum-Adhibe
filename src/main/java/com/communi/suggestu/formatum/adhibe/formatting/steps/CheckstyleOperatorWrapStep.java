package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CheckstyleOperatorWrapStep extends FormattingStep {
    private static final Set<String> DEFAULT_TOKENS = Set.of(
            "QUESTION", "COLON", "EQUAL", "NOT_EQUAL", "DIV", "PLUS", "MINUS", "STAR", "MOD",
            "SR", "BSR", "GE", "GT", "SL", "LE", "LT", "BXOR", "BOR", "LOR", "BAND", "LAND",
            "TYPE_EXTENSION_AND", "LITERAL_INSTANCEOF");

    @Inject
    public CheckstyleOperatorWrapStep() {
    }

    @Input
    public abstract Property<String> getOption();

    @Input
    public abstract ListProperty<String> getTokens();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private record Edit(int start, int end, String replacement) {}

    private String apply(String text) {
        List<String> configured = getTokens().getOrElse(List.of());
        Set<String> selected = configured.isEmpty() ? DEFAULT_TOKENS : Set.copyOf(configured);
        Map<Integer, String> operators = new HashMap<>();
        for (var site : WhitespaceAroundAnalyzer.analyze(text, WhitespaceAroundAnalyzer.Options.defaults())) {
            if (selected.contains(site.token().name()) && !site.skipBefore() && !site.skipAfter()
                    && (DEFAULT_TOKENS.contains(site.token().name()) || site.token().name().endsWith("ASSIGN")
                    || site.token() == WhitespaceAroundToken.LAMBDA)) {
                operators.put(site.start(), text.substring(site.start(), site.end()));
            }
        }
        List<JavaLexer.Token> tokens = JavaLexer.lex(text);
        List<Edit> edits = new ArrayList<>();
        boolean endOfLine = "eol".equalsIgnoreCase(getOption().getOrElse("nl"));
        for (int i = 1; i + 1 < tokens.size(); i++) {
            var token = tokens.get(i);
            String operator = operators.get(token.start());
            if (token.isKeyword("instanceof") && selected.contains("LITERAL_INSTANCEOF")
                    || token.isOp("::") && selected.contains("METHOD_REF")) {
                operator = token.text();
            }
            if (operator == null) {
                continue;
            }
            var previous = tokens.get(i - 1);
            var next = tokens.get(i + 1);
            String before = text.substring(previous.end(), token.start());
            String after = text.substring(token.end(), next.start());
            if (!endOfLine && horizontal(before) && after.contains("\n")) {
                edits.add(new Edit(previous.end(), token.end(), ""));
                edits.add(new Edit(next.start(), next.start(), operator + " "));
            } else if (endOfLine && before.contains("\n") && horizontal(after)) {
                edits.add(new Edit(previous.end(), previous.end(), " " + operator));
                edits.add(new Edit(token.start(), next.start(), ""));
            }
        }
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        StringBuilder result = new StringBuilder(text);
        for (Edit edit : edits) {
            result.replace(edit.start(), edit.end(), edit.replacement());
        }
        return result.toString();
    }

    private static boolean horizontal(String text) {
        return text.chars().allMatch(c -> c == ' ' || c == '\t');
    }
}
