package com.communi.suggestu.formatum.adhibe.formatting.steps;

import dev.lukebemish.immaculate.FileFormatter;
import dev.lukebemish.immaculate.FormattingStep;

import javax.inject.Inject;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Normalises Javadoc paragraphs to match Checkstyle's JavadocParagraph:
 * <ul>
 *   <li>One blank line between each paragraph.</li>
 *   <li>Every paragraph but the first gets a {@code <p>} tag on
 *       its own line immediately before the first word.</li>
 *   <li>No {@code <p>} tag directly before block-level HTML
 *       (e.g. {@code <table>}, {@code <pre>}, {@code <ul>}).</li>
 * </ul>
 */
public abstract class CheckstyleJavadocParagraphStep extends FormattingStep {

    private static final Set<String> BLOCK_TAGS = Set.of(
            "address", "blockquote", "div", "dl",
            "h1", "h2", "h3", "h4", "h5", "h6",
            "hr", "ol", "p", "pre", "table", "ul"
    );

    @Inject
    public CheckstyleJavadocParagraphStep() {
    }

    @Override
    public FileFormatter formatter() {
        return (fileName, text) -> apply(TextFormattingUtils.normalizeNewlines(text));
    }

    private String apply(String text) {
        String[] lines = text.split("\n", -1);

        // Phase 1: Find all javadoc block comments
        List<int[]> javadocRanges = findJavadocRanges(lines);
        if (javadocRanges.isEmpty()) {
            return text;
        }

        // Phase 2: Apply paragraph normalisation to each javadoc
        String[] result = lines;
        for (int[] range : javadocRanges) {
            int start = range[0];
            int end = range[1];
            String[] fixed = fixJavadocParagraph(result, start, end);
            // Splice the fixed javadoc back into the full result
            String[] before = result.length > start ? java.util.Arrays.copyOfRange(result, 0, start) : new String[0];
            String[] after = result.length > end + 1 ? java.util.Arrays.copyOfRange(result, end + 1, result.length) : new String[0];
            String[] newResult = new String[before.length + fixed.length + after.length];
            System.arraycopy(before, 0, newResult, 0, before.length);
            System.arraycopy(fixed, 0, newResult, before.length, fixed.length);
            System.arraycopy(after, 0, newResult, before.length + fixed.length, after.length);
            result = newResult;
            // Adjust ranges for the next javadoc (they shift as lines are modified)
            for (int[] r : javadocRanges) {
                if (r[0] > end) {
                    int diff = result.length - lines.length;
                    r[0] += diff;
                    r[1] += diff;
                }
            }
            lines = result;
        }

        return String.join("\n", result);
    }

