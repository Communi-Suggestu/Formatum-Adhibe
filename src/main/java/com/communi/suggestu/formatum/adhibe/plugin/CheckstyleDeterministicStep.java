package com.communi.suggestu.formatum.adhibe.plugin;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleConfigParser;
import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.DeterministicCheckstylePlanner;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.GeneratedImmaculateStepSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.GeneratedStepKind;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.ImportOrderGeneratedStepSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.LeadingWhitespaceToTabsGeneratedStepSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.SimpleGeneratedStepSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.suppression.SuppressionCommentFilterConfig;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.CheckstyleHintsParser;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.HintRegexStep;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.HintRegexStepFactory;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.HintResolver;
import com.communi.suggestu.formatum.adhibe.formatting.steps.CheckstyleImportLintStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.CheckstyleImportOrderStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.CheckstyleLeftCurlyStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.CheckstyleNeedBracesStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.CheckstyleRightCurlyStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.CollapseConsecutiveBlankLinesStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.EnsureTrailingNewlineStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.InsertBlankLineAfterIndentedBlockStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.InsertBlankLineBeforeIndentedBlockStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.LeadingWhitespaceToTabsStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.RemoveBlankLineAfterOpeningBraceStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.RemoveBlankLineBeforeClosingBraceStep;
import com.communi.suggestu.formatum.adhibe.formatting.steps.TextFormattingUtils;
import com.communi.suggestu.formatum.adhibe.formatting.steps.TrimTrailingWhitespaceStep;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.ProblemReporter;
import org.gradle.api.problems.Problems;
import org.gradle.api.problems.Severity;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.Classpath;

