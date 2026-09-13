package com.example.platform.storage;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.*;
import com.example.platform.storage.api.StorageObjectIssuance.*;
import com.example.platform.storage.app.*;
import com.example.platform.storage.app.identity.*;
import com.example.platform.storage.infrastructure.*;
import com.example.platform.storage.infrastructure.identity.*;
import com.example.platform.storage.domain.*;
import com.example.platform.storage.domain.identity.*;
import com.example.platform.artifact.app.*;
import com.example.platform.artifact.domain.*;
import com.example.platform.artifact.infrastructure.*;
import com.example.platform.render.infrastructure.RenderArtifactStorageService;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.app.*;
import com.example.platform.render.app.event.*;
import com.example.platform.render.api.event.*;
import com.example.platform.render.infrastructure.providerruntime.engine.*;
import com.example.platform.outbox.app.*;
import com.example.platform.audit.app.*;
import com.example.platform.notification.app.*;
import com.example.platform.delivery.app.*;
import com.example.platform.delivery.infrastructure.DeliveryAdapterRegistry;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.entitlement.api.commercial.QuotaConsumptionPort;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.nio.file.*;
import java.time.Clock;
import java.util.*;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real LocalFs bytes + production Storage recovery + Artifact commit on current Flyway/PostgreSQL.
 * Input is a deterministic test file, not a claim of provider rendering. */
