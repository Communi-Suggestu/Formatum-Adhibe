package com.communi.suggestu.formatum.adhibe.utils;

public record RegionOffset(
		int totalCharacterOffset,
		int lineOffset,
		int inLineOffset) {

	private static int calculateLineOffset(String content, int totalCharacterOffset) {
		if (totalCharacterOffset == 0) {
			return 0;
		}

		return (int) content.substring(0, totalCharacterOffset).chars().filter(c -> c == '\n').count();
	}

	private static int calculateInLineOffset(String content, int totalCharacterOffset) {
		if (totalCharacterOffset == 0) {
			return 0;
		}

		var preContent = content.substring(0, totalCharacterOffset);
		var lastIndexOfNewLine = preContent.lastIndexOf('\n');
		if (lastIndexOfNewLine < 0) {
			return totalCharacterOffset;
		}

		var linePreContent = preContent.substring(lastIndexOfNewLine + 1);
		return linePreContent.length();
	}

	public RegionOffset(String content, int totalCharacterOffset) {
		this(totalCharacterOffset, calculateLineOffset(content, totalCharacterOffset), calculateInLineOffset(content, totalCharacterOffset));
	}
}
