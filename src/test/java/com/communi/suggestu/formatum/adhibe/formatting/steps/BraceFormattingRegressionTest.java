package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.plugin.CheckstyleDeterministicStep;
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

class BraceFormattingRegressionTest {
    @Test
    void fullRepositoryFormatterStabilizesPreviouslyJoinedIndependentWhileOnFirstPass() {
        var step = ProjectBuilder.builder().build().getObjects()
                .newInstance(CheckstyleDeterministicStep.class, "fullCheckstyle");
        step.getCheckstyleConfig().set(Path.of("libs/checkstyle/checkstyle.xml").toAbsolutePath().toFile());
        var formatter = step.formatter();
        String source = """
                class Example {
                    void run(boolean flag) {
                        if (flag) {
                            work();
                        } while (flag) {
                                work();
                            }
                    }
                    
                    void work() {}
                }
                """.replace("    ", "\t");
        String formatted = formatter.format("Example.java", source);
        assertTrue(formatted.contains("\t\t}\n\n\t\twhile (flag) {\n\t\t\twork();\n\t\t}"), formatted);
        assertEquals(formatted, formatter.format("Example.java", formatted));
    }

    @Test
    void rightCurlyJoinsOnlyActualDoWhileTails() {
        CheckstyleRightCurlyStep step = rightCurly("same");
        String source = "class Example {\n"
                + "\tvoid run() {\n"
                + "\t\tif (ready) {\n"
                + "\t\t\twork();\n"
                + "\t\t}\n"
                + "\t\twhile (again) {\n"
                + "\t\t\twork();\n"
                + "\t\t}\n"
                + "\t\tdo {\n"
                + "\t\t\twork();\n"
                + "\t\t}\n"
                + "\t\twhile (again);\n"
                + "\t}\n"
                + "}\n";
        String expected = source.replace("\t\t}\n\t\twhile (again);", "\t\t} while (again);");
        String formatted = step.formatter().format("Example.java", source);

        assertEquals(expected, formatted);
        assertEquals(formatted, step.formatter().format("Example.java", formatted));
    }

    @Test
    void preservesAnonymousClassTerminatorAcrossRightCurlyAndSeparatorWrap() {
        CheckstyleRightCurlyStep rightCurly = rightCurly("same");
        CheckstyleSeparatorWrapStep separator = separatorWrap("eol", "SEMI");
        String source = "class Example {\n"
                + "\tObject value = new Object() {\n"
                + "\t\t@Override public String toString() { return \"value\"; }\n"
                + "\t}\n"
                + "\n"
                + "\t;\n"
                + "}\n";
        String formatted = separator.formatter().format("Example.java",
                rightCurly.formatter().format("Example.java", source));

        assertEquals("class Example {\n"
                + "\tObject value = new Object() {\n"
                + "\t\t@Override public String toString() { return \"value\"; }\n"
                + "\t};\n"
                + "}\n", formatted);
        assertEquals(formatted, separator.formatter().format("Example.java",
                rightCurly.formatter().format("Example.java", formatted)));
    }

