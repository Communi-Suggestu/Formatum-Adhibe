package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.formatting.steps.JavaLexer.Token;
import com.communi.suggestu.formatum.adhibe.formatting.steps.JavaLexer.Type;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Classifies the tokens of a Java source file the way Checkstyle's AST does for the {@code WhitespaceAround} check.
 * <p>
 * It is a lightweight, context-aware scan rather than a full parser: a stack of brace and parenthesis frames tracks what
 * each block belongs to (method, constructor, loop, lambda, type body, array initializer, ...) so that ambiguous tokens
 * ({@code <}/{@code >} generics versus comparisons, unary versus binary {@code +}/{@code -}, {@code ?} wildcard versus
 * ternary, {@code :} in {@code case}/for-each/ternary, {@code &} bounds versus bit-and, ...) resolve like they do in
 * Checkstyle, and so that the {@code allowEmpty*} and {@code ignoreEnhancedForColon} options and Checkstyle's special
 * cases (star imports, array initializers, anonymous class ends, double brace initializers) can be honored.
 */
final class WhitespaceAroundAnalyzer {
    record Options(
            boolean allowEmptyConstructors,
            boolean allowEmptyMethods,
            boolean allowEmptyTypes,
            boolean allowEmptyLoops,
            boolean allowEmptyLambdas,
            boolean allowEmptyCatches,
            boolean allowEmptySwitchBlockStatements,
            boolean ignoreEnhancedForColon) {
        static Options defaults() {
            return new Options(false, false, false, false, false, false, false, true);
        }
    }

    /**
     * An occurrence of a Checkstyle token. {@code skipBefore}/{@code skipAfter} record the places where Checkstyle does
     * not inspect the neighbouring character for this particular occurrence.
     */
    record Site(WhitespaceAroundToken token, int start, int end, boolean skipBefore, boolean skipAfter) {
    }

    private enum BraceKind {
        ROOT, TYPE, SWITCH_BLOCK, METHOD, CTOR, STATIC_INIT, INSTANCE_INIT, LOOP, LAMBDA, CATCH, SWITCH_RULE, CASE_BLOCK,
        BLOCK, ARRAY_INIT, ANNOT_ARRAY_INIT
    }

    private enum ParenKind { IF, WHILE, FOR, SWITCH, CATCH, SYNCHRONIZED, TRY, NEW, ANNOTATION, CALL, PLAIN }

    private enum Pending { TERNARY, CASE }

    private enum Colon { NONE, TERNARY, CASE, OTHER }

    private static final class Frame {
        final boolean paren;
        final BraceKind brace;
        final ParenKind parenKind;
        final int openIdx;
        final ArrayDeque<Pending> pending = new ArrayDeque<>();
        int genericDepth;
        boolean forSemicolon;
        boolean doBody;
        int doStatements;
        boolean initLast;

        boolean typeHeaderPending;
        String pendingTypeName;
        boolean pendingEnum;
        boolean pendingAnnotation;

        String typeName;
        boolean enumType;
        boolean annotationType;
        boolean enumConstants;

        Frame(boolean paren, BraceKind brace, ParenKind parenKind, int openIdx) {
            this.paren = paren;
            this.brace = brace;
            this.parenKind = parenKind;
            this.openIdx = openIdx;
        }

        boolean isBrace(BraceKind kind) {
            return !paren && brace == kind;
        }
    }

    private static final Set<String> MODIFIERS = Set.of(
            "public", "protected", "private", "static", "final", "abstract", "synchronized", "native", "strictfp",
            "default", "transient", "volatile", "new");

    private final String text;
    private final Options options;
    private final List<Token> toks;
    private final int[] match;
    private final ParenKind[] parenKindAt;
    private final int[] parenNameAt;
    private final ArrayDeque<Frame> stack = new ArrayDeque<>();
    private final List<Site> sites = new ArrayList<>();

    private boolean prevOperand;
    private Colon lastColon = Colon.NONE;
    private boolean lastArrowRule;
    private Frame lastClosedParen;
    private BraceKind lastClosedBrace;
    private int lastClosedBraceIdx = -2;
    private boolean lastClosedBraceDo;
    private int skipUntil;

    private WhitespaceAroundAnalyzer(String text, Options options) {
        this.text = text;
        this.options = options;
        this.toks = JavaLexer.lex(text);
        this.match = new int[toks.size()];
        this.parenKindAt = new ParenKind[toks.size()];
        this.parenNameAt = new int[toks.size()];
        Arrays.fill(match, -1);
        Arrays.fill(parenNameAt, -1);
        computeMatches();
    }

    static List<Site> analyze(String text, Options options) {
        WhitespaceAroundAnalyzer analyzer = new WhitespaceAroundAnalyzer(text, options);
        analyzer.run();
        return analyzer.sites;
    }

