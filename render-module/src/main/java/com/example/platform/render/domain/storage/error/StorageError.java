package com.example.platform.render.domain.storage.error;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

public sealed interface StorageError extends Serializable {
    ErrorCode code();
    String providerId();
    String namespace();
    String objectId();
    String replicaId();
    String writeSessionId();
    String expected();
    String actual();
    String operation();
    List<String> relationEndpoints();

    public enum ErrorCode implements Serializable {
        STORAGE_PROVIDER_UNKNOWN, STORAGE_PROVIDER_UNAVAILABLE, STORAGE_CAPABILITY_UNSUPPORTED,
        STORAGE_NAMESPACE_INVALID, STORAGE_PLACEMENT_POLICY_VIOLATION, STORAGE_CROSS_TENANT_ACCESS_DENIED,
        STORAGE_WRITE_SESSION_NOT_FOUND, STORAGE_WRITE_SESSION_STATE_INVALID,
        STORAGE_WRITE_ALREADY_COMMITTED, STORAGE_IDEMPOTY_CONFLICT,
        STORAGE_OBJECT_NOT_FOUND, STORAGE_OBJECT_ALREADY_EXISTS, STORAGE_IMMUTABILITY_VIOLATION,
        STORAGE_CONTENT_LENGTH_MISMATCH, STORAGE_CONTENT_DIGEST_MISMATCH, STORAGE_RANGE_INVALID,
        STORAGE_REPLICA_STATE_INVALID, STORAGE_REPLICA_NOT_AVAILABLE, STORAGE_DELETE_NOT_AUTHORIZED,
        STORAGE_LEASE_EXPIRED, STORAGE_PRESIGN_NOT_SUPPORTED, STORAGE_NOT_IMPLEMENTED,
        STORAGE_TENANT_ISOLATION_VIOLATION, STORAGE_ENCRYPTION_REQUIRED, STORAGE_IDEMPOTENT_COMMIT_REQUIRED;

        public String codeString() { return name(); }
    }

    record Error(
        ErrorCode code, String providerId, String namespace, String objectId,
        String replicaId, String writeSessionId, String expected, String actual,
        String operation, List<String> relationEndpoints
    ) implements StorageError {
        public Error {
            Objects.requireNonNull(code, "code");
            if (relationEndpoints == null) relationEndpoints = List.of();
            else relationEndpoints = List.copyOf(relationEndpoints);
        }

        public static Builder builder(ErrorCode code) { return new Builder(code); }

        public static final class Builder {
            private final ErrorCode code;
            private String providerId, namespace, objectId, replicaId, writeSessionId;
            private String expected, actual, operation;
            private List<String> relationEndpoints = List.of();
            Builder(ErrorCode code) { this.code = code; }
            public Builder providerId(String v) { this.providerId = v; return this; }
            public Builder namespace(String v) { this.namespace = v; return this; }
            public Builder objectId(String v) { this.objectId = v; return this; }
            public Builder replicaId(String v) { this.replicaId = v; return this; }
            public Builder writeSessionId(String v) { this.writeSessionId = v; return this; }
            public Builder expected(String v) { this.expected = v; return this; }
            public Builder actual(String v) { this.actual = v; return this; }
            public Builder operation(String v) { this.operation = v; return this; }
            public Builder entityType(String v) { return this; }
            public Builder relationEndpoints(String... v) { this.relationEndpoints = List.of(v); return this; }
            public Error build() { return new Error(code, providerId, namespace, objectId, replicaId, writeSessionId, expected, actual, operation, relationEndpoints); }
        }
    }

    class ValidationException extends RuntimeException {
        private final List<Error> errors;
        public ValidationException(List<Error> errors) {
            super("Storage validation failed: " + errors.size() + " error(s)");
            this.errors = List.copyOf(errors);
        }
        public List<Error> errors() { return errors; }
    }
}
