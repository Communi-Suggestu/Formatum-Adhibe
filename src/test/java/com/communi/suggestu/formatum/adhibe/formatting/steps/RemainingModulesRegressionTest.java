package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemainingModulesRegressionTest {
    @Test
    void wrapsStringConcatenationsWithoutChangingTheirContents() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleOperatorWrapStep.class, "wrap");
        String source = "String text = \"Storage{\" +\n"
                + "\t\"palette = \" + palette +\n"
                + "\t\", data = \" + data +\n"
                + "\t\", width = \" + width +\n"
                + "\t'}';\n";
        String expected = "String text = \"Storage{\"\n"
                + "\t+ \"palette = \" + palette\n"
                + "\t+ \", data = \" + data\n"
                + "\t+ \", width = \" + width\n"
                + "\t+ '}';\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
        assertEquals(expected, step.formatter().format("Example.java", expected));
        step.getOption().set("eol");
        assertEquals(source, step.formatter().format("Example.java", expected));
    }

    @Test
    void wrapsOnlyConfiguredBinaryOperatorsOutsideLiteralsAndComments() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleOperatorWrapStep.class, "wrap");
        step.getTokens().set(List.of("PLUS"));
        String prefix = "String block = \"\"\"\ntext +\nmore text\n\"\"\";\n"
                + "// comment +\n/* comment +\ncontinued */\n";
        String source = prefix + "int value = first + // keep this comment\n\tsecond;\n"
                + "int other = first -\n\tsecond;\nint unary = +\n\tfirst;\n";
        String expected = prefix + "int value = first // keep this comment\n\t+ second;\n"
                + "int other = first -\n\tsecond;\nint unary = +\n\tfirst;\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void indentsCommentsInsideMethodAndNestedBlockToFollowingStatements() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleCommentsIndentationStep.class, "comments");
        String source = "class Example {\n\tvoid run() {\n\t// method comment\n\t\ttry {\n"
                + "\t\t// nested comment\n\t\t\tcall();\n\t\t} finally {\n"
                + "// empty block\n\t\t}\n\t}\n}\n";
        String expected = "class Example {\n\tvoid run() {\n\t\t// method comment\n\t\ttry {\n"
                + "\t\t\t// nested comment\n\t\t\tcall();\n\t\t} finally {\n"
                + "\t\t\t// empty block\n\t\t}\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
        assertEquals(expected, step.formatter().format("Example.java", expected));
    }

    @Test
    void preservesTrailingCommentsTextBlocksAndRelativeBlockCommentIndentation() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleCommentsIndentationStep.class, "comments");
        String source = "class Example {\n\tvoid run() {\n\t\tcall(); // keep\n"
                + "\t\t/*\n\t\t   diagram\n\t\t     detail\n\t\t */\n\t\tother();\n"
                + "\t\t// end of block\n\t}\n"
                + "\tString block = \"\"\"\n// literal\n/* literal */\n\"\"\";\n}\n";
        assertEquals(source, step.formatter().format("Example.java", source));
        assertEquals(source, step.formatter().format("Example.java", step.formatter().format("Example.java", source)));
    }

    @Test
    void removesSpaceAfterInlineParagraphTag() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, "paragraphs");
        String source = "/**\n * First paragraph.\n *\n * <p> Cases where closing fails.\n"
                + " * More details.\n */\ninterface Example {}\n";
        String expected = source.replace("<p> Cases", "<p>Cases");
        assertEquals(expected, step.formatter().format("Example.java", source));
        assertEquals(expected, step.formatter().format("Example.java", expected));
    }

    @Test
    void respectsParagraphNewlineConfiguration() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, "paragraphs");
        String source = "/**\n * First.\n *\n * <p>\n * Second.\n */\nclass Example {}\n";
        assertEquals(source, step.formatter().format("Example.java", source));
        step.getAllowNewlineParagraph().set(false);
        String expected = source.replace(" * <p>\n * Second.", " * <p>Second.");
        assertEquals(expected, step.formatter().format("Example.java", source));
        assertEquals(expected, step.formatter().format("Example.java", expected));
    }

    @Test
    void preservesPreformattedContentAndNormalizesUppercaseParagraphTag() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocParagraphStep.class, "paragraphs");
        String source = "/**\n * First.\n *\n * <pre>\n * <p> Literal example.\n * </pre>\n"
                + " *\n * <P> Next paragraph.\n */\nclass Example {}\n";
        String expected = source.replace("<P> Next", "<P>Next");
        assertEquals(expected, step.formatter().format("Example.java", source));
        assertEquals(expected, step.formatter().format("Example.java", expected));
    }

    @Test
    void importOnlyPackageInfoRetainsNewlineThroughoutFormatting() {
        var objects = ProjectBuilder.builder().build().getObjects();
        var newline = objects.newInstance(EnsureTrailingNewlineStep.class, "newline").formatter();
        var order = objects.newInstance(CheckstyleImportOrderStep.class, "order");
        order.getGroups().set(List.of("java", "*"));
        order.getSeparated().set(true);
        order.getOption().set("top");
        order.getSortStaticImportsAlphabetically().set(true);
        String source = "@Deprecated\npackage example;\n\nimport java.lang.Deprecated;";
        String expected = source + "\n";
        String actual = order.formatter().format("package-info.java", newline.format("package-info.java", source));
        assertEquals(expected, actual);
        assertEquals(actual, order.formatter().format("package-info.java", newline.format("package-info.java", actual)));
    }

    @Test
    void allFourCorrectionsSatisfyCheckstyleAndRemainStable(@TempDir Path directory) throws Exception {
        var objects = ProjectBuilder.builder().build().getObjects();
        var newline = objects.newInstance(EnsureTrailingNewlineStep.class, "newline").formatter();
        var operators = objects.newInstance(CheckstyleOperatorWrapStep.class, "operators").formatter();
        var comments = objects.newInstance(CheckstyleCommentsIndentationStep.class, "comments").formatter();
        var paragraphs = objects.newInstance(CheckstyleJavadocParagraphStep.class, "paragraphs").formatter();
        String source = "/**\n * First paragraph.\n *\n * <p> Cases where closing fails.\n */\n"
                + "class Example {\n\tString run(boolean flag) {\n"
                + "\t// Before statement\n\t\tif (flag) {\n"
                + "\t\t// Inside block\n\t\t\treturn \"first +\" +\n\t\t\t\t\"second\";\n"
                + "\t\t}\n\t\treturn \"other\";\n\t}\n}";
        DefaultConfiguration configuration = new DefaultConfiguration("Checker");
        configuration.addProperty("tabWidth", "4");
        configuration.addChild(new DefaultConfiguration("NewlineAtEndOfFile"));
        DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
        for (String check : List.of("OperatorWrap", "CommentsIndentation", "JavadocParagraph")) {
            walker.addChild(new DefaultConfiguration(check));
        }
        configuration.addChild(walker);
        Checker checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(configuration);
            Path file = directory.resolve("Example.java");
            Files.writeString(file, source);
            assertTrue(checker.process(List.of(file.toFile())) > 0);
            String actual = source;
            for (int pass = 0; pass < 2; pass++) {
                String formatted = newline.format("Example.java", actual);
                formatted = operators.format("Example.java", formatted);
                formatted = comments.format("Example.java", formatted);
                formatted = paragraphs.format("Example.java", formatted);
                Files.writeString(file, formatted);
                assertEquals(0, checker.process(List.of(file.toFile())));
                if (pass > 0) {
                    assertEquals(actual, formatted);
                }
                actual = formatted;
            }
        } finally {
            checker.destroy();
        }
    }
}