    @Test
    void separatorWrapLeavesStandaloneEmptyStatementsAfterOpeningBraces() {
        CheckstyleSeparatorWrapStep step = separatorWrap("eol", "SEMI");
        String source = "class Example {\n\tvoid run() {\n\t\t;\n\t}\n}\n";

        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void needBracesUsesStatementBoundariesForMultilineAndNestedControls() {
        CheckstyleNeedBracesStep step = needBraces();
        String source = "class Example {\n"
                + "\tvoid run(boolean first, boolean second, int value) {\n"
                + "\t\tif (\n"
                + "\t\t\tfirst && second\n"
                + "\t\t)\n"
                + "\t\t\tif (value > 0)\n"
                + "\t\t\t\twork(value);\n"
                + "\t\telse if (value < 0)\n"
                + "\t\t\twork(-value);\n"
                + "\t\tfor (int index = 0;\n"
                + "\t\t\tindex < value;\n"
                + "\t\t\tindex++)\n"
                + "\t\t\twork(index);\n"
                + "\t}\n"
                + "\tvoid work(int value) {}\n"
                + "}\n";
        String expected = "class Example {\n"
                + "\tvoid run(boolean first, boolean second, int value) {\n"
                + "\t\tif (\n"
                + "\t\t\tfirst && second\n"
                + "\t\t) {\n"
                + "\t\t\tif (value > 0) {\n"
                + "\t\t\t\twork(value);\n"
                + "\t\t\t}\n"
                + "\t\telse if (value < 0) {\n"
                + "\t\t\twork(-value);\n"
                + "\t\t}\n"
                + "\t\t}\n"
                + "\t\tfor (int index = 0;\n"
                + "\t\t\tindex < value;\n"
                + "\t\t\tindex++) {\n"
                + "\t\t\twork(index);\n"
                + "\t\t}\n"
                + "\t}\n"
                + "\tvoid work(int value) {}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void needBracesHonorsOptionsAndIgnoresKeywordsInsideCommentsAndLiterals() {
        CheckstyleNeedBracesStep step = needBraces();
        step.getTokens().set(List.of("LITERAL_IF", "LITERAL_WHILE"));
        step.getAllowSingleLineStatement().set(true);
        step.getAllowEmptyLoopBody().set(true);
        String source = "class Example {\n"
                + "\tvoid run(boolean flag) {\n"
                + "\t\tif (flag) work();\n"
                + "\t\twhile (flag) ;\n"
                + "\t\tString text = \"if (flag) work();\";\n"
                + "\t\t// if (flag) work();\n"
                + "\t}\n"
                + "}\n";

        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void needBracesKeepsCommentsWithWrappedMultilineBodies() {
        CheckstyleNeedBracesStep step = needBraces();
        String source = "class Example {\n"
                + "\tvoid run(boolean flag) {\n"
                + "\t\tif (flag) /* if (ignored) */ work();\n"
                + "\t\tif (flag) // keep this comment\n"
                + "\t\t\twork();\n"
                + "\t}\n"
                + "\tvoid work() {}\n"
                + "}\n";
        String expected = "class Example {\n"
                + "\tvoid run(boolean flag) {\n"
                + "\t\tif (flag) { /* if (ignored) */\n"
                + "\t\t\twork();\n"
                + "\t\t}\n"
                + "\t\tif (flag) { // keep this comment\n"
                + "\t\t\twork();\n"
                + "\t\t}\n"
                + "\t}\n"
                + "\tvoid work() {}\n"
                + "}\n";

        assertEquals(expected, step.formatter().format("Example.java", source));
    }

    @Test
    void braceFormattingPassesActualCheckstyleAndIsIdempotent(@TempDir Path directory) throws Exception {
        String source = "class Example {\n"
                + "\tvoid run(boolean flag, int value) {\n"
                + "\t\tif (\n"
                + "\t\t\tflag\n"
                + "\t\t)\n"
                + "\t\t\twork(value);\n"
                + "\t\twhile (flag) {\n"
                + "\t\t\twork(value);\n"
                + "\t\t}\n"
                + "\t\tdo {\n"
                + "\t\t\twork(value);\n"
                + "\t\t}\n"
                + "\t\twhile (flag);\n"
                + "\t\tObject object = new Object() {\n"
                + "\t\t\t@Override public String toString() { return \"x\"; }\n"
                + "\t\t}\n"
                + "\t\t;\n"
                + "\t}\n"
                + "\tvoid work(int value) {}\n"
                + "}\n";

        String formatted = applyBraceSteps(source);
        Path file = directory.resolve("Example.java");
        Files.writeString(file, formatted);
        assertEquals(0, checkstyle(file, "NeedBraces", "RightCurly", "SeparatorWrap"));
        assertEquals(formatted, applyBraceSteps(formatted));
    }

    private static String applyBraceSteps(String source) {
        CheckstyleNeedBracesStep needBraces = needBraces();
        CheckstyleRightCurlyStep rightCurly = rightCurly("same");
        CheckstyleSeparatorWrapStep separator = separatorWrap("eol", "SEMI");
        String formatted = needBraces.formatter().format("Example.java", source);
        formatted = rightCurly.formatter().format("Example.java", formatted);
        return separator.formatter().format("Example.java", formatted);
    }

    private static CheckstyleNeedBracesStep needBraces() {
        CheckstyleNeedBracesStep step = ProjectBuilder.builder().build().getObjects()
                .newInstance(CheckstyleNeedBracesStep.class, "needBraces");
        step.getTokens().set(List.of("LITERAL_DO", "LITERAL_ELSE", "LITERAL_FOR", "LITERAL_IF", "LITERAL_WHILE"));
        step.getAllowSingleLineStatement().set(false);
        step.getAllowEmptyLoopBody().set(false);
        return step;
    }

    private static CheckstyleRightCurlyStep rightCurly(String option) {
        CheckstyleRightCurlyStep step = ProjectBuilder.builder().build().getObjects()
                .newInstance(CheckstyleRightCurlyStep.class, "rightCurly");
        step.getOption().set(option);
        return step;
    }

    private static CheckstyleSeparatorWrapStep separatorWrap(String option, String... tokens) {
        CheckstyleSeparatorWrapStep step = ProjectBuilder.builder().build().getObjects()
                .newInstance(CheckstyleSeparatorWrapStep.class, "separatorWrap");
        step.getOption().set(option);
        step.getTokens().set(List.of(tokens));
        return step;
    }

    private static int checkstyle(Path file, String... modules) throws Exception {
        DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
        for (String module : modules) {
            DefaultConfiguration child = new DefaultConfiguration(module);
            if (module.equals("SeparatorWrap")) {
                child.addProperty("option", "eol");
                child.addProperty("tokens", "SEMI");
            }
            walker.addChild(child);
        }
        DefaultConfiguration configuration = new DefaultConfiguration("Checker");
        configuration.addChild(walker);
        Checker checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(configuration);
            return checker.process(List.of(file.toFile()));
        } finally {
            checker.destroy();
        }
    }
}
