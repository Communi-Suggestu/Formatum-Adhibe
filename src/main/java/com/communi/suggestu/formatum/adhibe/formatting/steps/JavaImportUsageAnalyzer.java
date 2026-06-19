package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreePathScanner;
import com.sun.source.util.Trees;

import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.JavaCompiler;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Uses javac's public Tree API to extract import usage from source text.
 * This stays language-level compatible with the running JDK (including Java 25).
 */
final class JavaImportUsageAnalyzer {
    private JavaImportUsageAnalyzer() {
    }

    static Optional<ImportUsage> analyze(String sourceText) {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            return Optional.empty();
        }

        try {
            JavaSource source = new JavaSource(sourceText);
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    null,
                    null,
                    List.of("-proc:none"),
                    null,
                    List.of(source)
            );

            CompilationUnitTree unit = first(task.parse());
            if (unit == null) {
                return Optional.empty();
            }

            // Attribution makes Trees#getElement available for identifiers.
            task.analyze();

            Trees trees = Trees.instance(task);
            UsageScanner scanner = new UsageScanner(trees);
            scanner.scan(unit, null);
            return Optional.of(scanner.toImportUsage());
        } catch (Exception ignored) {
            return Optional.empty();
        }
    }

    private static CompilationUnitTree first(Iterable<? extends CompilationUnitTree> trees) {
        for (CompilationUnitTree tree : trees) {
            return tree;
        }
        return null;
    }

    private static String packageName(TypeElement typeElement) {
        Element current = typeElement;
        while (current != null && !(current instanceof PackageElement)) {
            current = current.getEnclosingElement();
        }
        if (current instanceof PackageElement pkg) {
            return pkg.getQualifiedName().toString();
        }
        return "";
    }

    private static boolean isTopLevel(TypeElement typeElement) {
        return typeElement.getEnclosingElement() instanceof PackageElement;
    }

    private static final class UsageScanner extends TreePathScanner<Void, Void> {
        private final Trees trees;
        private final Set<String> typeImportTargets = new LinkedHashSet<>();
        private final Map<String, LinkedHashSet<String>> topLevelTypesByPackage = new LinkedHashMap<>();
        private final Map<String, LinkedHashSet<String>> staticMembersByOwner = new LinkedHashMap<>();

        private UsageScanner(Trees trees) {
            this.trees = trees;
        }

        @Override
        public Void visitIdentifier(IdentifierTree node, Void unused) {
            Element element = trees.getElement(getCurrentPath());
            if (element instanceof TypeElement typeElement) {
                String target = typeElement.getQualifiedName().toString();
                if (!target.isBlank()) {
                    typeImportTargets.add(target);
                    if (isTopLevel(typeElement)) {
                        String packageName = packageName(typeElement);
                        topLevelTypesByPackage.computeIfAbsent(packageName, ignored -> new LinkedHashSet<>()).add(target);
                    }
                }
            } else if (isStaticMember(element)) {
                TypeElement owner = ownerType(element);
                if (owner != null) {
                    staticMembersByOwner
                            .computeIfAbsent(owner.getQualifiedName().toString(), ignored -> new LinkedHashSet<>())
                            .add(element.getSimpleName().toString());
                }
            }
            return super.visitIdentifier(node, unused);
        }

        private static boolean isStaticMember(Element element) {
            if (element == null) {
                return false;
            }
            ElementKind kind = element.getKind();
            if (!(kind == ElementKind.FIELD || kind == ElementKind.METHOD || kind == ElementKind.ENUM_CONSTANT)) {
                return false;
            }
            return element.getModifiers().contains(javax.lang.model.element.Modifier.STATIC);
        }

        private static TypeElement ownerType(Element element) {
            Element owner = element == null ? null : element.getEnclosingElement();
            while (owner != null && !(owner instanceof TypeElement)) {
                owner = owner.getEnclosingElement();
            }
            return owner instanceof TypeElement type ? type : null;
        }

        private ImportUsage toImportUsage() {
            Map<String, Set<String>> copiedTopLevel = new LinkedHashMap<>();
            for (Map.Entry<String, LinkedHashSet<String>> entry : topLevelTypesByPackage.entrySet()) {
                copiedTopLevel.put(entry.getKey(), Set.copyOf(entry.getValue()));
            }

            Map<String, Set<String>> copiedStaticMembers = new LinkedHashMap<>();
            for (Map.Entry<String, LinkedHashSet<String>> entry : staticMembersByOwner.entrySet()) {
                copiedStaticMembers.put(entry.getKey(), Set.copyOf(entry.getValue()));
            }

            return new ImportUsage(true, Set.copyOf(typeImportTargets), copiedTopLevel, copiedStaticMembers);
        }
    }

    static final class ImportUsage {
        private static final ImportUsage UNAVAILABLE = new ImportUsage(false, Set.of(), Map.of(), Map.of());

        private final boolean available;
        private final Set<String> typeImportTargets;
        private final Map<String, Set<String>> topLevelTypesByPackage;
        private final Map<String, Set<String>> staticMembersByOwner;

        private ImportUsage(
                boolean available,
                Set<String> typeImportTargets,
                Map<String, Set<String>> topLevelTypesByPackage,
                Map<String, Set<String>> staticMembersByOwner) {
            this.available = available;
            this.typeImportTargets = typeImportTargets;
            this.topLevelTypesByPackage = topLevelTypesByPackage;
            this.staticMembersByOwner = staticMembersByOwner;
        }

        static ImportUsage unavailable() {
            return UNAVAILABLE;
        }

        boolean available() {
            return available;
        }

        boolean isUnavailable() {
            return !available;
        }

        boolean usesTypeImport(String importTarget) {
            return typeImportTargets.contains(importTarget);
        }

        Set<String> usedTopLevelTypesInPackage(String packageName) {
            return topLevelTypesByPackage.getOrDefault(packageName, Set.of());
        }

        boolean usesStaticMemberImport(String ownerType, String memberName) {
            return staticMembersByOwner.getOrDefault(ownerType, Set.of()).contains(memberName);
        }

        Set<String> usedStaticMembersOf(String ownerType) {
            return staticMembersByOwner.getOrDefault(ownerType, Set.of());
        }
    }

    private static final class JavaSource extends SimpleJavaFileObject {
        private final String source;

        private JavaSource(String source) {
            super(URI.create("string:///FormatumAdhibeSnippet.java"), Kind.SOURCE);
            this.source = source;
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }
    }
}




