package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.DocTrees;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import javax.lang.model.element.Modifier;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Repairs missing description punctuation. HTML structure and empty descriptions remain check-only:
 * inventing prose or changing HTML structure is not a safe automatic repair.
 */
public abstract class CheckstyleJavadocStyleStep extends FormattingStep {
    private static final String DEFAULT_END = "([.?!][ \\t\\n\\r\\f<])|([.?!]$)";

    @Inject
    public CheckstyleJavadocStyleStep() {}

    @Input
    public abstract Property<Boolean> getCheckFirstSentence();

    @Input
    public abstract Property<String> getEndOfSentenceFormat();

    @Input
    public abstract Property<String> getScope();

    @Input
    public abstract Property<String> getExcludeScope();

    @Input
    public abstract ListProperty<String> getTokens();

    private record Comment(int start, int end) {}
    private record Description(String text, int insertion) {}

    @Override
    public FileFormatter formatter() {
        return (fileName, source) -> {
            if (!getCheckFirstSentence().getOrElse(true) || !source.contains("/**")) {
                return source;
            }
            return apply(fileName, source);
        };
    }

    private String apply(String fileName, String source) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("JavadocStyle formatting requires a full JDK.");
        }
        Pattern ending = Pattern.compile(getEndOfSentenceFormat().getOrElse(DEFAULT_END));
        int scope = scope(getScope().getOrElse("private"));
        String excluded = getExcludeScope().getOrElse("");
        int excludeScope = excluded.isBlank() ? -1 : scope(excluded);
        List<Comment> comments = comments(source);
        Set<Integer> insertions = new HashSet<>();
        var diagnostics = new DiagnosticCollector<JavaFileObject>();
        var file = new SimpleJavaFileObject(URI.create("string:///FormatumJavadocStyle.java"), JavaFileObject.Kind.SOURCE) {
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
                throw new IllegalArgumentException("Unable to parse " + fileName + " for JavadocStyle: " + diagnostics.getDiagnostics());
            }
            DocTrees trees = DocTrees.instance(task);
            new TreePathScanner<Void, Void>() {
                @Override
                public Void scan(Tree tree, Void unused) {
                    if (tree == null) {
                        return null;
                    }
                    TreePath path = tree instanceof CompilationUnitTree compilationUnit
                            ? new TreePath(compilationUnit) : new TreePath(getCurrentPath(), tree);
                    String token = token(path, source, unit, trees);
                    List<String> selected = getTokens().getOrElse(List.of());
                    if (token != null && (selected.isEmpty() || selected.contains(token))
                            && eligible(path, scope, excludeScope, fileName) && trees.getDocCommentTree(path) != null) {
                        int start = (int) trees.getSourcePositions().getStartPosition(unit, tree);
                        Comment comment = comments.stream().filter(c -> c.end() <= start)
                                .max(Comparator.comparingInt(Comment::end)).orElse(null);
                        if (comment != null) {
                            Description description = description(source, comment);
                            String text = description.text();
                            boolean inheritDoc = text.startsWith("{@inheritDoc}")
                                    && (tree instanceof MethodTree || tree instanceof ClassTree);
                            boolean inlineReturn = Pattern.compile("\\{@return.*?}\\s*")
                                    .matcher(text.split("\\.(?=\\s|$)", 2)[0]).find();
                            if (!text.isEmpty() && !inheritDoc && !inlineReturn && !ending.matcher(text).find()) {
                                String repaired = text + ".";
                                if (ending.matcher(repaired).find()) {
                                    insertions.add(description.insertion());
                                }
                            }
                        }
                    }
                    return super.scan(tree, unused);
                }
            }.scan(unit, null);
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to parse Javadoc for " + fileName, exception);
        }
        StringBuilder result = new StringBuilder(source);
        for (int insertion : insertions.stream().sorted(Comparator.reverseOrder()).toList()) {
            result.insert(insertion, '.');
        }
        return result.toString();
    }

    private static List<Comment> comments(String source) {
        List<Comment> result = new ArrayList<>();
        int cursor = 0;
        List<JavaLexer.Token> tokens = JavaLexer.lex(source);
        for (int i = 0; i <= tokens.size(); i++) {
            int end = i == tokens.size() ? source.length() : tokens.get(i).start();
            while (cursor < end) {
                if (source.startsWith("//", cursor)) {
                    int newline = source.indexOf('\n', cursor);
                    cursor = newline < 0 ? end : newline;
                } else if (source.startsWith("/*", cursor)) {
                    int close = source.indexOf("*/", cursor + 2);
                    if (close < 0) {
                        throw new IllegalArgumentException("Unclosed Java comment at offset " + cursor);
                    }
                    if (source.startsWith("/**", cursor)) {
                        result.add(new Comment(cursor, close + 2));
                    }
                    cursor = close + 2;
                } else {
                    cursor++;
                }
            }
            if (i < tokens.size()) {
                cursor = tokens.get(i).end();
            }
        }
        return result;
    }

    private static Description description(String source, Comment comment) {
        StringBuilder text = new StringBuilder();
        int insertion = -1;
        int cursor = comment.start() + 3;
        int end = comment.end() - 2;
        while (cursor < end) {
            int newline = source.indexOf('\n', cursor);
            int lineEnd = newline < 0 ? end : Math.min(newline, end);
            int start = cursor;
            while (start < lineEnd && (Character.isWhitespace(source.charAt(start)) || source.charAt(start) == '*')) {
                start++;
            }
            if (start < lineEnd && source.charAt(start) == '@') {
                break;
            }
            int last = lineEnd;
            while (last > start && Character.isWhitespace(source.charAt(last - 1))) {
                last--;
            }
            if (last > start) {
                text.append(source, start, last).append('\n');
                insertion = last;
            }
            cursor = lineEnd + 1;
        }
        return new Description(text.toString().trim(), insertion);
    }

    private static int scope(String name) {
        return switch (name.toLowerCase(Locale.ROOT)) {
            case "public" -> 0;
            case "protected" -> 1;
            case "package" -> 2;
            case "private" -> 3;
            case "anoninner" -> 4;
            default -> throw new IllegalArgumentException("Unknown JavadocStyle scope: " + name);
        };
    }

    private static boolean eligible(TreePath path, int scope, int excluded, String fileName) {
        if (path.getLeaf().getKind() == Tree.Kind.PACKAGE) {
            return fileName.replace('\\', '/').endsWith("package-info.java");
        }
        int own = visibility(path);
        int surrounding = path.getLeaf() instanceof ClassTree ? own : 0;
        for (TreePath parent = path.getParentPath(); parent != null; parent = parent.getParentPath()) {
            if (parent.getLeaf() instanceof BlockTree || parent.getLeaf() instanceof MethodTree
                    || parent.getLeaf().getKind() == Tree.Kind.LAMBDA_EXPRESSION) {
                return false;
            }
            if (parent.getLeaf() instanceof ClassTree type) {
                surrounding = Math.max(surrounding, type.getSimpleName().isEmpty() ? 4 : visibility(parent));
            }
        }
        return own <= scope && surrounding <= scope && (excluded < 0 || own > excluded || surrounding > excluded);
    }

    private static int visibility(TreePath path) {
        Tree tree = path.getLeaf();
        ModifiersTree modifiers = tree instanceof ClassTree type ? type.getModifiers()
                : tree instanceof MethodTree method ? method.getModifiers()
                : tree instanceof VariableTree variable ? variable.getModifiers() : null;
        if (modifiers == null) {
            return 0;
        }
        Set<Modifier> flags = modifiers.getFlags();
        if (flags.contains(Modifier.PRIVATE)) {
            return 3;
        }
        if (flags.contains(Modifier.PROTECTED)) {
            return 1;
        }
        Tree parent = path.getParentPath() == null ? null : path.getParentPath().getLeaf();
        if (parent != null && parent.getKind() == Tree.Kind.ENUM) {
            if (tree instanceof MethodTree method && method.getReturnType() == null) {
                return 3;
            }
            if (tree instanceof VariableTree variable && variable.getInitializer() instanceof com.sun.source.tree.NewClassTree
                    && flags.contains(Modifier.STATIC) && flags.contains(Modifier.FINAL)) {
                return 0;
            }
        }
        if (flags.contains(Modifier.PUBLIC) || parent != null
                && (parent.getKind() == Tree.Kind.INTERFACE || parent.getKind() == Tree.Kind.ANNOTATION_TYPE)) {
            return 0;
        }
        return 2;
    }

    private static String token(TreePath path, String source, CompilationUnitTree unit, DocTrees trees) {
        Tree tree = path.getLeaf();
        return switch (tree.getKind()) {
            case CLASS -> "CLASS_DEF";
            case INTERFACE -> "INTERFACE_DEF";
            case ENUM -> "ENUM_DEF";
            case RECORD -> "RECORD_DEF";
            case ANNOTATION_TYPE -> "ANNOTATION_DEF";
            case PACKAGE -> "PACKAGE_DEF";
            case VARIABLE -> {
                VariableTree variable = (VariableTree) tree;
                yield variable.getInitializer() != null
                        && trees.getSourcePositions().getStartPosition(unit, variable.getType())
                        == trees.getSourcePositions().getStartPosition(unit, variable.getInitializer())
                        ? "ENUM_CONSTANT_DEF" : "VARIABLE_DEF";
            }
            case METHOD -> {
                MethodTree method = (MethodTree) tree;
                if (method.getReturnType() != null) {
                    yield path.getParentPath().getLeaf().getKind() == Tree.Kind.ANNOTATION_TYPE
                            ? "ANNOTATION_FIELD_DEF" : "METHOD_DEF";
                }
                int start = (int) trees.getSourcePositions().getStartPosition(unit, method);
                int body = method.getBody() == null ? start
                        : (int) trees.getSourcePositions().getStartPosition(unit, method.getBody());
                yield source.substring(start, body).contains("(") ? "CTOR_DEF" : "COMPACT_CTOR_DEF";
            }
            default -> null;
        };
    }
}
