package com.example.platform.shared.io;

import java.util.regex.Pattern;

/**
 * Validates and normalizes checksum strings.
 *
 * <p>Supported format: {@code sha256:<64 lowercase hex digits>}
 */
public final class ChecksumFormat {

    private static final Pattern SHA256_PATTERN =
            Pattern.compile("^sha256:[a-fA-F0-9]{64}$");

    private ChecksumFormat() {}

    /**
     * Returns true if the checksum is null (allowed) or matches sha256 format.
     */
    public static boolean isValid(String checksum) {
        return checksum == null || SHA256_PATTERN.matcher(checksum).matches();
    }

    /**
     * Normalizes a sha256 checksum to lowercase hex. Returns null if input is null.
     *
     * @throws IllegalArgumentException if checksum is non-null and does not match sha256 format
     */
    public static String normalizeSha256(String checksum) {
        if (checksum == null) return null;
        if (!SHA256_PATTERN.matcher(checksum).matches()) {
            throw new IllegalArgumentException(
                    "Invalid checksum format. Expected sha256:<64 hex>, got: " + checksum);
        }
        return "sha256:" + checksum.substring(7).toLowerCase();
    }
}
