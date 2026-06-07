package com.communi.suggestu.formatum.adhibe.checkstyle.planning;

public record LeadingWhitespaceToTabsGeneratedStepSpec(
        String name,
        String sourceModulePath,
        int tabWidth
) implements GeneratedImmaculateStepSpec {
    @Override
    public GeneratedStepKind kind() {
        return GeneratedStepKind.CONVERT_LEADING_SPACES_TO_TABS;
    }
}

