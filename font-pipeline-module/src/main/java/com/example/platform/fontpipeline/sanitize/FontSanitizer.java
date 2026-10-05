package com.example.platform.fontpipeline.sanitize;

/**
 * Structural/security sanitization of raw font bytes as the first pipeline stage.
 *
 * <p>Sanitization is executed outside the JVM, inside the sandbox; a request always yields a
 * {@link SanitizedFontResult} (never {@code null}). The only sanitized bytes that may continue
 * downstream are the OTS-produced output bytes.
 */
public interface FontSanitizer {

    /**
     * Sanitize the raw font bytes carried by {@code request}.
     *
     * @param request the raw font bytes and their source digest
     * @return the sanitization result (sanitized, rejected, or unavailable)
     * @throws FontSanitizationException if the sanitization infrastructure itself fails
     */
    SanitizedFontResult sanitize(FontSanitizeRequest request);
}
