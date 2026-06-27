package com.example.platform.render.domain.timeline.compile;

/**
 * Exception thrown during timeline compile when an unsupported or invalid
 * construct is encountered.
 *
 * <p>Used for fail-closed behavior: if v0 encounters an unsupported effect,
 * transition, or invalid timeline structure, it throws this exception
 * rather than silently ignoring or producing incorrect output.</p>
 */
public class TimelineCompileException extends RuntimeException {

    private final String errorCode;
    private final String field;

    public TimelineCompileException(String message) {
        super(message);
        this.errorCode = "TIMELINE_COMPILE_ERROR";
        this.field = null;
    }

    public TimelineCompileException(String errorCode, String field, String message) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    public TimelineCompileException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = "TIMELINE_COMPILE_ERROR";
        this.field = null;
    }

    public String errorCode() {
        return errorCode;
    }

    public String field() {
        return field;
    }

    /**
     * Creates an exception for unsupported constructs.
     */
    public static TimelineCompileException unsupported(String constructType, String detail) {
        return new TimelineCompileException(
                "UNSUPPORTED_CONSTRUCT",
                constructType,
                "Unsupported " + constructType + " in v0 compile: " + detail);
    }

    /**
     * Creates an exception for missing required fields.
     */
    public static TimelineCompileException missingField(String field) {
        return new TimelineCompileException(
                "MISSING_FIELD",
                field,
                "Required field missing for compile: " + field);
    }

    /**
     * Creates an exception for invalid data.
     */
    public static TimelineCompileException invalidData(String field, String detail) {
        return new TimelineCompileException(
                "INVALID_DATA",
                field,
                "Invalid data in field '" + field + "': " + detail);
    }
}
