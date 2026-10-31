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

class SimplifyBooleanReturnRegressionTest {
    @TempDir
    Path directory;

    private String format(String source) {
        var step = ProjectBuilder.builder().build().getObjects()
                .newInstance(CheckstyleSimplifyBooleanReturnStep.class, "booleanReturn");
        String actual = step.formatter().format("Example.java", source);
        assertEquals(actual, step.formatter().format("Example.java", actual));
        return actual;
    }

    private int violations(String source) throws Exception {
        var configuration = new DefaultConfiguration("Checker");
        var walker = new DefaultConfiguration("TreeWalker");
        walker.addChild(new DefaultConfiguration("SimplifyBooleanReturn"));
        configuration.addChild(walker);
        var checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(configuration);
            Path file = directory.resolve("Example.java");
            Files.writeString(file, source);
            return checker.process(List.of(file.toFile()));
        } finally {
            checker.destroy();
        }
    }

    @Test
    void replacesConsumerConditionAndInverseWithoutRepeatingCalls() throws Exception {
        String source = """
                class Example {
                    boolean run() {
                        if (isEquippable() && !hasItem() && isAlive()) {
                            return true;
                        } else {
                            return false;
                        }
                    }
                    boolean inverse() {
                        if (first() || second()) return false;
                        else return true;
                    }
                }
                """;
        assertEquals(2, violations(source));
        String actual = format(source);
        assertTrue(actual.contains("return isEquippable() && !hasItem() && isAlive();"));
        assertTrue(actual.contains("return !(first() || second());"));
        assertEquals(0, violations(actual));
    }

    @Test
    void preservesCommentsAndDanglingElseOwnership() throws Exception {
        String source = """
                class Example {
                    boolean run(boolean outer, boolean value) {
                        if (outer)
                            if (value /* condition */) {
                                // true branch
                                return true;
                            } else {
                                /* false branch */
                                return false;
                            }
                        else return true;
                    }
                }
                """;
        String actual = format(source);
        assertTrue(actual.contains("// true branch"));
        assertTrue(actual.contains("/* false branch */"));
        assertTrue(actual.contains("/* condition */"));
        assertEquals(0, violations(actual));
        assertEquals(0, javax.tools.ToolProvider.getSystemJavaCompiler().run(null, null, null,
                "-d", directory.toString(), directory.resolve("Example.java").toString()));
    }

    @Test
    void preservesCommentsInsideExpressionsAndLiteralContents() throws Exception {
        String source = """
                class Example {
                    String text = "if (ready()) return true; else return false;";
                    boolean run() {
                        if (ready() // condition details
                                && other()) return true; else return false;
                    }
                }
                """;
        String actual = format(source);
        assertTrue(actual.contains("return ready() // condition details\n"));
        assertTrue(actual.contains("\"if (ready()) return true; else return false;\""));
        assertEquals(0, violations(actual));
    }

    @Test
    void simplifiesYieldAndLeavesUnsafeBranchesUntouched() throws Exception {
        String source = """
                class Example {
                    boolean run(int value, boolean flag) {
                        return switch (value) {
                            default -> {
                                if (flag) { yield false; } else { yield true; }
                            }
                        };
                    }
                }
                """;
        String actual = format(source);
        assertTrue(actual.contains("yield !(flag);"));
        assertEquals(0, violations(actual));
        String unsafe = """
                class Example {
                    boolean equal() {
                        if (sideEffect()) return true; else return true;
                    }
                    boolean extra() {
                        if (sideEffect()) { record(); return true; } else return false;
                    }
                    boolean trailing() {
                        if (sideEffect()) return true;
                        return false;
                    }
                    boolean mixed(int value, boolean flag) {
                        return switch (value) {
                            default -> { if (flag) return false; else yield true; }
                        };
                    }
                }
                """;
        assertEquals(unsafe, format(unsafe));
    }

    @Test
    void handlesMemberAndStatementFragments() {
        assertEquals("boolean run() { return ready(); }\n",
                format("boolean run() { if (ready()) return true; else return false; }\n"));
        assertEquals("return !(ready());\n", format("if (ready()) return false; else return true;\n"));
    }
}
