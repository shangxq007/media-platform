package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.version.TimelineRevision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B. Determinism (brief §13B, R2 B1/B3): same input 100+ times -> identical
 * fingerprint; semantic changes -> different; non-semantic changes (resolution
 * state, capability context, request id) -> UNCHANGED. R2 B1: revision change
 * is expressed through a separate verified revision projection (constructed via
 * the authoritative factory); fragments cannot be mixed arbitrarily.
 */
class RenderPlanDeterminismTest {

    @Test
    void sameInputRepeatedProducesIdenticalFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput input = TestPlans.canonicalInput();
        String first = planner.plan(input).plan().fingerprint().sha256Hex();
        for (int i = 0; i < 100; i++) {
            assertEquals(first, planner.plan(input).plan().fingerprint().sha256Hex(),
                    "deterministic fingerprint, iteration " + i);
        }
    }

    @Test
    void extentChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();

        RenderRequest req = base.request();
        RenderRequest changed = new RenderRequest(req.id(),
                new RenderExtent(MediaTime.ofRational(0, 1), MediaTime.ofRational(3, 1), req.extent().frameRate()),
                req.outputs());
        RenderPlanningInput changedInput = new RenderPlanningInput(
                base.authoredSnapshot(),
                changed, base.resolution(), base.capabilities());
        String changedFp = planner.plan(changedInput).plan().fingerprint().sha256Hex();

        assertNotEquals(baseFp, changedFp, "extent change -> different fingerprint");
    }

    @Test
    void outputRequirementChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();

        RenderRequest req = base.request();
        RenderRequest changed = new RenderRequest(req.id(), req.extent(),
                List.of(RenderOutputRequirement.of(RenderOutputRole.DELIVERY_RENDITION)));
        RenderPlanningInput changedInput = new RenderPlanningInput(
                base.authoredSnapshot(),
                changed, base.resolution(), base.capabilities());
        String changedFp = planner.plan(changedInput).plan().fingerprint().sha256Hex();

        assertNotEquals(baseFp, changedFp, "output requirement change -> different fingerprint");
    }

    @Test
    void resolutionStateChangeDoesNotChangeFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput resolved = TestPlans.inputWithSourceState(RenderSourceResolutionState.RESOLVED);
        RenderPlanningInput unavailable = TestPlans.inputWithSourceState(RenderSourceResolutionState.UNAVAILABLE);
        assertEquals(planner.plan(resolved).plan().fingerprint().sha256Hex(),
                planner.plan(unavailable).plan().fingerprint().sha256Hex(),
                "resolution state change -> fingerprint UNCHANGED");
    }

    @Test
    void capabilityContextChangeDoesNotChangeFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        RenderPlanningInput caps = TestPlans.inputWithCapabilities(new CapabilityContext(
                Set.of(CapabilityId.of("video.decode"))));
        assertEquals(planner.plan(base).plan().fingerprint().sha256Hex(),
                planner.plan(caps).plan().fingerprint().sha256Hex(),
                "capability context change -> fingerprint UNCHANGED");
    }

    @Test
    void requestIdChangeDoesNotChangeFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        RenderPlanningInput changed = TestPlans.inputWithRequestId("req-other");
        assertEquals(planner.plan(base).plan().fingerprint().sha256Hex(),
                planner.plan(changed).plan().fingerprint().sha256Hex(),
                "request id change -> fingerprint UNCHANGED");
    }

    @Test
    void fingerprintExcludesPlanId() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        // different request id => different plan id, but same fingerprint
        RenderPlanningInput changed = TestPlans.inputWithRequestId("req-other");
        assertNotEquals(planner.plan(base).plan().id(), planner.plan(changed).plan().id(),
                "plan id differs");
        assertEquals(planner.plan(base).plan().fingerprint().sha256Hex(),
                planner.plan(changed).plan().fingerprint().sha256Hex(),
                "fingerprint excludes plan id");
    }

    @Test
    void revisionChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();

        // R2 B1: a different revision is a DIFFERENT verified projection built
        // through the authoritative factory (its own digest is verified), NOT an
        // arbitrary assembly of base fragments under a different id. The rev-2
        // document has different content (different text), so its canonical
        // digest differs and is recorded on the revision.
        TimelineDocument rev2Doc = new TimelineDocument(
                com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new com.example.platform.timeline.canonical.TimelineTrack(
                        TestPlans.TRACK_ID, "v1",
                        com.example.platform.timeline.canonical.TrackType.VIDEO,
                        List.of(TestPlans.canonicalTimelineClip()))),
                com.example.platform.timeline.canonical.TimelineMetadata.empty(),
                TestPlans.audioMix(),
                List.of(),
                List.of(TestPlans.textElementWithContent("rev-2-content")));
        TimelineContentDigester digester = new TimelineContentDigester();
        TimelineRevision otherTimelineRevision = new TimelineRevision(
                "rev-2", "product-1", null,
                com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                rev2Doc, digester.digest(rev2Doc), java.time.Instant.EPOCH, "test");
        VerifiedTimelineRevision otherVerified = VerifiedTimelineRevisionFactory.verified(
                otherTimelineRevision, digester);
        RenderPlanningInput changed = new RenderPlanningInput(
                new VerifiedRenderSemanticSnapshot(otherVerified, base.effectSemanticSnapshot()),
                base.request(), base.resolution(), base.capabilities());

        assertNotEquals(baseFp, planner.plan(changed).plan().fingerprint().sha256Hex(),
                "revision change -> different fingerprint");
    }
}
