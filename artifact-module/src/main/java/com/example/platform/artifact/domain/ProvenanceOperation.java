package com.example.platform.artifact.domain;

import java.io.Serializable;
import java.util.Objects;

/**
 * Versioned operation description for provenance tracking.
 *
 * <p>Describes the operation that produced one or more provenance edges.
 * Never stores plaintext credentials, full Authorization Headers, temporary signed URLs,
 * or unredacted Prompt Secrets.
 */
public record ProvenanceOperation(
        String operationId,
        int operationVersion,
        String operationCategory,
        String deterministicClassification,
        String executionReference,
        String capabilityId,
        String providerId,
        String modelId,
        String modelVersion,
        String renderBackend,
        String executionProvider,
        String attemptId,
        String requestDigest,
        String resultDigest
) implements Serializable {

    public ProvenanceOperation {
        Objects.requireNonNull(operationId, "operationId");
        if (operationId.isBlank()) throw new IllegalArgumentException("operationId must not be blank");
        if (operationVersion < 1) throw new IllegalArgumentException("operationVersion must be >= 1");
        Objects.requireNonNull(operationCategory, "operationCategory");
        if (operationCategory.isBlank()) throw new IllegalArgumentException("operationCategory must not be blank");
        Objects.requireNonNull(attemptId, "attemptId");
        if (attemptId.isBlank()) throw new IllegalArgumentException("attemptId must not be blank");
        Objects.requireNonNull(requestDigest, "requestDigest");
        Objects.requireNonNull(resultDigest, "resultDigest");
    }

    public static Builder builder(String operationId, int operationVersion, String operationCategory, String attemptId, String requestDigest, String resultDigest) {
        return new Builder(operationId, operationVersion, operationCategory, attemptId, requestDigest, resultDigest);
    }

    public static final class Builder {
        private final String operationId;
        private final int operationVersion;
        private final String operationCategory;
        private String deterministicClassification;
        String executionReference;
        String capabilityId;
        String providerId;
        String modelId;
        String modelVersion;
        String renderBackend;
        String executionProvider;
        private final String attemptId;
        private final String requestDigest;
        private final String resultDigest;

        Builder(String operationId, int operationVersion, String operationCategory, String attemptId, String requestDigest, String resultDigest) {
            this.operationId = operationId;
            this.operationVersion = operationVersion;
            this.operationCategory = operationCategory;
            this.attemptId = attemptId;
            this.requestDigest = requestDigest;
            this.resultDigest = resultDigest;
        }

        public Builder deterministicClassification(String v) { this.deterministicClassification = v; return this; }
        public Builder executionReference(String v) { this.executionReference = v; return this; }
        public Builder capabilityId(String v) { this.capabilityId = v; return this; }
        public Builder providerId(String v) { this.providerId = v; return this; }
        public Builder modelId(String v) { this.modelId = v; return this; }
        public Builder modelVersion(String v) { this.modelVersion = v; return this; }
        public Builder renderBackend(String v) { this.renderBackend = v; return this; }
        public Builder executionProvider(String v) { this.executionProvider = v; return this; }

        public ProvenanceOperation build() {
            return new ProvenanceOperation(operationId, operationVersion, operationCategory,
                    deterministicClassification, executionReference, capabilityId, providerId,
                    modelId, modelVersion, renderBackend, executionProvider,
                    attemptId, requestDigest, resultDigest);
        }
    }

    /**
     * Canonical serialization with deterministic field ordering.
     */
    public String canonicalForm() {
        StringBuilder sb = new StringBuilder("operation{");
        sb.append("id=").append(operationId);
        sb.append(",version=").append(operationVersion);
        sb.append(",category=").append(operationCategory);
        if (deterministicClassification != null) sb.append(",detClass=").append(deterministicClassification);
        if (executionReference != null) sb.append(",execRef=").append(executionReference);
        if (capabilityId != null) sb.append(",capability=").append(capabilityId);
        if (providerId != null) sb.append(",provider=").append(providerId);
        if (modelId != null) sb.append(",model=").append(modelId);
        if (modelVersion != null) sb.append(",modelVer=").append(modelVersion);
        if (renderBackend != null) sb.append(",backend=").append(renderBackend);
        if (executionProvider != null) sb.append(",execProvider=").append(executionProvider);
        sb.append(",attempt=").append(attemptId);
        sb.append(",requestDigest=").append(requestDigest);
        sb.append(",resultDigest=").append(resultDigest);
        sb.append('}');
        return sb.toString();
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
