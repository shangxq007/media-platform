package com.example.platform;

import com.example.platform.ai.app.AiGatewayService;
import com.example.platform.extension.runtime.PluginRuntime;
import com.example.platform.render.app.RenderStepExecutionService;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.usage.*;
import com.example.platform.usage.app.ObservedRuntimeUsageEmissionService;
import com.example.platform.usage.app.ObservedRuntimeUsageOutboxPublisher;
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.ApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.util.AopTestUtils;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

/** EP32: actual platform composition and durable append, without provider execution. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles({"test", "preview"})
@TestPropertySource(properties = {"app.security.enabled=false", "app.identity.api-key-auth-enabled=false",
        "app.outbox.dispatcher-enabled=false"})
class BillingUsageCompositionTest extends PostgresTestContainerSupport {
    @Autowired ApplicationContext context;
    @Autowired ObservedRuntimeUsageEmissionPort emission;
    @Autowired ObservedRuntimeUsageJdbcRepository repository;
    @Autowired JdbcTemplate jdbc;

    @Test void onePlatformUsageAuthorityIsWiredIntoEveryRuntimeConsumer() {
        assertEquals(1, context.getBeansOfType(ObservedRuntimeUsageEmissionPort.class).size());
        assertEquals(1, context.getBeansOfType(ObservedRuntimeUsageJdbcRepository.class).size());
        assertEquals(1, context.getBeansOfType(ObservedRuntimeUsageOutboxPublisher.class).size());
        assertInstanceOf(ObservedRuntimeUsageEmissionService.class, AopTestUtils.getUltimateTargetObject(emission));
        for (Class<?> consumer : new Class<?>[]{AiGatewayService.class, RenderStepExecutionService.class}) {
            Object target = AopTestUtils.getUltimateTargetObject(context.getBean(consumer));
            assertSame(emission, ReflectionTestUtils.getField(target, "emissionPort"));
        }
        Object runtime = AopTestUtils.getUltimateTargetObject(context.getBean(PluginRuntime.class));
        Object runtimeEmitter = ReflectionTestUtils.getField(runtime, "usageEmitter");
        assertNotNull(runtimeEmitter, "Production PluginRuntime must emit through the canonical usage port");
        assertSame(emission, ReflectionTestUtils.getField(runtimeEmitter, "emissionPort"));
    }

    @Test @Transactional void platformPortPersistsObservationAndOutboxIdempotently() {
        Instant now = Instant.parse("2026-09-12T00:00:00Z");
        var observation = ObservedRuntimeUsage.observe("ep32-tenant", "ep32-project",
                new CanonicalActorRef("ep32-user", "USER"), OperationRef.of("ep32-operation", "ep32-attempt"),
                "ep32-execution", new ProviderRef("ep32-provider"), "ep32-capability", UsageDimension.DURATION,
                UsageQuantity.fromBaseUnits(12, UsageUnit.MILLISECONDS), RuntimeOutcome.SUCCEEDED,
                now, now, now, UsageProvenance.REPORTED, "ep32-test", "ep32-source", "ep32-trace", "ep32-idempotency");
        var saved = emission.emit(observation);
        assertEquals(saved, emission.emit(observation));
        assertEquals(saved, repository.findByTenantAndId("ep32-tenant", saved.observedUsageId()).orElseThrow());
        assertTrue(repository.findByTenantAndId("other-tenant", saved.observedUsageId()).isEmpty());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM observed_runtime_usage WHERE tenant_id = ? AND idempotency_key = ?",
                Integer.class, "ep32-tenant", "ep32-idempotency"));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'RUNTIME_USAGE_OBSERVED'",
                Integer.class, saved.observedUsageId()));
    }

    @Test @Transactional void composedPluginUsageConsumerPersistsRequestAndDuration() {
        Object runtime = AopTestUtils.getUltimateTargetObject(context.getBean(PluginRuntime.class));
        var runtimeEmitter = (com.example.platform.extension.runtime.internal.RuntimeUsageEmitter)
                ReflectionTestUtils.getField(runtime, "usageEmitter");
        assertNotNull(runtimeEmitter);
        runtimeEmitter.emitBaseFacts("ep32-runtime-tenant", new CanonicalActorRef("ep32-user", "USER"),
                OperationRef.of("ep32-operation", "ep32-attempt"), new ProviderRef("ep32-provider"),
                "ep32-capability", 12, RuntimeOutcome.SUCCEEDED, Instant.parse("2026-09-12T00:00:00Z"), "ep32-trace");
        var observations = repository.findByTenant("ep32-runtime-tenant");
        assertEquals(java.util.Set.of(UsageDimension.REQUEST, UsageDimension.DURATION),
                observations.stream().map(ObservedRuntimeUsage::dimension).collect(java.util.stream.Collectors.toSet()));
        for (var observation : observations) assertEquals(1, jdbc.queryForObject(
                "SELECT count(*) FROM outbox_events WHERE aggregate_id = ? AND event_type = 'RUNTIME_USAGE_OBSERVED'",
                Integer.class, observation.observedUsageId()));
    }

    @Test void missingDatabaseCannotSelectAnInMemoryUsageFallback() {
        new ApplicationContextRunner().withUserConfiguration(PlatformBeanConfiguration.class).run(application -> {
            assertNotNull(application.getStartupFailure());
            Throwable failure = application.getStartupFailure();
            while (failure.getCause() != null) failure = failure.getCause();
            assertTrue(failure.getMessage().contains("JdbcTemplate"), failure.toString());
        });
    }

    @Autowired com.example.platform.outbox.app.OutboxEventService outbox;
    @Autowired com.example.platform.outbox.app.OutboxEventRouter outboxRouter;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;

    @Test void typedEventRoundTripsThroughProductionDispatcherWithScopedContextAndNoDuplicateDelivery() {
        var type = com.example.platform.usage.api.ObservedUsageEvents.RUNTIME_USAGE_OBSERVED;
        var payload = new com.example.platform.usage.api.ObservedUsageEvents.RuntimeUsageObserved(
                "ep08-observation", "ep08-tenant", "operation", "attempt", UsageDimension.DURATION);
        String id = outbox.append(type.append("ep08-tenant", payload, "ep08-roundtrip"));
        java.util.List<Object> delivered = new java.util.ArrayList<>();
        org.springframework.context.ApplicationEventPublisher listener = event -> {
            assertEquals("ep08-tenant", com.example.platform.shared.web.TenantContext.get());
            delivered.add(event);
        };
        var dispatcher = new com.example.platform.outbox.app.OutboxEventDispatcher(outbox, listener, outboxRouter, 3, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        assertTrue(dispatcher.processOnce(id));
        assertFalse(dispatcher.processOnce(id));
        assertEquals(java.util.List.of(payload), delivered);
        assertNull(com.example.platform.shared.web.TenantContext.get());
        assertEquals(id, outbox.append(type.append("ep08-tenant", payload, "ep08-roundtrip")));
        assertThrows(IllegalArgumentException.class, () -> outbox.append(type.append("other", payload, null)));
        try {
            com.example.platform.shared.web.TenantContext.set("other");
            assertThrows(IllegalArgumentException.class, () -> outbox.append(type.append("ep08-tenant", payload, "wrong-tenant")));
        } finally { com.example.platform.shared.web.TenantContext.clear(); }
    }

    @Test void unsupportedOrMalformedPersistedContractsAndInvalidRetryPolicyAreRejectedWithoutFallback() {
        var type = com.example.platform.usage.api.ObservedUsageEvents.RUNTIME_USAGE_OBSERVED;
        var payload = new com.example.platform.usage.api.ObservedUsageEvents.RuntimeUsageObserved("quarantine-observation", "ep08-tenant", "operation", null, UsageDimension.REQUEST);
        org.springframework.context.ApplicationEventPublisher listener = event -> fail("Invalid event must not be published");
        var dispatcher = new com.example.platform.outbox.app.OutboxEventDispatcher(outbox, listener, outboxRouter, 3, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
        String unknown = outbox.append(type.append("ep08-tenant", payload, null));
        jdbc.update("update outbox_events set event_version=99 where id=?", unknown);
        assertFalse(dispatcher.processOnce(unknown));
        assertEquals("UNSUPPORTED_EVENT_VERSION", jdbc.queryForObject("select last_error_code from outbox_events where id=?", String.class, unknown));
        String malformed = outbox.append(type.append("ep08-tenant", payload, null));
        jdbc.update("update outbox_events set payload='{}' where id=?", malformed);
        assertFalse(dispatcher.processOnce(malformed));
        assertEquals("DEAD_LETTER", jdbc.queryForObject("select status from outbox_events where id=?", String.class, malformed));
        String badPolicy = outbox.append(type.append("ep08-tenant", payload, null));
        assertThrows(org.springframework.dao.DataIntegrityViolationException.class,
                () -> jdbc.update("update outbox_events set max_retries=null where id=?", badPolicy));
        jdbc.update("update outbox_events set max_retries=0 where id=?", badPolicy);
        outbox.markFailedWithDetails(badPolicy, "TEST", "failure");
        assertEquals("INVALID_RETRY_POLICY", jdbc.queryForObject("select last_error_code from outbox_events where id=?", String.class, badPolicy));
        assertEquals("DEAD_LETTER", jdbc.queryForObject("select status from outbox_events where id=?", String.class, badPolicy));
    }

    @Test void sameKeyConcurrentTransactionsPersistOneScopedEvent() throws Exception {
        var type = com.example.platform.usage.api.ObservedUsageEvents.RUNTIME_USAGE_OBSERVED;
        var payload = new com.example.platform.usage.api.ObservedUsageEvents.RuntimeUsageObserved("concurrent-observation", "ep08-tenant", "operation", null, UsageDimension.REQUEST);
        var first = java.util.concurrent.CompletableFuture.supplyAsync(() -> outbox.append(type.append("ep08-tenant", payload, "ep08-concurrent")));
        var second = java.util.concurrent.CompletableFuture.supplyAsync(() -> outbox.append(type.append("ep08-tenant", payload, "ep08-concurrent")));
        assertEquals(first.get(15, java.util.concurrent.TimeUnit.SECONDS), second.get(15, java.util.concurrent.TimeUnit.SECONDS));
        assertEquals(1, jdbc.queryForObject("select count(*) from outbox_events where idempotency_key='ep08-concurrent'", Integer.class));
        var foreign = new com.example.platform.usage.api.ObservedUsageEvents.RuntimeUsageObserved("concurrent-observation", "foreign-tenant", "operation", null, UsageDimension.REQUEST);
        assertThrows(IllegalArgumentException.class, () -> outbox.append(type.append("foreign-tenant", foreign, "ep08-concurrent")));
    }

    @Test void rollbackOfTheRealDomainTransactionLeavesNoObservationOrOrphanOutboxEvent() {
        var now = Instant.parse("2026-09-12T00:00:00Z");
        var observation = ObservedRuntimeUsage.observe("ep08-rollback", "project", new CanonicalActorRef("actor", "USER"),
                OperationRef.of("operation", "attempt"), "execution", new ProviderRef("provider"), "capability", UsageDimension.REQUEST,
                UsageQuantity.fromBaseUnits(1, UsageUnit.COUNT), RuntimeOutcome.SUCCEEDED, now, now, now, UsageProvenance.REPORTED,
                "test", "source", "trace", "ep08-rollback");
        var transaction = new org.springframework.transaction.support.TransactionTemplate(transactions);
        assertThrows(IllegalStateException.class, () -> transaction.executeWithoutResult(status -> {
            emission.emit(observation);
            throw new IllegalStateException("domain transaction rejected");
        }));
        assertEquals(0, jdbc.queryForObject("select count(*) from observed_runtime_usage where tenant_id='ep08-rollback'", Integer.class));
        assertEquals(0, jdbc.queryForObject("select count(*) from outbox_events where idempotency_key='observed-usage:ep08-rollback:ep08-rollback'", Integer.class));
    }

    @Autowired com.example.platform.billing.usage.CostObservationEmissionService costEmission;
    @Test void realSchemaCostReplayKeepsThePersistedIdentityAndTypedVersion() {
        var cost = com.example.platform.billing.usage.ProviderCostObservation.record("ep08-cost", "project",
                new CanonicalActorRef("actor", "USER"), OperationRef.of("operation", "attempt"), "execution", new ProviderRef("provider"), "capability",
                new java.math.BigDecimal("1999"), "USD", com.example.platform.billing.usage.CostType.REPORTED, "test-report", Instant.now(), null, "ep08-cost-key");
        var saved = costEmission.persistCostWithOutbox(cost);
        assertEquals(saved.observationId(), costEmission.persistCostWithOutbox(cost).observationId());
        var row = jdbc.queryForMap("select * from outbox_events where event_type='COST_OBSERVED' and aggregate_id=?", saved.observationId());
        var payload = (com.example.platform.billing.usage.BillingOutboxEvents.CostObserved) outboxRouter.decode("COST_OBSERVED", 1, "PROVIDER_COST", saved.observationId(), (String) row.get("payload")).payload();
        assertEquals(saved.observationId(), payload.costObservationId());
        assertEquals(saved.amountMinor(), payload.amountMinor());
    }
}
