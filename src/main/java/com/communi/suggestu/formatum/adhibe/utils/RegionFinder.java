package com.communi.suggestu.formatum.adhibe.utils;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public record RegionFinder(ParseMode parseMode, DebugMode debugMode) {

	public enum ParseMode {
		DEFAULT(false),
		HARD(true);

		private final boolean throwOnUnopenedStatements;

		ParseMode(final boolean throwOnUnopenedStatements) {this.throwOnUnopenedStatements = throwOnUnopenedStatements;}

		private boolean throwsOnUnopenedStatements() {
			return throwOnUnopenedStatements;
		}
	}

	public enum DebugMode {
		Off,
		RegionDepthTracking
	}

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

	private static String[] splitLinesPreservingTrailingNewLines(String content) {
		var linesStream = content.lines();
		if (content.endsWith("\n")) {
			linesStream = Stream.concat(linesStream, Stream.of(""));
		}

		return linesStream.toArray(String[]::new);
	}

	public record Region(
			Region[] children,
			boolean isParameterBlock,
			boolean isCodeBlock,
			boolean isArrayInitializer,
			boolean isStatement,
			boolean isControl,
			String content,
			String[] lines,
			RegionOffset start,
			RegionOffset end,
			DebugMode debugMode) {

		public Region(
				final Region[] children,
				final boolean isParameterBlock,
				final boolean isCodeBlock,
				final boolean isArrayInitializer,
				final boolean isStatement,
				final boolean isControl,
				final String content,
				final RegionOffset start,
				final RegionOffset end,
				final DebugMode debugMode) {
			this(
					children,
					isParameterBlock,
					isCodeBlock,
					isArrayInitializer,
					isStatement,
					isControl,
					content,
					splitLinesPreservingTrailingNewLines(content),
					start,
					end,
					debugMode
			);
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
			String line = content.split("\n")[lineIndex - start().lineOffset()];
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
							if (debugMode == DebugMode.RegionDepthTracking) {
								depthReasons.add("Parameter block depth increase 1");
							}
							depth++;
							if (debugMode == DebugMode.RegionDepthTracking) {
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
							!region.hasAssignmentCoveringParameterBlock()) {
						var inRegionLineOffset = region.inRegionLineOffset(lineIndex);
						if (inRegionLineOffset > 0) {
							depth++;
							if (debugMode == DebugMode.RegionDepthTracking) {
								depthReasons.add("Multi-line broken assignment continuation depth increase 1");
							}
							depth++;
							if (debugMode == DebugMode.RegionDepthTracking) {
								depthReasons.add("Multi-line broken assignment continuation depth increase 2");
							}
						}

						if (region.isMultiLineAssignmentStatementSplitOnOperator() && inRegionLineOffset > 1) {
							depth++;
							if (debugMode == DebugMode.RegionDepthTracking) {
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
					if (debugMode == DebugMode.RegionDepthTracking) {
						depthReasons.add("Multi-line method statement continuation depth increase 1");
					}
					depth++;
					if (debugMode == DebugMode.RegionDepthTracking) {
						depthReasons.add("Multi-line method statement continuation depth increase 2");
					}
				}

				//Any code block we are in also gets us one
				if (region.isCodeBlock() && !region.isIsolatedStartOrEndOperand(lineIndex)) {
					depth++;
					if (debugMode == DebugMode.RegionDepthTracking) {
						depthReasons.add("Code block depth increase");
					}
				}
			}

			return depth;
		}

		private boolean isLambdaParameterStartingOnSameLine(final Region region, final Region target, final Region next) {
			if (!isLambdaParameter(target, next))
				return false;

			//We know next is not null, that is a requisite from the isLambdaParameter
			return next.start().lineOffset() == region.start().lineOffset();
		}

		private boolean isLambdaParameter(final Region target, final Region next) {
			//A lambda in a parameter.
			return target != null && target.isParameterBlock() &&
					next != null && next.isCodeBlock();
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

	private record BracedRegionWrapper(
			int index,
			boolean isLambdaOrAnonymousClass) {}

	private record StatementStartWrapper(
			@Nullable Integer start,
			int codeBlockStart) {}

	private record ParameterRegionStartInfo(
			Integer index,
			boolean isParameter,
			@Nullable Integer annotationStart) {}

	public Region findRoot(String content) {
		boolean inString = false;
		boolean inChar = false;

		Deque<BracedRegionWrapper> bracedRegionStack = new ArrayDeque<>();
		Deque<ParameterRegionStartInfo> parameterizedRegionStack = new ArrayDeque<>();
		Deque<Integer> arrayInitializerStack = new ArrayDeque<>();
		Deque<StatementStartWrapper> statementStack = new ArrayDeque<>();

		Deque<List<Region>> childrenStack = new ArrayDeque<>();
		List<Region> currentChildren = new ArrayList<>();

		Integer statementStart = null;
		Character previousNoneWhitespaceC = null;

		boolean nextBraceStartsLambda = false;
		boolean nextBraceStartsAnonymousClass = false;
		boolean insideNewStatement = false;

		Integer annotationStart = null;

		contentLoop:
		for (int i = 0; i < content.length(); i++) {
			char c = content.charAt(i);
			boolean isWhitespace = !String.valueOf(c).trim().equals(String.valueOf(c));
			String surroundingContent = content.substring(Math.max(i - 4, 0), Math.min(i + 4, content.length() - 1));

			for (int j = i - 1; j > 0; j--) {
				char previous = content.charAt(j);
				if (String.valueOf(previous).trim().equals(String.valueOf(previous))) {
					previousNoneWhitespaceC = previous;
					break;
				}
			}

			char next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';
			char secondNext = i + 2 < content.length() ? content.charAt(i + 2) : '\0';
			if (!inString && !inChar && c == '/' && next == '/') {
				while (c != '\n') {
					i++;
					if (i == content.length()) {
						break contentLoop;
					}

					c = content.charAt(i);
				}
			}

			if (!inString && !inChar && c == '/' && next == '*' && secondNext == '*') {
				while (!(c == '*' && next == '/')) {
					i++;
					if (i == content.length()) {
						break contentLoop;
					}

					c = content.charAt(i);
					next = i + 1 < content.length() ? content.charAt(i + 1) : '\0';
					secondNext = i + 2 < content.length() ? content.charAt(i + 2) : '\0';
				}

				i++;
				continue;
			}

			if (!inChar && c == '"') {
				boolean escaped = i > 0 && content.charAt(i - 1) == '\\';
				if (!escaped) {
					inString = !inString;
				}
				continue;
			}

			if (!inString && c == '\'') {
				boolean escaped = i > 0 && content.charAt(i - 1) == '\\';
				if (!escaped) {
					inChar = !inChar;
				}
				continue;
			}

			if (inString || inChar) {
				continue;
			}

			//Reset anonymous class operator
			if (nextBraceStartsAnonymousClass && !isWhitespace && c != '{') {
				nextBraceStartsAnonymousClass = false;
			}

			//Reset lambda operator
			if (nextBraceStartsLambda && !isWhitespace && c != '{') {
				nextBraceStartsLambda = false;
			}

			if (isWhitespace && !insideNewStatement && i > 3 && content.startsWith("new ", i - 3)) {
				insideNewStatement = true;
			}
			else if (isWhitespace && i > 2 && content.charAt(i - 2) == '(' && content.charAt(i - 1) == ')' && insideNewStatement) {
				nextBraceStartsAnonymousClass = true;
				insideNewStatement = false;
			}
			else if (isWhitespace && insideNewStatement) {
				insideNewStatement = false;
			}

			// Lambda operator
			if (c == '>' && previousNoneWhitespaceC != null && previousNoneWhitespaceC == '-') {
				nextBraceStartsLambda = true;
			}

			if (c == '@' && annotationStart == null) {
				annotationStart = i;
			}

			if (c == '}') {
                if (bracedRegionStack.isEmpty()) {
                    throw new IllegalArgumentException("Found closing brace without opening companion!");
                }

				var bracedRegion = bracedRegionStack.pop();
				int bracedRegionStartIndex = bracedRegion.index();
				boolean isLambda = bracedRegion.isLambdaOrAnonymousClass();

				boolean performedStatementTerminationPrePop = false;
				if (statementStart == null && !statementStack.isEmpty() && statementStack.peek().codeBlockStart() == bracedRegionStartIndex) {
					statementStart = statementStack.pop().start();
					performedStatementTerminationPrePop = true;
				}

				var start = new RegionOffset(content, bracedRegionStartIndex);
				var end = new RegionOffset(content, i + 1);
				var regionContent = content.substring(start.totalCharacterOffset(), end.totalCharacterOffset());

				var region = new Region(
						currentChildren.toArray(new Region[0]),
						false,
						true,
						false,
						false,
						false,
						regionContent,
						start,
						end,
						debugMode()
				);

				currentChildren = childrenStack.pop();
				currentChildren.add(region);

				if (statementStart != null && !isLambda) {
					Region statement = createStatementLikeRegion(content, currentChildren, statementStart, i);

					statementStart = null;
					currentChildren = childrenStack.pop();
					currentChildren.add(statement);
				}

                if (!performedStatementTerminationPrePop) {
                    statementStart = statementStack.isEmpty() ? null : statementStack.pop().start();
                }

				continue;
			}

			if (c == '{') {
				bracedRegionStack.push(new BracedRegionWrapper(i, nextBraceStartsLambda || nextBraceStartsAnonymousClass));
				childrenStack.push(currentChildren);
				currentChildren = new ArrayList<>();

				nextBraceStartsLambda = false;
				nextBraceStartsAnonymousClass = false;

				statementStack.push(new StatementStartWrapper(statementStart, i));
				statementStart = null;
				continue;
			}

			if (c == '(') {
				boolean isParameterBlock = previousNoneWhitespaceC != null &&
						previousNoneWhitespaceC != ',' &&
						previousNoneWhitespaceC != '=' &&
						previousNoneWhitespaceC != '(';

				parameterizedRegionStack.push(new ParameterRegionStartInfo(i, isParameterBlock, annotationStart));
				annotationStart = null;

				if (isParameterBlock) {
					childrenStack.push(currentChildren);
					currentChildren = new ArrayList<>();
					continue;
				}
			}

			if (c == ')') {
                if (parameterizedRegionStack.isEmpty()) {
                    throw new IllegalArgumentException("Found closing parameter declaration without opening companion!");
                }

				var parameterInfo = parameterizedRegionStack.pop();

				if (parameterInfo.isParameter()) {
					var start = new RegionOffset(content, parameterInfo.index());
					var end = new RegionOffset(content, i + 1);
					var regionContent = content.substring(start.totalCharacterOffset(), end.totalCharacterOffset());

					var region = new Region(
							currentChildren.toArray(new Region[0]),
							true,
							false,
							false,
							false,
							false,
							regionContent,
							start,
							end,
							debugMode()
					);

					if (parameterInfo.annotationStart() != null) {
						start = new RegionOffset(content, parameterInfo.annotationStart());
						end = new RegionOffset(content, i + 1);
						regionContent = content.substring(start.totalCharacterOffset(), end.totalCharacterOffset());

						region = new Region(
								new Region[]{region},
								false,
								false,
								false,
								true,
								false,
								regionContent,
								start,
								end,
								debugMode()
						);

						if (statementStart != null && statementStart.equals(parameterInfo.annotationStart())) {
							statementStart = null;
							currentChildren = childrenStack.pop();
							currentChildren.add(region);
						}
					}

					currentChildren = childrenStack.pop();
					currentChildren.add(region);
					continue;
				}
			}

			if (c == '[') {
				arrayInitializerStack.push(i);
				childrenStack.push(currentChildren);
				currentChildren = new ArrayList<>();
				continue;
			}

			if (c == ']') {
                if (arrayInitializerStack.isEmpty()) {
                    throw new IllegalArgumentException("Found closing array initializer without opening companion!");
                }

				var start = new RegionOffset(content, arrayInitializerStack.pop());
				var end = new RegionOffset(content, i + 1);
				var regionContent = content.substring(start.totalCharacterOffset(), end.totalCharacterOffset());

				var region = new Region(
						currentChildren.toArray(new Region[0]),
						false,
						false,
						true,
						false,
						false,
						regionContent,
						start,
						end,
						debugMode()
				);

				currentChildren = childrenStack.pop();
				currentChildren.add(region);
				continue;
			}

			if (c == ';') {
				if (statementStart == null) {
					//We are not interested in regions outside of object structs.
                    if (bracedRegionStack.isEmpty()) {
                        continue;
                    }

                    if (parseMode().throwsOnUnopenedStatements()) {
                        throw new IllegalStateException("Found closing parameter declaration without opening companion!");
                    }

					continue;
				}

				Region region = createStatementLikeRegion(content, currentChildren, statementStart, i);

				statementStart = null;
				currentChildren = childrenStack.pop();
				currentChildren.add(region);
				continue;
			}

			if (statementStart == null && !bracedRegionStack.isEmpty() && !String.valueOf(c).trim().isEmpty()) {
				statementStart = i;
				childrenStack.push(currentChildren);
				currentChildren = new ArrayList<>();
				continue;
			}
		}

        if (!bracedRegionStack.isEmpty()) {
            throw new IllegalArgumentException("Found brace without closing companion! " + bracedRegionStack.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "))
            );
        }

        if (!parameterizedRegionStack.isEmpty()) {
            throw new IllegalArgumentException("Found parameter declaration without opening companion!");
        }

        if (!arrayInitializerStack.isEmpty()) {
            throw new IllegalArgumentException("Found array initializer without opening companion!");
        }

        if (!childrenStack.isEmpty()) {
            throw new IllegalArgumentException("Found children without closing companion! " + childrenStack.stream()
                    .map(Object::toString)
                    .collect(Collectors.joining(", "))
            );
        }

		return new Region(
				currentChildren.toArray(new Region[0]),
				false,
				false,
				false,
				false,
				false,
				content,
				new RegionOffset(content, 0),
				new RegionOffset(content, content.length()),
				debugMode()
		);
	}

	private Region createStatementLikeRegion(final String content, final List<Region> currentChildren, final Integer statementStart, final int i) {
		var start = new RegionOffset(content, statementStart);
		var end = new RegionOffset(content, i + 1);
		var regionContent = content.substring(start.totalCharacterOffset(), end.totalCharacterOffset());

		var isControl = CONTROL_BLOCK_START_REGEX.matcher(regionContent).find();

		return new Region(
				currentChildren.toArray(new Region[0]),
				false,
				false,
				false,
				!isControl,
				isControl,
				regionContent,
				start,
				end,
				debugMode()
		);
	}

	private static final Pattern CONTROL_BLOCK_START_REGEX = Pattern.compile("^(if|else|while|for|switch|try|catch|finally)\\s*\\(");
}
