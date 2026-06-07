package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

import dev.lukebemish.immaculate.FileFormatter;

import java.util.regex.Pattern;

public final class HintRegexStep {
    private final String modulePath;
    private final Pattern pattern;
    private final String replacement;

    public HintRegexStep(String modulePath, Pattern pattern, String replacement) {
        this.modulePath = modulePath;
        this.pattern = pattern;
        this.replacement = replacement;
    }

    public String modulePath() {
        return modulePath;
    }

    public FileFormatter formatter() {
        return (fileName, text) -> pattern.matcher(text).replaceAll(replacement);
    }
}

