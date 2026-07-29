package com.example.platform.execution.domain.operation;

import com.example.platform.execution.domain.ExecutionStepKind;

import java.io.Serializable;
import java.util.List;
import java.util.Objects;

/**
 * Operation to package media into a container format (MP4, DASH, HLS, etc.).
 *
 * <p>Corresponds to {@link ExecutionStepKind#PACKAGE}.
 */
public record PackageOperation(
        String containerFormat,
        List<String> includedStreams,
        String muxingMode,
        String initializationPattern,
        String segmentPattern
) implements Serializable, MediaOperation {

    public PackageOperation {
        Objects.requireNonNull(containerFormat, "containerFormat");
        if (containerFormat.isBlank()) throw new IllegalArgumentException("containerFormat must not be blank");
        Objects.requireNonNull(includedStreams, "includedStreams");
        includedStreams = List.copyOf(includedStreams);
    }

    /**
     * Creates an MP4 packaging operation.
     */
    public static PackageOperation mp4(List<String> streams) {
        return new PackageOperation("mp4", streams, "standard", null, null);
    }

    /**
     * Creates a DASH packaging operation.
     */
    public static PackageOperation dash(List<String> streams) {
        return new PackageOperation("dash", streams, "segmented", "init.m4s", "seg_$Number$.m4s");
    }

    /**
     * Creates an HLS packaging operation.
     */
    public static PackageOperation hls(List<String> streams) {
        return new PackageOperation("hls", streams, "segmented", null, "seg_$Number$.ts");
    }

    @Override
    public ExecutionStepKind stepKind() {
        return ExecutionStepKind.PACKAGE;
    }

    @Override
    public String operationType() {
        return "PACKAGE";
    }

    @Override
    public int schemaVersion() {
        return 1;
    }

    @Override
    public String canonicalForm() {
        return "package{" +
                "format=" + containerFormat +
                ",streams=" + includedStreams +
                ",mode=" + (muxingMode != null ? muxingMode : "") +
                ",init=" + (initializationPattern != null ? initializationPattern : "") +
                ",seg=" + (segmentPattern != null ? segmentPattern : "") +
                ",v=" + schemaVersion() +
                '}';
    }

    @Override
    public String toString() {
        return canonicalForm();
    }
}
