package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;

/**
 * Operation to generate a thumbnail image from media.
 *
 * <p>Corresponds to {@link ExecutionStepKind#GENERATE}.
 */
public record ThumbnailOperation(
        int width,
        int height,
        String format,
        long timeOffsetMs,
        boolean maintainAspectRatio
) implements Serializable, MediaOperation {

    public ThumbnailOperation {
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
        if (height <= 0) throw new IllegalArgumentException("height must be positive");
        if (format == null || format.isBlank()) throw new IllegalArgumentException("format required");
    }

    /**
     * Creates a thumbnail operation at the specified resolution.
     */
    public static ThumbnailOperation at(int width, int height) {
        return new ThumbnailOperation(width, height, "jpg", 0L, true);
    }

    /**
     * Creates a thumbnail operation at a specific time offset.
     */
    public static ThumbnailOperation atTime(int width, int height, long timeOffsetMs) {
        return new ThumbnailOperation(width, height, "jpg", timeOffsetMs, true);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.GENERATE;
    }

    @Override
    public String operationType() {
        return "THUMBNAIL";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "thumbnail{" +
                "w=" + width +
                ",h=" + height +
                ",fmt=" + format +
                ",offset=" + timeOffsetMs +
                ",aspect=" + maintainAspectRatio +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
