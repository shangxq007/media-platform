package com.example.platform.fontpipeline.sanitize;

import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.security.FontSecurityState;
import java.util.List;
import java.util.Objects;

/**
 * Result of a font sanitization attempt.
 *
 * <p>Only {@link SanitizationOutcome#SANITIZED} carries bytes: {@code sanitizedBytes} and
 * {@code contentDigest} (SHA-256 of the sanitized bytes) are non-null exactly then, and are null for
 * {@link SanitizationOutcome#REJECTED} / {@link SanitizationOutcome#UNAVAILABLE}. A rejected or
 * unavailable font produces no parseable bytes.
 *
 * @param state security lifecycle state ({@code SANITIZED} on success, otherwise {@code RAW})
 * @param sanitizedBytes sanitized font bytes, or {@code null}
 * @param sourceDigest digest of the raw input
 * @param contentDigest SHA-256 of {@code sanitizedBytes}, or {@code null}
 * @param outcome sanitization outcome
 * @param diagnostics captured sanitizer diagnostics (may be empty)
 */
public record SanitizedFontResult(
        FontSecurityState state,
        byte[] sanitizedBytes,
        FontContentDigest sourceDigest,
        FontContentDigest contentDigest,
        SanitizationOutcome outcome,
        List<String> diagnostics) {

    public SanitizedFontResult {
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(sourceDigest, "sourceDigest");
        Objects.requireNonNull(outcome, "outcome");
        Objects.requireNonNull(diagnostics, "diagnostics");
        diagnostics = List.copyOf(diagnostics);
        if (outcome == SanitizationOutcome.SANITIZED) {
            Objects.requireNonNull(sanitizedBytes, "sanitizedBytes must be present when SANITIZED");
            Objects.requireNonNull(contentDigest, "contentDigest must be present when SANITIZED");
        } else if (sanitizedBytes != null || contentDigest != null) {
            throw new IllegalArgumentException(
                    "sanitizedBytes and contentDigest must be null unless the outcome is SANITIZED");
        }
        if (sanitizedBytes != null) {
            sanitizedBytes = sanitizedBytes.clone();
        }
    }

    @Override
    public byte[] sanitizedBytes() {
        return sanitizedBytes == null ? null : sanitizedBytes.clone();
    }
}
