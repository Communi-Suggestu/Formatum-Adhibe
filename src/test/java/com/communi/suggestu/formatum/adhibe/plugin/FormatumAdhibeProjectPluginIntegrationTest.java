package com.communi.suggestu.formatum.adhibe.plugin;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FormatumAdhibeProjectPluginIntegrationTest
{
    private static final Path REPOSITORY_CHECKSTYLE_CONFIG = Path.of("libs/checkstyle/checkstyle.xml");
    private static final Path REPOSITORY_HINTS_FILE = Path.of("libs/checkstyle/checkstyle-immaculate-hints.yaml");

    @TempDir
    Path projectDirectory;

    @Test
    void pluginGeneratesAndAppliesDeterministicImmaculateSteps() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'it-project'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.communi.suggestu.formatum.adhibe'
                }

                repositories {
                    mavenCentral()
                }

                immaculate {
                    workflows.register('java') {
                        java()
                        checkstyle('checkstyle') {
                            checkstyleConfig = file('checkstyle.xml')
                        }
                    }
                }
                """);
        Files.writeString(projectDirectory.resolve("checkstyle.xml"), """
                <?xml version="1.0"?>
                <!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN" "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
                <module name="Checker">
                    <property name="tabWidth" value="4"/>
                    <module name="NewlineAtEndOfFile"/>
                    <module name="RegexpSingleline">
                        <property name="format" value="\\s+$"/>
                    </module>
                    <module name="RegexpMultiline">
                        <property name="format" value="\\n[\\t ]*\\r?\\n[\\t ]*\\r?\\n"/>
                    </module>
                    <module name="TreeWalker">
                        <module name="ImportOrder">
                            <property name="groups" value="java,javax,*,net.fabricmc"/>
                            <property name="separated" value="true"/>
                            <property name="option" value="top"/>
                            <property name="sortStaticImportsAlphabetically" value="true"/>
                        </module>
                        <module name="RegexpSinglelineJava">
                            <property name="format" value="^\\t* ([^*]|\\*[^ /])"/>
                        </module>
                    </module>
                </module>
                """);

        Path sourceFile = projectDirectory.resolve("src/main/java/test/Example.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, String.join("\n",
                "package test;",
                "",
                "import javax.swing.JButton;",
                "import static java.util.Collections.emptyList;",
                "import java.util.Map;",
                "",
                "public class Example {",
                "",
                "    public static void run() {    ",
                "",
                "        Map<String, String> value = Map.of();",
                "    }",
                "}",
                ""
        ));

        var apply = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateApply", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(apply.task(":javaImmaculateApply")).getOutcome());

        String formatted = Files.readString(sourceFile);
        assertEquals("""
                package test;

                import static java.util.Collections.emptyList;

                import java.util.Map;

                import javax.swing.JButton;

                public class Example {

                	public static void run() {

                		Map<String, String> value = Map.of();
                	}
                }
                """, formatted);

    }

    @Test
    void pluginAppliesHintDrivenFixesInAggressiveMode() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'it-project'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.communi.suggestu.formatum.adhibe'
                }

                immaculate {
                    workflows.register('java') {
                        java()
                        checkstyle('checkstyle') {
                            checkstyleConfig = file('checkstyle.xml')
                            hintsFile = file('checkstyle-immaculate-hints.yaml')
                            fixMode = com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode.AGGRESSIVE
                            failOnUnmatchedHints = true
                        }
                    }
                }
                """);

        Files.writeString(projectDirectory.resolve("checkstyle.xml"), """
                <?xml version="1.0"?>
                <!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN" "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
                <module name="Checker">
                    <module name="TreeWalker">
                        <module name="MatchXpath">
                            <property name="query" value="//LITERAL_ASSERT"/>
                            <message key="matchxpath.match" value="Avoid using 'assert'."/>
                        </module>
                    </module>
                </module>
                """);

        Files.writeString(projectDirectory.resolve("checkstyle-immaculate-hints.yaml"), """
                hints:
                  - id: assert-to-comment
                    moduleName: MatchXpath
                    messageContains: "Avoid using 'assert'."
                    find: '\\bassert\\b'
                    replace: "// assert"
                    mode: AGGRESSIVE
                """);

        Path sourceFile = projectDirectory.resolve("src/main/java/test/Example.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package test;

                public class Example {
                    public static void run() {
                        assert true;
                    }
                }
                """);

        var apply = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateApply", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(apply.task(":javaImmaculateApply")).getOutcome());
        assertTrue(Files.readString(sourceFile).contains("// assert true;"));
    }

    @Test
    void pluginUsesRepositoryCheckstyleConfigAsComplianceTarget() throws IOException {
        Files.copy(REPOSITORY_CHECKSTYLE_CONFIG, projectDirectory.resolve("checkstyle.xml"));
        Files.copy(REPOSITORY_HINTS_FILE, projectDirectory.resolve("checkstyle-immaculate-hints.yaml"));

        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'it-project'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.communi.suggestu.formatum.adhibe'
                }

                immaculate {
                    workflows.register('java') {
                        java()
                        checkstyle('checkstyle') {
                            checkstyleConfig = file('checkstyle.xml')
                            hintsFile = file('checkstyle-immaculate-hints.yaml')
                            fixMode = com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode.AGGRESSIVE
                            failOnUnmatchedHints = true
                        }
                    }
                }
                """);

        Path sourceFile = projectDirectory.resolve("src/main/java/test/Example.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, String.join("\n",
                "package test;",
                "",
                "import javax.swing.JButton;",
                "import static java.util.Collections.emptyList;",
                "import java.util.Map;",
                "",
                "public class Example {",
                "",
                "    public static void run() {    ",
                "",
                "        Map<String, String> value = Map.of();",
                "    }",
                "}",
                ""
        ));

        var apply = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateApply", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(apply.task(":javaImmaculateApply")).getOutcome());
    }

    @Test
    void pluginKeepsComplexWrappedCodeStructureWhenApplyingRepositoryConfig() throws IOException {
        Files.copy(REPOSITORY_CHECKSTYLE_CONFIG, projectDirectory.resolve("checkstyle.xml"));
        Files.copy(REPOSITORY_HINTS_FILE, projectDirectory.resolve("checkstyle-immaculate-hints.yaml"));

        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'it-project'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.communi.suggestu.formatum.adhibe'
                }

                immaculate {
                    workflows.register('java') {
                        java()
                        checkstyle('checkstyle') {
                            checkstyleConfig = file('checkstyle.xml')
                            hintsFile = file('checkstyle-immaculate-hints.yaml')
                            fixMode = com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode.AGGRESSIVE
                            failOnUnmatchedHints = true
                        }
                    }
                }
                """);

        Path sourceFile = projectDirectory.resolve("src/main/java/test/Example.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package test;

                import java.util.List;

                public class Example {
                	private Example()

                	{
                	}

                	public static List<String> compress(
                	  final List<String> input)

                	{
                		if (input != null)
                			input.forEach(
                			  value -> {
                				  if (value != null)
                					  if (value.length() > 1
                						|| value.startsWith("x"))
                						  System.out.println(
                							value
                						  );
                			  }
                			);

                		return input;
                	}
                }
                """);

        var apply = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateApply", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(apply.task(":javaImmaculateApply")).getOutcome());

        String formatted = Files.readString(sourceFile);
        assertTrue(formatted.contains("public static List<String> compress("), formatted);
        assertTrue(formatted.matches("(?s).*public static List<String> compress\\(\\n\\s*final List<String> input\\).*"), formatted);
        assertTrue(formatted.contains("if (value.length()"), formatted);
        assertTrue(formatted.contains("value.startsWith(\"x\")"), formatted);
        assertTrue(formatted.matches("(?s).*System\\.out\\.println\\(\\n\\s*value\\n\\s*\\);.*"), formatted);
        assertTrue(formatted.matches("(?s).*\\n\\s*return input;\\n.*"), formatted);

        // Guard against previously reported structural clobbering symptoms.
        assertFalse(formatted.contains("List <String>"), formatted);
        assertFalse(formatted.contains("\n {"), formatted);
        assertFalse(formatted.contains("\n);\n\n\t\treturn input;"), formatted);
    }

    @Test
    void pluginKeepsIndentationForWrappedMethodChainsAndClosingBraces() throws IOException {
        Files.copy(REPOSITORY_CHECKSTYLE_CONFIG, projectDirectory.resolve("checkstyle.xml"));
        Files.copy(REPOSITORY_HINTS_FILE, projectDirectory.resolve("checkstyle-immaculate-hints.yaml"));

        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'it-project'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.communi.suggestu.formatum.adhibe'
                }

                immaculate {
                    workflows.register('java') {
                        java()
                        checkstyle('checkstyle') {
                            checkstyleConfig = file('checkstyle.xml')
                            hintsFile = file('checkstyle-immaculate-hints.yaml')
                            fixMode = com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode.AGGRESSIVE
                            failOnUnmatchedHints = true
                        }
                    }
                }
                """);

        Path sourceFile = projectDirectory.resolve("src/main/java/test/Example.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, String.join("\n",
                "package test;",
                "",
                "class Example {",
                "\tvoid run() {",
                "\t\tStringBuilder builder = new StringBuilder();",
                "\t\tbuilder",
                "\t\t\t.append(\" t\")",
                "\t\t\t.append(\"s \")",
                "\t\t\t.toString();",
                "\t}",
                "}",
                ""
        ));

        var apply = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateApply", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(apply.task(":javaImmaculateApply")).getOutcome());

        String formatted = Files.readString(sourceFile);
        assertTrue(formatted.contains("\n\t\t\t.append(\" t\")"), formatted);
        assertTrue(formatted.contains("\n\t\t\t.append(\"s \")"), formatted);
        assertTrue(formatted.contains("\n\t\t\t.toString();"), formatted);
        assertTrue(formatted.contains("\n\t}\n}"), formatted);

        // Guard against indentation-stripping regression.
        assertFalse(formatted.contains("\n.pattern("), formatted);
        assertFalse(formatted.contains("\n }\n }"), formatted);
    }

    @Test
    void pluginKeepsIndentationInsideAnonymousConsumerAcceptMethod() throws IOException {
        Files.copy(REPOSITORY_CHECKSTYLE_CONFIG, projectDirectory.resolve("checkstyle.xml"));
        Files.copy(REPOSITORY_HINTS_FILE, projectDirectory.resolve("checkstyle-immaculate-hints.yaml"));

        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'it-project'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.communi.suggestu.formatum.adhibe'
                }

                immaculate {
                    workflows.register('java') {
                        java()
                        checkstyle('checkstyle') {
                            checkstyleConfig = file('checkstyle.xml')
                            hintsFile = file('checkstyle-immaculate-hints.yaml')
                            fixMode = com.communi.suggestu.formatum.adhibe.checkstyle.hints.FixMode.AGGRESSIVE
                            failOnUnmatchedHints = true
                        }
                    }
                }
                """);

        Path sourceFile = projectDirectory.resolve("src/main/java/test/Example.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, String.join("\n",
                "package test;",
                "",
                "import java.util.function.Consumer;",
                "",
                "class Example {",
                "\tvoid run()",
                "",
                "\t{",
                "\t\tConsumer<String> consumer = new Consumer<>()",
                "",
                "\t\t{",
                "\t\t\t@Override",
                "\t\t\tpublic void accept(final String value)",
                "",
                "\t\t\t{",
                "\t\t\t\tif (value != null)",
                "",
                "\t\t\t\t{",
                "\t\t\t\t\tSystem.out.println(value);",
                "\t\t\t\t}",
                "\t\t\t}",
                "\t\t};",
                "\t\tconsumer.accept(\"x\");",
                "\t}",
                "}",
                ""
        ));

        var apply = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateApply", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(apply.task(":javaImmaculateApply")).getOutcome());

        String formatted = Files.readString(sourceFile);
        assertTrue(formatted.contains("\tvoid run() {"), formatted);
        assertTrue(formatted.contains("\t\tConsumer<String> consumer = new Consumer<>() {"), formatted);
        assertTrue(formatted.contains("\t\t\t@Override"), formatted);
        assertTrue(formatted.contains("\t\t\tpublic void accept(final String value) {"), formatted);
        assertTrue(formatted.contains("\t\t\t\tif (value != null) {"), formatted);
        assertTrue(formatted.contains("\t\t\t\t\tSystem.out.println(value);"), formatted);
        assertTrue(formatted.contains("\t\t\t\t}"), formatted);
        assertTrue(formatted.contains("\t\t\t}"), formatted);
        assertTrue(formatted.contains("\t\t};"), formatted);

        // Guard against the previous accept-block deindent/clobber pattern.
        assertFalse(formatted.contains("\npublic void accept"), formatted);
        assertFalse(formatted.contains("\nif (value != null)\n\n\t\t\t\t{"), formatted);
    }

    @Test
    void pluginRespectsSuppressionCommentFilterRanges() throws IOException {
        Files.writeString(projectDirectory.resolve("settings.gradle"), "rootProject.name = 'it-project'\n");
        Files.writeString(projectDirectory.resolve("build.gradle"), """
                plugins {
                    id 'java'
                    id 'com.communi.suggestu.formatum.adhibe'
                }

                immaculate {
                    workflows.register('java') {
                        java()
                        checkstyle('checkstyle') {
                            checkstyleConfig = file('checkstyle.xml')
                        }
                    }
                }
                """);

        Files.writeString(projectDirectory.resolve("checkstyle.xml"), """
                <?xml version="1.0"?>
                <!DOCTYPE module PUBLIC "-//Puppy Crawl//DTD Check Configuration 1.3//EN" "http://www.puppycrawl.com/dtds/configuration_1_3.dtd">
                <module name="Checker">
                    <module name="TreeWalker">
                        <module name="SuppressionCommentFilter">
                            <property name="offCommentFormat" value="CHECKSTYLE.OFF\\: ([\\w\\|]+)"/>
                            <property name="onCommentFormat" value="CHECKSTYLE.ON\\: ([\\w\\|]+)"/>
                            <property name="checkFormat" value="$1"/>
                        </module>
                        <module name="ImportOrder">
                            <property name="groups" value="java,javax,*"/>
                            <property name="separated" value="false"/>
                            <property name="option" value="under"/>
                            <property name="sortStaticImportsAlphabetically" value="true"/>
                        </module>
                        <module name="RegexpSinglelineJava">
                            <property name="format" value="^\\t* ([^*]|\\*[^ /])"/>
                        </module>
                    </module>
                </module>
                """);

        Path sourceFile = projectDirectory.resolve("src/main/java/test/Example.java");
        Files.createDirectories(sourceFile.getParent());
        Files.writeString(sourceFile, """
                package test;

                // CHECKSTYLE.OFF: ImportOrder
                import javax.swing.JButton;
                import java.util.Map;
                // CHECKSTYLE.ON: ImportOrder

                class Example {
                    void run() {
                        Map<String, String> value = Map.of();
                    }
                }
                """);

        var apply = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateApply", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(apply.task(":javaImmaculateApply")).getOutcome());

        String formatted = Files.readString(sourceFile);
        assertTrue(formatted.contains("import javax.swing.JButton;\nimport java.util.Map;"));
        assertTrue(formatted.contains("\tvoid run()"));
    }
}


