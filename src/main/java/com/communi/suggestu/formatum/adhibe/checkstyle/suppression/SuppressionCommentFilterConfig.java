package com.communi.suggestu.formatum.adhibe.checkstyle.suppression;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SuppressionCommentFilterConfig {
    private final Pattern offPattern;
    private final Pattern onPattern;
    private final String checkFormat;

    private SuppressionCommentFilterConfig(Pattern offPattern, Pattern onPattern, String checkFormat) {
        this.offPattern = offPattern;
        this.onPattern = onPattern;
        this.checkFormat = checkFormat;
    }

    public static Optional<SuppressionCommentFilterConfig> fromRoot(CheckstyleModuleSpec root) {
        Optional<CheckstyleModuleSpec> treeWalker = root.children().stream()
                .filter(module -> "TreeWalker".equals(module.name()))
                .findFirst();
        if (treeWalker.isEmpty()) {
            return Optional.empty();
        }

        Optional<CheckstyleModuleSpec> filter = treeWalker.get().children().stream()
                .filter(module -> "SuppressionCommentFilter".equals(module.name()))
                .findFirst();
        if (filter.isEmpty()) {
            return Optional.empty();
        }

        String off = filter.get().property("offCommentFormat").orElse("CHECKSTYLE:OFF");
        String on = filter.get().property("onCommentFormat").orElse("CHECKSTYLE:ON");
        String check = filter.get().property("checkFormat").orElse(".*");
        return Optional.of(new SuppressionCommentFilterConfig(Pattern.compile(off), Pattern.compile(on), check));
    }

    public boolean[] suppressedLinesForChecks(String text, Set<String> checkNames) {
        String[] lines = text.split("\n", -1);
        boolean[] suppressed = new boolean[lines.length];
        Map<String, Integer> activeExpressions = new HashMap<>();

        for (int i = 0; i < lines.length; i++) {
            List<TagEvent> events = parseEvents(lines[i]);
            for (TagEvent event : events) {
                String expression = expandCheckFormat(event.matchResult());
                if (event.offTag()) {
                    activeExpressions.merge(expression, 1, Integer::sum);
                } else {
                    int count = activeExpressions.getOrDefault(expression, 0);
                    if (count <= 1) {
                        activeExpressions.remove(expression);
                    } else {
                        activeExpressions.put(expression, count - 1);
                    }
                }
            }

            suppressed[i] = isSuppressedForChecks(activeExpressions.keySet(), checkNames);
        }

        return suppressed;
    }

    private List<TagEvent> parseEvents(String line) {
        List<TagEvent> events = new ArrayList<>();

        Matcher offMatcher = offPattern.matcher(line);
        while (offMatcher.find()) {
            events.add(new TagEvent(true, offMatcher.start(), offMatcher.toMatchResult()));
        }

        Matcher onMatcher = onPattern.matcher(line);
        while (onMatcher.find()) {
            events.add(new TagEvent(false, onMatcher.start(), onMatcher.toMatchResult()));
        }

        events.sort(Comparator.comparingInt(TagEvent::offset));
        return events;
    }

    private String expandCheckFormat(MatchResult matcher) {
        String expanded = checkFormat;
        for (int i = 1; i <= matcher.groupCount(); i++) {
            String replacement = matcher.group(i) == null ? "" : Matcher.quoteReplacement(matcher.group(i));
            expanded = expanded.replace("$" + i, replacement);
        }
        return expanded;
    }

    private static boolean isSuppressedForChecks(Set<String> expressions, Set<String> checkNames) {
        if (expressions.isEmpty()) {
            return false;
        }

        for (String expression : expressions) {
            Pattern checkPattern;
            try {
                checkPattern = Pattern.compile(expression);
            } catch (Exception ex) {
                continue;
            }

            for (String checkName : checkNames) {
                if (checkPattern.matcher(checkName).matches()) {
                    return true;
                }
            }
        }

        return false;
    }

    public Set<String> defaultCheckNames(CheckstyleModuleSpec module) {
        Set<String> names = new LinkedHashSet<>();
        names.add(module.name());
        module.id().ifPresent(names::add);
        if ("UnusedImports".equals(module.name())) {
            names.add("UnusedImport");
        }
        return names;
    }

    private record TagEvent(boolean offTag, int offset, MatchResult matchResult) {
    }
}


