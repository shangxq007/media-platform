package com.example.platform.render.infrastructure.providerruntime.capability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class EffectToProviderMapperTest {

    private final EffectToProviderMapper mapper = new EffectToProviderMapper();

    @Test
    void subtitleCapabilitiesRemainSemanticWithoutInventingProviderIdentity() {
        assertEquals(Set.of("subtitle_processing", "render"),
                mapper.getRequiredCapabilities("subtitle.burn_in"));
        assertTrue(mapper.getProvidersForCapability("subtitle_processing").isEmpty());
        assertTrue(mapper.getProvidersForEffect("subtitle.burn_in").isEmpty());
        assertTrue(mapper.getProvidersForEffect("subtitle.style").isEmpty());
    }
}
