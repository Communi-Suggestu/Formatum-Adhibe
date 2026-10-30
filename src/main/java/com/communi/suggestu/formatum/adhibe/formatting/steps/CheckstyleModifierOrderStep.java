package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;

/**
 * Reorders declaration modifiers into Checkstyle's {@code ModifierOrder} canonical order.
 */
public abstract class CheckstyleModifierOrderStep extends FormattingStep {
    @Inject
    public CheckstyleModifierOrderStep() {
    }

    @Override
    public FileFormatter formatter() {
        ModifierOrderFixer fixer = new ModifierOrderFixer();
        return (fileName, text) -> fixer.apply(TextFormattingUtils.normalizeNewlines(text));
    }
}
