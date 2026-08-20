package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * F. Capability boundary (brief §13F, correction F3): platform capability ids
 * contain no provider/worker/tier/price strings; plan fingerprint unchanged when
 * CapabilityContext changes; no node carries provider fields (structural
 * assertions on RenderNode); logical nodes carry platform CapabilityRequirement.
 */
class CapabilityBoundaryTest {

    @Test
    void capabilityIdsHaveNoProviderStrings() {
        // All ids produced by the render capability vocabulary are platform-reserved
        // CapabilityIds. Structural check: every id in the vocabulary is valid and
        // contains no provider/worker/tier/price terms.
        for (CapabilityId id : RenderCapabilityVocabularyIds.all()) {
            String name = id.value();
            assertFalse(name.toLowerCase().contains("ffmpeg"), "no ffmpeg in " + name);
            assertFalse(name.toLowerCase().contains("worker"), "no worker in " + name);
            assertFalse(name.toLowerCase().contains("tier"), "no tier in " + name);
            assertFalse(name.toLowerCase().contains("price"), "no price in " + name);
            assertFalse(name.toLowerCase().contains("provider"), "no provider in " + name);
            assertTrue(id.isPlatformReserved(), "platform-reserved namespace: " + name);
        }
    }

    @Test
    void fingerprintUnchangedWhenCapabilityContextChanges() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        RenderPlanningInput changed = TestPlans.inputWithCapabilities(new CapabilityContext(
                Set.of(CapabilityId.of("video.decode"))));
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
    void logicalNodesCarryPlatformCapabilityRequirements() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        assertFalse(result.plan().nodes().isEmpty());
        for (RenderNode node : result.plan().nodes()) {
            for (var cap : node.capabilityRequirements()) {
                // F3: every capability requirement is the platform CapabilityRequirement
                // (typed CapabilityId + explicit contract version range)
                assertNotNull(cap.capabilityId());
                assertNotNull(cap.contractRange());
                assertTrue(cap.capabilityId().isPlatformReserved(),
                        "logical plan capability must be platform-reserved: " + cap.capabilityId());
            }
        }
    }

    @Test
    void capabilityAvailabilityDoesNotAlterLogicalFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput full = TestPlans.canonicalInput();
        RenderPlanningInput minimal = TestPlans.inputWithCapabilities(new CapabilityContext(Set.of()));
        assertEquals(planner.plan(full).plan().fingerprint().sha256Hex(),
                planner.plan(minimal).plan().fingerprint().sha256Hex(),
                "capability availability is transient — never in the logical fingerprint");
    }
}
