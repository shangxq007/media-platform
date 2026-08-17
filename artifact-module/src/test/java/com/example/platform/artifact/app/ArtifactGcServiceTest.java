package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.infrastructure.ArtifactGcProperties;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.shared.audit.AuditPort;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.storage.domain.BlobStorage;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
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

/**
 * GCR-2: Artifact GC respects historical pin protection — pinned artifacts are
 * never purged (HISTORICAL_PIN_GC_BYPASS_COUNT = 0).
 */
class ArtifactGcServiceTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;

    private ArtifactRepository artifactRepository;
    private ArtifactPinRepository pinRepository;
    private ArtifactGcService gcService;
    private BlobStorage blobStorage;

    @BeforeAll
    static void createDataSourceFixture() {
        dataSource = createDataSource();
        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES, settings);
        com.example.platform.artifact.testutil.ArtifactSchemaFixture.createCanonicalTables(
                new org.springframework.jdbc.core.JdbcTemplate(dataSource));
        dsl.execute("CREATE TABLE IF NOT EXISTS artifact_relation ("
                + "id varchar(64) primary key,"
                + "source_artifact_id varchar(64) not null,"
                + "target_artifact_id varchar(64) not null,"
                + "relation_type varchar(64) not null,"
                + "created_at timestamp not null"
                + ")");
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }
    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE artifact_pin CASCADE");
        dsl.execute("TRUNCATE TABLE artifact_replica CASCADE");
        dsl.execute("TRUNCATE TABLE artifact CASCADE");

        artifactRepository = new ArtifactRepository(dsl);
        pinRepository = new ArtifactPinRepository(dsl);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        com.example.platform.artifact.app.ArtifactCatalogRepository catalogRepo =
                new com.example.platform.artifact.app.ArtifactCatalogRepository(dsl);
        com.example.platform.artifact.app.ArtifactRelationRepository relationRepo =
                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl);
        com.example.platform.artifact.infrastructure.JooqArtifactCommitService commitService =
                new com.example.platform.artifact.infrastructure.JooqArtifactCommitService(
                        artifactRepository, relationRepo, dsl);
        ArtifactCatalogService catalog =
                new ArtifactCatalogService(catalogRepo, relationRepo, commitService, registry);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        ArtifactLifecycleService lifecycle = new ArtifactLifecycleService(
                catalogRepo, catalog, artifactRepository, pinRepository, dsl, registry, events, java.util.List.of());
        blobStorage = mock(BlobStorage.class);
        AuditPort auditPort = mock(AuditPort.class);
        ArtifactGcProperties props = new ArtifactGcProperties();
        props.setRetentionDays(1);
        props.setBatchSize(10);
        gcService = new ArtifactGcService(artifactRepository, lifecycle, blobStorage, props, auditPort);
    }

    private void insertTombstonedArtifact(String id, Instant tombstonedAt) {
        artifactRepository.insertRaw(new ArtifactId(id), "t1", ContentDigest.sha256("a".repeat(64)),
                10L, ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, ArtifactState.DELETING,
                tombstonedAt);
    }

    @Test
    void purgesOldUnpinnedTombstonedArtifacts() {
        insertTombstonedArtifact("art_gc1", Instant.now().minusSeconds(86400 * 10));

        ArtifactGcService.GcResult result = gcService.runGc(1);
        assertEquals(1, result.purged());
        assertEquals(0, result.failed());
        // Logical purge marks the canonical Artifact DELETED (row remains as history).
        assertTrue(artifactRepository.findById("t1", new ArtifactId("art_gc1"))
                .map(a -> a.state() == ArtifactState.DELETED).orElse(false));
    }

    @Test
    void skipsPinnedArtifacts() {
        insertTombstonedArtifact("art_pinned", Instant.now().minusSeconds(86400 * 10));
        pinRepository.insert("pin_1", "trev_1", "prj_1", "art_pinned",
                ContentDigest.sha256("a".repeat(64)), Instant.now());

        ArtifactGcService.GcResult result = gcService.runGc(1);
        assertEquals(0, result.purged());
        assertEquals(1, result.skipped());
        verify(blobStorage, never()).deleteStorageUri(anyString());
        // Pinned artifact must remain present (not deleted).
        assertTrue(artifactRepository.findById("t1", new ArtifactId("art_pinned")).isPresent());
    }

    @Test
    void keepsYoungTombstonedArtifacts() {
        insertTombstonedArtifact("art_young", Instant.now().minusSeconds(60));

        ArtifactGcService.GcResult result = gcService.runGc(1, false, 10);
        // Young tombstoned artifact is outside the retention window: not a GC candidate.
        assertEquals(0, result.purged());
        assertEquals(0, result.scanned());
        assertTrue(artifactRepository.findById("t1", new ArtifactId("art_young"))
                .map(a -> a.state() == ArtifactState.DELETING).orElse(false));
    }
}
