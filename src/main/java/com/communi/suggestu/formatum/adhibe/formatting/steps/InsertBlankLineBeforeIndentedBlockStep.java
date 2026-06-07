package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class InsertBlankLineBeforeIndentedBlockStep extends FormattingStep {
    @Inject
    public InsertBlankLineBeforeIndentedBlockStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> {
            List<String> lines = new ArrayList<>(Arrays.asList(TextFormattingUtils.normalizeNewlines(text).split("\n", -1)));
            for (int index = 1; index < lines.size(); index++) {
                int insertionIndex = insertionIndex(lines, index);
                if (insertionIndex > 0 && !lines.get(insertionIndex - 1).isBlank()) {
                    lines.add(insertionIndex, "");
                    index++;
                }
            }
            return String.join("\n", lines);
        };
    }

    private static int insertionIndex(List<String> lines, int currentIndex) {
        String currentLine = lines.get(currentIndex);
        int indentTabs = TextFormattingUtils.countLeadingTabs(currentLine);
        if (indentTabs <= 0) {
            return -1;
        }

        String trimmedCurrent = currentLine.substring(indentTabs).trim();
        if (!trimmedCurrent.endsWith("{")) {
            return -1;
        }
        if (!(trimmedCurrent.equals("{") || startsWithControlBlock(trimmedCurrent))) {
            return -1;
        }

        int insertionIndex = currentIndex;
        while (insertionIndex > 0 && TextFormattingUtils.isSingleLineCommentAtIndent(lines.get(insertionIndex - 1), indentTabs)) {
            insertionIndex--;
        }
        if (insertionIndex <= 0) {
            return -1;
        }

        String previousLine = lines.get(insertionIndex - 1);
        if (previousLine.isBlank()) {
            return -1;
        }

        int previousIndent = TextFormattingUtils.countLeadingTabs(previousLine);
        if (previousIndent != indentTabs) {
            return -1;
        }

        String trimmedPrevious = previousLine.substring(previousIndent).trim();
        if (trimmedPrevious.startsWith("//") || trimmedPrevious.startsWith("/*") || trimmedPrevious.startsWith("*") || trimmedPrevious.startsWith(" ")) {
            return -1;
        }
        return insertionIndex;
    }

    private static boolean startsWithControlBlock(String line) {
        return line.startsWith("if")
                || line.startsWith("do")
                || line.startsWith("while")
                || line.startsWith("for")
                || line.startsWith("try");
    }
}


