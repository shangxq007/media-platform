package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.platform.artifact.domain.ArtifactCatalogEntry;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
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

import java.util.List;

class ArtifactLifecycleServiceTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private static ArtifactCatalogRepository repository;
    private static ArtifactRelationRepository relationRepository;
    private static ArtifactCatalogService catalogService;
    private static ArtifactLifecycleService lifecycleService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        // GCR-2: canonical Artifact schema (artifact + artifact_replica + artifact_pin)
        com.example.platform.artifact.testutil.ArtifactSchemaFixture.createCanonicalTables(jdbc);
        jdbc.execute("CREATE TABLE IF NOT EXISTS artifact_relation ("
                + "id varchar(64) primary key,"
                + "source_artifact_id varchar(64) not null,"
                + "target_artifact_id varchar(64) not null,"
                + "relation_type varchar(64) not null,"
                + "created_at timestamp not null"
                + ")");

        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES, settings);
        repository = new ArtifactCatalogRepository(dsl);
        relationRepository = new ArtifactRelationRepository(dsl);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        com.example.platform.artifact.infrastructure.ArtifactRepository canonicalRepo =
                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl);
        com.example.platform.artifact.infrastructure.ArtifactPinRepository pinRepo =
                new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl);
        com.example.platform.artifact.infrastructure.JooqArtifactCommitService commitService =
                new com.example.platform.artifact.infrastructure.JooqArtifactCommitService(
                        canonicalRepo, relationRepository, dsl);
        catalogService = new ArtifactCatalogService(repository, relationRepository, commitService, registry);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        lifecycleService = new ArtifactLifecycleService(
                repository, catalogService, canonicalRepo, pinRepo, dsl, registry, events, List.of());
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        // Clean up before each test
        dsl.execute("TRUNCATE TABLE artifact_relation CASCADE");
        dsl.execute("TRUNCATE TABLE artifact CASCADE");
    }

    @Test
    void deleteCheckAllowsWhenNoReferences() {
        ArtifactCatalogEntry artifact = catalogService.registerArtifact("rj_1", "prj_1", "s3://b/out.mp4", "mp4", "1080p", 10L, 100L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        var check = lifecycleService.deleteCheck(artifact.id());
        assertTrue(check.deletable());
    }

    @Test
    void tombstoneUpdatesStatus() {
        ArtifactCatalogEntry artifact = catalogService.registerArtifact("rj_1", "prj_1", "s3://b/out.mp4", "mp4", "1080p", 10L, 100L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ArtifactCatalogEntry tombstoned = lifecycleService.tombstone(artifact.id());
        assertEquals(ArtifactStatus.TOMBSTONED, tombstoned.status());
        assertTrue(tombstoned.tombstonedAt() != null);
    }

    @Test
    void tombstoneBlockedWhenRelationExists() {
        ArtifactCatalogEntry source = catalogService.registerArtifact("rj_1", "prj_1", "s3://b/a.mp4", "mp4", "1080p", 10L, 100L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        ArtifactCatalogEntry target = catalogService.registerArtifact("rj_2", "prj_1", "s3://b/b.srt", "srt", "sub", 0L, 100L, "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa");
        catalogService.relateArtifacts(source.id(), target.id(), "HAS_SUBTITLE");
        var check = lifecycleService.deleteCheck(source.id());
        assertFalse(check.deletable());
        org.junit.jupiter.api.Assertions.assertThrows(PlatformException.class,
                () -> lifecycleService.tombstone(source.id()));
    }
}
