package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
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
        int parenthesisDepth = 0;
        int continuationTabs = Math.max(1, getLineWrappingIndentation().getOrElse(8) / Math.max(1, getBasicOffset().getOrElse(4)));

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            boolean startsWithTab = !line.isEmpty() && line.charAt(0) == '\t';
            boolean hasLeadingSpaces = !line.isEmpty() && line.charAt(0) == ' ';

            int effectiveIndent = blockIndent;
            if (startsWithClosingBrace(trimmed)) {
                effectiveIndent = Math.max(0, effectiveIndent - 1);
            }

            if (parenthesisDepth > 0 && !startsWithClosingBrace(trimmed)) {
                effectiveIndent += continuationTabs;
            }

            if (trimmed.startsWith("case ") || trimmed.startsWith("default:")) {
                int caseOffset = Math.max(0, getCaseIndent().getOrElse(0) / Math.max(1, getBasicOffset().getOrElse(4)));
                effectiveIndent = Math.max(0, effectiveIndent - 1 + caseOffset);
            }

            // Keep existing tab-indented lines untouched to avoid reflowing multiline constructs.
            if (hasLeadingSpaces && !startsWithTab) {
                lines.set(i, "\t".repeat(Math.max(0, effectiveIndent)) + trimmed);
            }

            blockIndent = updateBlockIndent(blockIndent, trimmed);
            parenthesisDepth = updateParenthesisDepth(parenthesisDepth, trimmed);
        }

        return String.join("\n", lines);
    }

    private static boolean startsWithClosingBrace(String trimmed) {
        return trimmed.startsWith("}");
    }

    private static int updateBlockIndent(int currentIndent, String line) {
        int indent = currentIndent;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '{') {
                indent++;
            } else if (c == '}') {
                indent = Math.max(0, indent - 1);
            }
        }
        return indent;
    }

    private static int updateParenthesisDepth(int currentDepth, String line) {
        int depth = currentDepth;
        boolean inString = false;
        boolean inChar = false;
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
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
            if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
            }
        }
        return depth;
    }
}

