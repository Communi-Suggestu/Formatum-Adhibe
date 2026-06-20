package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public abstract class CheckstyleEmptyLineSeparatorStep extends FormattingStep {
    private static final Pattern PACKAGE_PATTERN = Pattern.compile("^\\s*package\\b.*");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\b.*");
    private static final Pattern TYPE_PATTERN = Pattern.compile("^\\s*(public|protected|private|abstract|final|static|sealed|non-sealed)?\\s*(class|interface|enum|record)\\b.*");

    @Inject
    public CheckstyleEmptyLineSeparatorStep() {
    }

    @Input
    public abstract Property<Boolean> getAllowNoEmptyLineBetweenFields();

    @Input
    public abstract Property<Boolean> getAllowMultipleEmptyLines();

    @Input
    public abstract ListProperty<String> getTokens();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));

        if (!getAllowMultipleEmptyLines().getOrElse(false)) {
            collapseMultipleEmptyLines(lines);
        }

        ensurePackageImportAndTypeSpacing(lines);

        return String.join("\n", lines);
    }

    private static void collapseMultipleEmptyLines(List<String> lines) {
        for (int i = 1; i < lines.size(); i++) {
            if (lines.get(i).trim().isEmpty() && lines.get(i - 1).trim().isEmpty()) {
                lines.remove(i);
                i--;
            }
        }
    }

    private static void ensurePackageImportAndTypeSpacing(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String current = lines.get(i);
            if (PACKAGE_PATTERN.matcher(current).matches()) {
                int next = nextNonEmptyLine(lines, i + 1);
                if (next >= 0 && (IMPORT_PATTERN.matcher(lines.get(next)).matches() || TYPE_PATTERN.matcher(lines.get(next)).matches())) {
                    ensureExactlyOneBlankLineBetween(lines, i, next);
                }
                continue;
            }

            if (IMPORT_PATTERN.matcher(current).matches()) {
                int next = nextNonEmptyLine(lines, i + 1);
                if (next >= 0 && TYPE_PATTERN.matcher(lines.get(next)).matches()) {
                    ensureExactlyOneBlankLineBetween(lines, i, next);
                }
            }
        }
    }

    private static void ensureExactlyOneBlankLineBetween(List<String> lines, int firstIndex, int secondIndex) {
        int gap = secondIndex - firstIndex - 1;
        if (gap == 1 && lines.get(firstIndex + 1).trim().isEmpty()) {
            return;
        }

        for (int i = secondIndex - 1; i > firstIndex; i--) {
            lines.remove(i);
        }
        lines.add(firstIndex + 1, "");
    }

    private static int nextNonEmptyLine(List<String> lines, int fromIndex) {
        for (int i = fromIndex; i < lines.size(); i++) {
            if (!lines.get(i).trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }
}

