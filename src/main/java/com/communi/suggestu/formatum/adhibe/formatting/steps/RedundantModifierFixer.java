package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.utils.JavaSourceTokens;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.BindingPatternTree;
import com.sun.source.tree.CatchTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.LambdaExpressionTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.ModifiersTree;
import com.sun.source.tree.NewClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.tree.TryTree;
import com.sun.source.tree.VariableTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

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
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

/**
 * Port of Checkstyle's {@code RedundantModifierCheck} as a fixer. The source is parsed exactly once:
 * every redundancy decision depends only on modifiers that are never themselves removed, so all
 * deletions found in that single pass are final and are applied together in one rewrite.
 */
final class RedundantModifierFixer {
    enum Visit {
        METHOD_DEF, VARIABLE_DEF, ANNOTATION_FIELD_DEF, INTERFACE_DEF, CTOR_DEF, CLASS_DEF, ENUM_DEF,
        RESOURCE, ANNOTATION_DEF, RECORD_DEF, PATTERN_VARIABLE_DEF, LITERAL_CATCH, LAMBDA;

        static Set<Visit> fromTokens(List<String> tokens) {
            if (tokens == null || tokens.isEmpty()) {
                return EnumSet.allOf(Visit.class);
            }
            EnumSet<Visit> result = EnumSet.noneOf(Visit.class);
            for (String token : tokens) {
                result.add(Visit.valueOf(token.trim().toUpperCase(Locale.ROOT)));
            }
            return result;
        }
    }

    record ParsedSource(CompilationUnitTree unit, SourcePositions positions, int prefixLength) {}

    @FunctionalInterface
    interface Parser {
        /**
         * @return the parsed source, or {@code null} if it cannot be parsed without errors
         */
        ParsedSource parse(String content);
    }

    static final int DEFAULT_JDK_VERSION = 22;
    private static final int JDK_17 = 17;
    private static final int JDK_22 = 22;
    private static final Set<String> MODIFIER_KEYWORDS = Set.of(
            "public", "protected", "private", "static", "abstract", "final", "native", "synchronized",
            "transient", "volatile", "strictfp", "default", "sealed");
    private static final Set<String> OBJECT_METHODS_WITHOUT_PARAMETERS = Set.of("hashCode", "toString", "clone", "finalize");

    private final int jdkVersion;
    private final Set<Visit> visits;
    private final Parser parser;

    RedundantModifierFixer(int jdkVersion, Set<Visit> visits) {
        this(jdkVersion, visits, RedundantModifierFixer::parseWithJavac);
    }

    RedundantModifierFixer(int jdkVersion, Set<Visit> visits, Parser parser) {
        this.jdkVersion = jdkVersion;
        this.visits = visits.isEmpty() ? EnumSet.noneOf(Visit.class) : EnumSet.copyOf(visits);
        this.parser = parser;
    }

    static int parseJdkVersion(String value) {
        String trimmed = value.trim();
        return Integer.parseInt(trimmed.startsWith("1.") ? trimmed.substring(2) : trimmed);
    }

    String apply(String text) {
        if (visits.isEmpty()) {
            return text;
        }
        List<JavaSourceTokens.Token> tokens;
        try {
            tokens = JavaSourceTokens.scan(text);
        } catch (IllegalArgumentException e) {
            return text;
        }
        ParsedSource parsed = parser.parse(text);
        if (parsed == null) {
            return text;
        }

        Collector collector = new Collector(text, tokens, parsed);
        collector.scan(new TreePath(parsed.unit()), null);
        if (collector.redundant.isEmpty()) {
            return text;
        }
        return rewrite(text, tokens, collector.redundant);
    }

    // ---------------------------------------------------------------- rewriting

