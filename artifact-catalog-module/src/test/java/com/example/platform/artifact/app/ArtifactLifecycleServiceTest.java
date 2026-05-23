package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

class ArtifactLifecycleServiceTest {

    private ArtifactCatalogService catalogService;
    private ArtifactLifecycleService lifecycleService;

    @BeforeEach
    void setUp() throws Exception {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:artifactLifecycle;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS artifact_relation");
            stmt.execute("DROP TABLE IF EXISTS artifact");
            stmt.execute("DROP TABLE IF EXISTS render_job");
            stmt.execute("CREATE TABLE artifact_relation ("
                    + "id varchar(64) primary key,"
                    + "source_artifact_id varchar(64) not null,"
                    + "target_artifact_id varchar(64) not null,"
                    + "relation_type varchar(64) not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("CREATE TABLE render_job ("
                    + "id varchar(64) primary key,"
                    + "artifact_uri text"
                    + ")");
            stmt.execute("CREATE TABLE artifact ("
                    + "id varchar(64) primary key,"
                    + "render_job_id varchar(64) not null,"
                    + "project_id varchar(64) not null,"
                    + "storage_uri text not null,"
                    + "format varchar(32),"
                    + "resolution varchar(32),"
                    + "duration bigint,"
                    + "status varchar(32) not null default 'ACTIVE',"
                    + "tombstoned_at timestamp,"
                    + "created_at timestamp not null"
                    + ")");
        }
        DSLContext dsl = DSL.using(ds, SQLDialect.H2);
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