    private void computeMatches() {
        ArrayDeque<Integer> parens = new ArrayDeque<>();
        ArrayDeque<Integer> braces = new ArrayDeque<>();
        for (int i = 0; i < toks.size(); i++) {
            Token t = toks.get(i);
            if (t.type() != Type.OP) {
                continue;
            }
            switch (t.text()) {
                case "(" -> parens.push(i);
                case "{" -> braces.push(i);
                case ")" -> {
                    if (!parens.isEmpty()) {
                        int open = parens.pop();
                        match[open] = i;
                        match[i] = open;
                    }
                }
                case "}" -> {
                    if (!braces.isEmpty()) {
                        int open = braces.pop();
                        match[open] = i;
                        match[i] = open;
                    }
                }
                default -> {
                }
            }
        }
    }

    private void run() {
        stack.push(new Frame(false, BraceKind.ROOT, null, -1));
        for (int i = 0; i < toks.size(); i++) {
            if (i < skipUntil) {
                continue;
            }
            Token t = toks.get(i);
            Token p = i > 0 ? toks.get(i - 1) : null;
            Frame f = stack.peek();
            boolean operand = switch (t.type()) {
                case NUMBER, STRING, CHAR -> true;
                case IDENT -> identifier(i, t, p, f);
                case KEYWORD -> keyword(i, t, p, f);
                case OP -> operator(i, t, p, f);
            };
            prevOperand = operand;
        }
    }

    private Token tok(int index) {
        return index >= 0 && index < toks.size() ? toks.get(index) : null;
    }

    private static boolean isOp(Token token, String text) {
        return token != null && token.isOp(text);
    }

    private void add(WhitespaceAroundToken token, Token t, boolean skipBefore, boolean skipAfter) {
        sites.add(new Site(token, t.start(), t.end(), skipBefore, skipAfter));
    }

    private void add(WhitespaceAroundToken token, Token t) {
        add(token, t, false, false);
    }

    private boolean identifier(int i, Token t, Token p, Frame f) {
        if (t.is("non") && isOp(tok(i + 1), "-") && tok(i + 1).start() == t.end()
                && tok(i + 2) != null && tok(i + 2).is("sealed") && tok(i + 2).start() == tok(i + 1).end()) {
            skipUntil = i + 3;
            return false;
        }
        if (t.is("record") && !isOp(p, ".") && tok(i + 1) != null && tok(i + 1).type() == Type.IDENT
                && (isOp(tok(i + 2), "(") || isOp(tok(i + 2), "<"))) {
            f.typeHeaderPending = true;
            f.pendingTypeName = tok(i + 1).text();
            f.pendingEnum = false;
            f.pendingAnnotation = false;
            return false;
        }
        if (t.is("when") && !isOp(p, ".") && f.pending.peek() == Pending.CASE) {
            add(WhitespaceAroundToken.LITERAL_WHEN, t);
            return false;
        }
        if (t.is("yield") && isStatementBoundary(p)) {
            Token next = tok(i + 1);
            if (next != null && !(next.type() == Type.OP && (next.text().endsWith("=") && !next.text().equals("==")
                    || Set.of(".", "[", "++", "--").contains(next.text())))) {
                return false;
            }
        }
        return true;
    }

    private static boolean isStatementBoundary(Token p) {
        return p == null || p.isOp(";") || p.isOp("{") || p.isOp("}") || p.isOp(":") || p.isOp("->");
    }

    /** Statement keywords that are direct children of the block of a trailing instance initializer. */
    private boolean instanceInitQuirk(Token p, Frame f) {
        return f.isBrace(BraceKind.INSTANCE_INIT) && f.initLast
                && (p == null || p.isOp("{") || p.isOp(";") || p.isOp("}"));
    }

    private void statementKeyword(WhitespaceAroundToken token, Token p, Token t, Frame f, boolean skipAfter) {
        add(token, t, false, skipAfter || instanceInitQuirk(p, f));
    }

