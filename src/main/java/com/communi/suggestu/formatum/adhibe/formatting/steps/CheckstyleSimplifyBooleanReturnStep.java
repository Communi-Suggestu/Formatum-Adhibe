package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.utils.JavaSourceTokens;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.ParenthesizedTree;
import com.sun.source.tree.ReturnTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.YieldTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** Replaces opposing literal branches without dropping evaluation of their condition. */
public abstract class CheckstyleSimplifyBooleanReturnStep extends FormattingStep {
    @Inject
    public CheckstyleSimplifyBooleanReturnStep() {}

    @Override
    public FileFormatter formatter() {
        return (file, source) -> apply(TextFormattingUtils.normalizeNewlines(source));
    }

    private String apply(String text) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("SimplifyBooleanReturn formatting requires a full JDK.");
        }
        List<String> prefixes = List.of("", "class __FormatumFragment {\n",
                "class __FormatumFragment {\nvoid __fragment() {\n");
        String errors = "";
        for (int nesting = 0; nesting < prefixes.size(); nesting++) {
            String prefix = prefixes.get(nesting);
            String source = prefix + text + "\n}".repeat(nesting);
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var file = new SimpleJavaFileObject(URI.create("string:///FormatumBooleanReturn.java"), JavaFileObject.Kind.SOURCE) {
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
                if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
                    if (nesting == 0) {
                        errors = diagnostics.getDiagnostics().toString();
                    }
                    continue;
                }
                var positions = Trees.instance(task).getSourcePositions();
                var tokens = JavaSourceTokens.scan(text);
                var edits = new ArrayList<Edit>();
                new TreeScanner<Void, Void>() {
                    private int start(Tree tree) {
                        return (int) positions.getStartPosition(unit, tree) - prefix.length();
                    }

                    private int end(Tree tree) {
                        return (int) positions.getEndPosition(unit, tree) - prefix.length();
                    }

                    @Override
                    public Void visitIf(IfTree tree, Void unused) {
                        Branch thenBranch = branch(tree.getThenStatement());
                        Branch elseBranch = branch(tree.getElseStatement());
                        if (thenBranch == null || elseBranch == null
                                || thenBranch.value() == elseBranch.value()
                                || !thenBranch.keyword().equals(elseBranch.keyword())) {
                            return super.visitIf(tree, unused);
                        }
                        int from = start(tree);
                        int to = end(tree);
                        if (from < 0 || to > text.length()) {
                            return super.visitIf(tree, unused);
                        }
                        Tree condition = tree.getCondition();
                        // javac includes the mandatory if-condition parentheses.
                        if (condition instanceof ParenthesizedTree parenthesized) {
                            condition = parenthesized.getExpression();
                        }
                        int conditionStart = start(condition);
                        int conditionEnd = end(condition);
                        String expression = text.substring(conditionStart, conditionEnd);
                        String replacement = thenBranch.keyword() + " "
                                + (thenBranch.value() ? expression : "!(" + expression + ")") + ";";
                        String indent = indentation(text, from);
                        var comments = new ArrayList<String>();
                        int cursor = from;
                        for (var token : tokens) {
                            if (token.start() < from || token.end() > to) {
                                continue;
                            }
                            if (token.start() > cursor && !(cursor >= conditionStart && token.start() <= conditionEnd)) {
                                String gap = text.substring(cursor, token.start()).strip();
                                if (!gap.isEmpty()) {
                                    comments.add(gap);
                                }
                            }
                            cursor = token.end();
                        }
                        if (!comments.isEmpty()) {
                            // A block keeps comments inside an unbraced parent and preserves dangling-else ownership.
                            replacement = "{\n" + indent + "\t"
                                    + String.join("\n" + indent + "\t", comments)
                                    + "\n" + indent + "\t" + replacement + "\n" + indent + "}";
                        }
                        edits.add(new Edit(from, to, replacement));
                        return null;
                    }
                }.scan(unit, null);
                var result = new StringBuilder(text);
                for (Edit edit : edits.stream().sorted(Comparator.comparingInt(Edit::start).reversed()).toList()) {
                    result.replace(edit.start(), edit.end(), edit.text());
                }
                return result.toString();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to parse SimplifyBooleanReturn source.", exception);
            }
        }
        throw new IllegalArgumentException("Unable to parse SimplifyBooleanReturn source: " + errors);
    }

    private static Branch branch(StatementTree statement) {
        if (statement instanceof BlockTree block && block.getStatements().size() == 1) {
            statement = block.getStatements().getFirst();
        }
        Tree expression;
        String keyword;
        if (statement instanceof ReturnTree returnTree) {
            expression = returnTree.getExpression();
            keyword = "return";
        } else if (statement instanceof YieldTree yieldTree) {
            expression = yieldTree.getValue();
            keyword = "yield";
        } else {
            return null;
        }
        if (expression instanceof LiteralTree literal && literal.getValue() instanceof Boolean value) {
            return new Branch(keyword, value);
        }
        return null;
    }

    private static String indentation(String text, int offset) {
        int start = text.lastIndexOf('\n', offset) + 1;
        int end = start;
        while (end < offset && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return text.substring(start, end);
    }

    private record Branch(String keyword, boolean value) {}
    private record Edit(int start, int end, String text) {}
}
