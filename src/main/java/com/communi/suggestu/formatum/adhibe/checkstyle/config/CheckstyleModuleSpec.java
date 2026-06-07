package com.communi.suggestu.formatum.adhibe.checkstyle.config;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CheckstyleModuleSpec(
        String name,
        String path,
        int line,
        int column,
        Map<String, String> properties,
        List<CheckstylePropertySpec> propertySpecs,
        List<CheckstyleMessageSpec> messages,
        List<CheckstyleModuleSpec> children
) {
    public Optional<String> property(String propertyName) {
        return Optional.ofNullable(properties.get(propertyName));
    }

    public Optional<String> id() {
        return property("id");
    }

    public Optional<String> message() {
        return property("message").or(() -> messages.stream().findFirst().map(CheckstyleMessageSpec::value));
    }

    public boolean isLeaf() {
        return children.isEmpty();
    }
}

