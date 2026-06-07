package com.communi.suggestu.formatum.adhibe.formatting.steps;

final class TextFormattingUtils {
    private TextFormattingUtils() {
    }

    static String normalizeNewlines(String text) {
        return text.replace("\r\n", "\n");
    }

    static int countLeadingTabs(String line) {
        int count = 0;
        while (count < line.length() && line.charAt(count) == '\t') {
            count++;
        }
        return count;
    }

    static int countIndentColumns(String line, int tabWidth) {
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

    static boolean isSingleLineCommentAtIndent(String line, int indentTabs) {
        if (countLeadingTabs(line) != indentTabs) {
            return false;
        }
        return line.substring(indentTabs).startsWith("//");
    }
}

