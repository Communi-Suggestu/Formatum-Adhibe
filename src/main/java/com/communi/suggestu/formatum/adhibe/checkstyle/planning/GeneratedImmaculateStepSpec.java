package com.communi.suggestu.formatum.adhibe.checkstyle.planning;

public sealed interface GeneratedImmaculateStepSpec permits ImportOrderGeneratedStepSpec, LeadingWhitespaceToTabsGeneratedStepSpec, SimpleGeneratedStepSpec {
    String name();

    String sourceModulePath();

    GeneratedStepKind kind();
}

