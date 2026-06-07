package com.communi.suggestu.formatum.adhibe.plugin;

import org.gradle.testkit.runner.GradleRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import static org.gradle.testkit.runner.TaskOutcome.SUCCESS;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class FormatumAdhibePluginIntegrationTest {
    private static final Path REPOSITORY_CHECKSTYLE_CONFIG = Path.of("libs/checkstyle/checkstyle.xml");

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

        var check = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateCheck", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(check.task(":javaImmaculateCheck")).getOutcome());
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
        Files.writeString(projectDirectory.resolve("checkstyle-immaculate-hints.yaml"), buildMatchXpathHintsYaml());

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

        String formatted = Files.readString(sourceFile);
        assertTrue(formatted.contains("\tpublic static void run() {"));
        assertTrue(formatted.contains("import static java.util.Collections.emptyList;\n\nimport java.util.Map;\n\nimport javax.swing.JButton;\n\nimport test.Helper;"));

        var check = GradleRunner.create()
                .withProjectDir(projectDirectory.toFile())
                .withPluginClasspath()
                .withArguments("javaImmaculateCheck", "--stacktrace")
                .build();

        assertEquals(SUCCESS, Objects.requireNonNull(check.task(":javaImmaculateCheck")).getOutcome());
    }

    private static String buildMatchXpathHintsYaml() {
        List<String> lines = new ArrayList<>();
        lines.add("hints:");
        for (int index = 0; index <= 10; index++) {
            lines.add("  - id: compliance-matchxpath-" + index);
            lines.add("    modulePath: Checker[0]/TreeWalker[0]/MatchXpath[" + index + "]");
            lines.add("    find: '(?!)'");
            lines.add("    replace: ''");
            lines.add("    mode: AGGRESSIVE");
        }
        lines.add("");
        return String.join("\n", lines);
    }
}


