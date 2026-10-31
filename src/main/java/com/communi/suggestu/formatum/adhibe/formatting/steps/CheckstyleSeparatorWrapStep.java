package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.EmptyStatementTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public abstract class CheckstyleSeparatorWrapStep extends FormattingStep {
    @Inject
    public CheckstyleSeparatorWrapStep() {
    }

    @Input
    public abstract Property<String> getOption();

    @Input
    public abstract ListProperty<String> getTokens();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        String option = getOption().getOrElse("eol").toLowerCase(Locale.ROOT);
        List<String> tokens = getTokens().getOrElse(List.of());
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        Set<Integer> emptyStatementLines = new HashSet<>(emptyStatementLines(text));

        if ("nl".equals(option)) {
            if (tokens.contains("DOT")) {
                moveTrailingTokenToNextLine(lines, ".");
            }
            if (tokens.contains("ELLIPSIS")) {
                moveTrailingTokenToNextLine(lines, "...");
            }
            if (tokens.contains("AT")) {
                moveTrailingTokenToNextLine(lines, "@");
            }
        } else {
            if (tokens.contains("COMMA")) {
                moveLeadingTokenToPreviousLine(lines, ",", new HashSet<>());
            }
            if (tokens.contains("SEMI")) {
                moveLeadingTokenToPreviousLine(lines, ";", emptyStatementLines);
            }
        }

        return String.join("\n", lines);
    }

    private static void moveTrailingTokenToNextLine(List<String> lines, String token) {
        for (int i = 0; i < lines.size() - 1; i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.endsWith(token) || isCommentLine(trimmed) || line.contains("//")) {
                continue;
            }

            int next = nextNonEmpty(lines, i + 1);
            if (next < 0) {
                continue;
            }

            String nextTrimmed = lines.get(next).trim();
            if (nextTrimmed.startsWith(token)) {
                continue;
            }

            String without = line.substring(0, line.lastIndexOf(token)).stripTrailing();
            String nextLine = lines.get(next);
            String nextIndent = nextLine.substring(0, nextLine.length() - nextLine.stripLeading().length());

            lines.set(i, without);
            lines.set(next, nextIndent + token + nextTrimmed);
        }
    }

    private static void moveLeadingTokenToPreviousLine(List<String> lines, String token, Set<Integer> emptyStatementLines) {
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty() || !trimmed.startsWith(token)
                    || (token.equals(";") && trimmed.startsWith(";") && emptyStatementLines.contains(i))) {
                continue;
            }

            int previous = previousNonEmpty(lines, i - 1);
            if (previous < 0) {
                continue;
            }

            String previousLine = lines.get(previous).stripTrailing();
            lines.set(previous, previousLine + token);
            String withoutToken = line.substring(line.indexOf(token) + token.length()).stripLeading();
            String currentIndent = line.substring(0, line.length() - line.stripLeading().length());
            if (withoutToken.isEmpty()) {
                for (int remove = i; remove > previous; remove--) {
                    if (remove == i || lines.get(remove).trim().isEmpty()) {
                        lines.remove(remove);
                        shiftLinesAfter(emptyStatementLines, remove, -1);
                    }
                }
                i = previous;
            } else {
                lines.set(i, currentIndent + withoutToken);
                for (int remove = i - 1; remove > previous; remove--) {
                    if (lines.get(remove).trim().isEmpty()) {
                        lines.remove(remove);
                        shiftLinesAfter(emptyStatementLines, remove, -1);
                        i--;
                    }
                }
            }
        }
    }

    private static int nextNonEmpty(List<String> lines, int from) {
        for (int i = from; i < lines.size(); i++) {
            if (!lines.get(i).trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static int previousNonEmpty(List<String> lines, int from) {
        for (int i = from; i >= 0; i--) {
            if (!lines.get(i).trim().isEmpty()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean isCommentLine(String trimmed) {
        return trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*");
    }

    private static Set<Integer> emptyStatementLines(String text) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return Set.of();
        }
        List<String> prefixes = List.of("", "class __FormatumFragment {\n",
                "class __FormatumFragment {\nvoid __fragment() {\n");
        for (int nesting = 0; nesting < prefixes.size(); nesting++) {
            String prefix = prefixes.get(nesting);
            String source = prefix + text + "\n}".repeat(nesting);
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var file = new SimpleJavaFileObject(URI.create("string:///FormatumSeparatorWrap.java"), JavaFileObject.Kind.SOURCE) {
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
                Set<Integer> lines = new HashSet<>();
                var positions = Trees.instance(task).getSourcePositions();
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitEmptyStatement(EmptyStatementTree tree, Void unused) {
                        int offset = (int) positions.getStartPosition(unit, tree) - prefix.length();
                        if (offset >= 0 && offset < text.length()) {
                            int line = 0;
                            for (int i = 0; i < offset; i++) {
                                if (text.charAt(i) == '\n') {
                                    line++;
                                }
                            }
                            lines.add(line);
                        }
                        return super.visitEmptyStatement(tree, unused);
                    }
                }.scan(unit, null);
                return lines;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to parse Java source for SeparatorWrap formatting.", exception);
            }
        }
        return Set.of();
    }

    private static void shiftLinesAfter(Set<Integer> lineNumbers, int line, int delta) {
        Set<Integer> shifted = new HashSet<>();
        for (int existing : lineNumbers) {
            shifted.add(existing > line ? existing + delta : existing);
        }
        lineNumbers.clear();
        lineNumbers.addAll(shifted);
    }
}
