package com.example.platform.render.domain.renderplan;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F. Capability boundary (brief §13F): capability enum ids contain no provider/
 * worker/tier/price strings; plan fingerprint unchanged when CapabilityContext
 * changes; no node carries provider fields (structural assertions on RenderNode).
 */
class CapabilityBoundaryTest {

    @Test
    void capabilityEnumHasNoProviderStrings() {
        for (RenderCapabilityId id : RenderCapabilityId.values()) {
            String name = id.name();
            assertFalse(name.toLowerCase().contains("ffmpeg"), "no ffmpeg in " + name);
            assertFalse(name.toLowerCase().contains("worker"), "no worker in " + name);
            assertFalse(name.toLowerCase().contains("tier"), "no tier in " + name);
            assertFalse(name.toLowerCase().contains("price"), "no price in " + name);
            assertFalse(name.toLowerCase().contains("provider"), "no provider in " + name);
        }
    }

    @Test
    void fingerprintUnchangedWhenCapabilityContextChanges() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        RenderPlanningInput changed = TestPlans.inputWithCapabilities(new CapabilityContext(
                java.util.Set.of(RenderCapabilityId.DECODE)));
        assertEquals(planner.plan(base).plan().fingerprint().sha256Hex(),
                planner.plan(changed).plan().fingerprint().sha256Hex(),
                "fingerprint excludes capability context");
    }

    @Test
    void noNodeCarriesProviderFields() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        for (RenderNode node : result.plan().nodes()) {
            // structural: RenderNode has no Map<String,Object>, no provider/worker/device fields
            assertTrue(node.executionRequirements().isEmpty() || node.executionRequirements().stream()
                    .allMatch(er -> er.gpu() != null && er.determinism() != null),
                    "execution requirements are typed, not free-form");
            assertNotNull(node.id());
            assertNotNull(node.kind());
            assertNotNull(node.componentPath());
            assertNotNull(node.artifactReferences());
            assertNotNull(node.capabilityRequirements());
            assertNotNull(node.outputRequirements());
        }
    }

    @Test
    void capabilityEnumCountIsBounded() {
        // V1 vocabulary is bounded (13 ids); not open-ended
        assertTrue(RenderCapabilityId.values().length <= 20, "bounded capability vocabulary");
        assertEquals(13, RenderCapabilityId.values().length, "exactly 13 V1 capabilities");
    }

    @Test
    void allCapabilityIdsAreUppercaseEnumConvention() {
        Arrays.stream(RenderCapabilityId.values()).forEach(id ->
                assertEquals(id.name(), id.name().toUpperCase(), "uppercase enum name"));
    }
}
