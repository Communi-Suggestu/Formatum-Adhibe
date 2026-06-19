package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CheckstyleImportLintStep extends FormattingStep {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(static\\s+)?([^;\\s]+)\\s*;(.*)$");
    private static final Pattern IDENTIFIER_PATTERN = Pattern.compile("\\b[A-Za-z_][A-Za-z0-9_]*\\b");
    private static final Set<String> COMMON_JAVA_UTIL_TYPES = Set.of(
            "ArrayList", "Collection", "Collections", "Comparator", "Deque", "EnumSet", "HashMap",
            "HashSet", "LinkedHashMap", "LinkedHashSet", "List", "Map", "Objects", "Optional", "Queue",
            "Set", "SortedMap", "SortedSet", "TreeMap", "TreeSet"
    );

    @Inject
    public CheckstyleImportLintStep() {
    }

    @Input
    public abstract Property<Boolean> getAvoidStarImport();

    @Input
    public abstract Property<Boolean> getRemoveIllegalImports();

    @Input
    public abstract ListProperty<String> getIllegalClasses();

    @Input
    public abstract ListProperty<String> getIllegalPkgs();

    @Input
    public abstract Property<Boolean> getRemoveRedundantImports();

    @Input
    public abstract Property<Boolean> getRemoveUnusedImports();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> sanitizeImports(TextFormattingUtils.normalizeNewlines(text));
    }

    private String sanitizeImports(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        int firstImport = -1;
        int lastImport = -1;
        String packageName = "";

        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
            if (trimmed.startsWith("package ")) {
                packageName = trimmed.substring("package ".length(), trimmed.length() - 1).trim();
            }
            if (trimmed.startsWith("import ")) {
                if (firstImport < 0) {
                    firstImport = i;
                }
                lastImport = i;
                continue;
            }
            if (firstImport >= 0) {
                if (trimmed.isEmpty()) {
                    lastImport = i;
                    continue;
                }
                if (trimmed.startsWith("//") || trimmed.startsWith("/*") || trimmed.startsWith("*")) {
                    return text;
                }
                break;
            }
        }

        if (firstImport < 0) {
            return text;
        }

        List<ImportLine> imports = new ArrayList<>();
        for (int i = firstImport; i <= lastImport; i++) {
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            Matcher matcher = IMPORT_PATTERN.matcher(line);
            if (!matcher.matches()) {
                return text;
            }
            imports.add(new ImportLine(matcher.group(1) != null, matcher.group(2), matcher.group(3)));
        }

        Set<String> usedIdentifiers = usedIdentifiers(lines, lastImport + 1);
        List<ImportLine> sanitized = applyRules(imports, packageName, usedIdentifiers);

        if (sanitized.equals(imports)) {
            return text;
        }

        List<String> rendered = sanitized.stream().map(this::render).toList();
        List<String> output = new ArrayList<>(lines.subList(0, firstImport));
        output.addAll(rendered);
        if (lastImport + 1 < lines.size() && !lines.get(lastImport + 1).isBlank()) {
            output.add("");
        }
        output.addAll(lines.subList(lastImport + 1, lines.size()));
        return String.join("\n", output);
    }

    private List<ImportLine> applyRules(List<ImportLine> imports, String packageName, Set<String> usedIdentifiers) {
        LinkedHashSet<ImportLine> output = new LinkedHashSet<>();
        for (ImportLine importLine : imports) {
            if (getRemoveIllegalImports().get() && isIllegal(importLine)) {
                continue;
            }

            if (getRemoveRedundantImports().get() && isRedundant(importLine, packageName)) {
                continue;
            }

            if (getRemoveUnusedImports().get() && isUnused(importLine, usedIdentifiers)) {
                continue;
            }

            if (getAvoidStarImport().get() && importLine.target().endsWith(".*")) {
                if (isSafeStarRemoval(importLine, packageName)) {
                    continue;
                }
                List<ImportLine> expanded = expandKnownStar(importLine, usedIdentifiers);
                if (!expanded.isEmpty()) {
                    output.addAll(expanded);
                    continue;
                }
            }

            output.add(importLine);
        }
        return List.copyOf(output);
    }

    private boolean isIllegal(ImportLine importLine) {
        Set<String> illegalClasses = new LinkedHashSet<>(getIllegalClasses().getOrElse(List.of()));
        Set<String> illegalPkgs = new LinkedHashSet<>(getIllegalPkgs().getOrElse(List.of()));
        if (illegalClasses.contains(importLine.target())) {
            return true;
        }
        String packagePrefix = packageOf(importLine.target());
        return illegalPkgs.stream().anyMatch(pkg -> packagePrefix.equals(pkg) || packagePrefix.startsWith(pkg + "."));
    }

    private boolean isRedundant(ImportLine importLine, String currentPackage) {
        if (importLine.target().endsWith(".*")) {
            return false;
        }
        if (!importLine.staticImport() && importLine.target().startsWith("java.lang.")) {
            return true;
        }
        if (!currentPackage.isEmpty() && !importLine.staticImport()) {
            return packageOf(importLine.target()).equals(currentPackage);
        }
        return false;
    }

    private boolean isUnused(ImportLine importLine, Set<String> usedIdentifiers) {
        if (importLine.target().endsWith(".*")) {
            return false;
        }
        String name = simpleName(importLine.target());
        return !usedIdentifiers.contains(name);
    }

    private boolean isSafeStarRemoval(ImportLine importLine, String currentPackage) {
        String packagePrefix = importLine.target().substring(0, importLine.target().length() - 2);
        if (!importLine.staticImport() && packagePrefix.equals("java.lang")) {
            return true;
        }
        return !currentPackage.isEmpty() && !importLine.staticImport() && packagePrefix.equals(currentPackage);
    }

    private List<ImportLine> expandKnownStar(ImportLine importLine, Set<String> usedIdentifiers) {
        if (importLine.staticImport()) {
            return List.of();
        }
        String packagePrefix = importLine.target().substring(0, importLine.target().length() - 2);
        if (!"java.util".equals(packagePrefix)) {
            return List.of();
        }

        List<ImportLine> expanded = new ArrayList<>();
        for (String identifier : COMMON_JAVA_UTIL_TYPES) {
            if (usedIdentifiers.contains(identifier)) {
                expanded.add(new ImportLine(false, "java.util." + identifier, importLine.trailing()));
            }
        }
        return expanded;
    }

    private static String stripStringsAndComments(String text) {
        StringBuilder result = new StringBuilder(text.length());
        boolean inLineComment = false;
        boolean inBlockComment = false;
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            char next = i + 1 < text.length() ? text.charAt(i + 1) : '\0';

            if (inLineComment) {
                if (c == '\n') {
                    inLineComment = false;
                    result.append(c);
                } else {
                    result.append(' ');
                }
                continue;
            }

            if (inBlockComment) {
                if (c == '*' && next == '/') {
                    inBlockComment = false;
                    result.append("  ");
                    i++;
                } else {
                    result.append(c == '\n' ? '\n' : ' ');
                }
                continue;
            }

            if (inSingleQuote) {
                if (c == '\\') {
                    result.append("  ");
                    i++;
                    continue;
                }
                if (c == '\'') {
                    inSingleQuote = false;
                }
                result.append(' ');
                continue;
            }

            if (inDoubleQuote) {
                if (c == '\\') {
                    result.append("  ");
                    i++;
                    continue;
                }
                if (c == '"') {
                    inDoubleQuote = false;
                }
                result.append(' ');
                continue;
            }

            if (c == '/' && next == '/') {
                inLineComment = true;
                result.append("  ");
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                inBlockComment = true;
                result.append("  ");
                i++;
                continue;
            }
            if (c == '\'') {
                inSingleQuote = true;
                result.append(' ');
                continue;
            }
            if (c == '"') {
                inDoubleQuote = true;
                result.append(' ');
                continue;
            }

            result.append(c);
        }

        return result.toString();
    }

    private static Set<String> usedIdentifiers(List<String> lines, int bodyStartIndex) {
        if (bodyStartIndex >= lines.size()) {
            return Set.of();
        }
        String body = String.join("\n", lines.subList(bodyStartIndex, lines.size()));
        String scrubbed = stripStringsAndComments(body);
        Matcher matcher = IDENTIFIER_PATTERN.matcher(scrubbed);
        Set<String> identifiers = new LinkedHashSet<>();
        while (matcher.find()) {
            identifiers.add(matcher.group());
        }
        return identifiers;
    }

    private static String packageOf(String importTarget) {
        int lastDot = importTarget.lastIndexOf('.');
        return lastDot < 0 ? "" : importTarget.substring(0, lastDot);
    }

    private static String simpleName(String importTarget) {
        int lastDot = importTarget.lastIndexOf('.');
        return lastDot < 0 ? importTarget : importTarget.substring(lastDot + 1);
    }

    private String render(ImportLine importLine) {
        return "import " + (importLine.staticImport() ? "static " : "") + importLine.target() + ";" + importLine.trailing();
    }

    private record ImportLine(boolean staticImport, String target, String trailing) {
    }
}

