package com.example.platform.execution.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration;
import com.example.platform.execution.compatibility.ProviderBoundaryCompatibilityDeclaration.Declaration;
import com.example.platform.execution.compatibility.ProviderCandidate;
import com.example.platform.execution.compatibility.ProviderStaticCompatibility;
import com.example.platform.execution.compatibility.StaticCompatibilityConstraint.BoundaryContractId;
import com.example.platform.execution.domain.ExecutionEdgeId;
import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
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
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.ExecutionIntentRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.LogicalExecutionGraph.LogicalDependencyEdge;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderDependency;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.render.domain.renderplan.RenderMaterializationRequirement;
import com.example.platform.render.domain.renderplan.TimedTextMaterializationRequirement;
import com.example.platform.render.domain.renderplan.RenderNodeId;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderOutputRequirement;
import com.example.platform.render.domain.renderplan.RenderOutputRole;
import com.example.platform.render.domain.renderplan.RenderPlanFingerprint;
import com.example.platform.render.domain.renderplan.RenderSampleWindow;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.capability.ContractVersion;
import com.example.platform.shared.capability.ContractVersionRange;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.fonttext.resolution.FontFallbackPolicy;
import com.example.platform.fonttext.text.ParagraphBaseDirection;
import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.typography.FontFamilyName;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.LineHeight;
import com.example.platform.fonttext.typography.ParagraphStyle;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.timeline.canonical.TextElementId;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Bounded V1 canonical codecs: deterministic bytes, exact round-trip, and
 * fail-closed behaviour on anything the bounded surface cannot represent.
 */
class BoundedCodecTest {

    private static final MediaTime ZERO = MediaTime.ofRational(0, 1);
    private static final MediaTime TWO = MediaTime.ofRational(2, 1);
    private static final FrameRate FPS = FrameRate.of(30, 1);

    @Test
    void physicalPlanRoundTripsAndEncodesDeterministically() {
        PhysicalExecutionPlan plan = plan();

        byte[] first = PhysicalExecutionPlanCanonicalCodec.encode(plan);
        byte[] second = PhysicalExecutionPlanCanonicalCodec.encode(plan);

        assertThat(first).containsExactly(second);
        assertThat(new String(first, StandardCharsets.UTF_8))
                .startsWith(frame(PhysicalExecutionPlanCanonicalCodec.FORMAT));
        assertThat(PhysicalExecutionPlanCanonicalCodec.decode(first)).isEqualTo(plan);
        assertThat(PhysicalExecutionPlanCanonicalCodec.digestHex(plan))
                .isEqualTo(PhysicalExecutionPlanCanonicalCodec.digestHex(
                        PhysicalExecutionPlanCanonicalCodec.decode(first)));
    }

    @Test
    void physicalPlanRoundTripsTimedTextMaterializationRequirement() {
        PhysicalExecutionPlan plan = planWithTimedTextMaterialization();

        byte[] first = PhysicalExecutionPlanCanonicalCodec.encode(plan);
        byte[] second = PhysicalExecutionPlanCanonicalCodec.encode(plan);

        assertThat(first).containsExactly(second);
        PhysicalExecutionPlan decoded = PhysicalExecutionPlanCanonicalCodec.decode(first);
        assertThat(decoded).isEqualTo(plan);
        assertThat(decoded.units().getFirst().typedOutputs().getFirst()
                .materializationRequirements())
                .containsExactlyElementsOf(plan.units().getFirst().typedOutputs().getFirst()
                        .materializationRequirements());
    }

    @Test
    void providerCandidateRoundTripsAndEncodesDeterministically() {
        ProviderCandidate candidate = candidate("provider-a");

        byte[] first = ProviderCandidateCanonicalCodec.encode(candidate);
        byte[] second = ProviderCandidateCanonicalCodec.encode(candidate);

        assertThat(first).containsExactly(second);
        assertThat(ProviderCandidateCanonicalCodec.decode(first)).isEqualTo(candidate);
        assertThat(new String(first, StandardCharsets.UTF_8))
                .startsWith(frame(ProviderCandidateCanonicalCodec.FORMAT));
    }

