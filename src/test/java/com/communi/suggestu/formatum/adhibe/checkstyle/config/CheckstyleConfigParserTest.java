package com.communi.suggestu.formatum.adhibe.checkstyle.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckstyleConfigParserTest {
    @Test
    void parsesConfiguredCheckstyleFile() {
        CheckstyleConfigParser parser = new CheckstyleConfigParser();
        CheckstyleModuleSpec root = parser.parse(Path.of("libs/checkstyle/checkstyle.xml"));

        assertEquals("Checker", root.name());
        assertTrue(root.property("charset").isPresent());
        assertFalse(root.children().isEmpty());

        boolean hasTreeWalker = root.children().stream().anyMatch(module -> "TreeWalker".equals(module.name()));
        assertTrue(hasTreeWalker);
    }
}

