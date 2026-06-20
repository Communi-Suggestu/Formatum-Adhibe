package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;

public abstract class CheckstyleSingleSpaceSeparatorStep extends FormattingStep {
    @Inject
    public CheckstyleSingleSpaceSeparatorStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> {
            String normalized = TextFormattingUtils.normalizeNewlines(text);
            String[] lines = normalized.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                int indentEnd = 0;
                while (indentEnd < line.length() && (line.charAt(indentEnd) == '\t' || line.charAt(indentEnd) == ' ')) {
                    indentEnd++;
                }
                String indent = line.substring(0, indentEnd);
                String content = line.substring(indentEnd).replaceAll(" {2,}", " ");
                lines[i] = indent + content;
            }
            return String.join("\n", lines);
        };
    }
}

