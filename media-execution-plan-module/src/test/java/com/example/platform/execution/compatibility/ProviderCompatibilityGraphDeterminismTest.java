package com.example.platform.execution.compatibility;

import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProviderCompatibilityGraphDeterminismTest {

    @Test
    void inputPermutationsHaveSemanticEqualitySameSerializationAndSameDigest() {
        CompatibilityRequest firstUnit = CompatibilityRequest.forUnit(unit("unit-a"));
        CompatibilityRequest secondUnit = CompatibilityRequest.forUnit(unit("unit-b"));
        ProviderCandidate ffmpeg = candidate("ffmpeg", CompatibilityKernelTest.fullySupported());
        ProviderCandidate blender = candidate("blender", CompatibilityKernelTest.fullySupported());

        ProviderCompatibilityGraph first = ProviderCompatibilityGraph.build(
                List.of(secondUnit, firstUnit), List.of(ffmpeg, blender));
        ProviderCompatibilityGraph permuted = ProviderCompatibilityGraph.build(
                List.of(firstUnit, secondUnit), List.of(blender, ffmpeg));

        assertEquals(first, permuted);
        assertEquals(first.hashCode(), permuted.hashCode());
        assertArrayEquals(first.canonicalSerialization(), permuted.canonicalSerialization());
        assertEquals(first.digest(), permuted.digest());
        assertEquals(List.of(new ExecutionStepId("unit-a"), new ExecutionStepId("unit-b")),
                first.unitCandidates().stream()
                        .map(ProviderCompatibilityGraph.UnitCandidates::physicalPlanUnitId).toList());
    }

    @Test
    void unknownAndIncompatibleCandidatesAreExcludedFailClosed() {
        ProviderCandidate compatible = candidate("ffmpeg", CompatibilityKernelTest.fullySupported());
        ProviderCandidate unknown = candidate("unknown", ProviderStaticCompatibility.unknown());
        ProviderStaticCompatibility cannotLower = new ProviderStaticCompatibility(
                ProviderStaticCompatibility.Knowledge.DECLARED,
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                ProviderStaticCompatibility.LoweringSupport.UNSUPPORTED);
        ProviderCandidate incompatible = candidate("incompatible", cannotLower);

        ProviderCompatibilityGraph graph = ProviderCompatibilityGraph.build(
                List.of(CompatibilityRequest.forUnit(unit("unit-a"))),
                List.of(unknown, incompatible, compatible));

        assertEquals(1, graph.unitCandidates().getFirst().feasibleProviderBindings().size());
        assertEquals(ProviderId.of("ffmpeg"),
                graph.unitCandidates().getFirst().feasibleProviderBindings().getFirst().providerId());
        assertFalse(graph.canonicalSerialization().length == 0);
    }

    @Test
    void duplicateUnitOrBindingCandidateFailsStructurally() {
        CompatibilityRequest request = CompatibilityRequest.forUnit(unit("unit-a"));
        ProviderCandidate candidate = candidate("ffmpeg", CompatibilityKernelTest.fullySupported());

        assertThrows(IllegalArgumentException.class,
                () -> ProviderCompatibilityGraph.build(List.of(request, request), List.of(candidate)));
        assertThrows(IllegalArgumentException.class,
                () -> ProviderCompatibilityGraph.build(List.of(request), List.of(candidate, candidate)));
    }

    private static PhysicalPlanUnit unit(String id) {
        return new PhysicalPlanUnit(
                new ExecutionStepId(id),
                "logical-" + id,
                new RenderNodeId("render-" + id),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(), List.of(), List.of(), null, null,
                List.of(), List.of(), null, true);
    }

    private static ProviderCandidate candidate(
            String provider, ProviderStaticCompatibility staticCompatibility) {
        ProviderId providerId = ProviderId.of(provider);
        ProviderImplementationId implementationId = ProviderImplementationId.of(provider + ".implementation");
        ProviderVersion providerVersion = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(
                        ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, providerVersion, contractVersion, profileReference, List.of());
        ProviderDescriptor descriptor = new ProviderDescriptor(
                providerId, implementationId, providerVersion, contractVersion, profileReference);
        ProviderExecutionContract contract = new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of());
        ProviderCapabilityProfile profile = new ProviderCapabilityProfile(profileReference, List.of());
        return new ProviderCandidate(binding, descriptor, contract, profile, staticCompatibility);
    }
}
