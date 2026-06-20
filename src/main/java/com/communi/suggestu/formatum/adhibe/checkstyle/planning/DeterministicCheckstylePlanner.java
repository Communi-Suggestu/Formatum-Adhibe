package com.communi.suggestu.formatum.adhibe.checkstyle.planning;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.classification.CheckstyleRuleClassifier;
import com.communi.suggestu.formatum.adhibe.checkstyle.classification.RuleSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

public final class DeterministicCheckstylePlanner {
    private final CheckstyleRuleClassifier classifier = new CheckstyleRuleClassifier();

    public PlanningResult plan(CheckstyleModuleSpec root, String stepNamePrefix) {
        List<GeneratedImmaculateStepSpec> steps = new ArrayList<>();
        List<PlanningDiagnostic> diagnostics = new ArrayList<>();
        AtomicInteger stepCounter = new AtomicInteger();
        int inheritedTabWidth = root.property("tabWidth").map(Integer::parseInt).orElse(4);

        walk(root, stepNamePrefix, inheritedTabWidth, stepCounter, steps, diagnostics);

        return new PlanningResult(List.copyOf(steps), List.copyOf(diagnostics));
    }

    private void walk(
            CheckstyleModuleSpec module,
            String stepNamePrefix,
            int inheritedTabWidth,
            AtomicInteger stepCounter,
            List<GeneratedImmaculateStepSpec> steps,
            List<PlanningDiagnostic> diagnostics
    ) {
        int currentTabWidth = module.property("tabWidth").map(Integer::parseInt).orElse(inheritedTabWidth);
        GeneratedImmaculateStepSpec planned = planSingle(module, stepNamePrefix, stepCounter.get(), currentTabWidth);
        if (planned != null) {
            steps.add(planned);
            stepCounter.incrementAndGet();
        } else {
            RuleSupport support = classifier.classify(module).support();
            if (support == RuleSupport.AUTO_FIX_SAFE && !module.children().isEmpty()) {
                diagnostics.add(new PlanningDiagnostic(module.path(), "Safe rule is a container module and is not turned into a direct step."));
            } else if (support == RuleSupport.AUTO_FIX_SAFE) {
                diagnostics.add(new PlanningDiagnostic(module.path(), "Safe rule is not implemented in Phase B yet."));
            }
        }

        for (CheckstyleModuleSpec child : module.children()) {
            walk(child, stepNamePrefix, currentTabWidth, stepCounter, steps, diagnostics);
        }
    }

