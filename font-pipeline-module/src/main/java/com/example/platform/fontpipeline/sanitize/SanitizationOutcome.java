package com.example.platform.fontpipeline.sanitize;

/** Result of a font sanitization attempt. */
public enum SanitizationOutcome {

    /** The sanitizer produced sanitized bytes. */
    SANITIZED,

    /** The sanitizer examined the input and refused it (malformed / unsafe). */
    REJECTED,

    /** The sanitizer could not run (binary missing, sandbox unavailable, timeout). */
    UNAVAILABLE
}
