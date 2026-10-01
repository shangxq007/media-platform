package com.example.platform.render.app.renderplan;

import static com.example.platform.render.app.renderplan.RenderPlanningEntryTestFixture.ARTIFACT_ID;
import static com.example.platform.render.app.renderplan.RenderPlanningEntryTestFixture.REVISION_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.domain.renderplan.CapabilityContext;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlanStatus;
import com.example.platform.render.domain.renderplan.RenderPlanner;
import com.example.platform.render.domain.renderplan.RenderPlanningInput;
import com.example.platform.render.domain.renderplan.RenderPlanningResult;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.version.TimelineRevision;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

/**
 * Unit coverage for {@link RenderPlanningEntryService} — the typed-chain #20
 * production entry. Verifies input construction (fail-closed verification),
 * planner delegation, deterministic results, and rejection of invalid inputs.
 */
class RenderPlanningEntryServiceTest {

    private final RenderPlanningEntryService service = new RenderPlanningEntryService();

    @Test
    void planBuildsVerifiedInputAndRunsTheCanonicalPlanner() {
        RenderPlanningResult result = service.plan(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                RenderPlanningEntryTestFixture.resolvedSources(),
                RenderPlanningEntryTestFixture.capabilities());

        assertEquals(RenderPlanStatus.PLANNABLE, result.status(),
                "a resolved single-clip revision with decode/output capabilities is PLANNABLE");
        assertNotNull(result.plan(), "plan produced");
        assertNotNull(result.graph(), "graph produced");
        assertEquals(REVISION_ID, result.plan().revision().revisionId(),
                "plan carries the authored revision identity");
        assertTrue(result.graph().nodes().stream()
                        .anyMatch(n -> n.kind() instanceof RenderNodeKind.Decode),
                "graph contains the DECODE node for the clip");
        assertTrue(result.graph().nodes().stream()
                        .anyMatch(n -> n.kind() instanceof RenderNodeKind.Output),
                "graph contains the OUTPUT node for the requested output");
    }

    @Test
    void planIsDeterministicAcrossIdenticalInputs() {
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

        assertEquals(first.plan().fingerprint(), second.plan().fingerprint(),
                "same frozen semantics produce the same plan fingerprint");
        assertEquals(first.status(), second.status());
    }

    @Test
    void buildInputBindsVerifiedSnapshotToTheRevision() {
        TimelineRevision revision = RenderPlanningEntryTestFixture.revision();
        EffectSemanticSnapshot snapshot = RenderPlanningEntryTestFixture.emptyEffectSnapshot();
        var request = RenderPlanningEntryTestFixture.request();
        SourceResolutionInput resolution = RenderPlanningEntryTestFixture.resolvedSources();
        CapabilityContext capabilities = RenderPlanningEntryTestFixture.capabilities();

        RenderPlanningInput input =
                service.buildInput(revision, snapshot, request, resolution, capabilities);

        assertEquals(REVISION_ID,
                input.authoredSnapshot().timelineRevision().revision().revisionId(),
                "the verified snapshot is bound to the supplied revision identity");
        assertNotNull(input.authoredSnapshot().timelineRevision().contentDigest(),
                "the verified revision carries its exact content digest");
        assertSame(request, input.request(), "transient request passed through unchanged");
        assertSame(resolution, input.resolution());
        assertSame(capabilities, input.capabilities());
    }

    @Test
    void planFromInputDelegatesToTheInjectedPlanner() {
        RenderPlanningInput input = service.buildInput(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                RenderPlanningEntryTestFixture.resolvedSources(),
                RenderPlanningEntryTestFixture.capabilities());
        RenderPlanningResult sentinel = service.planFromInput(input);

        AtomicReference<RenderPlanningInput> captured = new AtomicReference<>();
        RenderPlanner recordingPlanner = observed -> {
            captured.set(observed);
            return sentinel;
        };
        RenderPlanningEntryService recordingService =
                new RenderPlanningEntryService(recordingPlanner, new TimelineContentDigester());

        RenderPlanningResult returned = recordingService.planFromInput(input);

        assertSame(sentinel, returned, "planFromInput returns the planner's result verbatim");
        assertSame(input, captured.get(), "the planner receives the exact supplied input");
    }

    @Test
    void planFromInputRejectsNullInput() {
        assertThrows(NullPointerException.class, () -> service.planFromInput(null));
    }