class RenderOutputAcceptanceTest extends PostgresTestContainerSupport {
    static final String SCHEMA=isolatedSchemaName();
    static AnnotationConfigApplicationContext context;
    static Path root;
    static JdbcTemplate jdbc;
    static LocalFsStorageProvider backend;
    static StorageOutputPort output;
    static StoragePlacementQuery placements;
    static RenderArtifactStorageService render;
    static TransactionTemplate tx;
    static DataSource admin;
    static OutboxEventService rawOutbox;
    static RenderJobLifecycleService lifecycle;
    static OutboxEventDispatcher dispatcher;
    static com.example.platform.notification.domain.NotificationProvider notification;
    static final java.util.concurrent.atomic.AtomicInteger transfers=new java.util.concurrent.atomic.AtomicInteger();
    static byte[] delivered;
    static String deliveredMime,deliveredFileName;
    static com.example.platform.render.infrastructure.product.ProductRepository previewProducts;
    static boolean previewDenied;
    static String previewActorTenant;
    static volatile RetryRace retryRace;
    static class RetryRace {
        final String mode;
        final java.util.concurrent.CountDownLatch observed=new java.util.concurrent.CountDownLatch(2), releaseSecond=new java.util.concurrent.CountDownLatch(1),
                transportStarted=new java.util.concurrent.CountDownLatch(1), finishTransport=new java.util.concurrent.CountDownLatch(1), secondOutcome=new java.util.concurrent.CountDownLatch(1);
        final Set<String> seen=java.util.concurrent.ConcurrentHashMap.newKeySet();
        RetryRace(String mode){this.mode=mode;}
        static void await(java.util.concurrent.CountDownLatch latch){try{if(!latch.await(10,java.util.concurrent.TimeUnit.SECONDS))throw new IllegalStateException("race latch timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new IllegalStateException(e);}}
    }

    @EnableTransactionManagement(proxyTargetClass=true) static class Transactions {}
    @BeforeAll static void setup() throws Exception {
        root=Files.createTempDirectory("ep04-output-");
        admin=createDataSource();
        new JdbcTemplate(admin).execute("create schema "+SCHEMA);
        Flyway.configure().dataSource(jdbcUrl(),username(),password()).locations("classpath:db/migration")
            .schemas(SCHEMA).defaultSchema(SCHEMA).load().migrate();
        var ds=new DriverManagerDataSource(jdbcUrl()+(jdbcUrl().contains("?")?"&":"?")+"currentSchema="+SCHEMA,username(),password());
        var manager=new DataSourceTransactionManager(ds); tx=new TransactionTemplate(manager);
        jdbc=new JdbcTemplate(ds);
        context=new AnnotationConfigApplicationContext(); context.register(Transactions.class);
        context.registerBean("transactionManager",DataSourceTransactionManager.class,()->manager);
        context.registerBean(JdbcTemplate.class,()->jdbc);
        context.registerBean(DSLContext.class,()->{
            var dsl=DSL.using(new TransactionAwareDataSourceProxy(ds),SQLDialect.POSTGRES,new org.jooq.conf.Settings().withRenderSchema(false));
            dsl.configuration().set(new org.jooq.impl.DefaultExecuteListenerProvider(new org.jooq.impl.DefaultExecuteListener(){
                @Override public void fetchEnd(org.jooq.ExecuteContext ctx){
                    var race=retryRace;String thread=Thread.currentThread().getName();String sql=ctx.sql();
                    if(race!=null && thread.startsWith("delivery-retry-") && sql!=null && sql.contains("delivery_job") && sql.contains("render_job_id") && race.seen.add(thread)) {
                        race.observed.countDown();RetryRace.await(race.observed);
                        if(thread.endsWith("-B"))RetryRace.await(race.releaseSecond);
                    }
                }
            }));return dsl;
        });
        context.registerBean(Clock.class,Clock::systemUTC);
        context.registerBean(CanonicalStorageObjectIdAllocator.class);
        context.registerBean(JdbcStorageWriteIntentRepository.class);
        context.registerBean(JdbcStorageObjectAuthorityRepository.class);
        context.registerBean(CanonicalStorageWriteIntentRecoveryService.class);
        context.registerBean(StorageReferenceRepository.class);
        backend=spy(new LocalFsStorageProvider(root.toString()));
        context.registerBean(BlobStorage.class,()->backend);
        context.registerBean(StorageOutputService.class,()->new StorageOutputService(backend,
            context.getBean(StorageWriteIntentRecovery.class),context.getBean(StorageObjectAuthorityRepository.class),
            context.getBean(StorageReferenceStore.class),root.toString(),new OutputStorageProperties(),new com.example.platform.storage.infrastructure.StorageS3Properties()));
        context.registerBean(ArtifactRepository.class); context.registerBean(ArtifactRelationRepository.class);
        context.registerBean(JooqArtifactCommitService.class); context.registerBean(JooqArtifactQueryService.class);
        context.registerBean(JooqArtifactApplicationQuery.class); context.registerBean(ArtifactOutputCommitService.class);
        context.registerBean(RenderArtifactStorageService.class);
        context.registerBean(ArtifactOutputReadService.class);
        context.registerBean(ArtifactOutputReferenceIndexService.class);
        context.registerBean(OutboxEventRouter.class,()->new OutboxEventRouter(List.of(new RenderOutboxEvents(),new ProviderBindingOutboxEvents(),new com.example.platform.artifact.api.event.ArtifactOutboxEvents(),new com.example.platform.delivery.api.event.DeliveryOutboxEvents(),new com.example.platform.audit.api.event.AuditOutboxEvents())));
        context.registerBean(PostgresNotificationService.class);
        context.registerBean(OutboxEventService.class,()->{
            rawOutbox=spy(new OutboxEventService(context.getBean(DSLContext.class),3,context.getBean(PostgresNotificationService.class),context.getBean(OutboxEventRouter.class)));return rawOutbox;});
        context.registerBean(RenderLifecyclePublisher.class);context.registerBean(ProviderBindingPublisher.class);
        context.registerBean(RenderJobRepository.class);context.registerBean(RenderJobStatusHistoryRepository.class);
        context.registerBean(QuotaConsumptionPort.class,()->mock(QuotaConsumptionPort.class));
        context.registerBean(RenderJobLifecycleService.class);context.registerBean(RenderJobFailureService.class);
        context.registerBean(com.example.platform.render.infrastructure.farm.RenderJobLeaseRepository.class);
        context.registerBean(com.example.platform.render.infrastructure.farm.RenderWorkerRegistryService.class,()->mock(com.example.platform.render.infrastructure.farm.RenderWorkerRegistryService.class));
        context.registerBean(com.example.platform.render.infrastructure.farm.RenderJobLeaseService.class);
        context.registerBean(RenderJobService.class,()->new RenderJobService(context.getBean(RenderJobRepository.class),mock(com.example.platform.render.policy.RenderPolicyEngine.class),context.getBean(RenderLifecyclePublisher.class),context.getBean(RenderJobStatusHistoryRepository.class),null,mock(com.example.platform.identity.api.project.ProjectReadQuery.class),mock(com.example.platform.identity.api.authorization.CanonicalActorResolver.class),mock(com.example.platform.identity.api.authorization.AuthorizationDecisionPort.class)));
        context.registerBean(AuditService.class,()->new AuditService(context.getBean(DSLContext.class),null));
        context.registerBean(AuditEventHandler.class);
        notification=mock(com.example.platform.notification.domain.NotificationProvider.class);
        context.registerBean(NotificationRenderingService.class);
        context.registerBean(NotificationEventHandler.class,()->new NotificationEventHandler(context.getBean(DSLContext.class),List.of(notification),context.getBean(NotificationRenderingService.class),null));
        context.registerBean(DeliverySourceResolver.class);
        var adapter=new com.example.platform.delivery.spi.DeliveryAdapter(){
            public com.example.platform.delivery.domain.DeliveryProtocol protocol(){return com.example.platform.delivery.domain.DeliveryProtocol.SFTP;}
            public ProbeResult probe(com.example.platform.delivery.spi.DeliveryContext c){return ProbeResult.success();}
            public DeliveryResult deliver(com.example.platform.delivery.spi.DeliveryContext c){
                try{deliveredMime=c.contentType();deliveredFileName=c.sourceFileName();delivered=c.sourceStream().readAllBytes();transfers.incrementAndGet();
                    var race=retryRace;if(race!=null){race.transportStarted.countDown();if(Thread.currentThread().getName().endsWith("-B"))race.secondOutcome.countDown();RetryRace.await(race.finishTransport);if(race.mode.equals("UNCERTAIN"))throw new IllegalStateException("transport uncertain");}
                    return DeliveryResult.ok(c.remotePath(),"sftp://test/"+c.deliveryJobId(),delivered.length);}
                catch(java.io.IOException e){throw new IllegalStateException(e);}
            }
        };
        context.registerBean(com.example.platform.delivery.app.DeliveryOutcomeService.class);
        context.registerBean(DeliveryJobService.class,()->new DeliveryJobService(context.getBean(DSLContext.class),new DeliveryAdapterRegistry(List.of(adapter)),context.getBean(DeliverySourceResolver.class),context.getBean(com.example.platform.delivery.app.DeliveryOutcomeService.class),mock(com.example.platform.secrets.api.port.CredentialBundlePort.class),true,3));
        context.registerBean(DeliveryCompletionListener.class);
        context.registerBean(DeliveryRemoteUriIndexService.class);
        context.registerBean(DeliveryStorageUriReferenceContributor.class);
        context.registerBean(OutboxEventDispatcher.class,()->new OutboxEventDispatcher(context.getBean(OutboxEventService.class),context,context.getBean(OutboxEventRouter.class),3,new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        context.registerBean(com.example.platform.artifact.api.event.ArtifactMetadataEventPublisher.class);
        context.registerBean(com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository.class);
        context.registerBean(com.example.platform.media.infrastructure.persistence.JooqMediaAssetRepository.class);
        context.registerBean(com.example.platform.media.app.MediaAssetService.class);
        context.registerBean(com.example.platform.media.app.MediaAuthorization.class, () -> new com.example.platform.media.app.MediaAuthorization(
            () -> java.util.Optional.of(com.example.platform.shared.authorization.CanonicalActor.user("fixture", TenantContext.get(), java.util.Set.of("EDITOR"), "fixture")),
            request -> com.example.platform.shared.authorization.AuthorizationDecision.allow("fixture")));
        context.registerBean(com.example.platform.render.app.asset.AssetRegistryService.class);
        context.registerBean(com.example.platform.render.app.asset.AssetSemanticMetadataService.class);
        context.registerBean(com.example.platform.outbox.coordination.PlatformJobRepository.class);
        context.registerBean(com.example.platform.outbox.coordination.PlatformTaskRepository.class);
        context.registerBean(com.example.platform.outbox.coordination.PlatformCoordinationService.class);
        context.registerBean(com.example.platform.render.app.asset.AssetSearchConsumer.class);
        context.registerBean(StorageFileService.class,()->new StorageFileService(backend,context.getBean(StorageReferenceStore.class),
                root.toString(),context.getBean(StorageWriteIntentRecovery.class),context.getBean(StorageObjectAuthorityRepository.class),new StorageS3Properties()));
        context.registerBean(com.example.platform.render.infrastructure.product.ProductRepository.class,()->{
            previewProducts=spy(new com.example.platform.render.infrastructure.product.ProductRepository(context.getBean(DSLContext.class)));return previewProducts;});
        context.registerBean(com.example.platform.render.infrastructure.product.ProductDependencyRepository.class);
        context.registerBean(com.example.platform.render.app.product.ProductRuntimeService.class);
        context.registerBean(com.example.platform.identity.api.authorization.CanonicalActorResolver.class,()->()->Optional.of(
                com.example.platform.shared.authorization.CanonicalActor.user("preview-actor",previewActorTenant,Set.of("EDITOR"),"fixture")));
        context.registerBean(com.example.platform.identity.api.authorization.AuthorizationDecisionPort.class,()->request->{
            if(previewDenied)throw new SecurityException("preview denied");
            assertEquals("ep04-tenant",request.resource().tenantId());
            return com.example.platform.shared.authorization.AuthorizationDecision.allow("fixture");});
        context.registerBean(com.example.platform.render.app.preview.PreviewMediaUploadService.class);
        context.registerBean(DeliveryProjectScopePort.class,()->(tenant,project)->"ep04-tenant".equals(tenant)&&"project".equals(project));
        context.registerBean(DeliveryAccess.class);
        context.registerBean(DeliveryAdministrationService.class,()->new DeliveryAdministrationService(context.getBean(DSLContext.class),context.getBean(DeliveryJobService.class),
                mock(DeliveryDestinationCredentialService.class),mock(com.example.platform.secrets.api.port.CredentialBundlePort.class),context.getBean(DeliveryAccess.class)));
        context.refresh();
        jdbc.update("insert into tenant(id,name,created_at) values ('ep04-tenant','test',now())");
        jdbc.update("insert into project(id,tenant_id,name,created_at) values ('project','ep04-tenant','test',now())");
        lifecycle=context.getBean(RenderJobLifecycleService.class);dispatcher=context.getBean(OutboxEventDispatcher.class);
        output=context.getBean(StorageOutputPort.class); placements=context.getBean(StoragePlacementQuery.class);
        render=context.getBean(RenderArtifactStorageService.class);
    }
    @AfterAll static void close() throws Exception {
        if(context!=null)context.close();
        if(admin!=null){new JdbcTemplate(admin).execute("drop schema "+SCHEMA+" cascade");closeDataSource(admin);}
        if(root!=null)try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
    }
    @BeforeEach void tenant(){TenantContext.set("ep04-tenant");reset(backend);reset(rawOutbox);reset(notification);transfers.set(0);reset(previewProducts);previewDenied=false;previewActorTenant="ep04-tenant";retryRace=null;
        when(notification.channel()).thenReturn("TEST");when(notification.providerCode()).thenReturn("test");
        when(notification.send(any())).thenReturn(new com.example.platform.notification.domain.DeliveryResult("SENT","accepted"));
    }
    @AfterEach void clear(){TenantContext.clear();}
    String file(String name) throws Exception {
        try(var source=getClass().getResourceAsStream("/render-output-fixture.mp4")){
            java.util.Objects.requireNonNull(source,"deterministic media fixture");
            Files.copy(source,root.resolve(name));
        }return name;
    }

    StorageOutputPort.OutputCommand command(String key,String path){return new StorageOutputPort.OutputCommand(new StorageOwnershipScope("ep04-tenant","project"),new IssuanceIdempotencyKey(key),path,"video/mp4");}
    long count(String table){return jdbc.queryForObject("select count(*) from "+table,Long.class);}

    @Test void realWriteReadbackCommitAndRenderReferenceReplay() throws Exception {
        String path=file("accepted.mp4");
        var accepted=render.uploadJobOutput("accepted-job","project",path,"video/mp4");
        var receipt=placements.find(new StorageOwnershipScope("ep04-tenant","project"),new IssuanceIdempotencyKey("render-output:accepted-job")).orElseThrow();
        assertArrayEquals(Files.readAllBytes(root.resolve(path)),placements.read(receipt.owner(),receipt.receipt().idempotencyKey()));
        assertEquals("ep04-tenant",receipt.placement().location().namespace().tenantId());
        var artifact=context.getBean(ArtifactQueryService.class).getArtifact("ep04-tenant",accepted.artifactId()).orElseThrow();
        assertEquals(receipt.placement().committedDigest(),artifact.contentDigest());
        assertEquals(receipt.placement().committedLength(),artifact.byteLength());
        assertEquals(ArtifactState.AVAILABLE,artifact.state());
        long before=count("artifact");
        assertEquals(accepted,render.uploadJobOutput("accepted-job","project",path,"video/mp4"));
        assertEquals(before,count("artifact"));
        verify(backend,times(1)).put(any());
    }
    @Test void databaseRollbackKeepsRecoverableStorageButNoArtifactAndRetrySucceeds() throws Exception {
        String path=file("rollback.mp4"); long before=count("artifact");
        assertThrows(IllegalStateException.class,()->tx.execute(status->{
            render.uploadJobOutput("rollback-job","project",path,"video/mp4");throw new IllegalStateException("downstream Render transition rejected");}));
        assertEquals(before,count("artifact"));
        var owner=new StorageOwnershipScope("ep04-tenant","project");var key=new IssuanceIdempotencyKey("render-output:rollback-job");
        assertTrue(placements.find(owner,key).isPresent());assertArrayEquals(Files.readAllBytes(root.resolve(path)),placements.read(owner,key));
        assertNotNull(render.uploadJobOutput("rollback-job","project",path,"video/mp4"));
        verify(backend,times(1)).put(any());
    }
    @Test void originalReceiptReplayDoesNotOverrideCurrentStorageQuarantine() throws Exception {
        var c=command("quarantine-key",file("quarantine.mp4"));var original=output.write(c).issuance();
        jdbc.update("update storage_object_placement set placement_state='QUARANTINED' where object_id=?",original.objectId().value());
        assertEquals(original,placements.find(c.owner(),c.key()).orElseThrow());
        assertThrows(IllegalStateException.class,()->placements.read(c.owner(),c.key()));
        assertThrows(IllegalArgumentException.class,()->placements.read(c.owner(),original.objectId(),original.placement().replicaId()));
    }
    @Test void changedInputRetryCannotOverwriteAcceptedOutput() throws Exception {
        String path=file("changed.mp4"); var c=command("changed-key",path);var accepted=output.write(c);
        byte[] original=placements.read(c.owner(),c.key());Files.writeString(root.resolve(path),"changed bytes");
        assertThrows(RuntimeException.class,()->output.write(c));
        assertArrayEquals(original,placements.read(c.owner(),c.key()));assertEquals(accepted.issuance(),placements.find(c.owner(),c.key()).orElseThrow());
        verify(backend,times(1)).put(any());
    }
    @Test void partialWriteHasNoReceiptAndRetryUsesSameIntent() throws Exception {
        String path=file("partial.mp4");var c=command("partial-key",path);
        doAnswer(i->{backend.getClass();throw new IllegalStateException("backend unavailable");}).when(backend).put(any());
        assertThrows(IllegalStateException.class,()->output.write(c));assertTrue(placements.find(c.owner(),c.key()).isEmpty());
        String object=jdbc.queryForObject("select object_id from storage_write_intent where issuance_idempotency_key=?",String.class,c.key().value());
        reset(backend);var result=output.write(c);assertEquals(object,result.issuance().objectId().value());
    }
    @Test void corruptReadbackCannotIssueReceiptOrCommitArtifact() throws Exception {
        String path=file("corrupt.mp4"); var c=command("corrupt-key",path);
        doReturn(Optional.of(new byte[]{1,2,3})).when(backend).get(anyString(),anyString());
        assertThrows(IllegalStateException.class,()->output.write(c));assertTrue(placements.find(c.owner(),c.key()).isEmpty());
        reset(backend);assertNotNull(output.write(c));
    }
    @Test void forgedReceiptAndCrossTenantScopeRejected() throws Exception {
        var c=command("forged-key",file("forged.mp4"));var written=output.write(c);var good=written.issuance();
        var p=good.placement();var badPlacement=new BackendPlacementResult(p.replicaId(),p.location(),p.state(),p.committedDigest(),p.committedLength()+1,p.providerCorrelationId());
        var q=good.receipt();var badReceipt=new PlacementReceipt(q.receiptId(),q.idempotencyKey(),q.semanticFingerprint(),q.purpose(),q.objectId(),q.replicaId(),q.location(),q.state(),q.committedDigest(),q.committedLength()+1,q.providerCorrelationId(),q.issuedAt());
        var forged=new IssuanceResult(good.owner(),good.objectId(),badPlacement,badReceipt);
        var artifacts=context.getBean(ArtifactOutputCommit.class);
        assertThrows(IllegalArgumentException.class,()->artifacts.commit(new ArtifactScope("ep04-tenant","project","forged-job"),new StorageOutputPort.WrittenOutput(forged,written.reference())));
        TenantContext.set("foreign");assertThrows(RuntimeException.class,()->output.write(c));assertThrows(RuntimeException.class,()->placements.find(c.owner(),c.key()));
        verify(backend,times(1)).put(any());
    }
    @Test void artifactCommitFailureRetainsReceiptAndRetryDoesNotRewrite() throws Exception {
        String path=file("commit-failure.mp4");
        var failingCommit=mock(ArtifactCommitService.class);
        when(failingCommit.commit(any())).thenThrow(new IllegalStateException("injected Artifact transaction failure"));
        var service=new ArtifactOutputCommitService(placements,failingCommit,context.getBean(ArtifactQueryService.class),
                context.getBean(ArtifactApplicationQuery.class),context.getBean(DSLContext.class),context.getBean(OutboxEventService.class));
        var failedRender=new RenderArtifactStorageService(output,(scope,written)->tx.execute(status->service.commit(scope,written)));
        long before=count("artifact");
        assertThrows(IllegalStateException.class,()->failedRender.uploadJobOutput("commit-failure-job","project",path,"video/mp4"));
        assertEquals(before,count("artifact"));
        assertTrue(placements.find(new StorageOwnershipScope("ep04-tenant","project"),new IssuanceIdempotencyKey("render-output:commit-failure-job")).isPresent());
        assertNotNull(render.uploadJobOutput("commit-failure-job","project",path,"video/mp4"));
        verify(backend,times(1)).put(any());
    }
    @Test void identicalBytesInDistinctJobsRemainDistinctScopedArtifacts() throws Exception {
        String path=file("same-content.mp4");
        var first=render.uploadJobOutput("same-one","project",path,"video/mp4");
        var second=render.uploadJobOutput("same-two","project",path,"video/mp4");
        assertNotEquals(first.artifactId(),second.artifactId());
        assertTrue(context.getBean(ArtifactApplicationQuery.class).findArtifact(first.scope(),first.artifactId()).isPresent());
        assertTrue(context.getBean(ArtifactApplicationQuery.class).findArtifact(second.scope(),second.artifactId()).isPresent());
        assertTrue(context.getBean(ArtifactApplicationQuery.class).findArtifact(first.scope(),second.artifactId()).isEmpty());
    }
    @Test void missingEmptyTraversalAndSymlinkSourceRejectedBeforeWrite() throws Exception {
        assertThrows(RuntimeException.class,()->output.write(command("missing","missing.mp4")));
        Files.write(root.resolve("empty.mp4"),new byte[0]);assertThrows(RuntimeException.class,()->output.write(command("empty","empty.mp4")));
        assertThrows(RuntimeException.class,()->output.write(command("escape","../escape.mp4")));
        Path outside=Files.createTempFile("ep04-outside-",".mp4");try{Files.createSymbolicLink(root.resolve("link.mp4"),outside);
            assertThrows(RuntimeException.class,()->output.write(command("link","link.mp4")));}finally{Files.deleteIfExists(outside);}
        verify(backend,never()).put(any());
    }
    void job(String id,String state){
        context.getBean(RenderJobRepository.class).create(id,"project","ep04-tenant","snapshot", "default",state,
            RenderInitiator.restore(ActorType.SYSTEM,"output-test","ep04-tenant"),OffsetDateTime.now());
    }
    List<String> eventIds(String job){return jdbc.queryForList("select id from outbox_events where aggregate_id=? order by created_at,id",String.class,job);}
    long jobEvents(String job){return jdbc.queryForObject("select count(*) from outbox_events where aggregate_id=?",Long.class,job);}
    void policy(){
        jdbc.update("insert into delivery_destination(id,tenant_id,name,protocol,config_json,enabled,created_at) values ('lifecycle-dest','ep04-tenant','test','SFTP','{}',true,now()) on conflict do nothing");
        jdbc.update("insert into delivery_policy(id,tenant_id,project_id,destination_id,path_template,trigger_mode,enabled,created_at) values ('lifecycle-policy','ep04-tenant','project','lifecycle-dest','{jobId}/output.mp4','AUTO',true,now()) on conflict do nothing");
    }
    @Test void queuedCreationRollsBackWithAppendAndDispatchesWithoutProviderSemantics(){
        var service=context.getBean(RenderJobService.class);
        var request=new com.example.platform.render.app.dto.CreateRenderJobRequest("project","snapshot","default");
        var initiator=RenderInitiator.restore(ActorType.SYSTEM,"creation-test","ep04-tenant");
        long jobs=count("render_job");
        doThrow(new org.jooq.exception.DataAccessException("injected creation append failure")).when(rawOutbox).append(argThat(a->a.type()==RenderOutboxEvents.RENDERJOBCREATEDEVENT));
        assertThrows(RuntimeException.class,()->service.createForProject("ep04-tenant","project",request,initiator));
        assertEquals(jobs,count("render_job"));
        reset(rawOutbox);var created=service.createForProject("ep04-tenant","project",request,initiator);
        assertEquals("QUEUED",created.status());
        String id=eventIds(created.id()).getFirst();
        String payload=jdbc.queryForObject("select payload from outbox_events where id=?",String.class,id);
        assertFalse(payload.contains("primaryBackend"));assertTrue(payload.contains("creation-test"));
        assertTrue(dispatcher.processOnce(id));assertFalse(dispatcher.processOnce(id));
        var fact=(RenderJobCreatedEvent)context.getBean(OutboxEventRouter.class).decode("render.job.created",2,"render_job",created.id(),payload).payload();
        context.publishEvent(fact);context.publishEvent(fact);
        assertEquals(1L,jdbc.queryForObject("select count(*) from audit_records where resource_id=? and action='RENDER_JOB_CREATED'",Long.class,created.id()));
        assertEquals(1L,jdbc.queryForObject("select count(*) from notification_event where subject_id=?",Long.class,created.id()));
    }
    @Test void completionTraversesRealOutboxCodecConsumersAndArtifactReadback() throws Exception {
        job("lifecycle-success","EXECUTING");policy();String path=file("lifecycle-success.mp4");
        var accepted=lifecycle.complete("ep04-tenant","lifecycle-success",path,"video/mp4");
        assertEquals("COMPLETED",jdbc.queryForObject("select status from render_job where id='lifecycle-success'",String.class));
        assertNull(jdbc.queryForObject("select artifact_uri from render_job where id='lifecycle-success'",String.class));
        var row=jdbc.queryForMap("select * from outbox_events where aggregate_id='lifecycle-success' and event_type='render.job.completed'");
        var router=context.getBean(OutboxEventRouter.class);
        var decoded=router.decode((String)row.get("event_type"),((Number)row.get("event_version")).intValue(),(String)row.get("aggregate_type"),(String)row.get("aggregate_id"),(String)row.get("payload"));
        var completed=assertInstanceOf(RenderJobCompletedEvent.class,decoded.payload());assertEquals(accepted,completed.result());
        assertFalse(((String)row.get("payload")).contains("storageUri"));assertFalse(((String)row.get("payload")).contains("primaryBackend"));
        for(String id:eventIds("lifecycle-success"))assertTrue(dispatcher.processOnce(id));
        assertFalse(dispatcher.processOnce((String)row.get("id")));
        // Replayed callbacks cannot duplicate committed local business effects.
        context.publishEvent(completed);context.publishEvent(completed);
        assertEquals(1L,jdbc.queryForObject("select count(*) from audit_records where resource_id='lifecycle-success' and action='RENDER_JOB_COMPLETED'",Long.class));
        assertEquals(1L,jdbc.queryForObject("select count(*) from delivery_job where render_job_id='lifecycle-success'",Long.class));
        assertEquals(accepted.artifactId().value(),jdbc.queryForObject("select artifact_id from delivery_job where render_job_id='lifecycle-success'",String.class));
        assertTrue(jdbc.queryForObject("select count(*) from notification_event where subject_id='lifecycle-success'",Long.class)>0);
        String delivery=jdbc.queryForObject("select id from delivery_job where render_job_id='lifecycle-success'",String.class);
        TenantContext.clear();assertTrue(context.getBean(DeliveryJobService.class).runJob(delivery));assertNull(TenantContext.get());
        assertFalse(context.getBean(DeliveryJobService.class).runJob(delivery));assertEquals(1,transfers.get());
        assertArrayEquals(Files.readAllBytes(root.resolve(path)),delivered);
        TenantContext.set("ep04-tenant");var receipt=placements.find(new StorageOwnershipScope(accepted.scope().tenantId(),accepted.scope().projectId()),new IssuanceIdempotencyKey("render-output:lifecycle-success")).orElseThrow();
        String uri=receipt.placement().location().opaqueLocator();
        assertEquals(1,context.getBean(DeliveryRemoteUriIndexService.class).findByAnyUri(uri,"project",10).size());
        assertEquals(1,context.getBean(DeliveryStorageUriReferenceContributor.class).findReferences(uri,"project").size());
    }
    @Test void completionAppendFailureRollsBackDomainRowsButRetainsPhysicalRecoveryEvidence() throws Exception {
        job("lifecycle-rollback","EXECUTING");String path=file("lifecycle-rollback.mp4");long artifacts=count("artifact");
        doThrow(new org.jooq.exception.DataAccessException("injected Outbox append failure")).when(rawOutbox).append(argThat(a->a.type()==RenderOutboxEvents.RENDERJOBCOMPLETEDEVENT));
        assertThrows(RuntimeException.class,()->lifecycle.complete("ep04-tenant","lifecycle-rollback",path,"video/mp4"));
        assertEquals("EXECUTING",jdbc.queryForObject("select status from render_job where id='lifecycle-rollback'",String.class));
        assertEquals(artifacts,count("artifact"));assertEquals(0,jobEvents("lifecycle-rollback"));
        assertEquals(0L,jdbc.queryForObject("select count(*) from render_job_status_history where job_id='lifecycle-rollback'",Long.class));
        var owner=new StorageOwnershipScope("ep04-tenant","project");var key=new IssuanceIdempotencyKey("render-output:lifecycle-rollback");
        var original=placements.find(owner,key).orElseThrow();assertArrayEquals(Files.readAllBytes(root.resolve(path)),placements.read(owner,key));
        reset(rawOutbox);var accepted=lifecycle.complete("ep04-tenant","lifecycle-rollback",path,"video/mp4");
        long events=jobEvents("lifecycle-rollback");Files.delete(root.resolve(path));
        assertEquals(accepted,lifecycle.complete("ep04-tenant","lifecycle-rollback",path,"video/mp4"));assertEquals(events,jobEvents("lifecycle-rollback"));
        assertEquals(original.objectId(),placements.find(owner,key).orElseThrow().objectId());verify(backend,times(1)).put(any());
    }
    @Test void rejectedTerminalAndCrossTenantFinalizationCannotWriteOrPublish() throws Exception {
        job("lifecycle-cancelled","CANCELLED");String path=file("lifecycle-cancelled.mp4");
        assertThrows(IllegalStateException.class,()->lifecycle.complete("ep04-tenant","lifecycle-cancelled",path,"video/mp4"));
        TenantContext.set("foreign");assertThrows(RuntimeException.class,()->lifecycle.complete("foreign","lifecycle-cancelled",path,"video/mp4"));
        assertEquals(0,jobEvents("lifecycle-cancelled"));verify(backend,never()).put(any());
    }
    @Test void failureTransitionAndEventAreAtomicAndReplayDoesNotInventAnotherFailure(){
        job("lifecycle-failed","EXECUTING");var failure=context.getBean(RenderJobFailureService.class);
        doThrow(new org.jooq.exception.DataAccessException("injected append rejection")).when(rawOutbox).append(argThat(a->a.type()==RenderOutboxEvents.RENDERJOBFAILEDEVENT));
        assertThrows(RuntimeException.class,()->failure.recordDurableFailure("lifecycle-failed", "native stderr fixture", com.example.platform.render.api.event.RenderFailureReason.EXECUTION_FAILED));
        assertEquals("EXECUTING",jdbc.queryForObject("select status from render_job where id='lifecycle-failed'",String.class));assertEquals(0,jobEvents("lifecycle-failed"));
        reset(rawOutbox);failure.recordDurableFailure("lifecycle-failed", "native stderr fixture", com.example.platform.render.api.event.RenderFailureReason.EXECUTION_FAILED);failure.recordDurableFailure("lifecycle-failed", "native stderr fixture", com.example.platform.render.api.event.RenderFailureReason.EXECUTION_FAILED);
        assertEquals(1,jobEvents("lifecycle-failed"));assertTrue(dispatcher.processOnce(eventIds("lifecycle-failed").getFirst()));
        assertEquals("FAILED",jdbc.queryForObject("select status from render_job where id='lifecycle-failed'",String.class));
        String payload=jdbc.queryForObject("select payload from outbox_events where aggregate_id='lifecycle-failed'",String.class);
        assertTrue(payload.contains("EXECUTION_FAILED"));assertFalse(payload.contains("native stderr"));
        assertEquals("native stderr fixture",jdbc.queryForObject("select error_message from render_job where id='lifecycle-failed'",String.class));
    }
    @Test void durableFailureAndItsTypedEventSurviveAnUnrelatedOuterRollback(){
        job("failure-outer-rollback","EXECUTING");var failure=context.getBean(RenderJobFailureService.class);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{
            jdbc.update("update project set description='rolled-back' where id='project'");
            failure.recordDurableFailure("failure-outer-rollback","native diagnostic",RenderFailureReason.EXECUTION_FAILED);
            throw new IllegalStateException("outer operation rolled back");
        }));
        assertNull(jdbc.queryForObject("select description from project where id='project'",String.class));
        assertEquals("FAILED",jdbc.queryForObject("select status from render_job where id='failure-outer-rollback'",String.class));
        assertEquals(1,jobEvents("failure-outer-rollback"));assertTrue(dispatcher.processOnce(eventIds("failure-outer-rollback").getFirst()));
    }
    @Test void refreshedWorkerObservationCannotBeFailedByStaleRecovery(){
        job("lifecycle-stale","EXECUTING");var jobs=context.getBean(RenderJobRepository.class);Instant cutoff=Instant.now().minusSeconds(30);
        jdbc.update("update render_job set updated_at=? where id='lifecycle-stale'",java.sql.Timestamp.from(cutoff.minusSeconds(60)));
        assertTrue(jobs.findStaleExecutingJobs(cutoff,100).stream().anyMatch(j->"lifecycle-stale".equals(j.get("id",String.class))));
        jdbc.update("update render_job set updated_at=now() where id='lifecycle-stale'");
        assertEquals(0,jobs.markExecutingJobFailed("lifecycle-stale","stale observation",cutoff));assertEquals(0,jobEvents("lifecycle-stale"));
    }
    void lease(String id,String job,Instant until){
        Instant now=Instant.now();
        context.getBean(com.example.platform.render.infrastructure.farm.RenderJobLeaseRepository.class).create(
            new com.example.platform.render.infrastructure.farm.RenderJobLeaseRecord(id,id,job,"ep04-tenant","worker-test","provider-fixture",
                com.example.platform.render.infrastructure.farm.RenderJobLeaseStatus.RUNNING,1L,now.minusSeconds(60),until,null,null,1,3,null,null,null,"test",now,now));
    }
    @Test void leaseCallbackValidatesWorkerExpiryChecksumAndAtomicArtifactAcceptance() throws Exception {
        job("lease-output","EXECUTING");lease("lease-output-id","lease-output",Instant.now().plusSeconds(120));
        String path=file("lease-output.mp4");var service=context.getBean(com.example.platform.render.infrastructure.farm.RenderJobLeaseService.class);
        assertFalse(service.completeLease("lease-output-id","foreign-worker","localFsStorageProvider://"+path,"0".repeat(64),1L).released());
        long before=count("artifact");
        assertThrows(IllegalArgumentException.class,()->service.completeLease("lease-output-id","worker-test","localFsStorageProvider://"+path,"0".repeat(64),1L));
        assertEquals(before,count("artifact"));assertEquals(0,jobEvents("lease-output"));
        assertEquals("RUNNING",jdbc.queryForObject("select status from render_job_lease where lease_id='lease-output-id'",String.class));
        assertEquals("EXECUTING",jdbc.queryForObject("select status from render_job where id='lease-output'",String.class));
        String digest=java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(root.resolve(path))));
        assertTrue(service.completeLease("lease-output-id","worker-test","localFsStorageProvider://"+path,digest,1L).released());
        assertEquals("COMPLETED",jdbc.queryForObject("select status from render_job where id='lease-output'",String.class));
        assertEquals("RELEASED",jdbc.queryForObject("select status from render_job_lease where lease_id='lease-output-id'",String.class));
        verify(backend,times(1)).put(any());
        job("lease-expired","EXECUTING");lease("lease-expired-id","lease-expired",Instant.now().minusSeconds(1));
        assertFalse(service.completeLease("lease-expired-id","worker-test","localFsStorageProvider://"+path,digest,1L).released());
        assertEquals(0,jobEvents("lease-expired"));
        assertFalse(context.getBean(com.example.platform.render.infrastructure.farm.RenderJobLeaseRepository.class).release("lease-expired-id","worker-test",99L,Instant.now()));
    }
    @Test void retiredTypesVersionsAndOldCompletionFieldsAreExplicitlyDeadLettered() throws Exception {
        long audits=count("audit_records");int index=0;
        for(String type:List.of("render.job.created","render.job.completed","render.job.failed","render.job.status.changed",
                "com.example.platform.shared.events.RenderJobCreatedEvent","com.example.platform.shared.events.RenderJobCompletedEvent",
                "com.example.platform.shared.events.RenderJobFailedEvent","com.example.platform.shared.events.RenderJobStatusChangedEvent",
                "com.example.platform.shared.events.RenderCacheHashInvalidatedEvent")){
            String id="retired-"+(index++);jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values (?,'render_job','retired',?,1,'{}','PENDING',0,3,now())",id,type);
            assertFalse(dispatcher.processOnce(id));assertEquals("DEAD_LETTER",jdbc.queryForObject("select status from outbox_events where id=?",String.class,id));
        }
        var event=new RenderJobCompletedEvent(new ArtifactOutputReference(new ArtifactScope("ep04-tenant","project","malformed"),new com.example.platform.shared.identity.ArtifactId("test-only-artifact")),Instant.now(),RenderInitiator.restore(ActorType.SYSTEM,"test","ep04-tenant"));
        var router=context.getBean(OutboxEventRouter.class);String payload=router.encode(RenderOutboxEvents.RENDERJOBCOMPLETEDEVENT.append("ep04-tenant",event,null));
        var mapper=new com.fasterxml.jackson.databind.ObjectMapper();var json=mapper.readTree(payload);((com.fasterxml.jackson.databind.node.ObjectNode)json.get("payload")).put("storageUri","retired://not-a-result");
        jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values ('malformed','render_job','malformed','render.job.completed',2,?,'PENDING',0,3,now())",json.toString());
        assertFalse(dispatcher.processOnce("malformed"));assertEquals("DEAD_LETTER",jdbc.queryForObject("select status from outbox_events where id='malformed'",String.class));assertEquals(audits,count("audit_records"));
    }
    @Test void actualRegistryBindingFactIsSeparateFromRenderLifecycle(){
        var registry=new com.example.platform.render.infrastructure.RenderProviderRegistry();
        var provider=mock(com.example.platform.render.infrastructure.RenderProvider.class);
        when(provider.getStatus()).thenReturn(com.example.platform.render.infrastructure.ProviderStatus.PRODUCTION);when(provider.getPriority()).thenReturn("P1");
        registry.register("registered-renderer",provider,mock(com.example.platform.render.infrastructure.RenderProviderCapability.class));
        var capability=mock(com.example.platform.render.infrastructure.providerruntime.capability.CapabilityNegotiationService.class);
        when(capability.describeProvider(any())).thenReturn(mock(com.example.platform.render.infrastructure.providerruntime.capability.CapabilityDescriptor.class));
        when(capability.negotiate(anyList(),any())).thenReturn(new com.example.platform.render.infrastructure.providerruntime.capability.CapabilityNegotiationResult(true,List.of("registered-renderer"),List.of("registered-renderer"),Set.of(),"fixture match"));
        var health=mock(com.example.platform.render.infrastructure.providerruntime.health.ProviderHealthMonitor.class);
        when(health.checkHealth(anyString())).thenReturn(com.example.platform.render.infrastructure.providerruntime.health.ProviderHealthStatus.healthy("fixture"));
        var engine=new ProviderRuntimeEngine(registry,capability,health,mock(com.example.platform.render.infrastructure.providerruntime.fallback.ProviderFallbackExecutor.class),mock(com.example.platform.render.infrastructure.providerruntime.trace.ProviderTraceEmitter.class),context.getBean(ProviderBindingPublisher.class));
        job("binding-job","EXECUTING");
        var result=engine.resolveProvider(new ProviderRuntimeEngine.ProviderResolutionRequest("binding-job","binding-trace",Set.of(),"default",Map.of(),"ep04-tenant","project"));
        assertTrue(result.isSuccess());assertEquals(1,jobEvents("binding-job"));assertTrue(dispatcher.processOnce(eventIds("binding-job").getFirst()));
        assertEquals("EXECUTING",jdbc.queryForObject("select status from render_job where id='binding-job'",String.class));
        String payload=jdbc.queryForObject("select payload from outbox_events where aggregate_id='binding-job'",String.class);
        assertTrue(payload.contains("registered-renderer"));assertFalse(payload.contains("primaryBackend"));
        assertEquals(1L,jdbc.queryForObject("select count(*) from audit_records where resource_id='binding-job' and action='PROVIDER_RUNTIME_BOUND'",Long.class));
        var empty=new ProviderRuntimeEngine(new com.example.platform.render.infrastructure.RenderProviderRegistry(),capability,health,mock(com.example.platform.render.infrastructure.providerruntime.fallback.ProviderFallbackExecutor.class),mock(com.example.platform.render.infrastructure.providerruntime.trace.ProviderTraceEmitter.class),context.getBean(ProviderBindingPublisher.class));
        assertFalse(empty.resolveProvider(new ProviderRuntimeEngine.ProviderResolutionRequest("no-binding","no-binding-trace",Set.of(),"default",Map.of(),"ep04-tenant","project")).isSuccess());assertEquals(0,jobEvents("no-binding"));
    }
    @Test void cacheInvalidationUsesTypedDurabilityAndNotificationDeduplication(){
        var notifier=new com.example.platform.render.app.cache.RenderCacheHashInvalidationNotifier(context.getBean(RenderLifecyclePublisher.class),new com.example.platform.render.infrastructure.RenderCacheProperties());
        notifier.notifyIfNeeded("ep04-tenant","project","cache-job","base-job",Set.of("task-1"));
        assertEquals(1,jobEvents("cache-job"));String id=eventIds("cache-job").getFirst();assertTrue(dispatcher.processOnce(id));assertFalse(dispatcher.processOnce(id));
        assertEquals(1L,jdbc.queryForObject("select count(*) from notification_event where subject_id='cache-job'",Long.class));
    }

