package com.example.platform.federation.graphql.dataloader;

import com.example.platform.billing.app.UsageMeteringService;
import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.billing.usage.MeteringTransformationKind;
import com.example.platform.usage.api.CanonicalActorRef;
import com.example.platform.usage.api.UsageDimension;
import com.example.platform.usage.api.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.usage.api.UsageUnit;
import com.example.platform.shared.web.TenantContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class BillingUsageDataLoaderTest {

    private static final Instant NOW = Instant.now();

    /** Canonical usage fact with an explicit recordId (direct record ctor; factory generates ids). */
    private static UsageRecord canonicalUsage(String recordId, String tenantId, String meterKey,
                                              long quantity, String unit) {
        UsageUnit canonicalUnit = switch (unit) {
            case "min", "seconds", "s" -> UsageUnit.SECONDS;
            case "bytes", "byte" -> UsageUnit.BYTE;
            default -> UsageUnit.COUNT;
        };
        UsageDimension dimension = switch (canonicalUnit) {
            case SECONDS, MILLISECONDS -> UsageDimension.DURATION;
            case BYTE -> UsageDimension.BYTE_STORED;
            default -> UsageDimension.REQUEST;
        };
        UsageQuantity typedQuantity = new UsageQuantity(quantity, canonicalUnit);
        return new BillableUsage(
                recordId, tenantId, new CanonicalActorRef("user-1", "USER"),
                "observed-" + recordId, dimension, typedQuantity, meterKey, dimension,
                typedQuantity, "meter-rule", "v1", MeteringTransformationKind.IDENTITY,
                "identity", NOW, NOW, "idem-" + recordId, "trace-1",
                "observed-" + recordId);
    }

    @BeforeEach
    void setUp() {
        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    void loadsUsageDataWithExplicitTenantId() throws Exception {
        UsageMeteringService meteringService = mock(UsageMeteringService.class);
        UsageRecord record = canonicalUsage("rec-1", "tenant-1", "render_minutes", 10, "min");
        when(meteringService.getUsageByTenant("tenant-1")).thenReturn(List.of(record));

        BillingUsageDataLoader loader = new BillingUsageDataLoader(new com.example.platform.billing.app.BillingReadProjection(meteringService), com.example.platform.federation.graphql.OwnerQueryFixtures.scope());
        CompletionStage<Map<String, List<Map<String, Object>>>> stage = loader.load(Set.of("tenant-1"));
        Map<String, List<Map<String, Object>>> result = stage.toCompletableFuture().get();

        assertNotNull(result);
        assertTrue(result.containsKey("tenant-1"));
        assertEquals(1, result.get("tenant-1").size());
        assertEquals("rec-1", result.get("tenant-1").get(0).get("id"));
        assertEquals("DURATION", result.get("tenant-1").get(0).get("meterKey"));
        assertEquals(10L, result.get("tenant-1").get(0).get("quantity"));
    }

    private BillingUsageDataLoader loader(UsageMeteringService service,String tenant){
        var actor=com.example.platform.shared.authorization.CanonicalActor.user("u",tenant,Set.of(),"test");
        return new BillingUsageDataLoader(new com.example.platform.billing.app.BillingReadProjection(service),
            new com.example.platform.federation.graphql.context.GraphQLReadScope(()->java.util.Optional.of(actor),mock(com.example.platform.identity.api.workspace.WorkspaceQueries.class)));
    }
    @Test void wrongTenantAndAbsentDispatchScopeFailWithoutReading(){
        var service=mock(UsageMeteringService.class);var loader=loader(service,"a");
        TenantContext.set("a");
        assertThrows(java.util.concurrent.CompletionException.class,()->loader.load(Set.of("b")).toCompletableFuture().join());
        TenantContext.clear();
        assertThrows(java.util.concurrent.CompletionException.class,()->loader.load(Set.of("a")).toCompletableFuture().join());
        verifyNoInteractions(service);
    }
    @Test void failureIsNotEmptySuccessAndNextAttemptRecovers(){
        var service=mock(UsageMeteringService.class);var loader=loader(service,"a");TenantContext.set("a");
        when(service.getUsageByTenant("a")).thenThrow(new IllegalStateException("db unavailable")).thenReturn(List.of(canonicalUsage("r","a","m",0,"calls")));
        assertThrows(java.util.concurrent.CompletionException.class,()->loader.load(Set.of("a")).toCompletableFuture().join());
        var result=loader.load(Set.of("a")).toCompletableFuture().join();assertEquals("r",result.get("a").getFirst().get("id"));assertEquals(0L,result.get("a").getFirst().get("quantity"));assertEquals("a",TenantContext.get());
    }
    @Test void concurrentRequestsAndReusedThreadKeepScopeAndCleanup() throws Exception {
        var service=mock(UsageMeteringService.class);
        when(service.getUsageByTenant(anyString())).thenAnswer(call->{String t=call.getArgument(0);assertEquals(t,TenantContext.get());return List.of(canonicalUsage("same",t,"m",t.equals("a")?1:2,"calls"));});
        var pool=Executors.newFixedThreadPool(2);var barrier=new java.util.concurrent.CyclicBarrier(2);
        try {
            java.util.List<java.util.concurrent.Future<Long>> results=new java.util.ArrayList<>();
            for(String tenant:List.of("a","b"))results.add(pool.submit(()->{TenantContext.set(tenant);try{barrier.await(5,TimeUnit.SECONDS);return (Long)loader(service,tenant).load(Set.of(tenant)).toCompletableFuture().join().get(tenant).getFirst().get("quantity");}finally{TenantContext.clear();}}));
            assertEquals(1L,results.get(0).get());assertEquals(2L,results.get(1).get());
            var single=Executors.newSingleThreadExecutor();try{single.submit(()->{TenantContext.set("a");try{assertThrows(java.util.concurrent.CompletionException.class,()->loader(service,"b").load(Set.of("b")).toCompletableFuture().join());}finally{TenantContext.clear();}}).get();assertNull(single.submit(TenantContext::get).get());}finally{single.shutdownNow();}
        } finally {pool.shutdownNow();}
    }
}
