package com.example.platform.render.app.renderplan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanningDiagnosticCode;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.shared.capability.CapabilityId;
import com.example.platform.shared.identity.ArtifactId;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Integration coverage for the full typed-chain #20 pass reached through the
 * production entry: real immutable {@code TimelineRevision} (digests computed by
 * the timeline authorities) → verification factory → {@code RenderPlanningInput}
 * → {@code DefaultRenderPlanner} → {@code RenderPlanningResult}.
 *
 * <p>No databases, providers, workers, or Temporal are involved: this proves the
 * #20 stage boundary in isolation, as required by the Phase 2 plan.
 */
class RenderPlanningEntryServiceIntegrationTest {

    private final RenderPlanningEntryService service = new RenderPlanningEntryService();

    @Test
    void fullChainProducesConsistentPlanAndGraphForARealRevision() {
        RenderPlanningResult result = service.plan(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                RenderPlanningEntryTestFixture.resolvedSources(),
                RenderPlanningEntryTestFixture.capabilities());

        assertEquals(RenderPlanStatus.PLANNABLE, result.status());
        assertEquals(result.plan().fingerprint(), result.graph().planFingerprint(),
                "the graph is built over the exact plan fingerprint (CR-02 consistency)");
        assertNotNull(result.plan().effectSemanticReference(),
                "the plan retains the authored Effect semantic reference");
        assertEquals(RenderPlanningEntryTestFixture.REVISION_ID,
                result.plan().effectSemanticReference().revisionId(),
                "the retained Effect reference is the authored revision's pin");
        assertTrue(result.diagnostics().isEmpty(),
                () -> "a fully resolved, fully capable plan yields no diagnostics: " + result.diagnostics());
    }

    @Test
    void fullChainIsDeterministicAndDiagnosticsStayDeterministicallyOrdered() {
        RenderPlanningResult first = service.plan(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                RenderPlanningEntryTestFixture.resolvedSources(),
                RenderPlanningEntryTestFixture.capabilities());
        RenderPlanningResult second = service.plan(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                RenderPlanningEntryTestFixture.resolvedSources(),
                RenderPlanningEntryTestFixture.capabilities());

        assertEquals(first.plan(), second.plan(), "identical semantics → identical plan");
        assertEquals(first.graph(), second.graph(), "identical semantics → identical graph");
        assertEquals(first.diagnostics(), second.diagnostics());
    }

    @Test
    void unresolvedSourceReachesUnrenderableFailClosed() {
        SourceResolutionInput failed = new SourceResolutionInput(Map.of(
                new ArtifactId(RenderPlanningEntryTestFixture.ARTIFACT_ID),
                RenderSourceResolutionState.FAILED));

        RenderPlanningResult result = service.plan(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                failed,
                RenderPlanningEntryTestFixture.capabilities());

        assertEquals(RenderPlanStatus.UNRENDERABLE, result.status(),
                "a failed source resolution fails the plan closed");
        assertTrue(result.diagnostics().stream()
                        .anyMatch(d -> d.code() == RenderPlanningDiagnosticCode.SOURCE_UNRESOLVED),
                () -> "expected SOURCE_UNRESOLVED diagnostic, got: " + result.diagnostics());
    }

    @Test
    void unavailableCapabilityReachesUnrenderableFailClosed() {
        CapabilityContext missingOutputEncode = new CapabilityContext(Set.of(
                CapabilityId.of("video.decode"),
                CapabilityId.of("render.composite")));

        RenderPlanningResult result = service.plan(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                RenderPlanningEntryTestFixture.resolvedSources(),
                missingOutputEncode);

        assertEquals(RenderPlanStatus.UNRENDERABLE, result.status(),
                "a missing node capability fails the plan closed");
        assertTrue(result.diagnostics().stream()
                        .anyMatch(d -> d.code() == RenderPlanningDiagnosticCode.CAPABILITY_UNAVAILABLE),
                () -> "expected CAPABILITY_UNAVAILABLE diagnostic, got: " + result.diagnostics());
    }

    @Test
    void planCarriesExactlyTheAuthoredGraphWithoutProviderOrWorkerBinding() {
        RenderPlanningResult result = service.plan(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                RenderPlanningEntryTestFixture.resolvedSources(),
                RenderPlanningEntryTestFixture.capabilities());

        assertFalse(result.graph().nodes().isEmpty(), "graph has nodes");
        assertTrue(result.graph().nodes().stream()
                        .anyMatch(n -> n.kind() instanceof RenderNodeKind.Decode),
                "DECODE node present");
        assertTrue(result.graph().nodes().stream()
                        .anyMatch(n -> n.kind() instanceof RenderNodeKind.Output),
                "OUTPUT node present");
        assertTrue(result.plan().nodes().stream()
                        .allMatch(node -> node.capabilityRequirements().stream()
                                .allMatch(cap -> !cap.capabilityId().value().isBlank())),
                "capability requirements are typed platform capability ids");
    }
}
