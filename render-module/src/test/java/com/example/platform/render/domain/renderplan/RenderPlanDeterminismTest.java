package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * B. Determinism (brief §13B): same input 100+ times -> identical fingerprint;
 * shuffled construction -> identical; semantic changes -> different; non-semantic
 * changes (resolution state, capability context, request id) -> UNCHANGED.
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
                base.hydratedRevision(), changed, base.resolution(), base.capabilities());
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
                base.hydratedRevision(), changed, base.resolution(), base.capabilities());
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
    void fingerprintDependsOnRevision() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        HydratedTimelineRevision otherRevision = new HydratedTimelineRevision(
                new TimelineRevisionReference("rev-2", ContentDigest.sha256(TestPlans.REVISION_DIGEST_HEX)),
                base.hydratedRevision().clips(),
                base.hydratedRevision().effects(),
                base.hydratedRevision().effectDefinitions(),
                base.hydratedRevision().audioMix(),
                base.hydratedRevision().textElements());
        RenderPlanningInput changed = new RenderPlanningInput(
                otherRevision, base.request(), base.resolution(), base.capabilities());
        assertNotEquals(planner.plan(base).plan().fingerprint().sha256Hex(),
                planner.plan(changed).plan().fingerprint().sha256Hex(),
                "revision change -> different fingerprint");
    }
}
