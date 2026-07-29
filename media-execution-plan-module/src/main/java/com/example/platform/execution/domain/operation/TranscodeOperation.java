package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.Objects;

/**
 * Operation to transcode media from one format/codec to another.
 *
 * <p>Corresponds to {@link ExecutionStepKind#ENCODE}.
 */
public record TranscodeOperation(
        String sourceCodec,
        String targetCodec,
        String targetContainer,
        String targetBitrateMode,
        long targetBitrate,
        String qualityPreset
) implements Serializable, MediaOperation {

    public TranscodeOperation {
        Objects.requireNonNull(targetCodec, "targetCodec");
        if (targetCodec.isBlank()) throw new IllegalArgumentException("targetCodec must not be blank");
        Objects.requireNonNull(targetContainer, "targetContainer");
        if (targetContainer.isBlank()) throw new IllegalArgumentException("targetContainer must not be blank");
        if (targetBitrate < 0) throw new IllegalArgumentException("targetBitrate must be non-negative");
        if (qualityPreset != null && qualityPreset.isBlank())
            throw new IllegalArgumentException("qualityPreset must not be blank");
    }

    /**
     * Creates a transcode operation to the target codec/container.
     */
    public static TranscodeOperation to(String targetCodec, String targetContainer) {
        return new TranscodeOperation(null, targetCodec, targetContainer, "vbr", 0L, "medium");
    }

    /**
     * Creates a transcode operation with a specific target bitrate.
     */
    public static TranscodeOperation withBitrate(String codec, String container, long bitrate) {
        return new TranscodeOperation(null, codec, container, "cbr", bitrate, "medium");
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.ENCODE;
    }

    @Override
    public String operationType() {
        return "TRANSCODE";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "transcode{" +
                "src=" + (sourceCodec != null ? sourceCodec : "") +
                ",codec=" + targetCodec +
                ",container=" + targetContainer +
                ",mode=" + (targetBitrateMode != null ? targetBitrateMode : "") +
                ",bitrate=" + targetBitrate +
                ",preset=" + (qualityPreset != null ? qualityPreset : "") +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
