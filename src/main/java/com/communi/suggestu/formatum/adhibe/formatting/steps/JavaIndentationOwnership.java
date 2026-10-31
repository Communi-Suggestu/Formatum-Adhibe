package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.utils.JavaSourceTokens;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CaseTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.MethodInvocationTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.SwitchTree;
import com.sun.source.tree.SwitchExpressionTree;
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
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Resolves continuation baselines from the AST owner instead of accumulating overlapping regions. */
final class JavaIndentationOwnership {
    private final int continuation;
    private final int throwsIndent;

    JavaIndentationOwnership(int basicOffset, int wrapping, int throwsIndent) {
        this.continuation = Math.max(1, wrapping / Math.max(1, basicOffset));
        this.throwsIndent = throwsIndent / Math.max(1, basicOffset);
    }

    static String normalizeSplitCaseLabels(String text) {
        String[] lines = text.split("\n", -1);
        String[] normalized = lines.clone();
        var tokens = JavaSourceTokens.scan(text);
        int line = 0;
        int start = 0;
        for (int i = 1; i + 1 < tokens.size(); i++) {
            var token = tokens.get(i);
            while (line + 1 < lines.length && start + lines[line].length() < token.start()) {
                start += lines[line++].length() + 1;
            }
            if (line > 0 && token.text().equals(":") && tokens.get(i + 1).text().equals("case")
                    && lines[line].stripLeading().startsWith(":")
                    && normalized[line - 1].stripLeading().startsWith("case ")
                    && text.substring(tokens.get(i - 1).end(), token.start()).isBlank()) {
                // Keep consecutive labels unambiguous across Checkstyle versions.
                normalized[line - 1] += ":";
                normalized[line] = lines[line].substring(0, token.start() - start)
                        + " " + lines[line].substring(token.start() - start + 1);
            }
        }
        return String.join("\n", normalized);
    }

