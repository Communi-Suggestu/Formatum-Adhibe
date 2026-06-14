package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class RemoveBlankLineAfterOpeningBraceStep extends FormattingStep {
    @Inject
    public RemoveBlankLineAfterOpeningBraceStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> {
            List<String> lines = new ArrayList<>(Arrays.asList(TextFormattingUtils.normalizeNewlines(text).split("\n", -1)));
            int i = 0;
            while (i < lines.size() - 1) {
                String current = lines.get(i);
                String next = lines.get(i + 1);
                if (current.stripTrailing().endsWith("{") && next.isBlank()) {
                    lines.remove(i + 1);
                    // do not advance i; check the new next line too
                } else {
                    i++;
                }
            }
            return String.join("\n", lines);
        };
    }
}
