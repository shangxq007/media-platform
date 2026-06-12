package com.example.platform.render.infrastructure.font;

import java.util.regex.Pattern;

/**
 * Validates font identifiers to prevent path injection attacks.
 *
 * <p>A fontId is a logical identifier, NOT a filesystem path. It must be a safe
 * slug or UUID that can be used as part of a filename without traversal risk.
 */
public final class FontIdPolicy {

    private static final Pattern SAFE_ID_PATTERN = Pattern.compile("^[A-Za-z0-9._-]{1,128}$");

    private FontIdPolicy() {}

    /**
     * Validate a fontId for safe use in file paths.
     *
     * @param fontId the font identifier to validate
     * @throws IllegalArgumentException if the fontId is unsafe
     */
    public static void requireValidFontId(String fontId) {
        if (fontId == null || fontId.isBlank()) {
            throw new IllegalArgumentException("fontId must not be blank");
        }

        String trimmed = fontId.trim();

        // Reject path separators
        if (trimmed.contains("/") || trimmed.contains("\\")) {
            throw new IllegalArgumentException("fontId must not contain path separators: " + trimmed);
        }

        // Reject traversal
        if (trimmed.contains("..")) {
            throw new IllegalArgumentException("fontId must not contain traversal: " + trimmed);
        }

        // Reject null bytes
        if (trimmed.contains("\0")) {
            throw new IllegalArgumentException("fontId must not contain null bytes");
        }

        // Reject URL schemes
        if (trimmed.contains("://")) {
            throw new IllegalArgumentException("fontId must not contain URL scheme: " + trimmed);
        }

        // Reject Windows drive letters
        if (trimmed.length() >= 2 && Character.isLetter(trimmed.charAt(0)) && trimmed.charAt(1) == ':') {
            throw new IllegalArgumentException("fontId must not be a Windows drive path: " + trimmed);
        }

        // Reject percent-encoded traversal
        String lower = trimmed.toLowerCase();
        if (lower.contains("%2e%2e") || lower.contains("%2f") || lower.contains("%5c")
                || lower.contains("%252e") || lower.contains("%252f") || lower.contains("%255c")) {
            throw new IllegalArgumentException("fontId must not contain encoded traversal: " + trimmed);
        }

        // Must match safe ID pattern
        if (!SAFE_ID_PATTERN.matcher(trimmed).matches()) {
            throw new IllegalArgumentException("fontId contains invalid characters: " + trimmed);
        }
    }

    /**
     * Check if a fontId is valid without throwing.
     */
    public static boolean isValidFontId(String fontId) {
        try {
            requireValidFontId(fontId);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
