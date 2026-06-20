package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class CheckstyleLeftCurlyStep extends FormattingStep {
    @Inject
    public CheckstyleLeftCurlyStep() {
    }

    @Input
    public abstract Property<String> getOption();

    @Input
    public abstract Property<Boolean> getIgnoreEnums();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> applyLeftCurlyPolicy(TextFormattingUtils.normalizeNewlines(text));
    }

    private String applyLeftCurlyPolicy(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        String option = getOption().getOrElse("eol");
        return switch (option) {
            case "nl", "nlow" -> moveLeftCurlyToNewLine(lines);
            default -> moveLeftCurlyToPreviousLine(lines);
        };
    }

    private String moveLeftCurlyToPreviousLine(List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            String current = lines.get(i).strip();
            if (!"{".equals(current)) {
                continue;
            }

            int previousIndex = findAttachCandidateIndex(lines, i - 1);
            if (previousIndex < 0) {
                continue;
            }

            String previous = lines.get(previousIndex);
            String previousTrimmed = stripInlineComment(previous).stripTrailing();
            if (previousTrimmed.isEmpty() || previousTrimmed.endsWith("{")) {
                continue;
            }

            if (getIgnoreEnums().getOrElse(true) && previousTrimmed.matches(".*\\benum\\b.*")) {
                continue;
            }

            lines.set(previousIndex, insertBraceBeforeInlineComment(previous));
            lines.subList(previousIndex + 1, i + 1).clear();
            i = previousIndex;
        }
        return String.join("\n", lines);
    }

    private static int findAttachCandidateIndex(List<String> lines, int start) {
        for (int i = start; i >= 0; i--) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            String codePart = stripInlineComment(line).trim();
            if (codePart.isEmpty()) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static String insertBraceBeforeInlineComment(String line) {
        int commentIndex = line.indexOf("//");
        if (commentIndex < 0) {
            return line.stripTrailing() + " {";
        }

        String beforeComment = line.substring(0, commentIndex).stripTrailing();
        String comment = line.substring(commentIndex).stripLeading();
        return beforeComment + " { " + comment;
    }

    private static String stripInlineComment(String line) {
        int commentIndex = line.indexOf("//");
        if (commentIndex < 0) {
            return line;
        }
        return line.substring(0, commentIndex);
    }

    private String moveLeftCurlyToNewLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int braceIndex = line.lastIndexOf('{');
            if (braceIndex <= 0) {
                continue;
            }

            String afterBrace = line.substring(braceIndex + 1).trim();
            if (!afterBrace.isEmpty()) {
                continue;
            }

            String beforeBrace = line.substring(0, braceIndex).stripTrailing();
            if (beforeBrace.isEmpty()) {
                continue;
            }

            String indent = line.substring(0, line.indexOf(beforeBrace));
            lines.set(i, beforeBrace);
            lines.add(i + 1, indent + "{");
            i++;
        }
        return String.join("\n", lines);
    }
}

