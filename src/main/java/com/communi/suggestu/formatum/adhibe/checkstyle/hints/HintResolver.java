package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;

import java.util.ArrayList;
import java.util.List;

public final class HintResolver {
    public HintResolutionResult resolve(CheckstyleModuleSpec root, CheckstyleHintsFile hintsFile, FixMode activeMode) {
        List<CheckstyleModuleSpec> modules = new ArrayList<>();
        flatten(root, modules);

        List<HintResolution> matched = new ArrayList<>();
        List<CheckstyleHint> unmatched = new ArrayList<>();

        for (CheckstyleHint hint : hintsFile.hints()) {
            if (hint.mode().ordinal() > activeMode.ordinal()) {
                continue;
            }

            boolean found = false;
            for (CheckstyleModuleSpec module : modules) {
                if (matches(hint, module)) {
                    matched.add(new HintResolution(hint, module));
                    found = true;
                }
            }
            if (!found) {
                unmatched.add(hint);
            }
        }

        return new HintResolutionResult(List.copyOf(matched), List.copyOf(unmatched));
    }

    private static boolean matches(CheckstyleHint hint, CheckstyleModuleSpec module) {
        if (hint.modulePath() != null && !hint.modulePath().equals(module.path())) {
            return false;
        }
        if (hint.moduleName() != null && !hint.moduleName().equals(module.name())) {
            return false;
        }
        if (hint.messageContains() != null) {
            String message = module.message().orElse("");
            return message.contains(hint.messageContains());
        }
        return hint.modulePath() != null || hint.moduleName() != null;
    }

    private void flatten(CheckstyleModuleSpec current, List<CheckstyleModuleSpec> modules) {
        modules.add(current);
        for (CheckstyleModuleSpec child : current.children()) {
            flatten(child, modules);
        }
    }
}

