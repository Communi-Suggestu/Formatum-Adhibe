package com.communi.suggestu.formatum.adhibe.formatting.steps;

import com.communi.suggestu.formatum.adhibe.utils.JavaSourceTokens;
import com.sun.source.tree.BlockTree;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.TreeScanner;
import com.sun.source.util.Trees;
import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import javax.inject.Inject;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaFileObject;
import javax.tools.SimpleJavaFileObject;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.io.StringWriter;
import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public abstract class CheckstyleLeftCurlyStep extends FormattingStep {
    @Inject
    public CheckstyleLeftCurlyStep() {
    }

    @Input
    public abstract Property<String> getOption();

    /** Allows enum constants on the opening-brace line; the brace itself still follows the configured placement. */
    @Input
    public abstract Property<Boolean> getIgnoreEnums();

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> applyLeftCurlyPolicy(TextFormattingUtils.normalizeNewlines(text));
    }

    private String applyLeftCurlyPolicy(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        String option = getOption().getOrElse("eol");
        return switch (option) {
            case "nl", "nlow" -> moveLeftCurlyToNewLine(lines);
            default -> breakAfterOpeningBraces(moveLeftCurlyToPreviousLine(lines));
        };
    }

    private String moveLeftCurlyToPreviousLine(List<String> lines) {
        Set<Integer> openings = new HashSet<>();
        String source = String.join("\n", lines);
        for (JavaLexer.Token token : JavaLexer.lex(source)) {
            if (token.isOp("{")) {
                openings.add(token.start());
            }
        }
        List<Boolean> startsWithBrace = new ArrayList<>();
        int offset = 0;
        for (String line : lines) {
            startsWithBrace.add(openings.contains(offset + line.length() - line.stripLeading().length()));
            offset += line.length() + 1;
        }
        for (int i = 1; i < lines.size(); i++) {
            if (!startsWithBrace.get(i)) {
                continue;
            }
            String line = lines.get(i);
            String stripped = line.strip();
            String afterBrace = stripped.substring(1);
            String afterBraceTrimmed = afterBrace.strip();
            int previousIndex = findAttachCandidateIndex(lines, i - 1);
            if (previousIndex < 0) {
                continue;
            }
            String previous = lines.get(previousIndex);
            String previousTrimmed = stripInlineComment(previous).stripTrailing();
            if (previousTrimmed.isEmpty() || previousTrimmed.endsWith("{")) {
                continue;
            }
            if (afterBraceTrimmed.isEmpty()) {
                lines.set(previousIndex, insertBraceBeforeInlineComment(previous));
                lines.subList(previousIndex + 1, i + 1).clear();
                startsWithBrace.subList(previousIndex + 1, i + 1).clear();
                i = previousIndex;
            } else {
                String indent = previous.substring(0, previous.length() - previous.stripLeading().length()) + "\t";
                String body = afterBrace;
                lines.set(previousIndex, insertBraceBeforeInlineComment(previous));
                lines.set(i, body);
                lines.set(i, indent + body.stripLeading());
            }
        }
        return String.join("\n", lines);
    }

    private String breakAfterOpeningBraces(String text) {
        List<JavaSourceTokens.Token> tokens = JavaSourceTokens.scan(text);
        Set<Integer> candidates = new HashSet<>();
        Map<Integer, Integer> braceEnds = new HashMap<>();
        for (int i = 0; i < tokens.size(); i++) {
            JavaSourceTokens.Token token = tokens.get(i);
            if (token.text().equals("{")) {
                if (i + 1 < tokens.size() && !tokens.get(i + 1).text().equals("}")
                        && text.substring(token.end(), tokens.get(i + 1).start()).indexOf('\n') < 0) {
                    candidates.add(token.start());
                    braceEnds.put(token.start(), token.end());
                }
            }
        }
        if (candidates.isEmpty()) {
            return text;
        }
        candidates.retainAll(bodyOpenings(text, tokens));
        StringBuilder result = new StringBuilder(text);
        for (int opening : candidates.stream().sorted(Comparator.reverseOrder()).toList()) {
            int braceEnd = braceEnds.get(opening);
            int end = braceEnd;
            while (end < text.length() && (text.charAt(end) == ' ' || text.charAt(end) == '\t')) {
                end++;
            }
            int lineStart = text.lastIndexOf('\n', opening) + 1;
            int indentEnd = lineStart;
            while (indentEnd < opening && (text.charAt(indentEnd) == ' ' || text.charAt(indentEnd) == '\t')) {
                indentEnd++;
            }
            result.replace(braceEnd, end, "\n" + text.substring(lineStart, indentEnd) + "\t");
        }
        return result.toString();
    }

    private Set<Integer> bodyOpenings(String text, List<JavaSourceTokens.Token> tokens) {
        var compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            throw new IllegalStateException("LeftCurly formatting requires a full JDK.");
        }
        var closingBraces = new HashMap<Integer, Integer>();
        for (JavaSourceTokens.Token token : tokens) {
            if (token.text().equals("}")) {
                closingBraces.put(token.end(), token.companion());
            }
        }
        List<String> prefixes = List.of("", "class __FormatumFragment {\n",
                "class __FormatumFragment {\nvoid __fragment() {\n");
        String errors = "";
        for (int nesting = 0; nesting < prefixes.size(); nesting++) {
            String prefix = prefixes.get(nesting);
            String source = prefix + text + "\n}".repeat(nesting);
            var diagnostics = new DiagnosticCollector<JavaFileObject>();
            var file = new SimpleJavaFileObject(URI.create("string:///FormatumLeftCurly.java"), JavaFileObject.Kind.SOURCE) {
                @Override
                public CharSequence getCharContent(boolean ignoreEncodingErrors) {
                    return source;
                }
            };
            try (var manager = compiler.getStandardFileManager(diagnostics, Locale.ROOT, null)) {
                var task = (JavacTask) compiler.getTask(new StringWriter(), manager, diagnostics,
                        List.of("-proc:none", "--enable-preview", "--source", Integer.toString(Runtime.version().feature())),
                        null, List.of(file));
                var unit = task.parse().iterator().next();
                if (diagnostics.getDiagnostics().stream().anyMatch(d -> d.getKind() == Diagnostic.Kind.ERROR)) {
                    if (nesting == 0) {
                        errors = diagnostics.getDiagnostics().toString();
                    }
                    continue;
                }
                var positions = Trees.instance(task).getSourcePositions();
                Set<Integer> openings = new HashSet<>();
                new TreeScanner<Void, Void>() {
                    private void addBody(Tree tree) {
                        int end = (int) positions.getEndPosition(unit, tree) - prefix.length();
                        Integer opening = closingBraces.get(end);
                        if (opening != null) {
                            openings.add(opening);
                        }
                    }

                    @Override
                    public Void visitBlock(BlockTree tree, Void unused) {
                        addBody(tree);
                        return super.visitBlock(tree, unused);
                    }

                    @Override
                    public Void visitClass(ClassTree tree, Void unused) {
                        if (tree.getKind() == Tree.Kind.ENUM && !getIgnoreEnums().getOrElse(true)) {
                            addBody(tree);
                        }
                        return super.visitClass(tree, unused);
                    }
                }.scan(unit, null);
                return openings;
            } catch (IOException exception) {
                throw new IllegalStateException("Unable to parse Java source for LeftCurly formatting.", exception);
            }
        }
        throw new IllegalArgumentException("Unable to parse Java source for LeftCurly formatting: " + errors);
    }

    private static int findAttachCandidateIndex(List<String> lines, int start) {
        for (int i = start; i >= 0; i--) {
            String line = lines.get(i);
            if (line.trim().isEmpty()) {
                continue;
            }
            String codePart = stripInlineComment(line).trim();
            if (codePart.isEmpty() || codePart.equals("*/")) {
                continue;
            }
            return i;
        }
        return -1;
    }

    private static String insertBraceBeforeInlineComment(String line) {
        int slashIndex = line.indexOf("//");
        if (slashIndex < 0) {
            return line.stripTrailing() + " {";
        }
        String beforeComment = line.substring(0, slashIndex).stripTrailing();
        String comment = line.substring(slashIndex).stripLeading();
        return beforeComment + " { " + comment;
    }

    private static String stripInlineComment(String line) {
        int slashIndex = line.indexOf("//");
        if (slashIndex < 0) {
            String trimmed = line.trim();
            if (trimmed.equals("*/")) {
                return "";
            }
            return line;
        }
        return line.substring(0, slashIndex);
    }

    private String moveLeftCurlyToNewLine(List<String> lines) {
        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            int braceIndex = line.lastIndexOf('{');
            if (braceIndex <= 0) {
                continue;
            }

            String afterBrace = line.substring(braceIndex + 1).trim();
            if (!afterBrace.isEmpty()) {
                continue;
            }

            String beforeBrace = line.substring(0, braceIndex).stripTrailing();
            if (beforeBrace.isEmpty()) {
                continue;
            }

            String indent = line.substring(0, line.indexOf(beforeBrace));
            lines.set(i, beforeBrace);
            lines.add(i + 1, indent + "{");
            i++;
        }
        return String.join("\n", lines);
    }
}
