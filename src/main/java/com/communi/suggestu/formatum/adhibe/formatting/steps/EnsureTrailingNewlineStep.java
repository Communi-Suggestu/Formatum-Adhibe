package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;

public abstract class EnsureTrailingNewlineStep extends FormattingStep {
    @Inject
    public EnsureTrailingNewlineStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> {
            if (text.isEmpty() || text.endsWith("\n") || text.endsWith("\r\n")) {
                return text;
            }
            return text + "\n";
        };
    }
}


