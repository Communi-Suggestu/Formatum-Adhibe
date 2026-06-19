package com.communi.suggestu.formatum.adhibe.plugin;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
                "import test.Helper;",
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

                import test.Helper;

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
                "import test.Helper;",
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


