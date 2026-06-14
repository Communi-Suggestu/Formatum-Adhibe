package com.communi.suggestu.formatum.adhibe.plugin;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleConfigParser;
import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.DeterministicCheckstylePlanner;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.GeneratedImmaculateStepSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.GeneratedStepKind;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.ImportOrderGeneratedStepSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.LeadingWhitespaceToTabsGeneratedStepSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.SimpleGeneratedStepSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.CheckstyleHintsParser;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.HintRegexStep;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.HintRegexStepFactory;
import com.communi.suggestu.formatum.adhibe.checkstyle.hints.HintResolver;
import com.communi.suggestu.formatum.adhibe.formatting.steps.CheckstyleImportOrderStep;
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
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import javax.inject.Inject;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

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

    @Inject
    protected abstract ObjectFactory getObjects();

    @Inject
    protected abstract Problems getProblems();

    @Override
    public FileFormatter formatter() {
        CheckstyleModuleSpec root = new CheckstyleConfigParser().parse(getCheckstyleConfig().get().getAsFile().toPath());
        String stepNamePrefix = getStepNamePrefix().getOrElse("checkstyle");
        var planningResult = new DeterministicCheckstylePlanner().plan(root, stepNamePrefix);

        ProblemReporter reporter = getProblems().getReporter();

        List<FileFormatter> formatters = new ArrayList<>();
        int stepIndex = 0;
        for (GeneratedImmaculateStepSpec spec : planningResult.steps()) {
            FileFormatter inner = createFormatter(spec, stepIndex++);
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
            case CONVERT_LEADING_SPACES_TO_TABS -> ID_LEADING_SPACES;
            case ORDER_IMPORTS -> ID_IMPORT_ORDER;
        };
    }

    private FileFormatter createFormatter(GeneratedImmaculateStepSpec spec, int stepIndex) {
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
            case SimpleGeneratedStepSpec simple -> createSimpleFormatter(simple, generatedName);
        };
    }

    private FileFormatter createSimpleFormatter(SimpleGeneratedStepSpec spec, String generatedName) {
        return switch (spec.kind()) {
            case ENSURE_TRAILING_NEWLINE -> getObjects().newInstance(EnsureTrailingNewlineStep.class, generatedName).formatter();
            case TRIM_TRAILING_WHITESPACE -> getObjects().newInstance(TrimTrailingWhitespaceStep.class, generatedName).formatter();
            case COLLAPSE_CONSECUTIVE_BLANK_LINES -> getObjects().newInstance(CollapseConsecutiveBlankLinesStep.class, generatedName).formatter();
            case REMOVE_BLANK_LINE_AFTER_OPENING_BRACE -> getObjects().newInstance(RemoveBlankLineAfterOpeningBraceStep.class, generatedName).formatter();
            case REMOVE_BLANK_LINE_BEFORE_CLOSING_BRACE -> getObjects().newInstance(RemoveBlankLineBeforeClosingBraceStep.class, generatedName).formatter();
            case INSERT_BLANK_LINE_BEFORE_INDENTED_BLOCK -> getObjects().newInstance(InsertBlankLineBeforeIndentedBlockStep.class, generatedName).formatter();
            case INSERT_BLANK_LINE_AFTER_INDENTED_BLOCK -> getObjects().newInstance(InsertBlankLineAfterIndentedBlockStep.class, generatedName).formatter();
            default -> throw new IllegalStateException("Unsupported simple step kind: " + spec.kind());
        };
    }

    @Inject
    public CheckstyleDeterministicStep() {
        getFixMode().convention(FixMode.SAFE);
        getFailOnUnmatchedHints().convention(true);
        getFailOnHintConflicts().convention(true);
    }
}

