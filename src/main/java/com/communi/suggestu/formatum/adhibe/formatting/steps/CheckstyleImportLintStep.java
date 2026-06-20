package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CheckstyleImportLintStep extends FormattingStep {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(static\\s+)?([^;\\s]+)\\s*;(.*)$");

    @Inject
    public CheckstyleImportLintStep() {
    }

    @Inject
    protected abstract ProjectLayout getProjectLayout();

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

    @Classpath
    @InputFiles
    @Optional
    public abstract ConfigurableFileCollection getAnalysisClasspath();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAnalysisSourcepath();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> sanitizeImports(fileName, TextFormattingUtils.normalizeNewlines(text));
    }

    private String sanitizeImports(String fileName, String text) {
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

        JavaImportUsageAnalyzer.AnalysisResult analysis = JavaImportUsageAnalyzer
                .analyze(text, fileName, getAnalysisClasspath().getFiles(), getAnalysisSourcepath().getFiles());
        if (analysis.hasErrors()) {
            writeDiagnosticsFile(fileName, analysis.diagnostics());
        }
        JavaImportUsageAnalyzer.ImportUsage usage = analysis
                .usage()
                .orElse(JavaImportUsageAnalyzer.ImportUsage.unavailable());
        List<ImportLine> sanitized = applyRules(imports, packageName, usage);

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

    private List<ImportLine> applyRules(List<ImportLine> imports, String packageName, JavaImportUsageAnalyzer.ImportUsage usage) {
        LinkedHashSet<ImportLine> output = new LinkedHashSet<>();
        for (ImportLine importLine : imports) {
            if (getRemoveIllegalImports().get() && isIllegal(importLine)) {
                continue;
            }

            if (getRemoveRedundantImports().get() && isRedundant(importLine, packageName)) {
                continue;
            }

            if (getRemoveUnusedImports().get() && isUnused(importLine, usage)) {
                continue;
            }

            if (getAvoidStarImport().get() && importLine.target().endsWith(".*")) {
                if (isSafeStarRemoval(importLine, packageName)) {
                    continue;
                }
                List<ImportLine> expanded = expandStar(importLine, usage);
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
        if (!currentPackage.isEmpty() && !importLine.staticImport()) {
            return packageOf(importLine.target()).equals(currentPackage);
        }
        return false;
    }

    private boolean isUnused(ImportLine importLine, JavaImportUsageAnalyzer.ImportUsage usage) {
        if (usage.isUnavailable()) {
            return false;
        }
        if (importLine.target().endsWith(".*")) {
            return false;
        }
        if (importLine.staticImport()) {
            String owner = packageOf(importLine.target());
            String member = simpleName(importLine.target());
            return !usage.usesStaticMemberImport(owner, member);
        }
        return !usage.usesTypeImport(importLine.target());
    }

    private boolean isSafeStarRemoval(ImportLine importLine, String currentPackage) {
        String packagePrefix = importLine.target().substring(0, importLine.target().length() - 2);
        if (!importLine.staticImport() && packagePrefix.equals("java.lang")) {
            return true;
        }
        return !currentPackage.isEmpty() && !importLine.staticImport() && packagePrefix.equals(currentPackage);
    }

    private List<ImportLine> expandStar(ImportLine importLine, JavaImportUsageAnalyzer.ImportUsage usage) {
        if (usage.isUnavailable()) {
            return List.of();
        }
        String starTarget = importLine.target().substring(0, importLine.target().length() - 2);
        if (importLine.staticImport()) {
            List<String> members = usage.usedStaticMembersOf(starTarget).stream()
                    .sorted()
                    .toList();
            if (members.isEmpty()) {
                return List.of();
            }
            List<ImportLine> expanded = new ArrayList<>();
            for (String member : members) {
                expanded.add(new ImportLine(true, starTarget + "." + member, importLine.trailing()));
            }
            return expanded;
        }

        List<String> usedTopLevelTypes = usage.usedTopLevelTypesInPackage(starTarget).stream()
                .sorted(Comparator.naturalOrder())
                .toList();
        if (usedTopLevelTypes.isEmpty()) {
            return List.of();
        }

        List<ImportLine> expanded = new ArrayList<>();
        for (String target : usedTopLevelTypes) {
            expanded.add(new ImportLine(false, target, importLine.trailing()));
        }
        return expanded;
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

    private void writeDiagnosticsFile(String fileName, List<String> diagnostics) {
        try {
            Path root = getProjectLayout().getBuildDirectory().dir("formatting/parsing/errors").get().getAsFile().toPath();
            Files.createDirectories(root);
            Path target = root.resolve(sanitizeFileName(fileName));
            String content = String.join("\n", diagnostics) + "\n";
            Files.writeString(target, content);
        } catch (IOException ignored) {
            // Keep formatting non-fatal if diagnostics logging cannot be written.
        }
    }

    private static String sanitizeFileName(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return "unknown.java";
        }
        return fileName.replace('\\', '_').replace('/', '_');
    }

    private record ImportLine(boolean staticImport, String target, String trailing) {
    }
}

