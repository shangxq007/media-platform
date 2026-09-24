package com.example.platform.artifact.domain.typed;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record ConversionContract(String contractId, String contractVersion, ArtifactRequirement input, ArtifactRequirement output,
                                 List<ParameterDefinition> parameters, ExecutionMode executionMode, Determinism determinism,
                                 Cardinality cardinality, ResourceEstimate resourceEstimate, RetryPolicy retryPolicy,
                                 Map<String,String> compatibilityRules) {
    public ConversionContract {
        if (contractId == null || contractId.isBlank() || contractVersion == null || contractVersion.isBlank()) throw new IllegalArgumentException("contract identity is required");
        Objects.requireNonNull(input); Objects.requireNonNull(output); Objects.requireNonNull(executionMode); Objects.requireNonNull(determinism); Objects.requireNonNull(cardinality); Objects.requireNonNull(resourceEstimate); Objects.requireNonNull(retryPolicy);
        parameters = parameters == null ? List.of() : List.copyOf(parameters); compatibilityRules = compatibilityRules == null ? Map.of() : Map.copyOf(compatibilityRules);
        if (cardinality == Cardinality.ONE_TO_ONE && (input.maximumCount()!=1 || output.maximumCount()!=1)) throw new IllegalArgumentException("one-to-one cardinality contradicts counts");
    }
    public record ResourceEstimate(long units, String quotaClass) { public ResourceEstimate { if(units<0 || quotaClass==null || quotaClass.isBlank()) throw new IllegalArgumentException("invalid resource estimate"); } }
    public record RetryPolicy(boolean retryable, int maxAttempts, boolean cancellable) { public RetryPolicy { if(maxAttempts<1) throw new IllegalArgumentException("maxAttempts must be positive"); } }
}
