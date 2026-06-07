package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class HintRegexStepFactory {
    public List<HintRegexStep> create(HintResolutionResult resolutionResult, boolean failOnConflicts) {
        if (failOnConflicts) {
            validateNoDuplicateIds(resolutionResult.matchedHints());
        }

        List<HintRegexStep> steps = new ArrayList<>();
        for (HintResolution resolution : resolutionResult.matchedHints()) {
            int flags = 0;
            if (resolution.hint().multiline()) {
                flags |= Pattern.MULTILINE;
            }
            if (resolution.hint().dotall()) {
                flags |= Pattern.DOTALL;
            }
            Pattern pattern = Pattern.compile(resolution.hint().find(), flags);
            steps.add(new HintRegexStep(resolution.module().path(), pattern, resolution.hint().replace()));
        }
        return List.copyOf(steps);
    }

    private void validateNoDuplicateIds(List<HintResolution> resolutions) {
        List<String> usedIds = new ArrayList<>();
        for (HintResolution resolution : resolutions) {
            String id = resolution.hint().id();
            if (id == null || id.isBlank()) {
                continue;
            }
            if (usedIds.contains(id)) {
                throw new IllegalStateException("Duplicate hint id detected: " + id);
            }
            usedIds.add(id);
        }
    }
}

