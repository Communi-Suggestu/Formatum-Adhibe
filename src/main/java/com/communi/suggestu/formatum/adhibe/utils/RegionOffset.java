package com.communi.suggestu.formatum.adhibe.utils;

public record RegionOffset(
		int totalCharacterOffset,
		int lineOffset,
		int inLineOffset) {

	private static int calculateLineOffset(String content, int totalCharacterOffset) {
		if (totalCharacterOffset == 0) {
			return 0;
		}

		if (totalCharacterOffset == content.length()) {
			var contentLineCount = content.split("\n").length;
			if (content.endsWith("\n")) {
				return contentLineCount;
			}

			return contentLineCount - 1;
		}

		var preContent = content.substring(0, totalCharacterOffset);
		var lastIndexOfNewLine = preContent.lastIndexOf('\n');
		if (lastIndexOfNewLine < 0) {
			return 0;
		}

		int contentLineCount = (int) preContent.lines().count();
		if (preContent.endsWith("\n")) {
			return contentLineCount;
		}

		return contentLineCount - 1;
	}

	private static int calculateInLineOffset(String content, int totalCharacterOffset) {
		if (totalCharacterOffset == 0) {
			return 0;
		}

		if (totalCharacterOffset == content.length()) {
			if (!content.contains("\n")) {
				return content.length() - 1;
			}
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
