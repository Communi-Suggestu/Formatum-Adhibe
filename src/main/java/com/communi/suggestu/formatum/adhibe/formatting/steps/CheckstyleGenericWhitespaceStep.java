package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.List;
import java.util.TreeSet;

public abstract class CheckstyleGenericWhitespaceStep extends FormattingStep {
    @Inject
    public CheckstyleGenericWhitespaceStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        return normalizeCommas(normalizeGenericAngleWhitespace(text));
    }

    private static String normalizeGenericAngleWhitespace(String text) {
        StringBuilder builder = new StringBuilder(text);
        StringBuilder codeMask = new StringBuilder(codeMask(text));
        int lineStart = 0;
        while (lineStart < builder.length()) {
            int newline = builder.indexOf("\n", lineStart);
            int lineEnd = newline < 0 ? builder.length() : newline;
            int genericDepth = 0;
            boolean isGenericMethodCall = false;

            for (int i = lineStart; i < lineEnd; i++) {
                if (codeMask.charAt(i) != '1') {
                    continue;
                }
                char c = builder.charAt(i);
                boolean specialInstanceofType = c == '<' && isInstanceofGenericOpen(builder, i);
                if (c == '<' && isGenericOpen(builder, i, genericDepth, specialInstanceofType)) {
                    genericDepth++;
                    if (genericDepth == 1 && i > 0 && builder.charAt(i - 1) == '.') {
                        isGenericMethodCall = true;
                    }
                    if (specialInstanceofType) {
                        while (i > lineStart && (builder.charAt(i - 1) == ' ' || builder.charAt(i - 1) == '\t')) {
                            builder.deleteCharAt(i - 1);
                            codeMask.deleteCharAt(i - 1);
                            i--;
                            lineEnd--;
                            if (newline >= 0) {
                                newline--;
                            }
                        }
                    }
                    while (i + 1 < lineEnd && builder.charAt(i + 1) == ' ') {
                        builder.deleteCharAt(i + 1);
                        codeMask.deleteCharAt(i + 1);
                        lineEnd--;
                        if (newline >= 0) {
                            newline--;
                        }
                    }
                    continue;
                }

                if (c == '>' && genericDepth > 0) {
                    while (i - 1 >= lineStart && builder.charAt(i - 1) == ' ') {
                        builder.deleteCharAt(i - 1);
                        codeMask.deleteCharAt(i - 1);
                        i--;
                        lineEnd--;
                        if (newline >= 0) {
                            newline--;
                        }
                    }
                    genericDepth--;
                    if (isGenericMethodCall && genericDepth == 0) {
                        if (i + 1 < lineEnd
                                && builder.charAt(i + 1) == ' '
                                && i + 2 < lineEnd
                                && isIdentifierStart(builder.charAt(i + 2))) {
                            builder.deleteCharAt(i + 1);
                            codeMask.deleteCharAt(i + 1);
                            lineEnd--;
                            if (newline >= 0) {
                                newline--;
                            }
                        }
                        isGenericMethodCall = false;
                    }
                }
            }
            lineStart = newline < 0 ? builder.length() : newline + 1;
        }
        return builder.toString();
    }

    private static String codeMask(String text) {
        char[] mask = new char[text.length()];
        java.util.Arrays.fill(mask, '1');
        List<JavaLexer.Token> tokens = JavaLexer.lex(text);
        int tokenIndex = 0;
        int index = 0;
        while (index < text.length()) {
            while (tokenIndex < tokens.size() && tokens.get(tokenIndex).end() <= index) {
                tokenIndex++;
            }
            JavaLexer.Token token = tokenIndex < tokens.size() ? tokens.get(tokenIndex) : null;
            if (token != null && token.start() == index
                    && (token.type() == JavaLexer.Type.STRING || token.type() == JavaLexer.Type.CHAR)) {
                java.util.Arrays.fill(mask, token.start(), token.end(), '0');
                index = token.end();
                tokenIndex++;
            } else if (text.startsWith("//", index)) {
                int end = text.indexOf('\n', index);
                end = end < 0 ? text.length() : end;
                java.util.Arrays.fill(mask, index, end, '0');
                index = end;
            } else if (text.startsWith("/*", index)) {
                int close = text.indexOf("*/", index + 2);
                int end = close < 0 ? text.length() : close + 2;
                java.util.Arrays.fill(mask, index, end, '0');
                index = end;
            } else {
                index++;
            }
        }
        return new String(mask);
    }

    private static boolean isIdentifierStart(char c) {
        return c == '_' || c == '$' || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
    }

    private static boolean isGenericOpen(StringBuilder builder, int index, int genericDepth, boolean specialInstanceofType) {
        if (index + 1 < builder.length() && builder.charAt(index + 1) == '<') {
            // Avoid treating shift operators (<<) as generic openings.
            return false;
        }

        if (genericDepth > 0) {
            return true;
        }

        if (index == 0 || Character.isWhitespace(builder.charAt(index - 1))) {
            return specialInstanceofType;
        }

        char previous = builder.charAt(index - 1);
        return Character.isJavaIdentifierPart(previous)
                || previous == '>'
                || previous == '?'
                || previous == ']'
                || previous == '.';
    }

    private static boolean isInstanceofGenericOpen(StringBuilder builder, int index) {
        String prefix = builder.substring(0, index);
        return prefix.matches("(?s).*\\binstanceof\\s+(?:(?:@[\\w$]+(?:\\.[\\w$]+)*(?:\\s*\\([^)]*\\))?)\\s+|final\\s+)*[\\w$]+(?:\\.[\\w$]+)*\\s+");
    }

    private static String normalizeCommas(String text) {
        StringBuilder withoutSpaceBefore = new StringBuilder(text);
        List<JavaLexer.Token> tokens = JavaLexer.lex(text);
        for (int i = tokens.size() - 1; i >= 0; i--) {
            JavaLexer.Token token = tokens.get(i);
            if (!token.isOp(",")) {
                continue;
            }
            int start = token.start();
            while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) {
                start--;
            }
            if (start < token.start()) {
                withoutSpaceBefore.delete(start, token.start());
            }
        }

        String normalized = withoutSpaceBefore.toString();
        TreeSet<Integer> insertions = new TreeSet<>();
        for (JavaLexer.Token token : JavaLexer.lex(normalized)) {
            if (token.isOp(",") && token.end() < normalized.length()
                    && !Character.isWhitespace(normalized.charAt(token.end()))) {
                insertions.add(token.end());
            }
        }
        if (insertions.isEmpty()) {
            return normalized;
        }
        StringBuilder result = new StringBuilder(normalized.length() + insertions.size());
        int previous = 0;
        for (int insertion : insertions) {
            result.append(normalized, previous, insertion).append(' ');
            previous = insertion;
        }
        return result.append(normalized, previous, normalized.length()).toString();
    }
}
