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
        result = normalizeShiftOperatorsInExpressions(result);
        result = normalizeRelationalOperators(result);
        result = normalizeTernaryOperators(result);
        result = result.replaceAll("(?<![<>=!+\\-*/%&|^])=(?!=)", " = ");
        result = result.replaceAll("(?<![<>=!+\\-*/%&|^])\\s*=\\s*\\n", " =\n");
        result = result.replaceAll("(?<![!])!=(?!=)", " != ");
        result = result.replace("==", " == ");
        result = normalizeClassicForConditionComparisons(result);
        result = result.replace("{}", "{ }");
        result = result.replaceAll(" {2,}", " ");
        return result;
    }

    private static String normalizeShiftOperatorsInExpressions(String text) {
        String[] lines = text.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = normalizeShiftOperatorsInLine(lines[i]);
        }
        return String.join("\n", lines);
    }

    private static String normalizeShiftOperatorsInLine(String line) {
        String result = line;

        int returnIndex = result.indexOf("return ");
        if (returnIndex >= 0) {
            int expressionStart = returnIndex + "return ".length();
            String prefix = result.substring(0, expressionStart);
            String expression = result.substring(expressionStart);
            result = prefix + normalizeShiftOperators(expression);
        }

        int assignmentIndex = findAssignmentOperatorIndex(result);
        if (assignmentIndex >= 0) {
            String left = result.substring(0, assignmentIndex + 1);
            String right = result.substring(assignmentIndex + 1);
            result = left + normalizeShiftOperators(right);
        }

        return result;
    }

    private static String normalizeShiftOperators(String expression) {
        StringBuilder normalized = new StringBuilder(expression.length());
        int genericDepth = 0;
        boolean inString = false;
        boolean inChar = false;

        for (int i = 0; i < expression.length(); i++) {
            char c = expression.charAt(i);

            if (c == '"' && !inChar && !isEscaped(expression, i)) {
                inString = !inString;
                normalized.append(c);
                continue;
            }
            if (c == '\'' && !inString && !isEscaped(expression, i)) {
                inChar = !inChar;
                normalized.append(c);
                continue;
            }
            if (inString || inChar) {
                normalized.append(c);
                continue;
            }

            if (c == '<' && isLikelyGenericOpen(expression, i, genericDepth)) {
                genericDepth++;
                normalized.append(c);
                continue;
            }

            if (c == '>' && genericDepth > 0) {
                genericDepth--;
                normalized.append(c);
                continue;
            }

            if (genericDepth == 0 && c == '<' && i + 1 < expression.length() && expression.charAt(i + 1) == '<'
                    && (i + 2 >= expression.length() || expression.charAt(i + 2) != '=')) {
                trimTrailingWhitespace(normalized);
                normalized.append(" << ");
                i = skipFollowingWhitespace(expression, i + 2) - 1;
                continue;
            }

            if (genericDepth == 0 && c == '>' && i + 1 < expression.length() && expression.charAt(i + 1) == '>'
                    && (i + 2 >= expression.length() || (expression.charAt(i + 2) != '=' && expression.charAt(i + 2) != '>'))) {
                trimTrailingWhitespace(normalized);
                normalized.append(" >> ");
                i = skipFollowingWhitespace(expression, i + 2) - 1;
                continue;
            }

            normalized.append(c);
        }

        return normalized.toString();
    }

    private static boolean isLikelyGenericOpen(String expression, int index, int genericDepth) {
        if (index + 1 < expression.length() && expression.charAt(index + 1) == '<') {
            return false;
        }
        if (genericDepth > 0) {
            return true;
        }

        int previousIndex = previousNonWhitespaceIndex(expression, index - 1);
        int nextIndex = nextNonWhitespaceIndex(expression, index + 1);
        if (previousIndex < 0 || nextIndex < 0) {
            return false;
        }

        char previous = expression.charAt(previousIndex);
        if (!(Character.isJavaIdentifierPart(previous)
                || previous == '.'
                || previous == '>'
                || previous == '?'
                || previous == ']'
                || previous == ')')) {
            return false;
        }

        char next = expression.charAt(nextIndex);
        return next == '?' || Character.isUpperCase(next);
    }

    private static int previousNonWhitespaceIndex(String text, int start) {
        int i = start;
        while (i >= 0 && Character.isWhitespace(text.charAt(i))) {
            i--;
        }
        return i;
    }

    private static int nextNonWhitespaceIndex(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static boolean isEscaped(String text, int index) {
        int backslashCount = 0;
        int i = index - 1;
        while (i >= 0 && text.charAt(i) == '\\') {
            backslashCount++;
            i--;
        }
        return (backslashCount % 2) == 1;
    }

    private static void trimTrailingWhitespace(StringBuilder builder) {
        while (builder.length() > 0 && Character.isWhitespace(builder.charAt(builder.length() - 1))) {
            builder.deleteCharAt(builder.length() - 1);
        }
    }

    private static int skipFollowingWhitespace(String text, int start) {
        int i = start;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return i;
    }

    private static int findAssignmentOperatorIndex(String line) {
        for (int i = 0; i < line.length() - 1; i++) {
            char c = line.charAt(i);
            char next = line.charAt(i + 1);
            if (c != '=') {
                continue;
            }

            char previous = i > 0 ? line.charAt(i - 1) : '\0';
            if (next == '=' || previous == '=' || previous == '!' || previous == '<' || previous == '>') {
                continue;
            }
            return i;
        }
        return -1;
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
         // Handle member-access comparisons like `index <this.bitSlots.size()`.
         result = result.replaceAll("([A-Za-z_$][A-Za-z0-9_$]*)\\s*<\\s*(this\\.[A-Za-z_$][A-Za-z0-9_$.]*)", "$1 < $2");
         result = result.replaceAll("([A-Za-z_$][A-Za-z0-9_$]*)\\s*>\\s*(this\\.[A-Za-z_$][A-Za-z0-9_$.]*)", "$1 > $2");
         return result;
     }

    private static String normalizeTernaryOperators(String text) {
        String[] lines = text.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (!lines[i].contains("?") && !trimmed.startsWith(":")) {
                continue;
            }

            int contentStart = 0;
            while (contentStart < lines[i].length() && Character.isWhitespace(lines[i].charAt(contentStart))) {
                contentStart++;
            }

            String indentation = lines[i].substring(0, contentStart);
            String content = lines[i].substring(contentStart);
            content = content.replaceAll("\\s*\\?\\s*", " ? ");
            content = content.replaceAll("(?<!:)\\s*:(?!:)\\s*", " : ");
            if (content.startsWith(" ? ")) {
                content = "?" + content.substring(2);
            }
            if (content.startsWith(" : ")) {
                content = ":" + content.substring(2);
            }
            lines[i] = indentation + content;
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


