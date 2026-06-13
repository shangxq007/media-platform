package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

class ArtifactLifecycleServiceTest extends PostgresTestContainerSupport {

    private ArtifactCatalogService catalogService;
    private ArtifactLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        var ds = createDataSource();
        var jdbc = new JdbcTemplate(ds);

        jdbc.execute("CREATE TABLE IF NOT EXISTS artifact_relation ("
                + "id varchar(64) primary key,"
                + "source_artifact_id varchar(64) not null,"
                + "target_artifact_id varchar(64) not null,"
                + "relation_type varchar(64) not null,"
                + "created_at timestamp not null"
                + ")");
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
        jdbc.execute("TRUNCATE TABLE artifact_relation CASCADE");
        jdbc.execute("TRUNCATE TABLE artifact CASCADE");

        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        DSLContext dsl = DSL.using(ds, SQLDialect.POSTGRES, settings);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        ArtifactCatalogRepository repository = new ArtifactCatalogRepository(dsl);
        ArtifactRelationRepository relationRepository = new ArtifactRelationRepository(dsl);
        catalogService = new ArtifactCatalogService(repository, relationRepository, registry);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        lifecycleService = new ArtifactLifecycleService(repository, catalogService, dsl, registry, events, List.of());
    }

    @Test
    void deleteCheckAllowsWhenNoReferences() {
        Artifact artifact = catalogService.registerArtifact("rj_1", "prj_1", "s3://b/out.mp4", "mp4", "1080p", 10L);
        var check = lifecycleService.deleteCheck(artifact.id());
        assertTrue(check.deletable());
    }

    @Test
    void tombstoneUpdatesStatus() {
        Artifact artifact = catalogService.registerArtifact("rj_1", "prj_1", "s3://b/out.mp4", "mp4", "1080p", 10L);
        Artifact tombstoned = lifecycleService.tombstone(artifact.id());
        assertEquals(ArtifactStatus.TOMBSTONED, tombstoned.status());
        assertTrue(tombstoned.tombstonedAt() != null);
    }

    @Test
    void tombstoneBlockedWhenRelationExists() {
        Artifact source = catalogService.registerArtifact("rj_1", "prj_1", "s3://b/a.mp4", "mp4", "1080p", 10L);
        Artifact target = catalogService.registerArtifact("rj_2", "prj_1", "s3://b/b.srt", "srt", "sub", 0L);
        catalogService.relateArtifacts(source.id(), target.id(), "HAS_SUBTITLE");
        var check = lifecycleService.deleteCheck(source.id());
        assertFalse(check.deletable());
        org.junit.jupiter.api.Assertions.assertThrows(PlatformException.class,
                () -> lifecycleService.tombstone(source.id()));
    }
}
