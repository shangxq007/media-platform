package com.example.platform.federation.graphql.dataloader;

import com.example.platform.identity.app.TenantRepository;
import com.example.platform.identity.domain.Tenant;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class TenantDataLoaderTest {

    @Test
    void loadsTenantDataInBatch() throws Exception {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        Tenant tenant = new Tenant("tenant-1", "Test Tenant", Tenant.TenantStatus.ACTIVE, Instant.now());
        when(tenantRepository.findById("tenant-1")).thenReturn(Optional.of(tenant));

        TenantDataLoader loader = new TenantDataLoader(tenantRepository);
        CompletionStage<Map<String, Map<String, Object>>> stage = loader.load(Set.of("tenant-1"));
        Map<String, Map<String, Object>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("tenant-1"));
        assertEquals("tenant-1", result.get("tenant-1").get("id"));
    }

    @Test
    void handlesMissingTenant() throws Exception {
        TenantRepository tenantRepository = mock(TenantRepository.class);
        when(tenantRepository.findById("missing")).thenReturn(Optional.empty());

        TenantDataLoader loader = new TenantDataLoader(tenantRepository);
        CompletionStage<Map<String, Map<String, Object>>> stage = loader.load(Set.of("missing"));
        Map<String, Map<String, Object>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("missing"));
        assertEquals("missing", result.get("missing").get("id"));
    }
}
