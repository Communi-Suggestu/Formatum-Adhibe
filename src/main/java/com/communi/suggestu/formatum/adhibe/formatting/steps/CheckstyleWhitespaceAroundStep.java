package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public abstract class CheckstyleWhitespaceAroundStep extends FormattingStep {
    @Inject
    public CheckstyleWhitespaceAroundStep() {
    }

    @Input
    public abstract ListProperty<String> getTokens();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        String result = text;
        result = normalizeRelationalOperators(result);
        result = normalizeTernaryOperators(result);
        result = result.replaceAll("(?<![<>=!+\\-*/%&|^])=(?!=)", " = ");
        result = result.replaceAll("(?<![!])!=(?!=)", " != ");
        result = result.replace("==", " == ");
        result = normalizeClassicForConditionComparisons(result);
        result = result.replace("{}", "{ }");
        result = result.replaceAll(" {2,}", " ");
        return result;
    }

    private static String normalizeRelationalOperators(String text) {
        String result = text;
        result = result.replaceAll("\\s*<=\\s*", " <= ");
        result = result.replaceAll("\\s*>=\\s*", " >= ");
        // Keep this narrow to avoid rewriting generic type arguments.
        result = result.replaceAll("([\\w)\\]])\\s*<\\s*(-?\\d)", "$1 < $2");
        result = result.replaceAll("([\\w)\\]])\\s*>\\s*(-?\\d)", "$1 > $2");
        // Handle method/array expression comparisons against identifiers in method bodies.
        result = result.replaceAll("([)\\]])\\s*<\\s*([A-Za-z_$][A-Za-z0-9_$]*)", "$1 < $2");
        result = result.replaceAll("([)\\]])\\s*>\\s*([A-Za-z_$][A-Za-z0-9_$]*)", "$1 > $2");
        return result;
    }

    private static String normalizeTernaryOperators(String text) {
        String[] lines = text.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            if (!lines[i].contains("?")) {
                continue;
            }

            lines[i] = lines[i].replaceAll("\\s*\\?\\s*", " ? ");
            lines[i] = lines[i].replaceAll("(?<!:)\\s*:(?!:)\\s*", " : ");
        }
        return String.join("\n", lines);
    }

    private static String normalizeClassicForConditionComparisons(String text) {
        String[] lines = text.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = normalizeLineForHeaderComparisons(lines[i]);
        }
        return String.join("\n", lines);
    }

    private static String normalizeLineForHeaderComparisons(String line) {
        int scan = 0;
        String result = line;
        while (scan < result.length()) {
            int forIndex = result.indexOf("for", scan);
            if (forIndex < 0) {
                break;
            }
            if (!isKeyword(result, forIndex, "for")) {
                scan = forIndex + 3;
                continue;
            }

            int afterKeyword = forIndex + 3;
            while (afterKeyword < result.length() && Character.isWhitespace(result.charAt(afterKeyword))) {
                afterKeyword++;
            }
            if (afterKeyword >= result.length() || result.charAt(afterKeyword) != '(') {
                scan = forIndex + 3;
                continue;
            }

            int closingParen = findMatchingParen(result, afterKeyword);
            if (closingParen < 0) {
                break;
            }

            String header = result.substring(afterKeyword + 1, closingParen);
            String normalizedHeader = normalizeClassicForHeader(header);
            result = result.substring(0, afterKeyword + 1) + normalizedHeader + result.substring(closingParen);
            scan = afterKeyword + 1 + normalizedHeader.length();
        }
        return result;
    }

    private static String normalizeClassicForHeader(String header) {
        int firstSemicolon = header.indexOf(';');
        if (firstSemicolon < 0) {
            return header;
        }

        int secondSemicolon = header.indexOf(';', firstSemicolon + 1);
        if (secondSemicolon < 0 || header.indexOf(';', secondSemicolon + 1) >= 0) {
            return header;
        }

        String init = header.substring(0, firstSemicolon + 1);
        String condition = header.substring(firstSemicolon + 1, secondSemicolon);
        String update = header.substring(secondSemicolon);

        return init + normalizeComparisonOperators(condition) + update;
    }

    private static String normalizeComparisonOperators(String input) {
        String result = input;
        result = result.replaceAll("\\s*<=\\s*", " <= ");
        result = result.replaceAll("\\s*>=\\s*", " >= ");
        result = result.replaceAll("\\s*==\\s*", " == ");
        result = result.replaceAll("\\s*!=\\s*", " != ");
        result = result.replaceAll("(?<!<)\\s*<\\s*(?![<=])", " < ");
        result = result.replaceAll("(?<!>)\\s*>\\s*(?![>=])", " > ");
        return result;
    }

    private static int findMatchingParen(String text, int openParenIndex) {
        int depth = 0;
        for (int i = openParenIndex; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    private static boolean isKeyword(String text, int start, String keyword) {
        int end = start + keyword.length();
        boolean leftBoundary = start == 0 || !Character.isJavaIdentifierPart(text.charAt(start - 1));
        boolean rightBoundary = end >= text.length() || !Character.isJavaIdentifierPart(text.charAt(end));
        return leftBoundary && rightBoundary;
    }
}


