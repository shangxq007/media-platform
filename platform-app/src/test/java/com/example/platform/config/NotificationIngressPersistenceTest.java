package com.example.platform.config;

import com.example.platform.notification.api.ingress.NotificationEventPublisher;
import com.example.platform.notification.api.ingress.NotificationInboundEvent;
import com.example.platform.notification.api.event.NotificationOutboxEvents;
import com.example.platform.notification.app.NotificationEventHandler;
import com.example.platform.notification.app.NotificationRenderingService;
import com.example.platform.notification.domain.*;
import com.example.platform.outbox.app.*;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real V1 migration, published ingress, production codec/dispatcher and transactional listener.
 * Only the provider effect and template rendering are isolated test doubles. No external delivery.
 */
@SpringJUnitConfig(NotificationIngressPersistenceTest.Config.class)
@org.springframework.test.context.ActiveProfiles("ep20-ingress-isolated")
class NotificationIngressPersistenceTest extends PostgresTestContainerSupport {
    @org.springframework.boot.test.context.TestConfiguration @EnableTransactionManagement
    @Profile("ep20-ingress-isolated")
    @Import({OutboxBackedNotificationEventPublisher.class, NotificationOutboxEvents.class})
    static class Config {
        @Bean(destroyMethod="close") DataSource dataSource() {
            DataSource ds = createDataSource();
            org.flywaydb.core.Flyway.configure().dataSource(ds)
                    .locations("classpath:db/migration").load().migrate();
            return ds;
        }
        @Bean DSLContext dsl(DataSource ds) { return DSL.using(new TransactionAwareDataSourceProxy(ds), SQLDialect.POSTGRES); }
        @Bean PlatformTransactionManager transactionManager(DataSource ds) { return new DataSourceTransactionManager(ds); }
        @Bean OutboxEventRouter router(NotificationOutboxEvents catalog) { return new OutboxEventRouter(List.of(catalog)); }
        @Bean OutboxEventService outbox(DSLContext dsl, OutboxEventRouter router) {
            return new OutboxEventService(dsl, 3, new PostgresNotificationService(null), router);
        }
        @Bean OutboxEventDispatcher dispatcher(OutboxEventService service, ApplicationEventPublisher publisher, OutboxEventRouter router) {
            return new OutboxEventDispatcher(service, publisher, router, 3, new SimpleMeterRegistry());
        }
        @Bean NotificationProvider provider() { return mock(NotificationProvider.class); }
        @Bean NotificationEventHandler handler(DSLContext dsl, NotificationProvider provider) {
            var renderer = mock(NotificationRenderingService.class);
            when(renderer.render(any(), anyString(), anyString(), anyMap()))
                    .thenReturn(new NotificationTemplatePayload("Subject", "Body"));
            return new NotificationEventHandler(dsl, List.of(provider), renderer, null);
        }
    }
    @Autowired NotificationEventPublisher ingress;
    @Autowired OutboxEventService outbox;
    @Autowired OutboxEventDispatcher dispatcher;
    @Autowired NotificationProvider provider;
    @Autowired DSLContext dsl;
    @Autowired PlatformTransactionManager tx;
    @Autowired ApplicationContext context;
    private final NotificationInboundEvent event = new NotificationInboundEvent("render.job.created", "job-ep20", Map.of("renderJobId", "job-ep20"));

    @BeforeEach void prepare() {
        dsl.execute("TRUNCATE outbox_events, notification_event, notification_delivery");
        reset(provider);
        when(provider.channel()).thenReturn("TEST");
        when(provider.providerCode()).thenReturn("TEST");
        when(provider.send(any())).thenReturn(new DeliveryResult("SENT", "{}"));
        TenantContext.set("ep20-tenant");
    }
    @AfterEach void clearTenant() { TenantContext.clear(); }
    private int count(String table) { return dsl.fetchCount(DSL.table(table)); }
    private String id() { return dsl.select(DSL.field("id", String.class)).from("outbox_events").fetchSingleInto(String.class); }

    @Test void typedIngressReconstructsAndDeliversOnceForRepeatedProcessedKey() {
        assertEquals(1, context.getBeansOfType(NotificationEventPublisher.class).size());
        ingress.publish(event, "ep20-notification");
        String id = id();
        ingress.publish(event, "ep20-notification");
        assertEquals(1, count("outbox_events"));
        assertTrue(dispatcher.processOnce(id));
        assertEquals("PROCESSED", outbox.readEvent(id).get("status"));
        assertEquals(1, count("notification_event"));
        assertEquals(1, count("notification_delivery"));
        assertEquals(event.eventType(), dsl.fetchOne("select event_type from notification_event").get(0));
        ingress.publish(event, "ep20-notification");
        assertFalse(dispatcher.processOnce(id));
        verify(provider, times(1)).send(any());
    }
    @Test void missingTenantAndKeyScopeCollisionRejectWithoutAnotherRow() {
        TenantContext.clear();
        assertThrows(RuntimeException.class, () -> ingress.publish(event, "key"));
        assertEquals(0, count("outbox_events"));
        TenantContext.set("ep20-tenant"); ingress.publish(event, "key");
        TenantContext.set("other-tenant");
        assertThrows(IllegalArgumentException.class, () -> ingress.publish(event, "key"));
        TenantContext.set("ep20-tenant");
        assertThrows(IllegalArgumentException.class, () -> ingress.publish(new NotificationInboundEvent("test", "different-subject", Map.of()), "key"));
        assertEquals(1, count("outbox_events"));
    }
    @Test void callerRollbackLeavesNoOrphanAppend() {
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(tx).execute(status -> {
            ingress.publish(event, "rolled-back"); throw new IllegalStateException("rollback domain transaction");
        }));
        assertEquals(0, count("outbox_events"));
    }
    @Test void providerExceptionRollsBackNotificationRowsAndOutboxRecordsFailure() {
        when(provider.send(any())).thenThrow(new IllegalStateException("isolated provider unavailable"));
        ingress.publish(event, "failed");
        String id=id();
        assertFalse(dispatcher.processOnce(id));
        assertEquals("FAILED", outbox.readEvent(id).get("status"));
        assertEquals(0, count("notification_event"));
        assertEquals(0, count("notification_delivery"));
    }
    @Test void noKeyKeepsIndependentNotificationsDistinct() {
        ingress.publish(event, null); ingress.publish(event, null);
        assertEquals(2, count("outbox_events"));
    }
}
