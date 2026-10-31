package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.puppycrawl.tools.checkstyle.Checker;
import com.puppycrawl.tools.checkstyle.DefaultConfiguration;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class IndentationOwnershipTest {
    @TempDir
    Path directory;

    static CheckstyleIndentationStep step() {
        var step = ProjectBuilder.builder().build().getObjects().newInstance(CheckstyleIndentationStep.class, "indent");
        step.getBasicOffset().set(4);
        step.getCaseIndent().set(0);
        step.getThrowsIndent().set(4);
        step.getLineWrappingIndentation().set(8);
        return step;
    }

    private void verify(String source) throws Exception {
        var formatter = step().formatter();
        String actual = formatter.format("Example.java", source);
        assertEquals(actual, formatter.format("Example.java", actual), "Formatting must be stable");
        DefaultConfiguration check = new DefaultConfiguration("Indentation");
        check.addProperty("basicOffset", "4");
        check.addProperty("caseIndent", "0");
        check.addProperty("throwsIndent", "4");
        check.addProperty("lineWrappingIndentation", "8");
        DefaultConfiguration walker = new DefaultConfiguration("TreeWalker");
        walker.addChild(check);
        DefaultConfiguration config = new DefaultConfiguration("Checker");
        config.addProperty("tabWidth", "4");
        config.addChild(walker);
        Checker checker = new Checker();
        var messages = new java.util.ArrayList<String>();
        checker.addListener(new com.puppycrawl.tools.checkstyle.api.AuditListener() {
            public void auditStarted(com.puppycrawl.tools.checkstyle.api.AuditEvent e) {}
            public void auditFinished(com.puppycrawl.tools.checkstyle.api.AuditEvent e) {}
            public void fileStarted(com.puppycrawl.tools.checkstyle.api.AuditEvent e) {}
            public void fileFinished(com.puppycrawl.tools.checkstyle.api.AuditEvent e) {}
            public void addError(com.puppycrawl.tools.checkstyle.api.AuditEvent e) {
                messages.add(e.getLine() + ": " + e.getMessage());
            }
            public void addException(com.puppycrawl.tools.checkstyle.api.AuditEvent e, Throwable t) {
                throw new AssertionError(t);
            }
        });
        try {
            checker.setModuleClassLoader(Checker.class.getClassLoader());
            checker.configure(config);
            Path file = directory.resolve("Example.java");
            Files.writeString(file, actual);
            assertEquals(0, checker.process(List.of(file.toFile())), () -> messages + "\n" + actual);
        } finally {
            checker.destroy();
        }
    }

    @Test
    void switchBlocksAndNestedStatements() throws Exception {
        verify("""
                class Example {
                void run(int axis) {
                switch (axis) {
                case 0 -> {
                call();
                if (ready()) {
                call();
                }
                }
                default -> {
                call();
                }
                }
                }
                }
                """);
    }

    @Test
    void classicCaseBlocks() throws Exception {
        verify("""
                class Example {
                void run(int axis) {
                switch (axis) {
                case 0:
                case 1: {
                call();
                break;
                }
                default: {
                call();
                }
                }
                }
                }
                """);
    }

    @Test
    void closingCallParenthesis() throws Exception {
        verify("""
                class Example {
                void run() {
                manager().extract(
                value(),
                () -> value(),
                other()
                );
                }
                }
                """);
    }

    @Test
    void lambdaArgumentsAndAssignedLambdas() throws Exception {
        verify("""
                class Example {
                Object field =
                new Cache(() -> {
                call();
                return value();
                });
                Runnable action =
                () -> {
                call();
                };
                void run() {
                boolean matches = stream()
                .allMatch(value -> {
                try {
                return ready();
                } catch (Exception e) {
                return false;
                }
                });
                }
                }
                """);
    }

    @Test
    void nestedUnbracedConditions() throws Exception {
        verify("""
                class Example {
                void run() {
                if (ready())
                if (ready())
                if (ready()) {
                call();
                }
                }
                }
                """);
    }

    @Test
    void wrappedDeclarationsAndThrows() throws Exception {
        verify("""
                class Example implements
                Runnable, AutoCloseable {
                public void run() {}
                public void close(
                )
                throws Exception {
                call();
                }
                }
                """);
    }

    @Test
    void switchExpressionRuleContinuation() throws Exception {
        verify("""
                class Example {
                int run(int axis) {
                int result = switch (axis) {
                case 0 ->
                helper().value();
                default -> 0;
                };
                return result;
                }
                }
                """);
    }

    @Test
    void chainedCallsAndAnonymousClassClosures() throws Exception {
        verify("""
                class Example {
                void run() {
                stream()
                .filter(value -> ready())
                .forEach(
                value -> call()
                );
                register(true,
                new Runner() {
                void run() {
                call();
                }
                });
                }
                }
                """);
    }

    @Test
    void wrappedParametersAndInlineThrows() throws Exception {
        verify("""
                class Example {
                void run(
                final Object first,
                final Object second) throws Exception {
                call();
                }
                void other(
                final Object value
                ) throws Exception {
                call();
                }
                }
                """);
    }

    @Test
    void splitClassicLabelAndSwitchInsideCall() throws Exception {
        verify("""
                class Example {
                void run(int axis) {
                switch (axis) {
                case 0
                : case 1
                : case 2 : {
                call();
                break;
                }
                default: break;
                }
                stream()
                .forEach(e -> accept(switch (axis) {
                case 0 -> 1;
                default -> 2;
                }));
                }
                }
                """);
    }

    @Test
    void classicCaseStatementsRetainTheirOwnDepth() throws Exception {
        verify("""
                class Example {
                void run(int axis) {
                switch (axis) {
                case 0:
                if (ready()) {
                while (ready()) {
                call();
                }
                }
                break;
                default:
                call();
                }
                }
                }
                """);
    }

    @Test
    void unbracedLoopsAndElseBodies() throws Exception {
        verify("""
                class Example {
                void run() {
                for (Object value : values)
                while (ready())
                if (ready())
                call();
                else
                call();
                for (int i = 0; i < 10; i++)
                call();
                do
                call();
                while (ready());
                }
                }
                """);
    }
}
