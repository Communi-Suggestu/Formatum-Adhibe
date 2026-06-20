package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public abstract class CheckstyleWhitespaceAroundStep extends FormattingStep {
    @Inject
    public CheckstyleWhitespaceAroundStep() {
    }

    @Input
    public abstract ListProperty<String> getTokens();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        String result = text;
        result = result.replaceAll("(?<![<>=!])=(?!=)", " = ");
        result = result.replaceAll("(?<![<])<(?![<=])", " < ");
        result = result.replaceAll("(?<![>])>(?![>=])", " > ");
        result = result.replaceAll("(?<![!])!=(?!=)", " != ");
        result = result.replaceAll("==", " == ");
        result = result.replaceAll("[ \\t]{2,}", " ");
        result = result.replaceAll(" ?\\{", " {");
        return result;
    }
}


