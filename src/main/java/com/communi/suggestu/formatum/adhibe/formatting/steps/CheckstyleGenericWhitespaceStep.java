package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;

public abstract class CheckstyleGenericWhitespaceStep extends FormattingStep {
    @Inject
    public CheckstyleGenericWhitespaceStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        return normalizeGenericAngleWhitespace(text)
                .replaceAll(",(?=\\S)", ", ")
                .replaceAll("\\s+,", ",");
    }

    private static String normalizeGenericAngleWhitespace(String text) {
        String[] lines = text.split("\\n", -1);
        for (int i = 0; i < lines.length; i++) {
            lines[i] = normalizeGenericAngleWhitespaceOnLine(lines[i]);
        }
        return String.join("\n", lines);
    }

    private static String normalizeGenericAngleWhitespaceOnLine(String line) {
        StringBuilder builder = new StringBuilder(line);
        int genericDepth = 0;
        boolean isGenericMethodCall = false;

        for (int i = 0; i < builder.length(); i++) {
            char c = builder.charAt(i);
            if (c == '<' && isGenericOpen(builder, i, genericDepth)) {
                genericDepth++;
                // When entering depth 1, check if preceded by a dot —
                // that indicates a generic method invocation (Foo.<Bar>),
                // not a type annotation (Foo<Bar>).
                if (genericDepth == 1 && i > 0 && builder.charAt(i - 1) == '.') {
                    isGenericMethodCall = true;
                }
                while (i + 1 < builder.length() && builder.charAt(i + 1) == ' ') {
                    builder.deleteCharAt(i + 1);
                }
                continue;
            }

            if (c == '>' && genericDepth > 0) {
                // Remove any space before this closing angle bracket (inside generics).
                while (i - 1 >= 0 && builder.charAt(i - 1) == ' ') {
                    builder.deleteCharAt(i - 1);
                    i--;
                }
                genericDepth--;
                // When exiting depth 1 to 0 after a generic method call,
                // strip the space (if any) between the closing > and the
                // following method name, producing "Foo.<Bar>baz()" instead
                // of "Foo.<Bar> baz()".
                if (isGenericMethodCall && genericDepth == 0) {
                    if (i + 1 < builder.length()
                            && builder.charAt(i + 1) == ' '
                            && i + 2 < builder.length()
                            && isIdentifierStart(builder.charAt(i + 2))) {
                        builder.deleteCharAt(i + 1);
                    }
                    isGenericMethodCall = false;
                }
            }
        }

        return builder.toString();
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || c == '$' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isGenericOpen(StringBuilder builder, int index, int genericDepth) {
        if (index + 1 < builder.length() && builder.charAt(index + 1) == '<') {
            // Avoid treating shift operators (<<) as generic openings.
            return false;
        }

        if (genericDepth > 0) {
            return true;
        }

        if (index == 0 || Character.isWhitespace(builder.charAt(index - 1))) {
            return false;
        }

        char previous = builder.charAt(index - 1);
        return Character.isJavaIdentifierPart(previous)
                || previous == '>'
                || previous == '?'
                || previous == ']'
                || previous == '.';
    }
}

