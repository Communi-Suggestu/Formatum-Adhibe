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

class ImportCommentsRegressionTest {
    private static CheckstyleImportOrderStep orderStep() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportOrderStep.class, "order");
        step.getGroups().set(List.of("java", "javax", "*", "net.minecraft", "net.fabricmc"));
        step.getSeparated().set(true);
        step.getOption().set("top");
        step.getSortStaticImportsAlphabetically().set(true);
        return step;
    }

    private static CheckstyleImportLintStep lintStep() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleImportLintStep.class, "lint");
        step.getAvoidStarImport().set(false);
        step.getRemoveIllegalImports().set(false);
        step.getIllegalImportsReason().set("");
        step.getIllegalClasses().set(List.of());
        step.getIllegalPkgs().set(List.of());
        step.getRemoveRedundantImports().set(true);
        step.getRemoveUnusedImports().set(true);
        return step;
    }

    @Test
    void ordersImportsAcrossLineAndMultilineComments() {
        String comments = "// ILLEGAL IMPORT: -> import forbidden.Nullable; -> use another annotation\n"
                + "/* Explanation with import fake.Type;\n"
                + "\n"
                + " * import another.Fake;\n"
                + " */\n";
        String source = "package test;\n\nimport java.util.Optional;\n" + comments
                + "import net.minecraft.core.Direction;\nimport org.jspecify.annotations.Nullable;\n\nclass Example {}\n";
        String expected = "package test;\n\n" + comments + "import java.util.Optional;\n\n"
                + "import org.jspecify.annotations.Nullable;\n\nimport net.minecraft.core.Direction;\n\nclass Example {}\n";
        var formatter = orderStep().formatter();
        String actual = formatter.format("Example.java", source);
        assertEquals(expected, actual);
        assertEquals(actual, formatter.format("Example.java", actual));
    }

    @Test
    void removesUnusedImportsAcrossCommentsWithoutLosingComments() {
        String comments = "// ILLEGAL IMPORT: -> import forbidden.Nullable;\n"
                + "/* import fake.Type;\n"
                + "\n"
                + " * Preserve this whole comment.\n"
                + " */\n";
        String source = "package test;\n\n\timport java.util.List;\n" + comments
                + "import java.util.Set;\nimport java.util.Map;\n\nclass Example { List<String> values; }\n";
        String expected = "package test;\n\nimport java.util.List;\n" + comments
                + "\nclass Example { List<String> values; }\n";
        var formatter = lintStep().formatter();
        String actual = formatter.format("Example.java", source);
        assertEquals(expected, actual);
        assertEquals(actual, formatter.format("Example.java", actual));
    }

    @Test
    void illegalImportNoticeDoesNotBlockLaterLintOrOrderingPasses() {
        var lint = lintStep();
        lint.getRemoveIllegalImports().set(true);
        lint.getIllegalClasses().set(List.of("java.util.Set"));
        lint.getIllegalImportsReason().set("Forbidden type");
        String source = "package test;\n\nimport java.util.List;\nimport java.util.Set;\nimport java.util.Map;\n"
                + "import java.util.ArrayList;\n\nclass Example { List<String> values = new ArrayList<>(); }\n";
        String sanitized = lint.formatter().format("Example.java", source);
        String expected = "package test;\n\n// ILLEGAL IMPORT: -> import java.util.Set;   ->   Forbidden type\n"
                + "import java.util.ArrayList;\nimport java.util.List;\n\nclass Example { List<String> values = new ArrayList<>(); }\n";
        String actual = orderStep().formatter().format("Example.java", sanitized);
        assertEquals(expected, actual);
        assertEquals(actual, lint.formatter().format("Example.java", actual));
        assertEquals(actual, orderStep().formatter().format("Example.java", actual));
    }

    @Test
    void commentsDoNotBecomeImportsWhenAllRealImportsAreRemoved() {
        String header = "package test;\n\n// Header: import fake.Header;\n";
        String comment = "/*\nimport java.util.Map;\n*/\n";
        String suffix = "\n// Trailing comment: import fake.Footer;\nclass Example {}\n";
        String source = header + "import java.util.List;\n" + comment + "import java.util.Set;\n" + suffix;
        var lint = lintStep().formatter();
        String actual = lint.format("Example.java", source);
        assertEquals(header + comment + suffix, actual);
        assertEquals(actual, lint.format("Example.java", actual));
        assertEquals(actual, orderStep().formatter().format("Example.java", actual));
    }

    @Test
    void orderingKeepsTrailingMultilineCommentTogether() {
        String source = "import java.util.Set; /* explanation\n * continued\n */\n"
                + "import java.util.List;\n\nclass Example {}\n";
        String expected = "import java.util.List;\nimport java.util.Set; /* explanation\n * continued\n */\n\nclass Example {}\n";
        var formatter = orderStep().formatter();
        String actual = formatter.format("Example.java", source);
        assertEquals(expected, actual);
        assertEquals(actual, formatter.format("Example.java", actual));
    }

    @Test
    void removingImportsAtEndOfFilePreservesFinalNewline() {
        String source = "package test;\n\nimport java.util.List;\nimport java.util.Set;\n";
        String actual = lintStep().formatter().format("package-info.java", source);
        assertEquals("package test;\n", actual);
        assertEquals(actual, orderStep().formatter().format("package-info.java", actual));
    }

    @Test
    void ordersAndLintsMultipleImportsOnOneLine() {
        String source = "package test;\n\nimport java.util.Set;\n\n"
                + "import java.util.Map; /* preserve */ import java.util.List;\n"
                + "import java.util.Optional;\n\nclass Example { List<String> values; Map<String, String> map; }\n";
        String ordered = orderStep().formatter().format("Example.java", source);
        assertTrue(ordered.contains("import java.util.List;\nimport java.util.Map; /* preserve */\n"));
        assertTrue(ordered.contains("import java.util.Optional;\nimport java.util.Set;"));
        assertEquals(ordered, orderStep().formatter().format("Example.java", ordered));
        String actual = lintStep().formatter().format("Example.java", ordered);
        assertTrue(!actual.contains("import java.util.Set;") && !actual.contains("import java.util.Optional;"));
        assertEquals(actual, lintStep().formatter().format("Example.java", actual));
    }

    @Test
    void commentsDoNotSeparateImportsInSameGroupAccordingToCheckstyle(@TempDir Path directory) throws Exception {
        String notice = "// ILLEGAL IMPORT: -> import org.jetbrains.annotations.Nullable; -> Use JSpecify\n";
        String source = "package test;\n\nimport mod.example.Accessor;\n" + notice
                + "/* Keep this explanation.\n * import fake.Type;\n */\n"
                + "import org.jspecify.annotations.Nullable;\n\n"
                + "import net.minecraft.core.Direction;\n\nclass Example {}\n";
        String expected = "package test;\n\n" + notice
                + "/* Keep this explanation.\n * import fake.Type;\n */\n"
                + "import mod.example.Accessor;\nimport org.jspecify.annotations.Nullable;\n\n"
                + "import net.minecraft.core.Direction;\n\nclass Example {}\n";
        DefaultConfiguration check = new DefaultConfiguration("ImportOrder");
        check.addProperty("groups", "java,javax,*,net.minecraft,net.fabricmc");
        check.addProperty("ordered", "true");
        check.addProperty("separated", "true");
        check.addProperty("option", "top");
        DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
        walker.addChild(check);
        DefaultConfiguration configuration = new DefaultConfiguration("Checker");
        configuration.addChild(walker);
        Checker checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(configuration);
            Path file = directory.resolve("Example.java");
            Files.writeString(file, source);
            assertTrue(checker.process(List.of(file.toFile())) > 0);
            String actual = orderStep().formatter().format("Example.java", source);
            assertEquals(expected, actual);
            Files.writeString(file, actual);
            assertEquals(0, checker.process(List.of(file.toFile())));
            assertEquals(actual, orderStep().formatter().format("Example.java", actual));
        } finally {
            checker.destroy();
        }
    }
}
