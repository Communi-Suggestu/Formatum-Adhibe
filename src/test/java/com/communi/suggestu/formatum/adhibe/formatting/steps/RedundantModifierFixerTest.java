package com.communi.suggestu.formatum.adhibe.formatting.steps;

import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

import java.util.EnumSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class RedundantModifierFixerTest {
    private static String fix(String source) {
        return fix(source, RedundantModifierFixer.DEFAULT_JDK_VERSION, List.of());
    }

    private static String fix(String source, int jdkVersion, List<String> tokens) {
        RedundantModifierFixer fixer = new RedundantModifierFixer(jdkVersion, RedundantModifierFixer.Visit.fromTokens(tokens));
        String result = fixer.apply(source);
        assertEquals(result, fixer.apply(result), "fix must be idempotent");
        return result;
    }

    @Test
    void checkstyleDocumentationDefaultExample() {
        String source = """
                public class Example1 {
                  void test() {
                    try (final var a = lock()) {
                    } catch (Exception e) {
                    }
                  }
                  abstract interface I {
                    public abstract void m();
                    public int x = 0;
                  }
                  static enum E {
                    A, B, C
                  }
                  public strictfp class Test { }
                  AutoCloseable lock() {
                    return null;
                  }
                }
                """;
        String expected = """
                public class Example1 {
                  void test() {
                    try (var a = lock()) {
                    } catch (Exception e) {
                    }
                  }
                  interface I {
                    void m();
                    int x = 0;
                  }
                  enum E {
                    A, B, C
                  }
                  public class Test { }
                  AutoCloseable lock() {
                    return null;
                  }
                }
                """;
        assertEquals(expected, fix(source));
    }

    @Test
    void tokensRestrictVisitedNodesButNotTheirRules() {
        String source = """
                public class Example2 {
                  void test() {
                    try (final var a = lock()) {
                    }
                  }
                  abstract interface I {
                    public abstract void m();
                    public int x = 0;
                  }
                  static enum E { A }
                  public strictfp class Test { }
                }
                """;
        String expected = """
                public class Example2 {
                  void test() {
                    try (final var a = lock()) {
                    }
                  }
                  abstract interface I {
                    void m();
                    public int x = 0;
                  }
                  static enum E { A }
                  public strictfp class Test { }
                }
                """;
        assertEquals(expected, fix(source, 22, List.of("METHOD_DEF")));
    }

    @Test
    void jdkVersionGatesStrictfpAndUnnamedVariables() {
        String source = """
                public strictfp class Test {
                  void run(Object o) {
                    try {
                    } catch (final Exception _) {
                    }
                    if (o instanceof final String _) {
                    }
                  }
                }
                """;
        assertEquals(source, fix(source, 11, List.of()));
        assertEquals(source.replace("strictfp ", ""), fix(source, 17, List.of()));
        assertEquals(source.replace("strictfp ", "").replace("final ", ""), fix(source, 22, List.of()));
    }

    @Test
    void removesAllRedundantModifiersFromSingleParse() {
        String source = """
                public interface Api {
                    public static final int A = 1;
                    public abstract void m(final int x);
                    public static abstract interface Nested {
                    }
                    public static final class Impl {
                        public final void done() {
                        }
                    }
                    public static record Value(int v) {
                    }
                    static enum Mode { ON, OFF }
                }
                """;
        String expected = """
                public interface Api {
                    int A = 1;
                    void m(int x);
                    interface Nested {
                    }
                    final class Impl {
                        public void done() {
                        }
                    }
                    record Value(int v) {
                    }
                    enum Mode { ON, OFF }
                }
                """;
        AtomicInteger parses = new AtomicInteger();
        RedundantModifierFixer fixer = new RedundantModifierFixer(22, EnumSet.allOf(RedundantModifierFixer.Visit.class), content -> {
            parses.incrementAndGet();
            return RedundantModifierFixer.parseWithJavac(content);
        });
        assertEquals(expected, fixer.apply(source));
        assertEquals(1, parses.get());
    }

    @Test
    void constructorRules() {
        String source = """
                public class Outer {
                    public Outer() {
                    }
                    protected class Prot {
                        public Prot() {
                        }
                    }
                    class Pkg {
                        public Pkg() {
                        }
                    }
                    public class Pub {
                        public Pub() {
                        }
                    }
                    enum Kind {
                        A;
                        private Kind() {
                        }
                    }
                    record Point(int x) {
                        public Point {
                        }
                    }
                    void local() {
                        class Local {
                            public Local() {
                            }
                        }
                    }
                }
                class PackagePrivate {
                    public PackagePrivate() {
                    }
                }
                """;
        String expected = """
                public class Outer {
                    public Outer() {
                    }
                    protected class Prot {
                        public Prot() {
                        }
                    }
                    class Pkg {
                        Pkg() {
                        }
                    }
                    public class Pub {
                        public Pub() {
                        }
                    }
                    enum Kind {
                        A;
                        Kind() {
                        }
                    }
                    record Point(int x) {
                        public Point {
                        }
                    }
                    void local() {
                        class Local {
                            Local() {
                            }
                        }
                    }
                }
                class PackagePrivate {
                    PackagePrivate() {
                    }
                }
                """;
        assertEquals(expected, fix(source));
    }

    @Test
    void finalMethodRulesIncludingCheckstyleQuirks() {
        String source = """
                public final class Holder {
                    public final void a() {
                    }
                    @SafeVarargs
                    public final void b(String... values) {
                    }
                    private final void c() {
                    }
                    Runnable r = new Runnable() {
                        public final void run() {
                        }
                    };
                }
                record Rec(int v) {
                    public final int twice() {
                        return v * 2;
                    }
                }
                enum E {
                    A {
                        @Override
                        public final void m() {
                        }
                    };
                    public void m() {
                    }
                    public final void n() {
                    }
                    static final void s() {
                    }
                }
                abstract class Base {
                    abstract void m(final int a, final String b);
                    native void n(final int a);
                    void body(final int a) {
                    }
                }
                """;
        String expected = """
                public final class Holder {
                    public void a() {
                    }
                    @SafeVarargs
                    public final void b(String... values) {
                    }
                    private void c() {
                    }
                    Runnable r = new Runnable() {
                        public void run() {
                        }
                    };
                }
                record Rec(int v) {
                    public final int twice() {
                        return v * 2;
                    }
                }
                enum E {
                    A {
                        @Override
                        public void m() {
                        }
                    };
                    public void m() {
                    }
                    public final void n() {
                    }
                    static void s() {
                    }
                }
                abstract class Base {
                    abstract void m(int a, String b);
                    native void n(int a);
                    void body(final int a) {
                    }
                }
                """;
        assertEquals(expected, fix(source));
    }

    @Test
    void annotationMembersAndNestedTypes() {
        String source = """
                public @interface Marker {
                    public static final String DEFAULT = "x";
                    public abstract String value() default DEFAULT;
                    public static abstract class Helper {
                    }
                }
                """;
        String expected = """
                public @interface Marker {
                    String DEFAULT = "x";
                    String value() default DEFAULT;
                    abstract class Helper {
                    }
                }
                """;
        assertEquals(expected, fix(source));
    }

    @Test
    void keepsNonRedundantInterfaceModifiers() {
        String source = """
                interface I {
                    default void d() {
                    }
                    static void s() {
                    }
                    private void p() {
                    }
                    abstract class Base {
                    }
                    final class Leaf {
                    }
                }
                """;
        assertEquals(source, fix(source));
    }

    @Test
    void handlesAnnotationsCommentsAndLineBreaksBetweenModifiers() {
        String source = """
                interface I {
                \t@Deprecated public @SuppressWarnings("public") abstract void a();
                \tpublic /* keep */ abstract void b();
                \t@Deprecated
                \tpublic
                \tvoid c();
                \tpublic static
                \tfinal int D = 1;
                }
                """;
        String expected = """
                interface I {
                \t@Deprecated @SuppressWarnings("public") void a();
                \t/* keep */ void b();
                \t@Deprecated

                \tvoid c();

                \tint D = 1;
                }
                """;
        assertEquals(expected, fix(source));
    }

    @Test
    void compactSourceFileTopLevelMethods() {
        assumeTrue(Runtime.version().feature() >= 25, "compact source files need JDK 25+ to parse");
        String source = """
                public void main() {
                }
                public String toString() {
                    return "";
                }
                public boolean equals(Object other) {
                    return false;
                }
                """;
        String expected = """
                void main() {
                }
                public String toString() {
                    return "";
                }
                public boolean equals(Object other) {
                    return false;
                }
                """;
        assertEquals(expected, fix(source));
    }

    @Test
    void unnamedVariablesInLambdasLoopsAndLocals() {
        String source = """
                class C {
                    void run(java.util.List<String> list) {
                        for (final var _ : list) {
                        }
                        java.util.function.BiFunction<String, String, String> f = (final var _, final var b) -> b;
                        final var named = 1;
                    }
                }
                """;
        String expected = """
                class C {
                    void run(java.util.List<String> list) {
                        for (var _ : list) {
                        }
                        java.util.function.BiFunction<String, String, String> f = (var _, final var b) -> b;
                        final var named = 1;
                    }
                }
                """;
        assertEquals(expected, fix(source));
    }

    @Test
    void unparseableSourceIsLeftUntouched() {
        String source = "interface I { public void m( }\n";
        assertEquals(source, fix(source));
    }

    @Test
    void stepReadsCheckstyleProperties() {
        CheckstyleRedundantModifierStep step = ProjectBuilder.builder().build().getObjects()
                .newInstance(CheckstyleRedundantModifierStep.class, "testStep");
        step.getJdkVersion().set(CheckstyleRedundantModifierStep.parseJdkVersion("1.8"));
        step.getTokens().set(List.of("INTERFACE_DEF"));

        String source = "public class A {\n\tstatic interface I {\n\t\tpublic void m();\n\t}\n\tpublic strictfp void s() {\n\t}\n}\n";
        String expected = "public class A {\n\tinterface I {\n\t\tpublic void m();\n\t}\n\tpublic strictfp void s() {\n\t}\n}\n";
        assertEquals(expected, step.formatter().format("A.java", source));
    }
}
