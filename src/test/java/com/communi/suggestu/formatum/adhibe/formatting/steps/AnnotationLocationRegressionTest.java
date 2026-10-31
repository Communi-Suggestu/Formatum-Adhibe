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

class AnnotationLocationRegressionTest {
    @TempDir
    Path directory;

    private CheckstyleAnnotationLocationStep step() {
        return ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleAnnotationLocationStep.class, "annotations");
    }

    private void check(String source, DefaultConfiguration... checks) throws Exception {
        var walker = new DefaultConfiguration("TreeWalker");
        for (var check : checks) {
            walker.addChild(check);
        }
        var configuration = new DefaultConfiguration("Checker");
        configuration.addProperty("tabWidth", "4");
        configuration.addChild(walker);
        var checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(configuration);
            Path file = directory.resolve("Example.java");
            Files.writeString(file, source);
            assertEquals(0, checker.process(List.of(file.toFile())), source);
        } finally {
            checker.destroy();
        }
    }

    @Test
    void indentationAndAnnotationLocationAgreeOnStackedFields() throws Exception {
        String source = """
                class Example {
                @Name("name")
                        @Label("Name")
                        public String name;
                }
                """;
        String actual = IndentationOwnershipTest.step().formatter().format("Example.java", source);
        String expected = "class Example {\n\t@Name(\"name\")\n\t@Label(\"Name\")\n\tpublic String name;\n}\n";
        assertEquals(expected, actual);
        assertEquals(actual, step().formatter().format("Example.java", actual));
        var indent = new DefaultConfiguration("Indentation");
        indent.addProperty("caseIndent", "0");
        check(actual, new DefaultConfiguration("AnnotationLocation"), indent);
    }

    @Test
    void separatesParameterizedAndMultipleAnnotationsButPreservesLiteralsAndLocals() throws Exception {
        String source = """
                @Name("Example") class Example {
                    @First @Second public String name;
                    @Override public String toString() {
                        @Local("keep") String value = "@First @Second";
                        return value;
                    }
                }
                """;
        var formatter = step().formatter();
        String actual = formatter.format("Example.java", source);
        assertTrue(actual.startsWith("@Name(\"Example\")\nclass Example {"));
        assertTrue(actual.contains("    @First\n    @Second\n    public String name;"));
        assertTrue(actual.contains("@Override public String"));
        assertTrue(actual.contains("@Local(\"keep\") String value = \"@First @Second\";"));
        assertEquals(actual, formatter.format("Example.java", actual));
        check(actual, new DefaultConfiguration("AnnotationLocation"));
    }

    @Test
    void respectsSameLineAndTokenOptions() throws Exception {
        String source = "@Name(\"Example\") class Example {\n    @First @Second String name;\n}\n";
        var step = step();
        step.getAllowSamelineMultipleAnnotations().set(true);
        assertEquals(source, step.formatter().format("Example.java", source));
        var config = new DefaultConfiguration("AnnotationLocation");
        config.addProperty("allowSamelineMultipleAnnotations", "true");
        check(source, config);
        step.getAllowSamelineMultipleAnnotations().set(false);
        step.getTokens().set(List.of("CLASS_DEF"));
        step.getAllowSamelineParameterizedAnnotation().set(true);
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void preservesCommentsAndTypeUseAnnotations() throws Exception {
        String source = "class Example {\n    @Name(\"value\") /* keep */ public @Nullable String value;\n}\n";
        String actual = step().formatter().format("Example.java", source);
        assertEquals("class Example {\n    @Name(\"value\") /* keep */\n    public @Nullable String value;\n}\n", actual);
        assertEquals(actual, step().formatter().format("Example.java", actual));
        check(actual, new DefaultConfiguration("AnnotationLocation"));
    }

    @Test
    void abstractModifierOrderingMatchesCheckstyle() throws Exception {
        String source = """
                abstract class Example {
                    private static abstract class Nested {}
                    abstract public void write();
                    abstract protected void read();
                }
                """;
        String actual = new ModifierOrderFixer().apply(source);
        check(actual, new DefaultConfiguration("ModifierOrder"));
    }

    @Test
    void handlesPackageEnumConstantAndCompactConstructorAnnotations() throws Exception {
        String source = """
                @Name("package") package test;
                enum Example {
                    @Name("value") VALUE;
                }
                record Data(String value) {
                    @Name("constructor") Data {}
                }
                """;
        String actual = step().formatter().format("Example.java", source);
        assertTrue(actual.startsWith("@Name(\"package\")\npackage test;"));
        assertTrue(actual.contains("@Name(\"value\")\n    VALUE;"));
        assertTrue(actual.contains("@Name(\"constructor\")\n    Data {}"));
        assertEquals(actual, step().formatter().format("Example.java", actual));
        check(actual, new DefaultConfiguration("AnnotationLocation"));
    }
}
