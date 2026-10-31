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
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+(static\\s+)?([^;\\s]+)\\s*;(.*)$", Pattern.DOTALL);

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
        ImportSection section = ImportSection.find(text);
        if (section == null) {
            return text;
        }

        List<ImportLine> imports = new ArrayList<>();
        for (String line : section.imports()) {
            Matcher matcher = IMPORT_PATTERN.matcher(line);
            if (!matcher.matches()) {
                return text;
            }
            imports.add(new ImportLine(matcher.group(1) != null, matcher.group(2), matcher.group(3)));
        }

        return section.replace(buildOrderedImports(imports), true);
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

