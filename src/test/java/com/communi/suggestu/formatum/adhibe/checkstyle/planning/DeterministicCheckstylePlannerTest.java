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

        assertEquals(31, result.steps().size());
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.ORDER_IMPORTS));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.CONVERT_LEADING_SPACES_TO_TABS));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.INSERT_BLANK_LINE_BEFORE_INDENTED_BLOCK));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.LEFT_CURLY));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.RIGHT_CURLY));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.NEED_BRACES));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.EMPTY_LINE_SEPARATOR));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.OPERATOR_WRAP));
        assertTrue(result.steps().stream().filter(step -> step.kind() == GeneratedStepKind.SEPARATOR_WRAP).count() == 2);
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.INDENTATION));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.PAREN_PAD));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.NO_WHITESPACE_BEFORE));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.NO_WHITESPACE_AFTER));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.WHITESPACE_AFTER));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.WHITESPACE_AROUND));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.SINGLE_SPACE_SEPARATOR));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.GENERIC_WHITESPACE));
        assertTrue(result.steps().stream().anyMatch(step -> step.kind() == GeneratedStepKind.COMMENTS_INDENTATION));
    }
}


