package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StepFormatterTest {
    private static final String STEP_NAME = "testStep";

    @Test
    void trimsTrailingWhitespace() {
        TrimTrailingWhitespaceStep step = ProjectBuilder.builder().build().getObjects().newInstance(TrimTrailingWhitespaceStep.class, STEP_NAME);
        String formatted = step.formatter().format("Example.java", "a  \n\tb \n");
        assertEquals("a\n\tb\n", formatted);
    }

    @Test
    void removesBraceAdjacentBlankLines() {
        RemoveBlankLineAfterOpeningBraceStep after = ProjectBuilder.builder().build().getObjects().newInstance(RemoveBlankLineAfterOpeningBraceStep.class, STEP_NAME);
        RemoveBlankLineBeforeClosingBraceStep before = ProjectBuilder.builder().build().getObjects().newInstance(RemoveBlankLineBeforeClosingBraceStep.class, STEP_NAME);

        assertEquals("if (true) {\n\tcall();\n}", after.formatter().format("Example.java", "if (true) {\n\n\tcall();\n}"));
        assertEquals("if (true) {\n\tcall();\n}", before.formatter().format("Example.java", "if (true) {\n\tcall();\n\n}"));
    }

    @Test
    void insertsBlankLinesAroundIndentedBlocks() {
        InsertBlankLineBeforeIndentedBlockStep before = ProjectBuilder.builder().build().getObjects().newInstance(InsertBlankLineBeforeIndentedBlockStep.class, STEP_NAME);
        InsertBlankLineAfterIndentedBlockStep after = ProjectBuilder.builder().build().getObjects().newInstance(InsertBlankLineAfterIndentedBlockStep.class, STEP_NAME);

        String source = "\tint value = 1;\n\tif (value > 0) {\n\t\tvalue++;\n\t}\n\tvalue--;";
        String expectedBefore = "\tint value = 1;\n\n\tif (value > 0) {\n\t\tvalue++;\n\t}\n\tvalue--;";
        String expectedAfter = "\tint value = 1;\n\tif (value > 0) {\n\t\tvalue++;\n\t}\n\n\tvalue--;";

        assertEquals(expectedBefore, before.formatter().format("Example.java", source));
        assertEquals(expectedAfter, after.formatter().format("Example.java", source));
    }

    @Test
    void convertsLeadingWhitespaceToTabs() {
        LeadingWhitespaceToTabsStep step = ProjectBuilder.builder().build().getObjects().newInstance(LeadingWhitespaceToTabsStep.class, STEP_NAME);
        step.getTabWidth().set(4);

        String formatted = step.formatter().format("Example.java", "    value();\n\t    mixed();\n    * comment\n");
        assertEquals("\tvalue();\n\t\tmixed();\n    * comment\n", formatted);
    }

    @Test
    void ordersStaticAndRegularImports() {
        CheckstyleImportOrderStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportOrderStep.class, STEP_NAME);
        step.getGroups().set(java.util.List.of("java", "javax", "*", "net.fabricmc"));
        step.getSeparated().set(true);
        step.getOption().set("top");
        step.getSortStaticImportsAlphabetically().set(true);

        String source = "package test;\n\nimport test.Helper;\nimport javax.swing.JButton;\nimport static java.util.Collections.emptyList;\nimport java.util.Map;\nimport net.fabricmc.api.EnvType;\n\nclass Example {}\n";
        String expected = "package test;\n\nimport static java.util.Collections.emptyList;\n\nimport java.util.Map;\n\nimport javax.swing.JButton;\n\nimport test.Helper;\n\nimport net.fabricmc.api.EnvType;\n\nclass Example {}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesImportLintRules() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(true);
        step.getRemoveIllegalImports().set(true);
        step.getIllegalClasses().set(java.util.List.of("org.jetbrains.annotations.Nullable"));
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(true);
        step.getRemoveUnusedImports().set(true);

        String source = "package test;\n\n"
                + "import java.util.*;\n"
                + "import java.util.Map;\n"
                + "import java.lang.String;\n"
                + "import org.jetbrains.annotations.Nullable;\n"
                + "import test.Helper;\n\n"
                + "class Example {\n"
                + "\tMap<String, String> values = java.util.Collections.emptyMap();\n"
                + "}\n";

        String expected = "package test;\n\n"
                + "import java.util.Map;\n\n"
                + "class Example {\n"
                + "\tMap<String, String> values = java.util.Collections.emptyMap();\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesLeftCurlyPolicy() {
        CheckstyleLeftCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        step.getOption().set("eol");
        step.getIgnoreEnums().set(true);

        String source = "class Example\n{\n}\n";
        assertEquals("class Example {\n}\n", step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesRightCurlySamePolicy() {
        CheckstyleRightCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        step.getOption().set("same");

        String source = "if (flag) {\n\tcall();\n}\nelse {\n\tother();\n}\nint x = 1;\n";
        String expected = "if (flag) {\n\tcall();\n} else {\n\tother();\n}\nint x = 1;\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void insertsNeedBracesForConfiguredTokens() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF", "LITERAL_FOR", "LITERAL_WHILE"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);

        String source = "if (ok)\n\tcall();\nfor (int i = 0; i < 1; i++)\n\tcall();\n";
        String expected = "if (ok) {\n\tcall();\n}\nfor (int i = 0; i < 1; i++) {\n\tcall();\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void allowsSingleLineNeedBracesWhenConfigured() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF"));
        step.getAllowSingleLineStatement().set(true);
        step.getAllowEmptyLoopBody().set(false);

        String source = "if (ok) return;\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }
}



