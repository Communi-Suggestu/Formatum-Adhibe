package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

public record CheckstyleHint(
        String id,
        String modulePath,
        String moduleName,
        String messageContains,
        String find,
        String replace,
        boolean multiline,
        boolean dotall,
        FixMode mode
) {
}


