package com.example.platform.render.domain.timeline.semantics.error;

import java.util.*;

/**
 * Stable error codes for the timeline media semantics domain.
 */
public sealed interface TimelineError {

    /**
     * Returns the error code.
     */
    ErrorCode code();

    /**
     * Returns the entity ID associated with this error (may be null).
     */
    String entityId();

    /**
     * Returns the entity type (may be null).
     */
    String entityType();

    /**
     * Returns the parameter path (may be null).
     */
    String parameter();

    /**
     * Returns the expected value (may be null).
     */
    String expected();

    /**
     * Returns the actual value (may be null).
     */
    String actual();

    /**
     * Returns relation endpoints if applicable (may be empty).
     */
    List<String> relationEndpoints();

    /**
     * Error code enumeration.
     */
    public enum ErrorCode {
        // Time
        TIMELINE_TIME_INVALID("TIMELINE_TIME_INVALID"),
        TIMELINE_SOURCE_RANGE_INVALID("TIMELINE_SOURCE_RANGE_INVALID"),
        TIMELINE_PLAYBACK_RATE_INVALID("TIMELINE_PLAYBACK_RATE_INVALID"),

        // Transition
        TIMELINE_TRANSITION_ENDPOINT_NOT_FOUND("TIMELINE_TRANSITION_ENDPOINT_NOT_FOUND"),
        TIMELINE_TRANSITION_ENDPOINT_INCOMPATIBLE("TIMELINE_TRANSITION_ENDPOINT_INCOMPATIBLE"),
        TIMELINE_TRANSITION_DURATION_INVALID("TIMELINE_TRANSITION_DURATION_INVALID"),
        TIMELINE_TRANSITION_HANDLE_INSUFFICIENT("TIMELINE_TRANSITION_HANDLE_INSUFFICIENT"),
        TIMELINE_TRANSITION_DUPLICATE_AT_CUT("TIMELINE_TRANSITION_DUPLICATE_AT_CUT"),

        // Effect
        TIMELINE_EFFECT_DEFINITION_UNKNOWN("TIMELINE_EFFECT_DEFINITION_UNKNOWN"),
        TIMELINE_EFFECT_VERSION_UNSUPPORTED("TIMELINE_EFFECT_VERSION_UNSUPPORTED"),
        TIMELINE_EFFECT_PARAMETER_INVALID("TIMELINE_EFFECT_PARAMETER_INVALID"),
        TIMELINE_EFFECT_MEDIA_TYPE_INCOMPATIBLE("TIMELINE_EFFECT_MEDIA_TYPE_INCOMPATIBLE"),

        // Automation
        TIMELINE_AUTOMATION_TARGET_NOT_FOUND("TIMELINE_AUTOMATION_TARGET_NOT_FOUND"),
        TIMELINE_AUTOMATION_PARAMETER_INVALID("TIMELINE_AUTOMATION_PARAMETER_INVALID"),
        TIMELINE_KEYFRAME_TIME_INVALID("TIMELINE_KEYFRAME_TIME_INVALID"),
        TIMELINE_KEYFRAME_DUPLICATE_TIME("TIMELINE_KEYFRAME_DUPLICATE_TIME"),

        // General
        TIMELINE_VALIDATION_ORDER_INVALID("TIMELINE_VALIDATION_ORDER_INVALID"),
        TIMELINE_ENTITY_NOT_FOUND("TIMELINE_ENTITY_NOT_FOUND"),
        TIMELINE_DUPLICATE_ENTITY("TIMELINE_DUPLICATE_ENTITY");

        private final String codeString;

        ErrorCode(String codeString) {
            this.codeString = codeString;
        }

        public String codeString() {
            return codeString;
        }
    }

    /**
     * Record implementation of TimelineError.
     */
    record Error(
        ErrorCode code,
        String entityId,
        String entityType,
        String parameter,
        String expected,
        String actual,
        List<String> relationEndpoints
    ) implements TimelineError {
        public Error {
            if (relationEndpoints == null) relationEndpoints = List.of();
            else relationEndpoints = List.copyOf(relationEndpoints);
        }

        public static Builder builder(ErrorCode code) {
            return new Builder(code);
        }

        public static final class Builder {
            private final ErrorCode code;
            private String entityId;
            private String entityType;
            private String parameter;
            private String expected;
            private String actual;
            private List<String> relationEndpoints = List.of();

            Builder(ErrorCode code) {
                this.code = code;
            }

            public Builder entityId(String entityId) { this.entityId = entityId; return this; }
            public Builder entityType(String entityType) { this.entityType = entityType; return this; }
            public Builder parameter(String parameter) { this.parameter = parameter; return this; }
            public Builder expected(String expected) { this.expected = expected; return this; }
            public Builder actual(String actual) { this.actual = actual; return this; }
            public Builder relationEndpoints(String... endpoints) {
                this.relationEndpoints = List.of(endpoints);
                return this;
            }

            public Error build() {
                return new Error(code, entityId, entityType, parameter, expected, actual, relationEndpoints);
            }
        }
    }

    /**
     * Exception thrown when validation fails.
     */
    class ValidationException extends RuntimeException {
        private final List<Error> errors;

        public ValidationException(List<Error> errors) {
            super("Timeline validation failed: " + errors.size() + " error(s)");
            this.errors = List.copyOf(errors);
        }

        public List<Error> errors() {
            return errors;
        }
    }
}
