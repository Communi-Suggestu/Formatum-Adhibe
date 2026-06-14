package com.communi.suggestu.formatum.adhibe.formatting.steps;

import java.util.ArrayList;
import java.util.List;

public final class TextFormattingUtils {
    private TextFormattingUtils() {
    }

    /**
     * Compares {@code original} and {@code result} and returns the 1-based line numbers in
     * {@code original} that were affected by the change.
     *
     * <p>When the number of lines is the same (pure in-place modifications), each differing line
     * is reported individually. When the number of lines differs (insertions or deletions), a
     * character-level scan is used to find the affected region and all original line numbers
     * within that region are returned.
     *
     * @return an immutable list of 1-based line numbers, empty when the texts are identical
     */
    public static List<Integer> findChangedLineNumbers(String original, String result) {
        if (original.equals(result)) {
            return List.of();
        }

        String[] origLines = original.split("\n", -1);
        String[] resLines = result.split("\n", -1);

        // Same number of lines → pure modifications; compare line by line
        if (origLines.length == resLines.length) {
            List<Integer> changed = new ArrayList<>();
            for (int i = 0; i < origLines.length; i++) {
                if (!origLines[i].equals(resLines[i])) {
                    changed.add(i + 1);
                }
            }
            return List.copyOf(changed);
        }

        // Different number of lines (insertions / deletions) → character-level boundary scan
        int firstDiff = 0;
        int minLen = Math.min(original.length(), result.length());
        while (firstDiff < minLen && original.charAt(firstDiff) == result.charAt(firstDiff)) {
            firstDiff++;
        }

        int lastDiffOrig = original.length() - 1;
        int lastDiffRes = result.length() - 1;
        while (lastDiffOrig > firstDiff && lastDiffRes > firstDiff
                && original.charAt(lastDiffOrig) == result.charAt(lastDiffRes)) {
            lastDiffOrig--;
            lastDiffRes--;
        }

        // Map the character offsets to 1-based line numbers in the original
        int firstLine = 1;
        for (int i = 0; i < firstDiff && i < original.length(); i++) {
            if (original.charAt(i) == '\n') {
                firstLine++;
            }
        }

        int lastLine = firstLine;
        for (int i = firstDiff; i <= lastDiffOrig && i < original.length(); i++) {
            if (original.charAt(i) == '\n') {
                lastLine++;
            }
        }

        List<Integer> changed = new ArrayList<>(lastLine - firstLine + 1);
        for (int i = firstLine; i <= lastLine; i++) {
            changed.add(i);
        }
        return List.copyOf(changed);
    }

    public static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }

    public static int countLeadingTabs(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '\t') {
            count++;
        }
        return count;
    }

    public static int countIndentColumns(String line, int tabWidth) {
        int columns = 0;
        for (int i = 0; i < line.length(); i++) {
            char character = line.charAt(i);
            if (character == '\t') {
                columns += tabWidth;
            } else if (character == ' ') {
                columns++;
            } else {
                break;
            }
        }
        return columns;
    }

    public static boolean isSingleLineCommentAtIndent(String line, int indentTabs) {
        if (countLeadingTabs(line) != indentTabs) {
            return false;
        }
        return line.substring(indentTabs).startsWith("//");
    }
}

