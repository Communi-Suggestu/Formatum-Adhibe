package com.communi.suggestu.formatum.adhibe.utils;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public record RegionFinder(ParseMode parseMode, DebugMode debugMode) {
	public enum ParseMode {
		DEFAULT,
		HARD
	}

	public enum DebugMode {
		Off,
		RegionDepthTracking
	}

	public Region findRoot(String content) {
		var compiler = ToolProvider.getSystemJavaCompiler();
		if (compiler == null) {
			throw new IllegalStateException("Java region parsing requires a full JDK with the jdk.compiler module.");
		}

		// Formatter tests and callers may supply a member or a statement fragment.
		List<String> prefixes = List.of("", "class __FormatumFragment {\n",
				"class __FormatumFragment {\nvoid __fragment() {\n");
		String originalErrors = "";
		for (int nesting = 0; nesting < prefixes.size(); nesting++) {
			String prefix = prefixes.get(nesting);
			String source = prefix + content + "\n}".repeat(nesting);
			var diagnostics = new DiagnosticCollector<JavaFileObject>();
			var file = new SimpleJavaFileObject(URI.create("string:///FormatumSource.java"), JavaFileObject.Kind.SOURCE) {
				@Override
				public CharSequence getCharContent(boolean ignoreEncodingErrors) {
					return source;
				}
			};

			try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
				var task = (JavacTask) compiler.getTask(new StringWriter(), manager, diagnostics,
						List.of("-proc:none", "--enable-preview", "--source", Integer.toString(Runtime.version().feature())),
						null, List.of(file));
				var unit = task.parse().iterator().next();
				var errors = diagnostics.getDiagnostics().stream()
						.filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
						.toList();
				if (!errors.isEmpty()) {
					if (prefix.isEmpty()) {
						originalErrors = errors.stream()
								.map(diagnostic -> "line " + diagnostic.getLineNumber() + ":" + diagnostic.getColumnNumber()
										+ ": " + diagnostic.getMessage(Locale.ROOT))
								.reduce((left, right) -> left + "\n" + right).orElseThrow();
					}
					continue;
				}
				return new Builder(content, prefix.length(), unit, Trees.instance(task).getSourcePositions()).build();
			} catch (IOException exception) {
				throw new IllegalStateException("Unable to parse Java source regions.", exception);
			}
		}
		throw new IllegalArgumentException("Unable to parse Java source with JDK " + Runtime.version().feature() + ":\n" + originalErrors);
	}

	private enum Kind {
		ROOT, BLOCK, PARAMETER, ARRAY, STATEMENT, CONTROL, ANNOTATION, ENUM_CONSTANT
	}

	private static final class Span {
		private final int start;
		private final int end;
		private final Kind kind;
		private boolean anonymous;
		private boolean lambda;
		private final List<Span> children = new ArrayList<>();

		private Span(int start, int end, Kind kind) {
			this.start = start;
			this.end = end;
			this.kind = kind;
		}
	}

	private final class Builder extends TreePathScanner<Void, Void> {
		private final String content;
		private final int prefixLength;
		private final CompilationUnitTree unit;
		private final SourcePositions positions;
		private final List<JavaSourceTokens.Token> tokens;
		private final Map<Integer, JavaSourceTokens.Token> closingBraces;
		private final List<Span> spans = new ArrayList<>();

		private Builder(String content, int prefixLength, CompilationUnitTree unit, SourcePositions positions) {
			this.content = content;
			this.prefixLength = prefixLength;
			this.unit = unit;
			this.positions = positions;
			this.tokens = JavaSourceTokens.scan(content);
			this.closingBraces = tokens.stream().filter(token -> token.text().equals("}"))
					.collect(Collectors.toMap(JavaSourceTokens.Token::end, Function.identity()));
		}

		private int start(Tree tree) {
			return (int) positions.getStartPosition(unit, tree) - prefixLength;
		}

		private int end(Tree tree) {
			return (int) positions.getEndPosition(unit, tree) - prefixLength;
		}

		private Span add(int start, int end, Kind kind) {
			if (start < 0 || end > content.length() || start >= end) {
				return null;
			}
			var span = new Span(start, end, kind);
			spans.add(span);
			return span;
		}

		private Region build() {
			scan(unit, null);
			addDelimiters();
			spans.sort(Comparator.comparingInt((Span span) -> span.start)
					.thenComparing(Comparator.comparingInt((Span span) -> span.end).reversed())
					.thenComparingInt(span -> span.kind == Kind.STATEMENT || span.kind == Kind.CONTROL ? 0 : 1));
			var root = new Span(0, content.length(), Kind.ROOT);
			Deque<Span> parents = new ArrayDeque<>();
			parents.push(root);
			for (Span span : spans) {
				while (parents.peek() != root && span.start >= parents.peek().end) {
					parents.pop();
				}
				// javac represents comma-separated declarations as variables with a shared start.
				if (span.kind == Kind.STATEMENT && parents.peek().kind == Kind.STATEMENT && span.start == parents.peek().start) {
					continue;
				}
				if (span.end > parents.peek().end) {
					throw new IllegalStateException("Crossing Java source regions at offsets " + span.start + " and " + parents.peek().start);
				}
				parents.peek().children.add(span);
				parents.push(span);
			}
			return region(root);
		}

		private Region region(Span span) {
			return new Region(span.children.stream().map(this::region).toArray(Region[]::new),
					span.kind == Kind.PARAMETER, span.kind == Kind.BLOCK, span.kind == Kind.ARRAY,
					span.kind == Kind.STATEMENT || span.kind == Kind.ANNOTATION || span.kind == Kind.ENUM_CONSTANT,
					span.kind == Kind.CONTROL,
					span.anonymous, span.lambda, content.substring(span.start, span.end),
					new RegionOffset(content, span.start), new RegionOffset(content, span.end), debugMode);
		}

		@Override
		public Void visitClass(ClassTree tree, Void unused) {
			int end = end(tree);
			var block = addBody(tree);
			if (block != null) {
				block.anonymous = getCurrentPath().getParentPath().getLeaf() instanceof NewClassTree;
				if (start(tree) >= 0 && getCurrentPath().getParentPath().getLeaf() instanceof ClassTree) {
					add(start(tree), end, Kind.STATEMENT);
				}
				if (tree.getKind() == Tree.Kind.ENUM) {
					var constants = tree.getMembers().stream()
							.filter(member -> member instanceof VariableTree variable && isEnumConstant(variable))
							.toList();
					if (!constants.isEmpty()) {
						add(start(constants.getFirst()), end(constants.getLast()), Kind.STATEMENT);
					}
				}
			}
			return super.visitClass(tree, unused);
		}

		@Override
		public Void visitBlock(BlockTree tree, Void unused) {
			Span block = addBody(tree);
			if (block != null) {
				block.lambda = getCurrentPath().getParentPath().getLeaf() instanceof LambdaExpressionTree;
			}
			return super.visitBlock(tree, unused);
		}

		@Override
		public Void scan(Tree tree, Void unused) {
			if (tree != null) {
				switch (tree.getKind()) {
					case EXPRESSION_STATEMENT, RETURN, THROW, BREAK, CONTINUE, ASSERT, YIELD ->
							add(start(tree), end(tree), Kind.STATEMENT);
					case IF, FOR_LOOP, ENHANCED_FOR_LOOP, WHILE_LOOP, DO_WHILE_LOOP, SWITCH, SWITCH_EXPRESSION,
							TRY, CATCH, SYNCHRONIZED -> add(start(tree), end(tree), Kind.CONTROL);
					case ANNOTATION -> add(start(tree), end(tree), Kind.ANNOTATION);
					default -> {}
				}
				switch (tree.getKind()) {
					case SWITCH, SWITCH_EXPRESSION, NEW_ARRAY, MODULE -> addBody(tree);
					default -> {}
				}
			}
			return super.scan(tree, unused);
		}

		private Span addBody(Tree tree) {
			var closing = closingBraces.get(end(tree));
			if (closing == null || closing.companion() < start(tree)) {
				return null;
			}
			return add(closing.companion(), closing.end(), Kind.BLOCK);
		}

		@Override
		public Void visitVariable(VariableTree tree, Void unused) {
			Tree parent = getCurrentPath().getParentPath().getLeaf();
			var classClosing = parent instanceof ClassTree ? closingBraces.get(end(parent)) : null;
			boolean isField = classClosing != null && start(tree) > classClosing.companion() && !isEnumConstant(tree);
			if (parent instanceof BlockTree || isField) {
				add(start(tree), end(tree), Kind.STATEMENT);
			} else if (parent instanceof ClassTree && isEnumConstant(tree)
					&& tokens.stream().anyMatch(token -> token.start() >= start(tree) && token.end() <= end(tree) && token.text().equals("("))) {
				// Keep each constant's arguments out of the aggregate enum-list continuation policy.
				add(start(tree), end(tree), Kind.ENUM_CONSTANT);
			}
			return super.visitVariable(tree, unused);
		}

		private boolean isEnumConstant(VariableTree tree) {
			// javac gives an enum constant's synthetic type the same position as its constructor.
			return tree.getInitializer() instanceof NewClassTree creation && start(creation) == start(tree.getType());
		}

		private void addDelimiters() {
			for (int i = 0; i < tokens.size(); i++) {
				var token = tokens.get(i);
				if (token.text().equals("(")) {
					String previous = i == 0 ? "" : tokens.get(i - 1).text();
					if (!previous.equals(",") && !previous.equals("=") && !previous.equals("(")) {
						add(token.start(), token.companion(), Kind.PARAMETER);
					}
				} else if (token.text().equals("[")) {
					add(token.start(), token.companion(), Kind.ARRAY);
				}
			}
		}
	}
}
