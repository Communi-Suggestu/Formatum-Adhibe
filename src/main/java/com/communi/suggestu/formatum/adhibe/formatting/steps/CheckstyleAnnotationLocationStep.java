package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.utils.JavaSourceTokens;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
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
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeMap;

public abstract class CheckstyleAnnotationLocationStep extends FormattingStep {
    @Inject
    public CheckstyleAnnotationLocationStep() {}

    @Input
    public abstract ListProperty<String> getTokens();

    @Input
    public abstract Property<Boolean> getAllowSamelineSingleParameterlessAnnotation();

    @Input
    public abstract Property<Boolean> getAllowSamelineParameterizedAnnotation();

    @Input
    public abstract Property<Boolean> getAllowSamelineMultipleAnnotations();

    @Override
    public FileFormatter formatter() {
        Set<String> defaults = Set.of("CLASS_DEF", "INTERFACE_DEF", "PACKAGE_DEF", "ENUM_CONSTANT_DEF",
                "ENUM_DEF", "METHOD_DEF", "CTOR_DEF", "VARIABLE_DEF", "RECORD_DEF", "COMPACT_CTOR_DEF");
        List<String> configured = getTokens().getOrElse(List.of());
        Set<String> enabled = configured.isEmpty() ? defaults : Set.copyOf(configured);
        return (file, source) -> apply(TextFormattingUtils.normalizeNewlines(source), enabled);
    }

