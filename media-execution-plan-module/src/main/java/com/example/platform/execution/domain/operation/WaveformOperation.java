package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;

/**
 * Operation to generate an audio waveform visualization.
 *
 * <p>Corresponds to {@link ExecutionStepKind#GENERATE}.
 */
public record WaveformOperation(
        int width,
        int height,
        String colorScheme,
        int samplesPerPixel
) implements Serializable, MediaOperation {

    public WaveformOperation {
        if (width <= 0) throw new IllegalArgumentException("width must be positive");
        if (height <= 0) throw new IllegalArgumentException("height must be positive");
        if (colorScheme == null || colorScheme.isBlank()) throw new IllegalArgumentException("colorScheme required");
        if (samplesPerPixel <= 0) throw new IllegalArgumentException("samplesPerPixel must be positive");
    }

    /**
     * Creates a waveform generation operation.
     */
    public static WaveformOperation standard(int width, int height) {
        return new WaveformOperation(width, height, "blue", 256);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.GENERATE;
    }

    @Override
    public String operationType() {
        return "WAVEFORM";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "waveform{" +
                "w=" + width +
                ",h=" + height +
                ",color=" + colorScheme +
                ",spp=" + samplesPerPixel +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
