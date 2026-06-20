package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public abstract class CheckstyleNoWhitespaceBeforeStep extends FormattingStep {
    @Inject
    public CheckstyleNoWhitespaceBeforeStep() {
    }

    @Input
    public abstract ListProperty<String> getTokens();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        return text
                .replaceAll("(\\S)[ \\t]+([,;\\)\\]\\}])", "$1$2")
                .replaceAll("(\\S)[ \\t]+([,;\\)\\]\\}])", "$1$2")
                .replaceAll("(\\S)[ \\t]+\\.", "$1.");
    }
}


