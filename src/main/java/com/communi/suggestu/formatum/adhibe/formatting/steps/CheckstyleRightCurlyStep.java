package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class CheckstyleRightCurlyStep extends FormattingStep {
    @Inject
    public CheckstyleRightCurlyStep() {
    }

    @Input
    public abstract Property<String> getOption();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> applyRightCurlyPolicy(TextFormattingUtils.normalizeNewlines(text));
    }

    private String applyRightCurlyPolicy(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        String option = getOption().getOrElse("same");
        return switch (option) {
            case "alone" -> enforceAlone(lines, false);
            case "alone_or_singleline" -> enforceAlone(lines, true);
            default -> enforceSame(lines);
        };
    }

    private String enforceSame(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int closeIndex = line.indexOf('}');
            if (closeIndex < 0) {
                continue;
            }

            String before = line.substring(0, closeIndex).trim();
            String after = line.substring(closeIndex + 1).trim();
            if (!before.isEmpty()) {
                continue;
            }

            if (!after.isEmpty() && !startsWithContinuationKeyword(after)) {
                String indent = line.substring(0, closeIndex);
                lines.set(i, indent + "}");
                lines.add(i + 1, indent + after);
                i++;
                continue;
            }

            if (after.isEmpty() && i + 1 < lines.size()) {
                // Skip empty lines to find the next continuation keyword
                int nextNonEmptyIndex = i + 1;
                while (nextNonEmptyIndex < lines.size() && lines.get(nextNonEmptyIndex).trim().isEmpty()) {
                    nextNonEmptyIndex++;
                }

                if (nextNonEmptyIndex < lines.size()) {
                    String next = lines.get(nextNonEmptyIndex).trim();
                    if (startsWithContinuationKeyword(next)) {
                        // Remove all empty lines between } and continuation keyword
                        String indent = line.substring(0, closeIndex);
                        String continuationLine = lines.get(nextNonEmptyIndex);
                        String continuationIndent = continuationLine.substring(0, continuationLine.length() - continuationLine.trim().length());
                        lines.set(i, indent + "} " + next);
                        // Remove empty lines and the continuation line
                        for (int j = nextNonEmptyIndex; j > i; j--) {
                            lines.remove(j);
                        }
                    } else if (startsWithClosingDelimiterContinuation(next)) {
                        String indent = line.substring(0, closeIndex);
                        lines.set(i, indent + "}" + next);
                        for (int j = nextNonEmptyIndex; j > i; j--) {
                            lines.remove(j);
                        }
                    }
                }
            }
        }

        return String.join("\n", lines);
    }

    private String enforceAlone(List<String> lines, boolean allowSingleLineBlock) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int closeIndex = line.indexOf('}');
            if (closeIndex < 0) {
                continue;
            }

            if (allowSingleLineBlock && line.contains("{") && closeIndex > line.indexOf('{')) {
                continue;
            }

            String before = line.substring(0, closeIndex);
            String after = line.substring(closeIndex + 1).trim();
            if (!after.isEmpty()) {
                // There's content after the }, split them onto separate lines
                String indent = indentation(before);
                lines.set(i, indent + "}");
                lines.add(i + 1, indent + after);
                i++;
                continue;
            }

            if (!before.trim().isEmpty()) {
                // There's content before the }, extract indent and place } on its own line with correct indentation
                String indent = indentation(before);
                lines.set(i, indent + "}");
            }
        }

        return String.join("\n", lines);
    }

    private static boolean startsWithContinuationKeyword(String text) {
        return text.startsWith("else")
                || text.startsWith("catch")
                || text.startsWith("finally")
                || text.startsWith("while");
    }

    private static boolean startsWithClosingDelimiterContinuation(String text) {
        return text.startsWith(")") || text.startsWith("]");
    }

    private static String indentation(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return text.substring(0, i);
    }
}