import javax.inject.Inject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class CheckstyleDeterministicStep extends FormattingStep {

    // ── Problem taxonomy ──────────────────────────────────────────────────────
    private static final ProblemGroup FORMATTING_GROUP =
            ProblemGroup.create("formatum-adhibe-formatting", "Formatum Adhibe Formatting");

    private static final ProblemGroup HINTS_GROUP =
            ProblemGroup.create("formatum-adhibe-hints", "Formatum Adhibe Hints", FORMATTING_GROUP);

    private static final ProblemId ID_TRAILING_NEWLINE =
            ProblemId.create("ensure-trailing-newline", "Missing trailing newline", FORMATTING_GROUP);
    private static final ProblemId ID_TRAILING_WHITESPACE =
            ProblemId.create("trim-trailing-whitespace", "Trailing whitespace", FORMATTING_GROUP);
    private static final ProblemId ID_CONSECUTIVE_BLANK_LINES =
            ProblemId.create("collapse-consecutive-blank-lines", "Consecutive blank lines", FORMATTING_GROUP);
    private static final ProblemId ID_BLANK_AFTER_OPENING_BRACE =
            ProblemId.create("remove-blank-line-after-opening-brace", "Blank line after opening brace", FORMATTING_GROUP);
    private static final ProblemId ID_BLANK_BEFORE_CLOSING_BRACE =
            ProblemId.create("remove-blank-line-before-closing-brace", "Blank line before closing brace", FORMATTING_GROUP);
    private static final ProblemId ID_MISSING_BLANK_BEFORE_BLOCK =
            ProblemId.create("insert-blank-line-before-indented-block", "Missing blank line before indented block", FORMATTING_GROUP);
    private static final ProblemId ID_MISSING_BLANK_AFTER_BLOCK =
            ProblemId.create("insert-blank-line-after-indented-block", "Missing blank line after indented block", FORMATTING_GROUP);
    private static final ProblemId ID_LEADING_SPACES =
            ProblemId.create("convert-leading-spaces-to-tabs", "Leading spaces instead of tabs", FORMATTING_GROUP);
    private static final ProblemId ID_IMPORT_ORDER =
            ProblemId.create("order-imports", "Incorrect import order", FORMATTING_GROUP);
    private static final ProblemId ID_LEFT_CURLY =
            ProblemId.create("left-curly", "Incorrect left brace placement", FORMATTING_GROUP);
    private static final ProblemId ID_RIGHT_CURLY =
            ProblemId.create("right-curly", "Incorrect right brace placement", FORMATTING_GROUP);
    private static final ProblemId ID_NEED_BRACES =
            ProblemId.create("need-braces", "Missing required braces", FORMATTING_GROUP);
    private static final ProblemId ID_AVOID_STAR_IMPORT =
            ProblemId.create("avoid-star-import", "Avoid wildcard imports", FORMATTING_GROUP);
    private static final ProblemId ID_ILLEGAL_IMPORT =
            ProblemId.create("illegal-import", "Illegal import used", FORMATTING_GROUP);
    private static final ProblemId ID_REDUNDANT_IMPORT =
            ProblemId.create("redundant-import", "Redundant import", FORMATTING_GROUP);
    private static final ProblemId ID_UNUSED_IMPORT =
            ProblemId.create("unused-import", "Unused import", FORMATTING_GROUP);
    private static final ProblemId ID_HINT =
            ProblemId.create("hint-fix", "Formatting hint applied", HINTS_GROUP);
    // ─────────────────────────────────────────────────────────────────────────

    @InputFile
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getCheckstyleConfig();

    @Input
    @Optional
    public abstract Property<String> getStepNamePrefix();

    @InputFile
    @Optional
    @PathSensitive(PathSensitivity.NONE)
    public abstract RegularFileProperty getHintsFile();

    @Input
    public abstract Property<FixMode> getFixMode();

    @Input
    public abstract Property<Boolean> getFailOnUnmatchedHints();

    @Input
    public abstract Property<Boolean> getFailOnHintConflicts();

    @Classpath
    @InputFiles
    @Optional
    public abstract ConfigurableFileCollection getAnalysisClasspath();

    @InputFiles
    @Optional
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract ConfigurableFileCollection getAnalysisSourcepath();

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract Problems getProblems();

    @Override
    public FileFormatter formatter() {
        CheckstyleModuleSpec root = new CheckstyleConfigParser().parse(getCheckstyleConfig().get().getAsFile().toPath());
        Map<String, CheckstyleModuleSpec> modulesByPath = indexByPath(root);
        SuppressionCommentFilterConfig suppressionConfig = SuppressionCommentFilterConfig.fromRoot(root).orElse(null);
        String stepNamePrefix = getStepNamePrefix().getOrElse("checkstyle");
        var planningResult = new DeterministicCheckstylePlanner().plan(root, stepNamePrefix);

        ProblemReporter reporter = getProblems().getReporter();

        List<FileFormatter> formatters = new ArrayList<>();
        int stepIndex = 0;
        for (GeneratedImmaculateStepSpec spec : planningResult.steps()) {
            FileFormatter inner = createFormatter(spec, stepIndex++, modulesByPath);
            CheckstyleModuleSpec sourceModule = modulesByPath.get(spec.sourceModulePath());
            Set<String> checkNames = sourceModule == null
                    ? Set.of(spec.kind().name())
                    : defaultCheckNames(suppressionConfig, sourceModule);
            if (suppressionConfig != null) {
                inner = withSuppressionRanges(inner, suppressionConfig, checkNames);
            }
            formatters.add(withProblemReporting(inner, problemIdFor(spec.kind()), spec.message(), reporter));
        }

        if (getHintsFile().isPresent() && Files.exists(getHintsFile().get().getAsFile().toPath())) {
            var hintsFile = new CheckstyleHintsParser().parse(getHintsFile().get().getAsFile().toPath());
            var resolved = new HintResolver().resolve(root, hintsFile, getFixMode().get());
            if (getFailOnUnmatchedHints().get() && !resolved.unmatchedHints().isEmpty()) {
                throw new IllegalStateException("Unmatched hints found: " + resolved.unmatchedHints().size());
            }
            List<HintRegexStep> hintSteps = new HintRegexStepFactory().create(resolved, getFailOnHintConflicts().get());
            for (HintRegexStep step : hintSteps) {
                formatters.add(withProblemReporting(step.formatter(), ID_HINT, step.message(), reporter));
            }
        }

        return (fileName, text) -> {
            String result = text;
            for (FileFormatter formatter : formatters) {
                String newText = formatter.format(fileName, result);
                if (newText != null) {
                    result = newText;
                }
            }
            return result;
        };
    }

    /**
     * Wraps {@code inner} so that every time the formatter changes the text, Gradle problem
     * warnings are reported – one per affected line in the original text.
     */
    private static FileFormatter withProblemReporting(
            FileFormatter inner,
            ProblemId problemId,
            String message,
            ProblemReporter reporter) {
        return (fileName, text) -> {
            String result = inner.format(fileName, text);
            if (result == null || result.equals(text)) {
                return result;
            }
            List<Integer> changedLines = TextFormattingUtils.findChangedLineNumbers(text, result);
            if (changedLines.isEmpty()) {
                reporter.report(problemId, spec -> {
                    spec.fileLocation(fileName).severity(Severity.WARNING);
                    if (message != null) spec.contextualLabel(message);
                });
            } else {
                for (int lineNum : changedLines) {
                    reporter.report(problemId, spec -> {
                        spec.lineInFileLocation(fileName, lineNum).severity(Severity.WARNING);
                        if (message != null) spec.contextualLabel(message);
                    });
                }
            }
            return result;
        };
    }

    private static ProblemId problemIdFor(GeneratedStepKind kind) {
        return switch (kind) {
            case ENSURE_TRAILING_NEWLINE -> ID_TRAILING_NEWLINE;
            case TRIM_TRAILING_WHITESPACE -> ID_TRAILING_WHITESPACE;
            case COLLAPSE_CONSECUTIVE_BLANK_LINES -> ID_CONSECUTIVE_BLANK_LINES;
            case REMOVE_BLANK_LINE_AFTER_OPENING_BRACE -> ID_BLANK_AFTER_OPENING_BRACE;
            case REMOVE_BLANK_LINE_BEFORE_CLOSING_BRACE -> ID_BLANK_BEFORE_CLOSING_BRACE;
            case INSERT_BLANK_LINE_BEFORE_INDENTED_BLOCK -> ID_MISSING_BLANK_BEFORE_BLOCK;
            case INSERT_BLANK_LINE_AFTER_INDENTED_BLOCK -> ID_MISSING_BLANK_AFTER_BLOCK;
            case LEFT_CURLY -> ID_LEFT_CURLY;
            case RIGHT_CURLY -> ID_RIGHT_CURLY;
            case NEED_BRACES -> ID_NEED_BRACES;
            case CONVERT_LEADING_SPACES_TO_TABS -> ID_LEADING_SPACES;
            case ORDER_IMPORTS -> ID_IMPORT_ORDER;
            case AVOID_STAR_IMPORT -> ID_AVOID_STAR_IMPORT;
            case ILLEGAL_IMPORT -> ID_ILLEGAL_IMPORT;
            case REDUNDANT_IMPORT -> ID_REDUNDANT_IMPORT;
            case UNUSED_IMPORT -> ID_UNUSED_IMPORT;
        };
    }

    private FileFormatter createFormatter(GeneratedImmaculateStepSpec spec, int stepIndex, Map<String, CheckstyleModuleSpec> modulesByPath) {
        String generatedName = "generated" + stepIndex;
        return switch (spec) {
            case LeadingWhitespaceToTabsGeneratedStepSpec tabs -> {
                LeadingWhitespaceToTabsStep step = getObjects().newInstance(LeadingWhitespaceToTabsStep.class, generatedName);
                step.getTabWidth().set(tabs.tabWidth());
                yield step.formatter();
            }
            case ImportOrderGeneratedStepSpec importOrder -> {
                CheckstyleImportOrderStep step = getObjects().newInstance(CheckstyleImportOrderStep.class, generatedName);
                step.getGroups().set(importOrder.groups());
                step.getSeparated().set(importOrder.separated());
                step.getOption().set(importOrder.option());
                step.getSortStaticImportsAlphabetically().set(importOrder.sortStaticImportsAlphabetically());
                yield step.formatter();
            }
            case SimpleGeneratedStepSpec simple -> createSimpleFormatter(simple, generatedName, modulesByPath);
        };
    }

    private FileFormatter createSimpleFormatter(SimpleGeneratedStepSpec spec, String generatedName, Map<String, CheckstyleModuleSpec> modulesByPath) {
        return switch (spec.kind()) {
            case ENSURE_TRAILING_NEWLINE -> getObjects().newInstance(EnsureTrailingNewlineStep.class, generatedName).formatter();
            case TRIM_TRAILING_WHITESPACE -> getObjects().newInstance(TrimTrailingWhitespaceStep.class, generatedName).formatter();
            case COLLAPSE_CONSECUTIVE_BLANK_LINES -> getObjects().newInstance(CollapseConsecutiveBlankLinesStep.class, generatedName).formatter();
            case REMOVE_BLANK_LINE_AFTER_OPENING_BRACE -> getObjects().newInstance(RemoveBlankLineAfterOpeningBraceStep.class, generatedName).formatter();
            case REMOVE_BLANK_LINE_BEFORE_CLOSING_BRACE -> getObjects().newInstance(RemoveBlankLineBeforeClosingBraceStep.class, generatedName).formatter();
            case INSERT_BLANK_LINE_BEFORE_INDENTED_BLOCK -> getObjects().newInstance(InsertBlankLineBeforeIndentedBlockStep.class, generatedName).formatter();
            case INSERT_BLANK_LINE_AFTER_INDENTED_BLOCK -> getObjects().newInstance(InsertBlankLineAfterIndentedBlockStep.class, generatedName).formatter();
            case LEFT_CURLY -> {
                CheckstyleModuleSpec module = modulesByPath.get(spec.sourceModulePath());
                String option = module == null ? "eol" : module.property("option").orElse("eol");
                boolean ignoreEnums = module == null || module.property("ignoreEnums").map(Boolean::parseBoolean).orElse(true);
                CheckstyleLeftCurlyStep step = getObjects().newInstance(CheckstyleLeftCurlyStep.class, generatedName);
                step.getOption().set(option);
                step.getIgnoreEnums().set(ignoreEnums);
                yield step.formatter();
            }
            case RIGHT_CURLY -> {
                CheckstyleModuleSpec module = modulesByPath.get(spec.sourceModulePath());
                String option = module == null ? "same" : module.property("option").orElse("same");
                CheckstyleRightCurlyStep step = getObjects().newInstance(CheckstyleRightCurlyStep.class, generatedName);
                step.getOption().set(option);
                yield step.formatter();
            }
            case NEED_BRACES -> {
                CheckstyleModuleSpec module = modulesByPath.get(spec.sourceModulePath());
                CheckstyleNeedBracesStep step = getObjects().newInstance(CheckstyleNeedBracesStep.class, generatedName);
                step.getTokens().set(splitCsv(module == null ? "" : module.property("tokens").orElse("")));
                step.getAllowSingleLineStatement().set(module != null && module.property("allowSingleLineStatement").map(Boolean::parseBoolean).orElse(false));
                step.getAllowEmptyLoopBody().set(module != null && module.property("allowEmptyLoopBody").map(Boolean::parseBoolean).orElse(false));
                yield step.formatter();
            }
            case AVOID_STAR_IMPORT -> createImportLintFormatter(generatedName, step -> step.getAvoidStarImport().set(true));
            case REDUNDANT_IMPORT -> createImportLintFormatter(generatedName, step -> step.getRemoveRedundantImports().set(true));
            case UNUSED_IMPORT -> createImportLintFormatter(generatedName, step -> step.getRemoveUnusedImports().set(true));
            case ILLEGAL_IMPORT -> {
                CheckstyleModuleSpec module = modulesByPath.get(spec.sourceModulePath());
                List<String> illegalClasses = splitCsv(module == null ? "" : module.property("illegalClasses").orElse(""));
                List<String> illegalPkgs = splitCsv(module == null ? "" : module.property("illegalPkgs").orElse(""));
                yield createImportLintFormatter(generatedName, step -> {
                    step.getRemoveIllegalImports().set(true);
                    step.getIllegalClasses().set(illegalClasses);
                    step.getIllegalPkgs().set(illegalPkgs);
                });
            }
            default -> throw new IllegalStateException("Unsupported simple step kind: " + spec.kind());
        };
    }

    private FileFormatter createImportLintFormatter(String generatedName, java.util.function.Consumer<CheckstyleImportLintStep> customizer) {
        CheckstyleImportLintStep step = getObjects().newInstance(CheckstyleImportLintStep.class, generatedName);
        step.getAvoidStarImport().convention(false);
        step.getRemoveIllegalImports().convention(false);
        step.getIllegalClasses().convention(List.of());
        step.getIllegalPkgs().convention(List.of());
        step.getRemoveRedundantImports().convention(false);
        step.getRemoveUnusedImports().convention(false);
        step.getAnalysisClasspath().from(getAnalysisClasspath());
        step.getAnalysisSourcepath().from(getAnalysisSourcepath());
        customizer.accept(step);
        return step.formatter();
    }

    private static FileFormatter withSuppressionRanges(FileFormatter inner, SuppressionCommentFilterConfig suppressionConfig, Set<String> checkNames) {
        return (fileName, text) -> {
            boolean[] suppressed = suppressionConfig.suppressedLinesForChecks(text, checkNames);
            boolean hasSuppressedLines = false;
            for (boolean suppressedLine : suppressed) {
                if (suppressedLine) {
                    hasSuppressedLines = true;
                    break;
                }
            }
            if (!hasSuppressedLines) {
                return inner.format(fileName, text);
            }

            String candidate = inner.format(fileName, text);
            if (candidate == null || candidate.equals(text)) {
                return candidate;
            }

            List<Integer> changedLines = TextFormattingUtils.findChangedLineNumbers(text, candidate);
            boolean overlapsSuppressedLine = changedLines.stream()
                    .filter(line -> line > 0 && line <= suppressed.length)
                    .anyMatch(line -> suppressed[line - 1]);
            if (!overlapsSuppressedLine) {
                return candidate;
            }

            String[] originalLines = text.split("\n", -1);
            String[] candidateLines = candidate.split("\n", -1);
            if (originalLines.length != candidateLines.length) {
                return text;
            }

            for (int i = 0; i < suppressed.length && i < originalLines.length; i++) {
                if (suppressed[i]) {
                    candidateLines[i] = originalLines[i];
                }
            }

            return String.join("\n", candidateLines);
        };
    }

    private static Set<String> defaultCheckNames(SuppressionCommentFilterConfig suppressionConfig, CheckstyleModuleSpec sourceModule) {
        if (suppressionConfig != null) {
            return suppressionConfig.defaultCheckNames(sourceModule);
        }
        Set<String> names = new LinkedHashSet<>();
        names.add(sourceModule.name());
        sourceModule.id().ifPresent(names::add);
        return names;
    }

    private static Map<String, CheckstyleModuleSpec> indexByPath(CheckstyleModuleSpec root) {
        Map<String, CheckstyleModuleSpec> index = new LinkedHashMap<>();
        indexModules(root, index);
        return index;
    }

    private static void indexModules(CheckstyleModuleSpec module, Map<String, CheckstyleModuleSpec> index) {
        index.put(module.path(), module);
        for (CheckstyleModuleSpec child : module.children()) {
            indexModules(child, index);
        }
    }

    private static List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return java.util.Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(it -> !it.isEmpty())
                .toList();
    }

    @Inject
    public CheckstyleDeterministicStep() {
        getFixMode().convention(FixMode.SAFE);
        getFailOnUnmatchedHints().convention(true);
        getFailOnHintConflicts().convention(true);
    }
}

