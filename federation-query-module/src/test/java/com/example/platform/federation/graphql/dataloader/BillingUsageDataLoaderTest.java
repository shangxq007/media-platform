package com.example.platform.federation.graphql.dataloader;

import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.billing.domain.UsageRecord;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingUsageDataLoaderTest {

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void loadsUsageDataWithTenantIsolation() throws Exception {
        UsageMeteringService meteringService = mock(UsageMeteringService.class);
        UsageRecord record = new UsageRecord("rec-1", "tenant-1", "ws-1", "user-1",
                "render_minutes", 10.0, "min", Instant.now(), null);
        when(meteringService.getUsageByTenant("tenant-1")).thenReturn(List.of(record));

        BillingUsageDataLoader loader = new BillingUsageDataLoader(meteringService);
        CompletionStage<Map<String, List<Map<String, Object>>>> stage = loader.load(Set.of("tenant-1"));
        Map<String, List<Map<String, Object>>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("tenant-1"));
        assertEquals(1, result.get("tenant-1").size());
    }
}
