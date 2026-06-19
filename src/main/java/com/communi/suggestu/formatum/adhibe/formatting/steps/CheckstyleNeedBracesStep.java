package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CheckstyleNeedBracesStep extends FormattingStep {
    private static final Pattern IF_FOR_WHILE_PATTERN = Pattern.compile("^(\\s*)(if|for|while)\\b(.*)$");
    private static final Pattern ELSE_PATTERN = Pattern.compile("^(\\s*)(else)\\b(.*)$");
    private static final Pattern DO_PATTERN = Pattern.compile("^(\\s*)(do)\\b(.*)$");

    @Inject
    public CheckstyleNeedBracesStep() {
    }

    @Input
    public abstract ListProperty<String> getTokens();

    @Input
    public abstract Property<Boolean> getAllowSingleLineStatement();

    @Input
    public abstract Property<Boolean> getAllowEmptyLoopBody();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> applyNeedBraces(TextFormattingUtils.normalizeNewlines(text));
    }

    private String applyNeedBraces(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        Set<String> enabledTokens = new LinkedHashSet<>(getTokens().getOrElse(List.of("LITERAL_DO", "LITERAL_ELSE", "LITERAL_FOR", "LITERAL_IF", "LITERAL_WHILE")));

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("*")) {
                continue;
            }

            if (enabledTokens.contains("LITERAL_IF") || enabledTokens.contains("LITERAL_FOR") || enabledTokens.contains("LITERAL_WHILE")) {
                Matcher matcher = IF_FOR_WHILE_PATTERN.matcher(line);
                if (matcher.find()) {
                    String token = matcher.group(2);
                    String literalToken = "LITERAL_" + token.toUpperCase();
                    HeaderAndBody headerAndBody = splitConditionAndBody(matcher.group(3));
                    if (!headerAndBody.conditionClosed()) {
                        continue;
                    }
                    if (enabledTokens.contains(literalToken) && !hasBodyBrace(headerAndBody.bodyRemainder())) {
                        i = wrapStatement(lines, i, matcher.group(1), token, headerAndBody.headerRemainder(), headerAndBody.bodyRemainder());
                        continue;
                    }
                }
            }

            if (enabledTokens.contains("LITERAL_ELSE")) {
                Matcher matcher = ELSE_PATTERN.matcher(line);
                if (matcher.find() && !hasBodyBrace(matcher.group(3))) {
                    i = wrapStatement(lines, i, matcher.group(1), "else", "", matcher.group(3));
                    continue;
                }
            }

            if (enabledTokens.contains("LITERAL_DO")) {
                Matcher matcher = DO_PATTERN.matcher(line);
                if (matcher.find() && !hasBodyBrace(matcher.group(3))) {
                    i = wrapStatement(lines, i, matcher.group(1), "do", "", matcher.group(3));
                }
            }
        }

        return String.join("\n", lines);
    }

    private int wrapStatement(List<String> lines, int index, String indent, String token, String headerRemainder, String bodyRemainder) {
        String trimmedRemainder = bodyRemainder.trim();
        String tokenWithHeader = headerRemainder.isBlank() ? token : token + " " + headerRemainder;

        if (trimmedRemainder.equals(";") && isLoopToken(token) && getAllowEmptyLoopBody().getOrElse(false)) {
            return index;
        }

        if (!trimmedRemainder.isEmpty()) {
            if (trimmedRemainder.startsWith("if") && "else".equals(token)) {
                return index;
            }
            if (getAllowSingleLineStatement().getOrElse(false)) {
                return index;
            }

            lines.set(index, indent + tokenWithHeader + " {");
            lines.add(index + 1, indent + "\t" + trimmedRemainder);
            lines.add(index + 2, indent + "}");
            return index + 2;
        }

        int statementIndex = nextStatementLine(lines, index + 1);
        if (statementIndex < 0) {
            return index;
        }

        String statement = lines.get(statementIndex);
        if (statement.trim().startsWith("{")) {
            return index;
        }

        if (statement.trim().equals(";") && isLoopToken(token) && getAllowEmptyLoopBody().getOrElse(false)) {
            return index;
        }

        lines.set(index, indent + tokenWithHeader + " {");
        lines.set(statementIndex, indent + "\t" + statement.trim());
        lines.add(statementIndex + 1, indent + "}");
        return statementIndex + 1;
    }

    private static HeaderAndBody splitConditionAndBody(String remainder) {
        String trimmed = remainder.trim();
        if (!trimmed.startsWith("(")) {
            return new HeaderAndBody("", trimmed, true);
        }

        int depth = 0;
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth--;
                if (depth == 0) {
                    String header = trimmed.substring(0, i + 1).trim();
                    String body = trimmed.substring(i + 1).trim();
                    return new HeaderAndBody(header, body, true);
                }
            }
        }

        return new HeaderAndBody(trimmed, "", false);
    }

    private static int nextStatementLine(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.isEmpty() || trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static boolean hasBodyBrace(String text) {
        return text.contains("{");
    }

    private static boolean isLoopToken(String token) {
        return "for".equals(token) || "while".equals(token) || "do".equals(token);
    }

    private record HeaderAndBody(String headerRemainder, String bodyRemainder, boolean conditionClosed) {
    }
}


