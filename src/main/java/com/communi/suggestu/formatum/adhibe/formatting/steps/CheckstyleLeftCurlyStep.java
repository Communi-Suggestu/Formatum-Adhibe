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

            String previous = lines.get(i - 1);
            String previousTrimmed = previous.stripTrailing();
            if (previousTrimmed.isEmpty() || previousTrimmed.endsWith("{")) {
                continue;
            }

            if (getIgnoreEnums().getOrElse(true) && previousTrimmed.matches(".*\\benum\\b.*")) {
                continue;
            }

            lines.set(i - 1, previousTrimmed + " {");
            lines.remove(i);
            i--;
        }
        return String.join("\n", lines);
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

