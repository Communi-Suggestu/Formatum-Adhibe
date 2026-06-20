package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;

public abstract class CheckstyleGenericWhitespaceStep extends FormattingStep {
    @Inject
    public CheckstyleGenericWhitespaceStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        return text
                .replaceAll("< +", "<")
                .replaceAll(" +>", ">")
                .replaceAll(",(?=\\S)", ", ")
                .replaceAll("\\s+,", ",");
    }
}

