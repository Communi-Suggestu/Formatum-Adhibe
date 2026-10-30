package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModifierOrderFixerTest {
    private static String fix(String source) {
        ModifierOrderFixer fixer = new ModifierOrderFixer();
        String result = fixer.apply(source);
        assertEquals(result, fixer.apply(result), "fix must be idempotent");
        return result;
    }

    @Test
    void reportsCanonicalOrderingForVectorUtilsField() {
        String source = "public class VectorUtils {\n"
                + "    public final static double DEG_TO_RAD_FACTOR = (2 * Math.PI) / 360d;\n"
                + "}\n";
        String expected = "public class VectorUtils {\n"
                + "    public static final double DEG_TO_RAD_FACTOR = (2 * Math.PI) / 360d;\n"
                + "}\n";
        assertEquals(expected, fix(source));
    }

    @Test
    void preservesMultilineIndentationWhenReordering() {
        String source = "public class Multi {\n"
                + "    public\n"
                + "    final\n"
                + "    static\n"
                + "    int x;\n"
                + "}\n";
        String expected = "public class Multi {\n"
                + "    public\n"
                + "    static\n"
                + "    final\n"
                + "    int x;\n"
                + "}\n";
        assertEquals(expected, fix(source));
    }

    @Test
    void leavesAlreadyOrderedModifiersUntouched() {
        String source = "public class C {\n"
                + "    public static final int a = 1;\n"
                + "}\n";
        assertEquals(source, fix(source));
    }

    @Test
    void fixesOnlyTheOutOfOrderMemberAmongMany() {
        String source = "public class C {\n"
                + "    static final int a = 1;\n"
                + "    final static int b = 2;\n"
                + "}\n";
        String expected = "public class C {\n"
                + "    static final int a = 1;\n"
                + "    static final int b = 2;\n"
                + "}\n";
        assertEquals(expected, fix(source));
    }

    @Test
    void leavesAnnotatedDeclarationUntouched() {
        String source = "public class A {\n"
                + "    @Deprecated public final static int z = 0;\n"
                + "}\n";
        assertEquals(source, fix(source));
    }

    @Test
    void leavesUnparseableSourceUntouched() {
        String broken = "public class Broken {\n";
        assertEquals(broken, fix(broken));
    }
}
