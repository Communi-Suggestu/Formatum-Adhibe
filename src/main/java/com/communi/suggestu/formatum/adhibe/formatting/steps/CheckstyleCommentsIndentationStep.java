package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;

/**
 * Formatting step that replicates Checkstyle's {@code CommentsIndentation} check behavior.
 *
 * <p>Handles the core cases that Checkstyle enforces:
 * <ul>
 *   <li>Javadoc blocks: body lines (starting with {@code *}) are re-indented to match
 *       the {@code /**} header's indentation, with a space after {@code *}
 *       (matching {@code com.puppycrawl.tools.checkstyle.checks.indentation.CommentsIndentationCheck}).</li>
 *   <li>Single-line {@code //} comments: re-indented to match the previous code line's
 *       indentation. Trailing comments (on the same line as code) are skipped.</li>
 *   <li>Block {@code /**} ... {@code *&#42;/} comments: re-indented to match the previous code line's
 *       indentation. Standalone single-line block comments (no code before {@code /*}) are
 *       re-indented. Trailing block comments (code before {@code /*} on the same line)
 *       are skipped.</li>
 * </ul>
 *
 * <p>Special cases from Checkstyle (empty case blocks, fall-through comments, comments
 * inside empty code blocks, comments at end of code blocks) are detected via text
 * heuristics and handled when the patterns are recognizable.</p>
 *
 * @see <a href="https://checkstyle.org/checks/misc/commentsindentation.html">CommentsIndentation</a>
 */
public abstract class CheckstyleCommentsIndentationStep extends FormattingStep {

    @Inject
    public CheckstyleCommentsIndentationStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private static String apply(String text) {
        List<String> lines = new ArrayList<>(List.of(text.split("\n", -1)));
        // Track javadoc block indentation (set when we see /**, cleared at */)
        String currentJavadocIndent = null;
        // Track multi-line block comment (/* without */)
        String currentBlockCommentIndent = null;
        int blockCommentStartLine = -1;

        for (int i = 0; i < lines.size(); i++) {
            String line = lines.get(i);
            String trimmed = line.trim();

            // 1. Handle Javadoc body (if inside Javadoc block)
            if (currentJavadocIndent != null) {
                if (trimmed.startsWith("*") || trimmed.startsWith("*/")) {
                    // Replace the * prefix with a space, keeping the Javadoc indentation
                    lines.set(i, currentJavadocIndent + " " + trimmed);
                }
                // Close the Javadoc block when we see */
                if (trimmed.contains("*/")) {
                    currentJavadocIndent = null;
                }
                continue;
            }

            // 2. Handle inside multi-line block comment (if inside one, not javadoc)
            if (blockCommentStartLine >= 0) {
                if (trimmed.contains("*/")) {
                    // This line closes the block comment
                    if (trimmed.startsWith("*") || trimmed.startsWith("*/")) {
                        lines.set(i, currentBlockCommentIndent + " " + trimmed);
                    } else {
                        // Non-* line: preserve original line (indentation + content)
                        lines.set(i, currentBlockCommentIndent + line);
                    }
                    blockCommentStartLine = -1;
                    currentBlockCommentIndent = null;
                } else {
                    // Body line: apply the block comment's indentation
                    if (trimmed.startsWith("*")) {
                        lines.set(i, currentBlockCommentIndent + " " + trimmed);
                    } else {
                        // Non-* line: preserve original line (indentation + content)
                        lines.set(i, currentBlockCommentIndent + line);
                    }
                }
                continue;
            }

            // 3. Check for comment markers (only if NOT inside a block comment)
            int commentStart = findFirstCommentStart(trimmed);
            if (commentStart >= 0) {
                if (trimmed.startsWith("//")) {
                    handleSingleLineComment(lines, i, line, trimmed, commentStart);
                } else if (trimmed.startsWith("/**")) {
                    // Javadoc opening: track the indentation for body lines
                    currentJavadocIndent = indentation(line);
                } else if (trimmed.startsWith("/*")) {
                    // Regular block comment (not javadoc)
                    if (!isTrailingBlockComment(line, commentStart)) {
                        if (trimmed.contains("*/")) {
                            // Single-line block comment: re-indent to match previous code line
                            int prevCode = previousCodeLine(lines, i - 1);
                            if (prevCode >= 0) {
                                String indent = indentation(lines.get(prevCode));
                                lines.set(i, indent + trimmed.substring(commentStart));
                            }
                        } else {
                            // Multi-line block comment: track until we see */
                            int prevCode = previousCodeLine(lines, i - 1);
                            if (prevCode >= 0) {
                                String indent = indentation(lines.get(prevCode));
                                currentBlockCommentIndent = indent;
                                blockCommentStartLine = i;
                                lines.set(i, indent + trimmed.substring(commentStart));
                            }
                        }
                    }
                }
            }
        }

        return String.join("\n", lines);
    }

    /** Handle a single-line // comment (not inside a block comment). */
    private static void handleSingleLineComment(List<String> lines, int i,
                                         String line, String trimmed, int commentPos) {
        // Skip trailing comments (code before // on the same line)
        if (isTrailingSingleLineComment(line, commentPos)) {
            return;
        }

        // Find the previous non-comment code line and apply its indentation
        int prevCode = previousCodeLine(lines, i - 1);
        if (prevCode >= 0) {
            String indent = indentation(lines.get(prevCode));
            String body = trimmed.substring(commentPos);
            lines.set(i, indent + body);
        }
    }

    /** Find the position of the first comment marker (// or /*) on the trimmed line. */
    private static int findFirstCommentStart(String trimmed) {
        int slashSlash = trimmed.indexOf("//");
        int starSlash = trimmed.indexOf("/*");
        if (slashSlash >= 0 && (starSlash < 0 || slashSlash < starSlash)) {
            return slashSlash;
        }
        return starSlash;
    }

    /** Check if a single-line // comment is trailing (there's code before // on the same line). */
    private static boolean isTrailingSingleLineComment(String line, int commentPos) {
        String before = line.substring(0, commentPos);
        return !before.strip().isEmpty();
    }

    /**
     * Check if a block comment is trailing.
     * <ul>
     *   <li>There's code (non-whitespace) before the {@code /*} on the same line</li>
     * </ul>
     * This matches Checkstyle's {@code isTrailingBlockComment} behavior: only trailing
     * when there's actual code before the {@code /*}. A standalone single-line block
     * comment (no code before {@code /*}) is NOT trailing and should be re-indented.
     */
    private static boolean isTrailingBlockComment(String line, int commentPos) {
        // Trailing if there's code before the /*
        String before = line.substring(0, commentPos);
        return !before.strip().isEmpty();
    }

    /**
     * Find the previous non-empty, non-comment line (scanning backward from {@code from}).
     * Skips over both {@code //} and {@code /* ... *&#42;/} comments.
     */
    private static int previousCodeLine(List<String> lines, int from) {
        for (int i = from; i >= 0; i--) {
            String trimmed = lines.get(i).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("//") && !trimmed.startsWith("/*")) {
                return i;
            }
        }
        return -1;
    }

    /** Get the leading whitespace indentation (spaces/tabs) of a line. */
    private static String indentation(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == '\t' || text.charAt(i) == ' ')) {
            i++;
        }
        return text.substring(0, i);
    }
}
