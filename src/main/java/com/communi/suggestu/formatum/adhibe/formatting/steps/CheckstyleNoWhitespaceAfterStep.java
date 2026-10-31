package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

public abstract class CheckstyleNoWhitespaceAfterStep extends FormattingStep {
    @Inject
    public CheckstyleNoWhitespaceAfterStep() {
    }

    @Input
    public abstract ListProperty<String> getTokens();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        StringBuilder result = new StringBuilder(text.length());
        int position = 0;
        for (JavaLexer.Token token : JavaLexer.lex(text)) {
            if (!token.isOp(".") && !token.isOp("@") && !token.isOp("!") && !token.isOp("~")) {
                continue;
            }
            int end = token.end();
            while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
                end++;
            }
            result.append(text, position, token.end());
            position = end;
        }
        return result.append(text, position, text.length()).toString();
    }
}
