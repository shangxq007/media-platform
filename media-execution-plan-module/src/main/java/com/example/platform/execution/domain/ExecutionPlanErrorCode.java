package com.example.platform.execution.domain;

import java.io.Serializable;

/**
 * Typed error codes for the Media Execution Plan V1 module.
 *
 * <p>Each error retains: planId, stepId, inputId, outputId, tenantId, artifactId,
 * expected, actual, detail. No sensitive credentials in errors.
 * Never degrades to IllegalArgumentException, generic RuntimeException, or HTTP 500.
 */
public sealed interface ExecutionPlanErrorCode extends Serializable {

    String codeString();
    String title();
    int status();

    enum Code implements ExecutionPlanErrorCode {
        // Plan-level errors
        EXECUTION_PLAN_NOT_FOUND("EXECUTION-404-001", "Execution plan not found", 404),
        EXECUTION_PLAN_INVALID("EXECUTION-400-001", "Execution plan invalid", 400),
        EXECUTION_PLAN_SCHEMA_UNSUPPORTED("EXECUTION-400-002", "Execution plan schema version unsupported", 400),
        EXECUTION_PLAN_DIGEST_MISMATCH("EXECUTION-409-001", "Execution plan digest mismatch", 409),
        EXECUTION_PLAN_NOT_DETERMINISTIC("EXECUTION-400-003", "Execution plan is not deterministic", 400),

        // Input binding errors
        EXECUTION_PLAN_INPUT_NOT_FOUND("EXECUTION-404-002", "Input artifact not found", 404),
        EXECUTION_PLAN_INPUT_NOT_AVAILABLE("EXECUTION-409-002", "Input artifact not available", 409),
        EXECUTION_PLAN_INPUT_DIGEST_MISMATCH("EXECUTION-409-003", "Input artifact digest mismatch", 409),
        EXECUTION_PLAN_INPUT_MEDIA_TYPE_MISMATCH("EXECUTION-409-004", "Input artifact media type mismatch", 409),
        EXECUTION_PLAN_CROSS_TENANT_INPUT("EXECUTION-403-001", "Cross-tenant input access denied", 403),

        // Step errors
        EXECUTION_PLAN_STEP_NOT_FOUND("EXECUTION-404-003", "Execution step not found", 404),
        EXECUTION_PLAN_DUPLICATE_STEP("EXECUTION-409-005", "Duplicate step", 409),
        EXECUTION_PLAN_OPERATION_INVALID("EXECUTION-400-004", "Execution operation invalid", 400),

        // Dependency errors
        EXECUTION_PLAN_DUPLICATE_DEPENDENCY("EXECUTION-409-006", "Duplicate dependency", 409),
        EXECUTION_PLAN_SELF_DEPENDENCY("EXECUTION-400-005", "Self-dependency prohibited", 400),
        EXECUTION_PLAN_CYCLE("EXECUTION-400-006", "Cycle detected", 400),
        EXECUTION_PLAN_ORPHAN_OUTPUT("EXECUTION-400-007", "Output with no producing step", 400),
        EXECUTION_PLAN_MISSING_PRODUCER("EXECUTION-400-008", "Missing producer step", 400),
        EXECUTION_PLAN_OUTPUT_CONFLICT("EXECUTION-409-007", "Duplicate output declaration", 409),

        // Resource/capability errors
        EXECUTION_PLAN_RESOURCE_INVALID("EXECUTION-400-009", "Resource requirement invalid", 400),
        EXECUTION_PLAN_CAPABILITY_INVALID("EXECUTION-400-010", "Capability requirement invalid", 400),

        // Cache errors
        EXECUTION_PLAN_NON_DETERMINISTIC_CACHE_KEY("EXECUTION-400-011", "Non-deterministic cache key rejected", 400);

        private final String code;
        private final String title;
        private final int status;

        Code(String code, String title, int status) {
            this.code = code;
            this.title = title;
            this.status = status;
        }

        @Override
        public String codeString() { return code; }
        @Override
        public String title() { return title; }
        @Override
        public int status() { return status; }
    }

    /**
     * Structured error record carrying all context fields.
     */
    record Error(
            ExecutionPlanErrorCode code,
            String planId,
            String stepId,
            String inputId,
            String outputId,
            String tenantId,
            String artifactId,
            String expected,
            String actual,
            String detail
    ) implements Serializable {
        public Error {
            if (code == null) throw new IllegalArgumentException("code required");
        }

        public static Builder builder(ExecutionPlanErrorCode code) {
            return new Builder(code);
        }

        public static final class Builder {
            private final ExecutionPlanErrorCode code;
            private String planId;
            private String stepId;
            private String inputId;
            private String outputId;
            private String tenantId;
            private String artifactId;
            private String expected;
            private String actual;
            private String detail;

            Builder(ExecutionPlanErrorCode code) { this.code = code; }

            public Builder planId(String v) { this.planId = v; return this; }
            public Builder stepId(String v) { this.stepId = v; return this; }
            public Builder inputId(String v) { this.inputId = v; return this; }
            public Builder outputId(String v) { this.outputId = v; return this; }
            public Builder tenantId(String v) { this.tenantId = v; return this; }
            public Builder artifactId(String v) { this.artifactId = v; return this; }
            public Builder expected(String v) { this.expected = v; return this; }
            public Builder actual(String v) { this.actual = v; return this; }
            public Builder detail(String v) { this.detail = v; return this; }

            public Error build() {
                return new Error(code, planId, stepId, inputId, outputId, tenantId, artifactId, expected, actual, detail);
            }
        }
    }

    /**
     * Exception type for Execution Plan domain errors.
     */
    class ExecutionPlanDomainException extends RuntimeException {
        private final Error error;

        public ExecutionPlanDomainException(Error error) {
            super(error.code().title() + " [" + error.code().codeString() + "]");
            this.error = error;
        }

        public Error error() { return error; }
        public ExecutionPlanErrorCode code() { return error.code(); }
    }
}
