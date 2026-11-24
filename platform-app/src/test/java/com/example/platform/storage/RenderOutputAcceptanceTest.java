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
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.app.*;
import com.example.platform.render.app.event.*;
import com.example.platform.render.api.event.*;
import com.example.platform.outbox.app.*;
import com.example.platform.audit.app.*;
import com.example.platform.notification.app.*;
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

/** Real Storage recovery, owner facts and independent Product-preview acceptance, retained after legacy Render retirement. */
class RenderOutputAcceptanceTest extends PostgresTestContainerSupport {
    static final String SCHEMA=isolatedSchemaName();
    static AnnotationConfigApplicationContext context;
    static Path root;
    static JdbcTemplate jdbc;
    static LocalFsStorageProvider backend;
    static StorageOutputPort output;
    static StoragePlacementQuery placements;
    static TransactionTemplate tx;
    static DataSource admin;
    static OutboxEventService rawOutbox;
    static OutboxEventDispatcher dispatcher;
    static com.example.platform.notification.domain.NotificationProvider notification;
    static com.example.platform.render.infrastructure.product.ProductRepository previewProducts;
    static boolean previewDenied;
    static String previewActorTenant;
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
        context.registerBean(DSLContext.class,()->DSL.using(new TransactionAwareDataSourceProxy(ds),SQLDialect.POSTGRES,new org.jooq.conf.Settings().withRenderSchema(false)));
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
        context.registerBean(OutboxEventRouter.class,()->new OutboxEventRouter(List.of(new RenderOutboxEvents(),new com.example.platform.artifact.api.event.ArtifactOutboxEvents(),new com.example.platform.audit.api.event.AuditOutboxEvents())));
        context.registerBean(PostgresNotificationService.class);
        context.registerBean(OutboxEventService.class,()->{
            rawOutbox=spy(new OutboxEventService(context.getBean(DSLContext.class),3,context.getBean(PostgresNotificationService.class),context.getBean(OutboxEventRouter.class)));return rawOutbox;});
        context.registerBean(AuditService.class,()->new AuditService(context.getBean(DSLContext.class),null));
        context.registerBean(AuditEventHandler.class);
        notification=mock(com.example.platform.notification.domain.NotificationProvider.class);
        context.registerBean(NotificationRenderingService.class);
        context.registerBean(NotificationEventHandler.class,()->new NotificationEventHandler(context.getBean(DSLContext.class),List.of(notification),context.getBean(NotificationRenderingService.class),null));
        context.registerBean(OutboxEventDispatcher.class,()->new OutboxEventDispatcher(context.getBean(OutboxEventService.class),context,context.getBean(OutboxEventRouter.class),3,new io.micrometer.core.instrument.simple.SimpleMeterRegistry()));
        context.registerBean(com.example.platform.artifact.api.event.ArtifactMetadataEventPublisher.class);
        context.registerBean(com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository.class);
        var mediaAssets = mock(com.example.platform.media.api.MediaAssets.class);
        org.mockito.Mockito.when(mediaAssets.findById("ep04-tenant", "metadata-fact"))
                .thenReturn(java.util.Optional.of(new com.example.platform.media.api.Asset(
                        "metadata-fact", "ep04-tenant", "project", "ref", "VIDEO", null, 1L,
                        "metadata-fact-digest", "v3", null, null, null, null, null, null,
                        false, false, "DRAFT", Instant.now(), Instant.now())));
        context.registerBean(com.example.platform.media.api.MediaAssets.class, () -> mediaAssets);
        // These two services carry @Profile("legacy-media-disabled"); register them directly so the
        // test still exercises their repository path without activating the legacy profile.
        registerProfileFenced("assetRegistryService",
                com.example.platform.render.app.asset.AssetRegistryService.class,
                () -> new com.example.platform.render.app.asset.AssetRegistryService(
                        context.getBean(com.example.platform.media.api.MediaAssets.class)));
        registerProfileFenced("assetSemanticMetadataService",
                com.example.platform.render.app.asset.AssetSemanticMetadataService.class,
                () -> new com.example.platform.render.app.asset.AssetSemanticMetadataService(
                        context.getBean(com.example.platform.render.infrastructure.asset.AssetSemanticMetadataRepository.class),
                        context.getBean(com.example.platform.artifact.api.event.ArtifactMetadataEventPublisher.class),
                        context.getBean(com.example.platform.render.app.asset.AssetRegistryService.class)));
        context.registerBean(com.example.platform.outbox.coordination.PlatformJobRepository.class);
        context.registerBean(com.example.platform.outbox.coordination.PlatformTaskRepository.class);
        context.registerBean(com.example.platform.outbox.coordination.PlatformCoordinationService.class);
        registerProfileFenced("assetSearchConsumer",
                com.example.platform.render.app.asset.AssetSearchConsumer.class,
                () -> new com.example.platform.render.app.asset.AssetSearchConsumer(
                        context.getBean(com.example.platform.outbox.coordination.PlatformCoordinationService.class)));
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
        context.refresh();
        jdbc.update("insert into identity.tenant(id,name,created_at) values ('ep04-tenant','test',now())");
        jdbc.update("insert into identity.project(id,tenant_id,name,created_at) values ('project','ep04-tenant','test',now())");
        dispatcher=context.getBean(OutboxEventDispatcher.class);
        output=context.getBean(StorageOutputPort.class); placements=context.getBean(StoragePlacementQuery.class);
    }
    @BeforeEach void tenant(){TenantContext.set("ep04-tenant");reset(backend);reset(rawOutbox);reset(notification);reset(previewProducts);previewDenied=false;previewActorTenant="ep04-tenant";
        when(notification.channel()).thenReturn("TEST");when(notification.providerCode()).thenReturn("test");
        when(notification.send(any())).thenReturn(new com.example.platform.notification.domain.DeliveryResult("SENT","accepted"));
    }
    @AfterAll static void close() throws Exception {
        if(context!=null)context.close();
        if(admin!=null){new JdbcTemplate(admin).execute("drop schema "+SCHEMA+" cascade");closeDataSource(admin);}
        if(root!=null)try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
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

    long jobEvents(String job){return jdbc.queryForObject("select count(*) from infrastructure.outbox_events where aggregate_id=?",Long.class,job);}

    static void registerProfileFenced(String name,Class<?> type,java.util.function.Supplier<?> supplier){
        var definition=new org.springframework.beans.factory.support.RootBeanDefinition(type);
        definition.setInstanceSupplier(supplier);
        context.registerBeanDefinition(name,definition);
    }

    Record decodeFact(String type,String aggregate) {
        var row=jdbc.queryForMap("select * from infrastructure.outbox_events where event_type=? and aggregate_id=? order by created_at desc limit 1",type,aggregate);
        return context.getBean(OutboxEventRouter.class).decode(type,((Number)row.get("event_version")).intValue(),(String)row.get("aggregate_type"),aggregate,(String)row.get("payload")).payload();
    }

    byte[] previewBytes() throws Exception {
        try(var input=getClass().getResourceAsStream("/render-output-fixture.mp4")){return Objects.requireNonNull(input).readAllBytes();}
    }

    com.example.platform.render.app.preview.PreviewMediaUploadService.Result preview(String key) throws Exception {
        return context.getBean(com.example.platform.render.app.preview.PreviewMediaUploadService.class).upload(
                new com.example.platform.render.api.request.PreviewUploadKey(key),previewBytes(),"video/mp4");
    }

    @Test void originalReceiptReplayDoesNotOverrideCurrentStorageQuarantine() throws Exception {
        var c=command("quarantine-key",file("quarantine.mp4"));var original=output.write(c).issuance();
        jdbc.update("update storage.storage_object_placement set placement_state='QUARANTINED' where object_id=?",original.objectId().value());
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
        String object=jdbc.queryForObject("select object_id from storage.storage_write_intent where issuance_idempotency_key=?",String.class,c.key().value());
        reset(backend);var result=output.write(c);assertEquals(object,result.issuance().objectId().value());
    }

    @Test void corruptReadbackCannotIssueReceiptOrCommitArtifact() throws Exception {
        String path=file("corrupt.mp4"); var c=command("corrupt-key",path);
        doReturn(Optional.of(new byte[]{1,2,3})).when(backend).get(anyString(),anyString());
        assertThrows(IllegalStateException.class,()->output.write(c));assertTrue(placements.find(c.owner(),c.key()).isEmpty());
        reset(backend);assertNotNull(output.write(c));
    }

    @Test void missingEmptyTraversalAndSymlinkSourceRejectedBeforeWrite() throws Exception {
        assertThrows(RuntimeException.class,()->output.write(command("missing","missing.mp4")));
        Files.write(root.resolve("empty.mp4"),new byte[0]);assertThrows(RuntimeException.class,()->output.write(command("empty","empty.mp4")));
        assertThrows(RuntimeException.class,()->output.write(command("escape","../escape.mp4")));
        Path outside=Files.createTempFile("ep04-outside-",".mp4");try{Files.createSymbolicLink(root.resolve("link.mp4"),outside);
            assertThrows(RuntimeException.class,()->output.write(command("link","link.mp4")));}finally{Files.deleteIfExists(outside);}
        verify(backend,never()).put(any());
    }

    @Test void enrichmentUpdateAppendRollbackAndDuplicateSearchIntentUseActualScope() throws Exception {
        var service=context.getBean(com.example.platform.render.app.asset.AssetSemanticMetadataService.class);
        jdbc.update("insert into artifacts.artifact(id,tenant_id,project_id,workspace_id,content_digest,byte_length,media_type,artifact_kind,state,schema_version,created_at)"
                + " values ('metadata-fact','ep04-tenant','project','project','metadata-fact-digest',1,'VIDEO','SOURCE_MEDIA','AVAILABLE',1,now())");
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
        assertEquals(1L,jdbc.queryForObject("select count(*) from execution.platform_job where aggregate_id='metadata-fact'",Long.class));
        assertEquals(1L,jdbc.queryForObject("select count(*) from execution.platform_task t join execution.platform_job j on t.job_id=j.id where j.aggregate_id='metadata-fact'",Long.class));
        assertEquals("ep04-tenant",jdbc.queryForObject("select tenant_id from execution.platform_job where aggregate_id='metadata-fact'",String.class));
        assertEquals("project",jdbc.queryForObject("select project_id from execution.platform_job where aggregate_id='metadata-fact'",String.class));
        assertEquals(1L,jdbc.queryForObject("select count(*) from notification.notification_event where subject_id='metadata-fact'",Long.class));
        assertThrows(IllegalArgumentException.class,()->service.completeEnrichment(updated,"ep04-tenant","wrong-project","ASR"));
        TenantContext.set("foreign");assertThrows(IllegalArgumentException.class,()->service.completeEnrichment(updated,"foreign","project","ASR"));TenantContext.set("ep04-tenant");
        jdbc.update("update render.asset_semantic_metadata set asset_version='v4' where artifact_id='metadata-fact'");
        assertThrows(IllegalStateException.class,()->service.completeEnrichment(updated,"ep04-tenant","project","ASR"));assertEquals(1,jobEvents(before.assetId()));
        TenantContext.set("foreign");assertThrows(RuntimeException.class,()->service.completeEnrichment(updated,"ep04-tenant","project","ASR"));
    }

    @Test void actualAnomalyObservationUsesAuditCatalogAndProductionDispatcher() {
        var service=new UsageAnomalyDetectionService(context.getBean(OutboxEventService.class));
        for(int i=0;i<11;i++)service.analyzeSubmission("ep04-tenant","anomaly-test","default","test");
        var row=jdbc.queryForMap("select * from infrastructure.outbox_events where event_type='audit.usage.anomaly.detected' order by created_at desc limit 1");
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
            String id="other-retired-"+n++;jdbc.update("insert into infrastructure.outbox_events(id,aggregate_type,aggregate_id,event_type,event_version,payload,status,retry_count,max_retries,created_at) values (?,'unused','unused',?,1,'{}','PENDING',0,3,now())",id,type);
            assertFalse(dispatcher.processOnce(id));assertEquals("DEAD_LETTER",jdbc.queryForObject("select status from infrastructure.outbox_events where id=?",String.class,id));
        }
        assertEquals(audits,count("audit_records"));assertEquals(notifications,count("notification_event"));
    }

    @Test void reindexIntentTransactionRollbackAndConflictingReplayAreExplicit() {
        var service=context.getBean(com.example.platform.outbox.coordination.PlatformCoordinationService.class);
        assertThrows(IllegalStateException.class,()->tx.execute(status->{
            service.createJobWithTaskOnce("intent-rollback",com.example.platform.outbox.coordination.JobType.SEARCH_REINDEX,"ASSET","intent-asset","ep04-tenant","project","{}","REINDEX",com.example.platform.sandbox.execution.TaskCapability.REINDEX);
            throw new IllegalStateException("consumer rollback");
        }));
        assertEquals(0L,jdbc.queryForObject("select count(*) from execution.platform_job where aggregate_id='intent-asset'",Long.class));
        var first=service.createJobWithTaskOnce("intent-rollback",com.example.platform.outbox.coordination.JobType.SEARCH_REINDEX,"ASSET","intent-asset","ep04-tenant","project","{}","REINDEX",com.example.platform.sandbox.execution.TaskCapability.REINDEX);
        var duplicate=service.createJobWithTaskOnce("intent-rollback",com.example.platform.outbox.coordination.JobType.SEARCH_REINDEX,"ASSET","intent-asset","ep04-tenant","project","{}","REINDEX",com.example.platform.sandbox.execution.TaskCapability.REINDEX);
        assertEquals(first,duplicate);assertEquals(1,service.listTasks(first.id()).size());
        assertThrows(IllegalArgumentException.class,()->service.createJobWithTaskOnce("intent-rollback",com.example.platform.outbox.coordination.JobType.SEARCH_REINDEX,"ASSET","different","ep04-tenant","project","{}","REINDEX",com.example.platform.sandbox.execution.TaskCapability.REINDEX));
        assertEquals(1L,jdbc.queryForObject("select count(*) from execution.platform_job where aggregate_id='intent-asset'",Long.class));
    }

    @Test void previewRealWriteReceiptProductAndDuplicateAreAcceptedWithoutCanonicalMediaClaims() throws Exception {
        long products=count("product"),artifacts=count("artifact"),events=count("outbox_events");
        var result=preview("preview-real");var replay=preview("preview-real");
        assertEquals(result,replay);assertEquals(previewBytes().length,result.size());assertEquals(products+1,count("product"));
        var product=previewProducts.findByAsset(result.mediaId()).getFirst();
        assertEquals(com.example.platform.render.domain.product.ProductStatus.READY,product.status());
        assertEquals("ep04-tenant",product.tenantId());assertNull(product.projectId());
        var reference=context.getBean(StorageReferenceStore.class).findById(product.storageReferenceId()).orElseThrow();
        assertArrayEquals(previewBytes(),Files.readAllBytes(Path.of(reference.absolutePath())));
        String object=reference.relativePath().split("/")[1];
        assertEquals("CANONICAL_COMMITTED",jdbc.queryForObject("select intent_state from storage.storage_write_intent where object_id=?",String.class,object));
        assertEquals(1,jdbc.queryForObject("select count(*) from storage.storage_object_placement where object_id=? and placement_state='AVAILABLE'",Integer.class,object));
        assertEquals(artifacts,count("artifact"));assertEquals(events,count("outbox_events"));
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
        jdbc.update("update storage.storage_object_placement set placement_state='QUARANTINED' where object_id=?",ref.relativePath().split("/")[1]);
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
    List<String> eventIds(String job){return jdbc.queryForList("select id from infrastructure.outbox_events where aggregate_id=? order by created_at,id",String.class,job);}
}
