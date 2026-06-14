package com.communi.suggestu.formatum.adhibe.checkstyle.planning;

public sealed interface GeneratedImmaculateStepSpec permits ImportOrderGeneratedStepSpec, LeadingWhitespaceToTabsGeneratedStepSpec, SimpleGeneratedStepSpec {
    String name();

    String sourceModulePath();

    GeneratedStepKind kind();

    /**
     * The checkstyle message associated with this step, or {@code null} if not available.
     */
    String message();
}