    String apply(String text) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("Java indentation requires a full JDK.");
        }
        List<String> prefixes = List.of("", "class __FormatumFragment {\n",
                "class __FormatumFragment {\nvoid __fragment() {\n");
        for (int nesting = 0; nesting < prefixes.size(); nesting++) {
            String prefix = prefixes.get(nesting);
            String source = prefix + text + "\n}".repeat(nesting);
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var file = new SimpleJavaFileObject(URI.create("string:///FormatumIndentation.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            };
            try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
                var task = (JavacTask) compiler.getTask(new StringWriter(), manager, diagnostics,
                        List.of("-proc:none", "--enable-preview", "--source", Integer.toString(Runtime.version().feature())),
                        null, List.of(file));
                CompilationUnitTree unit = task.parse().iterator().next();
                if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
                    continue;
                }
                var collector = new Collector(text, prefix.length(), unit, Trees.instance(task).getSourcePositions());
                collector.scan(unit, null);
                return collector.result();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to resolve Java indentation ownership.", exception);
            }
        }
        throw new IllegalArgumentException("Unable to parse Java source for indentation ownership.");
    }

    private final class Collector extends TreePathScanner<Void, Void> {
        private final String text;
        private final int prefix;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final String[] lines;
        private final int[] starts;
        private final int[] depths;
        private final List<JavaSourceTokens.Token> tokens;

        Collector(String text, int prefix, CompilationUnitTree unit, SourcePositions positions) {
            this.text = text;
            this.prefix = prefix;
            this.unit = unit;
            this.positions = positions;
            lines = text.split("\n", -1);
            starts = new int[lines.length];
            depths = new int[lines.length];
            for (int i = 0; i < lines.length; i++) {
                if (i > 0) {
                    starts[i] = starts[i - 1] + lines[i - 1].length() + 1;
                }
                depths[i] = lines[i].length() - lines[i].stripLeading().length();
            }
            tokens = JavaSourceTokens.scan(text);
        }

        private int start(Tree tree) {
            return (int) positions.getStartPosition(unit, tree) - prefix;
        }

        private int end(Tree tree) {
            return (int) positions.getEndPosition(unit, tree) - prefix;
        }

        private int line(int offset) {
            int index = Arrays.binarySearch(starts, offset);
            return index < 0 ? -index - 2 : index;
        }

        private boolean valid(Tree tree) {
            return start(tree) >= 0 && end(tree) <= text.length() && end(tree) > start(tree);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            if (valid(tree) && !tree.getModifiers().getAnnotations().isEmpty()
                    && getCurrentPath().getParentPath().getLeaf() instanceof ClassTree) {
                int baseline = depths[line(start(tree))];
                int lastAnnotation = end(tree.getModifiers().getAnnotations().getLast());
                for (var annotation : tree.getModifiers().getAnnotations()) {
                    int target = line(start(annotation));
                    if (text.substring(starts[target], start(annotation)).isBlank()) {
                        depths[target] = baseline;
                    }
                }
                tokens.stream().filter(t -> t.start() >= lastAnnotation && t.start() < end(tree))
                        .findFirst().ifPresent(token -> {
                            int target = line(token.start());
                            if (text.substring(starts[target], token.start()).isBlank()) {
                                depths[target] = baseline;
                            }
                        });
            }
            return super.visitVariable(tree, unused);
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree tree, Void unused) {
            if (valid(tree) && tree.getBody() instanceof BlockTree body) {
                int header = line(start(tree));
                Tree parent = getCurrentPath().getParentPath().getLeaf();
                if (parent instanceof VariableTree variable && valid(variable) && header > line(start(variable))) {
                    depths[header] = depths[line(start(variable))] + continuation;
                }
                int closing = line(end(body) - 1);
                if (!body.getStatements().isEmpty()) {
                    int first = line(start(body.getStatements().getFirst()));
                    if (first > header) {
                        int delta = depths[header] + 1 - depths[first];
                        for (int i = first; i < closing; i++) {
                            depths[i] += delta;
                        }
                    }
                }
                if (closing > header && lines[closing].stripLeading().startsWith("}")) {
                    depths[closing] = depths[header];
                }
            }
            return super.visitLambdaExpression(tree, unused);
        }

        @Override
        public Void visitCase(CaseTree tree, Void unused) {
            if (valid(tree) && tree.getCaseKind() == CaseTree.CaseKind.RULE && tree.getBody() != null
                    && !(tree.getBody() instanceof BlockTree)) {
                int header = line(start(tree));
                int bodyLine = line(start(tree.getBody()));
                if (bodyLine > header) {
                    int delta = depths[header] + continuation - depths[bodyLine];
                    for (int i = bodyLine; i <= line(end(tree.getBody()) - 1); i++) {
                        depths[i] += delta;
                    }
                }
            }
            return super.visitCase(tree, unused);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            if (!valid(tree)) {
                return super.visitMethod(tree, unused);
            }
            int header = line(start(tree));
            int parameterOpening = tokens.stream()
                    .filter(t -> t.text().equals("(") && t.start() >= start(tree)
                            && (tree.getParameters().isEmpty() || t.end() <= start(tree.getParameters().getFirst())))
                    .reduce((first, second) -> second).map(t -> line(t.start())).orElse(header);
            for (VariableTree parameter : tree.getParameters()) {
                int target = line(start(parameter));
                if (target > parameterOpening) {
                    depths[target] = depths[header] + continuation;
                }
            }
            int signatureEnd = tree.getBody() == null ? end(tree) : start(tree.getBody());
            tokens.stream().filter(t -> t.text().equals(")") && t.start() >= start(tree) && t.end() <= signatureEnd)
                    .reduce((first, second) -> second).ifPresent(token -> {
                        int target = line(token.start());
                        if (lines[target].stripLeading().startsWith(")")) {
                            depths[target] = depths[header];
                        }
                    });
            if (!tree.getThrows().isEmpty()) {
                int before = start(tree.getThrows().getFirst());
                tokens.stream().filter(t -> t.text().equals("throws") && t.start() >= start(tree) && t.end() <= before)
                        .findFirst().ifPresent(token -> {
                            int target = line(token.start());
                            if (target > header && lines[target].stripLeading().startsWith("throws")) {
                                depths[target] = depths[header] + throwsIndent;
                            }
                        });
            }
            return super.visitMethod(tree, unused);
        }

        @Override
        public Void visitMethodInvocation(MethodInvocationTree tree, Void unused) {
            if (valid(tree)) {
                alignCallClosing(end(tree.getMethodSelect()), end(tree));
            }
            return super.visitMethodInvocation(tree, unused);
        }

        private void alignCallClosing(int afterSelect, int end) {
            tokens.stream().filter(t -> t.text().equals("(") && t.start() >= afterSelect && t.end() < end)
                    .findFirst().ifPresent(open -> {
                        int closing = line(end - 1);
                        if (closing > line(open.start()) && lines[closing].stripLeading().startsWith(")")) {
                            depths[closing] = depths[line(open.start())];
                        }
                    });
        }

        @Override
        public Void visitNewClass(NewClassTree tree, Void unused) {
            if (valid(tree) && tree.getClassBody() != null) {
                int closing = line(end(tree.getClassBody()) - 1);
                if (closing > line(start(tree)) && lines[closing].stripLeading().startsWith("}")) {
                    depths[closing] = depths[line(start(tree))];
                }
            }
            return super.visitNewClass(tree, unused);
        }

        @Override
        public Void visitSwitch(SwitchTree tree, Void unused) {
            alignCases(tree, tree.getCases());
            return super.visitSwitch(tree, unused);
        }

        @Override
        public Void visitSwitchExpression(SwitchExpressionTree tree, Void unused) {
            alignCases(tree, tree.getCases());
            return super.visitSwitchExpression(tree, unused);
        }

        private void alignCases(Tree tree, List<? extends CaseTree> cases) {
            if (!valid(tree)) {
                return;
            }
            int baseline = depths[line(start(tree))];
            for (CaseTree entry : cases) {
                int first = line(start(entry));
                int last = line(end(entry) - 1);
                if (!lines[first].stripLeading().startsWith("case ")
                        && !lines[first].stripLeading().startsWith("default")) {
                    continue;
                }
                int delta = baseline - depths[first];
                for (int i = first; i <= last; i++) {
                    depths[i] += delta;
                }
            }
            int closing = line(end(tree) - 1);
            if (lines[closing].stripLeading().startsWith("}")) {
                depths[closing] = baseline;
            }
        }

        String result() {
            for (int i = 0; i < lines.length; i++) {
                if (!lines[i].isBlank()) {
                    lines[i] = "\t".repeat(Math.max(0, depths[i])) + lines[i].stripLeading();
                }
            }
            return String.join("\n", lines);
        }
    }
}
