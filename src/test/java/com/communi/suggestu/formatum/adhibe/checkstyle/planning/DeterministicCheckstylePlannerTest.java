package com.communi.suggestu.formatum.adhibe.checkstyle.planning;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleConfigParser;
import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DeterministicCheckstylePlannerTest {
    @Test
    void plansExpectedDeterministicStepsFromProvidedConfiguration() {
        CheckstyleModuleSpec root = new CheckstyleConfigParser().parse(Path.of("libs/checkstyle/checkstyle.xml"));

        PlanningResult result = new DeterministicCheckstylePlanner().plan(root, "checkstyle");

        assertEquals(18, result.steps().size());
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.ORDER_IMPORTS));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.CONVERT_LEADING_SPACES_TO_TABS));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.INSERT_BLANK_LINE_BEFORE_INDENTED_BLOCK));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.LEFT_CURLY));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.RIGHT_CURLY));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.NEED_BRACES));
    }
}


