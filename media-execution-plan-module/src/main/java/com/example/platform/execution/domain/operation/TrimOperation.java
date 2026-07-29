package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Operation to trim media to a specific time range.
 *
 * <p>Corresponds to {@link ExecutionStepKind#TRANSFORM}.
 */
public record TrimOperation(
        Duration sourceInPoint,
        Duration sourceOutPoint,
        boolean preserveAudioPitch
) implements Serializable, MediaOperation {

    public TrimOperation {
        Objects.requireNonNull(sourceInPoint, "sourceInPoint");
        Objects.requireNonNull(sourceOutPoint, "sourceOutPoint");
        if (sourceOutPoint.compareTo(sourceInPoint) <= 0)
            throw new IllegalArgumentException("outPoint must be after inPoint");
    }

    /**
     * Creates a trim operation from in-point to out-point.
     */
    public static TrimOperation of(Duration inPoint, Duration outPoint) {
        return new TrimOperation(inPoint, outPoint, false);
    }

    /**
     * Creates a trim operation that trims to the given duration starting at zero.
     */
    public static TrimOperation toDuration(Duration duration) {
        return new TrimOperation(Duration.ZERO, duration, false);
    }

    /**
     * Returns the trimmed duration.
     */
    public Duration trimmedDuration() {
        return sourceOutPoint.minus(sourceInPoint);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.TRANSFORM;
    }

    @Override
    public String operationType() {
        return "TRIM";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "trim{" +
                "in=" + sourceInPoint.toNanos() +
                ",out=" + sourceOutPoint.toNanos() +
                ",pitch=" + preserveAudioPitch +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
