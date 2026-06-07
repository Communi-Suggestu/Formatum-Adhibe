package com.communi.suggestu.formatum.adhibe.checkstyle.classification;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CheckstyleRuleClassifierTest {
    @Test
    void marksUnknownModulesAsUnsupported() {
        CheckstyleModuleSpec module = new CheckstyleModuleSpec(
                "SomeFutureRule",
                "Checker[0]/SomeFutureRule[0]",
                1,
                1,
                Map.of(),
                List.of(),
                List.of(),
                List.of()
        );

        RuleClassification classification = new CheckstyleRuleClassifier().classify(module);
        assertEquals(RuleSupport.UNSUPPORTED, classification.support());
    }
}

