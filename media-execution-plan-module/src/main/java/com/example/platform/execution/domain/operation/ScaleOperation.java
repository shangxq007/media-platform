package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.Objects;

/**
 * Operation to scale/resize media to a target resolution.
 *
 * <p>Corresponds to {@link ExecutionStepKind#TRANSFORM}.
 */
public record ScaleOperation(
        int targetWidth,
        int targetHeight,
        String scalingAlgorithm,
        boolean maintainAspectRatio
) implements Serializable, MediaOperation {

    public ScaleOperation {
        if (targetWidth <= 0 && targetHeight <= 0)
            throw new IllegalArgumentException("at least one of targetWidth/targetHeight must be positive");
        Objects.requireNonNull(scalingAlgorithm, "scalingAlgorithm");
        if (scalingAlgorithm.isBlank()) throw new IllegalArgumentException("scalingAlgorithm must not be blank");
    }

    /**
     * Creates a scale operation with the target resolution.
     */
    public static ScaleOperation to(int width, int height) {
        return new ScaleOperation(width, height, "lanczos", true);
    }

    /**
     * Creates a scale operation that only constrains width (height auto-calculated).
     */
    public static ScaleOperation toWidth(int width) {
        return new ScaleOperation(width, 0, "lanczos", true);
    }

    /**
     * Creates a scale operation that only constrains height (width auto-calculated).
     */
    public static ScaleOperation toHeight(int height) {
        return new ScaleOperation(0, height, "lanczos", true);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.TRANSFORM;
    }

    @Override
    public String operationType() {
        return "SCALE";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "scale{" +
                "w=" + targetWidth +
                ",h=" + targetHeight +
                ",algo=" + scalingAlgorithm +
                ",aspect=" + maintainAspectRatio +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
