package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.Objects;
import java.util.OptionalLong;

/**
 * Operation to decode compressed media into raw frames/samples.
 *
 * <p>Corresponds to {@link ExecutionStepKind#DECODE}.
 */
public record DecodeOperation(
        String codec,
        OptionalLong startFrame,
        OptionalLong endFrame,
        boolean hardwareAccelerated
) implements Serializable, MediaOperation {

    public DecodeOperation {
        Objects.requireNonNull(codec, "codec");
        if (codec.isBlank()) throw new IllegalArgumentException("codec must not be blank");
        Objects.requireNonNull(startFrame, "startFrame");
        Objects.requireNonNull(endFrame, "endFrame");
    }

    /**
     * Creates a full decode operation for the given codec.
     */
    public static DecodeOperation of(String codec) {
        return new DecodeOperation(codec, OptionalLong.empty(), OptionalLong.empty(), false);
    }

    /**
     * Creates a partial decode operation for a frame range.
     */
    public static DecodeOperation range(String codec, long startFrame, long endFrame) {
        return new DecodeOperation(codec, OptionalLong.of(startFrame), OptionalLong.of(endFrame), false);
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.DECODE;
    }

    @Override
    public String operationType() {
        return "DECODE";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    /**
     * Returns true if this is a partial decode (frame range specified).
     */
    public boolean isPartial() {
        return startFrame.isPresent() || endFrame.isPresent();
    }

    @Override
    public String canonicalForm() {
        return "decode{" +
                "codec=" + codec +
                ",start=" + startFrame.stream().mapToObj(String::valueOf).findFirst().orElse("") +
                ",end=" + endFrame.stream().mapToObj(String::valueOf).findFirst().orElse("") +
                ",hwaccel=" + hardwareAccelerated +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
