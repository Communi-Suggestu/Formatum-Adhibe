package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class InsertBlankLineAfterIndentedBlockStep extends FormattingStep {
    @Inject
    public InsertBlankLineAfterIndentedBlockStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> {
            List<String> lines = new ArrayList<>(Arrays.asList(TextFormattingUtils.normalizeNewlines(text).split("\n", -1)));
            for (int index = 0; index < lines.size() - 1; index++) {
                if (shouldInsert(lines, index)) {
                    lines.add(index + 1, "");
                    index++;
                }
            }
            return String.join("\n", lines);
        };
    }

    private static boolean shouldInsert(List<String> lines, int index) {
        String currentLine = lines.get(index);
        int indentTabs = TextFormattingUtils.countLeadingTabs(currentLine);
        if (indentTabs <= 0 || !currentLine.substring(indentTabs).trim().equals("}")) {
            return false;
        }

        String nextLine = lines.get(index + 1);
        if (nextLine.isBlank()) {
            return false;
        }

        int nextIndentTabs = TextFormattingUtils.countLeadingTabs(nextLine);
        if (nextIndentTabs != indentTabs) {
            return false;
        }

        String trimmedNext = nextLine.substring(nextIndentTabs).trim();
        return !trimmedNext.equals("}") && !trimmedNext.startsWith("case ") && !trimmedNext.startsWith("default");
    }
}


