package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;

public abstract class CollapseConsecutiveBlankLinesStep extends FormattingStep {
    @Inject
    public CollapseConsecutiveBlankLinesStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> TextFormattingUtils.normalizeNewlines(text)
                .replaceAll("\\n[\\t ]*\\n([\\t ]*\\n)+", "\n\n");
    }
}


