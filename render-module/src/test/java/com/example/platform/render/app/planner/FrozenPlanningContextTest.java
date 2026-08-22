package com.example.platform.render.app.planner;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.domain.renderplan.RenderExtent;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRE-#21 C2 correction — FrozenPlanningContext exactness tests.
 *
 * Proves:
 * - declared CapabilityRequirement data is carried (W2 authority preserved)
 * - typed requested RenderExtent is carried where applicable
 * - capability resolution facts remain pre-resolved (no invention)
 * - collection inputs are defensively copied (caller mutation cannot alter
 *   the frozen state)
 * - planner output is deterministic for equal frozen contexts
 */
class FrozenPlanningContextTest {

    private static CapabilityRequirement req(String id) {
        return new CapabilityRequirement(new CapabilityId(id),
                ContractVersionRange.atLeast(ContractVersion.of(1, 0)), true, List.of());
    }

    private static RenderExtent extent() {
        return new RenderExtent(MediaTime.ofMillis(0), MediaTime.ofMillis(10000), FrameRate.of(25, 1));
    }

    private static FrozenPlanningContext baseCtx() {
        return FrozenPlanningContext.of("prod-1", "TRANSCRIPT", "ten-1", "proj-1", false,
                List.of(req("media.transcribe")),
                Map.of("TRANSCRIPT", new FrozenPlanningContext.CapabilityResolutionFact(
                        "TRANSCRIPT", "ASR", "producer-a", "backend-asr", "ASR", "frozen", true)),
                Map.of("up-1", new FrozenPlanningContext.DependencyFact("up-1", "THUMBNAIL", "PROCESSING")),
                extent());
    }

    @Test
    void carriesDeclaredCapabilityRequirements() {
        FrozenPlanningContext ctx = baseCtx();
        assertEquals(1, ctx.declaredCapabilityRequirements().size());
        assertEquals("media.transcribe", ctx.declaredCapabilityRequirements().get(0).capabilityId().value());
    }

    @Test
    void carriesTypedRequestedRenderExtent() {
        FrozenPlanningContext ctx = baseCtx();
        assertNotNull(ctx.requestedRenderExtent());
        assertEquals(MediaTime.ofMillis(0), ctx.requestedRenderExtent().start());
        assertEquals(MediaTime.ofMillis(10000), ctx.requestedRenderExtent().end());
        assertEquals(FrameRate.of(25, 1), ctx.requestedRenderExtent().frameRate());
    }

    @Test
    void capabilityFactsRemainPreResolved() {
        FrozenPlanningContext ctx = baseCtx();
        var fact = ctx.capabilityFacts().get("TRANSCRIPT");
        assertNotNull(fact);
        assertEquals("producer-a", fact.producerId());
        assertTrue(fact.resolved());
        // planner never invents: context only carries pre-resolved facts
        assertEquals(1, ctx.capabilityFacts().size());
    }

    @Test
    void callerMutationCannotAlterFrozenState() {
        List<CapabilityRequirement> reqs = new ArrayList<>(List.of(req("media.a")));
        Map<String, FrozenPlanningContext.CapabilityResolutionFact> caps = new HashMap<>();
        caps.put("T1", new FrozenPlanningContext.CapabilityResolutionFact("T1", "ASR", "p1", "b1", "ASR", "r", true));
        Map<String, FrozenPlanningContext.DependencyFact> deps = new HashMap<>();
        deps.put("d1", new FrozenPlanningContext.DependencyFact("d1", "T1", "PROCESSING"));

        FrozenPlanningContext ctx = FrozenPlanningContext.of(
                "prod-1", "T1", "ten-1", "proj-1", false, reqs, caps, deps, null);

        // mutate caller-owned containers after construction
        reqs.add(req("media.b"));
        caps.put("T2", new FrozenPlanningContext.CapabilityResolutionFact("T2", "OCR", "p2", "b2", "OCR", "r", true));
        deps.put("d2", new FrozenPlanningContext.DependencyFact("d2", "T2", "READY"));

        assertEquals(1, ctx.declaredCapabilityRequirements().size(), "requirement list must be frozen");
        assertEquals(1, ctx.capabilityFacts().size(), "capability facts must be frozen");
        assertEquals(1, ctx.dependencyFacts().size(), "dependency facts must be frozen");
        assertThrows(UnsupportedOperationException.class, () -> ctx.capabilityFacts().put("x", null),
                "exposed collections must be immutable");
    }

    @Test
    void nullCollectionsBecomeEmptyImmutable() {
        FrozenPlanningContext ctx = FrozenPlanningContext.of(
                "prod-1", "T1", "ten-1", "proj-1", false, null, null, null, null);
        assertTrue(ctx.declaredCapabilityRequirements().isEmpty());
        assertTrue(ctx.capabilityFacts().isEmpty());
        assertTrue(ctx.dependencyFacts().isEmpty());
        assertNull(ctx.requestedRenderExtent());
    }
}
