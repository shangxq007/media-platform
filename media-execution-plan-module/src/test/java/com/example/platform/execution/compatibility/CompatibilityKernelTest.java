package com.example.platform.execution.compatibility;

import com.example.platform.execution.compatibility.ProviderStaticCompatibility.ArtifactRequirementKind;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility.Knowledge;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility.LoweringSupport;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility.SandboxMode;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.Codec;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.CodecId;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.CrossProviderBoundary;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.DeviceKind;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.ProviderDeviceKind;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.ProviderRuntime;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.ProviderRuntimeClass;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.domain.provider.ProviderBindingPin;
import com.example.platform.execution.domain.provider.ProviderCapabilityContractReference;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfile;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersion;
import com.example.platform.execution.domain.provider.ProviderCapabilityProfileVersionOrDigest;
import com.example.platform.execution.domain.provider.ProviderCapabilitySupport;
import com.example.platform.execution.domain.provider.ProviderDescriptor;
import com.example.platform.execution.domain.provider.ProviderExecutionContract;
import com.example.platform.execution.domain.provider.ProviderExecutionContractSchemaVersion;
import com.example.platform.execution.domain.provider.ProviderExecutionContractVersion;
import com.example.platform.execution.domain.provider.ProviderId;
import com.example.platform.execution.domain.provider.ProviderImplementationId;
import com.example.platform.execution.domain.provider.ProviderVersion;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.ExecutionIntentRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.GpuRequirement;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement.RenderDeterminismClass;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CompatibilityKernelTest {

    private static final CapabilityId DECODE = CapabilityId.of("media.decode.video");
    private static final ContractVersionRange V1 =
            ContractVersionRange.exactly(ContractVersion.of(1, 0));
    private static final BoundaryContractId ARTIFACT_BOUNDARY =
            BoundaryContractId.of("immutable-artifact-materialization.v1");

    @Test
    void compatibleDecisionUsesTypedResultAndNoExceptionFlow() {
        CompatibilityDecision decision = assertDoesNotThrow(() -> CompatibilityKernel.evaluate(
                CompatibilityRequest.forUnit(unit("u1", true, List.of(), List.of())),
                candidate("ffmpeg", profile(V1), contract(V1), fullySupported())));

        assertEquals(CompatibilityDecision.Status.COMPATIBLE, decision.status());
        assertTrue(decision.compatible());
        assertTrue(decision.reasons().isEmpty());
        assertTrue(decision.evidence().isEmpty());
    }

    @Test
    void capabilityUnsupportedIsTypedDecisionData() {
        assertFailure(
                CompatibilityRequest.forUnit(unitWithCapability("u1", V1)),
                candidate("ffmpeg", emptyProfile(), emptyContract(), fullySupported()),
                StaticCompatibilityFailure.CAPABILITY_UNSUPPORTED);
    }

    @Test
    void capabilityContractVersionUnsupportedIsTypedDecisionData() {
        ContractVersionRange v2 = ContractVersionRange.exactly(ContractVersion.of(2, 0));
        assertFailure(
                CompatibilityRequest.forUnit(unitWithCapability("u1", V1)),
                candidate("ffmpeg", profile(v2), contract(v2), fullySupported()),
                StaticCompatibilityFailure.CONTRACT_VERSION_UNSUPPORTED);
    }

    @Test
    void immutableArtifactIncompatibilityIsTypedDecisionData() {
        ProviderStaticCompatibility withoutArtifacts = staticSupport(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                LoweringSupport.SUPPORTED);
        assertFailure(
                CompatibilityRequest.forUnit(unit("u1", true, List.of(sourceInput()), List.of())),
                candidate("ffmpeg", emptyProfile(), emptyContract(), withoutArtifacts),
                StaticCompatibilityFailure.ARTIFACT_INCOMPATIBLE);
    }

    @Test
    void codecUnsupportedIsTypedDecisionData() {
        assertFailure(
                requestWith(new Codec(CodecId.of("video/h265"))),
                candidate("ffmpeg", emptyProfile(), emptyContract(), fullySupported()),
                StaticCompatibilityFailure.CODEC_UNSUPPORTED);
    }

    @Test
    void deviceKindUnsupportedInPrincipleIsTypedDecisionData() {
        assertFailure(
                requestWith(new DeviceKind(ProviderDeviceKind.OTHER_ACCELERATOR)),
                candidate("ffmpeg", emptyProfile(), emptyContract(), fullySupported()),
                StaticCompatibilityFailure.DEVICE_KIND_UNSUPPORTED_IN_PRINCIPLE);
    }

    @Test
    void providerRuntimeClassUnsupportedIsTypedDecisionData() {
        assertFailure(
                requestWith(new ProviderRuntime(ProviderRuntimeClass.REMOTE_SERVICE)),
                candidate("ffmpeg", emptyProfile(), emptyContract(), fullySupported()),
                StaticCompatibilityFailure.PROVIDER_RUNTIME_CLASS_UNSUPPORTED);
    }

    @Test
    void inconsistentBindingAndDescriptorIsProviderContractIncompatibility() {
        ProviderCandidate valid = candidate("ffmpeg", emptyProfile(), emptyContract(), fullySupported());
        ProviderDescriptor inconsistent = new ProviderDescriptor(
                ProviderId.of("other"),
                valid.descriptor().providerImplementationId(),
                valid.descriptor().providerVersion(),
                valid.descriptor().providerExecutionContractVersion(),
                valid.descriptor().providerCapabilityProfileReference());
        ProviderCandidate invalid = new ProviderCandidate(
                valid.bindingPin(), inconsistent, valid.executionContract(),
                valid.capabilityProfile(), valid.staticCompatibility());

        assertFailure(
                CompatibilityRequest.forUnit(unit("u1", true, List.of(), List.of())),
                invalid,
                StaticCompatibilityFailure.PROVIDER_CONTRACT_INCOMPATIBLE);
    }

    @Test
    void sandboxModeUnsupportedIsTypedDecisionData() {
        ProviderStaticCompatibility noSandbox = staticSupport(
                List.of(), List.of(), List.of(), List.of(),
                List.of(SandboxMode.UNSANDBOXED),
                List.of(RenderDeterminismClass.DETERMINISTIC),
                List.of(), LoweringSupport.SUPPORTED);
        var sandboxed = new ExecutionIntentRef(new RenderExecutionRequirement(
                GpuRequirement.NONE, RenderDeterminismClass.DETERMINISTIC, true));
        assertFailure(
                CompatibilityRequest.forUnit(unit("u1", true, List.of(), List.of(sandboxed))),
                candidate("ffmpeg", emptyProfile(), emptyContract(), noSandbox),
                StaticCompatibilityFailure.SANDBOX_MODE_UNSUPPORTED);
    }

    @Test
    void determinismUnsupportedIsTypedDecisionData() {
        ProviderStaticCompatibility noDeterminism = staticSupport(
                List.of(), List.of(), List.of(), List.of(),
                List.of(SandboxMode.UNSANDBOXED), List.of(), List.of(),
                LoweringSupport.SUPPORTED);
        var deterministic = new ExecutionIntentRef(new RenderExecutionRequirement(
                GpuRequirement.NONE, RenderDeterminismClass.DETERMINISTIC, false));
        assertFailure(
                CompatibilityRequest.forUnit(unit("u1", true, List.of(), List.of(deterministic))),
                candidate("ffmpeg", emptyProfile(), emptyContract(), noDeterminism),
                StaticCompatibilityFailure.DETERMINISM_UNSUPPORTED);
    }

    @Test
    void crossProviderBoundaryRequiresExplicitStaticContract() {
        assertFailure(
                requestWith(new CrossProviderBoundary(ProviderId.of("blender"),
                        BoundaryContractId.of("unknown-boundary.v1"))),
                candidate("ffmpeg", emptyProfile(), emptyContract(), fullySupported()),
                StaticCompatibilityFailure.CROSS_PROVIDER_BOUNDARY_INCOMPATIBLE);
    }

    @Test
    void semanticallyUnrepresentableLoweringIsTypedDecisionData() {
        ProviderStaticCompatibility unsupported = staticSupport(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                LoweringSupport.UNSUPPORTED);
        assertFailure(
                CompatibilityRequest.forUnit(unit("u1", true, List.of(), List.of())),
                candidate("ffmpeg", emptyProfile(), emptyContract(), unsupported),
                StaticCompatibilityFailure.LOWERING_SEMANTICALLY_UNREPRESENTABLE);
    }

    @Test
    void unknownStaticCompatibilityFailsClosed() {
        CompatibilityDecision decision = CompatibilityKernel.evaluate(
                CompatibilityRequest.forUnit(unit("u1", true, List.of(), List.of())),
                candidate("ffmpeg", emptyProfile(), emptyContract(), ProviderStaticCompatibility.unknown()));

        assertEquals(CompatibilityDecision.Status.UNKNOWN_FAIL_CLOSED, decision.status());
        assertFalse(decision.compatible());
        assertEquals(List.of(StaticCompatibilityFailure.UNKNOWN_STATIC_COMPATIBILITY), decision.reasons());
    }

    private static void assertFailure(
            CompatibilityRequest request,
            ProviderCandidate candidate,
            StaticCompatibilityFailure expected) {
        CompatibilityDecision decision = assertDoesNotThrow(
                () -> CompatibilityKernel.evaluate(request, candidate));
        assertEquals(CompatibilityDecision.Status.INCOMPATIBLE, decision.status());
        assertFalse(decision.compatible());
        assertEquals(List.of(expected), decision.reasons());
        assertTrue(decision.evidence().stream().allMatch(item -> item.failure() == expected));
    }

    private static CompatibilityRequest requestWith(StaticCompatibilityConstraint constraint) {
        return new CompatibilityRequest(
                unit("u1", true, List.of(), List.of()), List.of(constraint));
    }

    private static PhysicalPlanUnit unitWithCapability(String id, ContractVersionRange range) {
        CapabilityRequirement requirement = CapabilityRequirement.of(DECODE, range);
        return unit(id, true, List.of(), List.of(), new CapabilityRequirementRef(requirement));
    }

    private static PhysicalPlanUnit unit(
            String id,
            boolean cacheable,
            List<InputBinding> inputs,
            List<ExecutionIntentRef> intents,
            CapabilityRequirementRef... capabilityRequirements) {
        return new PhysicalPlanUnit(
                new ExecutionStepId(id),
                "logical-" + id,
                new RenderNodeId("render-" + id),
                new RenderNodeKind.Decode(),
                "decode",
                inputs,
                List.of(),
                List.of(),
                null,
                null,
                List.of(capabilityRequirements),
                intents,
                null,
                cacheable);
    }

    private static InputBinding sourceInput() {
        return new InputBinding(
                new ExecutionInputId("input-1"),
                "logical-u1",
                null, null, null, null, null, null,
                new RenderArtifactReference.SourceArtifact(
                        new ArtifactId("artifact-1"), ContentDigest.sha256("a".repeat(64))),
                null);
    }

    private static ProviderCandidate candidate(
            String provider,
            ProviderCapabilityProfile profile,
            ProviderExecutionContract contract,
            ProviderStaticCompatibility support) {
        ProviderId providerId = ProviderId.of(provider);
        ProviderImplementationId implementationId = ProviderImplementationId.of(provider + ".implementation");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = contract.contractVersion();
        ProviderCapabilityProfileVersionOrDigest profileReference = profile.reference();
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileReference, List.of());
        ProviderDescriptor descriptor = new ProviderDescriptor(
                providerId, implementationId, version, contractVersion, profileReference);
        return new ProviderCandidate(binding, descriptor, contract, profile, support);
    }

    private static ProviderCapabilityProfile profile(ContractVersionRange range) {
        return new ProviderCapabilityProfile(profileReference(),
                List.of(ProviderCapabilitySupport.unpinned(DECODE, range)));
    }

    private static ProviderCapabilityProfile emptyProfile() {
        return new ProviderCapabilityProfile(profileReference(), List.of());
    }

    private static ProviderExecutionContract contract(ContractVersionRange range) {
        return new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1),
                ProviderExecutionContractVersion.of(1, 0),
                List.of(new ProviderCapabilityContractReference(DECODE, range)));
    }

    private static ProviderExecutionContract emptyContract() {
        return new ProviderExecutionContract(
                ProviderExecutionContractSchemaVersion.of(1),
                ProviderExecutionContractVersion.of(1, 0),
                List.of());
    }

    private static ProviderCapabilityProfileVersionOrDigest profileReference() {
        return ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));
    }

    static ProviderStaticCompatibility fullySupported() {
        return staticSupport(
                List.of(ArtifactRequirementKind.PINNED_SOURCE_INPUT,
                        ArtifactRequirementKind.MANDATORY_MATERIALIZATION,
                        ArtifactRequirementKind.INTERMEDIATE_OUTPUT,
                        ArtifactRequirementKind.FINAL_OUTPUT),
                List.of(CodecId.of("video/h264")),
                List.of(ProviderDeviceKind.CPU, ProviderDeviceKind.GPU,
                        ProviderDeviceKind.MEDIA_ACCELERATOR),
                List.of(ProviderRuntimeClass.NATIVE_PROCESS,
                        ProviderRuntimeClass.ISOLATED_PROCESS,
                        ProviderRuntimeClass.CONTAINERIZED),
                List.of(SandboxMode.UNSANDBOXED, SandboxMode.SANDBOXED),
                List.of(RenderDeterminismClass.values()),
                List.of(ARTIFACT_BOUNDARY),
                LoweringSupport.SUPPORTED);
    }

    private static ProviderStaticCompatibility staticSupport(
            List<ArtifactRequirementKind> artifacts,
            List<CodecId> codecs,
            List<ProviderDeviceKind> devices,
            List<ProviderRuntimeClass> runtimes,
            List<SandboxMode> sandboxes,
            List<RenderDeterminismClass> determinism,
            List<BoundaryContractId> boundaries,
            LoweringSupport lowering) {
        return new ProviderStaticCompatibility(
                Knowledge.DECLARED, artifacts, codecs, devices, runtimes,
                sandboxes, determinism, boundaries, lowering);
    }
}
