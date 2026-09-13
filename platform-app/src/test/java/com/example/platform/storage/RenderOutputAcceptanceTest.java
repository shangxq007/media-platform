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
    @EnableTransactionManagement static class Transactions {}
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
        context.registerBean(ArtifactRepository.class); context.registerBean(ArtifactRelationRepository.class);
        context.registerBean(JooqArtifactCommitService.class); context.registerBean(JooqArtifactQueryService.class);
        context.registerBean(JooqArtifactApplicationQuery.class); context.registerBean(ArtifactOutputCommitService.class);
        context.registerBean(RenderArtifactStorageService.class); context.refresh();
        output=context.getBean(StorageOutputPort.class); placements=context.getBean(StoragePlacementQuery.class);
        render=context.getBean(RenderArtifactStorageService.class);
    }
    @AfterAll static void close() throws Exception {
        if(context!=null)context.close();
        if(admin!=null){new JdbcTemplate(admin).execute("drop schema "+SCHEMA+" cascade");closeDataSource(admin);}
        if(root!=null)try(var files=Files.walk(root)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.delete(p);}
    }
    @BeforeEach void tenant(){TenantContext.set("ep04-tenant");reset(backend);}
    @AfterEach void clear(){TenantContext.clear();}
    String file(String name) throws Exception {Files.write(root.resolve(name), ("deterministic-output:"+name).getBytes(java.nio.charset.StandardCharsets.UTF_8));return name;}
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
        var c=command("forged-key",file("forged.mp4"));var good=output.write(c).issuance();
        var p=good.placement();var badPlacement=new BackendPlacementResult(p.replicaId(),p.location(),p.state(),p.committedDigest(),p.committedLength()+1,p.providerCorrelationId());
        var q=good.receipt();var badReceipt=new PlacementReceipt(q.receiptId(),q.idempotencyKey(),q.semanticFingerprint(),q.purpose(),q.objectId(),q.replicaId(),q.location(),q.state(),q.committedDigest(),q.committedLength()+1,q.providerCorrelationId(),q.issuedAt());
        var forged=new IssuanceResult(good.owner(),good.objectId(),badPlacement,badReceipt);
        var artifacts=context.getBean(ArtifactOutputCommit.class);
        assertThrows(IllegalArgumentException.class,()->artifacts.commit(new ArtifactScope("ep04-tenant","project","forged-job"),forged,ArtifactMediaType.VIDEO));
        TenantContext.set("foreign");assertThrows(RuntimeException.class,()->output.write(c));assertThrows(RuntimeException.class,()->placements.find(c.owner(),c.key()));
        verify(backend,times(1)).put(any());
    }
    @Test void artifactCommitFailureRetainsReceiptAndRetryDoesNotRewrite() throws Exception {
        String path=file("commit-failure.mp4");
        var failingCommit=mock(ArtifactCommitService.class);
        when(failingCommit.commit(any())).thenThrow(new IllegalStateException("injected Artifact transaction failure"));
        var service=new ArtifactOutputCommitService(placements,failingCommit,context.getBean(ArtifactQueryService.class),
                context.getBean(ArtifactApplicationQuery.class),context.getBean(DSLContext.class));
        var failedRender=new RenderArtifactStorageService(output,(scope,receipt,media)->tx.execute(status->service.commit(scope,receipt,media)));
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
}
