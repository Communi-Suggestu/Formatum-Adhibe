package com.communi.suggestu.formatum.adhibe.checkstyle.classification;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;

import java.util.List;

public final class DiagnosticsReporter {
    public String reportUnsupported(List<CheckstyleModuleSpec> unsupportedModules) {
        if (unsupportedModules.isEmpty()) {
            return "No unsupported modules found.\n";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("Unsupported modules found: ").append(unsupportedModules.size()).append("\n");
        for (CheckstyleModuleSpec module : unsupportedModules) {
            builder.append("- ")
                    .append(module.path())
                    .append(" @ ")
                    .append(module.line())
                    .append(":")
                    .append(module.column())
                    .append(" -> ")
                    .append(module.name())
                    .append("\n");
        }
        return builder.toString();
    }
}

