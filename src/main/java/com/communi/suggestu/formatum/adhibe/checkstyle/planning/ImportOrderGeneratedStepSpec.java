package com.communi.suggestu.formatum.adhibe.checkstyle.planning;

import java.util.List;

public record ImportOrderGeneratedStepSpec(
        String name,
        String sourceModulePath,
        List<String> groups,
        boolean separated,
        String option,
        boolean sortStaticImportsAlphabetically
) implements GeneratedImmaculateStepSpec {
    @Override
    public GeneratedStepKind kind() {
        return GeneratedStepKind.ORDER_IMPORTS;
    }
}

