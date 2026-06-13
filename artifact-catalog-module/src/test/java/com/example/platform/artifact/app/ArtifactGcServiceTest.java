package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.artifact.infrastructure.ArtifactGcProperties;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.storage.domain.BlobStorage;
import java.time.Instant;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

class ArtifactGcServiceTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private static ArtifactCatalogRepository repository;
    private static ArtifactLifecycleService lifecycle;
    
    private ArtifactGcService gcService;
    private BlobStorage blobStorage;
    private AuditPort auditPort;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS artifact ("
                + "id varchar(64) primary key,"
                + "render_job_id varchar(64) not null,"
                + "project_id varchar(64) not null,"
                + "storage_uri text not null,"
                + "format varchar(32),"
                + "resolution varchar(32),"
                + "duration bigint,"
                + "created_at timestamp not null,"
                + "status varchar(32) not null default 'ACTIVE',"
                + "tombstoned_at timestamp"
                + ")");

        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES, settings);
        repository = new ArtifactCatalogRepository(dsl);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        ArtifactCatalogService catalog = new ArtifactCatalogService(repository, null, registry);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        lifecycle = new ArtifactLifecycleService(repository, catalog, dsl, registry, events, java.util.List.of());
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        // Clean up before each test
        dsl.execute("TRUNCATE TABLE artifact CASCADE");

        blobStorage = mock(BlobStorage.class);
        auditPort = mock(AuditPort.class);
        ArtifactGcProperties props = new ArtifactGcProperties();
        props.setRetentionDays(1);
        props.setBatchSize(10);
        gcService = new ArtifactGcService(repository, lifecycle, blobStorage, props);
        gcService.setAuditPort(auditPort);
    }

    @Test
    void purgesOldTombstonedArtifacts() {
        repository.save(new Artifact(
                "art_gc1", "rj_1", "prj_1", "s3://bucket/old.mp4",
                "mp4", "1080p", 10L, null, null, ArtifactStatus.TOMBSTONED,
                Instant.now().minusSeconds(86400 * 10), Instant.now()));
        when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);

        ArtifactGcService.GcResult result = gcService.runGc(1);
        assertEquals(1, result.purged());
        assertEquals(0, result.failed());

        Artifact updated = repository.findById("art_gc1").orElseThrow();
        assertEquals(ArtifactStatus.PURGED, updated.status());
        verify(blobStorage).deleteStorageUri("s3://bucket/old.mp4");
    }

    @Test
    void dryRunShouldNotDeleteBlob() {
        repository.save(new Artifact(
                "art_dry1", "rj_1", "prj_1", "s3://bucket/dry.mp4",
                "mp4", "1080p", 10L, null, null, ArtifactStatus.TOMBSTONED,
                Instant.now().minusSeconds(86400 * 10), Instant.now()));

        ArtifactGcService.GcResult result = gcService.runGc(1, true, 50);

        assertEquals(1, result.purged());  // dry-run counts as "would purge"
        assertEquals(0, result.failed());

        // Should NOT delete blob
        verify(blobStorage, never()).deleteStorageUri(anyString());

        // Should NOT update status to PURGED
        Artifact unchanged = repository.findById("art_dry1").orElseThrow();
        assertEquals(ArtifactStatus.TOMBSTONED, unchanged.status());

        // Should have recorded dry-run audit
        verify(auditPort).record(eq("SYSTEM"), eq("ARTIFACT_BLOB_GC"), eq("ARTIFACT_CATALOG"),
                eq("artifact"), eq("gc"), argThat(m -> Boolean.TRUE.equals(m.get("dryRun"))));
    }

    @Test
    void shouldSkipActiveArtifact() {
        repository.save(new Artifact(
                "art_active", "rj_1", "prj_1", "s3://bucket/active.mp4",
                "mp4", "1080p", 10L, null, null, ArtifactStatus.ACTIVE,
                null, Instant.now()));

        ArtifactGcService.GcResult result = gcService.runGc(1);

        assertEquals(0, result.purged());
        verify(blobStorage, never()).deleteStorageUri(anyString());
    }

    @Test
    void shouldRespectLimit() {
        for (int i = 0; i < 5; i++) {
            repository.save(new Artifact(
                    "art_limit_" + i, "rj_1", "prj_1", "s3://bucket/f" + i + ".mp4",
                    "mp4", "1080p", 10L, null, null, ArtifactStatus.TOMBSTONED,
                    Instant.now().minusSeconds(86400 * 10), Instant.now()));
        }
        when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);

        ArtifactGcService.GcResult result = gcService.runGc(1, false, 2);

        assertEquals(5, result.scanned());  // all candidates scanned
        assertEquals(2, result.purged());   // only 2 processed due to limit
    }

    @Test
    void shouldRespectGracePeriod() {
        // Tombstoned only 1 hour ago (within 7-day grace)
        repository.save(new Artifact(
                "art_recent", "rj_1", "prj_1", "s3://bucket/recent.mp4",
                "mp4", "1080p", 10L, null, null, ArtifactStatus.TOMBSTONED,
                Instant.now().minusSeconds(3600), Instant.now()));

        ArtifactGcService.GcResult result = gcService.runGc(7);

        assertEquals(0, result.purged());  // too recent, within grace period
        verify(blobStorage, never()).deleteStorageUri(anyString());
    }

    @Test
    void deleteFailureShouldBeRecordedAndContinue() {
        repository.save(new Artifact(
                "art_ok", "rj_1", "prj_1", "s3://bucket/ok.mp4",
                "mp4", "1080p", 10L, null, null, ArtifactStatus.TOMBSTONED,
                Instant.now().minusSeconds(86400 * 10), Instant.now()));
        repository.save(new Artifact(
                "art_fail", "rj_1", "prj_1", "s3://bucket/fail.mp4",
                "mp4", "1080p", 10L, null, null, ArtifactStatus.TOMBSTONED,
                Instant.now().minusSeconds(86400 * 10), Instant.now()));

        when(blobStorage.deleteStorageUri("s3://bucket/ok.mp4")).thenReturn(true);
        when(blobStorage.deleteStorageUri("s3://bucket/fail.mp4"))
                .thenThrow(new RuntimeException("S3 delete failed"));

        ArtifactGcService.GcResult result = gcService.runGc(1);

        assertEquals(1, result.purged());   // ok one succeeded
        assertEquals(1, result.failed());   // fail one recorded
        assertTrue(result.errors().stream().anyMatch(e -> e.contains("art_fail")));
    }

    @Test
    void auditShouldNotContainStorageUri() {
        repository.save(new Artifact(
                "art_audit", "rj_1", "prj_1", "s3://bucket/secret.mp4",
                "mp4", "1080p", 10L, null, null, ArtifactStatus.TOMBSTONED,
                Instant.now().minusSeconds(86400 * 10), Instant.now()));
        when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);

        gcService.runGc(1);

        verify(auditPort).record(anyString(), anyString(), anyString(),
                anyString(), anyString(), argThat(m -> {
                    String s = m.toString();
                    return !s.contains("s3://bucket") &&
                            !s.contains("secret.mp4") &&
                            m.containsKey("scanned") &&
                            m.containsKey("purged") &&
                            m.containsKey("failed");
                }));
    }
}
