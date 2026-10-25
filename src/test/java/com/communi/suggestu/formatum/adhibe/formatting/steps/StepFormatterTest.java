package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.ToolProvider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

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
    void enforcesLeftCurlyPolicyHandlesBraceFollowedByBody() {
        CheckstyleLeftCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        step.getOption().set("eol");
        step.getIgnoreEnums().set(true);

        String source = "ChiselingOperation(final boolean processesAir)\n\t{\n\t\tthis.processesAir = processesAir;\n\t}\n";
        String expected = "ChiselingOperation(final boolean processesAir) {\n\t\tthis.processesAir = processesAir;\n\t}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesLeftCurlySkipsEnumBodyOpeningWhenIgnoreEnumsIsTrue() {
        CheckstyleLeftCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        step.getOption().set("eol");
        step.getIgnoreEnums().set(true);

        String source = "public enum Foo\n\t{\n\tA, B;\n}\n";
        String expected = "public enum Foo\n\t{\n\tA, B;\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesLeftCurlyHandlesEnumBodyOpeningWhenIgnoreEnumsIsFalse() {
        CheckstyleLeftCurlyStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        step.getOption().set("eol");
        step.getIgnoreEnums().set(false);

        String source = "public enum Foo\n\t{\n\tA, B;\n}\n";
        String expected = "public enum Foo {\n\tA, B;\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void enforcesLeftCurlyHandlesEnumBodyOpeningAndMethodBodies() {
        // Test with ignoreEnums=false (user explicitly wants enums treated like classes)
        CheckstyleLeftCurlyStep stepFalse = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleLeftCurlyStep.class, STEP_NAME);
        stepFalse.getOption().set("eol");
        stepFalse.getIgnoreEnums().set(false);

        String source = "package mod.chiselsandbits.api.chiseling;\n\n/**\n * Represents the mode of operandus of the current chiseling context.\n */\npublic enum ChiselingOperation\n\t{\n\tCHISELING(false),\n\tPLACING(true);\n\n\tChiselingOperation(final boolean processesAir) {this.processesAir = processesAir; }\n\n\tpublic boolean processesAir() {\n\t\treturn processesAir;\n\t}\n\n\tpublic boolean isPlacing() {\n\t\treturn this == PLACING;\n\t}\n}\n";
        String result = stepFalse.formatter().format("Example.java", source);
        // Verify that the enum body opening brace is moved to the enum declaration line
        // (the */ javadoc line is skipped, enum declaration is the attach point)
        assertTrue(result.contains("public enum ChiselingOperation {"));
        // Verify that method body braces are moved to previous line
        assertTrue(result.contains("processesAir() {"));
        assertTrue(result.contains("isPlacing() {"));
        // Verify that the constructor brace is moved
        assertTrue(result.contains("ChiselingOperation(final boolean processesAir) {"));
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

        String source = """
            class Example {
            	void run(
            	 final int a,
            	 final int b)
            
            	{
            		accessor.call(
            		 child(),
            		 new Consumer<>()
            
            		 {
            			@Override
            			public void accept(final String value)
            
            			{
            				final Optional<String> mapped = Optional.of(value).flatMap(
            				 d -> Optional.of(d)
            			);
            			}
            		 }
            
            		);
            	}
            }""";

        String expected = """
            class Example {
            	void run(
            	        final int a,
            	        final int b) {
            		accessor.call(
            		        child(),
            		        new Consumer<>() {
                                @Override
                                public void accept(final String value) {
                                    final Optional<String> mapped = Optional.of(value).flatMap(
                                            d -> Optional.of(d)
                                    );
                                }
            		        }
            
                    );
            	}
            }""";

        String afterLeftCurly = leftCurly.formatter().format("Example.java", source);
        String formatted = indentation.formatter().format("Example.java", afterLeftCurly);

        assertEquals(expected.replace("    ", "\t"), formatted);
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

        String expected = """
            class Example {
            \tvoid run() {
            \t\taccessor.call(
            \t\t\t\tIPositionMutator.xyz(),
            \t\t\t\tnew Consumer<>() {
            \t\t\t\t\t@Override
            \t\t\t\t\tpublic void accept(final String value) {
            \t\t\t\t\t\tif (value.isEmpty()) {
            \t\t\t\t\t\t\treturn;
            \t\t\t\t\t\t}
            \t\t\t\t\t\tcall();
            \t\t\t\t\t}
            \t\t\t\t}
            \t\t);
            \t}
            }
            """;

        String formatted = step.formatter().format("Example.java", source);

        assertEquals(expected, formatted);
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
    void indentsWrappedInterfaceExtendsListWithContinuationIndent() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);

        String source = "public interface IMultiStateBlockEntity extends IWorldAreaAccessor,\n"
                + "IWorldAreaMutator,\n"
                + "IBatchedAreaMutator,\n"
                + "IGenerallyModifiableAreaMutator,\n"
                + "IAreaAccessorWithVoxelShape,\n"
                + "ISingleBlockAxisAlignedAreaAccessor {\n"
                + "}\n";

        String expected = "public interface IMultiStateBlockEntity extends IWorldAreaAccessor,\n"
                + "\t\tIWorldAreaMutator,\n"
                + "\t\tIBatchedAreaMutator,\n"
                + "\t\tIGenerallyModifiableAreaMutator,\n"
                + "\t\tIAreaAccessorWithVoxelShape,\n"
                + "\t\tISingleBlockAxisAlignedAreaAccessor {\n"
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
    void indentsTernaryBranchesInsideWrappedLambdaInvocationWithContinuationIndent() {
        CheckstyleIndentationStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getArrayInitIndent().set(4);
        step.getLineWrappingIndentation().set(8);

        String source = "class Example {\n"
                + "\tString get() {\n"
                + "\t\treturn DistExecutor.unsafeRunForDist(\n"
                + "\t\t\t\t() -> () -> {\n"
                + "\t\t\t\t\tfinal Result result = getResult();\n"
                + "\t\t\t\t\treturn result.isOk() || result.canRetry()\n"
                + "\t\t\t\t\t? result.messageGreen()\n"
                + "\t\t\t\t\t: result.messageRed();\n"
                + "\t\t\t\t},\n"
                + "\t\t\t\t() -> () -> fallback()\n"
                + "\t\t);\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tString get() {\n"
                + "\t\treturn DistExecutor.unsafeRunForDist(\n"
                + "\t\t\t\t() -> () -> {\n"
                + "\t\t\t\t\tfinal Result result = getResult();\n"
                + "\t\t\t\t\treturn result.isOk() || result.canRetry()\n"
                + "\t\t\t\t\t\t\t? result.messageGreen()\n"
                + "\t\t\t\t\t\t\t: result.messageRed();\n"
                + "\t\t\t\t},\n"
                + "\t\t\t\t() -> () -> fallback()\n"
                + "\t\t);\n"
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
    void whitespaceAfterInsertsSpaceAfterVarargsEllipsis() {
        CheckstyleWhitespaceAfterStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAfterStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("ELLIPSIS"));

        String source = "IChiselContextPreviewRendererRegistry register(final IChiselContextPreviewRenderer...renderers);\n";
        String expected = "IChiselContextPreviewRendererRegistry register(final IChiselContextPreviewRenderer... renderers);\n";
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
    void whitespaceAroundNormalizesTernaryOperatorsInsideSwitchExpressionBranches() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("QUESTION", "COLON", "LT", "GT", "LE", "GE"));

        String source = "private Vec3 clampVectorToBlockAlongAxis(final Vec3 v, final Direction.Axis axis) {\n"
                + "\tswitch (axis) {\n"
                + "\t\tcase X -> {\n"
                + "\t\t\treturn new Vec3(v.x() <0 ? 0 : (v.x()>= 1 ? 1 - ONE_THOUSANDS : v.x()), v.y(), v.z());\n"
                + "\t\t}\n"
                + "\t\tcase Y -> {\n"
                + "\t\t\treturn new Vec3(v.x(), v.y() <0 ? 0 : (v.y()>= 1 ? 1 - ONE_THOUSANDS : v.y()), v.z());\n"
                + "\t\t}\n"
                + "\t\tcase Z -> {\n"
                + "\t\t\treturn new Vec3(v.x(), v.y(), v.z() <0 ? 0 : (v.z()>= 1 ? 1 - ONE_THOUSANDS : v.z()));\n"
                + "\t\t}\n"
                + "\t\tdefault -> throw new IllegalStateException(\"Unexpected value: \" + axis);\n"
                + "\t}\n"
                + "}\n";

        String expected = "private Vec3 clampVectorToBlockAlongAxis(final Vec3 v, final Direction.Axis axis) {\n"
                + "\tswitch (axis) {\n"
                + "\t\tcase X -> {\n"
                + "\t\t\treturn new Vec3(v.x() < 0 ? 0 : (v.x() >= 1 ? 1 - ONE_THOUSANDS : v.x()), v.y(), v.z());\n"
                + "\t\t}\n"
                + "\t\tcase Y -> {\n"
                + "\t\t\treturn new Vec3(v.x(), v.y() < 0 ? 0 : (v.y() >= 1 ? 1 - ONE_THOUSANDS : v.y()), v.z());\n"
                + "\t\t}\n"
                + "\t\tcase Z -> {\n"
                + "\t\t\treturn new Vec3(v.x(), v.y(), v.z() < 0 ? 0 : (v.z() >= 1 ? 1 - ONE_THOUSANDS : v.z()));\n"
                + "\t\t}\n"
                + "\t\tdefault -> throw new IllegalStateException(\"Unexpected value: \" + axis);\n"
                + "\t}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundPreservesIndentationForWrappedTernaryQuestionLine() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("QUESTION", "COLON"));

        String source = "\t\treturn !playerEntity.isShiftKeyDown()\n"
                + "\t\t\t\t?Vec3.atLowerCornerOf(blockRayTraceResult.getBlockPos())\n"
                + "\t\t\t\t:blockRayTraceResult.getLocation();\n";

        String expected = "\t\treturn !playerEntity.isShiftKeyDown()\n"
                + "\t\t\t\t? Vec3.atLowerCornerOf(blockRayTraceResult.getBlockPos())\n"
                + "\t\t\t\t: blockRayTraceResult.getLocation();\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundNormalizesRelationalOperatorsInLambdaContainedIfElseIf() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("GE", "LE", "LITERAL_IF", "LITERAL_ELSE"));

        String source = "default boolean validateBuildHeights() {\n"
                + "\treturn getMutator().map(mutator -> {\n"
                + "\t\tfinal BlockPos heightPos = mutator.getInWorldEndBlockPoint();\n\n"
                + "\t\tif (heightPos.getY()>= getWorld().getMaxY()) {\n"
                + "\t\t\tsetError(LocalStrings.ChiselAttemptFailedAttemptTooHigh.getText());\n"
                + "\t\t\treturn false;\n"
                + "\t\t} else if (heightPos.getY() <= getWorld().getMinY()) {\n"
                + "\t\t\tsetError(LocalStrings.ChiselAttemptFailedAttemptTooLow.getText());\n"
                + "\t\t\treturn false;\n"
                + "\t\t}\n\n"
                + "\t\treturn true;\n"
                + "\t}).orElse(true);\n"
                + "}\n";

        String expected = "default boolean validateBuildHeights() {\n"
                + "\treturn getMutator().map(mutator -> {\n"
                + "\t\tfinal BlockPos heightPos = mutator.getInWorldEndBlockPoint();\n\n"
                + "\t\tif (heightPos.getY() >= getWorld().getMaxY()) {\n"
                + "\t\t\tsetError(LocalStrings.ChiselAttemptFailedAttemptTooHigh.getText());\n"
                + "\t\t\treturn false;\n"
                + "\t\t} else if (heightPos.getY() <= getWorld().getMinY()) {\n"
                + "\t\t\tsetError(LocalStrings.ChiselAttemptFailedAttemptTooLow.getText());\n"
                + "\t\t\treturn false;\n"
                + "\t\t}\n\n"
                + "\t\treturn true;\n"
                + "\t}).orElse(true);\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundNormalizesComparisonOperatorInBooleanReturnExpression() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("GT", "LITERAL_RETURN", "ASSIGN"));

        String source = "default boolean tryDamageItem(final int damage) {\n"
                + "\treturn this.tryDamageItemAndDo(damage, () -> { }, () -> { })> 0;\n"
                + "\tfinal boolean damaged = this.tryDamageItemAndDo(damage, () -> { }, () -> { })> threshold;\n"
                + "}\n";

        String expected = "default boolean tryDamageItem(final int damage) {\n"
                + "\treturn this.tryDamageItemAndDo(damage, () -> { }, () -> { }) > 0;\n"
                + "\tfinal boolean damaged = this.tryDamageItemAndDo(damage, () -> { }, () -> { }) > threshold;\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundNormalizesLeGeComparisonOperatorsInBooleanReturnExpression() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LE", "GE", "LITERAL_RETURN"));

        String source = "default boolean isWithinBounds(final int damage) {\n"
                + "\treturn this.tryDamageItemAndDo(damage, () -> { }, () -> { })<= maxDamage;\n"
                + "}\n"
                + "default boolean isOverLimit(final int damage) {\n"
                + "\treturn this.tryDamageItemAndDo(damage, () -> { }, () -> { })>= limit;\n"
                + "}\n";

        String expected = "default boolean isWithinBounds(final int damage) {\n"
                + "\treturn this.tryDamageItemAndDo(damage, () -> { }, () -> { }) <= maxDamage;\n"
                + "}\n"
                + "default boolean isOverLimit(final int damage) {\n"
                + "\treturn this.tryDamageItemAndDo(damage, () -> { }, () -> { }) >= limit;\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundNormalizesLeGeComparisonOperatorsAssignedToBooleanVariables() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("LE", "GE", "ASSIGN"));

        String source = "default boolean evaluateDamageWindow(final int damage) {\n"
                + "\tfinal boolean underCap = this.tryDamageItemAndDo(damage, () -> { }, () -> { })<= maxDamage;\n"
                + "\tfinal boolean overFloor = this.tryDamageItemAndDo(damage, () -> { }, () -> { })>= minDamage;\n"
                + "\treturn underCap && overFloor;\n"
                + "}\n";

        String expected = "default boolean evaluateDamageWindow(final int damage) {\n"
                + "\tfinal boolean underCap = this.tryDamageItemAndDo(damage, () -> { }, () -> { }) <= maxDamage;\n"
                + "\tfinal boolean overFloor = this.tryDamageItemAndDo(damage, () -> { }, () -> { }) >= minDamage;\n"
                + "\treturn underCap && overFloor;\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundNormalizesShiftOperatorsInReturnStatements() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("SL", "SR", "LITERAL_RETURN"));

        String source = "int toMask(final int value) {\n"
                + "\treturn value<<4;\n"
                + "}\n"
                + "int fromMask(final int value) {\n"
                + "\treturn value>>2;\n"
                + "}\n";

        String expected = "int toMask(final int value) {\n"
                + "\treturn value << 4;\n"
                + "}\n"
                + "int fromMask(final int value) {\n"
                + "\treturn value >> 2;\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundDoesNotTreatGenericTypeClosersAsShiftOperators() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("SL", "SR", "LT", "GT", "ASSIGN", "LITERAL_RETURN"));

        String source = "\treturn RecordCodecBuilder.<Map.Entry<K, V>>create(instance -> instance);\n"
                + "\treturn value>>2;\n";

        String expected = "\treturn RecordCodecBuilder.<Map.Entry<K, V>>create(instance -> instance);\n"
                + "\treturn value >> 2;\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void whitespaceAroundNormalizesShiftOperatorsInAssignments() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        step.getTokens().set(java.util.List.of("SL", "SR", "ASSIGN"));

        String source = "void run(final int value) {\n"
                + "\tint shiftedLeft=value<<3;\n"
                + "\tint shiftedRight=value>>1;\n"
                + "}\n";

        String expected = "void run(final int value) {\n"
                + "\tint shiftedLeft = value << 3;\n"
                + "\tint shiftedRight = value >> 1;\n"
                + "}\n";

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
    void appliesArrayTypeStyleRules() {
        CheckstyleArrayTypeStyleStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleArrayTypeStyleStep.class, STEP_NAME);

        String source = "StreamCodec<FriendlyByteBuf, long[]> LONG_ARRAY = new StreamCodec<>() {\n"
                + "\tpublic long [] decode(FriendlyByteBuf buffer) {\n"
                + "\t\treturn buffer.readLongArray();\n"
                + "\t}\n"
                + "\n"
                + "\tpublic void encode(FriendlyByteBuf buffer, long [] payload) {\n"
                + "\t\tbuffer.writeLongArray(payload);\n"
                + "\t}\n"
                + "};\n";

        String expected = "StreamCodec<FriendlyByteBuf, long[]> LONG_ARRAY = new StreamCodec<>() {\n"
                + "\tpublic long[] decode(FriendlyByteBuf buffer) {\n"
                + "\t\treturn buffer.readLongArray();\n"
                + "\t}\n"
                + "\n"
                + "\tpublic void encode(FriendlyByteBuf buffer, long[] payload) {\n"
                + "\t\tbuffer.writeLongArray(payload);\n"
                + "\t}\n"
                + "};\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void arrayTypeStyleNormalizesMultidimensionalArraysAndLeavesLiteralsUntouched() {
        CheckstyleArrayTypeStyleStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleArrayTypeStyleStep.class, STEP_NAME);

        String source = "class Example {\n"
                + "\tint [][] grid = new int [] [];\n"
                + "\tlong[] values = { 1L };\n"
                + "\tString label = \"see [docs] for details\"; // long [] in a comment stays\n"
                + "\tint first = values [ 0 ];\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\tint[][] grid = new int[][];\n"
                + "\tlong[] values = { 1L };\n"
                + "\tString label = \"see [docs] for details\"; // long [] in a comment stays\n"
                + "\tint first = values[ 0 ];\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void genericMethodInvocationDoesNotAddSpaceAfterClosingAngles() {
        CheckstyleGenericWhitespaceStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleGenericWhitespaceStep.class, STEP_NAME);

        String source = "\treturn RecordCodecBuilder.<Map.Entry<K, V>>create(instance -> instance);\n";
        String expected = "\treturn RecordCodecBuilder.<Map.Entry<K, V>>create(instance -> instance);\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void genericMethodInvocationStripsSpaceAfterClosingAnglesWhenFollowedByMethod() {
        CheckstyleGenericWhitespaceStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleGenericWhitespaceStep.class, STEP_NAME);

        // Input has spaces between generic method invocation closers and method names —
        // the algorithm strips them because the < is preceded by a dot.
        String source = "\treturn RecordCodecBuilder.<Map.Entry<K, V>> create(instance -> instance);\n"
                + "\tFoo.<Bar.Baz> method();\n"
                + "\tList.<String> of();\n"
                + "\tBar.<T extends Comparable<T>> compute(1, 2);\n";
        String expected = "\treturn RecordCodecBuilder.<Map.Entry<K, V>>create(instance -> instance);\n"
                + "\tFoo.<Bar.Baz>method();\n"
                + "\tList.<String>of();\n"
                + "\tBar.<T extends Comparable<T>>compute(1, 2);\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void genericMethodInvocationWithNestedGenericsDoesNotAddSpace() {
        CheckstyleGenericWhitespaceStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleGenericWhitespaceStep.class, STEP_NAME);

        String source = "\tFoo.<Bar.Baz>method();\n"
                + "\tBar.<T extends Comparable<T>>compute(1, 2);\n"
                + "\tBaz.<Map<String, List<Integer>>>factory();\n";
        String expected = "\tFoo.<Bar.Baz>method();\n"
                + "\tBar.<T extends Comparable<T>>compute(1, 2);\n"
                + "\tBaz.<Map<String, List<Integer>>>factory();\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void genericWhitespaceDoesNotTreatComparisonOperatorsAsGenerics() {
        CheckstyleGenericWhitespaceStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleGenericWhitespaceStep.class, STEP_NAME);

        // Comparison operators must be left alone — they are not generics.
        String source = "\treturn v.x() < 0 ? 0 : v.x();\n"
                + "\treturn v.y() >= 1 ? 0 : v.y();\n"
                + "\tint a = value << 3;\n"
                + "\tint b = value >> 1;\n"
                + "\tboolean cond = left < right;\n"
                + "\tint result = a << 2 + b >> 1;\n";
        String expected = "\treturn v.x() < 0 ? 0 : v.x();\n"
                + "\treturn v.y() >= 1 ? 0 : v.y();\n"
                + "\tint a = value << 3;\n"
                + "\tint b = value >> 1;\n"
                + "\tboolean cond = left < right;\n"
                + "\tint result = a << 2 + b >> 1;\n";
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
    void retainsJavadocImportsAndRemovesUnusedImports() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = """
                package test;

                import java.util.List;
                import java.util.Set;

                /** {@link List} */
                class Example {}
                """;
        String expected = source.replace("import java.util.Set;\n", "");

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void expandsWildcardImportsUsedOnlyInJavadoc() {
        CheckstyleImportLintStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, STEP_NAME);
        step.getAvoidStarImport().set(true);
        step.getRemoveIllegalImports().set(false);
        step.getRemoveRedundantImports().set(false);
        step.getRemoveUnusedImports().set(true);

        String source = """
                package test;

                import java.util.*;

                /** {@link List} and {@link Map.Entry} */
                class Example {}
                """;
        String expected = source.replace("import java.util.*;", "import java.util.List;\nimport java.util.Map;");

        String formatted = step.formatter().format("Example.java", source);
        assertEquals(expected, formatted);
        assertEquals(expected, step.formatter().format("Example.java", formatted));
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

    @Test
    void formatsTheComparisonOperatorOfForLoops() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, STEP_NAME);
        CheckstyleGenericWhitespaceStep secondStep = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleGenericWhitespaceStep.class, STEP_NAME);

        final String[] tokens = "ASSIGN,BAND,BAND_ASSIGN,BOR,BOR_ASSIGN,BSR,BSR_ASSIGN,BXOR,BXOR_ASSIGN,COLON,DIV_ASSIGN,DO_WHILE,EQUAL,GE,GT,LAMBDA,LAND,LCURLY,LE,LITERAL_CATCH,LITERAL_DO,LITERAL_ELSE,LITERAL_FINALLY,LITERAL_FOR,LITERAL_IF,LITERAL_RETURN,LITERAL_SWITCH,LITERAL_SYNCHRONIZED,LITERAL_TRY,LITERAL_WHILE,LOR,LT,MINUS_ASSIGN,MOD,MOD_ASSIGN,NOT_EQUAL,PLUS_ASSIGN,QUESTION,RCURLY,SL,SLIST,SL_ASSIGN,SR,SR_ASSIGN,STAR,STAR_ASSIGN,LITERAL_ASSERT,TYPE_EXTENSION_AND".split(",");
        step.getTokens().set(Arrays.asList(tokens));

        String source = "/**\n"
            + "\t * Extracts a given amount of bits with the given blockstate from the the current inventory.\n"
            + "\t *\n"
            + "\t * @param blockInformation The blockstate.\n"
            + "\t * @param count The amount of bits to extract.\n"
            + "\t * @throws IllegalArgumentException when extraction is not possible.\n"
            + "\t */\n"
            + "\t@Override\n"
            + "\tpublic void extract(\n"
            + "\t\t\tfinal BlockInformation blockInformation,\n"
            + "\t\t\tfinal int count) throws IllegalArgumentException {\n"
            + "\t\tif (count == 0 || blockInformation.isAir()) {\n"
            + "\t\t\treturn;\n"
            + "\t\t}\n"
            + "\n"
            + "\t\tif (!canExtract(blockInformation, count)) {\n"
            + "\t\t\tthrow new IllegalArgumentException(\"Can not extract: \" + blockInformation);\n"
            + "\t\t}\n"
            + "\n"
            + "\t\tint toExtract = count;\n"
            + "\n"
            + "\t\tfor (int i = getInventorySize() - 1; i>= 0; i--) {\n"
            + "\t\t\tfinal ItemStack stack = getItem(i);\n"
            + "\n"
            + "\t\t\tif (stack.getItem() instanceof final IBitItem bitItem) {\n"
            + "\t\t\t\tif (bitItem.getBlockInformation(stack).equals(blockInformation)) {\n"
            + "\t\t\t\t\tfinal int stackExtractCount = Math.min(toExtract, stack.getCount());\n"
            + "\t\t\t\t\ttoExtract -= stackExtractCount;\n"
            + "\n"
            + "\t\t\t\t\tstack.setCount(stack.getCount() - stackExtractCount);\n"
            + "\n"
            + "\t\t\t\t\tsetItem(i, stack);\n"
            + "\t\t\t\t}\n"
            + "\t\t\t}\n"
            + "\t\t}\n"
            + "\t}";

        String expected = "/**\n"
            + "\t * Extracts a given amount of bits with the given blockstate from the the current inventory.\n"
            + "\t *\n"
            + "\t * @param blockInformation The blockstate.\n"
            + "\t * @param count The amount of bits to extract.\n"
            + "\t * @throws IllegalArgumentException when extraction is not possible.\n"
            + "\t */\n"
            + "\t@Override\n"
            + "\tpublic void extract(\n"
            + "\t\t\tfinal BlockInformation blockInformation,\n"
            + "\t\t\tfinal int count) throws IllegalArgumentException {\n"
            + "\t\tif (count == 0 || blockInformation.isAir()) {\n"
            + "\t\t\treturn;\n"
            + "\t\t}\n"
            + "\n"
            + "\t\tif (!canExtract(blockInformation, count)) {\n"
            + "\t\t\tthrow new IllegalArgumentException(\"Can not extract: \" + blockInformation);\n"
            + "\t\t}\n"
            + "\n"
            + "\t\tint toExtract = count;\n"
            + "\n"
            + "\t\tfor (int i = getInventorySize() - 1; i >= 0; i--) {\n"
            + "\t\t\tfinal ItemStack stack = getItem(i);\n"
            + "\n"
            + "\t\t\tif (stack.getItem() instanceof final IBitItem bitItem) {\n"
            + "\t\t\t\tif (bitItem.getBlockInformation(stack).equals(blockInformation)) {\n"
            + "\t\t\t\t\tfinal int stackExtractCount = Math.min(toExtract, stack.getCount());\n"
            + "\t\t\t\t\ttoExtract -= stackExtractCount;\n"
            + "\n"
            + "\t\t\t\t\tstack.setCount(stack.getCount() - stackExtractCount);\n"
            + "\n"
            + "\t\t\t\t\tsetItem(i, stack);\n"
            + "\t\t\t\t}\n"
            + "\t\t\t}\n"
            + "\t\t}\n"
            + "\t}";

        var initial = step.formatter().format("Example.java", source);
        assertEquals(expected, secondStep.formatter().format("Example.java", initial));
    }

    @Test
    void formattingLeadingWhitespaceToTabsStepLeavesStructuralIndentation() {
        LeadingWhitespaceToTabsStep step = ProjectBuilder.builder().build().getObjects().newInstance(LeadingWhitespaceToTabsStep.class, STEP_NAME);
        step.getTabWidth().set(4);

        String expected = "\n"
            + "\t/**\n"
            + "\t * Returns the maximal amount of bits with a given blockstate which can be inserted of a given blockstate.\n"
            + "\t *\n"
            + "\t * @param blockInformation The blockstate in question.\n"
            + "\t * @return The amount of bits that can be inserted with a given blockstate.\n"
            + "\t */\n"
            + "\t@Override\n"
            + "\tpublic int getMaxInsertAmount(final BlockInformation blockInformation) {\n"
            + "\t\treturn IntStream.range(0, getInventorySize())\n"
            + "\t\t\t\t.mapToObj(this::getItem)\n"
            + "\t\t\t\t.filter(stack -> stack.getItem() instanceof IBitItem || stack.isEmpty())\n"
            + "\t\t\t\t.mapToInt(stack -> {\n"
            + "\t\t\t\t\tif (stack.isEmpty()) {\n"
            + "\t\t\t\t\t\treturn getMaxBitsFor(stack);\n"
            + "\t\t\t\t\t}\n"
            + "\n"
            + "\t\t\t\t\tIBitItem bitItem = (IBitItem) stack.getItem();\n"
            + "\n"
            + "\t\t\t\t\tif (bitItem.getBlockInformation(stack).equals(blockInformation)) {\n"
            + "\t\t\t\t\t\treturn getMaxBitsFor(stack) - stack.getCount();\n"
            + "\t\t\t\t\t}\n"
            + "\n"
            + "\t\t\t\t\treturn 0;\n"
            + "\t\t\t\t})\n"
            + "\t\t\t\t.sum();\n"
            + "\t}";

        assertEquals(expected, step.formatter().format("Example.java", expected));
    }

    @Test
    void formatsNestedLambdaChainWithoutExtraIndentOrBlankLines() {
        CheckstyleIndentationStep indentation = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        indentation.getBasicOffset().set(4);
        indentation.getCaseIndent().set(0);
        indentation.getThrowsIndent().set(4);
        indentation.getArrayInitIndent().set(4);
        indentation.getLineWrappingIndentation().set(8);

        CheckstyleRightCurlyStep rightCurly = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        rightCurly.getOption().set("same");

        RemoveBlankLineBeforeClosingBraceStep removeBlankLines = ProjectBuilder.builder().build().getObjects().newInstance(RemoveBlankLineBeforeClosingBraceStep.class, STEP_NAME);

        String source = "class Example {\n"
                + "\t@Override\n"
                + "\tpublic Map<BlockInformation, Integer> getContainedStates() {\n"
                + "\t\treturn IntStream.range(0, getInventorySize())\n"
                + "\t\t\t\t.mapToObj(this::getItem)\n"
                + "\t\t\t\t.filter(stack -> stack.getItem() instanceof IBitItem)\n"
                + "\t\t\t\t.map(stack -> {\n"
                + "\t\t\t\t\t\tIBitItem bitItem = (IBitItem) stack.getItem();\n"
                + "\t\t\t\t\t\treturn Maps.newHashMap(ImmutableMap.of(bitItem.getBlockInformation(stack), stack.getCount()));\n"
                + "\t\t\t\t}\n"
                + "\t\t\t\t)\n"
                + "\t\t\t\t.reduce(\n"
                + "\t\t\t\t\t\tMaps.newHashMap(),\n"
                + "\t\t\t\t\t\t(blockStateIntegerHashMap, blockStateIntegerHashMap2) -> {\n"
                + "\t\t\t\t\t\tfinal HashMap<BlockInformation, Integer> result = Maps.newHashMap(blockStateIntegerHashMap);\n"
                + "\t\t\t\t\t\tblockStateIntegerHashMap2.forEach((state, count) -> {\n"
                + "\t\t\t\t\t\t\t\tif (!result.containsKey(state)) {\n"
                + "\t\t\t\t\t\t\t\tresult.put(state, count);\n"
                + "\t\t\t\t\t\t\t\t} else {\n"
                + "\t\t\t\t\t\t\t\tresult.put(state, result.get(state) + count);\n"
                + "\t\t\t\t\t}\n"
                + "\t\t\t\t\t\t}\n"
                + "\t\t\t\t\t\t);\n\n"
                + "\t\t\t\t\t\treturn result;\n"
                + "\t\t\t\t\t\t}\n"
                + "\t\t\t\t);\n"
                + "\t}\n"
                + "}\n";

        String expected = "class Example {\n"
                + "\t@Override\n"
                + "\tpublic Map<BlockInformation, Integer> getContainedStates() {\n"
                + "\t\treturn IntStream.range(0, getInventorySize())\n"
                + "\t\t\t\t.mapToObj(this::getItem)\n"
                + "\t\t\t\t.filter(stack -> stack.getItem() instanceof IBitItem)\n"
                + "\t\t\t\t.map(stack -> {\n"
                + "\t\t\t\t\tIBitItem bitItem = (IBitItem) stack.getItem();\n"
                + "\t\t\t\t\treturn Maps.newHashMap(ImmutableMap.of(bitItem.getBlockInformation(stack), stack.getCount()));\n"
                + "\t\t\t\t})\n"
                + "\t\t\t\t.reduce(\n"
                + "\t\t\t\t\t\tMaps.newHashMap(),\n"
                + "\t\t\t\t\t\t(blockStateIntegerHashMap, blockStateIntegerHashMap2) -> {\n"
                + "\t\t\t\t\t\t\tfinal HashMap<BlockInformation, Integer> result = Maps.newHashMap(blockStateIntegerHashMap);\n"
                + "\t\t\t\t\t\t\tblockStateIntegerHashMap2.forEach((state, count) -> {\n"
                + "\t\t\t\t\t\t\t\tif (!result.containsKey(state)) {\n"
                + "\t\t\t\t\t\t\t\t\tresult.put(state, count);\n"
                + "\t\t\t\t\t\t\t\t} else {\n"
                + "\t\t\t\t\t\t\t\t\tresult.put(state, result.get(state) + count);\n"
                + "\t\t\t\t\t\t\t\t}\n"
                + "\t\t\t\t\t\t\t});\n\n"
                + "\t\t\t\t\t\t\treturn result;\n"
                + "\t\t\t\t\t\t});\n"
                + "\t}\n"
                + "}\n";

        String formatted = indentation.formatter().format("Example.java", source);
        formatted = rightCurly.formatter().format("Example.java", formatted);
        formatted = removeBlankLines.formatter().format("Example.java", formatted);

        assertEquals(expected, formatted);
    }

    @Test
    void consecutiveRunsOfIndentationAndBraceHandlingAreStable() {
        CheckstyleIndentationStep indentation = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, STEP_NAME);
        indentation.getBasicOffset().set(4);
        indentation.getCaseIndent().set(0);
        indentation.getThrowsIndent().set(4);
        indentation.getArrayInitIndent().set(4);
        indentation.getLineWrappingIndentation().set(8);

        CheckstyleRightCurlyStep rightCurly = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleRightCurlyStep.class, STEP_NAME);
        rightCurly.getOption().set("same");

        RemoveBlankLineBeforeClosingBraceStep removeBlankLines = ProjectBuilder.builder().build().getObjects().newInstance(RemoveBlankLineBeforeClosingBraceStep.class, STEP_NAME);

        String source = "class Example {\n"
            + "\t@Override\n"
            + "\tpublic Map<BlockInformation, Integer> getContainedStates() {\n"
            + "\t\treturn IntStream.range(0, getInventorySize())\n"
            + "\t\t\t\t.mapToObj(this::getItem)\n"
            + "\t\t\t\t.filter(stack -> stack.getItem() instanceof IBitItem)\n"
            + "\t\t\t\t.map(stack -> {\n"
            + "\t\t\t\t\t\tIBitItem bitItem = (IBitItem) stack.getItem();\n"
            + "\t\t\t\t\t\treturn Maps.newHashMap(ImmutableMap.of(bitItem.getBlockInformation(stack), stack.getCount()));\n"
            + "\t\t\t\t}\n"
            + "\t\t\t\t)\n"
            + "\t\t\t\t.reduce(\n"
            + "\t\t\t\t\t\tMaps.newHashMap(),\n"
            + "\t\t\t\t\t\t(blockStateIntegerHashMap, blockStateIntegerHashMap2) -> {\n"
            + "\t\t\t\t\t\tfinal HashMap<BlockInformation, Integer> result = Maps.newHashMap(blockStateIntegerHashMap);\n"
            + "\t\t\t\t\t\tblockStateIntegerHashMap2.forEach((state, count) -> {\n"
            + "\t\t\t\t\t\t\t\tif (!result.containsKey(state)) {\n"
            + "\t\t\t\t\t\t\t\tresult.put(state, count);\n"
            + "\t\t\t\t\t\t\t\t} else {\n"
            + "\t\t\t\t\t\t\t\tresult.put(state, result.get(state) + count);\n"
            + "\t\t\t\t\t}\n"
            + "\t\t\t\t\t\t}\n"
            + "\t\t\t\t\t\t);\n\n"
            + "\t\t\t\t\t\treturn result;\n"
            + "\t\t\t\t\t\t}\n"
            + "\t\t\t\t);\n"
            + "\t}\n"
            + "}\n";

        String expected = "class Example {\n"
            + "\t@Override\n"
            + "\tpublic Map<BlockInformation, Integer> getContainedStates() {\n"
            + "\t\treturn IntStream.range(0, getInventorySize())\n"
            + "\t\t\t\t.mapToObj(this::getItem)\n"
            + "\t\t\t\t.filter(stack -> stack.getItem() instanceof IBitItem)\n"
            + "\t\t\t\t.map(stack -> {\n"
            + "\t\t\t\t\tIBitItem bitItem = (IBitItem) stack.getItem();\n"
            + "\t\t\t\t\treturn Maps.newHashMap(ImmutableMap.of(bitItem.getBlockInformation(stack), stack.getCount()));\n"
            + "\t\t\t\t})\n"
            + "\t\t\t\t.reduce(\n"
            + "\t\t\t\t\t\tMaps.newHashMap(),\n"
            + "\t\t\t\t\t\t(blockStateIntegerHashMap, blockStateIntegerHashMap2) -> {\n"
            + "\t\t\t\t\t\t\tfinal HashMap<BlockInformation, Integer> result = Maps.newHashMap(blockStateIntegerHashMap);\n"
            + "\t\t\t\t\t\t\tblockStateIntegerHashMap2.forEach((state, count) -> {\n"
            + "\t\t\t\t\t\t\t\tif (!result.containsKey(state)) {\n"
            + "\t\t\t\t\t\t\t\t\tresult.put(state, count);\n"
            + "\t\t\t\t\t\t\t\t} else {\n"
            + "\t\t\t\t\t\t\t\t\tresult.put(state, result.get(state) + count);\n"
            + "\t\t\t\t\t\t\t\t}\n"
            + "\t\t\t\t\t\t\t});\n\n"
            + "\t\t\t\t\t\t\treturn result;\n"
            + "\t\t\t\t\t\t});\n"
            + "\t}\n"
            + "}\n";

        String formatted = indentation.formatter().format("Example.java", source);
        formatted = rightCurly.formatter().format("Example.java", formatted);
        formatted = removeBlankLines.formatter().format("Example.java", formatted);

        assertEquals(expected, formatted);

        formatted = indentation.formatter().format("Example.java", formatted);
        formatted = rightCurly.formatter().format("Example.java", formatted);
        formatted = removeBlankLines.formatter().format("Example.java", formatted);

        assertEquals(expected, formatted);
    }

    @Test
    void removesBlankLinesBeforeClosingParensAndMixedDelimiters() {
        RemoveBlankLineBeforeClosingBraceStep removeBlankLines = ProjectBuilder.builder().build().getObjects().newInstance(RemoveBlankLineBeforeClosingBraceStep.class, STEP_NAME);

        // Test that blank lines before closing ) are removed
        String source1 = "{\n\tcall();\n}\n\n)";
        String expected1 = "{\n\tcall();\n}\n)";
        assertEquals(expected1, removeBlankLines.formatter().format("Example.java", source1));

        // Test that blank lines before }); are removed
        String source2 = "forEach(item -> {\n\tprocess(item);\n}\n\n);";
        String expected2 = "forEach(item -> {\n\tprocess(item);\n}\n);";
        assertEquals(expected2, removeBlankLines.formatter().format("Example.java", source2));
    }

    @Test
    void javaStepReturningNoChangesDoesNotBreakImportOrderStep() {
        // Tests the fix for CheckstyleDeterministicStep.withProblemReporting:
        // When the Java step returns null (unchanged text), the ImportOrder step
        // should still receive the original text and apply ordering.
        CheckstyleImportOrderStep orderStep = ProjectBuilder.builder().build()
                .getObjects().newInstance(CheckstyleImportOrderStep.class, STEP_NAME);
        orderStep.getGroups().set(java.util.List.of("java", "javax", "*", "net.fabricmc"));
        orderStep.getSeparated().set(true);
        orderStep.getOption().set("top");

        // Clean Java file: no formatting issues → Java step returns null
        String source = "package test;\n\nimport javax.swing.JButton;\nimport java.util.Map;\n\npublic class Example {\n}\n";

        // Simulate the Java step returning null (no changes needed)
        String javaResult = null;
        // withProblemReporting fix: return the original text, not the formatter's null result
        String checkstyleInput = javaResult != null ? javaResult : source;

        // The ImportOrder step should NOT crash and should reorder the imports
        String formatted = orderStep.formatter().format("Example.java", checkstyleInput);

        // Verify: java.util.Map should come before javax.swing.JButton
        assertTrue(formatted.indexOf("import java.util.Map") < formatted.indexOf("import javax.swing"));
        // Verify both imports still present (no unused import removal in ordering step)
        assertTrue(formatted.contains("import java.util.Map"));
        assertTrue(formatted.contains("import javax.swing"));
    }

    // ── JavadocParagraph tests ──────────────────────────────────────────────

    @Test
    void javadocParagraphFillsInMissingPAndBlankLine() {
        CheckstyleJavadocParagraphStep step =
                ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, STEP_NAME);

        String source = "/**\n"
                + " * First paragraph.\n"
                + " *\n"
                + " * Second paragraph without p tag.\n"
                + " */\n"
                + "class Example {}\n";
        String expected = "/**\n"
                + " * First paragraph.\n"
                + " *\n"
                + " * <p>\n"
                + " * Second paragraph without p tag.\n"
                + " */\n"
                + "class Example {}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void javadocParagraphRemovesPBeforeBlockTag() {
        CheckstyleJavadocParagraphStep step =
                ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, STEP_NAME);

        String source = "/**\n"
                + " * First paragraph.\n"
                + " *\n"
                + " * <p>\n"
                + " * <pre>code here</pre>\n"
                + " */\n"
                + "class Example {}\n";
        String expected = "/**\n"
                + " * First paragraph.\n"
                + " *\n"
                + " * <pre>code here</pre>\n"
                + " */\n"
                + "class Example {}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void javadocParagraphDoesNotAlterAlreadyCorrect() {
        CheckstyleJavadocParagraphStep step =
                ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, STEP_NAME);

        String source = "/**\n"
                + " * First paragraph.\n"
                + " *\n"
                + " * <p>\n"
                + " * Second paragraph.\n"
                + " */\n"
                + "class Example {}\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void javadocParagraphHandlesMultipleParagraphs() {
        CheckstyleJavadocParagraphStep step =
                ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, STEP_NAME);

        String source = "/**\n"
                + " * First.\n"
                + " *\n"
                + " * Second.\n"
                + " *\n"
                + " * Third.\n"
                + " */\n"
                + "class Example {}\n";
        String expected = "/**\n"
                + " * First.\n"
                + " *\n"
                + " * <p>\n"
                + " * Second.\n"
                + " *\n"
                + " * <p>\n"
                + " * Third.\n"
                + " */\n"
                + "class Example {}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void javadocParagraphHandlesCodeWithJavadocButNoChanges() {
        CheckstyleJavadocParagraphStep step =
                ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, STEP_NAME);

        String source = "class Example {}\n";
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void javadocParagraphInsertsBlankLineBeforePInMultilineFirstParagraph() {
        CheckstyleJavadocParagraphStep step =
                ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, STEP_NAME);

        // Case with minimal (1-space) indentation
        String source = "/**\n"
                + " * Provides a stream of BlockPos objects within a given range.\n"
                + " * The coordinates run from 0 to size - 1 on each axis.\n"
                + " * <p>\n"
                + " * Internally invokes {@link #getForRange(int, int)}.\n"
                + " *\n"
                + " * @param size The size of each axis.\n"
                + " * @return A stream of block positions.\n"
                + " */\n"
                + "class Example {}\n";
        // Only the body paragraph gets <p>; the @param/@return last paragraph does not
        String expected = "/**\n"
                + " * Provides a stream of BlockPos objects within a given range.\n"
                + " * The coordinates run from 0 to size - 1 on each axis.\n"
                + " *\n"
                + " * <p>\n"
                + " * Internally invokes {@link #getForRange(int, int)}.\n"
                + " *\n"
                + " * @param size The size of each axis.\n"
                + " * @return A stream of block positions.\n"
                + " */\n"
                + "class Example {}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void javadocParagraphInsertsIndentedBlankLineBeforeP() {
        CheckstyleJavadocParagraphStep step =
                ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, STEP_NAME);

        // Real-world case: 5-space indentation (as in the issue report)
        // No extra <p> inserted before @param/@return last paragraph
        String source = "/**\n"
                + "     * Provides a stream of BlockPos objects within a given range which is {@code size} cubed in size.\n"
                + "     * The coordinates will as such (on each axis) run from 0 to {@code size - 1}.\n"
                + "     * <p>\n"
                + "     * Internally invokes {@link #getForRange(int, int)}, with {@code 0} as the min value,\n"
                + "     * and {@code size - 1} as max value.\n"
                + "     *\n"
                + "     * @param size The size of each axis for the block positions stream.\n"
                + "     * @return A stream of block positions in the {@code size} cubed in area.\n"
                + "     */\n"
                + "    public static Stream<BlockPos> getForRange(final int size) {\n"
                + "        return getForRange(0, size - 1);\n"
                + "    }\n";
        String expected = "/**\n"
                + "     * Provides a stream of BlockPos objects within a given range which is {@code size} cubed in size.\n"
                + "     * The coordinates will as such (on each axis) run from 0 to {@code size - 1}.\n"
                + "     *\n"
                + "     * <p>\n"
                + "     * Internally invokes {@link #getForRange(int, int)}, with {@code 0} as the min value,\n"
                + "     * and {@code size - 1} as max value.\n"
                + "     *\n"
                + "     * @param size The size of each axis for the block positions stream.\n"
                + "     * @return A stream of block positions in the {@code size} cubed in area.\n"
                + "     */\n"
                + "    public static Stream<BlockPos> getForRange(final int size) {\n"
                + "        return getForRange(0, size - 1);\n"
                + "    }\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void importOrderSeparatesGroupsWithoutExistingBlankLine() {
        // Regression test: when imports from different groups have no blank line
        // between them, the formatter MUST insert one (separated=true).
        CheckstyleImportOrderStep step = ProjectBuilder.builder().build()
                .getObjects().newInstance(CheckstyleImportOrderStep.class, STEP_NAME);
        step.getGroups().set(java.util.List.of("java", "javax", "*", "net.minecraft", "net.fabricmc"));
        step.getSeparated().set(true);
        step.getOption().set("top");
        step.getSortStaticImportsAlphabetically().set(true);

        String source = "package test;\n\n"
                + "import java.lang.annotation.Annotation;\n"
                + "import java.lang.reflect.Field;\n"
                + "import java.lang.reflect.InvocationTargetException;\n"
                + "import java.lang.reflect.Modifier;\n"
                + "import java.util.function.Function;\n"
                + "import org.apache.logging.log4j.LogManager;\n"
                + "import org.apache.logging.log4j.Logger;\n"
                + "import org.jspecify.annotations.Nullable;\n"
                + "\n"
                + "class Example {}\n";
        String result = step.formatter().format("Example.java", source);

        // Verify: blank line must exist between the last java import and first org import
        int javaFunctionLine = result.indexOf("import java.util.function.Function;");
        int logManagerLine = result.indexOf("import org.apache.logging.log4j.LogManager;");
        assertTrue(javaFunctionLine >= 0, "java.util.function.Function should be in output");
        assertTrue(logManagerLine >= 0, "org.apache.logging.log4j.LogManager should be in output");
        assertTrue(logManagerLine > javaFunctionLine, "java imports before org imports");

        // There must be a blank line (empty string line) between them
        String[] lines = result.split("\n", -1);
        int javaLine = -1, logLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.equals("import java.util.function.Function;")) javaLine = i;
            if (trimmed.equals("import org.apache.logging.log4j.LogManager;")) logLine = i;
        }
        assertTrue(javaLine >= 0 && logLine >= 0, "Both imports must be present");
        // There should be at least one empty line between them
        boolean foundBlankLine = false;
        for (int i = javaLine + 1; i < logLine; i++) {
            if (lines[i].trim().isEmpty()) {
                foundBlankLine = true;
                break;
            }
        }
        assertTrue(foundBlankLine, "Expected blank line between java and org import groups");
    }

    @Test
    void importOrderHandlesTabIndentedImports() {
        // Regression test: when the LeadingWhitespaceToTabsStep has already converted
        // leading spaces to tabs (producing \timport ...), the ImportOrder step must
        // still match the import lines and insert blank lines between groups.
        CheckstyleImportOrderStep step = ProjectBuilder.builder().build()
                .getObjects().newInstance(CheckstyleImportOrderStep.class, STEP_NAME);
        step.getGroups().set(java.util.List.of("java", "javax", "*", "net.minecraft", "net.fabricmc"));
        step.getSeparated().set(true);
        step.getOption().set("top");
        step.getSortStaticImportsAlphabetically().set(true);

        // Simulate output from LeadingWhitespaceToTabsStep: imports now start with tabs
        String source = "package test;\n\n"
                + "\timport java.lang.annotation.Annotation;\n"
                + "\timport java.util.function.Function;\n"
                + "\timport org.apache.logging.log4j.LogManager;\n"
                + "\timport org.jspecify.annotations.Nullable;\n"
                + "\n"
                + "class Example {}\n";
        String result = step.formatter().format("Example.java", source);

        String[] lines = result.split("\n", -1);
        int javaFunctionLine = -1, logManagerLine = -1;
        for (int i = 0; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            if (trimmed.equals("import java.util.function.Function;")) javaFunctionLine = i;
            if (trimmed.equals("import org.apache.logging.log4j.LogManager;")) logManagerLine = i;
        }
        assertTrue(javaFunctionLine >= 0, "java.util.function.Function must be present");
        assertTrue(logManagerLine >= 0, "org.apache.logging.log4j.LogManager must be present");
        assertTrue(logManagerLine > javaFunctionLine, "java imports before org imports");

        boolean foundBlankLine = false;
        for (int i = javaFunctionLine + 1; i < logManagerLine; i++) {
            if (lines[i].trim().isEmpty()) {
                foundBlankLine = true;
                break;
            }
        }
        assertTrue(foundBlankLine, "Expected blank line between java and org import groups even with tab-indented imports");
    }

    @Test
    void javadocParagraphSkipsPForLastTagParagraph() {
        CheckstyleJavadocParagraphStep step =
                ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, STEP_NAME);

        // Two body paragraphs plus a final @param paragraph
        String source = "/**\n"
                + "     * First body paragraph.\n"
                + "     *\n"
                + "     * Second body paragraph.\n"
                + "     *\n"
                + "     * @param size The size.\n"
                + "     */\n"
                + "class Example {}\n";
        String expected = "/**\n"
                + "     * First body paragraph.\n"
                + "     *\n"
                + "     * <p>\n"
                + "     * Second body paragraph.\n"
                + "     *\n"
                + "     * @param size The size.\n"
                + "     */\n"
                + "class Example {}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }
}
