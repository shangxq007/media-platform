package com.example.platform.federation.graphql.dataloader;

import com.example.platform.identity.app.IdentityAccessService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class UserDataLoaderTest {

    @Test
    void loadsUserDataInBatch() throws Exception {
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        when(identityService.overview()).thenReturn(Map.of("id", "user-1", "status", "active"));

        UserDataLoader loader = new UserDataLoader(identityService);
        CompletionStage<Map<String, Map<String, Object>>> stage = loader.load(Set.of("user-1", "user-2"));
        Map<String, Map<String, Object>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("user-1"));
        assertTrue(result.containsKey("user-2"));
    }
}
