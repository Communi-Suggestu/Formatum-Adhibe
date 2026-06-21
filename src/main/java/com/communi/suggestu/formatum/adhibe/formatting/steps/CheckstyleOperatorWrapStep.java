package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public abstract class CheckstyleOperatorWrapStep extends FormattingStep {
    private static final List<String> SUPPORTED_OPERATORS = List.of(
            "||", "&&", "==", "!=", "<=", ">=", "+", "-", "*", "/", "%", "<", ">", "?", ":"
    );

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

    private String apply(String text) {
        String option = getOption().getOrElse("nl").toLowerCase(Locale.ROOT);
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));

        if ("eol".equals(option)) {
            wrapOperatorsEndOfLine(lines);
        } else {
            wrapOperatorsNewLine(lines);
        }

        return String.join("\n", lines);
    }

    private static void wrapOperatorsNewLine(List<String> lines) {
        for (int i = 0; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isCommentLine(trimmed) || containsLikelyStringOrComment(line)) {
                continue;
            }
            if (trimmed.endsWith("->") || trimmed.contains(" -> ")) {
                continue;
            }

            String matchedOperator = trailingOperator(trimmed);
            if (matchedOperator == null) {
                continue;
            }

            int next = nextNonEmpty(lines, i + 1);
            if (next < 0) {
                continue;
            }

            String nextTrimmed = lines.get(next).trim();
            if (startsWithOperator(nextTrimmed)) {
                continue;
            }

            String withoutOperator = line.substring(0, line.lastIndexOf(matchedOperator)).stripTrailing();
            lines.set(i, withoutOperator);
            String nextLine = lines.get(next);
            String nextIndent = nextLine.substring(0, nextLine.length() - nextLine.stripLeading().length());
            lines.set(next, nextIndent + matchedOperator + " " + nextTrimmed);
        }
    }

    private static void wrapOperatorsEndOfLine(List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || isCommentLine(trimmed) || containsLikelyStringOrComment(line)) {
                continue;
            }
            if (trimmed.startsWith(">") && i > 0 && lines.get(i - 1).trim().endsWith("-")) {
                // Don't split a lambda arrow across lines as "-" and ">".
                continue;
            }

            String operator = leadingOperator(trimmed);
            if (operator == null) {
                continue;
            }

            int previous = previousNonEmpty(lines, i - 1);
            if (previous < 0) {
                continue;
            }

            String previousLine = lines.get(previous).stripTrailing();
            lines.set(previous, previousLine + " " + operator);
            lines.set(i, line.substring(line.indexOf(operator) + operator.length()).stripLeading());
        }
    }

    private static String trailingOperator(String trimmed) {
        for (String operator : SUPPORTED_OPERATORS) {
            if (trimmed.endsWith(operator)) {
                return operator;
            }
        }
        return null;
    }

    private static String leadingOperator(String trimmed) {
        for (String operator : SUPPORTED_OPERATORS) {
            if (trimmed.startsWith(operator + " ") || trimmed.equals(operator)) {
                return operator;
            }
        }
        return null;
    }

    private static boolean startsWithOperator(String trimmed) {
        return leadingOperator(trimmed) != null;
    }

    private static int nextNonEmpty(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (!lines.get(i).trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static int previousNonEmpty(List<String> lines, int from) {
        for (int i = from; i >= 0; i--) {
            if (!lines.get(i).trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean containsLikelyStringOrComment(String line) {
        return line.contains("\"") || line.contains("//") || line.contains("/*") || line.contains("*/");
    }

    private static boolean isCommentLine(String trimmed) {
        return trimmed.startsWith("//")
                || trimmed.startsWith("/*")
                || trimmed.startsWith("*/")
                || trimmed.startsWith("*");
    }
}

