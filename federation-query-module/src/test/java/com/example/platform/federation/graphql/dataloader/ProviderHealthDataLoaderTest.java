package com.example.platform.federation.graphql.dataloader;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;

class ProviderHealthDataLoaderTest {

    @Test
    void loadsProviderHealthInBatch() throws Exception {
        ProviderHealthDataLoader loader = new ProviderHealthDataLoader();
        CompletionStage<Map<String, Map<String, Object>>> stage = loader.load(Set.of("provider-a", "provider-b"));
        Map<String, Map<String, Object>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("provider-a"));
        assertTrue(result.containsKey("provider-b"));
        assertEquals("HEALTHY", result.get("provider-a").get("status"));
    }
}
