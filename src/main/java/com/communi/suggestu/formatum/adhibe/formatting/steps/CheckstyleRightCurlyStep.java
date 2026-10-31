package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.DoWhileLoopTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
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

public abstract class CheckstyleRightCurlyStep extends FormattingStep {
    @Inject
    public CheckstyleRightCurlyStep() {
    }

    @Input
    public abstract Property<String> getOption();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> applyRightCurlyPolicy(TextFormattingUtils.normalizeNewlines(text));
    }

    private String applyRightCurlyPolicy(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        String option = getOption().getOrElse("same");
        return switch (option) {
            case "alone" -> enforceAlone(lines, false);
            case "alone_or_singleline" -> enforceAlone(lines, true);
            default -> enforceSame(lines, new HashSet<>(doWhileClosingLines(text)));
        };
    }

    private String enforceSame(List<String> lines, Set<Integer> doWhileClosingLines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int closeIndex = line.indexOf('}');
            if (closeIndex < 0) {
                continue;
            }

            String before = line.substring(0, closeIndex).trim();
            String after = line.substring(closeIndex + 1).trim();
            if (!before.isEmpty()) {
                continue;
            }

            if (!after.isEmpty() && (startsWithClosingDelimiterContinuation(after)
                    || startsWithStatementTerminator(after))) {
                continue;
            }

            if (!after.isEmpty() && !isContinuation(after, doWhileClosingLines.contains(i))) {
                String indent = line.substring(0, closeIndex);
                lines.set(i, indent + "}");
                lines.add(i + 1, indent + after);
                int inserted = 1;
                if (after.matches("while\\b.*") && indent.indexOf('\t') >= 0) {
                    // Recover the boundary hidden by older do-while detection before indentation runs.
                    lines.add(i + 1, "");
                    inserted++;
                }
                shiftLinesAfter(doWhileClosingLines, i, inserted);
                i += inserted;
                continue;
            }

            if (after.isEmpty() && i + 1 < lines.size()) {
                // Skip empty lines to find the next continuation keyword
                int nextNonEmptyIndex = i + 1;
                while (nextNonEmptyIndex < lines.size() && lines.get(nextNonEmptyIndex).trim().isEmpty()) {
                    nextNonEmptyIndex++;
                }

                if (nextNonEmptyIndex < lines.size()) {
                    String next = lines.get(nextNonEmptyIndex).trim();
                    if (isContinuation(next, doWhileClosingLines.contains(i))) {
                        // Remove all empty lines between } and continuation keyword
                        String indent = line.substring(0, closeIndex);
                        lines.set(i, indent + "} " + next);
                        // Remove empty lines and the continuation line
                        int removed = nextNonEmptyIndex - i;
                        for (int j = nextNonEmptyIndex; j > i; j--) {
                            lines.remove(j);
                        }
                        shiftLinesAfter(doWhileClosingLines, i, -removed);
                    } else if (startsWithClosingDelimiterContinuation(next)) {
                        String indent = line.substring(0, closeIndex);
                        lines.set(i, indent + "}" + next);
                        int removed = nextNonEmptyIndex - i;
                        for (int j = nextNonEmptyIndex; j > i; j--) {
                            lines.remove(j);
                        }
                        shiftLinesAfter(doWhileClosingLines, i, -removed);
                    }
                }
            }
        }

        return String.join("\n", lines);
    }

    private String enforceAlone(List<String> lines, boolean allowSingleLineBlock) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int closeIndex = line.indexOf('}');
            if (closeIndex < 0) {
                continue;
            }

            if (allowSingleLineBlock && line.contains("{") && closeIndex > line.indexOf('{')) {
                continue;
            }

            String before = line.substring(0, closeIndex);
            String after = line.substring(closeIndex + 1).trim();
            if (startsWithStatementTerminator(after)) {
                continue;
            }
            if (!after.isEmpty()) {
                // There's content after the }, split them onto separate lines
                String indent = indentation(before);
                lines.set(i, indent + "}");
                lines.add(i + 1, indent + after);
                i++;
                continue;
            }

            if (!before.trim().isEmpty()) {
                // There's content before the }, extract indent and place } on its own line with correct indentation
                String indent = indentation(before);
                lines.set(i, indent + "}");
            }
        }

        return String.join("\n", lines);
    }

    private static boolean isContinuation(String text, boolean doWhileTail) {
        return text.startsWith("else")
                || text.startsWith("catch")
                || text.startsWith("finally")
                || (doWhileTail && text.startsWith("while"));
    }

    private static boolean startsWithClosingDelimiterContinuation(String text) {
        return text.startsWith(")") || text.startsWith("]");
    }

    private static boolean startsWithStatementTerminator(String text) {
        return text.startsWith(";");
    }

    private static void shiftLinesAfter(Set<Integer> lineNumbers, int line, int delta) {
        Set<Integer> shifted = new HashSet<>();
        for (int existing : lineNumbers) {
            shifted.add(existing > line ? existing + delta : existing);
        }
        lineNumbers.clear();
        lineNumbers.addAll(shifted);
    }

    private static Set<Integer> doWhileClosingLines(String text) {
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
            var file = new SimpleJavaFileObject(URI.create("string:///FormatumRightCurly.java"), JavaFileObject.Kind.SOURCE) {
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
                Set<Integer> closingLines = new HashSet<>();
                var positions = Trees.instance(task).getSourcePositions();
                new TreeScanner<Void, Void>() {
                    @Override
                    public Void visitDoWhileLoop(DoWhileLoopTree tree, Void unused) {
                        if (tree.getStatement() instanceof BlockTree block) {
                            int closingOffset = (int) positions.getEndPosition(unit, block) - prefix.length() - 1;
                            if (closingOffset >= 0 && closingOffset < text.length() && text.charAt(closingOffset) == '}') {
                                int line = 0;
                                for (int index = 0; index < closingOffset; index++) {
                                    if (text.charAt(index) == '\n') {
                                        line++;
                                    }
                                }
                                closingLines.add(line);
                            }
                        }
                        return super.visitDoWhileLoop(tree, unused);
                    }
                }.scan(unit, null);
                return closingLines;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to parse Java source for RightCurly formatting.", exception);
            }
        }
        return Set.of();
    }

    private static String indentation(String text) {
        int i = 0;
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        return text.substring(0, i);
    }
}
