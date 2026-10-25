package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JavaImportUsageAnalyzerTest {
    @ParameterizedTest
    @ValueSource(strings = {
            "{@link List}",
            "{@linkplain List a list}",
            "@see List",
            "{@link List#size()}",
            "{@link List#missingMethod()}"
    })
    void retainsTypesReferencedInJavadoc(String reference) {
        var usage = analyze("""
                import java.util.List;
                import java.util.Set;
                /** %s */
                class Example {}
                """.formatted(reference));

        assertTrue(usage.usesTypeImport("java.util.List"));
        assertFalse(usage.usesTypeImport("java.util.Set"));
    }

    @Test
    void scansDocumentationOnMembersAndNestedTypes() {
        var usage = analyze("""
                import java.util.List;
                import java.util.Map;
                import java.util.Set;
                import java.util.Collection;
                import java.io.IOException;
                import java.io.UncheckedIOException;
                class Example {
                    /** {@link List} */
                    int field;
                    /** {@link Map} */
                    Example() {}
                    /** {@link Set} */
                    class Nested {}
                    /**
                     * {@link Collection}
                     * @throws IOException when reading fails
                     * @exception UncheckedIOException when reading fails
                     */
                    void run() {}
                }
                """);

        for (String type : Set.of("java.util.List", "java.util.Map", "java.util.Set", "java.util.Collection",
                "java.io.IOException", "java.io.UncheckedIOException")) {
            assertTrue(usage.usesTypeImport(type), type);
        }
    }

    @Test
    void scansPackageDocumentation() {
        var result = JavaImportUsageAnalyzer.analyze("""
                /** {@link List} */
                package example;
                import java.util.List;
                """, "example/package-info.java", Set.of(), Set.of());

        assertFalse(result.hasErrors(), () -> String.join("\n", result.diagnostics()));
        assertTrue(result.usage().orElseThrow().usesTypeImport("java.util.List"));
    }

    @Test
    void tracksOnlyWrittenTypesInMethodReferenceParameters() {
        var usage = analyze("""
                import java.util.Arrays;
                import java.util.List;
                import java.util.Set;
                import java.nio.file.Path;
                import java.nio.file.Files;
                import java.util.stream.Stream;
                /**
                 * {@link Arrays#asList(Object[])}
                 * {@link Arrays#stream(Object[])}
                 * {@link Files#readString(Path)}
                 * {@link #accept(int, boolean, List[], Set...)}
                 */
                class Example {
                    void accept(int count, boolean flag, java.util.List[] lists, java.util.Set... sets) {}
                }
                """);

        for (String type : Set.of("java.util.Arrays", "java.util.List", "java.util.Set",
                "java.nio.file.Path", "java.nio.file.Files")) {
            assertTrue(usage.usesTypeImport(type), type);
        }
        assertFalse(usage.usesTypeImport("java.util.stream.Stream"));
    }

    @Test
    void distinguishesNestedTypesFromFullyQualifiedReferences() {
        var usage = analyze("""
                import java.util.Map;
                import java.util.Map.Entry;
                import java.util.List;
                /**
                 * {@link Map.Entry}
                 * {@link java.util.List}
                 */
                class Example {}
                """);

        assertTrue(usage.usesTypeImport("java.util.Map"));
        assertFalse(usage.usesTypeImport("java.util.Map.Entry"));
        assertFalse(usage.usesTypeImport("java.util.List"));
    }

    @Test
    void retainsDirectlyImportedNestedTypes() {
        var usage = analyze("""
                import java.util.Map.Entry;
                /** {@link Entry} */
                class Example {}
                """);

        assertTrue(usage.usesTypeImport("java.util.Map.Entry"));
    }

    @Test
    void ignoresProseLabelsCodeAndMemberNames() {
        var usage = analyze("""
                import java.util.List;
                import java.util.Set;
                import java.util.Map;
                import java.util.Collection;
                /**
                 * List and Set are prose, {@code Map} is code.
                 * {@link java.lang.String Collection}
                 * {@link #List()}
                 * @see "List"
                 */
                class Example {
                    void List() {}
                    // {@link Set}
                    /* {@link Map} */
                    String text = "{@link Collection}";
                }
                """);

        for (String type : Set.of("java.util.List", "java.util.Set", "java.util.Map", "java.util.Collection")) {
            assertFalse(usage.usesTypeImport(type), type);
        }
    }

    @Test
    void includesJavadocTypesInWildcardExpansion() {
        var usage = analyze("""
                import java.util.*;
                /** {@link Map.Entry} and {@link List} */
                class Example {}
                """);

        assertEquals(Set.of("java.util.Map", "java.util.List"), usage.usedTopLevelTypesInPackage("java.util"));
    }

    @Test
    void explicitImportsTakePrecedenceOverWildcardImports() {
        var usage = analyze("""
                import java.awt.*;
                import java.util.List;
                /** {@link List} */
                class Example {}
                """);

        assertTrue(usage.usesTypeImport("java.util.List"));
        assertTrue(usage.usedTopLevelTypesInPackage("java.awt").isEmpty());
    }

    @Test
    void localTypesAndTypeParametersShadowWildcardImports() {
        var usage = analyze("""
                import java.util.*;
                /** {@link List} */
                class Example<List> {
                    /** {@link Map} */
                    void run() {}
                    class Map {}
                }
                """);

        assertFalse(usage.usesTypeImport("java.util.List"));
        assertFalse(usage.usesTypeImport("java.util.Map"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"import static java.util.Map.Entry;", "import static java.util.Map.*;"})
    void retainsStaticallyImportedNestedTypes(String importLine) {
        var usage = analyze("""
                %s
                /** {@link Entry} */
                class Example {}
                """.formatted(importLine));

        assertTrue(usage.usesStaticMemberImport("java.util.Map", "Entry"));
    }

    private static JavaImportUsageAnalyzer.ImportUsage analyze(String source) {
        var result = JavaImportUsageAnalyzer.analyze(source, "Example.java", Set.of(), Set.of());
        assertFalse(result.hasErrors(), () -> String.join("\n", result.diagnostics()));
        return result.usage().orElseThrow();
    }
}
