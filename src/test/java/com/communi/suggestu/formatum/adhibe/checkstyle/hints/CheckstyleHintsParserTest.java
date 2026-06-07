package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckstyleHintsParserTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesHintsYaml() throws IOException {
        Path file = tempDir.resolve("checkstyle-immaculate-hints.yaml");
        Files.writeString(file, """
                hints:
                  - id: trailing-space-fix
                    moduleName: RegexpSingleline
                    messageContains: trailing whitespace
                    find: "\\\\s+$"
                    replace: ""
                    multiline: true
                    mode: SAFE
                  - id: assert-fix
                    modulePath: Checker[0]/TreeWalker[0]/MatchXpath[6]
                    find: "\\bassert\\b"
                    replace: "// assert"
                    dotall: false
                    mode: AGGRESSIVE
                """);

        CheckstyleHintsFile hints = new CheckstyleHintsParser().parse(file);

        assertEquals(2, hints.hints().size());
        assertEquals("trailing-space-fix", hints.hints().getFirst().id());
        assertEquals(FixMode.AGGRESSIVE, hints.hints().get(1).mode());
    }
}

