package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.utils.JavaSourceTokens;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reorders Java declaration modifiers into the canonical JLS order that Checkstyle's
 * {@code ModifierOrder} check requires, for example turning {@code public final static} into
 * {@code public static final}.
 *
 * <p>The source is parsed exactly once. Only bare keyword modifiers are considered; their relative
 * positions are compared against the canonical order and, when out of order, each keyword word is
 * written back into its original slot while the whitespace separating consecutive keywords (including
 * newlines and indentation) is preserved verbatim. Declarations whose modifier list already carries an
 * annotation are left untouched so that annotation placement is never disturbed.
 */
final class ModifierOrderFixer {
    /** Checkstyle's {@code ModifierOrderCheck} canonical ordering. */
    static final String[] MODIFIERS = {
        "public", "protected", "private", "abstract", "default", "static", "sealed", "non-sealed", "final",
        "transient", "volatile", "synchronized", "native", "strictfp"
    };
    private static final Set<String> KEYWORDS;
    private static final Map<String, Integer> RANK;

    static {
        Set<String> set = new HashSet<>();
        Map<String, Integer> rank = new HashMap<>();
        for (int i = 0; i < MODIFIERS.length; i++) {
            set.add(MODIFIERS[i]);
            rank.put(MODIFIERS[i], i);
        }
        KEYWORDS = Set.copyOf(set);
        RANK = Map.copyOf(rank);
    }

    private record ParsedSource(CompilationUnitTree unit, SourcePositions positions) {}

    /** A contiguous keyword run replacement spanning {@code [start, end)} of the original text. */
    private record Replacement(int start, int end, String text) {}

    String apply(String text) {
        List<JavaSourceTokens.Token> tokens;
        try {
            tokens = JavaSourceTokens.scan(text);
        } catch (IllegalArgumentException ignored) {
            // Unclosed delimiter: not valid Java for this JDK, nothing to fix.
            return text;
        }
        ParsedSource parsed = parse(text);
        if (parsed == null) {
            return text;
        }
        Collector collector = new Collector(text, tokens, parsed.unit(), parsed.positions());
        collector.scan(new TreePath(parsed.unit()), null);
        if (collector.replacements.isEmpty()) {
            return text;
        }
        StringBuilder result = new StringBuilder(text.length());
        int cursor = 0;
        for (Replacement replacement : collector.replacements) {
            result.append(text, cursor, replacement.start());
            result.append(replacement.text());
            cursor = replacement.end();
        }
        result.append(text, cursor, text.length());
        return result.toString();
    }

    private ParsedSource parse(String content) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return null;
        }
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var file = new SimpleJavaFileObject(URI.create("string:///FormatumModifierOrder.java"), JavaFileObject.Kind.SOURCE) {
            @Override
            public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                return content;
            }
        };
        try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
            var task = (JavacTask) compiler.getTask(new StringWriter(), manager, diagnostics,
                    List.of("-proc:none", "--enable-preview", "--source", Integer.toString(Runtime.version().feature())),
                    null, List.of(file));
            CompilationUnitTree unit = task.parse().iterator().next();
            boolean hasErrors = diagnostics.getDiagnostics().stream()
                    .anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
            return hasErrors ? null : new ParsedSource(unit, Trees.instance(task).getSourcePositions());
        } catch (IOException ignored) {
            return null;
        }
    }

    private static final class Collector extends TreePathScanner<Void, Void> {
        private final List<JavaSourceTokens.Token> tokens;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final String text;
        private final List<Replacement> replacements = new ArrayList<>();

        Collector(String text, List<JavaSourceTokens.Token> tokens, CompilationUnitTree unit, SourcePositions positions) {
            this.text = text;
            this.tokens = tokens;
            this.unit = unit;
            this.positions = positions;
        }

        @Override
        public Void visitClass(ClassTree tree, Void scope) {
            processModifiers(tree.getModifiers());
            return super.visitClass(tree, scope);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void scope) {
            processModifiers(tree.getModifiers());
            return super.visitMethod(tree, scope);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void scope) {
            if (!isParameter(tree)) {
                processModifiers(tree.getModifiers());
            }
            return super.visitVariable(tree, scope);
        }

        private boolean isParameter(VariableTree tree) {
            TreePath path = getCurrentPath();
            Tree parent = path.getParentPath() == null ? null : path.getParentPath().getLeaf();
            return parent instanceof MethodTree || parent instanceof LambdaExpressionTree;
        }

        private void processModifiers(ModifiersTree modifiers) {
            if (!modifiers.getAnnotations().isEmpty()) {
                // Never reorder around annotations: keep placement stable.
                return;
            }
            int modStart = (int) positions.getStartPosition(unit, modifiers);
            int modEnd = (int) positions.getEndPosition(unit, modifiers);
            if (modStart < 0 || modEnd <= modStart) {
                return;
            }

            List<int[]> keywordSpans = new ArrayList<>();
            for (int i = tokenIndexAt(modStart); i < tokens.size() && tokens.get(i).start() < modEnd; i++) {
                JavaSourceTokens.Token token = tokens.get(i);
                if (token.text().equals("non") && i + 2 < tokens.size()
                        && text.substring(token.start(), tokens.get(i + 2).end()).equals("non-sealed")) {
                    keywordSpans.add(new int[]{token.start(), tokens.get(i + 2).end()});
                    i += 2;
                } else if (KEYWORDS.contains(token.text()) && token.start() >= modStart) {
                    keywordSpans.add(new int[]{token.start(), token.end()});
                }
            }

            int count = keywordSpans.size();
            if (count < 2) {
                return;
            }
            Integer[] sourceOrder = new Integer[count];
            for (int i = 0; i < count; i++) {
                sourceOrder[i] = i;
            }
            Integer[] sorted = sourceOrder.clone();
            java.util.Arrays.sort(sorted, (a, b) ->
                    Integer.compare(rank(keywordSpans.get(a)), rank(keywordSpans.get(b))));

            boolean alreadySorted = true;
            for (int i = 0; i < count; i++) {
                if (!sorted[i].equals(sourceOrder[i])) {
                    alreadySorted = false;
                    break;
                }
            }
            if (alreadySorted) {
                return;
            }

            StringBuilder rebuilt = new StringBuilder();
            for (int pos = 0; pos < count; pos++) {
                if (pos > 0) {
                    // Preserve the original whitespace between consecutive keyword slots.
                    int gapEnd = keywordSpans.get(pos - 1)[1];
                    int gapStart = keywordSpans.get(pos)[0];
                    rebuilt.append(text, gapEnd, gapStart);
                }
                int[] span = keywordSpans.get(sorted[pos]);
                rebuilt.append(text, span[0], span[1]);
            }

            replacements.add(new Replacement(keywordSpans.get(0)[0], keywordSpans.get(count - 1)[1], rebuilt.toString()));
        }

        private int rank(int[] span) {
            return RANK.get(text.substring(span[0], span[1]));
        }

        private int tokenIndexAt(int offset) {
            int low = 0;
            int high = tokens.size();
            while (low < high) {
                int mid = (low + high) >>> 1;
                if (tokens.get(mid).start() < offset) {
                    low = mid + 1;
                } else {
                    high = mid;
                }
            }
            return low;
        }
    }
}
