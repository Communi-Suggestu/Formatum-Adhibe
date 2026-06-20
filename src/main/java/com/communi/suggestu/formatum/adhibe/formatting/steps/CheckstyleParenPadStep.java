package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public abstract class CheckstyleParenPadStep extends FormattingStep {
    @Inject
    public CheckstyleParenPadStep() {
    }

    @Input
    public abstract Property<String> getOption();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        String option = getOption().getOrElse("nospace");
        if ("space".equalsIgnoreCase(option)) {
            return text
                    .replaceAll("\\((?![ )\\n])", "( ")
                    .replaceAll("(?<![ (\\n])\\)", " )");
        }

        return text
                .replaceAll("\\( +", "(")
                .replaceAll(" +\\)", ")");
    }
}

