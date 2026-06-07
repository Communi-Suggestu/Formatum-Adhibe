package com.communi.suggestu.formatum.adhibe.checkstyle.classification;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleConfigParser;
import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

class RuleMatrixGeneratorTest {
    @Test
    void generatesMarkdownMatrixForProvidedConfig() {
        CheckstyleModuleSpec root = new CheckstyleConfigParser().parse(Path.of("libs/checkstyle/checkstyle.xml"));
        RuleMatrixGenerator generator = new RuleMatrixGenerator(new CheckstyleRuleClassifier());

        String matrix = generator.generate(root);

        assertTrue(matrix.contains("# Rule Matrix"));
        assertTrue(matrix.contains("ImportOrder"));
        assertTrue(matrix.contains("MatchXpath"));
        assertTrue(matrix.contains("Total rules:"));
    }
}

