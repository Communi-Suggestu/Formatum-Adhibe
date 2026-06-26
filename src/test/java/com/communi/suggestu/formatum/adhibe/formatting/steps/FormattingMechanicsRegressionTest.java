package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FormattingMechanicsRegressionTest {
    private static final String STEP_NAME = "regressionStep";

    @Test
    void alignsRecordClosingParenBeforeImplementsClause() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tpublic record Config(\n"
                + "\t\t\tString namespace,\n"
                + "\t\t\tString registry\n"
                + "\t\t\t) implements NamespacedModuleConfig {}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tpublic record Config(\n"
                + "\t\t\tString namespace,\n"
                + "\t\t\tString registry\n"
                + "\t) implements NamespacedModuleConfig {}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void alignsTryWithResourcesClosingBraceToTryIndent() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tvoid run() throws Exception {\n"
                + "\t\ttry(FileOutputStream stream = new FileOutputStream(\"out.txt\");\n"
                + "\t\t\t\tOutputStreamWriter writer = new OutputStreamWriter(stream)) {\n"
                + "\t\t\twrite(writer);\n"
                + "\t\t\t\t\t}\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid run() throws Exception {\n"
                + "\t\ttry(FileOutputStream stream = new FileOutputStream(\"out.txt\");\n"
                + "\t\t\t\tOutputStreamWriter writer = new OutputStreamWriter(stream)) {\n"
                + "\t\t\twrite(writer);\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void indentsFluentChainLinesUsingWrappingIndentation() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tRegistry<?> registry = context.registryAccess()\n"
                + "\t\t\t.registries()\n"
                + "\t\t\t.filter(entry -> entry.active())\n"
                + "\t\t\t.findFirst()\n"
                + "\t\t\t.orElseThrow();\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tRegistry<?> registry = context.registryAccess()\n"
                + "\t\t\t\t.registries()\n"
                + "\t\t\t\t.filter(entry -> entry.active())\n"
                + "\t\t\t\t.findFirst()\n"
                + "\t\t\t\t.orElseThrow();\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void indentsTryCatchInsideWrappedLambdaBody() {
        CheckstyleIndentationStep step = indentationStep();

        String source = "class Example {\n"
                + "\tvoid run(MultiStateBlockEntity multiStateBlockEntity, List<IStateEntryInfo> before) {\n"
                + "\t\ttry (IBatchMutation batch = multiStateBlockEntity.batch()) {\n"
                + "\t\t\tmultiStateBlockEntity.initializeWith(BlockInformation.AIR);\n"
                + "\t\t\tbefore.stream().forEach(\n"
                + "\t\t\t\t\tiStateEntryInfo -> {\n"
                + "\t\t\t\t\t\ttry {\n"
                + "\t\t\t\tmultiStateBlockEntity.setInAreaTarget(iStateEntryInfo.getBlockInformation(), iStateEntryInfo.getStartPoint());\n"
                + "\t\t\t\t} catch (SpaceOccupiedException e) {\n"
                + "\t\t\t\t//Noop\n"
                + "\t\t\t\t}\n"
                + "\t\t\t\t\t});\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tvoid run(MultiStateBlockEntity multiStateBlockEntity, List<IStateEntryInfo> before) {\n"
                + "\t\ttry (IBatchMutation batch = multiStateBlockEntity.batch()) {\n"
                + "\t\t\tmultiStateBlockEntity.initializeWith(BlockInformation.AIR);\n"
                + "\t\t\tbefore.stream().forEach(\n"
                + "\t\t\t\t\tiStateEntryInfo -> {\n"
                + "\t\t\t\t\t\ttry {\n"
                + "\t\t\t\t\t\t\tmultiStateBlockEntity.setInAreaTarget(iStateEntryInfo.getBlockInformation(), iStateEntryInfo.getStartPoint());\n"
                + "\t\t\t\t\t\t} catch (SpaceOccupiedException e) {\n"
                + "\t\t\t\t\t\t\t//Noop\n"
                + "\t\t\t\t\t\t}\n"
                + "\t\t\t\t\t});\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void addsWhitespaceAfterTryKeyword() {
        CheckstyleWhitespaceAfterStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAfterStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_TRY"));

        String source = "try(FileOutputStream stream = open()) {\n\twork();\n}\n";
        String expected = "try (FileOutputStream stream = open()) {\n\twork();\n}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void addsWhitespaceInsideEmptyRecordBodyBraces() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LCURLY", "RCURLY"));

        String source = "record Config(String namespace) implements NamespacedModuleConfig {}\n";
        String expected = "record Config(String namespace) implements NamespacedModuleConfig { }\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    private static CheckstyleIndentationStep indentationStep() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);
        return step;
    }
}

