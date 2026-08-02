package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.utils.Region;
import com.communi.suggestu.formatum.adhibe.utils.RegionFinder;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

public abstract class CheckstyleIndentationStep extends FormattingStep
{
    @Inject
    public CheckstyleIndentationStep()
    {
    }

    @Input
    public abstract Property<Integer> getBasicOffset();

    @Input
    public abstract Property<Integer> getCaseIndent();

    @Input
    public abstract Property<Integer> getThrowsIndent();

    @Input
    public abstract Property<Integer> getArrayInitIndent();

    @Input
    public abstract Property<Integer> getLineWrappingIndentation();

    @Input
    public abstract Property<RegionFinder.ParseMode> getParseMode();

    @Override
    public FileFormatter formatter()
    {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text)
    {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        int continuationTabs = Math.max(1, getLineWrappingIndentation().getOrElse(8) / Math.max(1, getBasicOffset().getOrElse(4)));
        int inheritanceContinuationIndent = -1;

        final RegionFinder regionFinder = new RegionFinder(getParseMode().getOrElse(RegionFinder.ParseMode.DEFAULT), RegionFinder.DebugMode.Off);
        final Region rootRegion = regionFinder.findRoot(text);

        for (int i = 0; i < lines.size(); i++)
        {
            String line = lines.get(i);
            String trimmed = line.trim();
            if (trimmed.isEmpty())
            {
                continue;
            }

            int effectiveIndent = rootRegion.regionDepthAtStartOfLine(i);

            if (inheritanceContinuationIndent >= 0 && !startsWithClosingBrace(trimmed))
            {
                effectiveIndent = inheritanceContinuationIndent;
            }

            lines.set(i, "\t".repeat(Math.max(0, effectiveIndent)) + trimmed);

            if (inheritanceContinuationIndent >= 0 && !trimmed.endsWith(","))
            {
                inheritanceContinuationIndent = -1;
            }
            if (inheritanceContinuationIndent < 0 && startsWrappedInheritanceList(trimmed))
            {
                inheritanceContinuationIndent = effectiveIndent + continuationTabs;
            }
        }

        return String.join("\n", lines);
    }

    private static boolean startsWithClosingBrace(String trimmed)
    {
        return trimmed.startsWith("}");
    }

    private static boolean startsWrappedInheritanceList(String trimmed)
    {
        return (trimmed.contains(" extends ") || trimmed.contains(" implements ")) && trimmed.endsWith(",");
    }
}
