package com.communi.suggestu.formatum.adhibe.utils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

/**
 * Locates punctuation omitted from javac's public AST; syntax and ownership come from javac.
 */
final class JavaSourceTokens {
	private JavaSourceTokens() {}

	record Token(String text, int start, int end, int companion) {}

	static List<Token> scan(String source) {
		Translated translated = translate(source);
		source = translated.text();
		List<Token> tokens = new ArrayList<>();
		Deque<Integer> delimiters = new ArrayDeque<>();
		for (int i = 0; i < source.length();) {
			char c = source.charAt(i);
			if (Character.isWhitespace(c)) {
				i++;
				continue;
			}
			if (source.startsWith("//", i)) {
				while (i < source.length() && source.charAt(i) != '\n' && source.charAt(i) != '\r') {
					i++;
				}
				continue;
			}
			if (source.startsWith("/*", i)) {
				int end = source.indexOf("*/", i + 2);
				if (end < 0) {
					throw new IllegalArgumentException("Unclosed Java comment at offset " + translated.offsets()[i]);
				}
				i = end + 2;
				continue;
			}
			int start = i++;
			if (c == '"' || c == '\'') {
				boolean textBlock = c == '"' && source.startsWith("\"\"\"", start);
				boolean closed = false;
				if (textBlock) {
					i = start + 3;
				}
				while (i < source.length()) {
					if (source.charAt(i) == '\\') {
						i = Math.min(i + 2, source.length());
					} else if (textBlock ? source.startsWith("\"\"\"", i) : source.charAt(i) == c) {
						i += textBlock ? 3 : 1;
						closed = true;
						break;
					} else {
						i++;
					}
				}
				if (!closed) {
					throw new IllegalArgumentException("Unclosed Java literal at offset " + translated.offsets()[start]);
				}
			} else if (Character.isJavaIdentifierStart(c)) {
				while (i < source.length() && Character.isJavaIdentifierPart(source.charAt(i))) {
					i++;
				}
			}
			String text = source.substring(start, i);
			int rawStart = translated.offsets()[start];
			int rawEnd = translated.offsets()[i];
			int companion = -1;
			if (text.equals(")") || text.equals("]") || text.equals("}")) {
				if (delimiters.isEmpty()) {
					throw new IllegalArgumentException("Unmatched Java delimiter at offset " + rawStart);
				}
				int openingIndex = delimiters.pop();
				Token opening = tokens.get(openingIndex);
				if (!(opening.text().equals("(") && text.equals(")")
						|| opening.text().equals("[") && text.equals("]")
						|| opening.text().equals("{") && text.equals("}"))) {
					throw new IllegalArgumentException("Mismatched Java delimiter at offset " + rawStart);
				}
				companion = opening.start();
				tokens.set(openingIndex, new Token(opening.text(), opening.start(), opening.end(), rawEnd));
			}
			tokens.add(new Token(text, rawStart, rawEnd, companion));
			if (text.equals("(") || text.equals("[") || text.equals("{")) {
				delimiters.push(tokens.size() - 1);
			}
		}
		if (!delimiters.isEmpty()) {
			throw new IllegalArgumentException("Unclosed Java delimiter at offset " + tokens.get(delimiters.peek()).start());
		}
		return tokens;
	}

	private record Translated(String text, int[] offsets) {}

	// Java translates eligible Unicode escapes before recognizing comments and literals.
	private static Translated translate(String source) {
		StringBuilder text = new StringBuilder();
		int[] offsets = new int[source.length() + 1];
		int backslashes = 0;
		boolean previousWasEscape = false;
		for (int i = 0; i < source.length();) {
			offsets[text.length()] = i;
			char c = source.charAt(i++);
			boolean escaped = false;
			if (c == '\\' && (previousWasEscape || backslashes % 2 == 0) && i < source.length() && source.charAt(i) == 'u') {
				int hexStart = i;
				while (hexStart < source.length() && source.charAt(hexStart) == 'u') {
					hexStart++;
				}
				if (hexStart + 4 > source.length()) {
					throw new IllegalArgumentException("Incomplete Java Unicode escape at offset " + (i - 1));
				}
				int value = 0;
				for (int j = hexStart; j < hexStart + 4; j++) {
					int digit = Character.digit(source.charAt(j), 16);
					if (digit < 0) {
						throw new IllegalArgumentException("Invalid Java Unicode escape at offset " + (i - 1));
					}
					value = value * 16 + digit;
				}
				c = (char) value;
				i = hexStart + 4;
				escaped = true;
			}
			text.append(c);
			backslashes = c == '\\' ? backslashes + 1 : 0;
			previousWasEscape = escaped;
		}
		offsets[text.length()] = source.length();
		return new Translated(text.toString(), offsets);
	}
}
