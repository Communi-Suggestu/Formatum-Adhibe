package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CheckstyleWhitespaceAroundStepTest {
    private static final WhitespaceAroundAnalyzer.Options DEFAULT_OPTIONS = WhitespaceAroundAnalyzer.Options.defaults();

    private static String format(String source, WhitespaceAroundToken... tokens) {
        return CheckstyleWhitespaceAroundStep.apply(source, tokens.length == 0 ? WhitespaceAroundToken.DEFAULTS : EnumSet.copyOf(List.of(tokens)), DEFAULT_OPTIONS);
    }

    private static String format(String source, WhitespaceAroundAnalyzer.Options options, WhitespaceAroundToken... tokens) {
        return CheckstyleWhitespaceAroundStep.apply(source, tokens.length == 0 ? WhitespaceAroundToken.DEFAULTS : EnumSet.copyOf(List.of(tokens)), options);
    }

    private static String body(String statements) {
        return "class A {\n    void m() {\n        " + statements + "\n    }\n}\n";
    }

    private static String stepFormat(List<String> tokens, String source) {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, "ws");
        step.getTokens().set(tokens);
        return step.formatter().format("A.java", source);
    }

    @Test
    void defaultTokensMatchCheckstyle() {
        Set<WhitespaceAroundToken> defaults = WhitespaceAroundToken.DEFAULTS;
        for (WhitespaceAroundToken excluded : List.of(WhitespaceAroundToken.ARRAY_INIT, WhitespaceAroundToken.WILDCARD_TYPE,
                WhitespaceAroundToken.GENERIC_START, WhitespaceAroundToken.GENERIC_END, WhitespaceAroundToken.ELLIPSIS)) {
            assertTrue(!defaults.contains(excluded), excluded + " must not be a default");
        }
        assertEquals(WhitespaceAroundToken.values().length - 5, defaults.size());
        assertTrue(defaults.containsAll(List.of(WhitespaceAroundToken.PLUS, WhitespaceAroundToken.MINUS, WhitespaceAroundToken.DIV,
                WhitespaceAroundToken.LITERAL_WHEN, WhitespaceAroundToken.COLON)));
    }

    @Test
    void emptyTokenListSelectsDefaultsAndUnknownNamesAreIgnored() {
        assertEquals(WhitespaceAroundToken.DEFAULTS, WhitespaceAroundToken.resolve(List.of()));
        assertEquals(Set.of(WhitespaceAroundToken.PLUS), WhitespaceAroundToken.resolve(List.of("plus", "NOT_A_TOKEN")));
    }

    @Test
    void usesDefaultsWhenNoTokensConfigured() {
        assertEquals(body("int a = b + c;"), stepFormat(List.of(), body("int a=b+c;")));
    }

    @Test
    void onlyConfiguredTokensAreFormatted() {
        assertEquals(body("int a = b+c;"), stepFormat(List.of("ASSIGN"), body("int a=b+c;")));
        assertEquals(body("int a=b + c;"), stepFormat(List.of("PLUS"), body("int a=b+c;")));
    }

    @Test
    void arithmeticOperators() {
        assertEquals(body("x = a + b - c * d / e % f;"), format(body("x=a+b-c*d/e%f;")));
        assertEquals(body("x = a+b-c*d/e%f;"), format(body("x=a+b-c*d/e%f;"), WhitespaceAroundToken.ASSIGN));
    }

    @Test
    void assignmentOperators() {
        assertEquals(body("a += 1; a -= 1; a *= 1; a /= 1; a %= 1; a <<= 1; a >>= 1; a >>>= 1; a &= 1; a |= 1; a ^= 1;"),
                format(body("a+=1; a-=1; a*=1; a/=1; a%=1; a<<=1; a>>=1; a>>>=1; a&=1; a|=1; a^=1;")));
    }

    @Test
    void comparisonAndLogicalOperators() {
        assertEquals(body("t = a < b && b > a || a <= b && b >= a && a == b && a != b;"),
                format(body("t=a<b&&b>a||a<=b&&b>=a&&a==b&&a!=b;")));
    }

    @Test
    void bitwiseAndShiftOperators() {
        assertEquals(body("x = a & b | c ^ d << 1 >> 2 >>> 3;"), format(body("x=a&b|c^d<<1>>2>>>3;")));
    }

    @Test
    void unaryOperatorsAreNotChecked() {
        String source = body("x = -a + +b - ~c; i++; ++i; i--; --i; b = !b; x = (int) -y;");
        assertEquals(source, format(source));
    }

    @Test
    void binaryOperatorAfterIncrementIsChecked() {
        assertEquals(body("x = a++ + b;"), format(body("x = a+++b;")));
    }

    @Test
    void ternaryOperator() {
        assertEquals(body("x = c ? a : b;"), format(body("x = c?a:b;")));
    }

    @Test
    void lambdaArrow() {
        assertEquals(body("Runnable r = () -> run(); Function<A, B> f = a -> a;"), format(body("Runnable r = ()->run(); Function<A, B> f = a->a;")));
    }

    @Test
    void instanceofIsNotAToken() {
        String source = body("t = o instanceof String;");
        assertEquals(source, format(source));
    }

    @Test
    void controlFlowKeywords() {
        String formatted = format(body("if(a){b();}else{c();} for(;;){break;} while(a){b();} do{b();}while(a); try{b();}catch(E e){c();}finally{d();}"));
        assertEquals(body("if (a) { b(); } else { c(); } for (;;) { break; } while (a) { b(); } do { b(); } while (a); try { b(); } catch (E e) { c(); } finally { d(); }"), formatted);
    }

    @Test
    void returnWithoutValueIsNotChecked() {
        assertEquals(body("return;"), format(body("return;")));
        assertEquals(body("return (a);"), format(body("return(a);")));
    }

    @Test
    void synchronizedStatementAndModifier() {
        assertEquals(body("synchronized (this) { }"), format(body("synchronized(this){}")));
        assertEquals("class A {\n    synchronized void m() {\n    }\n}\n", format("class A {\n    synchronized void m(){\n    }\n}\n"));
    }

    @Test
    void assertStatement() {
        assertEquals(body("assert a : \"x\";"), format(body("assert a:\"x\";")));
    }

    @Test
    void switchStatementKeywordIsChecked() {
        assertEquals(body("switch (a) {\n            default:\n                break;\n        }"),
                format(body("switch(a){\n            default:\n                break;\n        }")));
    }

    @Test
    void caseAndDefaultColonsAreNotChecked() {
        String source = body("switch (a) {\n            case 1:\n                break;\n            default:\n                break;\n        }");
        assertEquals(source, format(source));
    }

    @Test
    void switchExpressionKeywordIsNotCheckedAsWholeExpression() {
        String source = body("int x = switch(a) { default -> 1; };");
        assertEquals(body("int x = switch(a) { default -> 1; };"), format(source));
    }

    @Test
    void switchExpressionAsOperandIsChecked() {
        assertEquals(body("int x = 1 + switch (a) { default -> 1; };"), format(body("int x = 1+switch(a) { default -> 1; };")));
    }

    @Test
    void switchRuleArrowAndWhenGuard() {
        assertEquals(body("switch (o) {\n            case Integer i when i > 1 -> a();\n            default -> b();\n        }"),
                format(body("switch (o) {\n            case Integer i when i>1->a();\n            default->b();\n        }")));
    }

    @Test
    void labelColonIsNotChecked() {
        String source = body("outer:\n        for (;;) { continue outer; }");
        assertEquals(source, format(source));
    }

    @Test
    void enhancedForColonIsIgnoredByDefault() {
        assertEquals(body("for(String s:list) { }"), format(body("for(String s:list){}"), WhitespaceAroundToken.COLON, WhitespaceAroundToken.LCURLY, WhitespaceAroundToken.SLIST));
    }

    @Test
    void enhancedForColonIsCheckedWhenNotIgnored() {
        WhitespaceAroundAnalyzer.Options options = new WhitespaceAroundAnalyzer.Options(false, false, false, false, false, false, false, false);
        assertEquals(body("for (String s : list) { }"), format(body("for (String s:list) { }"), options, WhitespaceAroundToken.COLON));
    }

    @Test
    void starImportIsNotChecked() {
        String source = "import java.util.*;\nclass A {\n}\n";
        assertEquals(source, format(source));
        assertEquals(body("x = a * b;"), format(body("x = a*b;")));
    }

    @Test
    void genericsAreNotTreatedAsComparisons() {
        String source = "class A<T extends Comparable<T>> {\n    Map<String, List<Integer>> m = new HashMap<>();\n    <U> void f(List<? extends U> l) {\n    }\n}\n";
        assertEquals(source, format(source));
    }

    @Test
    void comparisonsInCallArgumentsAreNotGenerics() {
        assertEquals(body("f(a < B, C > d);"), format(body("f(a<B, C>d);")));
        String lambda = body("f((Map<K, V> d) -> d);");
        assertEquals(lambda, format(lambda));
    }

    @Test
    void genericTokensCanBeSelectedExplicitly() {
        assertEquals("class A {\n    List < String> l;\n}\n", format("class A {\n    List<String> l;\n}\n", WhitespaceAroundToken.GENERIC_START));
        assertEquals("class A {\n    List<String> l;\n}\n", format("class A {\n    List<String> l;\n}\n", WhitespaceAroundToken.GT));
    }

    @Test
    void wildcardAndEllipsisOnlyWhenSelected() {
        assertEquals("class A {\n    List< ? > l;\n    void f(String ... a) {\n    }\n}\n",
                format("class A {\n    List<?> l;\n    void f(String... a) {\n    }\n}\n", WhitespaceAroundToken.WILDCARD_TYPE, WhitespaceAroundToken.ELLIPSIS));
        String source = "class A {\n    List<?> l;\n    void f(String... a) {\n    }\n}\n";
        assertEquals(source, format(source));
    }

    @Test
    void typeExtensionAnd() {
        assertEquals("class A<T extends B & C> {\n}\n", format("class A<T extends B&C> {\n}\n"));
    }

    @Test
    void methodReferenceAndAnnotationsAreNotChecked() {
        String source = "@Deprecated(since = \"1\")\nclass A {\n    Object o = String::length;\n}\n";
        assertEquals(source, format(source));
    }

    @Test
    void arrayInitializerOnlyChecksBefore() {
        assertEquals(body("int[] a = {1, 2};"), format(body("int[] a = {1, 2};")));
        assertEquals(body("int[] a = {1, 2};"), format(body("int[] a ={1, 2};"), WhitespaceAroundToken.ARRAY_INIT));
        assertEquals(body("int[] a = {1, 2};"), format(body("int[] a ={1, 2};")));
    }

    @Test
    void curlyBracesOfTypesAndBlocks() {
        assertEquals("class A { int a; }\n", format("class A{int a; }\n"));
        assertEquals(body("{ a(); }"), format(body("{a();}")));
    }

    @Test
    void allowEmptyMethods() {
        String source = "class A {\n    void m(){}\n}\n";
        assertEquals("class A {\n    void m() { }\n}\n", format(source));
        assertEquals(source, format(source, new WhitespaceAroundAnalyzer.Options(false, true, false, false, false, false, false, true)));
    }

    @Test
    void allowEmptyConstructors() {
        String source = "class A {\n    A(){}\n}\n";
        assertEquals("class A {\n    A() { }\n}\n", format(source));
        assertEquals("class A {\n    A() {}\n}\n", format(source, new WhitespaceAroundAnalyzer.Options(true, false, false, false, false, false, false, true)));
    }

    @Test
    void allowEmptyTypes() {
        assertEquals("class A { }\n", format("class A{}\n"));
        assertEquals("class A{}\n", format("class A{}\n", new WhitespaceAroundAnalyzer.Options(false, false, true, false, false, false, false, true)));
    }

    @Test
    void allowEmptyLoops() {
        assertEquals(body("while (a) { }"), format(body("while (a){}")));
        assertEquals(body("while (a){}"), format(body("while (a){}"), new WhitespaceAroundAnalyzer.Options(false, false, false, true, false, false, false, true)));
    }

    @Test
    void allowEmptyLambdas() {
        assertEquals(body("Runnable r = () -> { };"), format(body("Runnable r = () -> {};")));
        assertEquals(body("Runnable r = () -> {};"), format(body("Runnable r = () -> {};"), new WhitespaceAroundAnalyzer.Options(false, false, false, false, true, false, false, true)));
    }

    @Test
    void allowEmptyCatches() {
        assertEquals(body("try { a(); } catch (E e) { }"), format(body("try { a(); } catch (E e){}")));
        assertEquals(body("try { a(); } catch (E e){}"), format(body("try { a(); } catch (E e){}"), new WhitespaceAroundAnalyzer.Options(false, false, false, false, false, true, false, true)));
    }

    @Test
    void allowEmptySwitchBlockStatements() {
        String source = body("switch (a) {\n            case 1: {}\n            default: break;\n        }");
        assertEquals(body("switch (a) {\n            case 1: { }\n            default: break;\n        }"), format(source));
        assertEquals(source, format(source, new WhitespaceAroundAnalyzer.Options(false, false, false, false, false, false, true, true)));
    }

    @Test
    void anonymousClassEndIsNotChecked() {
        String source = body("run(new Runnable() {\n            public void run() {\n            }\n        });");
        assertEquals(source, format(source));
    }

    @Test
    void doubleBraceInitializerIsAccepted() {
        String source = body("Map<String, String> m = new HashMap<>() {{\n            put(\"a\", \"b\");\n        }};");
        assertEquals(source, format(source));
    }

    @Test
    void stringsCharsCommentsAndTextBlocksAreUntouched() {
        String source = body("String s = \"a+b=c{}\"; char c = '+'; // x+y=z\n        /* a=b */ String t = \"\"\"\n            a+b {\n            \"\"\";");
        assertEquals(source, format(source));
    }

    @Test
    void indentationAndExistingSpacingArePreserved() {
        String source = "class A {\n\tvoid m() {\n\t\tx  =  a   +   b;\n\t}\n}\n";
        assertEquals(source, format(source));
    }

    @Test
    void formattingIsIdempotent() {
        String once = format(body("if(a){x=a?b:c;}else{y+=1;}"));
        assertEquals(once, format(once));
    }

    @Test
    void doesNotChangeAlreadyCorrectCode() {
        String source = body("if (a == b) {\n            x = a + b;\n        }");
        assertEquals(source, format(source));
    }

    @Test
    void booleanOptionsAreWiredThroughTheStep() {
        CheckstyleWhitespaceAroundStep step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleWhitespaceAroundStep.class, "ws");
        step.getAllowEmptyMethods().set(true);
        assertEquals("class A {\n    void m(){}\n}\n", step.formatter().format("A.java", "class A {\n    void m(){}\n}\n"));
        step.getAllowEmptyMethods().set(false);
        assertEquals("class A {\n    void m() { }\n}\n", step.formatter().format("A.java", "class A {\n    void m(){}\n}\n"));
    }

    @Test
    void emptyBracesInsideStringLiteralsAreUntouched() {
        String log = body("LOGGER.debug(\"Found specified {} instance for: {}.Using it rather than creating a new instance.\", a.getSimpleName(),\n                nameFunction.apply(instance));");
        assertEquals(log, format(log));
        String stringOnly = body("String s = \"{}\"+\"{}\";");
        assertEquals(body("String s = \"{}\" + \"{}\";"), format(stringOnly));
    }

    @Test
    void emptyBracesInsideTextBlocksAndCharsAreUntouched() {
        String source = body("String s = \"\"\"\n        {} and {}\n        \"\"\";\n        char c = '{';");
        assertEquals(source, format(source));
    }
}
