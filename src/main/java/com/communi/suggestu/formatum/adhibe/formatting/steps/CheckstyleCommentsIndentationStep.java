package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class CheckstyleCommentsIndentationStep extends FormattingStep {
    @Inject
    public CheckstyleCommentsIndentationStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (!trimmed.startsWith("//")) {
                continue;
            }

            int previous = previousCodeLine(lines, i - 1);
            if (previous < 0) {
                continue;
            }

            String previousLine = lines.get(previous);
            int indentEnd = 0;
            while (indentEnd < previousLine.length() && (previousLine.charAt(indentEnd) == '\t' || previousLine.charAt(indentEnd) == ' ')) {
                indentEnd++;
            }
            String indent = previousLine.substring(0, indentEnd);
            lines.set(i, indent + trimmed);
        }

        return String.join("\n", lines);
    }

    private static int previousCodeLine(List<String> lines, int from) {
        for (int i = from; i >= 0; i--) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("//")) {
                return i;
            }
        }
        return -1;
    }
}

