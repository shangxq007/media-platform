package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.Objects;
import java.util.Set;

/**
 * Operation to analyze media content.
 *
 * <p>Corresponds to {@link ExecutionStepKind#ANALYZE}.
 * Examples: scene detection, face recognition, object detection, quality analysis.
 */
public record AnalysisOperation(
        String analysisType,
        Set<String> analysisTargets,
        String modelId,
        float confidenceThreshold
) implements Serializable, MediaOperation {

    public AnalysisOperation {
        Objects.requireNonNull(analysisType, "analysisType");
        if (analysisType.isBlank()) throw new IllegalArgumentException("analysisType must not be blank");
        Objects.requireNonNull(analysisTargets, "analysisTargets");
        analysisTargets = Set.copyOf(analysisTargets);
        if (confidenceThreshold < 0 || confidenceThreshold > 1)
            throw new IllegalArgumentException("confidenceThreshold must be in [0,1]");
    }

    /**
     * Creates a scene detection analysis.
     */
    public static AnalysisOperation sceneDetection() {
        return new AnalysisOperation("scene_detection", Set.of("transitions"), null, 0.5f);
    }

    /**
     * Creates a face detection analysis.
     */
    public static AnalysisOperation faceDetection(float confidence) {
        return new AnalysisOperation("face_detection", Set.of("faces"), null, confidence);
    }

    /**
     * Creates a quality analysis operation.
     */
    public static AnalysisOperation qualityAnalysis() {
        return new AnalysisOperation("quality", Set.of("psnr", "ssim", "vmaf"), null, 0.0f);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.ANALYZE;
    }

    @Override
    public String operationType() {
        return "ANALYSIS";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "analysis{" +
                "type=" + analysisType +
                ",targets=" + analysisTargets.stream().sorted().toList() +
                ",model=" + (modelId != null ? modelId : "") +
                ",conf=" + confidenceThreshold +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
