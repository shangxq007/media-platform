package com.example.platform.execution.binding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.example.platform.execution.domain.ExecutionInputId;
import com.example.platform.execution.domain.ExecutionOutputId;
import com.example.platform.execution.domain.ExecutionPlanId;
import com.example.platform.execution.domain.ExecutionPlanSchemaVersion;
import com.example.platform.execution.domain.ExecutionStepId;
import com.example.platform.execution.planning.ExecutionIoProjection.CapabilityRequirementRef;
import com.example.platform.execution.planning.ExecutionIoProjection.ExecutionIntentRef;
import com.example.platform.execution.planning.ExecutionIoProjection.InputBinding;
import com.example.platform.execution.planning.ExecutionIoProjection.OutputDeclaration;
import com.example.platform.execution.planning.PhysicalExecutionPlan;
import com.example.platform.execution.planning.PhysicalExecutionPlan.PhysicalPlanUnit;
import com.example.platform.execution.planning.PhysicalExecutionPlanDigest;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.render.domain.renderplan.RenderArtifactReference;
import com.example.platform.render.domain.renderplan.RenderExecutionCoverage;
import com.example.platform.render.domain.renderplan.RenderExecutionRequirement;
import com.example.platform.render.domain.renderplan.RenderExtent;
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
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * P1-1A.5-CANONICALCODEC-NULL-SAFETY-FIX-R1.
 *
 * <p>Regression for the production defect where {@code PhysicalExecutionPlanCanonicalCodec.unit()} /
 * {@code plan()} eagerly evaluated {@code window(...)} / {@code coverage(...)} / {@code extent(...)}
 * for optional fields that are legitimately {@code null} (notably the OUTPUT/{@code encode} unit of a
 * real render plan, whose {@code temporalWindow} / {@code executionCoverage} / {@code propagatedExtent}
 * are all {@code null}). The canonical format already carries an explicit present/absent marker and the
 * decoder already reads {@code optionalNested()}, so absent fields must encode as "absent" — never NPE,
 * and never a fabricated default.
 */
class PhysicalExecutionPlanCanonicalCodecNullSafetyTest {

    private static final MediaTime ZERO = MediaTime.ofRational(0, 1);
    private static final MediaTime TWO = MediaTime.ofRational(2, 1);
    private static final FrameRate FPS = FrameRate.of(30, 1);

    /** The exact real shape observed in R2: decode unit has all optionals; encode unit has none. */
    @Test
    void realEncodeUnitWithNullTemporalWindowAndCoverageRoundTrips() {
        PhysicalPlanUnit decode = unit("decode", "ln-decode",
                new RenderSampleWindow(ZERO, TWO, FPS),
                new RenderExecutionCoverage(ZERO, TWO, FPS),
                new RenderExtent(ZERO, TWO, FPS),
                List.of(sourceInput("input-a", "ln-decode")),
                List.of());
        PhysicalPlanUnit encode = unit("encode", "ln-encode",
                null, null, null,
                List.of(),
                List.of(outputDeclaration("ln-encode")));
        PhysicalExecutionPlan plan = plan(List.of(decode, encode), null);

        byte[] encoded = assertDoesNotThrowEncoding(plan);
        PhysicalExecutionPlan decoded = PhysicalExecutionPlanCanonicalCodec.decode(encoded);
        assertThat(decoded).isEqualTo(plan);
        assertThat(decoded.units().get(1).temporalWindow()).isNull();
        assertThat(decoded.units().get(1).executionCoverage()).isNull();
        assertThat(decoded.units().get(1).propagatedExtent()).isNull();
        // Canonical stability: re-encoding the decoded plan yields byte-identical output.
        assertThat(PhysicalExecutionPlanCanonicalCodec.encode(decoded)).isEqualTo(encoded);
        assertThat(PhysicalExecutionPlanCanonicalCodec.digestHex(decoded))
                .isEqualTo(PhysicalExecutionPlanCanonicalCodec.digestHex(plan));
    }

