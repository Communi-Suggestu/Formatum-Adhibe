package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public abstract class CheckstyleIndentationStep extends FormattingStep {
    @Inject
    public CheckstyleIndentationStep() {
    }

    @Input
    public abstract Property<Integer> getBasicOffset();

    @Input
    public abstract Property<Integer> getCaseIndent();

    @Input
    public abstract Property<Integer> getThrowsIndent();

    @Input
    public abstract Property<Integer> getArrayInitIndent();

    @Input
    public abstract Property<Integer> getLineWrappingIndentation();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        int blockIndent = 0;
        Deque<ParenContext> parenContexts = new ArrayDeque<>();
        Deque<Integer> braceIndentStack = new ArrayDeque<>();
        int continuationTabs = Math.max(1, getLineWrappingIndentation().getOrElse(8) / Math.max(1, getBasicOffset().getOrElse(4)));
        int caseOffset = Math.max(0, getCaseIndent().getOrElse(0) / Math.max(1, getBasicOffset().getOrElse(4)));

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int effectiveIndent = determineIndent(trimmed, blockIndent, parenContexts, braceIndentStack, continuationTabs, caseOffset);
            lines.set(i, "\t".repeat(Math.max(0, effectiveIndent)) + trimmed);
            updateBraceIndentStack(braceIndentStack, trimmed, effectiveIndent);
            blockIndent = updateBlockIndent(blockIndent, trimmed);
            updateParenContexts(parenContexts, trimmed, effectiveIndent);
        }

        return String.join("\n", lines);
    }

    private static int determineIndent(
            String trimmed,
            int blockIndent,
            Deque<ParenContext> parenContexts,
            Deque<Integer> braceIndentStack,
            int continuationTabs,
            int caseOffset
    ) {
        if (startsWithClosingBrace(trimmed)) {
            if (!braceIndentStack.isEmpty()) {
                return braceIndentStack.peek();
            }
            return Math.max(0, blockIndent - 1);
        }

        if (trimmed.startsWith("case ") || trimmed.startsWith("default:")) {
            return Math.max(0, blockIndent - 1 + caseOffset);
        }

        if (isStandaloneClosingParenLine(trimmed) && !parenContexts.isEmpty()) {
            return parenContexts.peek().anchorIndentTabs();
        }

        if (!parenContexts.isEmpty()) {
            if (trimmed.startsWith("new ")) {
                return Math.max(blockIndent, parenContexts.peek().anchorIndentTabs() + Math.max(1, continuationTabs - 1));
            }
            int wrappedIndent = Math.max(blockIndent, parenContexts.peek().anchorIndentTabs() + continuationTabs);
            if (blockIndent >= parenContexts.peek().anchorIndentTabs() + continuationTabs) {
                return blockIndent + 1;
            }
            return wrappedIndent;
        }

        if (trimmed.startsWith(".")) {
            return blockIndent + 1;
        }

        return blockIndent;
    }

    private static boolean startsWithClosingBrace(String trimmed) {
        return trimmed.startsWith("}");
    }

    private static int updateBlockIndent(int currentIndent, String line) {
        int indent = currentIndent;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
            if (!inString && !inChar && c == '/' && next == '/') {
                break;
            }
            if (!inChar && c == '"') {
                boolean escaped = i > 0 && line.charAt(i - 1) == '\\';
                if (!escaped) {
                    inString = !inString;
                }
                continue;
            }
            if (!inString && c == '\'') {
                boolean escaped = i > 0 && line.charAt(i - 1) == '\\';
                if (!escaped) {
                    inChar = !inChar;
                }
                continue;
            }
            if (inString || inChar) {
                continue;
            }
            if (c == '{') {
                indent++;
            } else if (c == '}') {
                indent = Math.max(0, indent - 1);
            }
        }
        return indent;
    }

    private static void updateBraceIndentStack(Deque<Integer> braceIndentStack, String line, int appliedIndentTabs) {
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
            if (!inString && !inChar && c == '/' && next == '/') {
                break;
            }
            if (!inChar && c == '"') {
                boolean escaped = i > 0 && line.charAt(i - 1) == '\\';
                if (!escaped) {
                    inString = !inString;
                }
                continue;
            }
            if (!inString && c == '\'') {
                boolean escaped = i > 0 && line.charAt(i - 1) == '\\';
                if (!escaped) {
                    inChar = !inChar;
                }
                continue;
            }
            if (inString || inChar) {
                continue;
            }
            if (c == '}') {
                if (!braceIndentStack.isEmpty()) {
                    braceIndentStack.pop();
                }
            } else if (c == '{') {
                braceIndentStack.push(appliedIndentTabs);
            }
        }
    }

    private static void updateParenContexts(Deque<ParenContext> parenContexts, String line, int anchorIndentTabs) {
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            char next = i + 1 < line.length() ? line.charAt(i + 1) : '\0';
            if (!inString && !inChar && c == '/' && next == '/') {
                break;
            }
            if (c == '"' && !inChar) {
                boolean escaped = i > 0 && line.charAt(i - 1) == '\\';
                if (!escaped) {
                    inString = !inString;
                }
                continue;
            }
            if (c == '\'' && !inString) {
                boolean escaped = i > 0 && line.charAt(i - 1) == '\\';
                if (!escaped) {
                    inChar = !inChar;
                }
                continue;
            }
            if (inString || inChar) {
                continue;
            }
            if (c == '(' || c == '[') {
                parenContexts.push(new ParenContext(anchorIndentTabs, c));
            } else if ((c == ')' || c == ']') && !parenContexts.isEmpty()) {
                parenContexts.pop();
            }
        }
    }

    private static boolean isStandaloneClosingParenLine(String trimmed) {
        return trimmed.matches("^[)\\]]+[;,]*$");
    }

    private record ParenContext(int anchorIndentTabs, char opener) {
    }
}