    private boolean keyword(int i, Token t, Token p, Frame f) {
        switch (t.text()) {
            case "this", "super", "null", "true", "false" -> {
                return true;
            }
            case "class" -> {
                if (isOp(p, ".")) {
                    return true;
                }
                beginTypeHeader(i, f, false, false);
            }
            case "interface" -> beginTypeHeader(i, f, false, isOp(p, "@"));
            case "enum" -> beginTypeHeader(i, f, true, false);
            case "if" -> statementKeyword(WhitespaceAroundToken.LITERAL_IF, p, t, f, false);
            case "for" -> statementKeyword(WhitespaceAroundToken.LITERAL_FOR, p, t, f, false);
            case "try" -> statementKeyword(WhitespaceAroundToken.LITERAL_TRY, p, t, f, false);
            case "assert" -> statementKeyword(WhitespaceAroundToken.LITERAL_ASSERT, p, t, f, false);
            case "synchronized" -> statementKeyword(WhitespaceAroundToken.LITERAL_SYNCHRONIZED, p, t, f, false);
            case "catch" -> add(WhitespaceAroundToken.LITERAL_CATCH, t);
            case "finally" -> add(WhitespaceAroundToken.LITERAL_FINALLY, t);
            case "else" -> add(WhitespaceAroundToken.LITERAL_ELSE, t);
            case "return" -> statementKeyword(WhitespaceAroundToken.LITERAL_RETURN, p, t, f, isOp(tok(i + 1), ";"));
            case "do" -> {
                statementKeyword(WhitespaceAroundToken.LITERAL_DO, p, t, f, false);
                if (!isOp(tok(i + 1), "{")) {
                    f.doStatements++;
                }
            }
            case "while" -> {
                boolean doWhile = (isOp(p, "}") && lastClosedBraceDo && lastClosedBraceIdx == i - 1)
                        || (isOp(p, ";") && f.doStatements > 0);
                if (doWhile) {
                    if (isOp(p, ";")) {
                        f.doStatements--;
                    }
                    add(WhitespaceAroundToken.DO_WHILE, t);
                } else {
                    statementKeyword(WhitespaceAroundToken.LITERAL_WHILE, p, t, f, false);
                }
            }
            case "switch" -> {
                if (isSwitchStatement(p) || !isWholeExpression(i)) {
                    statementKeyword(WhitespaceAroundToken.LITERAL_SWITCH, p, t, f, false);
                }
            }
            case "case" -> f.pending.push(Pending.CASE);
            case "default" -> {
                Token next = tok(i + 1);
                if (isOp(next, ":") || isOp(next, "->")) {
                    f.pending.push(Pending.CASE);
                }
            }
            default -> {
            }
        }
        return false;
    }

    private void beginTypeHeader(int i, Frame f, boolean isEnum, boolean isAnnotation) {
        Token name = tok(i + 1);
        f.typeHeaderPending = true;
        f.pendingTypeName = name != null && name.type() == Type.IDENT ? name.text() : null;
        f.pendingEnum = isEnum;
        f.pendingAnnotation = isAnnotation;
    }

    /**
     * Whether the switch expression at {@code index} is the complete expression, which Checkstyle does not check.
     * An assignment expression is not: only the initializer of a declaration is.
     */
    private boolean isWholeExpression(int index) {
        Token p = tok(index - 1);
        if (p == null) {
            return false;
        }
        if (p.type() == Type.OP) {
            String text = p.text();
            if (text.equals("=")) {
                Token name = tok(index - 2);
                Token type = tok(index - 3);
                return name != null && name.type() == Type.IDENT && type != null
                        && (type.type() == Type.IDENT || type.type() == Type.OP && type.text().matches(">+") || type.isOp("]") || JavaLexer.isPrimitive(type));
            }
            return text.equals("->") || text.equals(",") || text.equals("(");
        }
        return p.isKeyword("return") || p.type() == Type.IDENT && p.text().equals("yield");
    }

    private boolean isSwitchStatement(Token p) {
        if (p == null || p.isOp(";") || p.isOp("{") || p.isOp("}") || p.isKeyword("else") || p.isKeyword("do")) {
            return true;
        }
        if (p.isOp(":")) {
            return lastColon != Colon.TERNARY;
        }
        if (p.isOp(")") && lastClosedParen != null) {
            ParenKind kind = lastClosedParen.parenKind;
            return kind == ParenKind.IF || kind == ParenKind.FOR || kind == ParenKind.WHILE;
        }
        return false;
    }

