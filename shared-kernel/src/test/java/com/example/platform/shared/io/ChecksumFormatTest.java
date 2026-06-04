package com.example.platform.shared.io;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChecksumFormatTest {

    @Test
    void shouldAllowNull() {
        assertTrue(ChecksumFormat.isValid(null));
        assertNull(ChecksumFormat.normalizeSha256(null));
    }

    @Test
    void shouldAcceptValidSha256() {
        String valid = "sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
        assertTrue(ChecksumFormat.isValid(valid));
        assertEquals(valid, ChecksumFormat.normalizeSha256(valid));
    }

    @Test
    void shouldNormalizeUppercaseHex() {
        String input = "sha256:9F86D081884C7D659A2FEAA0C55AD015A3BF4F1B2B0B822CD15D6C15B0F00A08";
        String expected = "sha256:9f86d081884c7d659a2feaa0c55ad015a3bf4f1b2b0b822cd15d6c15b0f00a08";
        assertTrue(ChecksumFormat.isValid(input));
        assertEquals(expected, ChecksumFormat.normalizeSha256(input));
    }

    @Test
    void shouldRejectInvalidFormats() {
        assertFalse(ChecksumFormat.isValid("abc"));
        assertFalse(ChecksumFormat.isValid("md5:abc123"));
        assertFalse(ChecksumFormat.isValid("sha256:short"));
        assertFalse(ChecksumFormat.isValid("sha256:not-hex-gg"));
        assertFalse(ChecksumFormat.isValid("s3://bucket/key"));
    }

    @Test
    void shouldThrowOnNormalizeInvalid() {
        assertThrows(IllegalArgumentException.class, () -> ChecksumFormat.normalizeSha256("abc"));
        assertThrows(IllegalArgumentException.class, () -> ChecksumFormat.normalizeSha256("md5:abc123"));
    }
}