    @Test
    void boundaryDeclarationRoundTripsAndEncodesDeterministically() {
        ProviderCandidate candidate = candidate("provider-a");
        ProviderBoundaryCompatibilityDeclaration declaration =
                new ProviderBoundaryCompatibilityDeclaration(
                        edge(), candidate.bindingPin(), candidate.bindingPin(),
                        BoundaryContractId.of("test.boundary.v1"),
                        Declaration.DIRECT_INTEROPERABILITY_ALLOWED);

        byte[] first = ProviderBoundaryCompatibilityDeclarationCanonicalCodec.encode(declaration);
        byte[] second = ProviderBoundaryCompatibilityDeclarationCanonicalCodec.encode(declaration);

        assertThat(first).containsExactly(second);
        assertThat(ProviderBoundaryCompatibilityDeclarationCanonicalCodec.decode(first))
                .isEqualTo(declaration);
    }

    @Test
    void unknownFormatAndTruncatedStreamsFailClosed() {
        byte[] valid = PhysicalExecutionPlanCanonicalCodec.encode(plan());
        byte[] wrongFormat = new byte[valid.length];
        System.arraycopy(valid, 0, wrongFormat, 0, valid.length);
        wrongFormat[2] = 'X';

        assertThatThrownBy(() -> PhysicalExecutionPlanCanonicalCodec.decode(wrongFormat))
                .isInstanceOf(UnsupportedPersistedConstructException.class);
        assertThatThrownBy(() -> PhysicalExecutionPlanCanonicalCodec.decode(new byte[3]))
                .isInstanceOf(UnsupportedPersistedConstructException.class);
        assertThatThrownBy(() -> ProviderCandidateCanonicalCodec.decode(new byte[0]))
                .isInstanceOf(UnsupportedPersistedConstructException.class);
    }

    // ---------- fixtures ----------

    /** The canonical writer frames every value as {@code <byteLength>:<value>}. */
    private static String frame(String value) {
        return value.length() + ":" + value;
    }

    private static ProviderCandidate candidate(String provider) {
        ProviderId providerId = ProviderId.of(provider);
        ProviderImplementationId implementationId = ProviderImplementationId.of(provider + ".native");
        ProviderVersion version = ProviderVersion.of("1.0.0");
        ProviderExecutionContractVersion contractVersion = ProviderExecutionContractVersion.of(1, 0);
        ProviderCapabilityProfileVersionOrDigest profileReference =
                ProviderCapabilityProfileVersionOrDigest.version(ProviderCapabilityProfileVersion.of(1, 0));
        ProviderBindingPin binding = new ProviderBindingPin(
                providerId, implementationId, version, contractVersion, profileReference, List.of());
        return new ProviderCandidate(
                binding,
                new ProviderDescriptor(providerId, implementationId, version, contractVersion, profileReference),
                new ProviderExecutionContract(
                        ProviderExecutionContractSchemaVersion.of(1), contractVersion, List.of()),
                new ProviderCapabilityProfile(profileReference, List.of()),
                new ProviderStaticCompatibility(
                        ProviderStaticCompatibility.Knowledge.DECLARED,
                        List.of(ProviderStaticCompatibility.ArtifactRequirementKind.PINNED_SOURCE_INPUT),
                        List.of(),
                        List.of(com.example.platform.execution.compatibility.StaticCompatibilityConstraint
                                .ProviderDeviceKind.CPU),
                        List.of(),
                        List.of(ProviderStaticCompatibility.SandboxMode.SANDBOXED),
                        List.of(ProviderStaticCompatibility.DeterminismClass.DETERMINISTIC),
                        List.of(BoundaryContractId.of("test.boundary.v1")),
                        ProviderStaticCompatibility.LoweringSupport.SUPPORTED));
    }

    private static LogicalDependencyEdge edge() {
        return new LogicalDependencyEdge(
                new ExecutionEdgeId("edge-a-b"),
                "logical-unit-a", "logical-unit-b",
                new RenderNodeId("render-unit-a"), new RenderNodeId("render-unit-b"),
                new RenderDependency.DecodedFrames());
    }