    private static String rewrite(String text, List<JavaSourceTokens.Token> tokens, Set<Integer> redundantTokenIndices) {
        List<int[]> deletions = new ArrayList<>();
        for (int index : redundantTokenIndices) {
            deletions.add(deletionFor(text, tokens, index));
        }
        deletions.sort(Comparator.comparingInt(range -> range[0]));

        List<int[]> merged = new ArrayList<>();
        for (int[] deletion : deletions) {
            int[] last = merged.isEmpty() ? null : merged.getLast();
            if (last != null && deletion[0] <= last[1]) {
                last[1] = Math.max(last[1], deletion[1]);
            } else {
                merged.add(deletion.clone());
            }
        }

        StringBuilder result = new StringBuilder(text.length());
        TreeSet<Integer> touchedLines = new TreeSet<>();
        int position = 0;
        int line = 0;
        for (int[] deletion : merged) {
            line += countNewlines(text, position, deletion[0]);
            touchedLines.add(line);
            result.append(text, position, deletion[0]);
            position = deletion[1];
        }
        result.append(text, position, text.length());
        return clearWhitespaceOnlyLines(result.toString(), text, touchedLines);
    }

    /**
     * Deletion range for a modifier token. Ranges never contain a newline, so the line count stays
     * stable (the suppression wrapper relies on line-for-line correspondence).
     */
    private static int[] deletionFor(String text, List<JavaSourceTokens.Token> tokens, int index) {
        JavaSourceTokens.Token token = tokens.get(index);
        int nextStart = index + 1 < tokens.size() ? tokens.get(index + 1).start() : text.length();
        if (isHorizontalWhitespace(text, token.end(), nextStart)) {
            return new int[]{token.start(), nextStart};
        }

        int lineEnd = text.indexOf('\n', token.end());
        if (lineEnd < 0) {
            lineEnd = text.length();
        }
        if (isHorizontalWhitespace(text, token.end(), lineEnd)) {
            // Modifier is the last token on its line: also drop the whitespace in front of it.
            int start = token.start();
            while (start > 0 && (text.charAt(start - 1) == ' ' || text.charAt(start - 1) == '\t')) {
                start--;
            }
            return new int[]{start, lineEnd};
        }

        // A comment follows on the same line; only remove the keyword and its trailing spacing.
        int end = token.end();
        while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
            end++;
        }
        return new int[]{token.start(), end};
    }

    private static String clearWhitespaceOnlyLines(String result, String original, Set<Integer> touchedLines) {
        String[] lines = result.split("\n", -1);
        String[] originalLines = original.split("\n", -1);
        boolean changed = false;
        for (int line : touchedLines) {
            if (line < lines.length && !lines[line].isEmpty() && lines[line].isBlank() && !originalLines[line].isBlank()) {
                lines[line] = "";
                changed = true;
            }
        }
        return changed ? String.join("\n", lines) : result;
    }

    private static boolean isHorizontalWhitespace(String text, int start, int end) {
        for (int i = start; i < end; i++) {
            char c = text.charAt(i);
            if (c != ' ' && c != '\t') {
                return false;
            }
        }
        return true;
    }

    private static int countNewlines(String text, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (text.charAt(i) == '\n') {
                count++;
            }
        }
        return count;
    }

    // ---------------------------------------------------------------- parsing

    static ParsedSource parseWithJavac(String content) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("RedundantModifier requires a full JDK with the jdk.compiler module.");
        }
        // Formatter callers may supply a member or statement fragment instead of a compilation unit.
        List<String> prefixes = List.of("", "class __FormatumFragment {\n", "class __FormatumFragment {\nvoid __fragment() {\n");
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
                CompilationUnitTree unit = task.parse().iterator().next();
                boolean hasErrors = diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR);
                if (!hasErrors) {
                    return new ParsedSource(unit, Trees.instance(task).getSourcePositions(), prefix.length());
                }
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to parse Java source.", exception);
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- analysis

    private final class Collector extends TreePathScanner<Void, Void> {
        private final String text;
        private final List<JavaSourceTokens.Token> tokens;
        private final CompilationUnitTree unit;
        private final SourcePositions positions;
        private final int prefixLength;
        private final Set<Integer> redundant = new TreeSet<>();

        private Collector(String text, List<JavaSourceTokens.Token> tokens, ParsedSource parsed) {
            this.text = text;
            this.tokens = tokens;
            this.unit = parsed.unit();
            this.positions = parsed.positions();
            this.prefixLength = parsed.prefixLength();
        }

        @Override
        public Void visitClass(ClassTree tree, Void unused) {
            TreePath path = getCurrentPath();
            if (!isAnonymous(path) && !isImplicitClass(path)) {
                Visit visit = switch (tree.getKind()) {
                    case INTERFACE -> Visit.INTERFACE_DEF;
                    case ANNOTATION_TYPE -> Visit.ANNOTATION_DEF;
                    case ENUM -> Visit.ENUM_DEF;
                    case RECORD -> Visit.RECORD_DEF;
                    default -> Visit.CLASS_DEF;
                };
                if (visits.contains(visit)) {
                    ModifiersTree modifiers = tree.getModifiers();
                    switch (visit) {
                        case INTERFACE_DEF, ANNOTATION_DEF -> flag(modifiers, "static", "abstract");
                        case ENUM_DEF -> flag(modifiers, "static");
                        case RECORD_DEF -> flag(modifiers, "final", "static");
                        default -> {
                        }
                    }
                    finishMemberChecks(path, visit, modifiers);
                }
            }
            return super.visitClass(tree, unused);
        }

        @Override
        public Void visitMethod(MethodTree tree, Void unused) {
            TreePath path = getCurrentPath();
            Tree owner = path.getParentPath().getLeaf();
            if (owner instanceof ClassTree ownerClass) {
                boolean constructor = tree.getName().contentEquals("<init>");
                Visit visit = constructor ? Visit.CTOR_DEF
                        : ownerClass.getKind() == Tree.Kind.ANNOTATION_TYPE ? Visit.ANNOTATION_FIELD_DEF
                        : Visit.METHOD_DEF;
                boolean compactConstructor = constructor && isCompactConstructor(tree, ownerClass);
                if (visits.contains(visit) && !compactConstructor) {
                    if (visit == Visit.CTOR_DEF) {
                        checkConstructor(tree, path.getParentPath(), ownerClass);
                    } else if (visit == Visit.METHOD_DEF) {
                        processMethod(tree, path);
                    }
                    finishMemberChecks(path, visit, tree.getModifiers());
                }
            }
            return super.visitMethod(tree, unused);
        }

        @Override
        public Void visitVariable(VariableTree tree, Void unused) {
            TreePath path = getCurrentPath();
            Tree parent = path.getParentPath().getLeaf();
            boolean handledElsewhere = parent instanceof MethodTree
                    || parent instanceof LambdaExpressionTree
                    || parent instanceof CatchTree
                    || parent instanceof TryTree try_ && try_.getResources().contains(tree);
            if (!handledElsewhere) {
                Visit visit = parent instanceof BindingPatternTree ? Visit.PATTERN_VARIABLE_DEF : Visit.VARIABLE_DEF;
                if (visits.contains(visit)) {
                    checkUnnamedVariable(tree);
                    finishMemberChecks(path, visit, tree.getModifiers());
                }
            }
            return super.visitVariable(tree, unused);
        }

        @Override
        public Void visitTry(TryTree tree, Void unused) {
            if (visits.contains(Visit.RESOURCE)) {
                for (Tree resource : tree.getResources()) {
                    if (resource instanceof VariableTree variable) {
                        flag(variable.getModifiers(), "final");
                        checkStrictfp(variable.getModifiers());
                    }
                }
            }
            return super.visitTry(tree, unused);
        }

        @Override
        public Void visitCatch(CatchTree tree, Void unused) {
            if (visits.contains(Visit.LITERAL_CATCH)) {
                checkUnnamedVariable(tree.getParameter());
            }
            return super.visitCatch(tree, unused);
        }

        @Override
        public Void visitLambdaExpression(LambdaExpressionTree tree, Void unused) {
            if (visits.contains(Visit.LAMBDA)) {
                tree.getParameters().forEach(this::checkUnnamedVariable);
            }
            return super.visitLambdaExpression(tree, unused);
        }

        /** Checks Checkstyle applies to every visited node with a MODIFIERS child. */
        private void finishMemberChecks(TreePath path, Visit visit, ModifiersTree modifiers) {
            Tree parent = path.getParentPath().getLeaf();
            if (parent instanceof ClassTree owner
                    && (owner.getKind() == Tree.Kind.INTERFACE || owner.getKind() == Tree.Kind.ANNOTATION_TYPE)) {
                // javac does not allow final/static on interface methods or annotation fields.
                boolean isClass = visit == Visit.CLASS_DEF;
                for (int index : modifierTokens(modifiers)) {
                    String keyword = tokens.get(index).text();
                    if (keyword.equals("public")
                            || keyword.equals("static") && visit != Visit.METHOD_DEF
                            || keyword.equals("abstract") && !isClass
                            || keyword.equals("final") && !isClass) {
                        redundant.add(index);
                    }
                }
            }
            checkStrictfp(modifiers);
        }

        private void checkStrictfp(ModifiersTree modifiers) {
            if (jdkVersion >= JDK_17) {
                flag(modifiers, "strictfp");
            }
        }

        private void checkUnnamedVariable(VariableTree variable) {
            if (jdkVersion >= JDK_22 && (variable.getName().isEmpty() || variable.getName().contentEquals("_"))) {
                flag(variable.getModifiers(), "final");
            }
        }

        private void checkConstructor(MethodTree constructor, TreePath ownerPath, ClassTree owner) {
            ModifiersTree modifiers = constructor.getModifiers();
            if (owner.getKind() == Tree.Kind.ENUM) {
                List<Integer> keywords = modifierTokens(modifiers);
                if (!keywords.isEmpty()) {
                    redundant.add(keywords.getFirst());
                }
            } else if (!isClassPublic(ownerPath) && !owner.getModifiers().getFlags().contains(Modifier.PROTECTED)) {
                flag(modifiers, "public");
            }
        }

        private void processMethod(MethodTree method, TreePath path) {
            ModifiersTree modifiers = method.getModifiers();
            boolean checkFinal = modifiers.getFlags().contains(Modifier.PRIVATE);
            for (TreePath current = path.getParentPath(); current != null && !checkFinal; current = current.getParentPath()) {
                Tree leaf = current.getLeaf();
                if (leaf instanceof ClassTree classTree) {
                    if (isAnonymous(current)) {
                        // Covers both anonymous classes and enum constant bodies.
                        checkFinal = true;
                        break;
                    }
                    if (isImplicitClass(current)) {
                        break;
                    }
                    if (classTree.getKind() == Tree.Kind.CLASS) {
                        checkFinal = classTree.getModifiers().getFlags().contains(Modifier.FINAL);
                        break;
                    }
                    if (classTree.getKind() == Tree.Kind.ENUM) {
                        checkFinal = modifiers.getFlags().contains(Modifier.STATIC);
                        break;
                    }
                    // Checkstyle walks through records and interfaces to the next enclosing type.
                }
            }
            if (checkFinal && !isAnnotatedWithSafeVarargs(modifiers)) {
                flag(modifiers, "final");
            }
            if (method.getBody() == null) {
                for (VariableTree parameter : method.getParameters()) {
                    flag(parameter.getModifiers(), "final");
                }
            }
            if (isImplicitClass(path.getParentPath()) && !isObjectMethodOverride(method)) {
                flag(modifiers, "public");
            }
        }

        /** Mirrors Checkstyle's {@code isClassPublic}: only nesting inside an interface implies {@code public}. */
        private boolean isClassPublic(TreePath classPath) {
            ClassTree classTree = (ClassTree) classPath.getLeaf();
            boolean hasPublic = classTree.getModifiers().getFlags().contains(Modifier.PUBLIC);
            TreePath parentPath = classPath.getParentPath();
            Tree parent = parentPath.getLeaf();
            if (parent instanceof CompilationUnitTree || isImplicitClass(parentPath)) {
                return hasPublic;
            }
            if (parent instanceof ClassTree parentClass && !isAnonymous(parentPath)) {
                if (hasPublic || parentClass.getKind() == Tree.Kind.INTERFACE) {
                    return isClassPublic(parentPath);
                }
            }
            return false;
        }

        /** Checkstyle compares the annotation's last AST child, so only a bare {@code @SafeVarargs} matches. */
        private boolean isAnnotatedWithSafeVarargs(ModifiersTree modifiers) {
            for (AnnotationTree annotation : modifiers.getAnnotations()) {
                if (annotation.getAnnotationType() instanceof IdentifierTree identifier
                        && identifier.getName().contentEquals("SafeVarargs")
                        && end(annotation) == end(identifier)) {
                    return true;
                }
            }
            return false;
        }

        private boolean isObjectMethodOverride(MethodTree method) {
            String name = method.getName().toString();
            List<? extends VariableTree> parameters = method.getParameters();
            if (parameters.isEmpty()) {
                return OBJECT_METHODS_WITHOUT_PARAMETERS.contains(name);
            }
            if (!name.equals("equals") || parameters.size() != 1) {
                return false;
            }
            Tree type = parameters.getFirst().getType();
            String typeName = type instanceof IdentifierTree || type instanceof MemberSelectTree
                    ? type.toString().replaceAll("\\s+", "")
                    : "";
            return typeName.equals("Object") || typeName.equals("java.lang.Object");
        }

        /** Checkstyle has a separate COMPACT_CTOR_DEF token, which this check does not visit. */
        private boolean isCompactConstructor(MethodTree constructor, ClassTree owner) {
            if (owner.getKind() != Tree.Kind.RECORD || constructor.getBody() == null) {
                return false;
            }
            int bodyIndex = tokenIndexAt(start(constructor.getBody()));
            return bodyIndex > 0 && tokens.get(bodyIndex - 1).text().contentEquals(owner.getSimpleName());
        }

        private boolean isAnonymous(TreePath classPath) {
            return classPath.getParentPath() != null && classPath.getParentPath().getLeaf() instanceof NewClassTree;
        }

        /** The implicitly declared class of a compact source file is synthetic: final, with no source modifiers. */
        private boolean isImplicitClass(TreePath path) {
            return path != null
                    && path.getLeaf() instanceof ClassTree classTree
                    && path.getParentPath() != null
                    && path.getParentPath().getLeaf() instanceof CompilationUnitTree
                    && classTree.getModifiers().getFlags().contains(Modifier.FINAL)
                    && positions.getStartPosition(unit, classTree.getModifiers()) < 0;
        }

        private void flag(ModifiersTree modifiers, String... keywords) {
            for (int index : modifierTokens(modifiers)) {
                String text = tokens.get(index).text();
                for (String keyword : keywords) {
                    if (text.equals(keyword)) {
                        redundant.add(index);
                    }
                }
            }
        }

        /** Indices of modifier keyword tokens inside a modifiers list, skipping annotations. */
        private List<Integer> modifierTokens(ModifiersTree modifiers) {
            int start = start(modifiers);
            int end = end(modifiers);
            if (start < 0 || end <= start) {
                return List.of();
            }
            List<int[]> annotationRanges = new ArrayList<>();
            for (AnnotationTree annotation : modifiers.getAnnotations()) {
                annotationRanges.add(new int[]{start(annotation), end(annotation)});
            }
            List<Integer> result = new ArrayList<>();
            for (int i = tokenIndexAt(start); i >= 0 && i < tokens.size() && tokens.get(i).start() < end; i++) {
                JavaSourceTokens.Token token = tokens.get(i);
                if (MODIFIER_KEYWORDS.contains(token.text()) && !insideAny(token.start(), annotationRanges)) {
                    result.add(i);
                }
            }
            return result;
        }

        private static boolean insideAny(int offset, List<int[]> ranges) {
            for (int[] range : ranges) {
                if (offset >= range[0] && offset < range[1]) {
                    return true;
                }
            }
            return false;
        }

        /** Index of the first token starting at or after {@code offset}. */
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

        private int start(Tree tree) {
            long position = positions.getStartPosition(unit, tree);
            return position < 0 ? -1 : (int) position - prefixLength;
        }

        private int end(Tree tree) {
            long position = positions.getEndPosition(unit, tree);
            return position < 0 ? -1 : (int) position - prefixLength;
        }
    }
}
