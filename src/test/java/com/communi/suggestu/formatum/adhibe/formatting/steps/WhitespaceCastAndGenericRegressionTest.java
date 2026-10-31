package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WhitespaceCastAndGenericRegressionTest {
    @Test
    void insertsWhitespaceAfterPrimitiveAndReferenceCastsOnlyWhenConfigured() {
        var objects = ProjectBuilder.builder().build().getObjects();
        var defaultStep = objects.newInstance(CheckstyleWhitespaceAfterStep.class, "whitespaceAfter");
        String source = "class Example { Object value; void run() { int i=(int)value; double d=(double)(i); "
                + "String s=(String)value; String literal=\"(int)value\"; /* (long)value */ } }\n";
        String expected = "class Example { Object value; void run() { int i=(int) value; double d=(double) (i); "
                + "String s=(String) value; String literal=\"(int)value\"; /* (long)value */ } }\n";
        assertEquals(expected, defaultStep.formatter().format("Example.java", source));

        var commaOnly = objects.newInstance(CheckstyleWhitespaceAfterStep.class, "commaOnly");
        commaOnly.getTokens().set(List.of("COMMA"));
        assertEquals(source, commaOnly.formatter().format("Example.java", source));
    }

    @Test
    void recoversSpacedInstanceofGenericsWithoutChangingComparisonsOrLiterals() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleGenericWhitespaceStep.class, "genericWhitespace");
        String source = "class Example { boolean run(Object value, int a, int b) { "
                + "if (value instanceof final IWithModeItem < ? > item) return a < b; "
                + "return \"IWithModeItem < ? >, a , b\".length() > 0; } }\n";
        String expected = "class Example { boolean run(Object value, int a, int b) { "
                + "if (value instanceof final IWithModeItem<?> item) return a < b; "
                + "return \"IWithModeItem < ? >, a , b\".length() > 0; } }\n";
        assertEquals(expected, step.formatter().format("Example.java", source));
        assertEquals(expected, step.formatter().format("Example.java", expected));
    }

    @Test
    void sequentialWhitespaceStepsPassCheckstyleAndAreIdempotent() throws Exception {
        var objects = ProjectBuilder.builder().build().getObjects();
        var whitespaceAfter = objects.newInstance(CheckstyleWhitespaceAfterStep.class, "whitespaceAfter");
        var whitespaceAround = objects.newInstance(CheckstyleWhitespaceAroundStep.class, "whitespaceAround");
        var genericWhitespace = objects.newInstance(CheckstyleGenericWhitespaceStep.class, "genericWhitespace");
        String source = "class Example {\n"
                + "    int cast(Object value) { int first=(int)value; double second=(double)(first); "
                + "String third=(String)value; return first; }\n"
                + "    boolean match(Object value, int a, int b) {\n"
                + "        if (value instanceof final IWithModeItem<?> item) return item.enabled()&&a<b;\n"
                + "        if (value instanceof @Deprecated final IWithModeItem<?> item) return item.enabled()&&a>b;\n"
                + "        return \"IWithModeItem<?> a<b\".length()>0;\n"
                + "    }\n"
                + "}\n";
        Path file = Path.of("build", "whitespace-regression", "Example.java");
        Files.createDirectories(file.getParent());
        DefaultConfiguration checkerConfiguration = new DefaultConfiguration("Checker");
        DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
        walker.addChild(new DefaultConfiguration("WhitespaceAfter"));
        walker.addChild(new DefaultConfiguration("WhitespaceAround"));
        walker.addChild(new DefaultConfiguration("GenericWhitespace"));
        checkerConfiguration.addChild(walker);
        Checker checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(checkerConfiguration);
            Files.writeString(file, source);
            assertTrue(checker.process(List.of(file.toFile())) > 0);

            String formatted = source;
            for (int pass = 0; pass < 2; pass++) {
                formatted = whitespaceAfter.formatter().format("Example.java", formatted);
                formatted = whitespaceAround.formatter().format("Example.java", formatted);
                formatted = genericWhitespace.formatter().format("Example.java", formatted);
                Files.writeString(file, formatted);
                assertEquals(0, checker.process(List.of(file.toFile())));
                if (pass == 0) {
                    source = formatted;
                } else {
                    assertEquals(source, formatted);
                }
            }
        } finally {
            checker.destroy();
            Files.deleteIfExists(file);
            Files.deleteIfExists(file.getParent());
        }
    }
}
