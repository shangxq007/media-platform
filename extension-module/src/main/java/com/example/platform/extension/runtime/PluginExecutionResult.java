package com.example.platform.extension.runtime;

import com.example.platform.shared.identity.ArtifactId;

import java.util.List;
import java.util.Objects;

/**
 * Canonical plugin execution result (frozen PRV2-ADR-006).
 *
 * <p>Durable media/file/model outputs are ALWAYS typed {@link ArtifactId} references —
 * never File, Path, InputStream, byte[], Mat, AVFrame, Packet, Tensor or
 * provider-native media objects (AR-PRV2-07, PRV2-RED-011,
 * RUNTIME_OBJECT_IS_NOT_PLATFORM_ARTIFACT).</p>
 *
 * @param status               terminal execution status
 * @param output               small typed result payload (nullable; bounded, never a raw media object)
 * @param artifactRefs         durable output artifact references (empty when none) — typed {@link ArtifactId} (GCR-2)
 * @param providerObservations provider observations (cost/usage metadata, nullable)
 * @param error                canonical error when status != SUCCEEDED (nullable)
 */
public record PluginExecutionResult(
        PluginExecutionStatus status,
        Object output,
        List<ArtifactId> artifactRefs,
        Object providerObservations,
        PluginRuntimeError error) {

    public PluginExecutionResult {
        Objects.requireNonNull(status, "status must not be null");
        if (artifactRefs == null) {
            artifactRefs = List.of();
        }
    }

    public static PluginExecutionResult succeeded(Object output, List<ArtifactId> artifactRefs, Object providerObservations) {
        return new PluginExecutionResult(PluginExecutionStatus.SUCCEEDED, output, artifactRefs, providerObservations, null);
    }

    public static PluginExecutionResult failed(PluginRuntimeError error) {
        return new PluginExecutionResult(PluginExecutionStatus.FAILED, null, List.of(), null, error);
    }

    public static PluginExecutionResult failed(PluginRuntimeErrorCategory category, String code, String message) {
        return failed(PluginRuntimeError.of(category, code, message));
    }

    public static PluginExecutionResult timedOut(String message) {
        return new PluginExecutionResult(PluginExecutionStatus.TIMED_OUT, null, List.of(), null,
                PluginRuntimeError.of(PluginRuntimeErrorCategory.TIMEOUT, "PRV2-TIMEOUT", message));
    }

    public static PluginExecutionResult cancelled(String message) {
        return new PluginExecutionResult(PluginExecutionStatus.CANCELLED, null, List.of(), null,
                PluginRuntimeError.of(PluginRuntimeErrorCategory.CANCELLED, "PRV2-CANCELLED", message));
    }
}
