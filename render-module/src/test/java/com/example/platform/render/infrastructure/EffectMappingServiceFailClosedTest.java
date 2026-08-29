package com.example.platform.render.infrastructure;

import com.example.platform.render.app.dto.EffectPackDtos.EffectPackEffectDto;
import com.example.platform.render.infrastructure.effects.EffectProviderRouter;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectMappingServiceFailClosedTest {

    @Test
    void historicalConcreteMappingsDoNotClaimGenericProviderSupport() {
        EffectMappingService mappings = new EffectMappingService();

        for (EffectDescriptor descriptor : mappings.getAllDescriptors()) {
            assertFalse(descriptor.providerKeys().contains("provider"), descriptor.effectKey());
            assertEquals(new HashSet<>(descriptor.providerKeys()).size(),
                    descriptor.providerKeys().size(), descriptor.effectKey());
        }
        assertEquals(List.of("ofx"), mappings.getDescriptor("video.particle_overlay")
                .orElseThrow().providerKeys());
    }

    @Test
    void missingCatalogProviderMappingsRemainEmpty() {
        EffectMappingService mappings = new EffectMappingService();
        mappings.reloadFromCatalog(List.of(new EffectPackEffectDto(
                "video.unbound", "Unbound", "video", "No provider binding",
                Map.of(), Map.of(), null, List.of("FREE"), "filter", true)));

        assertTrue(mappings.getDescriptor("video.unbound").orElseThrow()
                .providerKeys().isEmpty());
        assertTrue(mappings.getMappings("video.unbound").isEmpty());
    }

    @Test
    void routerUsesIndependentOfxMappingWithoutGenericAlias() {
        EffectMappingService mappings = new EffectMappingService();
        EffectProviderRouter router = new EffectProviderRouter(mappings);

        assertEquals("ofx", router.resolveProviderForEffect(
                "video.blur", Set.of("ofx", "provider")).orElseThrow());
    }
}
