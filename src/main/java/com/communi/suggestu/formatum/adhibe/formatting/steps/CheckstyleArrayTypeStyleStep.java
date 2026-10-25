package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.utils.JavaSourceTokens;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Normalizes array declaration style to the Java {@code long[]} form by removing whitespace
 * between a type name (or closing bracket of an earlier dimension) and the opening square
 * bracket, e.g. {@code long [] value} becomes {@code long[] value}. Only pure-whitespace gaps
 * are collapsed; comments or literals in between leave the source untouched.
 */
public abstract class CheckstyleArrayTypeStyleStep extends FormattingStep {
    @Inject
    public CheckstyleArrayTypeStyleStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private static String apply(String text) {
        List<JavaSourceTokens.Token> tokens;
        try {
            tokens = JavaSourceTokens.scan(text);
        } catch (IllegalArgumentException e) {
            // Source fragment is not reliably tokenizable; leave it untouched.
            return text;
        }

        List<int[]> deletions = new ArrayList<>();
        for (int i = 1; i < tokens.size(); i++) {
            JavaSourceTokens.Token opening = tokens.get(i);
            if (!opening.text().equals("[")) {
                continue;
            }
            JavaSourceTokens.Token previous = tokens.get(i - 1);
            if (!isArrayDeclarationContext(previous) || previous.end() >= opening.start()) {
                continue;
            }
            String gap = text.substring(previous.end(), opening.start());
            if (gap.isEmpty() || !gap.chars().allMatch(Character::isWhitespace)) {
                continue;
            }
            deletions.add(new int[]{previous.end(), opening.start()});
        }

        if (deletions.isEmpty()) {
            return text;
        }

        StringBuilder result = new StringBuilder(text.length());
        int position = 0;
        for (int[] deletion : deletions) {
            result.append(text, position, deletion[0]);
            position = deletion[1];
        }
        result.append(text, position, text.length());
        return result.toString();
    }

    /**
     * A bracket directly follows a type name ({@code long}, {@code Foo}) or the closing bracket
     * of an earlier array dimension ({@code int [][]}). Both are declaration positions where
     * Java style keeps {@code []} adjacent to the preceding token.
     */
    private static boolean isArrayDeclarationContext(JavaSourceTokens.Token previous) {
        if (previous.text().isEmpty()) {
            return false;
        }
        char last = previous.text().charAt(previous.text().length() - 1);
        return Character.isJavaIdentifierPart(last) || last == ']';
    }
}