    private boolean operator(int i, Token t, Token p, Frame f) {
        String op = t.text();
        switch (op) {
            case "(" -> {
                openParen(i, p);
                return false;
            }
            case ")" -> {
                return closeParen(i);
            }
            case "{" -> {
                openBrace(i, t, p, f);
                return false;
            }
            case "}" -> {
                closeBrace(i, t, f);
                return false;
            }
            case "]" -> {
                return true;
            }
            case ";" -> {
                f.typeHeaderPending = false;
                f.enumConstants = false;
                f.pending.clear();
                if (f.paren && f.parenKind == ParenKind.FOR) {
                    f.forSemicolon = true;
                }
                return false;
            }
            case "=" -> add(WhitespaceAroundToken.ASSIGN, t);
            case "==" -> add(WhitespaceAroundToken.EQUAL, t);
            case "!=" -> add(WhitespaceAroundToken.NOT_EQUAL, t);
            case "<=" -> add(WhitespaceAroundToken.LE, t);
            case "<<" -> add(WhitespaceAroundToken.SL, t);
            case "<<=" -> add(WhitespaceAroundToken.SL_ASSIGN, t);
            case "&&" -> add(WhitespaceAroundToken.LAND, t);
            case "||" -> add(WhitespaceAroundToken.LOR, t);
            case "+=" -> add(WhitespaceAroundToken.PLUS_ASSIGN, t);
            case "-=" -> add(WhitespaceAroundToken.MINUS_ASSIGN, t);
            case "*=" -> add(WhitespaceAroundToken.STAR_ASSIGN, t);
            case "/=" -> add(WhitespaceAroundToken.DIV_ASSIGN, t);
            case "%=" -> add(WhitespaceAroundToken.MOD_ASSIGN, t);
            case "&=" -> add(WhitespaceAroundToken.BAND_ASSIGN, t);
            case "|=" -> add(WhitespaceAroundToken.BOR_ASSIGN, t);
            case "^=" -> add(WhitespaceAroundToken.BXOR_ASSIGN, t);
            case "|" -> add(WhitespaceAroundToken.BOR, t);
            case "^" -> add(WhitespaceAroundToken.BXOR, t);
            case "/" -> add(WhitespaceAroundToken.DIV, t);
            case "%" -> add(WhitespaceAroundToken.MOD, t);
            case "..." -> add(WhitespaceAroundToken.ELLIPSIS, t);
            case "*" -> {
                if (!isOp(p, ".")) {
                    add(WhitespaceAroundToken.STAR, t);
                }
            }
            case "+" -> {
                if (prevOperand) {
                    add(WhitespaceAroundToken.PLUS, t);
                }
            }
            case "-" -> {
                if (prevOperand) {
                    add(WhitespaceAroundToken.MINUS, t);
                }
            }
            case "++", "--" -> {
                return prevOperand;
            }
            case "&" -> {
                if (f.genericDepth > 0 || isIntersectionCast(f)) {
                    add(WhitespaceAroundToken.TYPE_EXTENSION_AND, t);
                } else {
                    add(WhitespaceAroundToken.BAND, t);
                }
            }
            case "?" -> {
                if (f.genericDepth > 0) {
                    add(WhitespaceAroundToken.WILDCARD_TYPE, t);
                } else {
                    add(WhitespaceAroundToken.QUESTION, t);
                    f.pending.push(Pending.TERNARY);
                }
            }
            case ":" -> colon(i, t, f);
            case "->" -> {
                add(WhitespaceAroundToken.LAMBDA, t);
                lastArrowRule = f.pending.peek() == Pending.CASE;
                if (lastArrowRule) {
                    f.pending.pop();
                }
            }
            case "<" -> {
                if (looksGenericOpen(i, p, f)) {
                    f.genericDepth++;
                    add(WhitespaceAroundToken.GENERIC_START, t);
                } else {
                    add(WhitespaceAroundToken.LT, t);
                }
            }
            default -> {
                if (op.startsWith(">")) {
                    greater(t, f);
                }
            }
        }
        return false;
    }

    private void greater(Token t, Frame f) {
        String op = t.text();
        boolean pure = op.indexOf('=') < 0;
        int index = 0;
        if (pure && f.genericDepth > 0) {
            while (index < op.length() && f.genericDepth > 0) {
                sites.add(new Site(WhitespaceAroundToken.GENERIC_END, t.start() + index, t.start() + index + 1, false, false));
                f.genericDepth--;
                index++;
            }
            if (index == op.length()) {
                return;
            }
        }
        int start = t.start() + index;
        WhitespaceAroundToken token = switch (op.substring(index)) {
            case ">" -> WhitespaceAroundToken.GT;
            case ">>" -> WhitespaceAroundToken.SR;
            case ">>>" -> WhitespaceAroundToken.BSR;
            case ">=" -> WhitespaceAroundToken.GE;
            case ">>=" -> WhitespaceAroundToken.SR_ASSIGN;
            case ">>>=" -> WhitespaceAroundToken.BSR_ASSIGN;
            default -> null;
        };
        if (token != null) {
            sites.add(new Site(token, start, t.end(), false, false));
        }
    }

    private void colon(int i, Token t, Frame f) {
        Pending pending = f.pending.peek();
        if (pending == Pending.TERNARY) {
            f.pending.pop();
            lastColon = Colon.TERNARY;
            add(WhitespaceAroundToken.COLON, t);
        } else if (pending == Pending.CASE) {
            f.pending.pop();
            lastColon = Colon.CASE;
        } else if (f.paren && f.parenKind == ParenKind.FOR && !f.forSemicolon) {
            lastColon = Colon.OTHER;
            add(WhitespaceAroundToken.COLON, t, options.ignoreEnhancedForColon(), options.ignoreEnhancedForColon());
        } else if (isLabel(i)) {
            // Checkstyle retypes the colon of a labeled statement, so it is never a COLON token
            lastColon = Colon.OTHER;
        } else {
            lastColon = Colon.OTHER;
            add(WhitespaceAroundToken.COLON, t);
        }
    }

