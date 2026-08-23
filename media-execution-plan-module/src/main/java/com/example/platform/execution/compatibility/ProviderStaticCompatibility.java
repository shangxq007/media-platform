package com.example.platform.execution.compatibility;

import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.CodecId;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.ProviderRuntimeClass;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Frozen non-capability support declaration used by Stage 1. Capability support remains solely in
 * {@code ProviderCapabilityProfile}; this type contains no capability identity.
 */
public record ProviderStaticCompatibility(
        Knowledge knowledge,
        List<ArtifactRequirementKind> supportedArtifactRequirements,
        List<CodecId> supportedCodecs,
        List<StaticCompatibilityConstraint.ProviderDeviceKind> supportedDeviceKinds,
        List<ProviderRuntimeClass> supportedRuntimeClasses,
        List<SandboxMode> supportedSandboxModes,
        List<RenderDeterminismClass> supportedDeterminismClasses,
        List<BoundaryContractId> supportedBoundaryContracts,
        LoweringSupport loweringSupport) {

    public ProviderStaticCompatibility {
        Objects.requireNonNull(knowledge, "knowledge");
        supportedArtifactRequirements = canonical(
                supportedArtifactRequirements, Comparator.naturalOrder(), "supportedArtifactRequirements");
        supportedCodecs = canonical(
                supportedCodecs, Comparator.comparing(CodecId::value), "supportedCodecs");
        supportedDeviceKinds = canonical(
                supportedDeviceKinds, Comparator.naturalOrder(), "supportedDeviceKinds");
        supportedRuntimeClasses = canonical(
                supportedRuntimeClasses, Comparator.naturalOrder(), "supportedRuntimeClasses");
        supportedSandboxModes = canonical(
                supportedSandboxModes, Comparator.naturalOrder(), "supportedSandboxModes");
        supportedDeterminismClasses = canonical(
                supportedDeterminismClasses, Comparator.naturalOrder(), "supportedDeterminismClasses");
        supportedBoundaryContracts = canonical(
                supportedBoundaryContracts,
                Comparator.comparing(BoundaryContractId::value),
                "supportedBoundaryContracts");
        Objects.requireNonNull(loweringSupport, "loweringSupport");
    }

    public static ProviderStaticCompatibility unknown() {
        return new ProviderStaticCompatibility(
                Knowledge.UNKNOWN,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                LoweringSupport.UNKNOWN);
    }

    private static <T> List<T> canonical(
            List<T> values, Comparator<? super T> comparator, String fieldName) {
        Objects.requireNonNull(values, fieldName);
        var copy = new ArrayList<T>(values.size());
        for (T value : values) {
            copy.add(Objects.requireNonNull(value, fieldName + " element"));
        }
        copy.sort(comparator);
        for (int i = 1; i < copy.size(); i++) {
            if (copy.get(i - 1).equals(copy.get(i))) {
                throw new IllegalArgumentException("duplicate " + fieldName + " declaration");
            }
        }
        return List.copyOf(copy);
    }

    public enum Knowledge {
        DECLARED,
        UNKNOWN
    }

    public enum ArtifactRequirementKind {
        PINNED_SOURCE_INPUT,
        MANDATORY_MATERIALIZATION,
        INTERMEDIATE_OUTPUT,
        FINAL_OUTPUT
    }

    public enum SandboxMode {
        UNSANDBOXED,
        SANDBOXED
    }

    public enum LoweringSupport {
        SUPPORTED,
        UNSUPPORTED,
        UNKNOWN
    }
}
