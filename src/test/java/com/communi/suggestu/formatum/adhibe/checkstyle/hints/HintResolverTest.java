package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleConfigParser;
import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HintResolverTest {
    @Test
    void resolvesHintsAgainstConfigAndMode() {
        CheckstyleModuleSpec root = new CheckstyleConfigParser().parse(Path.of("libs/checkstyle/checkstyle.xml"));
        CheckstyleHintsFile hints = new CheckstyleHintsFile(List.of(
                new CheckstyleHint("safe-hint", null, "RegexpSingleline", "trailing whitespace", "\\s+$", "", true, false, FixMode.SAFE),
                new CheckstyleHint("aggressive-hint", null, "MatchXpath", "Avoid using 'assert'.", "\\bassert\\b", "// assert", true, false, FixMode.AGGRESSIVE)
        ));

        HintResolutionResult safeResult = new HintResolver().resolve(root, hints, FixMode.SAFE);
        HintResolutionResult aggressiveResult = new HintResolver().resolve(root, hints, FixMode.AGGRESSIVE);

        assertEquals(1, safeResult.matchedHints().size());
        assertEquals(2, aggressiveResult.matchedHints().size());
    }
}