    @Test
    void clippedDissolveRetainsItsAuthoredTimeAndBothEndpointIdentities() {
        var start = MediaTime.ofMillis(2500);
        var end = MediaTime.ofMillis(3500);
        var dissolve = new com.example.platform.render.domain.renderplan.RenderDependency.Dissolve(
                "transition-1", "outgoing", "incoming", TWO);
        var dependency = com.example.platform.render.domain.renderplan.RenderDependency.TimedTrackInput.video(
                start, end, 1,
                com.example.platform.timeline.semantics.effect.VideoTransform.IDENTITY,
                new com.example.platform.render.domain.renderplan.VideoEffectSpec(0, 1, 1, true, 0.5, 0.5, true),
                TWO, MediaTime.ofMillis(4500), java.util.Optional.of(dissolve));
        var input = new InputBinding(new ExecutionInputId("input-clip"), "composite",
                new ExecutionStepId("composite"), new RenderNodeId("render-composite"),
                "clip", new ExecutionStepId("clip"), new RenderNodeId("render-clip"), dependency, null, null);
        var plan = plan(List.of(unit("multitrack-composite", "composite", null, null, null, List.of(input),
                List.of(outputDeclaration("composite")))), null);

        byte[] bytes = PhysicalExecutionPlanCanonicalCodec.encode(plan);
        assertThat(PhysicalExecutionPlanCanonicalCodec.decode(bytes)).isEqualTo(plan);
        String oldTag = new String(bytes, java.nio.charset.StandardCharsets.UTF_8)
                .replace("p25a.physical-execution-plan.v3", "p25a.physical-execution-plan.v2");
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                PhysicalExecutionPlanCanonicalCodec.decode(oldTag.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .isInstanceOf(UnsupportedPersistedConstructException.class);
    }

    @Test
    void positionAndOpacityRoundTripWithNonzeroPlacementAndTransition() {
        var position = com.example.platform.render.domain.renderplan.RenderDependency.PositionRamp.parseSamples(
                "3/1;200000;100000|7/2;250000;-50000|4/1;400000;250000");
        var dependency = com.example.platform.render.domain.renderplan.RenderDependency.TimedTrackInput.video(
                MediaTime.ofMillis(2500), MediaTime.ofMillis(4250), 1,
                com.example.platform.timeline.semantics.effect.VideoTransform.IDENTITY,
                com.example.platform.render.domain.renderplan.VideoEffectSpec.IDENTITY, TWO, MediaTime.ofMillis(4500),
                java.util.Optional.of(new com.example.platform.render.domain.renderplan.RenderDependency.Dissolve(
                        "transition", "outgoing", "incoming", TWO)),
                java.util.Optional.of(new com.example.platform.render.domain.renderplan.RenderDependency.OpacityRamp(
                        TWO, 0.2, MediaTime.ofMillis(4000), 0.9)), java.util.Optional.of(position));
        var input = new InputBinding(new ExecutionInputId("input-clip"), "composite",
                new ExecutionStepId("composite"), new RenderNodeId("render-composite"),
                "clip", new ExecutionStepId("clip"), new RenderNodeId("render-clip"), dependency, null, null);
        var plan = plan(List.of(unit("multitrack-composite", "composite", null, null, null, List.of(input),
                List.of(outputDeclaration("composite")))), null);
        byte[] data = PhysicalExecutionPlanCanonicalCodec.encode(plan);
        var restored = PhysicalExecutionPlanCanonicalCodec.decode(data);
        assertThat(restored).isEqualTo(plan);
        assertThat(PhysicalExecutionPlanCanonicalCodec.encode(restored)).isEqualTo(data);
    }

    /** A plan whose unit and plan-level optionals are all absent must encode as "absent" without NPE. */
    @Test
    void allAbsentOptionalsEncodeAsAbsentAndRoundTrip() {
        PhysicalPlanUnit unit = unit("encode", "ln-encode", null, null, null,
                List.of(), List.of(outputDeclaration("ln-encode")));
        PhysicalExecutionPlan plan = plan(List.of(unit), null);

        byte[] encoded = assertDoesNotThrowEncoding(plan);
        PhysicalExecutionPlan decoded = PhysicalExecutionPlanCanonicalCodec.decode(encoded);
        assertThat(decoded).isEqualTo(plan);
        assertThat(PhysicalExecutionPlanCanonicalCodec.encode(decoded)).isEqualTo(encoded);
    }

    /** temporalWindow and executionCoverage are independent optionals; each must survive presence/absence. */
    @Test
    void temporalWindowAndCoveragePresenceAreIndependentAndPreserved() {
        PhysicalPlanUnit windowOnly = unit("decode", "ln-a", new RenderSampleWindow(ZERO, TWO, FPS), null, null,
                List.of(), List.of(outputDeclaration("ln-a")));
        PhysicalPlanUnit coverageOnly = unit("decode", "ln-b", null, new RenderExecutionCoverage(ZERO, TWO, FPS), null,
                List.of(), List.of(outputDeclaration("ln-b")));
        PhysicalExecutionPlan plan = plan(List.of(windowOnly, coverageOnly), null);

        PhysicalExecutionPlan decoded = PhysicalExecutionPlanCanonicalCodec.decode(
                PhysicalExecutionPlanCanonicalCodec.encode(plan));
        assertThat(decoded).isEqualTo(plan);
        assertThat(decoded.units().get(0).temporalWindow()).isNotNull();
        assertThat(decoded.units().get(0).executionCoverage()).isNull();
        assertThat(decoded.units().get(1).temporalWindow()).isNull();
        assertThat(decoded.units().get(1).executionCoverage()).isNotNull();
    }

    /** Non-null optionals must still round-trip and encode deterministically (no regression). */
    @Test
    void nonNullOptionalsStillRoundTripDeterministically() {
        PhysicalPlanUnit unit = unit("decode", "ln-a",
                new RenderSampleWindow(ZERO, TWO, FPS),
                new RenderExecutionCoverage(ZERO, TWO, FPS),
                new RenderExtent(ZERO, TWO, FPS),
                List.of(sourceInput("input-a", "ln-a")),
                List.of(outputDeclaration("ln-a")));
        PhysicalExecutionPlan plan = plan(List.of(unit), new RenderExtent(ZERO, TWO, FPS));

        byte[] encoded = PhysicalExecutionPlanCanonicalCodec.encode(plan);
        assertThat(PhysicalExecutionPlanCanonicalCodec.decode(encoded)).isEqualTo(plan);
        assertThat(PhysicalExecutionPlanCanonicalCodec.encode(plan)).isEqualTo(encoded);
    }

    private static byte[] assertDoesNotThrowEncoding(PhysicalExecutionPlan plan) {
        assertThatCode(() -> PhysicalExecutionPlanCanonicalCodec.encode(plan)).doesNotThrowAnyException();
        return PhysicalExecutionPlanCanonicalCodec.encode(plan);
    }

    private static PhysicalPlanUnit unit(
            String operationKey, String logicalNodeId,
            RenderSampleWindow temporalWindow, RenderExecutionCoverage coverage, RenderExtent propagatedExtent,
            List<InputBinding> inputs, List<OutputDeclaration> outputs) {
        return new PhysicalPlanUnit(
                new ExecutionStepId(logicalNodeId),
                logicalNodeId,
                new RenderNodeId("render-" + logicalNodeId),
                new RenderNodeKind.Decode(),
                operationKey,
                inputs,
                outputs,
                List.of(),
                temporalWindow,
                coverage,
                List.of(new CapabilityRequirementRef(CapabilityRequirement.of(
                        CapabilityId.of("video.decode"),
                        ContractVersionRange.exactly(ContractVersion.of(1, 0))))),
                List.of(new ExecutionIntentRef(new RenderExecutionRequirement(
                        RenderExecutionRequirement.GpuRequirement.NONE,
                        RenderExecutionRequirement.RenderDeterminismClass.DETERMINISTIC,
                        true))),
                propagatedExtent,
                true);
    }

    private static InputBinding sourceInput(String inputId, String consumerLogicalNodeId) {
        return new InputBinding(
                new ExecutionInputId(inputId),
                consumerLogicalNodeId,
                new ExecutionStepId(consumerLogicalNodeId),
                new RenderNodeId("render-" + consumerLogicalNodeId),
                null, null, null, null,
                new RenderArtifactReference.SourceArtifact(
                        new ArtifactId("art-source"), ContentDigest.sha256("a".repeat(64))),
                new RenderSampleWindow(ZERO, TWO, FPS));
    }

    private static OutputDeclaration outputDeclaration(String logicalNodeId) {
        return new OutputDeclaration(
                new ExecutionOutputId("output-" + logicalNodeId),
                logicalNodeId,
                new RenderNodeId("render-" + logicalNodeId),
                List.of(RenderOutputRequirement.of(RenderOutputRole.RENDER_MASTER)),
                List.of(),
                List.of(),
                List.of(new RenderArtifactReference.FinalArtifactExpectation(RenderOutputRole.RENDER_MASTER)));
    }

    private static PhysicalExecutionPlan plan(List<PhysicalPlanUnit> units, RenderExtent propagatedExtent) {
        return new PhysicalExecutionPlan(
                "1",
                new ExecutionPlanId("codec-null-safety-plan"),
                ExecutionPlanSchemaVersion.V1,
                new RenderPlanFingerprint("codec-null-safety-fingerprint"),
                units,
                propagatedExtent,
                new PhysicalExecutionPlanDigest("declared-digest"));
    }
}
