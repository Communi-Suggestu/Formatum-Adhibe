package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class LeadingWhitespaceToTabsStep extends FormattingStep {
    @Inject
    public LeadingWhitespaceToTabsStep() {
    }

    @Input
    public abstract Property<Integer> getTabWidth();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> Arrays.stream(TextFormattingUtils.normalizeNewlines(text).split("\n", -1))
                .map(this::normalizeIndentation)
                .collect(Collectors.joining("\n"));
    }

    private String normalizeIndentation(String line) {
        if (line.isBlank()) {
            return line;
        }

        String stripped = line.stripLeading();
        if (stripped.startsWith("* ") || stripped.startsWith("*/") || stripped.equals("*")) {
            return line;
        }

        int tabWidth = getTabWidth().get();
        int indentColumns = TextFormattingUtils.countIndentColumns(line, tabWidth);
        int contentStart = 0;
        while (contentStart < line.length()) {
            char character = line.charAt(contentStart);
            if (character != ' ' && character != '\t') {
                break;
            }
            contentStart++;
        }

        int tabs = indentColumns / tabWidth;
        int spaces = indentColumns % tabWidth;
        return "\t".repeat(tabs) + " ".repeat(spaces) + line.substring(contentStart);
    }
}


