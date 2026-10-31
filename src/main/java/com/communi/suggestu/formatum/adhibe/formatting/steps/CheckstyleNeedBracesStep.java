package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.tree.EnhancedForLoopTree;
import com.sun.source.tree.ForLoopTree;
import com.sun.source.tree.IfTree;
import com.sun.source.tree.StatementTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.WhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class CheckstyleNeedBracesStep extends FormattingStep {
    @Inject
    public CheckstyleNeedBracesStep() {
    }

    @Input
    public abstract ListProperty<String> getTokens();

    @Input
    public abstract Property<Boolean> getAllowSingleLineStatement();

    @Input
    public abstract Property<Boolean> getAllowEmptyLoopBody();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> applyNeedBraces(TextFormattingUtils.normalizeNewlines(text));
    }

    private String applyNeedBraces(String text) {
        Set<String> enabled = new LinkedHashSet<>(getTokens().getOrElse(List.of(
                "LITERAL_DO", "LITERAL_ELSE", "LITERAL_FOR", "LITERAL_IF", "LITERAL_WHILE")));
        List<Edit> edits = new ArrayList<>();
        collectEdits(text, enabled, edits);
        edits.sort(Comparator.comparingInt(Edit::start).reversed());
        StringBuilder result = new StringBuilder(text);
        int previousStart = Integer.MAX_VALUE;
        for (Edit edit : edits) {
            if (edit.end() > previousStart) {
                continue;
            }
            result.replace(edit.start(), edit.end(), edit.replacement());
            previousStart = edit.start();
        }
        return result.toString();
    }

    private void collectEdits(String text, Set<String> enabled, List<Edit> edits) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("NeedBraces formatting requires a full JDK.");
        }
        List<String> prefixes = List.of("", "class __FormatumFragment {\n",
                "class __FormatumFragment {\nvoid __fragment() {\n");
        for (int nesting = 0; nesting < prefixes.size(); nesting++) {
            String prefix = prefixes.get(nesting);
            String source = prefix + text + "\n}".repeat(nesting);
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var file = new SimpleJavaFileObject(URI.create("string:///FormatumNeedBraces.java"), JavaFileObject.Kind.SOURCE) {
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
                    continue;
                }
                SourcePositions positions = Trees.instance(task).getSourcePositions();
                new TreePathScanner<Void, Void>() {
                    private void wrap(String token, StatementTree body, Tree control) {
                        if (!enabled.contains("LITERAL_" + token.toUpperCase(Locale.ROOT))
                                || body == null || body instanceof BlockTree
                                || (body instanceof EmptyStatementTree && isLoop(token) && getAllowEmptyLoopBody().getOrElse(false))
                                || (token.equals("else") && body instanceof IfTree)
                                || (getAllowSingleLineStatement().getOrElse(false) && sameLine(control, body))) {
                            return;
                        }
                        int bodyStart = (int) positions.getStartPosition(unit, body) - prefix.length();
                        int bodyEnd = (int) positions.getEndPosition(unit, body) - prefix.length();
                        int controlStart = (int) positions.getStartPosition(unit, control) - prefix.length();
                        if (controlStart < 0 || bodyStart < 0 || bodyEnd <= bodyStart || bodyEnd > text.length()) {
                            return;
                        }
                        int headerEnd = headerEnd(text, controlStart, bodyStart, token);
                        if (headerEnd < controlStart || headerEnd > bodyStart) {
                            return;
                        }

                        String controlIndent = indentationAt(text, controlStart);
                        String gap = text.substring(headerEnd, bodyStart);
                        String opening;
                        if (gap.isBlank()) {
                            opening = " {\n" + controlIndent + "\t";
                        } else {
                            opening = " {" + gap.stripTrailing() + "\n" + controlIndent + "\t";
                        }
                        edits.add(new Edit(headerEnd, bodyStart, opening));
                        edits.add(new Edit(bodyEnd, bodyEnd, "\n" + controlIndent + "}"));
                    }

                    @Override
                    public Void visitIf(IfTree tree, Void unused) {
                        wrap("if", tree.getThenStatement(), tree);
                        if (tree.getElseStatement() != null) {
                            wrap("else", tree.getElseStatement(), tree);
                        }
                        return super.visitIf(tree, unused);
                    }

                    @Override
                    public Void visitForLoop(ForLoopTree tree, Void unused) {
                        wrap("for", tree.getStatement(), tree);
                        return super.visitForLoop(tree, unused);
                    }

                    @Override
                    public Void visitEnhancedForLoop(EnhancedForLoopTree tree, Void unused) {
                        wrap("for", tree.getStatement(), tree);
                        return super.visitEnhancedForLoop(tree, unused);
                    }

                    @Override
                    public Void visitWhileLoop(WhileLoopTree tree, Void unused) {
                        wrap("while", tree.getStatement(), tree);
                        return super.visitWhileLoop(tree, unused);
                    }

                    @Override
                    public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) {
                        wrap("do", tree.getStatement(), tree);
                        return super.visitDoWhileLoop(tree, unused);
                    }

                    private boolean sameLine(Tree control, Tree body) {
                        int controlStart = (int) positions.getStartPosition(unit, control) - prefix.length();
                        int bodyStart = (int) positions.getStartPosition(unit, body) - prefix.length();
                        return controlStart >= 0 && bodyStart >= controlStart
                                && text.substring(controlStart, bodyStart).indexOf('\n') < 0;
                    }
                }.scan(unit, null);
                return;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to parse Java source for NeedBraces formatting.", exception);
            }
        }
        // Partial snippets can be malformed while a formatter pipeline is still normalizing them.
    }

    private static int headerEnd(String text, int controlStart, int bodyStart, String token) {
        List<JavaLexer.Token> tokens = JavaLexer.lex(text);
        JavaLexer.Token keyword = tokens.stream()
                .filter(t -> t.start() >= controlStart && t.start() < bodyStart && t.text().equals(token))
                .findFirst().orElse(null);
        if (keyword == null) {
            return -1;
        }
        if (token.equals("else") || token.equals("do")) {
            return keyword.end();
        }
        int opening = -1;
        int depth = 0;
        for (JavaLexer.Token candidate : tokens) {
            if (candidate.start() < keyword.end() || candidate.start() >= bodyStart) {
                continue;
            }
            if (candidate.text().equals("(")) {
                if (opening < 0) {
                    opening = candidate.start();
                }
                depth++;
            } else if (candidate.text().equals(")") && opening >= 0) {
                depth--;
                if (depth == 0) {
                    return candidate.end();
                }
            }
        }
        return -1;
    }

    private static String indentationAt(String text, int offset) {
        int lineStart = text.lastIndexOf('\n', Math.max(0, offset - 1)) + 1;
        int cursor = lineStart;
        while (cursor < text.length() && cursor < offset
                && (text.charAt(cursor) == ' ' || text.charAt(cursor) == '\t')) {
            cursor++;
        }
        return text.substring(lineStart, cursor);
    }

    private static boolean isLoop(String token) {
        return token.equals("for") || token.equals("while") || token.equals("do");
    }

    private record Edit(int start, int end, String replacement) {
    }
}
