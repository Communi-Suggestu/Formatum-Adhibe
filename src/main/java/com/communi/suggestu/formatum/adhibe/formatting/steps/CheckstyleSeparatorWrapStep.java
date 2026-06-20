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

public abstract class CheckstyleSeparatorWrapStep extends FormattingStep {
    @Inject
    public CheckstyleSeparatorWrapStep() {
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
        String option = getOption().getOrElse("eol").toLowerCase(Locale.ROOT);
        List<String> tokens = getTokens().getOrElse(List.of());
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));

        if ("nl".equals(option)) {
            if (tokens.contains("DOT")) {
                moveTrailingTokenToNextLine(lines, ".");
            }
            if (tokens.contains("ELLIPSIS")) {
                moveTrailingTokenToNextLine(lines, "...");
            }
            if (tokens.contains("AT")) {
                moveTrailingTokenToNextLine(lines, "@");
            }
        } else {
            if (tokens.contains("COMMA")) {
                moveLeadingTokenToPreviousLine(lines, ",");
            }
            if (tokens.contains("SEMI")) {
                moveLeadingTokenToPreviousLine(lines, ";");
            }
        }

        return String.join("\n", lines);
    }

    private static void moveTrailingTokenToNextLine(List<String> lines, String token) {
        for (int i = 0; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.endsWith(token)) {
                continue;
            }

            int next = nextNonEmpty(lines, i + 1);
            if (next < 0) {
                continue;
            }

            String nextTrimmed = lines.get(next).trim();
            if (nextTrimmed.startsWith(token)) {
                continue;
            }

            String without = line.substring(0, line.lastIndexOf(token)).stripTrailing();
            String nextLine = lines.get(next);
            String nextIndent = nextLine.substring(0, nextLine.length() - nextLine.stripLeading().length());

            lines.set(i, without);
            lines.set(next, nextIndent + token + nextTrimmed);
        }
    }

    private static void moveLeadingTokenToPreviousLine(List<String> lines, String token) {
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith(token)) {
                continue;
            }

            int previous = previousNonEmpty(lines, i - 1);
            if (previous < 0) {
                continue;
            }

            String previousLine = lines.get(previous).stripTrailing();
            lines.set(previous, previousLine + token);
            String withoutToken = line.substring(line.indexOf(token) + token.length()).stripLeading();
            String currentIndent = line.substring(0, line.length() - line.stripLeading().length());
            lines.set(i, withoutToken.isEmpty() ? withoutToken : currentIndent + withoutToken);
        }
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
}