    String deliveryFixture(String name,String protocol) throws Exception {
        job(name,"EXECUTING");lifecycle.complete("ep04-tenant",name,file(name+".mp4"),"video/mp4");
        jdbc.update("insert into delivery_destination(id,tenant_id,name,protocol,config_json,enabled,created_at) values (?,'ep04-tenant','test',?,'{}',true,now())",name+"-dest",protocol);
        return context.getBean(DeliveryJobService.class).triggerManual("ep04-tenant","project",name,name+"-dest");
    }
    Record decodeFact(String type,String aggregate) {
        var row=jdbc.queryForMap("select * from outbox_events where event_type=? and aggregate_id=? order by created_at desc limit 1",type,aggregate);
        return context.getBean(OutboxEventRouter.class).decode(type,((Number)row.get("event_version")).intValue(),(String)row.get("aggregate_type"),aggregate,(String)row.get("payload")).payload();
    }
    @Test void artifactOwnerCreationIsDurableScopedAndReplayedWithoutDuplicateEffects() throws Exception {
        String path=file("owner-created.mp4");var accepted=render.uploadJobOutput("owner-created","project",path,"video/mp4");
        assertEquals(accepted,render.uploadJobOutput("owner-created","project",path,"video/mp4"));
        String id=accepted.artifactId().value();assertEquals(1,jobEvents(id));
        var fact=assertInstanceOf(com.example.platform.artifact.api.event.ArtifactCreatedEvent.class,decodeFact("artifact.created",id));
        assertEquals(accepted,fact.result());assertTrue(dispatcher.processOnce(eventIds(id).getFirst()));
        context.publishEvent(fact);context.publishEvent(fact);
        assertEquals(1L,jdbc.queryForObject("select count(*) from audit_records where resource_id=? and action='ARTIFACT_CREATED'",Long.class,id));
        assertEquals(1L,jdbc.queryForObject("select count(*) from notification_event where subject_id=? and event_type='artifact.created'",Long.class,id));
        assertEquals(0,jobEvents("owner-created")); // Artifact creation does not invent a Render transition.
    }
    @Test void creationAppendFailureRollsBackArtifactButRetainsStorageRecovery() throws Exception {
        String path=file("creation-fail.mp4");long before=count("artifact");
        doThrow(new IllegalStateException("creation append unavailable")).when(rawOutbox).append(argThat(a->a.type()==com.example.platform.artifact.api.event.ArtifactOutboxEvents.ARTIFACTCREATEDEVENT));
        assertThrows(IllegalStateException.class,()->render.uploadJobOutput("creation-fail","project",path,"video/mp4"));
        assertEquals(before,count("artifact"));
        assertTrue(placements.find(new StorageOwnershipScope("ep04-tenant","project"),new IssuanceIdempotencyKey("render-output:creation-fail")).isPresent());
        reset(rawOutbox);var accepted=render.uploadJobOutput("creation-fail","project",path,"video/mp4");assertEquals(1,jobEvents(accepted.artifactId().value()));verify(backend,times(1)).put(any());
    }
    @Test void deliveryAcceptedAttemptDispatchesTypedOutcomeAndDeduplicatesNotification() throws Exception {
        String id=deliveryFixture("delivery-fact","SFTP");assertTrue(context.getBean(DeliveryJobService.class).runJob(id));
        var fact=assertInstanceOf(com.example.platform.delivery.api.event.DeliveryCompletedEvent.class,decodeFact("delivery.completed",id));
        assertEquals(1,fact.attempt());assertEquals("ep04-tenant",fact.tenantId());assertEquals("delivery-fact",fact.renderJobId());
        assertEquals(Files.size(root.resolve("delivery-fact.mp4")),fact.bytesTransferred());
        assertTrue(dispatcher.processOnce(eventIds(id).getFirst()));context.publishEvent(fact);context.publishEvent(fact);
        assertEquals(1L,jdbc.queryForObject("select count(*) from notification_event where subject_id=? and event_type='render.delivery.completed'",Long.class,id));
        assertFalse(context.getBean(DeliveryJobService.class).runJob(id));assertEquals(1,transfers.get());
    }
    @Test void deliveryCompletionAppendFailurePreservesUncertaintyAndHasNoOrphanFact() throws Exception {
        String id=deliveryFixture("delivery-append-fail","SFTP");
        doThrow(new IllegalStateException("outcome append unavailable")).when(rawOutbox).append(argThat(a->a.type()==com.example.platform.delivery.api.event.DeliveryOutboxEvents.COMPLETED));
        assertFalse(context.getBean(DeliveryJobService.class).runJob(id));
        assertEquals("UNCERTAIN",jdbc.queryForObject("select status from delivery_job where id=?",String.class,id));
        assertNull(jdbc.queryForObject("select remote_uri from delivery_job where id=?",String.class,id));assertEquals(0,jobEvents(id));assertEquals(1,transfers.get());
        assertThrows(IllegalStateException.class,()->context.getBean(DeliveryAdministrationService.class).retryDelivery("ep04-tenant","project","delivery-append-fail",id));
    }
    @Test void deliveryFailureAndRetryHaveDistinctAttemptFactsAndRejectStaleOutcome() throws Exception {
        String id=deliveryFixture("delivery-retry","HTTPS_PUT");var service=context.getBean(DeliveryJobService.class);
        assertFalse(service.runJob(id));assertEquals(0,transfers.get());
        var failed=assertInstanceOf(com.example.platform.delivery.api.event.DeliveryFailedEvent.class,decodeFact("delivery.failed",id));assertEquals("ADAPTER_MISSING",failed.errorCode());
        assertTrue(dispatcher.processOnce(eventIds(id).getFirst()));context.publishEvent(failed);assertEquals(1L,jdbc.queryForObject("select count(*) from notification_event where subject_id=?",Long.class,id));
        jdbc.update("update delivery_destination set protocol='SFTP' where id='delivery-retry-dest'");
        assertTrue(context.getBean(DeliveryAdministrationService.class).retryDelivery("ep04-tenant","project","delivery-retry",id));
        var completed=assertInstanceOf(com.example.platform.delivery.api.event.DeliveryCompletedEvent.class,decodeFact("delivery.completed",id));assertEquals(2,completed.attempt());
        assertThrows(IllegalStateException.class,()->context.getBean(com.example.platform.delivery.app.DeliveryOutcomeService.class).failed(failed));
        assertEquals("COMPLETED",jdbc.queryForObject("select status from delivery_job where id=?",String.class,id));assertEquals(2,jobEvents(id));
    }
    @Test void deliveryFailureAppendRejectionRollsBackTheClaimedOutcome() throws Exception {
        String id=deliveryFixture("delivery-failed-append","HTTPS_PUT");
        doThrow(new IllegalStateException("failure append unavailable")).when(rawOutbox).append(argThat(a->a.type()==com.example.platform.delivery.api.event.DeliveryOutboxEvents.FAILED));
        assertThrows(IllegalStateException.class,()->context.getBean(DeliveryJobService.class).runJob(id));
        assertEquals("RUNNING",jdbc.queryForObject("select status from delivery_job where id=?",String.class,id));assertEquals(0,jobEvents(id));assertEquals(0,transfers.get());
    }
    @Test void enrichmentUpdateAppendRollbackAndDuplicateSearchIntentUseActualScope() throws Exception {
        var service=context.getBean(com.example.platform.render.app.asset.AssetSemanticMetadataService.class);
        jdbc.update("insert into media_asset(id,tenant_id,project_id,storage_key,media_type,created_at) values ('metadata-fact','ep04-tenant','project','test-metadata-source','VIDEO',now())");
        var before=service.create("metadata-fact","v3");
        var updated=new com.example.platform.render.domain.asset.semantic.AssetSemanticMetadata(before.assetId(),before.assetVersion(),
            com.example.platform.render.domain.asset.semantic.AssetSemanticMetadata.EnrichmentStatus.COMPLETE,"en",List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),List.of(),before.createdAt(),Instant.now());
        doThrow(new IllegalStateException("metadata append unavailable")).when(rawOutbox).append(argThat(a->a.type()==com.example.platform.artifact.api.event.ArtifactOutboxEvents.ASSETENRICHEDEVENT));
        assertThrows(IllegalStateException.class,()->service.completeEnrichment(updated,"ep04-tenant","project","ASR"));assertEquals(before.status(),service.get(before.assetId()).orElseThrow().status());assertEquals(0,jobEvents(before.assetId()));
        reset(rawOutbox);var fact=service.completeEnrichment(updated,"ep04-tenant","project","ASR");
        assertTrue(dispatcher.processOnce(eventIds(before.assetId()).getFirst()));
        try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            List<java.util.concurrent.Future<?>> futures=new ArrayList<>();
            for(int i=0;i<2;i++)futures.add(pool.submit(()->{TenantContext.set("ep04-tenant");try{context.publishEvent(fact);}finally{TenantContext.clear();}}));
            for(var future:futures)future.get(30,java.util.concurrent.TimeUnit.SECONDS);
        }
        assertEquals(1L,jdbc.queryForObject("select count(*) from platform_job where aggregate_id='metadata-fact'",Long.class));
        assertEquals(1L,jdbc.queryForObject("select count(*) from platform_task t join platform_job j on t.job_id=j.id where j.aggregate_id='metadata-fact'",Long.class));
        assertEquals("ep04-tenant",jdbc.queryForObject("select tenant_id from platform_job where aggregate_id='metadata-fact'",String.class));
        assertEquals("project",jdbc.queryForObject("select project_id from platform_job where aggregate_id='metadata-fact'",String.class));
        assertEquals(1L,jdbc.queryForObject("select count(*) from notification_event where subject_id='metadata-fact'",Long.class));
        assertThrows(IllegalArgumentException.class,()->service.completeEnrichment(updated,"ep04-tenant","wrong-project","ASR"));
        TenantContext.set("foreign");assertThrows(IllegalArgumentException.class,()->service.completeEnrichment(updated,"foreign","project","ASR"));TenantContext.set("ep04-tenant");
        jdbc.update("update asset_semantic_metadata set asset_version='v4' where asset_id='metadata-fact'");
        assertThrows(IllegalStateException.class,()->service.completeEnrichment(updated,"ep04-tenant","project","ASR"));assertEquals(1,jobEvents(before.assetId()));
        TenantContext.set("foreign");assertThrows(RuntimeException.class,()->service.completeEnrichment(updated,"ep04-tenant","project","ASR"));
    }
    @Test void actualAnomalyObservationUsesAuditCatalogAndProductionDispatcher() {
        var service=new UsageAnomalyDetectionService(context.getBean(OutboxEventService.class));
        for(int i=0;i<11;i++)service.analyzeSubmission("ep04-tenant","anomaly-test","default","test");
        var row=jdbc.queryForMap("select * from outbox_events where event_type='audit.usage.anomaly.detected' order by created_at desc limit 1");
        var fact=assertInstanceOf(com.example.platform.audit.api.event.UsageAnomalyDetectedEvent.class,decodeFact("audit.usage.anomaly.detected",(String)row.get("aggregate_id")));
        assertEquals("ep04-tenant",fact.tenantId());assertEquals("anomaly-test",fact.userId());assertEquals("render_burst",fact.ruleType());
        assertTrue(dispatcher.processOnce((String)row.get("id")));assertFalse(dispatcher.processOnce((String)row.get("id")));
        assertNotNull(service.getRiskProfile("ep04-tenant","anomaly-test")); // Detector state is still process-local.
    }
    @Test void retiredOtherDomainDurableContractsAreDeadLetteredWithoutEffects() {
        long audits=count("audit_records"),notifications=count("notification_event");int n=0;
        for(String type:List.of("artifact.created","asset.enriched","render.delivery.completed","render.delivery.failed",
            "com.example.platform.shared.events.ArtifactCreatedEvent","com.example.platform.shared.events.AssetEnrichedEvent",
            "com.example.platform.shared.events.AssetRegisteredEvent","com.example.platform.shared.events.AssetMetadataUpdatedEvent",
            "com.example.platform.shared.events.RenderDeliveryCompletedEvent","com.example.platform.shared.events.RenderDeliveryFailedEvent",
            "com.example.platform.shared.events.UsageAnomalyDetectedEvent")) {
            String id="other-retired-"+n++;jdbc.update("insert into outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values (?,'unused','unused',?,1,'{}','PENDING',0,3,now())",id,type);
            assertFalse(dispatcher.processOnce(id));assertEquals("DEAD_LETTER",jdbc.queryForObject("select status from outbox_events where id=?",String.class,id));
        }
        assertEquals(audits,count("audit_records"));assertEquals(notifications,count("notification_event"));
    }

    @Test void deliveryOutcomeRejectsWrongArtifactTenantAndAttemptBeforeWriting() throws Exception {
        String id=deliveryFixture("delivery-scope","SFTP");
        jdbc.update("update delivery_job set status='RUNNING',attempt_count=1 where id=?",id);
        var accepted=context.getBean(ArtifactOutputRead.class).find(new ArtifactScope("ep04-tenant","project","delivery-scope")).orElseThrow();
        var wrong=new ArtifactOutputReference(accepted.scope(),new com.example.platform.shared.identity.ArtifactId("not-the-accepted-output"));
        var outcomes=context.getBean(com.example.platform.delivery.app.DeliveryOutcomeService.class);
        var bad=new com.example.platform.delivery.api.event.DeliveryCompletedEvent(id,wrong,"delivery-scope-dest",1,com.example.platform.delivery.domain.DeliveryProtocol.SFTP,"sftp://fixture/output",1,Instant.now());
        assertThrows(IllegalStateException.class,()->outcomes.completed(bad));
        var stale=new com.example.platform.delivery.api.event.DeliveryCompletedEvent(id,accepted,"delivery-scope-dest",2,com.example.platform.delivery.domain.DeliveryProtocol.SFTP,"sftp://fixture/output",1,Instant.now());
        assertThrows(IllegalStateException.class,()->outcomes.completed(stale));
        TenantContext.set("foreign");assertThrows(RuntimeException.class,()->outcomes.completed(bad));TenantContext.set("ep04-tenant");
        assertEquals("RUNNING",jdbc.queryForObject("select status from delivery_job where id=?",String.class,id));assertEquals(0,jobEvents(id));
    }
    @Test void reindexIntentTransactionRollbackAndConflictingReplayAreExplicit() {
        var service=context.getBean(com.example.platform.outbox.coordination.PlatformCoordinationService.class);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{
            service.createJobWithTaskOnce("intent-rollback",com.example.platform.outbox.coordination.JobType.SEARCH_REINDEX,"ASSET","intent-asset","ep04-tenant","project","{}","REINDEX",com.example.platform.sandbox.execution.TaskCapability.REINDEX);
            throw new IllegalStateException("consumer rollback");
        }));
        assertEquals(0L,jdbc.queryForObject("select count(*) from platform_job where aggregate_id='intent-asset'",Long.class));
        var first=service.createJobWithTaskOnce("intent-rollback",com.example.platform.outbox.coordination.JobType.SEARCH_REINDEX,"ASSET","intent-asset","ep04-tenant","project","{}","REINDEX",com.example.platform.sandbox.execution.TaskCapability.REINDEX);
        var duplicate=service.createJobWithTaskOnce("intent-rollback",com.example.platform.outbox.coordination.JobType.SEARCH_REINDEX,"ASSET","intent-asset","ep04-tenant","project","{}","REINDEX",com.example.platform.sandbox.execution.TaskCapability.REINDEX);
        assertEquals(first,duplicate);assertEquals(1,service.listTasks(first.id()).size());
        assertThrows(IllegalArgumentException.class,()->service.createJobWithTaskOnce("intent-rollback",com.example.platform.outbox.coordination.JobType.SEARCH_REINDEX,"ASSET","different","ep04-tenant","project","{}","REINDEX",com.example.platform.sandbox.execution.TaskCapability.REINDEX));
        assertEquals(1L,jdbc.queryForObject("select count(*) from platform_job where aggregate_id='intent-asset'",Long.class));
    }

    byte[] previewBytes() throws Exception {
        try(var input=getClass().getResourceAsStream("/render-output-fixture.mp4")){return Objects.requireNonNull(input).readAllBytes();}
    }
    com.example.platform.render.app.preview.PreviewMediaUploadService.Result preview(String key) throws Exception {
        return context.getBean(com.example.platform.render.app.preview.PreviewMediaUploadService.class).upload(
                new com.example.platform.render.api.request.PreviewUploadKey(key),previewBytes(),"video/mp4");
    }
    @Test void previewRealWriteReceiptProductAndDuplicateAreAcceptedWithoutCanonicalMediaClaims() throws Exception {
        long products=count("product"),assets=count("media_asset"),artifacts=count("artifact"),events=count("outbox_events");
        var result=preview("preview-real");var replay=preview("preview-real");
        assertEquals(result,replay);assertEquals(previewBytes().length,result.size());assertEquals(products+1,count("product"));
        var product=previewProducts.findByAsset(result.mediaId()).getFirst();
        assertEquals(com.example.platform.render.domain.product.ProductStatus.READY,product.status());
        assertEquals("ep04-tenant",product.tenantId());assertNull(product.projectId());
        var reference=context.getBean(StorageReferenceStore.class).findById(product.storageReferenceId()).orElseThrow();
        assertArrayEquals(previewBytes(),Files.readAllBytes(Path.of(reference.absolutePath())));
        String object=reference.relativePath().split("/")[1];
        assertEquals("CANONICAL_COMMITTED",jdbc.queryForObject("select intent_state from storage_write_intent where object_id=?",String.class,object));
        assertEquals(1,jdbc.queryForObject("select count(*) from storage_object_placement where object_id=? and placement_state='AVAILABLE'",Integer.class,object));
        assertEquals(assets,count("media_asset"));assertEquals(artifacts,count("artifact"));assertEquals(events,count("outbox_events"));
    }
    @Test void previewProductRollbackRetainsPhysicalEvidenceAndRetryUsesSamePlacement() throws Exception {
        long products=count("product"),intents=count("storage_write_intent"),refs=count("storage_reference");
        doAnswer(call->{var accepted=call.callRealMethod();
            if(((com.example.platform.render.domain.product.Product)call.getArgument(0)).status()==com.example.platform.render.domain.product.ProductStatus.READY)
                throw new IllegalStateException("after real READY insert");return accepted;
        }).when(previewProducts).save(any());
        assertThrows(IllegalStateException.class,()->preview("preview-product-failure"));
        assertEquals(products,count("product"));assertEquals(intents+1,count("storage_write_intent"));assertEquals(refs+1,count("storage_reference"));
        reset(previewProducts);var retry=preview("preview-product-failure");
        assertEquals(products+1,count("product"));assertEquals(intents+1,count("storage_write_intent"));assertEquals(refs+1,count("storage_reference"));
        assertEquals(retry,preview("preview-product-failure"));
    }
    @Test void previewIncompletePhysicalWriteRejectsAndResumesOnlyItsOwnIntent() throws Exception {
        long products=count("product");
        doAnswer(call->{var command=(PutObjectCommand)call.getArgument(0);
            new LocalFsStorageProvider(root.toString()).put(new PutObjectCommand(command.bucket(),command.objectKey(),new byte[]{1},command.contentType()));
            throw new IllegalStateException("partial write fixture");}).when(backend).put(any());
        assertThrows(IllegalStateException.class,()->preview("preview-partial"));assertEquals(products,count("product"));
        reset(backend);var retry=preview("preview-partial");var product=previewProducts.findByAsset(retry.mediaId()).getFirst();
        var ref=context.getBean(StorageReferenceStore.class).findById(product.storageReferenceId()).orElseThrow();
        assertArrayEquals(previewBytes(),Files.readAllBytes(Path.of(ref.absolutePath())));
        assertEquals(products+1,count("product"));
    }
    @Test void previewPlacementMismatchAndDigestMismatchNeverAcceptProduct() throws Exception {
        long products=count("product");
        doAnswer(call->{call.callRealMethod();return new StorageObjectRef(backend.code(),"preview-media","unrelated");}).when(backend).put(any());
        assertThrows(IllegalStateException.class,()->preview("preview-bad-placement"));assertEquals(products,count("product"));
        reset(backend);doAnswer(call->{var command=(PutObjectCommand)call.getArgument(0);
            return new LocalFsStorageProvider(root.toString()).put(new PutObjectCommand(command.bucket(),command.objectKey(),new byte[]{7,8},command.contentType()));
        }).when(backend).put(any());
        assertThrows(IllegalStateException.class,()->preview("preview-bad-digest"));assertEquals(products,count("product"));
        reset(backend);preview("preview-bad-placement");preview("preview-bad-digest");assertEquals(products+2,count("product"));
    }
    @Test void previewSameRequestDifferentBytesRejectsAndQuarantinedPlacementCannotReplay() throws Exception {
        var accepted=preview("preview-integrity");long products=count("product");
        var service=context.getBean(com.example.platform.render.app.preview.PreviewMediaUploadService.class);
        assertThrows(StorageIssuanceConflictException.class,()->service.upload(new com.example.platform.render.api.request.PreviewUploadKey("preview-integrity"),new byte[]{2,3},"video/mp4"));
        var product=previewProducts.findByAsset(accepted.mediaId()).getFirst();
        var ref=context.getBean(StorageReferenceStore.class).findById(product.storageReferenceId()).orElseThrow();
        jdbc.update("update storage_object_placement set placement_state='QUARANTINED' where object_id=?",ref.relativePath().split("/")[1]);
        assertThrows(IllegalStateException.class,()->preview("preview-integrity"));assertEquals(products,count("product"));
    }
    @Test void previewDeniedWrongActorAndInvalidScopeCreateNoEvidence() throws Exception {
        long products=count("product"),intents=count("storage_write_intent");
        previewDenied=true;assertThrows(SecurityException.class,()->preview("denied-preview"));previewDenied=false;
        previewActorTenant="foreign";assertThrows(org.springframework.web.server.ResponseStatusException.class,()->preview("wrong-preview"));previewActorTenant="ep04-tenant";
        var files=context.getBean(StorageFilePort.class);
        assertThrows(IllegalArgumentException.class,()->files.uploadPreview(new StorageOwnershipScope("ep04-tenant","project"),new IssuanceIdempotencyKey("wrong-scope"),previewBytes(),"video/mp4"));
        assertThrows(IllegalArgumentException.class,()->files.uploadPreview(StorageOwnershipScope.tenant("ep04-tenant"),new IssuanceIdempotencyKey("empty"),new byte[0],"video/mp4"));
        assertThrows(IllegalArgumentException.class,()->files.uploadPreview(StorageOwnershipScope.tenant("ep04-tenant"),new IssuanceIdempotencyKey("type"),previewBytes(),"text/plain"));
        assertEquals(products,count("product"));assertEquals(intents,count("storage_write_intent"));verify(backend,never()).put(any());
    }
    @Test void previewProductConcurrentRetryHasOneLocalEffect() throws Exception {
        String identity="f".repeat(64);
        var ref=context.getBean(StorageFilePort.class).uploadPreview(StorageOwnershipScope.tenant("ep04-tenant"),
                new IssuanceIdempotencyKey("preview:"+identity),previewBytes(),"video/mp4");
        long products=count("product");var runtime=context.getBean(com.example.platform.render.app.product.ProductRuntimeService.class);
        try(var executor=java.util.concurrent.Executors.newFixedThreadPool(2)) {
            var calls=List.of(executor.submit(()->{TenantContext.set("ep04-tenant");try{return runtime.registerPreview("ep04-tenant",identity,ref);}finally{TenantContext.clear();}}),
                    executor.submit(()->{TenantContext.set("ep04-tenant");try{return runtime.registerPreview("ep04-tenant",identity,ref);}finally{TenantContext.clear();}}));
            for(var call:calls)assertEquals("prod_preview_"+identity.substring(0,40),call.get().productId());
        }
        assertEquals(products+1,count("product"));
    }
    @Test void staleDeliveryRetryCannotResetClaimCompletionOrUncertainty() throws Exception {
        for(String mode:List.of("RUNNING","COMPLETED","UNCERTAIN")) {
            String name="retry-race-"+mode.toLowerCase();String id=deliveryFixture(name,"HTTPS_PUT");
            assertFalse(context.getBean(DeliveryJobService.class).runJob(id));
            jdbc.update("update delivery_destination set protocol='SFTP' where id=?",name+"-dest");
            transfers.set(0);var race=new RetryRace(mode);retryRace=race;
            var admin=context.getBean(DeliveryAdministrationService.class);
            try(var pool=java.util.concurrent.Executors.newFixedThreadPool(2)) {
                var first=pool.submit(()->{Thread.currentThread().setName("delivery-retry-A");TenantContext.set("ep04-tenant");try{return admin.retryDelivery("ep04-tenant","project",name,id);}catch(RuntimeException failed){if(mode.equals("UNCERTAIN"))return false;throw failed;}finally{TenantContext.clear();}});
                var second=pool.submit(()->{Thread.currentThread().setName("delivery-retry-B");TenantContext.set("ep04-tenant");try{return admin.retryDelivery("ep04-tenant","project",name,id);}catch(IllegalStateException stale){return false;}finally{race.secondOutcome.countDown();TenantContext.clear();}});
                try {
                    RetryRace.await(race.transportStarted);assertEquals(0,race.observed.getCount());
                    if(!mode.equals("RUNNING")){race.finishTransport.countDown();first.get(10,java.util.concurrent.TimeUnit.SECONDS);}
                    race.releaseSecond.countDown();RetryRace.await(race.secondOutcome);
                    assertEquals(1,transfers.get(),"stale retry started a second transport in "+mode);
                    assertFalse(second.get(10,java.util.concurrent.TimeUnit.SECONDS));
                    assertEquals(mode.equals("UNCERTAIN")?"UNCERTAIN":mode,jdbc.queryForObject("select status from delivery_job where id=?",String.class,id));
                    race.finishTransport.countDown();assertEquals(!mode.equals("UNCERTAIN"),first.get(10,java.util.concurrent.TimeUnit.SECONDS));
                    assertEquals(2,jdbc.queryForObject("select attempt_count from delivery_job where id=?",Integer.class,id));
                    assertEquals(mode.equals("UNCERTAIN")?"UNCERTAIN":"COMPLETED",jdbc.queryForObject("select status from delivery_job where id=?",String.class,id));
                } finally {race.releaseSecond.countDown();race.finishTransport.countDown();}
            } finally {retryRace=null;}
        }
    }
    @Test void deliveryRetryRejectsWrongActorAndScopeAndFencesLateCompletion() throws Exception {
        String name="retry-scope",id=deliveryFixture(name,"HTTPS_PUT");assertFalse(context.getBean(DeliveryJobService.class).runJob(id));
        jdbc.update("update delivery_destination set protocol='SFTP' where id=?",name+"-dest");
        var admin=context.getBean(DeliveryAdministrationService.class);int attempts=jdbc.queryForObject("select attempt_count from delivery_job where id=?",Integer.class,id);
        previewDenied=true;assertThrows(SecurityException.class,()->admin.retryDelivery("ep04-tenant","project",name,id));previewDenied=false;
        assertThrows(RuntimeException.class,()->admin.retryDelivery("foreign","project",name,id));
        assertThrows(RuntimeException.class,()->admin.retryDelivery("ep04-tenant","other",name,id));
        assertThrows(IllegalArgumentException.class,()->admin.retryDelivery("ep04-tenant","project","other-render",id));
        assertEquals(0,transfers.get());assertEquals(attempts,jdbc.queryForObject("select attempt_count from delivery_job where id=?",Integer.class,id));
        assertTrue(admin.retryDelivery("ep04-tenant","project",name,id));assertEquals(1,transfers.get());
        var completed=assertInstanceOf(com.example.platform.delivery.api.event.DeliveryCompletedEvent.class,decodeFact("delivery.completed",id));
        var stale=new com.example.platform.delivery.api.event.DeliveryCompletedEvent(id,completed.result(),completed.destinationId(),attempts,
                completed.protocol(),"sftp://stale",completed.bytesTransferred(),Instant.now());
        assertThrows(IllegalStateException.class,()->context.getBean(DeliveryOutcomeService.class).completed(stale));
        assertEquals(completed.remoteUri(),jdbc.queryForObject("select remote_uri from delivery_job where id=?",String.class,id));assertEquals(2,jobEvents(id));
    }
    @Test void explicitlyAuthorizedAdministrativeRetryScopesAndRestoresTenant() throws Exception {
        String name="retry-admin",id=deliveryFixture(name,"HTTPS_PUT");assertFalse(context.getBean(DeliveryJobService.class).runJob(id));
        jdbc.update("update delivery_destination set protocol='SFTP' where id=?",name+"-dest");
        var access=new DeliveryAccess(()->Optional.of(com.example.platform.shared.authorization.CanonicalActor.user("administrator","control",Set.of("ADMIN"),"fixture")),
                request->com.example.platform.shared.authorization.AuthorizationDecision.deny("unused","unused","unused"),(t,p)->false);
        var admin=new DeliveryAdministrationService(context.getBean(DSLContext.class),context.getBean(DeliveryJobService.class),
                mock(DeliveryDestinationCredentialService.class),mock(com.example.platform.secrets.api.port.CredentialBundlePort.class),access);
        TenantContext.set("control");admin.retryAdministrativeJob(id);assertEquals("control",TenantContext.get());
        assertEquals("COMPLETED",jdbc.queryForObject("select status from delivery_job where id=?",String.class,id));assertEquals(1,transfers.get());
    }
    @Test void webmDeliveryMetadataMatchesTheAcceptedRealBytes() throws Exception {
        byte[] bytes;try(var input=getClass().getResourceAsStream("/output-formats/output.webm")){bytes=Objects.requireNonNull(input).readAllBytes();}
        Files.write(root.resolve("format-repro.webm"),bytes);
        var reference=render.uploadJobOutput("format-repro","project","format-repro.webm","video/webm");
        try(var content=context.getBean(DeliverySourceResolver.class).open(reference).orElseThrow()) {
            assertArrayEquals(bytes,content.stream().readAllBytes());assertEquals("video/webm",content.contentType());assertEquals("output.webm",content.fileName());
        }
    }
    @Test void everySupportedRealOutputFormatPersistsAndReachesDeliveryWithoutRelabeling() throws Exception {
        var formats=Map.of("mp4","video/mp4","webm","video/webm","mov","video/quicktime","wav","audio/wav",
                "mp3","audio/mpeg","flac","audio/flac","png","image/png","jpg","image/jpeg");
        for(var entry:formats.entrySet()) {
            String extension=entry.getKey(),mime=entry.getValue(),name="format-"+extension;
            byte[] bytes;try(var input=getClass().getResourceAsStream("/output-formats/output."+extension)){bytes=Objects.requireNonNull(input).readAllBytes();}
            String path=name+".bin";Files.write(root.resolve(path),bytes);job(name,"EXECUTING");
            var reference=lifecycle.complete("ep04-tenant",name,path,mime);
            var content=context.getBean(ArtifactOutputRead.class).read(reference);
            assertArrayEquals(bytes,content.bytes());assertEquals(mime,content.contentType());assertEquals("output."+extension,content.fileName());
            var receipt=placements.find(new StorageOwnershipScope("ep04-tenant","project"),new IssuanceIdempotencyKey("render-output:"+name)).orElseThrow();
            var metadata=placements.reference(receipt.owner(),receipt.objectId(),receipt.placement().replicaId());
            assertEquals(mime,metadata.mimeType());assertEquals(bytes.length,metadata.fileSize());
            assertEquals("COMPLETED",jdbc.queryForObject("select status from render_job where id=?",String.class,name));
            jdbc.update("insert into delivery_destination(id,tenant_id,name,protocol,config_json,enabled,created_at) values (?,'ep04-tenant','test','SFTP','{}',true,now())",name+"-dest");
            String delivery=context.getBean(DeliveryJobService.class).triggerManual("ep04-tenant","project",name,name+"-dest");
            assertTrue(jdbc.queryForObject("select remote_path from delivery_job where id=?",String.class,delivery).endsWith("/output."+extension));
            assertTrue(context.getBean(DeliveryJobService.class).runJob(delivery));
            assertArrayEquals(bytes,delivered);assertEquals(mime,deliveredMime);assertEquals("output."+extension,deliveredFileName);
        }
    }
    @Test void inconsistentOutputFormatCannotCommitOrCompleteAndValidNewOutputStillWorks() throws Exception {
        byte[] bytes;try(var input=getClass().getResourceAsStream("/output-formats/output.png")){bytes=Objects.requireNonNull(input).readAllBytes();}
        Files.write(root.resolve("wrong-format.bin"),bytes);job("wrong-format","EXECUTING");long artifacts=count("artifact");
        assertThrows(IllegalArgumentException.class,()->lifecycle.complete("ep04-tenant","wrong-format","wrong-format.bin","video/mp4"));
        assertEquals(artifacts,count("artifact"));assertEquals("EXECUTING",jdbc.queryForObject("select status from render_job where id='wrong-format'",String.class));assertEquals(0,jobEvents("wrong-format"));
        assertTrue(placements.find(new StorageOwnershipScope("ep04-tenant","project"),new IssuanceIdempotencyKey("render-output:wrong-format")).isPresent());
        long intents=count("storage_write_intent");
        assertThrows(IllegalArgumentException.class,()->render.uploadJobOutput("unknown-format","project","wrong-format.bin","application/unknown"));assertEquals(intents,count("storage_write_intent"));
        job("correct-format","EXECUTING");var accepted=lifecycle.complete("ep04-tenant","correct-format","wrong-format.bin","image/png");
        assertEquals("image/png",context.getBean(ArtifactOutputRead.class).read(accepted).contentType());
    }
    @Test void persistedMetadataMismatchRejectsReadAndOwnerReplayRestoresItsOriginalMetadata() throws Exception {
        String path=file("metadata-repair.mp4");var reference=render.uploadJobOutput("metadata-repair","project",path,"video/mp4");
        var receipt=placements.find(new StorageOwnershipScope("ep04-tenant","project"),new IssuanceIdempotencyKey("render-output:metadata-repair")).orElseThrow();
        var metadata=placements.reference(receipt.owner(),receipt.objectId(),receipt.placement().replicaId());
        jdbc.update("update storage_reference set mime_type='audio/wav' where storage_reference_id=?",metadata.storageReferenceId());
        assertThrows(IllegalArgumentException.class,()->context.getBean(ArtifactOutputRead.class).read(reference));
        assertTrue(context.getBean(DeliverySourceResolver.class).open(reference).isEmpty());
        assertEquals(reference,render.uploadJobOutput("metadata-repair","project",path,"video/mp4"));
        assertEquals("video/mp4",context.getBean(ArtifactOutputRead.class).read(reference).contentType());
    }
}
