package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.Map;
import java.util.Objects;

/**
 * Operation to generate new media content (AI synthesis, procedural generation).
 *
 * <p>Corresponds to {@link ExecutionStepKind#GENERATE}.
 */
public record GeneratedMediaOperation(
        String generatorType,
        String modelId,
        Map<String, String> generatorParameters,
        long seed
) implements Serializable, MediaOperation {

    public GeneratedMediaOperation {
        Objects.requireNonNull(generatorType, "generatorType");
        if (generatorType.isBlank()) throw new IllegalArgumentException("generatorType must not be blank");
        Objects.requireNonNull(generatorParameters, "generatorParameters");
        generatorParameters = Map.copyOf(generatorParameters);
    }

    /**
     * Creates an AI-generated media operation.
     */
    public static GeneratedMediaOperation ai(String modelId, Map<String, String> params) {
        return new GeneratedMediaOperation("ai_generation", modelId, params, 0L);
    }

    /**
     * Creates a deterministic generated media operation with a fixed seed.
     */
    public static GeneratedMediaOperation deterministic(String generatorType, long seed) {
        return new GeneratedMediaOperation(generatorType, null, Map.of(), seed);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.GENERATE;
    }

    @Override
    public String operationType() {
        return "GENERATED_MEDIA";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "generatedMedia{" +
                "type=" + generatorType +
                ",model=" + (modelId != null ? modelId : "") +
                ",params=" + generatorParameters.entrySet().stream()
                        .sorted(Map.Entry.comparingByKey())
                        .toList() +
                ",seed=" + seed +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