    private static PhysicalExecutionPlan plan() {
        InputBinding input = new InputBinding(
                new ExecutionInputId("input-a"),
                "logical-unit-a",
                new ExecutionStepId("unit-a"),
                new RenderNodeId("render-unit-a"),
                null, null, null, null,
                new RenderArtifactReference.SourceArtifact(
                        new ArtifactId("art-source"), ContentDigest.sha256("a".repeat(64))),
                new RenderSampleWindow(ZERO, TWO, FPS));
        OutputDeclaration output = new OutputDeclaration(
                new ExecutionOutputId("output-a"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)),
                List.of(),
                List.of(),
                List.of(new RenderArtifactReference.FinalArtifactExpectation(RenderOutputRole.RENDER_MASTER)));
        PhysicalPlanUnit unit = new PhysicalPlanUnit(
                new ExecutionStepId("unit-a"),
                "logical-unit-a",
                new RenderNodeId("render-unit-a"),
                new RenderNodeKind.Decode(),
                "decode",
                List.of(input),
                List.of(output),
                List.of(),
                new RenderSampleWindow(ZERO, TWO, FPS),
                new RenderExecutionCoverage(ZERO, TWO, FPS),
                List.of(new CapabilityRequirementRef(CapabilityRequirement.of(
                        CapabilityId.of("video.decode"),
                        ContractVersionRange.exactly(ContractVersion.of(1, 0))))),
                List.of(new ExecutionIntentRef(new RenderExecutionRequirement(
                        RenderExecutionRequirement.GpuRequirement.NONE,
                        RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC,
                        true))),
                new RenderExtent(ZERO, TWO, FPS),
                true);
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("codec-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("codec-fingerprint"),
                List.of(unit),
                new RenderExtent(ZERO, TWO, FPS),
                new PhysicalExecutionPlanDigest("declared-digest"));
    }

    private static PhysicalExecutionPlan planWithTimedTextMaterialization() {
        PhysicalExecutionPlan base = plan();
        PhysicalPlanUnit unit = base.units().getFirst();
        OutputDeclaration original = unit.typedOutputs().getFirst();
        TimedTextMaterializationRequirement requirement =
                new TimedTextMaterializationRequirement(
                        new TextElementId("text-codec-requirement"),
                        FontRational.whole(1),
                        FontRational.whole(2),
                        new StyledText(
                                new TextContent("中文 Hello"),
                                List.of(),
                                List.of(),
                                new ParagraphStyle(
                                        ParagraphStyle.Alignment.START,
                                        ParagraphStyle.Justification.NONE,
                                        LineHeight.ratio(FontRational.of(6, 5)),
                                        ParagraphStyle.WrapPolicy.WRAP,
                                        ParagraphBaseDirection.AUTO,
                                        ParagraphStyle.LineBreakPolicy.STANDARD)),
                        new TextFrame(
                                FontRational.whole(640),
                                null,
                                TextFrame.HorizontalAlignment.START,
                                TextFrame.VerticalAlignment.TOP,
                                ParagraphStyle.WrapPolicy.WRAP,
                                TextFrame.OverflowBehavior.CLIP),
                        new FontFallbackPolicy(
                                List.of(new FontFamilyName("Noto Sans CJK SC")),
                                List.of(), List.of(), List.of()),
                        List.of());
        OutputDeclaration output = new OutputDeclaration(
                original.outputId(),
                original.logicalNodeId(),
                original.sourceRenderNodeId(),
                original.outputRequirements(),
                List.of((RenderMaterializationRequirement) requirement),
                original.intermediateArtifactExpectations(),
                original.finalArtifactExpectations());
        PhysicalPlanUnit withText = new PhysicalPlanUnit(
                unit.stepId(),
                unit.logicalNodeId(),
                unit.sourceRenderNodeId(),
                unit.sourceRenderNodeKind(),
                unit.operationKey(),
                unit.typedInputs(),
                List.of(output),
                unit.typedDependencies(),
                unit.temporalWindow(),
                unit.executionCoverage(),
                unit.capabilityRequirementRefs(),
                unit.executionIntentRefs(),
                unit.propagatedExtent(),
                unit.deterministicallyCacheable());
        return new PhysicalExecutionPlan(
                base.formatVersion(),
                base.planId(),
                base.schemaVersion(),
                base.planFingerprint(),
                List.of(withText),
                base.propagatedExtent(),
                base.digest());
    }
}
