package com.communi.suggestu.formatum.adhibe.utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public record Region(
		Region[] children,
		boolean isParameterBlock,
		boolean isCodeBlock,
		boolean isArrayInitializer,
		boolean isStatement,
		boolean isControl,
		boolean isAnonymousClass,
		boolean isLambda,
		String content,
		String[] lines,
		RegionOffset start,
		RegionOffset end,
		RegionFinder.DebugMode debugMode) {

	public Region(
			final Region[] children,
			final boolean isParameterBlock,
			final boolean isCodeBlock,
			final boolean isArrayInitializer,
			final boolean isStatement,
			final boolean isControl,
			final boolean isAnonymousClass,
			final boolean isLambda,
			final String content,
			final RegionOffset start,
			final RegionOffset end,
			final RegionFinder.DebugMode debugMode) {
		this(
				children,
				isParameterBlock,
				isCodeBlock,
				isArrayInitializer,
				isStatement,
				isControl,
				isAnonymousClass,
				isLambda,
				content,
				splitLinesPreservingTrailingNewLines(content),
				start,
				end,
				debugMode
		);
	}

	private static String[] splitLinesPreservingTrailingNewLines(String content) {
		var linesStream = content.lines();
		if (content.endsWith("\n")) {
			linesStream = Stream.concat(linesStream, Stream.of(""));
		}

		return linesStream.toArray(String[]::new);
	}

	private boolean hasMultipleParameterChildren() {
		return Arrays.stream(children())
				.filter(Region::isParameterBlock)
				.count() > 1;
	}

	private boolean isMultiLineBrokenAssignmentStatement() {
		var indexOfAssignmentOperator = content().indexOf("=");
		//TODO: Handle comparison operators which contain "="
		if (indexOfAssignmentOperator == -1) {
			return false;
		}

		if (!isStatement()) {
			return false;
		}

		if (!isMultiLine()) {
			return false;
		}

		return Arrays.stream(children())
				.filter(Region::isParameterBlock)
				.filter(region -> region.start().lineOffset() != start().lineOffset() ||
						region.end().lineOffset() != end().lineOffset())
				.findFirst()
				.map(r -> r.start().totalCharacterOffset() > indexOfAssignmentOperator + start().totalCharacterOffset())
				.orElse(false);
	}

	private boolean hasAssignmentCoveringParameterBlock() {
		return Arrays.stream(this.children())
				.filter(Region::isParameterBlock)
				.anyMatch(region -> region.start().lineOffset() == this.start().lineOffset() &&
						region.end().lineOffset() == this.end().lineOffset());
	}

	private boolean isMultiLineAssignmentStatementSplitOnOperator() {
		return lines()[0].trim().endsWith("=") && isMultiLineBrokenAssignmentStatement();
	}

	private boolean isMultiLineMethodStatement() {
		return isStatement() && hasMultipleParameterChildren() && isMultiLine();
	}

	private int inLineStartOfLineIndex(int lineIndex) {
		lineIndex -= start.lineOffset();
		var line = content.split("\n")[lineIndex];
		return line.length() - line.trim().length();
	}

	public int regionDepthAtStartOfLine(final int lineIndex) {
		return regionDepthAtStartOfLine(lineIndex, new ArrayList<>());
	}

	public int regionDepthAtStartOfLine(final int lineIndex, List<String> depthReasons) {
		String line = getContentLine(lineIndex);
		if (line.trim().isEmpty()) {
			return 0;
		}

		var inLineStartOfLineIndex = inLineStartOfLineIndex(lineIndex);

		var depth = 0;

		//Check all entries to the target
		List<Region> chain = parentalRegionChain(lineIndex, inLineStartOfLineIndex);
		for (int i = 0; i < chain.size(); i++) {
			final Region previous = i != 0 ? chain.get(i - 1) : null;
			final Region region = chain.get(i);
			final Region next = i < (chain.size() - 1) ? chain.get(i + 1) : null;
			final Region secondNext = i < (chain.size() - 2) ? chain.get(i + 2) : null;

			//If we are a parameter block, bump by two
			if (region.isParameterBlock()) {
				if (next == null || !next.isCodeBlock() || next.start().lineOffset() != region.start().lineOffset()) {
					//We do not apply parameter indentation if it is closing operands only.
					if (next != null || region.isNotClosingOperandsOnly(lineIndex)) {
						depth++;
						if (debugMode == RegionFinder.DebugMode.RegionDepthTracking) {
							depthReasons.add("Parameter block depth increase 1");
						}
						depth++;
						if (debugMode == RegionFinder.DebugMode.RegionDepthTracking) {
							depthReasons.add("Parameter block depth increase 2");
						}
					}
				}
			}

			//We are in a assignment continuation
			if (region.isMultiLineBrokenAssignmentStatement()
					&& (previous == null || !previous.isParameterBlock())) {
				//We only apply statement depth if we are not just closing operands only on the line and
				//when we are not an assignment that is covered by the same parameter statement entirely.
				if (region.isNotClosingOperandsOnly(lineIndex) &&
						!region.hasAssignmentCoveringParameterBlock() &&
						(next == null || !next.isAnonymousClass())) {
					var inRegionLineOffset = region.inRegionLineOffset(lineIndex);
					if (inRegionLineOffset > 0) {
						depth++;
						if (debugMode == RegionFinder.DebugMode.RegionDepthTracking) {
							depthReasons.add("Multi-line broken assignment continuation depth increase 1");
						}
						depth++;
						if (debugMode == RegionFinder.DebugMode.RegionDepthTracking) {
							depthReasons.add("Multi-line broken assignment continuation depth increase 2");
						}
					}

					if (region.isMultiLineAssignmentStatementSplitOnOperator() && inRegionLineOffset > 1) {
						depth++;
						if (debugMode == RegionFinder.DebugMode.RegionDepthTracking) {
							depthReasons.add("Multi-line assignment split on operator depth increase");
						}
					}
				}
			}
			//We are in a statement continuation -> bump by at least one
			else if (region.isMultiLineMethodStatement()
					&& (previous == null || !previous.isParameterBlock())
					&& (!isLambdaParameterStartingOnSameLine(region, next, secondNext))
					&& !region.isStart(lineIndex)) {
				depth++;
				if (debugMode == RegionFinder.DebugMode.RegionDepthTracking) {
					depthReasons.add("Multi-line method statement continuation depth increase 1");
				}
				depth++;
				if (debugMode == RegionFinder.DebugMode.RegionDepthTracking) {
					depthReasons.add("Multi-line method statement continuation depth increase 2");
				}
			}

			//Any code block we are in also gets us one
			if (region.isCodeBlock() && !region.isIsolatedStartOrEndOperand(lineIndex)) {
				depth++;
				if (debugMode == RegionFinder.DebugMode.RegionDepthTracking) {
					depthReasons.add("Code block depth increase");
				}
			}
		}

		return depth;
	}

	private boolean isLambdaParameterStartingOnSameLine(final Region region, final Region target, final Region next) {
		if (!isLambdaParameter(target, next)) {
			return false;
		}

		//We know next is not null, that is a requisite from the isLambdaParameter
		return next.start().lineOffset() == region.start().lineOffset();
	}

	private boolean isLambdaParameter(final Region target, final Region next) {
		//A lambda in a parameter.
		return target != null && target.isParameterBlock() &&
				next != null && next.isCodeBlock() && next.isLambda();
	}

	private static final Pattern NOT_CLOSING_OPERANDS = Pattern.compile("[^});\\]]");
	private static final Pattern CLOSING_OPERANDS     = Pattern.compile("[});\\]]");

	private boolean isNotClosingOperandsOnly(final int lineIndex) {
		var contentLine = getContentLine(lineIndex);
		if (contentLine.trim().isEmpty()) {
			return true;
		}

		//This line is not allowed to match closing operands.
		return !CLOSING_OPERANDS.matcher(contentLine).find() || NOT_CLOSING_OPERANDS.matcher(contentLine).find();
	}

	private String getContentLine(final int lineIndex) {
		var regionLineIndex = lineIndex - start().lineOffset();
		if (regionLineIndex < 0) {
			throw new IllegalArgumentException(
					"The given global line index: " + lineIndex + " targets a line which is before this regions start line: " + start().lineOffset());
		}

		if (regionLineIndex >= lines().length) {
			throw new IllegalArgumentException(
					"The given global line index: " + lineIndex + " targets a line which is after this regions end line: " + end().lineOffset() + " with line count: "
							+ lines().length + " and start line: " + start().lineOffset());
		}

		return lines()[regionLineIndex].trim();
	}

	private boolean isMultiLine() {
		return start().lineOffset() != end().lineOffset();
	}

	private boolean isStart(int lineIndex) {
		return start().lineOffset() == lineIndex;
	}

	private int inRegionLineOffset(int lineIndex) {
		return lineIndex - start().lineOffset();
	}

	private List<Region> parentalRegionChain(final int lineIndex, final int firstCharacterIndex) {
		var chain = new ArrayList<Region>();
		if (!contains(lineIndex, firstCharacterIndex)) {
			return chain;
		}

		chain.add(this);

		for (final Region child : children()) {
			if (child.contains(lineIndex, firstCharacterIndex)) {
				chain.addAll(child.parentalRegionChain(lineIndex, firstCharacterIndex));
				break;
			}
		}

		return chain;
	}

	private boolean contains(final int lineIndex, final int firstCharacterIndex) {
		if (start().lineOffset() > lineIndex) {
			return false;
		}

		if (end().lineOffset() < lineIndex) {
			return false;
		}

		if (start().lineOffset() == lineIndex && start().inLineOffset() > firstCharacterIndex) {
			return false;
		}

		if (end().lineOffset() == lineIndex && end().inLineOffset() < firstCharacterIndex) {
			return false;
		}

		return true;
	}

	private boolean isIsolatedStartOrEndOperand(final int lineIndex) {
		if (lineIndex == start().lineOffset()) {
			return isIsolatedStartOpeningOperand();
		}

		if (lineIndex == end().lineOffset()) {
			return isIsolatedEndClosingOperand();
		}

		return false;
	}

	private boolean isIsolatedEndClosingOperand() {
		final var endLine = lines()[lines.length - 1].trim();
		return endLine.equals("}") || endLine.equals(")") || endLine.equals("]");
	}

	private boolean isIsolatedStartOpeningOperand() {
		final var startLine = lines()[0].trim();
		return startLine.equals("{") || startLine.equals("(") || startLine.equals("[");
	}
}
