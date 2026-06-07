package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class CheckstyleImportOrderStep extends FormattingStep {
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^import\\s+(static\\s+)?([^;\\s]+)\\s*;(.*)$");

    @Inject
    public CheckstyleImportOrderStep() {
    }

    @Input
    public abstract ListProperty<String> getGroups();

    @Input
    public abstract Property<Boolean> getSeparated();

    @Input
    public abstract Property<String> getOption();

    @Input
    public abstract Property<Boolean> getSortStaticImportsAlphabetically();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> reorderImports(TextFormattingUtils.normalizeNewlines(text));
    }

    private String reorderImports(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        int firstImport = -1;
        int lastImport = -1;

        for (int i = 0; i < lines.size(); i++) {
            String trimmed = lines.get(i).trim();
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

        List<String> reordered = buildOrderedImports(imports);
        List<String> output = new ArrayList<>(lines.subList(0, firstImport));
        output.addAll(reordered);
        if (lastImport + 1 < lines.size() && !lines.get(lastImport + 1).isBlank()) {
            output.add("");
        }
        output.addAll(lines.subList(lastImport + 1, lines.size()));
        return String.join("\n", output);
    }

    private List<String> buildOrderedImports(List<ImportLine> imports) {
        List<ImportLine> regular = imports.stream().filter(it -> !it.staticImport).toList();
        List<ImportLine> statik = imports.stream().filter(ImportLine::staticImport).toList();

        List<String> sections = new ArrayList<>();
        String option = getOption().get().toLowerCase(Locale.ROOT);
        if ("top".equals(option)) {
            appendSection(sections, formatStaticImports(statik));
            appendSection(sections, formatRegularImports(regular));
        } else {
            appendSection(sections, formatRegularImports(regular));
            appendSection(sections, formatStaticImports(statik));
        }
        return sections;
    }

    private List<String> formatRegularImports(List<ImportLine> regular) {
        if (regular.isEmpty()) {
            return List.of();
        }
        return formatByGroup(regular);
    }

    private List<String> formatStaticImports(List<ImportLine> statik) {
        if (statik.isEmpty()) {
            return List.of();
        }
        List<ImportLine> prepared = getSortStaticImportsAlphabetically().get()
                ? statik.stream().sorted(Comparator.comparing(ImportLine::target)).toList()
                : statik;
        return prepared.stream().map(this::render).toList();
    }

    private List<String> formatByGroup(List<ImportLine> imports) {
        List<String> groups = getGroups().get();
        List<List<ImportLine>> grouped = new ArrayList<>();
        for (int i = 0; i < groups.size(); i++) {
            grouped.add(new ArrayList<>());
        }
        grouped.add(new ArrayList<>());

        for (ImportLine importLine : imports) {
            int groupIndex = groupIndex(importLine.target(), groups);
            grouped.get(groupIndex).add(importLine);
        }

        List<String> rendered = new ArrayList<>();
        boolean separated = getSeparated().get();
        for (List<ImportLine> group : grouped) {
            if (group.isEmpty()) {
                continue;
            }
            group.stream().sorted(Comparator.comparing(ImportLine::target)).map(this::render).forEach(rendered::add);
            if (separated) {
                rendered.add("");
            }
        }
        if (separated && !rendered.isEmpty()) {
            rendered.removeLast();
        }
        return rendered;
    }

    private int groupIndex(String target, List<String> groups) {
        int wildcardIndex = groups.indexOf("*");
        int bestIndex = wildcardIndex >= 0 ? wildcardIndex : groups.size();
        int bestLength = -1;
        for (int i = 0; i < groups.size(); i++) {
            String group = groups.get(i);
            if ("*".equals(group)) {
                continue;
            }
            if (target.startsWith(group) && group.length() > bestLength) {
                bestLength = group.length();
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void appendSection(List<String> destination, List<String> section) {
        if (section.isEmpty()) {
            return;
        }
        if (!destination.isEmpty() && getSeparated().get()) {
            destination.add("");
        }
        destination.addAll(section);
    }

    private String render(ImportLine importLine) {
        return "import " + (importLine.staticImport ? "static " : "") + importLine.target + ";" + importLine.trailing;
    }

    private record ImportLine(boolean staticImport, String target, String trailing) {
    }
}




