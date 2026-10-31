package com.communi.suggestu.formatum.adhibe.formatting.steps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.TreeSet;
import java.util.regex.Pattern;

/** Keeps standalone comments intact while import steps work on import statements only. */
record ImportSection(List<String> lines, int first, int last, List<String> imports, List<Comment> comments) {
    private static final Pattern IMPORT = Pattern.compile("^\\s*import\\s+(?:static\\s+)?[^;\\s]+\\s*;\\s*$");

    record Comment(int importsBefore, List<String> lines) {}

    static ImportSection find(String text) {
        text = splitInlineImports(text);
        char[] code = text.toCharArray();
        Arrays.fill(code, ' ');
        for (JavaLexer.Token token : JavaLexer.lex(text)) {
            text.getChars(token.start(), token.end(), code, token.start());
        }
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                code[i] = '\n';
            }
        }
        List<String> lines = List.of(text.split("\n", -1));
        String[] codeLines = new String(code).split("\n", -1);
        int first = -1;
        int last = -1;
        for (int i = 0; i < codeLines.length; i++) {
            if (IMPORT.matcher(codeLines[i]).matches()) {
                if (first < 0) {
                    first = i;
                }
                last = i;
            } else if (first >= 0 && !codeLines[i].isBlank()) {
                break;
            }
        }
        if (first < 0) {
            return null;
        }
        List<String> imports = new ArrayList<>();
        List<Comment> comments = new ArrayList<>();
        List<String> pending = new ArrayList<>();
        for (int i = first; i <= last; i++) {
            if (IMPORT.matcher(codeLines[i]).matches()) {
                if (!pending.isEmpty()) {
                    comments.add(new Comment(imports.size(), List.copyOf(pending)));
                    pending.clear();
                }
                String statement = lines.get(i);
                String trailing = statement.substring(statement.indexOf(';') + 1).stripLeading();
                if (trailing.startsWith("/*") && !trailing.contains("*/")) {
                    StringBuilder complete = new StringBuilder(statement);
                    do {
                        i++;
                        complete.append('\n').append(lines.get(i));
                    } while (!lines.get(i).contains("*/"));
                    statement = complete.toString();
                    last = Math.max(last, i);
                }
                imports.add(statement);
            } else if (!lines.get(i).isBlank() || !pending.isEmpty()) {
                pending.add(lines.get(i));
            }
        }
        while (last + 1 < lines.size() && lines.get(last + 1).isBlank()) {
            last++;
        }
        return new ImportSection(lines, first, last, imports, comments);
    }

    private static String splitInlineImports(String text) {
        var tokens = JavaLexer.lex(text);
        var breaks = new TreeSet<Integer>();
        for (int i = 0; i < tokens.size(); i++) {
            if (!tokens.get(i).text().equals("import")) {
                continue;
            }
            int start = tokens.get(i).start();
            int lineStart = text.lastIndexOf('\n', start) + 1;
            if (!text.substring(lineStart, start).isBlank()) {
                breaks.add(start);
            }
            while (i + 1 < tokens.size() && !tokens.get(i).text().equals(";")) {
                i++;
            }
            if (tokens.get(i).text().equals(";") && i + 1 < tokens.size()) {
                int next = tokens.get(i + 1).start();
                if (!text.substring(tokens.get(i).end(), next).contains("\n")) {
                    breaks.add(next);
                }
            }
        }
        var result = new StringBuilder(text);
        for (int offset : breaks.descendingSet()) {
            int from = offset;
            while (from > 0 && (text.charAt(from - 1) == ' ' || text.charAt(from - 1) == '\t')) {
                from--;
            }
            result.replace(from, offset, "\n");
        }
        return result.toString();
    }

    String replace(List<String> replacements) {
        return replace(replacements, false);
    }

    String replace(List<String> replacements, boolean commentsBeforeImports) {
        List<String> output = new ArrayList<>(lines.subList(0, first));
        int importCount = 0;
        int commentIndex = 0;
        if (commentsBeforeImports) {
            // Checkstyle treats even comment-only lines as illegal separation within an import group.
            while (commentIndex < comments.size()) {
                output.addAll(comments.get(commentIndex++).lines());
            }
        }
        for (String line : replacements) {
            if (!line.isBlank()) {
                while (commentIndex < comments.size() && comments.get(commentIndex).importsBefore() <= importCount) {
                    output.addAll(comments.get(commentIndex++).lines());
                }
                importCount++;
            }
            output.add(line);
        }
        while (commentIndex < comments.size()) {
            output.addAll(comments.get(commentIndex++).lines());
        }
        if (last + 1 < lines.size() && !lines.get(last + 1).isBlank()) {
            output.add("");
        }
        output.addAll(lines.subList(last + 1, lines.size()));
        String result = String.join("\n", output);
        if (lines.getLast().isEmpty() && !result.endsWith("\n")) {
            result += "\n";
        }
        return result;
    }
}