    /**
     * Returns [startLine, endLine] (0-based) for each javadoc block comment.
     */
    private List<int[]> findJavadocRanges(String[] lines) {
        List<int[]> ranges = new ArrayList<>();
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().startsWith("/**")) {
                int start = i;
                int end = start;
                for (int j = i + 1; j < lines.length; j++) {
                    if (lines[j].trim().contains("*/")) {
                        end = j;
                        break;
                    }
                }
                ranges.add(new int[]{start, end});
                i = end;
            }
        }
        return ranges;
    }

    private String[] fixJavadocParagraph(String[] lines, int javadocStart, int javadocEnd) {
        List<String> result = new ArrayList<>();

        // We track whether the *first* javadoc content line has been seen
        // (the first paragraph doesn't need <p>).
        boolean firstParagraphSeen = false;
        // Whether we just saw a <p> tag (so the next content is still the same paragraph)
        boolean afterP = false;
        // Whether we are inside a block-level HTML tag
        boolean inBlockTag = false;

        for (int i = javadocStart; i <= javadocEnd; i++) {
            String line = lines[i];
            String trimmed = line.trim();

            // Keep the opening /** line
            if (i == javadocStart) {
                result.add(line);
                continue;
            }

            // Keep the closing */ line
            if (i == javadocEnd && trimmed.endsWith("*/")) {
                result.add(line);
                continue;
            }

            // Detect block tags
            String openedTag = detectBlockTagOpen(trimmed);
            String closedTag = detectBlockTagClose(trimmed);

            if (closedTag != null) {
                // Closing a block tag
                inBlockTag = false;
                result.add(line);
                continue;
            }

            if (openedTag != null) {
                // Opening a block tag
                // Check if <p> directly precedes this block tag — remove the <p>
                handleBlockTagBeforeBlock(result, trimmed, openedTag, inBlockTag, line, javadocStart);
                if (!inBlockTag) {
                    inBlockTag = true;
                }
                continue;
            }

            // Check for <p> tag (standalone)
            boolean hasP = trimmed.startsWith("<p>") || trimmed.startsWith("<P>")
                    || trimmed.startsWith("* <p>") || trimmed.startsWith("* <P>");

            // Check for non-blank text content
            String textContent = extractTextContent(trimmed);
            boolean hasText = textContent != null && !textContent.isEmpty();

            if (hasText) {
                // This is a text paragraph line
                if (inBlockTag) {
                    // Inside block tag — don't add <p>, don't track paragraphs
                    result.add(line);
                    continue;
                }

                if (!firstParagraphSeen) {
                    // First paragraph — no <p> needed
                    firstParagraphSeen = true;
                    afterP = false;
                    result.add(line);
                    continue;
                }

                // Not the first paragraph:
                // Check: was there a <p> before this (after a blank line)?
                if (!afterP) {
                    // Skip <p> insertion for Javadoc tag lines (@param, @return,
                    // @throws, etc.) — these are the final paragraph and don't
                    // need a <p> tag before them.
                    if (textContent.startsWith("@")) {
                        result.add(line);
                        continue;
                    }
                    // Need to insert <p> + blank line
                    insertPBefore(result, line, javadocStart);
                }
                result.add(line);
                afterP = false;
            } else {
                // Blank or <p> line (no text content)
                if (hasP) {
                    afterP = true;
                    // Ensure there's exactly one blank line before this <p> tag
                    // (Checkstyle requires exactly one blank line before <p>)
                    if (firstParagraphSeen) {
                        // Check if the most recent line in the result is already a blank line.
                        // If so, no action needed. Otherwise insert a blank line before <p>.
                        String lastLine = result.get(result.size() - 1).trim();
                        boolean alreadyHasPrecedingBlank = lastLine.equals("*")
                                || lastLine.equals("* ")
                                || lastLine.isEmpty();
                        if (!alreadyHasPrecedingBlank) {
                            // Find the indentation from another line in the result.
                            String indent = findJavadocIndentation(result);
                            result.add(indentLineWithPrefix(indent));
                        }
                    }
                    result.add(line);
                } else {
                    // Blank line — reset afterP
                    afterP = false;
                    result.add(line);
                }
            }
        }

        return String.join("\n", result).split("\n", -1);
    }

    /**
     * When a block tag is found, check if it is directly preceded by <p>.
     * If so, remove the <p> (and its preceding blank line).
     */
    private void handleBlockTagBeforeBlock(List<String> result, String trimmed,
                                           String tagName, boolean currentlyInBlock,
                                           String currentLine, int javadocStart) {
        // If inside a block tag, nested <p> before block is OK
        if (currentlyInBlock) {
            result.add(currentLine);
            return;
        }

        // Look backwards for a <p> line that precedes this block tag
        boolean foundPriorP = false;
        int pLineIdx = -1;
        int blankLineBeforeP = -1;

        for (int i = result.size() - 1; i >= 0; i--) {
            String r = result.get(i).trim();
            String rContent = r;
            // Strip "* " prefix to check for <p>
            if (rContent.startsWith("*")) {
                rContent = rContent.substring(1).stripLeading();
            }
            if (rContent.startsWith("<p>") || rContent.startsWith("<P>")) {
                foundPriorP = true;
                pLineIdx = i;
                // Also look for a blank line before <p>
                if (i > 0 && (result.get(i - 1).trim().equals("*")
                        || result.get(i - 1).trim().equals("* "))) {
                    blankLineBeforeP = i - 1;
                }
                break;
            }
            if (r.startsWith("/**")) {
                break;
            }
        }

        if (foundPriorP) {
            // Remove <p> line only — keep the blank line before it
            // (it separates the previous paragraph from the block tag)
            result.remove(pLineIdx);
        }

        result.add(currentLine);
    }

    private String detectBlockTagOpen(String trimmed) {
        if (trimmed.isEmpty()) return null;
        // Strip leading "* " prefix if present (javadoc line)
        String content = trimmed;
        if (content.startsWith("*")) {
            content = content.substring(1).stripLeading();
        }
        if (content.isEmpty()) return null;
        for (String tag : BLOCK_TAGS) {
            String open = "<" + tag + ">";
            String openUp = "<" + tag.toUpperCase() + ">";
            if (content.startsWith(open)) {
                // Standalone <p> tag (no other content) — treat as paragraph marker, not block
                String after = content.length() > open.length()
                        ? content.substring(open.length())
                        : "";
                String afterUp = content.length() > openUp.length()
                        ? content.substring(openUp.length())
                        : "";
                if (tag.equals("p") && (after.isEmpty() || after.strip().isEmpty())
                        || (afterUp.isEmpty() || afterUp.strip().isEmpty())) {
                    return null;  // Standalone <p> is not a block tag
                }
                return tag;
            }
            if (content.startsWith(openUp)) {
                String after = content.length() > openUp.length()
                        ? content.substring(openUp.length())
                        : "";
                if (after.isEmpty() || after.strip().isEmpty()) {
                    return null;  // Standalone <P> is not a block tag
                }
                return tag;
            }
        }
        return null;
    }

    private String detectBlockTagClose(String trimmed) {
        if (trimmed.isEmpty()) return null;
        // Strip leading "* " prefix if present (javadoc line)
        String content = trimmed;
        if (content.startsWith("*")) {
            content = content.substring(1).stripLeading();
        }
        if (content.isEmpty()) return null;
        for (String tag : BLOCK_TAGS) {
            String close = "</" + tag + ">";
            String closeUp = "</" + tag.toUpperCase() + ">";
            if (content.startsWith(close)) {
                return tag;
            }
            if (content.startsWith(closeUp)) {
                return tag;
            }
        }
        return null;
    }

    /**
     * When a new paragraph content line is found but there is no <p> before it,
     * insert <p> + blank line before the current line.
     */
    private void insertPBefore(List<String> result, String currentLine, int javadocStart) {
        // Find the last blank line before the current insertion point
        // and turn it into a <p> line.
        // The blank line itself stays (it's the separator), we just add
        // the <p> after it.

        // Strategy: find the blank line closest to the end of result,
        // and insert "<p>" after it.
        int lastBlankIdx = -1;
        for (int i = result.size() - 1; i >= 0; i--) {
            String r = result.get(i).trim();
            if (r.equals("*") || r.equals("* ")) {
                lastBlankIdx = i;
                break;
            }
            if (r.startsWith("/**")) {
                // First paragraph — no <p> needed
                return;
            }
            // Hit a content line — look backward from there
            break;
        }

        if (lastBlankIdx < 0) {
            // No blank line found. This means paragraphs run together
            // without separation. We need to insert a blank + <p> before
            // the current line (which is at result.size()).
            // But we should insert before the current line, so just
            // let the caller add the current line after this.
            return;
        }

        // Insert <p> right after the blank line
        String pLine = indentLine(result.get(lastBlankIdx)) + "<p>";
        result.add(lastBlankIdx + 1, pLine);
    }

    /**
     * Looks through a result list for a line containing a javadoc '*' marker
     * and returns its leading whitespace (indentation). Falls back to "".
     */
    private String findJavadocIndentation(List<String> result) {
        for (String line : result) {
            String trimmed = line.trim();
            if (trimmed.startsWith("/**")) {
                // Opening: get the indent before /**
                String raw = line;
                int i = 0;
                while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
                    i++;
                }
                if (i > 0) return raw.substring(0, i);
            }
            if (trimmed.startsWith("*")) {
                // Content line: get the indent before *
                String raw = line;
                int i = 0;
                while (i < raw.length() && Character.isWhitespace(raw.charAt(i))) {
                    i++;
                }
                if (i > 0) return raw.substring(0, i);
            }
        }
        return "";
    }

    /**
     * Builds a blank javadoc line: {@code indent + "*"}.
     */
    private String indentLineWithPrefix(String indent) {
        return indent + "*";
    }

    private String indentLine(String line) {
        // Extract leading whitespace
        int i = 0;
        while (i < line.length() && Character.isWhitespace(line.charAt(i))) {
            i++;
        }
        // Return the indent + "* " (standard javadoc marker with trailing space)
        if (i >= line.length()) {
            // For an empty line, return a standard javadoc blank line
            return " *";
        }
        // Skip past the "*"
        if (line.charAt(i) == '*') {
            i++;
        }
        // Include space after "*" if present
        if (i < line.length() && line.charAt(i) == ' ') {
            i++;
        }
        // Always end with "* " for consistency
        return line.substring(0, i) + " ";
    }

    /**
     * Extracts text content from a trimmed javadoc line.
     * Returns null if the line has no meaningful text content
     * (it's a blank line, a tag line, etc.).
     */
    private String extractTextContent(String trimmed) {
        if (trimmed.isEmpty()) return null;

        // Strip leading "* " prefix if present
        String content = trimmed;
        if (content.startsWith("*")) {
            content = content.substring(1).stripLeading();
        }
        if (content.isEmpty()) return null;

        // Strip <p> or <P> if present
        if (content.startsWith("<p>") || content.startsWith("<P>")) {
            content = content.substring(3).stripLeading();
        }
        // Strip surrounding <p>...</p>
        if (content.startsWith("<p>") && content.endsWith("</p>") || content.endsWith("</P>")) {
            content = content.substring(3, content.length() - 4).stripLeading();
        }
        // Strip surrounding <P>...</P>
        if (content.startsWith("<P>") && content.endsWith("</P>")) {
            content = content.substring(3, content.length() - 4).stripLeading();
        }

        return content.isEmpty() ? null : content;
    }
}