    private String apply(String text, Set<String> enabled) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("AnnotationLocation formatting requires a full JDK.");
        }
        List<String> prefixes = List.of("", "class __FormatumFragment {\n",
                "class __FormatumFragment {\nvoid __fragment() {\n");
        String errors = "";
        for (int nesting = 0; nesting < prefixes.size(); nesting++) {
            String prefix = prefixes.get(nesting);
            String source = prefix + text + "\n}".repeat(nesting);
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var file = new SimpleJavaFileObject(URI.create("string:///FormatumAnnotations.java"), JavaFileObject.Kind.SOURCE) {
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
                    if (nesting == 0) {
                        errors = diagnostics.getDiagnostics().toString();
                    }
                    continue;
                }
                var positions = Trees.instance(task).getSourcePositions();
                var tokens = JavaSourceTokens.scan(text);
                var edits = new TreeMap<Integer, Edit>();
                new TreePathScanner<Void, Void>() {
                    private int start(Tree tree) {
                        return (int) positions.getStartPosition(unit, tree) - prefix.length();
                    }

                    private int end(Tree tree) {
                        return (int) positions.getEndPosition(unit, tree) - prefix.length();
                    }

                    private void annotations(Tree owner, List<? extends AnnotationTree> annotations, String token) {
                        if (!enabled.contains(token) || annotations.isEmpty() || start(owner) < 0
                                || end(owner) > text.length()) {
                            return;
                        }
                        int first = start(annotations.getFirst());
                        // Checkstyle ignores annotations following a keyword modifier (potential type-use annotations).
                        if (!text.substring(start(owner), first).isBlank()) {
                            return;
                        }
                        int lineStart = text.lastIndexOf('\n', first) + 1;
                        int indentEnd = lineStart;
                        while (indentEnd < first && Character.isWhitespace(text.charAt(indentEnd))) {
                            indentEnd++;
                        }
                        String indent = text.substring(lineStart, indentEnd);
                        for (int i = 0; i < annotations.size(); i++) {
                            var annotation = annotations.get(i);
                            int from = start(annotation);
                            int to = end(annotation);
                            int previousEnd = i == 0 ? start(owner) : end(annotations.get(i - 1));
                            if (tokens.stream().anyMatch(t -> t.start() >= previousEnd && t.end() <= from)) {
                                break;
                            }
                            int currentLine = text.lastIndexOf('\n', from) + 1;
                            boolean previousOnLine = i > 0
                                    && text.lastIndexOf('\n', start(annotations.get(i - 1))) == currentLine - 1;
                            int nextStart = i + 1 < annotations.size() ? start(annotations.get(i + 1)) : -1;
                            boolean nextOnLine = nextStart >= 0
                                    && text.lastIndexOf('\n', nextStart) == currentLine - 1
                                    && tokens.stream().noneMatch(t -> t.start() >= to
                                    && t.end() <= nextStart);
                            boolean allowMultiple = getAllowSamelineMultipleAnnotations().getOrElse(false);
                            if (previousOnLine && !allowMultiple) {
                                breakBefore(from, indent);
                            } else if (text.substring(currentLine, from).isBlank()) {
                                edits.put(currentLine, new Edit(from, indent));
                            }
                            boolean allowed = allowMultiple || !previousOnLine && !nextOnLine
                                    && (annotation.getArguments().isEmpty()
                                    ? getAllowSamelineSingleParameterlessAnnotation().getOrElse(true)
                                    : getAllowSamelineParameterizedAnnotation().getOrElse(false));
                            if (!allowed) {
                                tokens.stream().filter(t -> t.start() >= to && t.start() < end(owner))
                                        .findFirst().ifPresent(next -> {
                                            if (!text.substring(to, next.start()).contains("\n")) {
                                                breakBefore(next.start(), indent);
                                            }
                                        });
                            }
                        }
                    }

                    private void breakBefore(int offset, String indent) {
                        int from = offset;
                        while (from > 0 && (text.charAt(from - 1) == ' ' || text.charAt(from - 1) == '\t')) {
                            from--;
                        }
                        edits.put(from, new Edit(offset, "\n" + indent));
                    }

                    @Override
                    public Void visitCompilationUnit(CompilationUnitTree tree, Void unused) {
                        if (tree.getPackage() != null) {
                            annotations(tree.getPackage(), tree.getPackageAnnotations(), "PACKAGE_DEF");
                        }
                        return super.visitCompilationUnit(tree, unused);
                    }

                    @Override
                    public Void visitClass(ClassTree tree, Void unused) {
                        String token = switch (tree.getKind()) {
                            case INTERFACE -> "INTERFACE_DEF";
                            case ENUM -> "ENUM_DEF";
                            case RECORD -> "RECORD_DEF";
                            case ANNOTATION_TYPE -> "ANNOTATION_DEF";
                            default -> "CLASS_DEF";
                        };
                        annotations(tree, tree.getModifiers().getAnnotations(), token);
                        return super.visitClass(tree, unused);
                    }

                    @Override
                    public Void visitMethod(MethodTree tree, Void unused) {
                        String token = tree.getReturnType() == null ? "CTOR_DEF" : "METHOD_DEF";
                        if (tree.getReturnType() == null && tree.getBody() != null
                                && tokens.stream().noneMatch(t -> t.text().equals("(")
                                && t.start() >= Math.max(start(tree), end(tree.getModifiers()))
                                && t.end() <= start(tree.getBody()))) {
                            token = "COMPACT_CTOR_DEF";
                        }
                        if (getCurrentPath().getParentPath().getLeaf() instanceof ClassTree parent
                                && parent.getKind() == Tree.Kind.ANNOTATION_TYPE) {
                            token = "ANNOTATION_FIELD_DEF";
                        }
                        annotations(tree, tree.getModifiers().getAnnotations(), token);
                        return super.visitMethod(tree, unused);
                    }

                    @Override
                    public Void visitVariable(VariableTree tree, Void unused) {
                        if (getCurrentPath().getParentPath().getLeaf() instanceof ClassTree) {
                            boolean constant = tree.getInitializer() instanceof NewClassTree creation
                                    && tree.getType() != null && start(creation) == start(tree.getType());
                            annotations(tree, tree.getModifiers().getAnnotations(),
                                    constant ? "ENUM_CONSTANT_DEF" : "VARIABLE_DEF");
                        }
                        return super.visitVariable(tree, unused);
                    }
                }.scan(unit, null);
                var result = new StringBuilder(text);
                for (var entry : edits.descendingMap().entrySet()) {
                    result.replace(entry.getKey(), entry.getValue().end(), entry.getValue().text());
                }
                return result.toString();
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to parse AnnotationLocation source.", exception);
            }
        }
        throw new IllegalArgumentException("Unable to parse AnnotationLocation source: " + errors);
    }

    private record Edit(int end, String text) {}
}
