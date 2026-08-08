package com.communi.suggestu.formatum.adhibe.utils;

import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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

	private record BracedRegionWrapper(
			int index,
			boolean isLambda,
			boolean isAnonymousClass) {}

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

			//Determine whether we are in the new operator statement.
			if (isWhitespace && !insideNewStatement && i > 3 && content.startsWith("new ", i - 3)) {
				insideNewStatement = true;
			}
			else if (isWhitespace && i > 2 && content.charAt(i - 2) == '(' && content.charAt(i - 1) == ')' && insideNewStatement) {
				//We are in a new statement, and we discovered the pre-conditions for an anonymous class.
				nextBraceStartsAnonymousClass = true;
				insideNewStatement = false;
			}
			else if (isWhitespace && insideNewStatement) {
				//Lost the new statement conditions -> Object creation.
				insideNewStatement = false;
			}

			// Determine where a lambda starts
			if (c == '>' && previousNoneWhitespaceC != null && previousNoneWhitespaceC == '-') {
				nextBraceStartsLambda = true;
			}

			// Determine where the annotations start
			if (c == '@' && annotationStart == null) {
				annotationStart = i;
			}

			// Closing of a code block.
			if (c == '}') {
                if (bracedRegionStack.isEmpty()) {
					//No open code block -> Illegal state, if we close it we must have it opened.
                    throw new IllegalArgumentException("Found closing brace without opening companion!");
                }

				//Grab the opening status from the stack.
				var bracedRegion = bracedRegionStack.pop();
				int bracedRegionStartIndex = bracedRegion.index();

				//We track this directly because we need to handle the statement logic inside it.
				boolean isLambdaOrAnonymousClass = bracedRegion.isLambda() || bracedRegion.isAnonymousClass();

				//We are ending a codeblock -> This is an implicit end to a statement as well.
				//If we did not have a statement open for some reason, check the current statement stack, whether that starts at the same place as our
				//current codeblock, if so, then close that statement, but mark that we pre-popped the statement.
				//This happens regularly for enums, as they do not need to terminate with a ; so the statement needs to be processed!
				boolean performedStatementTerminationPrePop = false;
				if (statementStart == null && !statementStack.isEmpty() && statementStack.peek().codeBlockStart() == bracedRegionStartIndex) {
					statementStart = statementStack.pop().start();
					performedStatementTerminationPrePop = true;
				}

				//We have a not closed statement (so no ; before the }, excluding whitespaces)
				if (statementStart != null && !isLambdaOrAnonymousClass && statementStart > bracedRegionStartIndex) {
					//Close the statement first, then handle that.
					Region statement = createStatementLikeRegion(content, currentChildren, statementStart, i);

					statementStart = null;
					currentChildren = childrenStack.pop();
					currentChildren.add(statement);
				}

				//Create the region.
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
						bracedRegion.isAnonymousClass(),
						bracedRegion.isLambda(),
						regionContent,
						start,
						end,
						debugMode()
				);

				currentChildren = childrenStack.pop();
				currentChildren.add(region);

				//UNSURE: this feels flaky and I can't remember why we need it, but it seem to pass tests, so it covers some regresssions.
                if (!performedStatementTerminationPrePop) {
                    statementStart = statementStack.isEmpty() ? null : statementStack.pop().start();
                }

				continue;
			}

			//Start a new code block.
			if (c == '{') {
				bracedRegionStack.push(new BracedRegionWrapper(i, nextBraceStartsLambda, nextBraceStartsAnonymousClass));
				childrenStack.push(currentChildren);
				currentChildren = new ArrayList<>();

				//Reset state trackers.
				nextBraceStartsLambda = false;
				nextBraceStartsAnonymousClass = false;

				//A new code block, also means a new statement start!
				statementStack.push(new StatementStartWrapper(statementStart, i));
				statementStart = null;
				continue;
			}

			//Start of a parameter block.
			if (c == '(') {
				//Double check the previous none whitespace character, as it might like an inline pair of brackets to handle if switches etc.
				boolean isParameterBlock = previousNoneWhitespaceC != null &&
						previousNoneWhitespaceC != ',' &&
						previousNoneWhitespaceC != '=' &&
						previousNoneWhitespaceC != '(';

				//Create and pus the parameter region!
				parameterizedRegionStack.push(new ParameterRegionStartInfo(i, isParameterBlock, annotationStart));
				annotationStart = null;

				//Handle the parameter block
				if (isParameterBlock) {
					childrenStack.push(currentChildren);
					currentChildren = new ArrayList<>();
					continue;
				}
			}

			//Closing of the parameter block.
			if (c == ')') {
                if (parameterizedRegionStack.isEmpty()) {
                    throw new IllegalArgumentException("Found closing parameter declaration without opening companion!");
                }

				//Pop it of.
				var parameterInfo = parameterizedRegionStack.pop();

				if (parameterInfo.isParameter()) {
					//if it is a parameter block, then handle that directly.
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
							false,
							false,
							regionContent,
							start,
							end,
							debugMode()
					);

					if (parameterInfo.annotationStart() != null) {
						//If it is also an annotation block, then handle that afterwards, as the annotation wraps its parameter block.
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
								false,
								false,
								regionContent,
								start,
								end,
								debugMode()
						);

						//Handle the statement information inside the annotation block.
						if (statementStart != null && statementStart.equals(parameterInfo.annotationStart())) {
							statementStart = null;
							currentChildren = childrenStack.pop();
							currentChildren.add(region);
						}
					}

					//Pop the statement.
					currentChildren = childrenStack.pop();
					currentChildren.add(region);
					continue;
				}
			}

			//Handle array start.
			if (c == '[') {
				arrayInitializerStack.push(i);
				childrenStack.push(currentChildren);
				currentChildren = new ArrayList<>();
				continue;
			}

			//Handle array closure.
			if (c == ']') {
                if (arrayInitializerStack.isEmpty()) {
                    throw new IllegalArgumentException("Found closing array initializer without opening companion!");
                }

				//Pop the array information.
				var start = new RegionOffset(content, arrayInitializerStack.pop());
				var end = new RegionOffset(content, i + 1);
				var regionContent = content.substring(start.totalCharacterOffset(), end.totalCharacterOffset());

				//Create the region.
				var region = new Region(
						currentChildren.toArray(new Region[0]),
						false,
						false,
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

				currentChildren = childrenStack.pop();
				currentChildren.add(region);
				continue;
			}

			//In-Line statement end.
			if (c == ';') {
				//Code outside of the root code block is not relevant here, (package and import statements).
				if (statementStart == null) {
					//We are not interested in regions outside of object structs.
                    if (bracedRegionStack.isEmpty()) {
                        continue;
                    }

					//Helps with debugging.
                    if (parseMode().throwsOnUnopenedStatements()) {
                        throw new IllegalStateException("Found closing parameter declaration without opening companion!");
                    }

					continue;
				}

				//Create the region
				Region region = createStatementLikeRegion(content, currentChildren, statementStart, i);

				statementStart = null;
				currentChildren = childrenStack.pop();
				currentChildren.add(region);
				continue;
			}

			//Start a statement when we are in the first block.
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

		//Validate that we properly processed the entire code.
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

		//Return the root.
		return new Region(
				currentChildren.toArray(new Region[0]),
				false,
				false,
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
				false,
				false,
				regionContent,
				start,
				end,
				debugMode()
		);
	}

	private static final Pattern CONTROL_BLOCK_START_REGEX = Pattern.compile("^(if|else|while|for|switch|try|catch|finally)\\s*\\(");
}