    private boolean isLabel(int colon) {
        Token name = tok(colon - 1);
        if (name == null || name.type() != Type.IDENT) {
            return false;
        }
        Token before = tok(colon - 2);
        return before == null || before.isOp(";") || before.isOp("{") || before.isOp("}") || before.isOp(":") || before.isOp(")")
                || before.isOp("->") || before.isKeyword("else");
    }

    private void openParen(int i, Token p) {
        ParenKind kind = ParenKind.PLAIN;
        int name = -1;
        if (p != null) {
            if (p.type() == Type.KEYWORD) {
                kind = switch (p.text()) {
                    case "if" -> ParenKind.IF;
                    case "while" -> ParenKind.WHILE;
                    case "for" -> ParenKind.FOR;
                    case "switch" -> ParenKind.SWITCH;
                    case "catch" -> ParenKind.CATCH;
                    case "synchronized" -> ParenKind.SYNCHRONIZED;
                    case "try" -> ParenKind.TRY;
                    case "this", "super" -> ParenKind.CALL;
                    default -> ParenKind.PLAIN;
                };
            } else if (p.type() == Type.IDENT || (p.type() == Type.OP && p.text().startsWith(">") && !p.text().contains("="))) {
                if (isNewBefore(i)) {
                    kind = ParenKind.NEW;
                } else if (p.type() == Type.IDENT) {
                    kind = isAnnotationBefore(i) ? ParenKind.ANNOTATION : ParenKind.CALL;
                    name = i - 1;
                }
            }
        }
        parenKindAt[i] = kind;
        parenNameAt[i] = name;
        stack.push(new Frame(true, null, kind, i));
    }

    private boolean isNewBefore(int openIdx) {
        int j = openIdx - 1;
        Token t = tok(j);
        if (t != null && t.type() == Type.OP && t.text().startsWith(">")) {
            int depth = 0;
            while (j >= 0) {
                Token current = toks.get(j);
                if (current.type() == Type.OP && current.text().startsWith(">") && !current.text().contains("=")) {
                    depth += current.text().length();
                } else if (current.isOp("<")) {
                    depth--;
                    if (depth <= 0) {
                        break;
                    }
                }
                j--;
            }
            j--;
        }
        Token ident = tok(j);
        if (ident == null || ident.type() != Type.IDENT) {
            return false;
        }
        while (isOp(tok(j - 1), ".") && tok(j - 2) != null && tok(j - 2).type() == Type.IDENT) {
            j -= 2;
        }
        Token before = tok(j - 1);
        return before != null && before.isKeyword("new");
    }

    private boolean isAnnotationBefore(int openIdx) {
        int j = openIdx - 1;
        while (isOp(tok(j - 1), ".") && tok(j - 2) != null && tok(j - 2).type() == Type.IDENT) {
            j -= 2;
        }
        return isOp(tok(j - 1), "@");
    }

    private boolean closeParen(int i) {
        Frame f = stack.peek();
        if (f == null || !f.paren) {
            return true;
        }
        stack.pop();
        lastClosedParen = f;
        boolean primitiveCast = f.parenKind == ParenKind.PLAIN && f.openIdx + 2 == i
                && JavaLexer.isPrimitive(toks.get(f.openIdx + 1));
        return !primitiveCast;
    }

    private boolean isIntersectionCast(Frame f) {
        if (!f.paren || f.parenKind != ParenKind.PLAIN || match[f.openIdx] < 0) {
            return false;
        }
        int close = match[f.openIdx];
        for (int j = f.openIdx + 1; j < close; j++) {
            Token t = toks.get(j);
            boolean typeLike = switch (t.type()) {
                case IDENT -> true;
                case KEYWORD -> JavaLexer.isPrimitive(t) || t.is("extends") || t.is("super");
                case OP -> Set.of(".", "&", "<", ",", "?", "[", "]", "@").contains(t.text())
                        || (t.text().startsWith(">") && !t.text().contains("="));
                default -> false;
            };
            if (!typeLike) {
                return false;
            }
        }
        Token next = tok(close + 1);
        if (next == null) {
            return false;
        }
        return switch (next.type()) {
            case IDENT, NUMBER, STRING, CHAR -> true;
            case KEYWORD -> Set.of("this", "new", "super", "true", "false", "null", "switch").contains(next.text());
            case OP -> next.is("(") || next.is("!") || next.is("~");
        };
    }

    private boolean looksGenericOpen(int i, Token p, Frame f) {
        if (p == null) {
            return false;
        }
        if (f.genericDepth > 0) {
            return true;
        }
        boolean candidate = p.type() == Type.IDENT
                || p.isOp(".") || p.isOp("::")
                || (p.type() == Type.KEYWORD && MODIFIERS.contains(p.text()))
                || ((p.isOp("{") || p.isOp("}") || p.isOp(";") || p.isOp(")")) && f.isBrace(BraceKind.TYPE));
        return candidate && speculateGeneric(i, followsInstanceof(i)) && !isComparisonInExpressionParens(i, f);
    }

