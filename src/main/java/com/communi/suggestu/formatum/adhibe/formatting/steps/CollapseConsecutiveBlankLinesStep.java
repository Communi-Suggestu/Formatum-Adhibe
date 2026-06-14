package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class CollapseConsecutiveBlankLinesStep extends FormattingStep {
    @Inject
    public CollapseConsecutiveBlankLinesStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> {
            List<String> lines = new ArrayList<>(Arrays.asList(TextFormattingUtils.normalizeNewlines(text).split("\n", -1)));
            int consecutiveBlanks = 0;
            int i = 0;
            while (i < lines.size()) {
                if (lines.get(i).isBlank()) {
                    consecutiveBlanks++;
                    if (consecutiveBlanks > 1) {
                        lines.remove(i);
                        // do not advance i; re-evaluate at same index
                        continue;
                    }
                } else {
                    consecutiveBlanks = 0;
                }
                i++;
            }
            return String.join("\n", lines);
        };
    }
}
