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
}
