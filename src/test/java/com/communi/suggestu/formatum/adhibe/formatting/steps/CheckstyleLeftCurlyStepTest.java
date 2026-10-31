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

class CheckstyleLeftCurlyStepTest {
    private static String format(String source, boolean ignoreEnums) {
        CheckstyleLeftCurlyStep step = ProjectBuilder.builder().build().getObjects()
                .newInstance(CheckstyleLeftCurlyStep.class, "leftCurly");
        step.getOption().set("eol");
        step.getIgnoreEnums().set(ignoreEnums);
        return step.formatter().format("Example.java", source);
    }

    @Test
    void movesTopLevelAndNestedEnumBracesRegardlessOfIgnoreEnums() {
        String source = "enum Example\n{\n\tA;\n\tenum Nested\n\n\t{\n\t\tB;\n\t}\n}\n";
        String expected = "enum Example {\n\tA;\n\tenum Nested {\n\t\tB;\n\t}\n}\n";
        assertEquals(expected, format(source, true));
        assertEquals(expected, format(source, false));
    }

    @Test
    void ignoreEnumsOnlyControlsLineBreakBeforeConstants() {
        String source = "enum Example { A, B;\n}\n";
        assertEquals(source, format(source, true));
        assertEquals("enum Example {\n\tA, B;\n}\n", format(source, false));
    }

    @Test
    void splitsInlineConstructorAndMethodBodiesInsideEnums() {
        String source = "enum Example {\n\tA;\n"
                + "\tExample() { call();\n\t\tother();\n\t}\n"
                + "\tboolean valid() { return true; }\n}\n";
        String expected = "enum Example {\n\tA;\n"
                + "\tExample() {\n\t\tcall();\n\t\tother();\n\t}\n"
                + "\tboolean valid() {\n\t\treturn true; }\n}\n";
        assertEquals(expected, format(source, true));
        assertEquals(expected, format(source, false));
        assertEquals(expected, format(expected, true));
    }

    @Test
    void preservesEmptyBodiesArraysLiteralsAndComments() {
        String source = "class Example {\n"
                + "\tExample() {}\n"
                + "\tvoid empty() { /* empty */ }\n"
                + "\tint[][] values = {{1, 2}, {3, 4}};\n"
                + "\tString message = \"{ return true; } {}. Using it\";\n"
                + "\tchar brace = '{';\n"
                + "\tString block = \"\"\"\n"
                + "{ literal body }\n"
                + "\"\"\";\n"
                + "\t// { return true; }\n"
                + "\t/* { return true; } */\n"
                + "}\n";
        assertEquals(source, format(source, true));
    }

    @Test
    void preservesInlineCommentWhenSplittingBody() {
        String source = "class Example {\n\tvoid run() { /* keep */ call();\n\t}\n}\n";
        String expected = "class Example {\n\tvoid run() {\n\t\t/* keep */ call();\n\t}\n}\n";
        assertEquals(expected, format(source, true));
    }

    @Test
    void movesBraceAndSplitsFollowingBody() {
        String source = "class Example {\n\tvoid run()\n\t{ call();\n\t}\n}\n";
        String expected = "class Example {\n\tvoid run() {\n\t\tcall();\n\t}\n}\n";
        assertEquals(expected, format(source, true));
    }

    @Test
    void formattedEnumConstructorsAndMethodsPassCheckstyle(@TempDir Path directory) throws Exception {
        String source = "enum Example\n{\n\tA;\n"
                + "\tExample() { call();\n\t\tother();\n\t}\n"
                + "\tboolean valid() { return true; }\n"
                + "\tenum Nested\n\t{\n\t\tB;\n\t}\n}\n";
        Path file = directory.resolve("Example.java");
        for (boolean ignoreEnums : List.of(true, false)) {
            DefaultConfiguration check = new DefaultConfiguration("LeftCurly");
            check.addProperty("ignoreEnums", Boolean.toString(ignoreEnums));
            DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
            walker.addChild(check);
            DefaultConfiguration configuration = new DefaultConfiguration("Checker");
            configuration.addChild(walker);
            Checker checker = new Checker();
            try {
                checker.setModuleClassLoader(Checker.class.getClassLoader());
                checker.configure(configuration);
                Files.writeString(file, source);
                assertTrue(checker.process(List.of(file.toFile())) > 0);
                String formatted = format(source, ignoreEnums);
                Files.writeString(file, formatted);
                assertEquals(0, checker.process(List.of(file.toFile())));
                assertEquals(formatted, format(formatted, ignoreEnums));
            } finally {
                checker.destroy();
            }
        }
    }
}
