package com.communi.suggestu.formatum.adhibe.formatting.steps;

import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * The Checkstyle tokens accepted by the {@code WhitespaceAround} module.
 */
enum WhitespaceAroundToken {
    ASSIGN,
    ARRAY_INIT,
    BAND,
    BAND_ASSIGN,
    BOR,
    BOR_ASSIGN,
    BSR,
    BSR_ASSIGN,
    BXOR,
    BXOR_ASSIGN,
    COLON,
    DIV,
    DIV_ASSIGN,
    DO_WHILE,
    EQUAL,
    GE,
    GT,
    LAMBDA,
    LAND,
    LCURLY,
    LE,
    LITERAL_CATCH,
    LITERAL_DO,
    LITERAL_ELSE,
    LITERAL_FINALLY,
    LITERAL_FOR,
    LITERAL_IF,
    LITERAL_RETURN,
    LITERAL_SWITCH,
    LITERAL_SYNCHRONIZED,
    LITERAL_TRY,
    LITERAL_WHILE,
    LOR,
    LT,
    MINUS,
    MINUS_ASSIGN,
    MOD,
    MOD_ASSIGN,
    NOT_EQUAL,
    PLUS,
    PLUS_ASSIGN,
    QUESTION,
    RCURLY,
    SL,
    SLIST,
    SL_ASSIGN,
    SR,
    SR_ASSIGN,
    STAR,
    STAR_ASSIGN,
    LITERAL_ASSERT,
    TYPE_EXTENSION_AND,
    WILDCARD_TYPE,
    GENERIC_START,
    GENERIC_END,
    ELLIPSIS,
    LITERAL_WHEN;

    /** Tokens Checkstyle checks when the {@code tokens} property is not configured. */
    static final Set<WhitespaceAroundToken> DEFAULTS;

    static {
        EnumSet<WhitespaceAroundToken> defaults = EnumSet.allOf(WhitespaceAroundToken.class);
        defaults.remove(ARRAY_INIT);
        defaults.remove(WILDCARD_TYPE);
        defaults.remove(GENERIC_START);
        defaults.remove(GENERIC_END);
        defaults.remove(ELLIPSIS);
        DEFAULTS = Set.copyOf(defaults);
    }

    /**
     * Resolves a configured token list. An empty list selects {@link #DEFAULTS}, matching Checkstyle. Names are
     * matched case-insensitively and unknown names are ignored.
     */
    static Set<WhitespaceAroundToken> resolve(List<String> configured) {
        EnumSet<WhitespaceAroundToken> selected = EnumSet.noneOf(WhitespaceAroundToken.class);
        boolean any = false;
        for (String name : configured) {
            String trimmed = name == null ? "" : name.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            any = true;
            try {
                selected.add(valueOf(trimmed.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
                // Not a token WhitespaceAround supports.
            }
        }
        return any ? selected : DEFAULTS;
    }
}
