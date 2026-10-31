package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckstyleJavadocStyleStepTest {
    @TempDir
    Path directory;

    private static CheckstyleJavadocStyleStep step() {
        return ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleJavadocStyleStep.class, "javadocStyle");
    }

    private int violations(String source, String fileName, Map<String, String> properties) throws Exception {
        DefaultConfiguration check = new DefaultConfiguration("JavadocStyle");
        properties.forEach(check::addProperty);
        DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
        walker.addChild(check);
        DefaultConfiguration configuration = new DefaultConfiguration("Checker");
        configuration.addChild(walker);
        Checker checker = new Checker();
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(configuration);
            Path file = directory.resolve(fileName);
            Files.writeString(file, source);
            return checker.process(List.of(file.toFile()));
        } finally {
            checker.destroy();
        }
    }

    @Test
    void repairsMultilineAndSingleLineDescriptionsButNotBlockTags() throws Exception {
        String source = "/** Represents a pattern */\npublic interface Example {\n"
                + "\t/**\n\t * Redoes the change\n\t *\n\t * @param player The player.\n\t */\n"
                + "\tvoid redo(Object player);\n"
                + "\t/**\n\t * Causes the preview to render the model,\n\t * ignoring its textures\n\t */\n"
                + "\tvoid render();\n}\n";
        String expected = source.replace("pattern */", "pattern. */")
                .replace("Redoes the change\n", "Redoes the change.\n")
                .replace("ignoring its textures\n", "ignoring its textures.\n");
        assertEquals(3, violations(source, "Example.java", Map.of()));
        String actual = step().formatter().format("Example.java", source);
        assertEquals(expected, actual);
        assertEquals(0, violations(actual, "Example.java", Map.of()));
        assertEquals(actual, step().formatter().format("Example.java", actual));
    }

    @Test
    void preservesCheckstyleExemptionsAndExistingSentenceEndings() throws Exception {
        String source = "/** Already documented! */\ninterface Example {\n"
                + "/** Are we done? */ void question();\n"
                + "/** Sentence. More details */ void sentence();\n"
                + "/** A value.<p>More details */ void html();\n"
                + "/** {@inheritDoc} */ String inherited();\n"
                + "/** {@return the value} */ String value();\n"
                + "/** @return the value */ String tagOnly();\n"
                + "/** */ void empty();\n}\n";
        assertEquals(0, violations(source, "Example.java", Map.of()));
        assertEquals(source, step().formatter().format("Example.java", source));
    }

    @Test
    void ignoresLiteralsOrdinaryCommentsAndLocalDeclarations() throws Exception {
        String source = "/** Example. */ class Example {\n"
                + "String text = \"/** not a real Javadoc */\";\n"
                + "String block = \"\"\"\n/** not Javadoc either */\n\"\"\";\n"
                + "// /** not a Javadoc */\n/* ordinary comment */\n"
                + "void run() {\n/** Local description */ int local = 1;\n"
                + "/** Local class description */ class Local {}\n}\n}\n";
        assertEquals(0, violations(source, "Example.java", Map.of()));
        assertEquals(source, step().formatter().format("Example.java", source));
    }

    @Test
    void respectsScopeAndExcludedScopeIncludingSurroundingTypes() throws Exception {
        String source = "/** Public type */\npublic class Example {\n"
                + "/** Public method */\npublic void visible() {}\n"
                + "/** Protected method */\nprotected void inherited() {}\n"
                + "/** Package method */\nvoid internal() {}\n"
                + "/** Private method */\nprivate void hidden() {}\n"
                + "/** Private type */\nprivate class Hidden {\n"
                + "/** Public member */\npublic void member() {}\n}\n}\n";
        var step = step();
        step.getScope().set("protected");
        step.getExcludeScope().set("public");
        String actual = step.formatter().format("Example.java", source);
        assertEquals(source.replace("Protected method */", "Protected method. */"), actual);
        assertEquals(0, violations(actual, "Example.java", Map.of("scope", "protected", "excludeScope", "public")));
    }

    @Test
    void respectsDisabledSentenceCheckAndConfiguredTokenSelection() throws Exception {
        String source = "/** Type description */ public class Example {\n/** Method description */ void run() {}\n}\n";
        var step = step();
        step.getCheckFirstSentence().set(false);
        assertEquals(source, step.formatter().format("Example.java", source));
        step.getCheckFirstSentence().set(true);
        step.getTokens().set(List.of("CLASS_DEF"));
        String actual = step.formatter().format("Example.java", source);
        assertEquals(source.replace("Type description */", "Type description. */"), actual);
        assertEquals(0, violations(actual, "Example.java", Map.of("tokens", "CLASS_DEF")));
    }

    @Test
    void respectsCustomSentencePatternWithoutInventingPunctuation() throws Exception {
        String source = "/** Question? */ class Example {}\n";
        var step = step();
        step.getEndOfSentenceFormat().set("\\.$");
        String actual = step.formatter().format("Example.java", source);
        assertEquals("/** Question?. */ class Example {}\n", actual);
        assertEquals(0, violations(actual, "Example.java", Map.of("endOfSentenceFormat", "\\.$")));
        step.getEndOfSentenceFormat().set("END$");
        assertEquals(source, step.formatter().format("Example.java", source));
    }

    @Test
    void repairsEnumConstantsAnnotationsAndPackageInfo() throws Exception {
        String source = "/** A mode */ enum Example {\n/** First mode */ FIRST;\n"
                + "/** Constructor */ Example() {}\n}\n"
                + "/** Annotation */ @interface Marker {\n/** Value */ String value() default \"\";\n}\n";
        assertTrue(violations(source, "Example.java", Map.of()) > 0);
        String actual = step().formatter().format("Example.java", source);
        assertEquals(0, violations(actual, "Example.java", Map.of()));
        String packageInfo = "/** A package */\n@Deprecated\npackage example;\n";
        String formatted = step().formatter().format("package-info.java", packageInfo);
        assertEquals(packageInfo.replace("A package */", "A package. */"), formatted);
        assertEquals(0, violations(formatted, "package-info.java", Map.of()));
    }

    @Test
    void respectsImplicitEnumAndAnnotationVisibility() throws Exception {
        String source = "/** Mode. */\npublic enum Example {\n/** Constant */\nFIRST;\n"
                + "/** Constructor */\nExample() {}\n}\n";
        var step = step();
        step.getScope().set("public");
        String actual = step.formatter().format("Example.java", source);
        assertEquals(source.replace("Constant */", "Constant. */"), actual);
        assertEquals(0, violations(actual, "Example.java", Map.of("scope", "public")));
        String annotation = "/** Annotation. */\npublic @interface Example {\n/** Value */\nString value();\n}\n";
        step.getTokens().set(List.of("ANNOTATION_FIELD_DEF"));
        actual = step.formatter().format("Example.java", annotation);
        assertEquals(annotation.replace("Value */", "Value. */"), actual);
        assertEquals(0, violations(actual, "Example.java", Map.of("scope", "public", "tokens", "ANNOTATION_FIELD_DEF")));
    }

    @Test
    void repairsRecordsCompactConstructorsAndAnnotatedMethods() throws Exception {
        String source = "/** Record description */\npublic record Example(int value) {\n"
                + "/** Compact constructor */\npublic Example {}\n"
                + "/** Gets the value */\n@Deprecated\npublic int get() { return value; }\n}\n";
        String expected = source.replace("Record description */", "Record description. */")
                .replace("Compact constructor */", "Compact constructor. */")
                .replace("Gets the value */", "Gets the value. */");
        String actual = step().formatter().format("Example.java", source);
        assertEquals(expected, actual);
        assertEquals(0, violations(actual, "Example.java", Map.of()));
    }

    @Test
    void htmlAndEmptyDescriptionProblemsRemainCheckOnly() throws Exception {
        String source = "/** Description. <b>Unclosed */ class Example {\n/** */ void run() {}\n}\n";
        assertEquals(source, step().formatter().format("Example.java", source));
        assertTrue(violations(source, "Example.java", Map.of("checkEmptyJavadoc", "true")) > 0);
    }

    @Test
    void punctuationInsideTagSectionDoesNotHideMissingDescriptionPeriod() throws Exception {
        String source = "/**\n * Ends the profiling of a given profiler\n * @param profiler The profiler.\n"
                + " * @return The result.\n */\ninterface Example {}\n";
        String actual = step().formatter().format("Example.java", source);
        assertEquals(source.replace("given profiler\n", "given profiler.\n"), actual);
        assertEquals(0, violations(actual, "Example.java", Map.of()));
    }
}
