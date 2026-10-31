package com.communi.suggestu.formatum.adhibe.formatting.steps;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Minimal Java lexer. Comments and whitespace are skipped, literals are returned as single tokens and operators use
 * maximal munch with one exception: runs of {@code >} (optionally followed by {@code =}) are returned as one token so
 * the caller can decide whether they close generic type arguments or form a shift/relational operator.
 */
final class JavaLexer {
    enum Type { IDENT, KEYWORD, NUMBER, STRING, CHAR, OP }

    record Token(Type type, String text, int start, int end) {
        boolean is(String value) {
            return text.equals(value);
        }

        boolean isOp(String value) {
            return type == Type.OP && text.equals(value);
        }

        boolean isKeyword(String value) {
            return type == Type.KEYWORD && text.equals(value);
        }
    }

    private static final Set<String> KEYWORDS = Set.of(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class", "const", "continue",
            "default", "do", "double", "else", "enum", "extends", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native", "new", "package", "private",
            "protected", "public", "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false", "null");

    private static final String[] OPERATORS = {
            "<<=", "...", "->", "::", "++", "--", "&&", "||", "==", "!=", "<=", "+=", "-=", "*=", "/=", "&=", "|=",
            "^=", "%=", "<<"
    };

    private JavaLexer() {
    }

    static boolean isPrimitive(Token token) {
        return token.type() == Type.KEYWORD && switch (token.text()) {
            case "int", "long", "short", "byte", "char", "boolean", "float", "double" -> true;
            default -> false;
        };
    }

    static List<Token> lex(String text) {
        List<Token> tokens = new ArrayList<>();
        int n = text.length();
        int i = 0;
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isWhitespace(c)) {
                i++;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '/') {
                int eol = text.indexOf('\n', i);
                i = eol < 0 ? n : eol;
            } else if (c == '/' && i + 1 < n && text.charAt(i + 1) == '*') {
                int close = text.indexOf("*/", i + 2);
                i = close < 0 ? n : close + 2;
            } else if (c == '"') {
                int end = text.startsWith("\"\"\"", i) ? textBlockEnd(text, i) : quotedEnd(text, i, '"');
                tokens.add(new Token(Type.STRING, text.substring(i, end), i, end));
                i = end;
            } else if (c == '\'') {
                int end = quotedEnd(text, i, '\'');
                tokens.add(new Token(Type.CHAR, text.substring(i, end), i, end));
                i = end;
            } else if (Character.isDigit(c) || (c == '.' && i + 1 < n && Character.isDigit(text.charAt(i + 1)))) {
                int end = numberEnd(text, i);
                tokens.add(new Token(Type.NUMBER, text.substring(i, end), i, end));
                i = end;
            } else if (Character.isJavaIdentifierStart(c)) {
                int end = i + 1;
                while (end < n && Character.isJavaIdentifierPart(text.charAt(end))) {
                    end++;
                }
                String word = text.substring(i, end);
                tokens.add(new Token(KEYWORDS.contains(word) ? Type.KEYWORD : Type.IDENT, word, i, end));
                i = end;
            } else {
                int end = operatorEnd(text, i);
                tokens.add(new Token(Type.OP, text.substring(i, end), i, end));
                i = end;
            }
        }
        return tokens;
    }

    private static int operatorEnd(String text, int i) {
        if (text.charAt(i) == '>') {
            int end = i;
            while (end < text.length() && end - i < 3 && text.charAt(end) == '>') {
                end++;
            }
            if (end < text.length() && text.charAt(end) == '=') {
                end++;
            }
            return end;
        }
        for (String operator : OPERATORS) {
            if (text.startsWith(operator, i)) {
                return i + operator.length();
            }
        }
        return i + 1;
    }

    private static int quotedEnd(String text, int start, char quote) {
        int i = start + 1;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (c == quote) {
                return i + 1;
            } else if (c == '\n') {
                return i;
            } else {
                i++;
            }
        }
        return text.length();
    }

    private static int textBlockEnd(String text, int start) {
        int i = start + 3;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (c == '\\') {
                i += 2;
            } else if (text.startsWith("\"\"\"", i)) {
                return i + 3;
            } else {
                i++;
            }
        }
        return text.length();
    }

    private static int numberEnd(String text, int start) {
        int n = text.length();
        boolean hex = text.startsWith("0x", start) || text.startsWith("0X", start);
        int i = start;
        while (i < n) {
            char c = text.charAt(i);
            if (Character.isLetterOrDigit(c) || c == '_') {
                boolean exponent = hex ? (c == 'p' || c == 'P') : (c == 'e' || c == 'E');
                i++;
                if (exponent && i < n && (text.charAt(i) == '+' || text.charAt(i) == '-')) {
                    i++;
                }
            } else if (c == '.' && i + 1 < n && (Character.isDigit(text.charAt(i + 1)) || hex && Character.isLetter(text.charAt(i + 1)))) {
                i++;
            } else if (c == '.' && i > start && !(i + 1 < n && Character.isJavaIdentifierStart(text.charAt(i + 1)))
                    && !(i + 1 < n && text.charAt(i + 1) == '.')) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }
}
