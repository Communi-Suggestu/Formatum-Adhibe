package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.List;

/**
 * Removes modifiers that Checkstyle's {@code RedundantModifier} reports, e.g. {@code public abstract}
 * on interface methods, {@code static} on nested enums or {@code final} on methods of final classes.
 * Mirrors Checkstyle's {@code jdkVersion} and {@code tokens} properties.
 */
public abstract class CheckstyleRedundantModifierStep extends FormattingStep {
    @Inject
    public CheckstyleRedundantModifierStep() {
        getJdkVersion().convention(RedundantModifierFixer.DEFAULT_JDK_VERSION);
    }

    /** JDK version the code targets; gates the {@code strictfp} (17+) and unnamed variable (22+) rules. */
    @Input
    public abstract Property<Integer> getJdkVersion();

    /** Checkstyle token names to visit; empty means all acceptable tokens. */
    @Input
    public abstract ListProperty<String> getTokens();

    public static int parseJdkVersion(String value) {
        return RedundantModifierFixer.parseJdkVersion(value);
    }

    @Override
    public FileFormatter formatter() {
        RedundantModifierFixer fixer = new RedundantModifierFixer(
                getJdkVersion().get(),
                RedundantModifierFixer.Visit.fromTokens(getTokens().getOrElse(List.of())));
        return (fileName, text) -> fixer.apply(TextFormattingUtils.normalizeNewlines(text));
    }
}
