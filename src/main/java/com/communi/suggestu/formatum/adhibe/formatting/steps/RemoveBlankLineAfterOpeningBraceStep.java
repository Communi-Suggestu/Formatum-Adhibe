package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;

public abstract class RemoveBlankLineAfterOpeningBraceStep extends FormattingStep {
    @Inject
    public RemoveBlankLineAfterOpeningBraceStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> TextFormattingUtils.normalizeNewlines(text)
                .replaceAll("\\{[\\t ]*\\n[\\t ]*\\n", "{\n");
    }
}