    @Test
    void buildInputFailsClosedWhenRevisionIsNotHydrated() {
        EffectSemanticSnapshot snapshot = RenderPlanningEntryTestFixture.emptyEffectSnapshot();
        TimelineRevision notHydrated = RenderPlanningEntryTestFixture.revisionWithTimelineDigest(
                RenderPlanningEntryTestFixture.canonicalTimelineDigest(), snapshot, null);

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.buildInput(notHydrated, snapshot,
                        RenderPlanningEntryTestFixture.request(),
                        RenderPlanningEntryTestFixture.resolvedSources(),
                        RenderPlanningEntryTestFixture.capabilities()));
        assertTrue(failure.getMessage().contains("canonicalTimeline"),
                () -> "expected fail-closed hydration message, got: " + failure.getMessage());
    }

    @Test
    void buildInputFailsClosedWhenTimelineContentDigestDoesNotMatch() {
        EffectSemanticSnapshot snapshot = RenderPlanningEntryTestFixture.emptyEffectSnapshot();
        TimelineRevision mismatched = RenderPlanningEntryTestFixture.revisionWithTimelineDigest(
                "not-the-real-timeline-digest", snapshot, RenderPlanningEntryTestFixture.document());

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.buildInput(mismatched, snapshot,
                        RenderPlanningEntryTestFixture.request(),
                        RenderPlanningEntryTestFixture.resolvedSources(),
                        RenderPlanningEntryTestFixture.capabilities()));
        assertTrue(failure.getMessage().contains("digest mismatch"),
                () -> "expected content-digest fail-closed message, got: " + failure.getMessage());
    }

    @Test
    void buildInputFailsClosedWhenEffectSnapshotDoesNotMatchTheRevisionPin() {
        EffectSemanticSnapshot pinned = RenderPlanningEntryTestFixture.emptyEffectSnapshot();
        TimelineRevision revision = RenderPlanningEntryTestFixture.revision(
                RenderPlanningEntryTestFixture.document(), pinned);
        EffectSemanticSnapshot other = RenderPlanningEntryTestFixture.newEmptyEffectSnapshot();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> service.buildInput(revision, other,
                        RenderPlanningEntryTestFixture.request(),
                        RenderPlanningEntryTestFixture.resolvedSources(),
                        RenderPlanningEntryTestFixture.capabilities()));
        assertTrue(failure.getMessage().contains("binding mismatch"),
                () -> "expected effect-pin fail-closed message, got: " + failure.getMessage());
    }

    @Test
    void buildInputRejectsNullArguments() {
        TimelineRevision revision = RenderPlanningEntryTestFixture.revision();
        EffectSemanticSnapshot snapshot = RenderPlanningEntryTestFixture.emptyEffectSnapshot();
        var request = RenderPlanningEntryTestFixture.request();
        SourceResolutionInput resolution = RenderPlanningEntryTestFixture.resolvedSources();
        CapabilityContext capabilities = RenderPlanningEntryTestFixture.capabilities();

        assertThrows(NullPointerException.class,
                () -> service.buildInput(null, snapshot, request, resolution, capabilities));
        assertThrows(NullPointerException.class,
                () -> service.buildInput(revision, null, request, resolution, capabilities));
        assertThrows(NullPointerException.class,
                () -> service.buildInput(revision, snapshot, null, resolution, capabilities));
        assertThrows(NullPointerException.class,
                () -> service.buildInput(revision, snapshot, request, null, capabilities));
        assertThrows(NullPointerException.class,
                () -> service.buildInput(revision, snapshot, request, resolution, null));
    }

    @Test
    void planResolvesTheExactSourceArtifactFromTheResolutionInput() {
        RenderPlanningResult result = service.plan(
                RenderPlanningEntryTestFixture.revision(),
                RenderPlanningEntryTestFixture.emptyEffectSnapshot(),
                RenderPlanningEntryTestFixture.request(),
                RenderPlanningEntryTestFixture.resolvedSources(),
                RenderPlanningEntryTestFixture.capabilities());

        assertInstanceOf(RenderPlanStatus.class, result.status());
        assertTrue(result.plan().nodes().stream()
                        .flatMap(node -> node.artifactReferences().stream())
                        .anyMatch(ref -> ref instanceof
                                com.example.platform.render.domain.renderplan.RenderArtifactReference.SourceArtifact source
                                && source.artifactId().equals(new com.example.platform.shared.identity.ArtifactId(ARTIFACT_ID))),
                "the DECODE node pins the exact source Artifact from the resolution input");
    }
}
