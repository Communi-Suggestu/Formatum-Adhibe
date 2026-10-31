package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Inserts the whitespace Checkstyle's {@code WhitespaceAround} check demands: for every selected token, a space is
 * added where the character directly before or after the token is not whitespace. Like the check, it only ever adds
 * whitespace and honors the {@code tokens} property (Checkstyle's default tokens when empty) as well as the
 * {@code allowEmpty*} and {@code ignoreEnhancedForColon} options with their Checkstyle defaults.
 */
public abstract class CheckstyleWhitespaceAroundStep extends FormattingStep {
    @Inject
    public CheckstyleWhitespaceAroundStep() {
        getAllowEmptyConstructors().convention(false);
        getAllowEmptyMethods().convention(false);
        getAllowEmptyTypes().convention(false);
        getAllowEmptyLoops().convention(false);
        getAllowEmptyLambdas().convention(false);
        getAllowEmptyCatches().convention(false);
        getAllowEmptySwitchBlockStatements().convention(false);
        getIgnoreEnhancedForColon().convention(true);
    }

    /** Checkstyle token names to check; empty selects Checkstyle's default tokens. */
    @Input
    public abstract ListProperty<String> getTokens();

    @Input
    public abstract Property<Boolean> getAllowEmptyConstructors();

    @Input
    public abstract Property<Boolean> getAllowEmptyMethods();

    @Input
    public abstract Property<Boolean> getAllowEmptyTypes();

    @Input
    public abstract Property<Boolean> getAllowEmptyLoops();

    @Input
    public abstract Property<Boolean> getAllowEmptyLambdas();

    @Input
    public abstract Property<Boolean> getAllowEmptyCatches();

    @Input
    public abstract Property<Boolean> getAllowEmptySwitchBlockStatements();

    @Input
    public abstract Property<Boolean> getIgnoreEnhancedForColon();

    @Override
    public FileFormatter formatter() {
        Set<WhitespaceAroundToken> tokens = WhitespaceAroundToken.resolve(getTokens().getOrElse(List.of()));
        WhitespaceAroundAnalyzer.Options options = new WhitespaceAroundAnalyzer.Options(
                getAllowEmptyConstructors().get(),
                getAllowEmptyMethods().get(),
                getAllowEmptyTypes().get(),
                getAllowEmptyLoops().get(),
                getAllowEmptyLambdas().get(),
                getAllowEmptyCatches().get(),
                getAllowEmptySwitchBlockStatements().get(),
                getIgnoreEnhancedForColon().get());
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text), tokens, options);
    }

    static String apply(String text, Set<WhitespaceAroundToken> tokens, WhitespaceAroundAnalyzer.Options options) {
        TreeSet<Integer> insertions = new TreeSet<>();
        for (WhitespaceAroundAnalyzer.Site site : WhitespaceAroundAnalyzer.analyze(text, options)) {
            if (!tokens.contains(site.token())) {
                continue;
            }
            if (!site.skipBefore() && site.start() > 0 && !Character.isWhitespace(text.charAt(site.start() - 1))) {
                insertions.add(site.start());
            }
            if (!site.skipAfter() && site.end() < text.length() && !Character.isWhitespace(text.charAt(site.end()))) {
                insertions.add(site.end());
            }
        }
        if (insertions.isEmpty()) {
            return text;
        }
        StringBuilder out = new StringBuilder(text.length() + insertions.size());
        int last = 0;
        for (int index : insertions) {
            out.append(text, last, index).append(' ');
            last = index;
        }
        return out.append(text, last, text.length()).toString();
    }
}
