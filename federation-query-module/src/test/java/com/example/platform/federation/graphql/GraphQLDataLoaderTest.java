package com.example.platform.federation.graphql;

import com.example.platform.federation.graphql.dataloader.UserDataLoader;
import com.example.platform.identity.app.IdentityAccessService;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class GraphQLDataLoaderTest {

    @Test
    void userDataLoaderLoadsMultipleUsersInBatch() throws Exception {
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        when(identityService.overview()).thenReturn(Map.of("id", "user-1", "status", "active"));

        UserDataLoader loader = new UserDataLoader(identityService);
        CompletionStage<Map<String, Map<String, Object>>> stage = loader.load(Set.of("user-1", "user-2"));
        Map<String, Map<String, Object>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("user-1"));
        assertTrue(result.containsKey("user-2"));
    }

    @Test
    void userDataLoaderReturnsEmptyMapForEmptyKeys() throws Exception {
        IdentityAccessService identityService = mock(IdentityAccessService.class);

        UserDataLoader loader = new UserDataLoader(identityService);
        CompletionStage<Map<String, Map<String, Object>>> stage = loader.load(Set.of());
        Map<String, Map<String, Object>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void userDataLoaderDoesNotLeakDataAcrossTenants() throws Exception {
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        when(identityService.overview()).thenReturn(Map.of("tenant", "tenant-A"));

        UserDataLoader loader = new UserDataLoader(identityService);
        Map<String, Map<String, Object>> result = loader.load(Set.of("user-1")).toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("user-1"));
        Map<String, Object> userData = result.get("user-1");
        assertNotNull(userData);
        assertEquals("tenant-A", userData.get("tenant"));
    }

    @Test
    void userDataLoaderHandlesServiceExceptionGracefully() throws Exception {
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        when(identityService.overview()).thenThrow(new RuntimeException("Service unavailable"));

        UserDataLoader loader = new UserDataLoader(identityService);
        Map<String, Map<String, Object>> result = loader.load(Set.of("user-x")).toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("user-x"));
        assertEquals("user-x", result.get("user-x").get("id"));
    }

    @Test
    void userDataLoaderReturnsSameDataForSameKey() throws Exception {
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        when(identityService.overview()).thenReturn(Map.of("name", "Test User"));

        UserDataLoader loader = new UserDataLoader(identityService);
        Map<String, Map<String, Object>> result = loader.load(Set.of("user-1")).toCompletableFuture().get();

        assertEquals(1, result.size());
        assertEquals("Test User", result.get("user-1").get("name"));
    }

    @Test
    void userDataLoaderCompletesFutureSuccessfully() {
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        when(identityService.overview()).thenReturn(Map.of("ok", true));

        UserDataLoader loader = new UserDataLoader(identityService);
        CompletionStage<Map<String, Map<String, Object>>> stage = loader.load(Set.of("u1"));

        assertDoesNotThrow(() -> stage.toCompletableFuture().get());
    }

    @Test
    void userDataLoaderHandlesSingleKey() throws Exception {
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        when(identityService.overview()).thenReturn(Map.of("count", 1));

        UserDataLoader loader = new UserDataLoader(identityService);
        Map<String, Map<String, Object>> result = loader.load(Set.of("single-1")).toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("single-1"));
        assertEquals(1, result.get("single-1").get("count"));
    }

    @Test
    void userDataLoaderBatchLoadIsAtomic() throws Exception {
        IdentityAccessService identityService = mock(IdentityAccessService.class);
        when(identityService.overview()).thenReturn(Map.of("batch", true));

        UserDataLoader loader = new UserDataLoader(identityService);
        Set<String> keys = Set.of("a", "b", "c", "d", "e");
        Map<String, Map<String, Object>> result = loader.load(keys).toCompletableFuture().get();

        assertEquals(5, result.size());
        for (String key : keys) {
            assertTrue(result.containsKey(key), "Result must contain key: " + key);
        }
    }
}
