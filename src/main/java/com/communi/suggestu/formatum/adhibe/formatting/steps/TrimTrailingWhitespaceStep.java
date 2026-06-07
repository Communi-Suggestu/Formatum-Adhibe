package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.Arrays;
import java.util.stream.Collectors;

public abstract class TrimTrailingWhitespaceStep extends FormattingStep {
    @Inject
    public TrimTrailingWhitespaceStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> Arrays.stream(TextFormattingUtils.normalizeNewlines(text).split("\n", -1))
                .map(String::stripTrailing)
                .collect(Collectors.joining("\n"));
    }
}


