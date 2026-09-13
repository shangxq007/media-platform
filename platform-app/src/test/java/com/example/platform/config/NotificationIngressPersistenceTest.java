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
@org.springframework.test.context.TestPropertySource(properties="app.outbox.claim-lease-ms=1500")
@org.springframework.test.context.ActiveProfiles("ep20-ingress-isolated")
class NotificationIngressPersistenceTest extends PostgresTestContainerSupport {
    static OutboxEventService rawOutbox;
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
            return rawOutbox=spy(new OutboxEventService(dsl, 3, new PostgresNotificationService(null), router));
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
        reset(provider); reset(rawOutbox);
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
        for(String id:dsl.select(DSL.field("id",String.class)).from("outbox_events").fetchInto(String.class))assertTrue(dispatcher.processOnce(id));
        assertEquals(2,count("notification_event"));assertEquals(2,count("notification_delivery"));verify(provider,times(2)).send(any());
    }
    @Test void postClaimReadFailureDoesNotStrandProcessing() {
        ingress.publish(event,"read-failure");String id=id();
        doThrow(new IllegalStateException("read after claim failed")).when(rawOutbox).readClaimedEvent(any(OutboxClaim.class));
        Throwable escaped=null;try{dispatcher.processOnce(id);}catch(RuntimeException failure){escaped=failure;}
        assertEquals("FAILED",dsl.fetchValue("select status from outbox_events where id=?",id),"durable claim stranded after read failure: "+escaped);
        assertEquals(0,count("notification_event"));assertEquals(0,count("notification_delivery"));
        reset(rawOutbox);due(id);assertTrue(dispatcher.processOnce(id));assertEquals(1,count("notification_event"));assertEquals(1,count("notification_delivery"));
    }
    @Test void consumerCommitThenAcknowledgmentFailureReplaysOneLocalNotification() {
        ingress.publish(event,"ack-failure");String id=id();
        doAnswer(call->{
            // Acknowledgment has its own transaction. A separate connection proves the listener already committed.
            try(var connection=context.getBean(DataSource.class).getConnection();var statement=connection.createStatement()) {
                try(var rows=statement.executeQuery("select count(*) from notification_event")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
                try(var rows=statement.executeQuery("select count(*) from notification_delivery")){assertTrue(rows.next());assertEquals(1,rows.getInt(1));}
            }
            throw new IllegalStateException("acknowledgment failed after listener commit");})
            .doCallRealMethod().when(rawOutbox).markProcessed(any(OutboxClaim.class));
        assertFalse(dispatcher.processOnce(id));assertEquals("FAILED",outbox.readEvent(id).get("status"));
        dsl.execute("update outbox_events set next_attempt_at=? where id=?",java.time.LocalDateTime.now().minusSeconds(10),id);
        assertTrue(dispatcher.processOnce(id));assertEquals("PROCESSED",outbox.readEvent(id).get("status"));
        assertEquals(1,count("notification_event"));assertEquals(1,count("notification_delivery"));verify(provider,times(1)).send(any());
    }
    private void due(String id){dsl.execute("update outbox_events set next_attempt_at=? where id=?",java.time.LocalDateTime.now().minusSeconds(10),id);}
    private void expire(String id){dsl.execute("update outbox_events set locked_at=? where id=?",java.time.Instant.now().minusSeconds(3600),id);}
    @Test void expiredClaimsRecoverAndEveryOldResultIsFenced() {
        ingress.publish(event,"expired");String id=id();var first=outbox.claimForProcessing(id,"same-processor").orElseThrow();
        assertEquals(0,outbox.recoverExpiredClaims(100));expire(id);assertEquals(1,outbox.recoverExpiredClaims(100));
        assertEquals("FAILED",outbox.readEvent(id).get("status"));due(id);
        var second=outbox.claimForProcessing(id,"same-processor").orElseThrow();assertNotEquals(first.token(),second.token());
        assertNull(outbox.readClaimedEvent(first));assertFalse(outbox.renewClaim(first));assertFalse(outbox.markProcessed(first));
        assertFalse(outbox.markFailedWithDetails(first,"OLD","obsolete"));assertFalse(outbox.quarantine(first,"OLD","obsolete"));
        assertEquals("PROCESSING",outbox.readEvent(id).get("status"));assertEquals(second.token(),dsl.fetchValue("select locked_by from outbox_events where id=?",id));
        assertEquals(1,dsl.fetchValue("select retry_count from outbox_events where id=?",id));assertTrue(outbox.markProcessed(second));
    }
    @Test void longRunningListenerRenewsItsClaimBeyondTheInitialLease() throws Exception {
        ingress.publish(event,"renewal");String id=id();
        var entered=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);var renewed=new java.util.concurrent.CountDownLatch(4);
        when(provider.send(any())).thenAnswer(call->{entered.countDown();if(!release.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("listener release timeout");return new DeliveryResult("SENT","{}");});
        doAnswer(call->{boolean ok=(boolean)call.callRealMethod();if(ok)org.springframework.transaction.support.TransactionSynchronizationManager.registerSynchronization(
                new org.springframework.transaction.support.TransactionSynchronization(){@Override public void afterCommit(){renewed.countDown();}});return ok;}).when(rawOutbox).renewClaim(any(OutboxClaim.class));
        try(var pool=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var result=pool.submit(()->{TenantContext.set("ep20-tenant");try{return dispatcher.processOnce(id);}finally{TenantContext.clear();}});
            try {
                assertTrue(entered.await(5,java.util.concurrent.TimeUnit.SECONDS));
                var original=(java.time.OffsetDateTime)dsl.fetchValue("select locked_at from outbox_events where id=?",id);
                assertTrue(renewed.await(8,java.util.concurrent.TimeUnit.SECONDS));
                assertTrue(java.time.Instant.now().isAfter(original.toInstant().plusMillis(outbox.claimLeaseMillis())));
                assertEquals(0,outbox.recoverExpiredClaims(100));assertEquals("PROCESSING",outbox.readEvent(id).get("status"));
            } finally{release.countDown();}
            assertTrue(result.get(5,java.util.concurrent.TimeUnit.SECONDS));
        }
        assertEquals(1,count("notification_event"));assertEquals(1,count("notification_delivery"));
    }
    @Test void concurrentDuplicateListenerTransactionsUseTheSameDurableIdentity() throws Exception {
        ingress.publish(event,"concurrent");String id=id();var ready=new java.util.concurrent.CountDownLatch(2);var start=new java.util.concurrent.CountDownLatch(1);
        var handler=context.getBean(NotificationEventHandler.class);
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var futures=new java.util.ArrayList<java.util.concurrent.Future<?>>();
            for(int i=0;i<2;i++)futures.add(pool.submit(()->{TenantContext.set("ep20-tenant");try{
                ready.countDown();if(!start.await(5,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("start timeout");
                com.example.platform.outbox.api.event.OutboxDeliveryContext.run(new com.example.platform.outbox.api.event.OutboxDeliveryContext.Delivery(id,"ep20-tenant"),()->handler.handle(event));
            }catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}finally{TenantContext.clear();}}));
            assertTrue(ready.await(5,java.util.concurrent.TimeUnit.SECONDS));start.countDown();for(var future:futures)future.get(10,java.util.concurrent.TimeUnit.SECONDS);
        } finally{start.countDown();}
        assertTrue(dispatcher.processOnce(id));assertEquals(1,count("notification_event"));assertEquals(1,count("notification_delivery"));verify(provider,times(1)).send(any());
    }
    @Test void listenerRollbackCanRetryAndUncertainProviderResultIsNotRelabeled() {
        ingress.publish(event,"rollback-retry");String id=id();when(provider.send(any())).thenThrow(new IllegalStateException("provider unavailable"));
        assertFalse(dispatcher.processOnce(id));assertEquals(0,count("notification_event"));assertEquals(0,count("notification_delivery"));
        when(provider.send(any())).thenReturn(new DeliveryResult("UNCERTAIN","unconfirmed"));due(id);assertTrue(dispatcher.processOnce(id));
        assertEquals(1,count("notification_event"));assertEquals(1,count("notification_delivery"));assertEquals("UNCERTAIN",dsl.fetchValue("select status from notification_delivery"));
        assertFalse(dispatcher.processOnce(id));verify(provider,times(2)).send(any());
    }
    @Test void durableIdentityRequiresMatchingTenantAndIsRetiredAfterDispatch() {
        ingress.publish(event,"scope");String id=id();TenantContext.set("other");
        assertThrows(RuntimeException.class,()->com.example.platform.outbox.api.event.OutboxDeliveryContext.run(
                new com.example.platform.outbox.api.event.OutboxDeliveryContext.Delivery(id,"ep20-tenant"),()->context.getBean(NotificationEventHandler.class).handle(event)));
        assertEquals(0,count("notification_event"));TenantContext.set("ep20-tenant");assertTrue(dispatcher.processOnce(id));
        assertThrows(IllegalStateException.class,com.example.platform.outbox.api.event.OutboxDeliveryContext::require);
    }
    @Test void terminatedClaimantIsRecoveredByANewDispatcher() throws Exception {
        ingress.publish(event,"terminated");String id=id();
        var loader=(java.net.URLClassLoader)OutboxClaimCrashProcess.class.getClassLoader();
        String classpath=java.util.Arrays.stream(loader.getURLs()).map(url->{try{return java.nio.file.Path.of(url.toURI()).toString();}catch(Exception e){throw new IllegalStateException(e);}})
                .collect(java.util.stream.Collectors.joining(java.io.File.pathSeparator));
        var builder=new ProcessBuilder(java.nio.file.Path.of(System.getProperty("java.home"),"bin","java").toString(),"-cp",classpath,OutboxClaimCrashProcess.class.getName(),id);
        builder.environment().put("OUTBOX_TEST_JDBC",jdbcUrl());builder.environment().put("OUTBOX_TEST_USER",username());builder.environment().put("OUTBOX_TEST_PASSWORD",password());
        builder.environment().put("OUTBOX_TEST_LEASE",Long.toString(outbox.claimLeaseMillis()));builder.redirectErrorStream(true);
        Process process=builder.start();String token;
        try(var reader=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            try {
                var line=reader.submit(()->{try(var input=process.inputReader()){String value;while((value=input.readLine())!=null)if(value.startsWith("CLAIMED "))return value.substring(8);throw new IllegalStateException("claimant exited before claim");}});
                token=line.get(15,java.util.concurrent.TimeUnit.SECONDS);
                assertEquals("PROCESSING",outbox.readEvent(id).get("status"));assertEquals(token,dsl.fetchValue("select locked_by from outbox_events where id=?",id));
            } finally {process.destroyForcibly();assertTrue(process.waitFor(5,java.util.concurrent.TimeUnit.SECONDS));}
        }
        assertFalse(process.isAlive());assertEquals("PROCESSING",outbox.readEvent(id).get("status"));
        expire(id);
        try(var restarted=new OutboxEventDispatcher(outbox,context,context.getBean(OutboxEventRouter.class),3,new SimpleMeterRegistry())) {
            restarted.retryDueEvents();assertEquals("FAILED",outbox.readEvent(id).get("status"));due(id);assertTrue(restarted.processOnce(id));
        }
        assertFalse(outbox.markProcessed(new OutboxClaim(id,token)));
        assertEquals("PROCESSED",outbox.readEvent(id).get("status"));assertEquals(1,count("notification_event"));assertEquals(1,count("notification_delivery"));
    }
}
