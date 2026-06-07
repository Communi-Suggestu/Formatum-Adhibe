package com.communi.suggestu.formatum.adhibe.checkstyle.classification;

import com.communi.suggestu.formatum.adhibe.checkstyle.config.CheckstyleModuleSpec;

import java.util.Map;

public final class CheckstyleRuleClassifier {
    private static final Map<String, RuleClassification> RULES = Map.ofEntries(
            Map.entry("NewlineAtEndOfFile", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Deterministic newline normalization.")),
            Map.entry("RegexpSingleline", new RuleClassification(RuleSupport.AUTO_FIX_WITH_HINTS, "Regex-only checks need explicit rewrite hints.")),
            Map.entry("RegexpMultiline", new RuleClassification(RuleSupport.AUTO_FIX_WITH_HINTS, "Multiline regex rules need rewrite hints or templates.")),
            Map.entry("RegexpSinglelineJava", new RuleClassification(RuleSupport.AUTO_FIX_WITH_HINTS, "Java regex checks may need context-aware replacement hints.")),
            Map.entry("AvoidStarImport", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Can be fixed by replacing star imports with explicit imports when resolvable.")),
            Map.entry("IllegalImport", new RuleClassification(RuleSupport.CHECK_ONLY, "Safe replacement target is project-specific.")),
            Map.entry("RedundantImport", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Redundant import removal is deterministic.")),
            Map.entry("UnusedImports", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Unused import removal is deterministic.")),
            Map.entry("ImportOrder", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Import sorting/grouping is deterministic from configured groups.")),
            Map.entry("LeftCurly", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Brace placement is deterministic.")),
            Map.entry("RightCurly", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Brace placement is deterministic.")),
            Map.entry("NeedBraces", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Single-statement blocks can be wrapped deterministically.")),
            Map.entry("EmptyLineSeparator", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Blank line insertion/removal is deterministic.")),
            Map.entry("OperatorWrap", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Operator wrapping is deterministic by style option.")),
            Map.entry("SeparatorWrap", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Separator wrapping is deterministic by style option.")),
            Map.entry("Indentation", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Indentation depth can be normalized deterministically.")),
            Map.entry("ParenPad", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Parenthesis spacing is deterministic.")),
            Map.entry("NoWhitespaceBefore", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Whitespace removal before tokens is deterministic.")),
            Map.entry("NoWhitespaceAfter", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Whitespace removal after tokens is deterministic.")),
            Map.entry("WhitespaceAfter", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Whitespace insertion after tokens is deterministic.")),
            Map.entry("WhitespaceAround", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Whitespace normalization around tokens is deterministic.")),
            Map.entry("SingleSpaceSeparator", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Single-space normalization is deterministic.")),
            Map.entry("GenericWhitespace", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Generic token whitespace normalization is deterministic.")),
            Map.entry("CommentsIndentation", new RuleClassification(RuleSupport.CHECK_ONLY, "Comment intent can be ambiguous without semantic context.")),
            Map.entry("ArrayTypeStyle", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Array bracket style can be normalized deterministically.")),
            Map.entry("DefaultComesLast", new RuleClassification(RuleSupport.CHECK_ONLY, "Switch case reordering can alter behavior in edge cases.")),
            Map.entry("SimplifyBooleanExpression", new RuleClassification(RuleSupport.CHECK_ONLY, "Expression simplification can change semantics in corner cases.")),
            Map.entry("SimplifyBooleanReturn", new RuleClassification(RuleSupport.CHECK_ONLY, "Return expression simplification can change readability policy.")),
            Map.entry("StringLiteralEquality", new RuleClassification(RuleSupport.CHECK_ONLY, "Requires semantic replacement with equals; preserve null safety.")),
            Map.entry("ModifierOrder", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Modifier ordering is deterministic.")),
            Map.entry("RedundantModifier", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Redundant modifier removal is deterministic.")),
            Map.entry("AnnotationLocation", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Annotation placement is deterministic.")),
            Map.entry("MissingOverride", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "@Override insertion is deterministic when override is confirmed.")),
            Map.entry("EmptyCatchBlock", new RuleClassification(RuleSupport.CHECK_ONLY, "Fix intent is policy-specific (log/rethrow/comment).")),
            Map.entry("OuterTypeFilename", new RuleClassification(RuleSupport.CHECK_ONLY, "Fix requires file rename and build-tool coordination.")),
            Map.entry("PackageDeclaration", new RuleClassification(RuleSupport.CHECK_ONLY, "Adding package declaration safely needs project/package resolution.")),
            Map.entry("PackageName", new RuleClassification(RuleSupport.CHECK_ONLY, "Renaming packages impacts file paths and references.")),
            Map.entry("JavadocParagraph", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Javadoc paragraph spacing normalization is deterministic.")),
            Map.entry("JavadocStyle", new RuleClassification(RuleSupport.CHECK_ONLY, "Natural-language formatting is not always deterministic.")),
            Map.entry("AtclauseOrder", new RuleClassification(RuleSupport.AUTO_FIX_SAFE, "Javadoc tag reordering is deterministic.")),
            Map.entry("MatchXpath", new RuleClassification(RuleSupport.CHECK_ONLY, "Xpath detections require per-rule semantic fix strategies.")),
            Map.entry("SuppressionCommentFilter", new RuleClassification(RuleSupport.CHECK_ONLY, "Configuration-only module; no source rewrite."))
    );

    public RuleClassification classify(CheckstyleModuleSpec module) {
        return RULES.getOrDefault(
                module.name(),
                new RuleClassification(RuleSupport.UNSUPPORTED, "No mapping implemented for this module yet.")
        );
    }
}

