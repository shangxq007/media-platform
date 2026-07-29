package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;

/**
 * Operation to crop media to a region of interest.
 *
 * <p>Corresponds to {@link ExecutionStepKind#TRANSFORM}.
 */
public record CropOperation(
        int x,
        int y,
        int width,
        int height
) implements Serializable, MediaOperation {

    public CropOperation {
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
        if (height <= 0) throw new IllegalArgumentException("height must be positive");
        if (x < 0) throw new IllegalArgumentException("x must be non-negative");
        if (y < 0) throw new IllegalArgumentException("y must be non-negative");
    }

    /**
     * Creates a crop operation for the specified region.
     */
    public static CropOperation of(int x, int y, int width, int height) {
        return new CropOperation(x, y, width, height);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.TRANSFORM;
    }

    @Override
    public String operationType() {
        return "CROP";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "crop{" +
                "x=" + x +
                ",y=" + y +
                ",w=" + width +
                ",h=" + height +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