    /**
     * A declaration cannot appear inside the parentheses of a call or condition, so {@code f(a < B, C > d)} holds two
     * comparisons. Declarations are possible in parameter lists of types, lambdas, loops, resources and catches.
     */
    private boolean isComparisonInExpressionParens(int index, Frame f) {
        if (!f.paren || lastSpeculationEnd < 0 || followsInstanceof(index) || isOp(tok(index - 1), ".")) {
            return false;
        }
        if (f.parenKind != ParenKind.CALL && f.parenKind != ParenKind.PLAIN && f.parenKind != ParenKind.IF
                && f.parenKind != ParenKind.WHILE && f.parenKind != ParenKind.SWITCH) {
            return false;
        }
        Token follow = tok(lastSpeculationEnd + 1);
        if (follow == null || follow.type() != Type.IDENT) {
            return false;
        }
        if (match[f.openIdx] >= 0 && isOp(tok(match[f.openIdx] + 1), "->")) {
            return false;
        }
        Frame parent = null;
        boolean seen = false;
        for (Frame candidate : stack) {
            if (seen) {
                parent = candidate;
                break;
            }
            seen = candidate == f;
        }
        if (parent == null || parent.isBrace(BraceKind.TYPE) || parent.isBrace(BraceKind.ROOT)) {
            return false;
        }
        Token recordKeyword = tok(f.openIdx - 2);
        return recordKeyword == null || !(recordKeyword.type() == Type.IDENT && recordKeyword.text().equals("record")
                || recordKeyword.isKeyword("record"));
    }

    private boolean followsInstanceof(int index) {
        int j = index - 1;
        if (j < 0 || toks.get(j).type() != Type.IDENT) {
            return false;
        }

        j = skipQualifiedNameBack(j);
        while (j >= 0) {
            if (toks.get(j).isKeyword("final")) {
                j--;
                continue;
            }

            if (toks.get(j).isOp(")") && match[j] >= 0) {
                int annotationOpen = match[j];
                int annotationName = annotationOpen - 1;
                if (annotationName >= 0 && toks.get(annotationName).type() == Type.IDENT) {
                    int beforeName = skipQualifiedNameBack(annotationName);
                    if (beforeName >= 0 && toks.get(beforeName).isOp("@")) {
                        j = beforeName - 1;
                        continue;
                    }
                }
            }

            if (toks.get(j).type() == Type.IDENT) {
                int beforeName = skipQualifiedNameBack(j);
                if (beforeName >= 0 && toks.get(beforeName).isOp("@")) {
                    j = beforeName - 1;
                    continue;
                }
            }
            break;
        }
        return j >= 0 && toks.get(j).isKeyword("instanceof");
    }

    private int skipQualifiedNameBack(int index) {
        int j = index - 1;
        while (j >= 1 && toks.get(j).isOp(".") && toks.get(j - 1).type() == Type.IDENT) {
            j -= 2;
        }
        return j;
    }

    private int lastSpeculationEnd = -1;

    private boolean speculateGeneric(int start, boolean unambiguous) {
        lastSpeculationEnd = -1;
        int depth = 0;
        int limit = Math.min(toks.size(), start + 400);
        for (int j = start; j < limit; j++) {
            Token t = toks.get(j);
            if (t.isOp("<")) {
                depth++;
                if (!typeArgumentStart(j + 1)) {
                    return false;
                }
                unambiguous |= strongStart(j + 1);
            } else if (t.type() == Type.OP && t.text().startsWith(">")) {
                if (t.text().contains("=")) {
                    return false;
                }
                depth -= t.text().length();
                if (depth < 0) {
                    return false;
                }
                if (depth == 0) {
                    lastSpeculationEnd = j;
                    return unambiguous || genericFollowOk(tok(j + 1));
                }
            } else if (t.isOp(",")) {
                if (!typeArgumentStart(j + 1)) {
                    return false;
                }
                unambiguous |= strongStart(j + 1);
            } else if (t.type() == Type.IDENT) {
                continue;
            } else if (t.isOp("(") && match[j] > j && isAnnotationName(j - 1)) {
                j = match[j];
            } else if (t.type() == Type.KEYWORD && (JavaLexer.isPrimitive(t) || t.is("extends") || t.is("super"))) {
                continue;
            } else if (!(t.isOp(".") || t.isOp("?") || t.isOp("&") || t.isOp("[") || t.isOp("]") || t.isOp("@"))) {
                return false;
            }
        }
        return false;
    }

    private boolean isAnnotationName(int index) {
        int j = index;
        while (j >= 2 && toks.get(j - 1).isOp(".") && toks.get(j - 2).type() == Type.IDENT) {
            j -= 2;
        }
        return j >= 1 && toks.get(index).type() == Type.IDENT && toks.get(j - 1).isOp("@");
    }

