package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Operation to compose multiple media sources into a single output.
 *
 * <p>Corresponds to {@link ExecutionStepKind#COMPOSE}.
 * Handles layering, transitions, and compositing of visual elements.
 */
public record ComposeOperation(
        List<String> layerOrder,
        String outputResolution,
        String outputFrameRate,
        String outputPixelAspectRatio,
        String backgroundColor
) implements Serializable, MediaOperation {

    public ComposeOperation {
        Objects.requireNonNull(layerOrder, "layerOrder");
        if (layerOrder.isEmpty()) throw new IllegalArgumentException("layerOrder must not be empty");
        layerOrder = List.copyOf(layerOrder);
        Objects.requireNonNull(outputResolution, "outputResolution");
        Objects.requireNonNull(outputFrameRate, "outputFrameRate");
    }

    /**
     * Creates a compose operation with the specified layers.
     */
    public static ComposeOperation layers(List<String> layers, String resolution, String frameRate) {
        return new ComposeOperation(layers, resolution, frameRate, "1:1", "black");
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.COMPOSE;
    }

    @Override
    public String operationType() {
        return "COMPOSE";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "compose{" +
                "layers=" + layerOrder +
                ",res=" + outputResolution +
                ",fps=" + outputFrameRate +
                ",par=" + (outputPixelAspectRatio != null ? outputPixelAspectRatio : "") +
                ",bg=" + (backgroundColor != null ? backgroundColor : "") +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
