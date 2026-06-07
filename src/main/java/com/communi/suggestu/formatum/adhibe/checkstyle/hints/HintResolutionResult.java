package com.communi.suggestu.formatum.adhibe.checkstyle.hints;

import java.util.List;

public record HintResolutionResult(List<HintResolution> matchedHints, List<CheckstyleHint> unmatchedHints) {
}

