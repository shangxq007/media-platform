package com.example.platform.artifact.domain;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Typed error codes for the Artifact and Provenance V1 module.
 *
 * <p>Each error retains: tenantId, artifactId, parentArtifactId, childArtifactId,
 * storageReplicaId, operationId, attemptId, expected, actual.
 * No sensitive credentials in errors.
 * Never degrades to IllegalArgumentException, generic RuntimeException, or HTTP 500.
 */
public sealed interface ArtifactErrorCode extends Serializable {

    String codeString();
    String title();
    int status();

    enum Code implements ArtifactErrorCode {
        ARTIFACT_NOT_FOUND("ARTIFACT-404-001", "Artifact not found", 404),
        ARTIFACT_ALREADY_EXISTS("ARTIFACT-409-001", "Artifact already exists", 409),
        ARTIFACT_STATE_INVALID("ARTIFACT-409-002", "Artifact state transition invalid", 409),
        ARTIFACT_CONTENT_DIGEST_MISMATCH("ARTIFACT-409-003", "Artifact content digest mismatch", 409),
        ARTIFACT_CONTENT_LENGTH_MISMATCH("ARTIFACT-409-004", "Artifact content length mismatch", 409),
        ARTIFACT_REPLICA_NOT_AVAILABLE("ARTIFACT-409-005", "Artifact replica not available", 409),
        ARTIFACT_REPLICA_BINDING_CONFLICT("ARTIFACT-409-006", "Artifact replica binding conflict", 409),
        ARTIFACT_IDEMPOTENCY_CONFLICT("ARTIFACT-409-007", "Artifact idempotency conflict", 409),
        ARTIFACT_SCHEMA_VERSION_INVALID("ARTIFACT-400-001", "Artifact schema version invalid", 400),
        ARTIFACT_PROVENANCE_ENDPOINT_NOT_FOUND("ARTIFACT-404-002", "Provenance endpoint not found", 404),
        ARTIFACT_PROVENANCE_CROSS_TENANT("ARTIFACT-403-001", "Provenance cross-tenant access denied", 403),
        ARTIFACT_PROVENANCE_SELF_REFERENCE("ARTIFACT-400-002", "Provenance self-reference prohibited", 400),
        ARTIFACT_PROVENANCE_DUPLICATE("ARTIFACT-409-008", "Provenance duplicate edge", 409),
        ARTIFACT_PROVENANCE_CYCLE("ARTIFACT-409-009", "Provenance cycle detected", 409),
        ARTIFACT_PROVENANCE_OPERATION_INVALID("ARTIFACT-400-003", "Provenance operation invalid", 400),
        ARTIFACT_TENANT_ISOLATION_VIOLATION("ARTIFACT-403-002", "Artifact tenant isolation violation", 403);

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
            ArtifactErrorCode code,
            String tenantId,
            String artifactId,
            String parentArtifactId,
            String childArtifactId,
            String storageReplicaId,
            String operationId,
            String attemptId,
            String expected,
            String actual
    ) implements Serializable {
        public Error {
            Objects.requireNonNull(code, "code");
        }

        public static Builder builder(ArtifactErrorCode code) {
            return new Builder(code);
        }

        public static final class Builder {
            private final ArtifactErrorCode code;
            private String tenantId;
            private String artifactId;
            private String parentArtifactId;
            private String childArtifactId;
            private String storageReplicaId;
            private String operationId;
            private String attemptId;
            private String expected;
            private String actual;

            Builder(ArtifactErrorCode code) { this.code = code; }

            public Builder tenantId(String v) { this.tenantId = v; return this; }
            public Builder artifactId(String v) { this.artifactId = v; return this; }
            public Builder parentArtifactId(String v) { this.parentArtifactId = v; return this; }
            public Builder childArtifactId(String v) { this.childArtifactId = v; return this; }
            public Builder storageReplicaId(String v) { this.storageReplicaId = v; return this; }
            public Builder operationId(String v) { this.operationId = v; return this; }
            public Builder attemptId(String v) { this.attemptId = v; return this; }
            public Builder expected(String v) { this.expected = v; return this; }
            public Builder actual(String v) { this.actual = v; return this; }

            public Error build() {
                return new Error(code, tenantId, artifactId, parentArtifactId, childArtifactId,
                        storageReplicaId, operationId, attemptId, expected, actual);
            }
        }
    }

    /**
     * Exception type for Artifact domain errors.
     */
    class ArtifactDomainException extends RuntimeException {
        private final Error error;

        public ArtifactDomainException(Error error) {
            super(error.code().title() + " [" + error.code().codeString() + "]");
            this.error = Objects.requireNonNull(error, "error");
        }

        public Error error() { return error; }
        public ArtifactErrorCode code() { return error.code(); }
    }

    /**
     * Exception type for provenance-specific errors.
     */
    class ProvenanceException extends RuntimeException {
        private final Error error;
        private final List<String> violations;

        public ProvenanceException(Error error, List<String> violations) {
            super(error.code().title() + " [" + error.code().codeString() + "]: " +
                    (violations != null ? String.join("; ", violations) : ""));
            this.error = Objects.requireNonNull(error, "error");
            this.violations = violations != null ? List.copyOf(violations) : List.of();
        }

        public Error error() { return error; }
        public ArtifactErrorCode code() { return error.code(); }
        public List<String> violations() { return violations; }
    }
}
