package com.example.platform.render.ir;

/**
 * Stable machine-readable error codes for IR validation, canonicalization, and digest errors.
 *
 * <p>Each code has a fixed name (for serialization), a safe human-readable message template,
 * a retryability classification, and is guaranteed not to contain secret content.
 */
public enum IrErrorCode {

    /** Generic validation error. Retryable: depends on context. */
    VALIDATION_ERROR("VALIDATION_ERROR", "Validation failed", false),

    /** Schema version is missing, blank, or unsupported. Retryable: false. */
    UNSUPPORTED_SCHEMA_VERSION("UNSUPPORTED_SCHEMA_VERSION", "Unsupported or missing schema version", false),

    /** Time value is invalid (zero denominator, overflow, negative where prohibited). Retryable: false. */
    INVALID_TIME_VALUE("INVALID_TIME_VALUE", "Invalid time value", false),

    /** Duplicate identifier detected within a scope. Retryable: false. */
    DUPLICATE_IDENTIFIER("DUPLICATE_IDENTIFIER", "Duplicate identifier", false),

    /** Reference to an undeclared asset version. Retryable: false. */
    MISSING_ASSET_REFERENCE("MISSING_ASSET_REFERENCE", "Referenced asset version not declared", false),

    /** Unsupported or unknown extension key. Retryable: false. */
    UNSUPPORTED_EXTENSION("UNSUPPORTED_EXTENSION", "Unsupported extension", false),

    /** Canonical serialization failed. Retryable: false. */
    CANONICALIZATION_FAILED("CANONICALIZATION_FAILED", "Canonical serialization failed", false),

    /** Digest generation failed. Retryable: false. */
    DIGEST_GENERATION_FAILED("DIGEST_GENERATION_FAILED", "Digest generation failed", false);

    private final String code;
    private final String message;
    private final boolean retryable;

    IrErrorCode(String code, String message, boolean retryable) {
        this.code = code;
        this.message = message;
        this.retryable = retryable;
    }

    /** Stable machine-readable error code. */
    public String code() { return code; }

    /** Safe human-readable message (no secrets). */
    public String message() { return message; }

    /** Whether the error might be resolved by retry. */
    public boolean retryable() { return retryable; }
}
