package com.communi.suggestu.formatum.adhibe.plugin;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleConfigParser;
import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.DeterministicCheckstylePlanner;
import com.communi.suggestu.formatum.adhibe.checkstyle.planning.GeneratedImmaculateStepSpec;
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
import com.communi.suggestu.formatum.adhibe.formatting.steps.TrimTrailingWhitespaceStep;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.model.ObjectFactory;
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

    @Override
    public FileFormatter formatter() {
        CheckstyleModuleSpec root = new CheckstyleConfigParser().parse(getCheckstyleConfig().get().getAsFile().toPath());
        String stepNamePrefix = getStepNamePrefix().getOrElse("checkstyle");
        var planningResult = new DeterministicCheckstylePlanner().plan(root, stepNamePrefix);

        List<FileFormatter> formatters = new ArrayList<>();
        int stepIndex = 0;
        for (GeneratedImmaculateStepSpec spec : planningResult.steps()) {
            formatters.add(createFormatter(spec, stepIndex++));
        }

        if (getHintsFile().isPresent() && Files.exists(getHintsFile().get().getAsFile().toPath())) {
            var hintsFile = new CheckstyleHintsParser().parse(getHintsFile().get().getAsFile().toPath());
            var resolved = new HintResolver().resolve(root, hintsFile, getFixMode().get());
            if (getFailOnUnmatchedHints().get() && !resolved.unmatchedHints().isEmpty()) {
                throw new IllegalStateException("Unmatched hints found: " + resolved.unmatchedHints().size());
            }
            List<HintRegexStep> hintSteps = new HintRegexStepFactory().create(resolved, getFailOnHintConflicts().get());
            for (HintRegexStep step : hintSteps) {
                formatters.add(step.formatter());
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

