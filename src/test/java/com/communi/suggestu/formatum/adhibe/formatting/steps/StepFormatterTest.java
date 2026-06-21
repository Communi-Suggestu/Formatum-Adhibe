package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
        step.getIllegalImportsReason().set("We use JSPECIFY in this project for nullable annotations.");
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
                + "}\n\n"
                + "class Helper {}\n";

        String expected = "package test;\n\n"
                + "import java.util.Map;\n"
                + "import java.lang.String;\n"
                + "// ILLEGAL IMPORT: -> import org.jetbrains.annotations.Nullable;   ->   We use JSPECIFY in this project for nullable annotations.\n\n"
                + "class Example {\n"
                + "\tMap<String, String> values = java.util.Collections.emptyMap();\n"
                + "}\n\n"
                + "class Helper {}\n";

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
    void enforcesLeftCurlyPolicyAcrossBlankLineBeforeBrace() {
        CheckstyleLeftCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        step.getOption().set("eol");
        step.getIgnoreEnums().set(true);

        String source = "void run()\n\n{\n\tcall();\n}\n";
        assertEquals("void run() {\n\tcall();\n}\n", step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesLeftCurlyPolicyBeforeInlineComment() {
        CheckstyleLeftCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        step.getOption().set("eol");
        step.getIgnoreEnums().set(true);

        String source = "catch (Exception ignored) // should never happen;\n\n{\n}\n";
        String expected = "catch (Exception ignored) { // should never happen;\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
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
    void appliesEmptyLineSeparatorRules() {
        CheckstyleEmptyLineSeparatorStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleEmptyLineSeparatorStep.class, STEP_NAME);
        step.getAllowNoEmptyLineBetweenFields().set(true);
        step.getAllowMultipleEmptyLines().set(false);
        step.getTokens().set(java.util.List.of("PACKAGE_DEF", "IMPORT", "CLASS_DEF"));

        String source = "package test;\nimport java.util.List;\n\n\npublic class Example {}\n";
        String expected = "package test;\n\nimport java.util.List;\n\npublic class Example {}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesOperatorWrapNlPolicy() {
        CheckstyleOperatorWrapStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleOperatorWrapStep.class, STEP_NAME);
        step.getOption().set("nl");
        step.getTokens().set(java.util.List.of("LAND", "LOR"));

        String source = "if (a &&\n\tb) {\n\tcall();\n}\n";
        String expected = "if (a\n\t&& b) {\n\tcall();\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesSeparatorWrapEolPolicy() {
        CheckstyleSeparatorWrapStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleSeparatorWrapStep.class, STEP_NAME);
        step.getOption().set("eol");
        step.getTokens().set(java.util.List.of("COMMA"));

        String source = "values(\n\ta\n\t, b\n);\n";
        String expected = "values(\n\ta,\n\tb\n);\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesIndentationRules() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);

        String source = "class Example {\n    public void run() {\n        if (a\n                && b) {\n            call();\n        }\n    }\n}\n";
        String expected = "class Example {\n\tpublic void run() {\n\t\tif (a\n\t\t\t\t&& b) {\n\t\t\tcall();\n\t\t}\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void keepsMethodAndIfClosingBracesAtStructuralIndent() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);

        String source = "class Example {\n\tvoid run(\n\t\t\tfinal int a,\n\t\t\tfinal int b) {\n\t\tif (a\n\t\t\t\t&& b) {\n\t\t\tcall();\n\t\t\t\t}\n\t\t\t\t}\n}\n";
        String expected = "class Example {\n\tvoid run(\n\t\t\tfinal int a,\n\t\t\tfinal int b) {\n\t\tif (a\n\t\t\t\t&& b) {\n\t\t\tcall();\n\t\t}\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesIndentationToAabbStyleWrappedDeclarationsAndAnonymousClasses() {
        CheckstyleLeftCurlyStep leftCurly = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        leftCurly.getOption().set("eol");
        leftCurly.getIgnoreEnums().set(true);

        CheckstyleIndentationStep indentation = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        indentation.getBasicOffset().set(4);
        indentation.getCaseIndent().set(0);
        indentation.getThrowsIndent().set(4);
        indentation.getArrayInitIndent().set(4);
        indentation.getLineWrappingIndentation().set(8);

        String source = "class Example {\n"
                + "\tvoid run(\n"
                + "\t final int a,\n"
                + "\t final int b)\n\n"
                + "\t{\n"
                + "\t\taccessor.call(\n"
                + "\t\t child(),\n"
                + "\t\t new Consumer<>()\n\n"
                + "\t\t {\n"
                + "\t\t\t@Override\n"
                + "\t\t\tpublic void accept(final String value)\n\n"
                + "\t\t\t{\n"
                + "\t\t\t\tfinal Optional<String> mapped = Optional.of(value).flatMap(\n"
                + "\t\t\t\t d -> Optional.of(d)\n"
                + "\t\t\t);\n"
                + "\t\t\t}\n"
                + "\t\t }\n\n"
                + "\t\t);\n"
                + "\t}\n"
                + "}\n";

        String afterLeftCurly = leftCurly.formatter().format("Example.java", source);
        String formatted = indentation.formatter().format("Example.java", afterLeftCurly);

        assertTrue(formatted.contains("\tvoid run(\n\t\t\tfinal int a,\n\t\t\tfinal int b) {"), formatted);
        assertTrue(formatted.contains("\t\taccessor.call(\n\t\t\t\tchild(),\n\t\t\tnew Consumer<>() {"), formatted);
        assertTrue(formatted.contains("\t\t\t\tpublic void accept(final String value) {"), formatted);
        assertTrue(formatted.contains("\t\t\t\tfinal Optional<String> mapped = Optional.of(value).flatMap("), formatted);
        assertTrue(formatted.contains("\t\t\t\t\t\td -> Optional.of(d)"), formatted);
        assertTrue(formatted.contains("\t\t);"), formatted);
    }

    @Test
    void keepsAnonymousConsumerAcceptClosingBracesAlignedWithinWrappedInvocation() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);

        String source = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\taccessor.call(\n"
                + "\t\t\tIPositionMutator.xyz(),\n"
                + "\t\t\tnew Consumer<>() {\n"
                + "\t\t\t\t@Override\n"
                + "\t\t\t\tpublic void accept(final String value) {\n"
                + "\t\t\t\t\tif (value.isEmpty()) {\n"
                + "\t\t\t\t\t\treturn;\n"
                + "\t\t\t\t\t}\n"
                + "\t\t\t\t\tcall();\n"
                + "\t\t\t\t}\n"
                + "\t\t\t}\n"
                + "\t\t);\n"
                + "\t}\n"
                + "}\n";

        String formatted = step.formatter().format("Example.java", source);

        assertTrue(formatted.contains("\t\t\t\t\tif (value.isEmpty()) {\n\t\t\t\t\t\treturn;\n\t\t\t\t\t}"), formatted);
        assertTrue(formatted.contains("\t\t\t\t}\n\t\t\t}"), formatted);
    }

    @Test
    void alignsMethodParameterClosingParenWithDeclarationIndent() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);

        String source = "class Example {\n"
                + "\tList<String> get(\n"
                + "\t\t\tfinal String first,\n"
                + "\t\t\tfinal String second\n"
                + "\t\t\t) {\n"
                + "\t\treturn List.of(first, second);\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tList<String> get(\n"
                + "\t\t\tfinal String first,\n"
                + "\t\t\tfinal String second\n"
                + "\t) {\n"
                + "\t\treturn List.of(first, second);\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void indentsWrappedTernaryBranchesWithContinuationIndent() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);

        String source = "class Example {\n"
                + "\tdouble getValue(Direction direction, AABB bb) {\n"
                + "\t\treturn direction.getAxisDirection() == Direction.AxisDirection.POSITIVE\n"
                + "\t\t? bb.max(direction.getAxis())\n"
                + "\t\t: bb.min(direction.getAxis());\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tdouble getValue(Direction direction, AABB bb) {\n"
                + "\t\treturn direction.getAxisDirection() == Direction.AxisDirection.POSITIVE\n"
                + "\t\t\t\t? bb.max(direction.getAxis())\n"
                + "\t\t\t\t: bb.min(direction.getAxis());\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void keepsWrappedLambdaBodyIndentedRelativeToLambdaBrace() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);

        String source = "class Example {\n"
                + "\tint run() {\n"
                + "\t\treturn IntStream.range(0, 10)\n"
                + "\t\t\t\t.mapToInt(value -> {\n"
                + "\t\t\t\t\t\tint mapped = value + 1;\n"
                + "\n"
                + "\t\t\t\t\t\tif (mapped > 5) {\n"
                + "\t\t\t\t\t\treturn mapped;\n"
                + "\t\t\t\t\t\t}\n"
                + "\n"
                + "\t\t\t\t\t\treturn 0;\n"
                + "\t\t\t\t}\n"
                + "\t\t\t\t)\n"
                + "\t\t\t\t.sum();\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tint run() {\n"
                + "\t\treturn IntStream.range(0, 10)\n"
                + "\t\t\t\t.mapToInt(value -> {\n"
                + "\t\t\t\t\tint mapped = value + 1;\n"
                + "\n"
                + "\t\t\t\t\tif (mapped > 5) {\n"
                + "\t\t\t\t\t\treturn mapped;\n"
                + "\t\t\t\t\t}\n"
                + "\n"
                + "\t\t\t\t\treturn 0;\n"
                + "\t\t\t\t}\n"
                + "\t\t\t\t)\n"
                + "\t\t\t\t.sum();\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesParenPadNoSpacePolicy() {
        CheckstyleParenPadStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleParenPadStep.class, STEP_NAME);
        step.getOption().set("nospace");

        String source = "if ( value ) {\n\tcall( arg );\n}\n";
        String expected = "if (value) {\n\tcall(arg);\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesNoWhitespaceBeforeRules() {
        CheckstyleNoWhitespaceBeforeStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNoWhitespaceBeforeStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("COMMA", "SEMI", "DOT"));

        String source = "value . map ( a , b ) ;\n";
        String expected = "value. map ( a, b);\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void noWhitespaceBeforeDoesNotStripIndentForLeadingDotOrStandaloneClosingBrace() {
        CheckstyleNoWhitespaceBeforeStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNoWhitespaceBeforeStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("DOT", "RCURLY"));

        String source = "class Example {\n\tvoid run() {\n\t\tbuilder\n\t\t\t.method()\n\t\t\t.chain();\n\t}\n}\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesNoWhitespaceAfterRules() {
        CheckstyleNoWhitespaceAfterStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNoWhitespaceAfterStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("DOT", "AT"));

        String source = "@ Deprecated\nvalue. map();\n";
        String expected = "@Deprecated\nvalue.map();\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesWhitespaceAfterRules() {
        CheckstyleWhitespaceAfterStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAfterStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("COMMA"));

        String source = "call(a,b,c);\n";
        String expected = "call(a, b, c);\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesWhitespaceAroundRules() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("ASSIGN", "EQUAL", "LITERAL_IF"));

        String source = "if(flag==true){\n\tint value=1;\n}\n";
        String expected = "if(flag == true){\n\tint value = 1;\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void doesNotSplitCompoundAssignmentOperators() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("ASSIGN", "PLUS_ASSIGN", "MINUS_ASSIGN"));

        String source = "int x = 0;\nx += 1;\ny -= 2;\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void operatorWrapDoesNotSplitLambdaArrow() {
        CheckstyleOperatorWrapStep stepNl = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleOperatorWrapStep.class, STEP_NAME);
        stepNl.getOption().set("nl");
        stepNl.getTokens().set(java.util.List.of("LAMBDA", "MINUS", "GT"));

        CheckstyleOperatorWrapStep stepEol = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleOperatorWrapStep.class, STEP_NAME);
        stepEol.getOption().set("eol");
        stepEol.getTokens().set(java.util.List.of("LAMBDA", "MINUS", "GT"));

        String source = "list.stream()\n\t.map(v -> v + 1)\n\t.forEach(v -> call(v));\n";
        String formattedNl = stepNl.formatter().format("Example.java", source);
        String formattedEol = stepEol.formatter().format("Example.java", source);

        assertTrue(formattedNl.contains("->"), formattedNl);
        assertTrue(formattedEol.contains("->"), formattedEol);
        assertFalse(formattedNl.contains("- >"), formattedNl);
        assertFalse(formattedEol.contains("- >"), formattedEol);
        assertFalse(formattedNl.contains("-\n"), formattedNl);
    }

    @Test
    void operatorWrapDoesNotSplitJavadocHtmlTags() {
        CheckstyleOperatorWrapStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleOperatorWrapStep.class, STEP_NAME);
        step.getOption().set("nl");
        step.getTokens().set(java.util.List.of("LT", "GT"));

        String source = "/**\n"
                + " * Represents a plugin for ChiselsAndBits.\n"
                + " * <p>\n"
                + " * Plugins have callbacks that can be invoked by chisels and bits.\n"
                + " * See their documentation for more information.\n"
                + " * </p>\n"
                + " */\n";

        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void separatorWrapDoesNotMoveDotFromTrailingComment() {
        CheckstyleSeparatorWrapStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleSeparatorWrapStep.class, STEP_NAME);
        step.getOption().set("nl");
        step.getTokens().set(java.util.List.of("DOT"));

        String source = "int value = 0; // keep this sentence.\nvalue++;\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesSingleSpaceSeparatorRules() {
        CheckstyleSingleSpaceSeparatorStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleSingleSpaceSeparatorStep.class, STEP_NAME);

        String source = "class  Example {\n\tint   value =  1;\n}\n";
        String expected = "class Example {\n\tint value = 1;\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesGenericWhitespaceRules() {
        CheckstyleGenericWhitespaceStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleGenericWhitespaceStep.class, STEP_NAME);

        String source = "Map< String ,Integer > values;\n";
        String expected = "Map<String, Integer> values;\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesCommentsIndentationRules() {
        CheckstyleCommentsIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleCommentsIndentationStep.class, STEP_NAME);

        String source = "class Example {\n\tvoid run() {\n// comment\n\t\tcall();\n\t}\n}\n";
        String expected = "class Example {\n\tvoid run() {\n\t// comment\n\t\tcall();\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void appliesCommentsIndentationRulesToMultilineJavadoc() {
        CheckstyleCommentsIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleCommentsIndentationStep.class, STEP_NAME);

        String source = "interface Example {\n\t/**\n\t* Line one.\n\t* Line two.\n\t*/\n\tvoid run();\n}\n";
        String expected = "interface Example {\n\t/**\n\t * Line one.\n\t * Line two.\n\t */\n\tvoid run();\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void keepsAlreadyCorrectMultilineJavadocIndentation() {
        CheckstyleCommentsIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleCommentsIndentationStep.class, STEP_NAME);

        String source = "interface Example {\n\t/**\n\t * Line one.\n\t * @param value value.\n\t */\n\tvoid run(int value);\n}\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void parenPadDoesNotCollapseMultilineMethodSignatures() {
        CheckstyleParenPadStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleParenPadStep.class, STEP_NAME);
        step.getOption().set("nospace");

        String source = "void test(\n\tfinal int a,\n\tfinal int b\n) {\n}\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundDoesNotCollapseTabIndentation() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("ASSIGN"));

        String source = "class Example {\n\t\tint value=1;\n}\n";
        String expected = "class Example {\n\t\tint value = 1;\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundDoesNotInsertSpacesInsideGenericTypeArguments() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("ASSIGN", "LT", "GT"));

        String source = "List<String> values=List.of();\n";
        String expected = "List<String> values = List.of();\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundNormalizesComparisonOperatorsInClassicForHeaders() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("ASSIGN", "LT", "GT", "LE", "GE"));

        String source = "for (int i = getInventorySize() - 1; i>= 0; i--) {\n"
                + "}\n"
                + "for (int i = 0; i <getInventorySize(); i++) {\n"
                + "}\n";

        String expected = "for (int i = getInventorySize() - 1; i >= 0; i--) {\n"
                + "}\n"
                + "for (int i = 0; i < getInventorySize(); i++) {\n"
                + "}\n";

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

    @Test
    void doesNotCorruptMultilineIfConditionWhenApplyingNeedBraces() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);

        String source = "if (inAreaTarget.x() < 0 ||\n"
                + "\tinAreaTarget.y() < 0 ||\n"
                + "\tinAreaTarget.z() < 0 ||\n"
                + "\tinAreaTarget.x() >= 1 ||\n"
                + "\tinAreaTarget.y() >= 1 ||\n"
                + "\tinAreaTarget.z() >= 1) {\n"
                + "\tthrow new IllegalArgumentException(\"Target is not in the current area.\");\n"
                + "}\n";

        String sourceTwo = "\t\tif (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator()) {\n"
            + "\t\t\tMeasurementRenderer.getInstance().renderMeasurements(\n"
            + "\t\t}\n"
            + "\t\t\t\tposeStack,\n"
            + "\t\t\t\tbufferSource,\n"
            + "\t\t\t\tpartialTickTime\n"
            + "\t\t\t);";

        assertEquals(source, step.formatter().format("Example.java", source));
        assertEquals(sourceTwo, step.formatter().format("Example.java", sourceTwo));
    }

    @Test
    void doesNotMangleNestedIfWhenBodyStartsWithMultilineIfHeader() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);

        String source = "package mod.chiselsandbits.logic;\n\n"
                + "import java.util.List;\n\n"
                + "import mod.chiselsandbits.api.chiseling.eligibility.IEligibilityAnalysisResult;\n"
                + "import mod.chiselsandbits.api.chiseling.eligibility.IEligibilityManager;\n"
                + "import mod.chiselsandbits.api.config.ICommonConfiguration;\n"
                + "import mod.chiselsandbits.item.MagnifyingGlassItem;\n\n"
                + "import net.minecraft.ChatFormatting;\n"
                + "import net.minecraft.client.Minecraft;\n"
                + "import net.minecraft.network.chat.Component;\n"
                + "import net.minecraft.world.item.BlockItem;\n"
                + "import net.minecraft.world.item.ItemStack;\n\n"
                + "public class MagnifyingGlassTooltipHandler\n"
                + "{\n"
                + "\tpublic static void onItemTooltip(final ItemStack itemStack, final List<Component> toolTips)\n\n"
                + "\t{\n"
                + "\t\tif (Minecraft.getInstance().player != null && ICommonConfiguration.getInstance().getEnableHelp().get())\n"
                + "\t\t\tif (Minecraft.getInstance().player.getMainHandItem().getItem() instanceof MagnifyingGlassItem\n"
                + "\t\t\t\t  || Minecraft.getInstance().player.getOffhandItem().getItem() instanceof MagnifyingGlassItem)\n\n"
                + "\t\t\t\tif (itemStack.getItem() instanceof BlockItem) {\n"
                + "\t\t\t\t\tfinal IEligibilityAnalysisResult result = IEligibilityManager.getInstance().analyse(itemStack);\n\n"
                + "\t\t\t\t\ttoolTips.add(\n"
                + "\t\t\t\t\t\tresult.canBeChiseled() || result.isAlreadyChiseled() ?\n"
                + "\t\t\t\t\t\t  result.getReason().withStyle(ChatFormatting.GREEN) :\n"
                + "\t\t\t\t\t\t  result.getReason().withStyle(ChatFormatting.RED)\n"
                + "\t\t\t\t\t);\n"
                + "\t\t\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("MagnifyingGlassTooltipHandler.java", source));
    }

    @Test
    void wrapsUnbracedIfWithMultilineMethodCallBody() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleNeedBracesStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LITERAL_IF"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);

        String source = "package mod.chiselsandbits.client.logic;\n\n"
                + "import com.mojang.blaze3d.vertex.PoseStack;\n"
                + "import mod.chiselsandbits.client.render.MeasurementRenderer;\n\n"
                + "import net.minecraft.client.Minecraft;\n"
                + "import net.minecraft.client.renderer.MultiBufferSource;\n\n"
                + "public class MeasurementsRenderHandler\n"
                + "{\n"
                + "\tpublic static void renderMeasurements(\n"
                + "\t\tfinal PoseStack poseStack,\n"
                + "\t\tfinal MultiBufferSource.BufferSource bufferSource,\n"
                + "\t\tfinal float partialTickTime)\n"
                + "\t{\n"
                + "\t\tif (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator())\n"
                + "\t\t\tMeasurementRenderer.getInstance().renderMeasurements(\n"
                + "\t\t\t\tposeStack,\n"
                + "\t\t\t\tbufferSource,\n"
                + "\t\t\t\tpartialTickTime\n"
                + "\t\t\t);\n"
                + "\t}\n"
                + "}\n";

        String expected = "package mod.chiselsandbits.client.logic;\n\n"
                + "import com.mojang.blaze3d.vertex.PoseStack;\n"
                + "import mod.chiselsandbits.client.render.MeasurementRenderer;\n\n"
                + "import net.minecraft.client.Minecraft;\n"
                + "import net.minecraft.client.renderer.MultiBufferSource;\n\n"
                + "public class MeasurementsRenderHandler\n"
                + "{\n"
                + "\tpublic static void renderMeasurements(\n"
                + "\t\tfinal PoseStack poseStack,\n"
                + "\t\tfinal MultiBufferSource.BufferSource bufferSource,\n"
                + "\t\tfinal float partialTickTime)\n"
                + "\t{\n"
                + "\t\tif (Minecraft.getInstance().player != null && !Minecraft.getInstance().player.isSpectator()) {\n"
                + "\t\t\tMeasurementRenderer.getInstance().renderMeasurements(\n"
                + "\t\t\t\tposeStack,\n"
                + "\t\t\t\tbufferSource,\n"
                + "\t\t\t\tpartialTickTime\n"
                + "\t\t\t);\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("MeasurementsRenderHandler.java", source));
    }

    @Test
    void keepsUsedImportsWhenRemovingUnusedImports() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = "package test;\n\n"
                + "import java.util.Map;\n"
                + "import java.util.Map.Entry;\n\n"
                + "class Example {\n"
                + "\tprivate final Entry<String, String> entry;\n"
                + "\tprivate final Map<String, String> map;\n\n"
                + "\tExample(Entry<String, String> entry, Map<String, String> map) {\n"
                + "\t\tthis.entry = entry;\n"
                + "\t\tthis.map = map;\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void expandsWildcardImportsToAllUsedTypesAndStaticMembers() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(true);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(false);

        String source = "package test;\n\n"
                + "import java.util.*;\n"
                + "import static java.util.Collections.*;\n\n"
                + "class Example {\n"
                + "\tprivate final List<String> values = emptyList();\n"
                + "\tprivate final Map<String, String> mappings = emptyMap();\n"
                + "}\n";

        String expected = "package test;\n\n"
                + "import java.util.List;\n"
                + "import java.util.Map;\n"
                + "import static java.util.Collections.emptyList;\n"
                + "import static java.util.Collections.emptyMap;\n\n"
                + "class Example {\n"
                + "\tprivate final List<String> values = emptyList();\n"
                + "\tprivate final Map<String, String> mappings = emptyMap();\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void keepsImportsWhenTypeResolutionFails() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = "package test;\n\n"
                + "import com.example.project.IAreaAccessor;\n\n"
                + "class BlockNeighborhoodEntry {\n"
                + "\tpublic IAreaAccessor getAccessor() {\n"
                + "\t\treturn null;\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void writesAnalyzerDiagnosticsWhenTypeResolutionFails() throws Exception {
        var project = ProjectBuilder.builder().build();
        CheckstyleImportLintStep step = project.getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = "package test;\n\n"
                + "import com.example.project.IAreaAccessor;\n\n"
                + "class BlockNeighborhoodEntry {\n"
                + "\tpublic IAreaAccessor getAccessor() {\n"
                + "\t\treturn null;\n"
                + "\t}\n"
                + "}\n";

        step.formatter().format("Example.java", source);

        Path diagnosticsPath = project.getLayout()
                .getBuildDirectory()
                .dir("formatting/parsing/errors")
                .get()
                .getAsFile()
                .toPath()
                .resolve("Example.java");

        assertTrue(Files.exists(diagnosticsPath));
        String diagnostics = Files.readString(diagnosticsPath);
        assertTrue(diagnostics.contains("ERROR:"));
    }

    @Test
    void preservesImportsUsedInVariableDeclarations() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = "package mod.chiselsandbits.api.util;\n\n"
                + "import java.lang.reflect.Field;\n\n"
                + "public class ReflectionUtils {\n"
                + "\tpublic static void setField(final Object targetObject, final String fieldName, final Object value) {\n"
                + "\t\ttry {\n"
                + "\t\t\tField f = targetObject.getClass().getDeclaredField(fieldName);\n"
                + "\t\t\tf.setAccessible(true);\n"
                + "\t\t\tf.set(targetObject, value);\n"
                + "\t\t} catch (NoSuchFieldException | IllegalAccessException e) {\n"
                + "\t\t\tthrow new IllegalStateException(\"Failed to set value!\");\n"
                + "\t\t}\n"
                + "\t}\n\n"
                + "\tpublic static Object getField(final Object target, final String name) {\n"
                + "\t\ttry {\n"
                + "\t\t\tField f = target.getClass().getDeclaredField(name);\n"
                + "\t\t\tf.setAccessible(true);\n"
                + "\t\t\treturn f.get(target);\n"
                + "\t\t} catch (NoSuchFieldException | IllegalAccessException e) {\n"
                + "\t\t\tthrow new IllegalStateException(\"Failed to get value!\");\n"
                + "\t\t}\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("ReflectionUtils.java", source));
    }

    @Test
    void removesUnusedExternalImportWhenClasspathIsAvailable(@TempDir Path tempDir) throws Exception {
        Path srcRoot = tempDir.resolve("src");
        Path clsRoot = tempDir.resolve("classes");
        Files.createDirectories(srcRoot.resolve("com/example/project"));
        Files.createDirectories(clsRoot);

        Path usedType = srcRoot.resolve("com/example/project/IAreaAccessor.java");
        Path unusedType = srcRoot.resolve("com/example/project/UnusedType.java");
        Files.writeString(usedType, "package com.example.project; public interface IAreaAccessor {}\n");
        Files.writeString(unusedType, "package com.example.project; public final class UnusedType {}\n");

        int compileResult = ToolProvider.getSystemJavaCompiler().run(
                null,
                null,
                null,
                "-d", clsRoot.toString(),
                usedType.toString(),
                unusedType.toString()
        );
        assertEquals(0, compileResult);

        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalClasses().set(java.util.List.of());
        step.getIllegalPkgs().set(java.util.List.of());
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);
        step.getAnalysisClasspath().from(clsRoot.toFile());
        step.getAnalysisSourcepath().from(srcRoot.toFile());

        String source = "package test;\n\n"
                + "import com.example.project.IAreaAccessor;\n"
                + "import com.example.project.UnusedType;\n\n"
                + "class BlockNeighborhoodEntry {\n"
                + "\tpublic IAreaAccessor getAccessor() {\n"
                + "\t\treturn null;\n"
                + "\t}\n"
                + "}\n";

        String expected = "package test;\n\n"
                + "import com.example.project.IAreaAccessor;\n\n"
                + "class BlockNeighborhoodEntry {\n"
                + "\tpublic IAreaAccessor getAccessor() {\n"
                + "\t\treturn null;\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesRightCurlySamePolicyWithTryCatchEmptyLineBetween() {
        CheckstyleRightCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        step.getOption().set("same");

        // Test try-catch with empty line between } and catch
        String source = "void run() {\n\ttry {\n\t\tcall();\n\t}\n\n\tcatch (Exception ignored) {\n\t}\n}\n";
        String expected = "void run() {\n\ttry {\n\t\tcall();\n\t} catch (Exception ignored) {\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesRightCurlySamePolicyWithFinally() {
        CheckstyleRightCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        step.getOption().set("same");

        // Test try-finally with empty line between } and finally
        String source = "void run() {\n\ttry {\n\t\tcall();\n\t}\n\n\tfinally {\n\t\tcleanup();\n\t}\n}\n";
        String expected = "void run() {\n\ttry {\n\t\tcall();\n\t} finally {\n\t\tcleanup();\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesRightCurlySamePolicyWithWhile() {
        CheckstyleRightCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        step.getOption().set("same");

        // Test do-while with empty line between } and while
        String source = "void run() {\n\tdo {\n\t\tcall();\n\t}\n\n\twhile (condition);\n}\n";
        String expected = "void run() {\n\tdo {\n\t\tcall();\n\t} while (condition);\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesRightCurlySamePolicyWithMultipleEmptyLinesBetweenTryCatch() {
        CheckstyleRightCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        step.getOption().set("same");

        // Test try-catch with multiple empty lines between } and catch
        String source = "void run() {\n\ttry {\n\t\tcall();\n\t}\n\n\n\tcatch (Exception e) {\n\t\thandle();\n\t}\n}\n";
        String expected = "void run() {\n\ttry {\n\t\tcall();\n\t} catch (Exception e) {\n\t\thandle();\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesRightCurlyAlonePolicyWithContentAfterClosingBrace() {
        CheckstyleRightCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        step.getOption().set("alone");

        // Test that content after closing brace is moved to the next line with proper indentation
        String source = "void run() {\n\tif (flag) {\n\t\tcall();\n\t} else {\n\t\tother();\n\t}\n}\n";
        String expected = "void run() {\n\tif (flag) {\n\t\tcall();\n\t}\n\telse {\n\t\tother();\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }
}
