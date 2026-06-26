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
        int inheritanceContinuationIndent = -1;
        boolean previousLineEndedWithAssignment = false;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            int effectiveIndent;
            boolean assignmentContinuation = previousLineEndedWithAssignment && !startsWithClosingBrace(trimmed);
            if (inheritanceContinuationIndent >= 0 && !startsWithClosingBrace(trimmed)) {
                effectiveIndent = inheritanceContinuationIndent;
            } else {
                effectiveIndent = determineIndent(trimmed, blockIndent, parenContexts, braceIndentStack, continuationTabs, caseOffset, assignmentContinuation);
            }
            lines.set(i, "\t".repeat(Math.max(0, effectiveIndent)) + trimmed);
            boolean insideInheritanceContinuation = inheritanceContinuationIndent >= 0;
            updateBraceIndentStack(braceIndentStack, trimmed, effectiveIndent, blockIndent, parenContexts, insideInheritanceContinuation);
            blockIndent = updateBlockIndent(blockIndent, trimmed);
            updateParenContexts(parenContexts, trimmed, effectiveIndent);

            if (inheritanceContinuationIndent >= 0 && !trimmed.endsWith(",")) {
                inheritanceContinuationIndent = -1;
            }
            if (inheritanceContinuationIndent < 0 && startsWrappedInheritanceList(trimmed)) {
                inheritanceContinuationIndent = effectiveIndent + continuationTabs;
            }

            previousLineEndedWithAssignment = endsWithStandaloneAssignment(trimmed);
        }

        return String.join("\n", lines);
    }

    private static int determineIndent(
            String trimmed,
            int blockIndent,
            Deque<ParenContext> parenContexts,
            Deque<Integer> braceIndentStack,
            int continuationTabs,
            int caseOffset,
            boolean assignmentContinuation
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

        if (startsWithClosingParenLine(trimmed) && !parenContexts.isEmpty()) {
            return parenContexts.peek().anchorIndentTabs();
        }

        if (startsWithTernaryContinuation(trimmed)) {
            if (isInsideWrappedBraceBody(trimmed, parenContexts, braceIndentStack)) {
                return braceIndentStack.peek() + 1 + continuationTabs;
            }
            if (!parenContexts.isEmpty()) {
                return Math.max(blockIndent + continuationTabs, parenContexts.peek().anchorIndentTabs() + continuationTabs);
            }
            return Math.max(blockIndent + 1, blockIndent + continuationTabs);
        }

        if (assignmentContinuation) {
            return Math.max(blockIndent + 1, blockIndent + Math.max(1, continuationTabs - 1));
        }

        if (isInsideWrappedBraceBody(trimmed, parenContexts, braceIndentStack)) {
            return braceIndentStack.peek() + 1;
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
            return Math.max(blockIndent + 1, blockIndent + continuationTabs);
        }

        return blockIndent;
    }

    private static boolean startsWithClosingBrace(String trimmed) {
        return trimmed.startsWith("}");
    }

    private static boolean isInsideWrappedBraceBody(
            String trimmed,
            Deque<ParenContext> parenContexts,
            Deque<Integer> braceIndentStack
    ) {
        return !parenContexts.isEmpty()
                && !braceIndentStack.isEmpty()
                && braceIndentStack.peek() >= parenContexts.peek().anchorIndentTabs();
    }


    private static boolean startsWithTernaryContinuation(String trimmed) {
        return trimmed.startsWith("?") || trimmed.startsWith(":");
    }

    private static boolean startsWrappedInheritanceList(String trimmed) {
        return (trimmed.contains(" extends ") || trimmed.contains(" implements ")) && trimmed.endsWith(",");
    }

    private static boolean endsWithStandaloneAssignment(String trimmed) {
        if (!trimmed.endsWith("=")) {
            return false;
        }

        int index = trimmed.length() - 1;
        char previous = index > 0 ? trimmed.charAt(index - 1) : '\0';
        return previous != '='
                && previous != '!'
                && previous != '<'
                && previous != '>'
                && previous != '+'
                && previous != '-'
                && previous != '*'
                && previous != '/'
                && previous != '%'
                && previous != '&'
                && previous != '|'
                && previous != '^';
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

    private static void updateBraceIndentStack(
            Deque<Integer> braceIndentStack,
            String line,
            int appliedIndentTabs,
            int blockIndent,
            Deque<ParenContext> parenContexts,
            boolean insideInheritanceContinuation
    ) {
        boolean inString = false;
        boolean inChar = false;
        String trimmed = line.trim();
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
                int anchorIndent = appliedIndentTabs;
                if (insideInheritanceContinuation || (!parenContexts.isEmpty() && !shouldUseAppliedBraceIndent(trimmed))) {
                    anchorIndent = blockIndent;
                }
                braceIndentStack.push(anchorIndent);
            }
        }
    }

    private static boolean shouldUseAppliedBraceIndent(String trimmed) {
        return trimmed.startsWith("new ")
                || (trimmed.startsWith(".") && !trimmed.matches(".*\\)\\)\\s*\\{$"))
                || isTryBlockOpener(trimmed)
                || trimmed.startsWith("} else")
                || trimmed.startsWith("} catch")
                || trimmed.startsWith("} finally")
                || trimmed.contains("->")
                || isDeclarationLikeBlockOpener(trimmed);
    }

    private static boolean isTryBlockOpener(String trimmed) {
        return trimmed.startsWith("try {") || trimmed.startsWith("try{");
    }

    private static boolean isDeclarationLikeBlockOpener(String trimmed) {
        if (!trimmed.endsWith("{") || !trimmed.contains("(") || !trimmed.contains(")")) {
            return false;
        }

        // Wrapped boolean condition continuations should close at structural indent of the
        // owning if/else-if, not at continuation indent.
        if (trimmed.matches("^[!~].*\\)\\s*\\{$")
                || trimmed.matches("^(this|super)\\..*\\)\\s*\\{$")) {
            return false;
        }

        if (trimmed.startsWith(".") && trimmed.matches(".*\\)\\)\\s*\\{$")) {
            return false;
        }

        // Resource declarations in try-with-resources are wrapped continuation lines that
        // should close at structural block indent, not at continuation indent.
        return !trimmed.matches(".*(?<![<>=!+\\-*/%&|^])=(?!=).*\\)\\s*\\{$");
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

    private static boolean startsWithClosingParenLine(String trimmed) {
        if (trimmed.matches("^[)\\]].*$")) {
            return trimmed.matches("^[)\\]]+(\\s*[,;])?$")
                    || trimmed.matches("^[)\\]]+\\s+implements\\b.*$")
                    || trimmed.matches("^[)\\]]+\\s+throws\\b.*$")
                    || trimmed.matches("^[)\\]]+\\s*\\{.*$");
        }

        return trimmed.matches("^[)\\]]+[;,]*(\\s*\\{)?$");
    }

    private record ParenContext(int anchorIndentTabs, char opener) {
    }
}

