package com.example.platform.fontpipeline.sanitize;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.security.FontSecurityState;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * Integration tests for {@link OtsFontSanitizer}, driving the real {@code /usr/bin/ots-sanitize}
 * through the bubblewrap sandbox. Skipped when OTS or the system font sample is absent.
 */
class OtsFontSanitizerTest {

    private static final Path OTS = Path.of("/usr/bin/ots-sanitize");
    private static final Path SAMPLE = Path.of("/usr/share/fonts/truetype/Roboto-Light.ttf");

    private final OtsFontSanitizer sanitizer = new OtsFontSanitizer();

    @BeforeAll
    static void requirePrerequisites() {
        assumeTrue(Files.isExecutable(OTS), "ots-sanitize is not installed at " + OTS);
        assumeTrue(Files.isRegularFile(SAMPLE), "font sample not installed: " + SAMPLE);
    }

    @Test
    void sanitizesValidTtf() {
        byte[] raw = read(SAMPLE);
        SanitizedFontResult result = sanitizer.sanitize(request(raw));

        assertEquals(SanitizationOutcome.SANITIZED, result.outcome());
        assertEquals(FontSecurityState.SANITIZED, result.state());
        byte[] sanitized = result.sanitizedBytes();
        assertNotNull(sanitized);
        assertTrue(sanitized.length > 0, "sanitized bytes must not be empty");
        assertEquals(FontContentDigest.ofBytes(sanitized), result.contentDigest(),
                "contentDigest is SHA-256 of the sanitized bytes");
        assertEquals(result.sourceDigest(), FontContentDigest.ofBytes(raw));
    }

    @Test
    void rejectsMalformedFont() {
        byte[] truncated = Arrays.copyOf(read(SAMPLE), 2000);
        SanitizedFontResult result = sanitizer.sanitize(request(truncated));

        assertEquals(SanitizationOutcome.REJECTED, result.outcome());
        assertEquals(FontSecurityState.RAW, result.state());
        assertNull(result.sanitizedBytes(), "a rejected font produces no parseable bytes");
        assertNull(result.contentDigest());
    }

    @Test
    void returnsSameContentDigestForIdenticalInput() {
        byte[] raw = read(SAMPLE);
        FontContentDigest first = sanitizer.sanitize(request(raw)).contentDigest();
        FontContentDigest second = sanitizer.sanitize(request(raw)).contentDigest();

        assertNotNull(first);
        assertEquals(first, second, "sanitization is idempotent for identical input");
    }

    @Test
    void contentDigestDiffersFromSourceDigest() {
        byte[] raw = read(SAMPLE);
        SanitizedFontResult result = sanitizer.sanitize(request(raw));

        assertNotEquals(result.contentDigest(), result.sourceDigest(),
                "OTS rewrites the font, so the sanitized digest differs from the raw digest");
        assertFalse(Arrays.equals(result.sanitizedBytes(), raw), "sanitized bytes differ from raw bytes");
    }

    @Test
    void diagnosticsCaptureOtsError() {
        byte[] truncated = Arrays.copyOf(read(SAMPLE), 2000);
        List<String> diagnostics = sanitizer.sanitize(request(truncated)).diagnostics();

        assertFalse(diagnostics.isEmpty(), "OTS diagnostics are captured");
        String joined = String.join("\n", diagnostics).toLowerCase(Locale.ROOT);
        assertTrue(joined.contains("error") || joined.contains("failed"),
                "diagnostics should surface the OTS rejection: " + diagnostics);
    }

    @Test
    void sanitizedBytesRoundTripThroughTheSandbox() {
        byte[] raw = read(SAMPLE);
        SanitizedFontResult result = sanitizer.sanitize(request(raw));

        // Re-sanitizing the sanitized bytes is stable (OTS is idempotent) and yields the same digest.
        SanitizedFontResult again = sanitizer.sanitize(new FontSanitizeRequest(
                result.sanitizedBytes(), result.contentDigest(), "font/ttf"));
        assertEquals(SanitizationOutcome.SANITIZED, again.outcome());
        assertEquals(result.contentDigest(), again.contentDigest());
    }

    private static FontSanitizeRequest request(byte[] raw) {
        return new FontSanitizeRequest(raw, FontContentDigest.ofBytes(raw), "font/ttf");
    }

    private static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
