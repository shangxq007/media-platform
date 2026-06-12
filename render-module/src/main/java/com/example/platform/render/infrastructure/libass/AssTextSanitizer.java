package com.example.platform.render.infrastructure.libass;

import java.util.regex.Pattern;

/**
 * Sanitizes user-supplied text before writing into ASS Dialogue lines.
 *
 * <p>ASS override tags use {@code {\\tag}} syntax. If user text contains
 * these patterns, it can inject malicious override commands (repositioning,
 * font loading, clipping, etc.). This sanitizer neutralizes all override tags
 * while preserving the text content.
 *
 * <h3>Threat model</h3>
 * <ul>
 *   <li>{@code {\pos(0,0)}} — reposition subtitle</li>
 *   <li>{@code {\fnMaliciousFont}} — load arbitrary font</li>
 *   <li>{@code {\clip(...)}} — clipping attack</li>
 *   <li>{@code {\p1}} — drawing mode</li>
 *   <li>{@code {\alpha&H00&}} — transparency manipulation</li>
 * </ul>
 */
public final class AssTextSanitizer {

    private static final Pattern OVERRIDE_TAG = Pattern.compile("\\\\\\{[^}]*\\}");
    private static final Pattern OPEN_BRACE = Pattern.compile("\\{");
    private static final Pattern CLOSE_BRACE = Pattern.compile("\\}");

    private static final int MAX_TEXT_LENGTH = 10000;

    private AssTextSanitizer() {}

    /**
     * Sanitize user text for safe inclusion in an ASS Dialogue line.
     *
     * <p>Transformations:
     * <ol>
     *   <li>Truncate to {@value MAX_TEXT_LENGTH} characters</li>
     *   <li>Replace {@code \r\n} and {@code \r} with {@code \N} (ASS line break)</li>
     *   <li>Remove all {@code {}} braces to prevent override tag injection</li>
     *   <li>Remove backslashes that could form override sequences</li>
     * </ol>
     *
     * @param text the raw user text
     * @return sanitized text safe for ASS Dialogue
     */
    public static String sanitize(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }

        String result = text;

        // Truncate excessively long text
        if (result.length() > MAX_TEXT_LENGTH) {
            result = result.substring(0, MAX_TEXT_LENGTH);
        }

        // Step 1: Convert raw newlines to ASS \N
        result = result.replace("\r\n", "\\N");
        result = result.replace("\r", "\\N");
        result = result.replace("\n", "\\N");

        // Step 2: Remove braces entirely — neutralizes all override tags
        result = result.replace("{", "");
        result = result.replace("}", "");

        // Step 3: Remove backslashes that are NOT part of \N, \n, \h
        // Process character by character to preserve valid ASS escapes
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < result.length(); i++) {
            char c = result.charAt(i);
            if (c == '\\' && i + 1 < result.length()) {
                char next = result.charAt(i + 1);
                if (next == 'N' || next == 'n' || next == 'h') {
                    // Valid ASS escape — preserve
                    sb.append(c).append(next);
                    i++; // skip next char
                }
                // Other backslash sequences — skip (remove)
            } else {
                sb.append(c);
            }
        }

        return sb.toString();
    }

    /**
     * Validate that a style name contains only safe characters.
     * ASS style names should be alphanumeric with spaces/dashes.
     */
    public static boolean isValidStyleName(String styleName) {
        if (styleName == null || styleName.isBlank()) return false;
        return styleName.matches("[a-zA-Z0-9 _-]+");
    }
}
