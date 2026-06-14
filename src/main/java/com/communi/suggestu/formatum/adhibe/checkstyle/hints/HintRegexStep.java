package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

import dev.lukebemish.immaculate.FileFormatter;

import java.util.regex.Pattern;

public final class HintRegexStep {
    private final String modulePath;
    private final Pattern pattern;
    private final String replacement;
    private final String message;

    public HintRegexStep(String modulePath, Pattern pattern, String replacement, String message) {
        this.modulePath = modulePath;
        this.pattern = pattern;
        this.replacement = replacement;
        this.message = message;
    }

    public String modulePath() {
        return modulePath;
    }

    public String message() {
        return message;
    }

    public FileFormatter formatter() {
        return (fileName, text) -> pattern.matcher(text).replaceAll(replacement);
    }
}

