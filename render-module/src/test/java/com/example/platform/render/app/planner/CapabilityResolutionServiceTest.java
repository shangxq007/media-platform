package com.example.platform.render.app.planner;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.extension.domain.ContractVersion;
import com.example.platform.extension.domain.ContractVersionRange;
import com.example.platform.render.app.capability.CapabilityCatalogService;
import com.example.platform.render.domain.capability.CapabilityDescriptor;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * PRE-#21 W2 — capability resolution authority tests.
 *
 * The resolver resolves DECLARED requirements; it never invents them.
 */
class CapabilityResolutionServiceTest {

    private final CapabilityCatalogService catalog = new CapabilityCatalogService(List.of()) {
        @Override
        public List<CapabilityDescriptor> candidatesFor(String capability) {
            if ("media.transcribe".equals(capability)) {
                return List.of(new CapabilityDescriptor(
                        "media.transcribe:producer-asr", "media.transcribe",
                        "producer-asr", "producer-asr", "1.0",
                        "backend-asr", "ASR",
                        List.of("JSON_DOCUMENT"), List.of("TRANSCRIPT"),
                        true, 10, true));
            }
            return List.of();
        }
    };

    private final CapabilityResolutionService resolver = new CapabilityResolutionService(catalog);

    private CapabilityRequirement req(String capabilityId) {
        return new CapabilityRequirement(
                new CapabilityId(capabilityId),
                ContractVersionRange.atLeast(ContractVersion.of(1, 0)),
                true, List.of());
    }

    @Test
    void resolvesDeclaredRequirement() {
        var res = resolver.resolve(req("media.transcribe"));
        assertTrue(res.resolved());
        assertEquals("producer-asr", res.producerId());
        assertEquals("media.transcribe", res.capabilityId());
    }

    @Test
    void unknownRequirementIsUnresolved() {
        var res = resolver.resolve(req("media.does-not-exist"));
        assertFalse(res.resolved());
        assertNull(res.producerId());
    }

    @Test
    void explainUsesRequirementIdentity() {
        String e = resolver.explain(req("media.transcribe"));
        assertTrue(e.contains("media.transcribe"));
        assertTrue(e.contains("producer-asr"));
    }
}