    private boolean strongStart(int index) {
        Token t = tok(index);
        return t != null && (t.isOp("?") || t.isOp("@") || JavaLexer.isPrimitive(t));
    }

    private boolean typeArgumentStart(int index) {
        Token t = tok(index);
        if (t == null) {
            return false;
        }
        if (t.isOp("?") || t.isOp("@") || (t.type() == Type.OP && t.text().startsWith(">")) || JavaLexer.isPrimitive(t)) {
            return true;
        }
        if (t.type() != Type.IDENT) {
            return false;
        }
        int j = index;
        while (isOp(tok(j + 1), ".") && tok(j + 2) != null && tok(j + 2).type() == Type.IDENT) {
            j += 2;
        }
        return Character.isUpperCase(toks.get(j).text().charAt(0));
    }

    private static boolean genericFollowOk(Token next) {
        if (next == null) {
            return false;
        }
        return switch (next.type()) {
            case IDENT -> true;
            case KEYWORD -> !Set.of("instanceof", "true", "false", "null").contains(next.text());
            case OP -> Set.of("(", ")", ",", ".", "::", "[", "{", ";", "&", "...", "@").contains(next.text());
            default -> false;
        };
    }

    private void openBrace(int i, Token t, Token p, Frame f) {
        BraceKind kind = classifyBrace(i, p, f);
        Token next = tok(i + 1);
        boolean empty = isOp(next, "}");
        Frame frame = new Frame(false, kind, null, i);
        frame.doBody = p != null && p.isKeyword("do");
        if (kind == BraceKind.TYPE) {
            if (f.typeHeaderPending) {
                frame.typeName = f.pendingTypeName;
                frame.enumType = f.pendingEnum;
                frame.annotationType = f.pendingAnnotation;
                frame.enumConstants = f.pendingEnum;
            }
            f.typeHeaderPending = false;
        }
        if (kind == BraceKind.INSTANCE_INIT) {
            frame.initLast = match[i] >= 0 && isOp(tok(match[i] + 1), "}");
        }
        lastArrowRule = false;
        boolean skipBefore = false;
        boolean skipAfter = false;
        WhitespaceAroundToken token = null;
        switch (kind) {
            case TYPE, SWITCH_BLOCK -> {
                token = WhitespaceAroundToken.LCURLY;
                if (empty && options.allowEmptyTypes()) {
                    skipBefore = skipAfter = true;
                }
                skipAfter |= kind == BraceKind.TYPE && isOp(next, "{");
            }
            case ARRAY_INIT -> {
                token = WhitespaceAroundToken.ARRAY_INIT;
                skipAfter = true;
            }
            case ANNOT_ARRAY_INIT -> {
            }
            default -> {
                token = WhitespaceAroundToken.SLIST;
                boolean emptyAllowed = empty && emptyAllowed(kind);
                if (kind == BraceKind.CTOR) {
                    skipAfter = emptyAllowed;
                } else {
                    skipBefore = skipAfter = emptyAllowed;
                }
                if (kind == BraceKind.INSTANCE_INIT) {
                    skipBefore = true;
                }
                if (kind == BraceKind.BLOCK && instanceInitQuirk(p, f)) {
                    skipAfter = true;
                }
            }
        }
        if (token != null) {
            add(token, t, skipBefore, skipAfter);
        }
        stack.push(frame);
    }

    private boolean emptyAllowed(BraceKind kind) {
        return switch (kind) {
            case METHOD -> options.allowEmptyMethods();
            case CTOR -> options.allowEmptyConstructors();
            case LOOP -> options.allowEmptyLoops();
            case LAMBDA -> options.allowEmptyLambdas();
            case CATCH -> options.allowEmptyCatches();
            case SWITCH_RULE, CASE_BLOCK -> options.allowEmptySwitchBlockStatements();
            default -> false;
        };
    }

