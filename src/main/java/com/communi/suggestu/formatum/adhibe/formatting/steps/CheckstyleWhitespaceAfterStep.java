package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.TypeCastTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.StringWriter;
import java.net.URI;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public abstract class CheckstyleWhitespaceAfterStep extends FormattingStep {
    private static final Set<String> DEFAULT_TOKENS = Set.of("COMMA", "SEMI", "TYPECAST");

    @Inject
    public CheckstyleWhitespaceAfterStep() {
    }

    @Input
    public abstract ListProperty<String> getTokens();

    @Override
    public FileFormatter formatter() {
        Set<String> configuredTokens = Set.copyOf(getTokens().getOrElse(List.of()));
        Set<String> tokens = configuredTokens.isEmpty() ? DEFAULT_TOKENS : configuredTokens;
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text), tokens);
    }

    private static String apply(String text, Set<String> tokens) {
        TreeSet<Integer> insertions = new TreeSet<>();
        List<JavaLexer.Token> sourceTokens = JavaLexer.lex(text);

        for (JavaLexer.Token token : sourceTokens) {
            if (token.isOp(",") && tokens.contains("COMMA")
                    || token.isOp(";") && tokens.contains("SEMI")
                    || token.isOp("...") && tokens.contains("ELLIPSIS")) {
                addInsertionAfter(text, token.end(), insertions);
            } else if (token.type() == JavaLexer.Type.KEYWORD) {
                String checkstyleToken = switch (token.text()) {
                    case "if" -> "LITERAL_IF";
                    case "for" -> "LITERAL_FOR";
                    case "while" -> "LITERAL_WHILE";
                    case "switch" -> "LITERAL_SWITCH";
                    case "catch" -> "LITERAL_CATCH";
                    case "synchronized" -> "LITERAL_SYNCHRONIZED";
                    case "try" -> "LITERAL_TRY";
                    default -> null;
                };
                if (checkstyleToken != null && tokens.contains(checkstyleToken)
                        && token.end() < text.length() && text.charAt(token.end()) == '(') {
                    addInsertionAfter(text, token.end(), insertions);
                }
            }
        }

        if (tokens.contains("TYPECAST")) {
            findCastClosings(text, insertions);
        }

        if (insertions.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length() + insertions.size());
        int previous = 0;
        for (int insertion : insertions) {
            result.append(text, previous, insertion).append(' ');
            previous = insertion;
        }
        return result.append(text, previous, text.length()).toString();
    }

    private static void addInsertionAfter(String text, int offset, Set<Integer> insertions) {
        if (offset < text.length() && !Character.isWhitespace(text.charAt(offset))) {
            insertions.add(offset);
        }
    }

    private static void findCastClosings(String text, Set<Integer> insertions) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return;
        }

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (var manager = compiler.getStandardFileManager(diagnostics, null, null)) {
            var source = new SimpleJavaFileObject(URI.create("string:///FormatumWhitespaceAfter.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return text;
                }
            };
            var task = (JavacTask) compiler.getTask(new StringWriter(), manager, diagnostics,
                    List.of("-proc:none"), null, List.of(source));
            CompilationUnitTree unit = task.parse().iterator().next();
            var positions = Trees.instance(task).getSourcePositions();
            List<JavaLexer.Token> sourceTokens = JavaLexer.lex(text);
            new TreePathScanner<Void, Void>() {
                @Override
                public Void visitTypeCast(TypeCastTree node, Void unused) {
                    long start = positions.getStartPosition(unit, node);
                    long typeEnd = positions.getEndPosition(unit, node.getType());
                    long expressionStart = positions.getStartPosition(unit, node.getExpression());
                    if (start >= 0 && typeEnd >= start && expressionStart >= typeEnd) {
                        JavaLexer.Token closingParenthesis = null;
                        for (JavaLexer.Token token : sourceTokens) {
                            if (token.start() >= typeEnd && token.end() <= expressionStart
                                    && token.isOp(")")) {
                                closingParenthesis = token;
                            }
                        }
                        if (closingParenthesis != null) {
                            addInsertionAfter(text, closingParenthesis.end(), insertions);
                        }
                    }
                    return super.visitTypeCast(node, unused);
                }
            }.scan(unit, null);
        } catch (RuntimeException | java.io.IOException ignored) {
            // Delimiter and keyword tokens can still be fixed if javac cannot parse this input.
        }
    }
}
