package com.example.platform.fontpipeline.sanitize;

/**
 * Raised when the sanitization infrastructure itself fails (e.g. the sandbox cannot be loaded).
 *
 * <p>It is not used for {@link SanitizationOutcome#REJECTED} or
 * {@link SanitizationOutcome#UNAVAILABLE}, which are normal {@link SanitizedFontResult} returns.
 */
public class FontSanitizationException extends RuntimeException {

    public FontSanitizationException(String message) {
        super(message);
    }

    public FontSanitizationException(String message, Throwable cause) {
        super(message, cause);
    }
}