    private GeneratedImmaculateStepSpec planSingle(CheckstyleModuleSpec module, String prefix, int stepIndex, int inheritedTabWidth) {
        return switch (module.name()) {
            case "NewlineAtEndOfFile" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.ENSURE_TRAILING_NEWLINE, module.message().orElse(null));
            case "RegexpSingleline" -> planRegexpSingleline(module, prefix, stepIndex);
            case "RegexpMultiline" -> planRegexpMultiline(module, prefix, stepIndex);
            case "RegexpSinglelineJava" -> planRegexpSinglelineJava(module, prefix, stepIndex, inheritedTabWidth);
            case "LeftCurly" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.LEFT_CURLY, module.message().orElse(null));
            case "RightCurly" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.RIGHT_CURLY, module.message().orElse(null));
            case "NeedBraces", "NeedsBraces" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.NEED_BRACES, module.message().orElse(null));
            case "EmptyLineSeparator" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.EMPTY_LINE_SEPARATOR, module.message().orElse(null));
            case "OperatorWrap" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.OPERATOR_WRAP, module.message().orElse(null));
            case "SeparatorWrap" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.SEPARATOR_WRAP, module.message().orElse(null));
            case "Indentation" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.INDENTATION, module.message().orElse(null));
            case "ParenPad" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.PAREN_PAD, module.message().orElse(null));
            case "NoWhitespaceBefore" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.NO_WHITESPACE_BEFORE, module.message().orElse(null));
            case "NoWhitespaceAfter" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.NO_WHITESPACE_AFTER, module.message().orElse(null));
            case "WhitespaceAfter" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.WHITESPACE_AFTER, module.message().orElse(null));
            case "WhitespaceAround" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.WHITESPACE_AROUND, module.message().orElse(null));
            case "SingleSpaceSeparator" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.SINGLE_SPACE_SEPARATOR, module.message().orElse(null));
            case "GenericWhitespace" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.GENERIC_WHITESPACE, module.message().orElse(null));
            case "CommentsIndentation" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.COMMENTS_INDENTATION, module.message().orElse(null));
            case "AvoidStarImport" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.AVOID_STAR_IMPORT, module.message().orElse(null));
            case "IllegalImport" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.ILLEGAL_IMPORT, module.message().orElse(null));
            case "RedundantImport" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.REDUNDANT_IMPORT, module.message().orElse(null));
            case "UnusedImport", "UnusedImports" -> new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), GeneratedStepKind.UNUSED_IMPORT, module.message().orElse(null));
            case "ImportOrder" -> planImportOrder(module, prefix, stepIndex);
            default -> null;
        };
    }

    private GeneratedImmaculateStepSpec planRegexpSingleline(CheckstyleModuleSpec module, String prefix, int stepIndex) {
        String format = module.property("format").orElse("");
        if ("\\s+$".equals(format)) {
            return new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, "TrailingWhitespace"), module.path(), GeneratedStepKind.TRIM_TRAILING_WHITESPACE, module.message().orElse(null));
        }
        return null;
    }

    private GeneratedImmaculateStepSpec planRegexpMultiline(CheckstyleModuleSpec module, String prefix, int stepIndex) {
        String format = module.property("format").orElse("");
        String normalized = format.replace("&lt;", "<");
        return switch (normalized) {
            case "\\n[\\t ]*\\r?\\n[\\t ]*\\r?\\n" ->
                    new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, "AdjacentBlankLines"), module.path(), GeneratedStepKind.COLLAPSE_CONSECUTIVE_BLANK_LINES, module.message().orElse(null));
            case "\\{[\\t ]*\\r?\\n[\\t ]*\\r?\\n" ->
                    new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, "BlankLineAfterOpeningBrace"), module.path(), GeneratedStepKind.REMOVE_BLANK_LINE_AFTER_OPENING_BRACE, module.message().orElse(null));
            case "\\n[\\t ]*\\r?\\n[\\t ]*\\}" ->
                    new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, "BlankLineBeforeClosingBrace"), module.path(), GeneratedStepKind.REMOVE_BLANK_LINE_BEFORE_CLOSING_BRACE, module.message().orElse(null));
            case "(?<=\\n)([\\t]+)(?:[^/\\r\\n \\t][^\\r\\n]*|/[^/\\r\\n][^\\r\\n]*|[^/\\r\\n][^\\r\\n]*(\\r?\\n\\1//[^\\r\\n]*)+)\\r?\\n\\1(|(if|do|while|for|try)[^\\r\\n]+)\\{[\\t ]*\\r?\\n" ->
                    new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, "BlankLineBeforeIndentedBlock"), module.path(), GeneratedStepKind.INSERT_BLANK_LINE_BEFORE_INDENTED_BLOCK, module.message().orElse(null));
            case "(?<=\\n)([\\t]+)\\}\\r?\\n\\1(?:[^\\r\\n\\}cd]|c[^\\r\\na]|ca[^\\r\\ns]|d[^\\r\\ne]|de[^\\r\\nf])" ->
                    new SimpleGeneratedStepSpec(stepName(prefix, stepIndex, "BlankLineAfterIndentedBlock"), module.path(), GeneratedStepKind.INSERT_BLANK_LINE_AFTER_INDENTED_BLOCK, module.message().orElse(null));
            default -> null;
        };
    }

    private GeneratedImmaculateStepSpec planRegexpSinglelineJava(CheckstyleModuleSpec module, String prefix, int stepIndex, int inheritedTabWidth) {
        String format = module.property("format").orElse("");
        if (!"^\\t* ([^*]|\\*[^ /])".equals(format)) {
            return null;
        }
        int tabWidth = module.property("tabWidth")
                .map(Integer::parseInt)
                .orElse(inheritedTabWidth);
        return new LeadingWhitespaceToTabsGeneratedStepSpec(stepName(prefix, stepIndex, "LeadingSpacesToTabs"), module.path(), tabWidth, module.message().orElse(null));
    }

    private GeneratedImmaculateStepSpec planImportOrder(CheckstyleModuleSpec module, String prefix, int stepIndex) {
        List<String> groups = module.property("groups")
                .stream()
                .flatMap(value -> Stream.of(value.split(",")))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .toList();
        if (groups.isEmpty()) {
            return null;
        }

        boolean separated = module.property("separated").map(Boolean::parseBoolean).orElse(false);
        String option = module.property("option").orElse("under").toLowerCase(Locale.ROOT);
        boolean sortStatic = module.property("sortStaticImportsAlphabetically").map(Boolean::parseBoolean).orElse(false);
        return new ImportOrderGeneratedStepSpec(stepName(prefix, stepIndex, module.name()), module.path(), groups, separated, option, sortStatic, module.message().orElse(null));
    }

    private static String stepName(String prefix, int stepIndex, String stem) {
        String normalizedPrefix = prefix == null || prefix.isBlank() ? "checkstyle" : prefix;
        return normalizedPrefix + stepIndex + Character.toUpperCase(stem.charAt(0)) + stem.substring(1);
    }
}