    private BraceKind classifyBrace(int i, Token p, Frame f) {
        if (p == null) {
            return BraceKind.BLOCK;
        }
        if (p.isOp("->")) {
            return lastArrowRule ? BraceKind.SWITCH_RULE : BraceKind.LAMBDA;
        }
        if (f.typeHeaderPending) {
            return BraceKind.TYPE;
        }
        if (f.isBrace(BraceKind.ANNOT_ARRAY_INIT) && (p.isOp("{") || p.isOp(","))) {
            return BraceKind.ANNOT_ARRAY_INIT;
        }
        if (f.paren && f.parenKind == ParenKind.ANNOTATION && (p.isOp("(") || p.isOp("=") || p.isOp(","))) {
            return BraceKind.ANNOT_ARRAY_INIT;
        }
        if (p.isKeyword("default") && f.isBrace(BraceKind.TYPE) && f.annotationType) {
            return BraceKind.ANNOT_ARRAY_INIT;
        }
        if (p.isOp("=") || p.isOp("]")) {
            return BraceKind.ARRAY_INIT;
        }
        if (f.isBrace(BraceKind.ARRAY_INIT) && (p.isOp("{") || p.isOp(","))) {
            return BraceKind.ARRAY_INIT;
        }
        if (p.isOp(")")) {
            return afterParen(match[i - 1] >= 0 ? match[i - 1] : -1, f);
        }
        if (p.isKeyword("else") || p.isKeyword("finally") || p.isKeyword("try")) {
            return BraceKind.BLOCK;
        }
        if (p.isKeyword("do")) {
            return BraceKind.LOOP;
        }
        if (f.isBrace(BraceKind.TYPE)) {
            if (p.isKeyword("static")) {
                return BraceKind.STATIC_INIT;
            }
            if (p.isOp("{") || p.isOp("}") || p.isOp(";")) {
                return BraceKind.INSTANCE_INIT;
            }
            if (f.enumConstants && p.type() == Type.IDENT && (isOp(tok(i - 2), "{") || isOp(tok(i - 2), ","))) {
                return BraceKind.TYPE;
            }
            if (p.type() == Type.IDENT && f.typeName != null && p.text().equals(f.typeName)) {
                return BraceKind.CTOR;
            }
        } else if (p.isOp("{") || p.isOp("}") || p.isOp(";")) {
            return BraceKind.BLOCK;
        }
        if (p.isOp(":")) {
            return lastColon == Colon.CASE && f.isBrace(BraceKind.SWITCH_BLOCK) ? BraceKind.CASE_BLOCK : BraceKind.BLOCK;
        }
        int j = i - 1;
        while (j >= 0 && isThrowsClauseToken(toks.get(j))) {
            j--;
        }
        if (j >= 0 && toks.get(j).isOp(")") && match[j] >= 0) {
            return afterParen(match[j], f);
        }
        return BraceKind.BLOCK;
    }

    private static boolean isThrowsClauseToken(Token t) {
        return t.type() == Type.IDENT || t.is("throws")
                || (t.type() == Type.OP && Set.of(".", ",", "<", "?", "[", "]", "&").contains(t.text()))
                || (t.type() == Type.OP && t.text().startsWith(">") && !t.text().contains("="));
    }

    private BraceKind afterParen(int openIdx, Frame f) {
        if (openIdx < 0 || parenKindAt[openIdx] == null) {
            return BraceKind.BLOCK;
        }
        return switch (parenKindAt[openIdx]) {
            case SWITCH -> BraceKind.SWITCH_BLOCK;
            case FOR, WHILE -> BraceKind.LOOP;
            case CATCH -> BraceKind.CATCH;
            case NEW -> BraceKind.TYPE;
            case CALL -> {
                if (!f.isBrace(BraceKind.TYPE)) {
                    yield BraceKind.BLOCK;
                }
                int name = parenNameAt[openIdx];
                if (name < 0) {
                    yield BraceKind.BLOCK;
                }
                Token before = tok(name - 1);
                if (f.enumConstants && (isOp(before, "{") || isOp(before, ","))) {
                    yield BraceKind.TYPE;
                }
                yield f.typeName != null && toks.get(name).text().equals(f.typeName) ? BraceKind.CTOR : BraceKind.METHOD;
            }
            default -> BraceKind.BLOCK;
        };
    }

    private void closeBrace(int i, Token t, Frame f) {
        if (f.paren || f.brace == BraceKind.ROOT) {
            add(WhitespaceAroundToken.RCURLY, t);
            return;
        }
        stack.pop();
        BraceKind kind = f.brace;
        boolean empty = f.openIdx + 1 == i;
        boolean skipBefore = false;
        boolean skipAfter = false;
        switch (kind) {
            case ARRAY_INIT, ANNOT_ARRAY_INIT -> skipBefore = skipAfter = true;
            case TYPE, SWITCH_BLOCK -> skipBefore = skipAfter = empty && options.allowEmptyTypes();
            case INSTANCE_INIT -> skipAfter = f.initLast;
            default -> skipBefore = skipAfter = empty && emptyAllowed(kind);
        }
        if (kind == BraceKind.TYPE && lastClosedBrace == BraceKind.INSTANCE_INIT && lastClosedBraceIdx == i - 1) {
            skipBefore = true;
        }
        if (t.end() < text.length()) {
            char next = text.charAt(t.end());
            if (next == ')' || next == ';' || next == ',' || next == '.') {
                skipAfter = true;
            }
        }
        add(WhitespaceAroundToken.RCURLY, t, skipBefore, skipAfter);
        lastClosedBrace = kind;
        lastClosedBraceIdx = i;
        lastClosedBraceDo = f.doBody;
    }
}
