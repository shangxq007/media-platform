package com.example.platform.artifact.app;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactRelation;
import com.example.platform.shared.test.PostgresTestContainer;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.shared.web.PlatformException;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ArtifactCatalogServiceTest extends PostgresTestContainer {

    private ArtifactCatalogService service;
    private ArtifactCatalogRepository repository;
    private DSLContext dsl;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(POSTGRES_URL);
        ds.setUsername(POSTGRES_USERNAME);
        ds.setPassword(POSTGRES_PASSWORD);

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
        dsl = DSL.using(ds, SQLDialect.POSTGRES, settings);
        repository = new ArtifactCatalogRepository(dsl);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        ArtifactRelationRepository relationRepository = new ArtifactRelationRepository(dsl);
        service = new ArtifactCatalogService(repository, relationRepository, registry);
    }

    @Test
    void registerArtifactReturnsArtifactWithGeneratedId() {
        Artifact artifact = service.registerArtifact("rj_123", "prj_456",
                "s3://bucket/output.mp4", "mp4", "1920x1080", 30L);
        assertNotNull(artifact.id());
        assertTrue(artifact.id().startsWith("art_"));
        assertEquals("rj_123", artifact.renderJobId());
        assertEquals("prj_456", artifact.projectId());
        assertEquals("s3://bucket/output.mp4", artifact.storageUri());
        assertEquals("mp4", artifact.format());
        assertEquals("1920x1080", artifact.resolution());
        assertEquals(30L, artifact.duration());
        assertNotNull(artifact.createdAt());
    }

    @Test
    void findArtifactReturnsArtifactWhenExists() {
        Artifact created = service.registerArtifact("rj_1", "prj_1", "uri", "mp4", "1080p", 10L);
        Optional<Artifact> found = service.findArtifact(created.id());
        assertTrue(found.isPresent());
        assertEquals("rj_1", found.get().renderJobId());
    }

    @Test
    void findArtifactReturnsEmptyWhenNotFound() {
        Optional<Artifact> found = service.findArtifact("art-nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void listArtifactsReturnsAllRegistered() {
        service.registerArtifact("rj_1", "prj_1", "uri1", "mp4", "1080p", 10L);
        service.registerArtifact("rj_2", "prj_1", "uri2", "mov", "720p", 20L);
        assertEquals(2, service.listArtifacts().size());
    }

    @Test
    void listArtifactsReturnsEmptyWhenNone() {
        assertTrue(service.listArtifacts().isEmpty());
    }

    @Test
    void listArtifactsByProjectFiltersCorrectly() {
        service.registerArtifact("rj_1", "prj_A", "uri1", "mp4", "1080p", 10L);
        service.registerArtifact("rj_2", "prj_B", "uri2", "mp4", "720p", 20L);
        service.registerArtifact("rj_3", "prj_A", "uri3", "mov", "4k", 30L);

        List<Artifact> prjA = service.listArtifactsByProject("prj_A");
        assertEquals(2, prjA.size());
        List<Artifact> prjB = service.listArtifactsByProject("prj_B");
        assertEquals(1, prjB.size());
    }

    @Test
    void listArtifactsByRenderJobFiltersCorrectly() {
        service.registerArtifact("rj_1", "prj_1", "uri1", "mp4", "1080p", 10L);
        service.registerArtifact("rj_2", "prj_1", "uri2", "mp4", "720p", 20L);

        List<Artifact> jobs = service.listArtifactsByRenderJob("rj_1");
        assertEquals(1, jobs.size());
        assertEquals("rj_1", jobs.get(0).renderJobId());
    }

    @Test
    void relateArtifactsCreatesRelation() {
        Artifact source = service.registerArtifact("rj_1", "prj_1", "uri1", "mp4", "1080p", 10L);
        Artifact target = service.registerArtifact("rj_2", "prj_1", "uri2", "srt", "subtitle", 0L);

        ArtifactRelation relation = service.relateArtifacts(source.id(), target.id(), "HAS_SUBTITLE");
        assertNotNull(relation.id());
        assertTrue(relation.id().startsWith("rel_"));
        assertEquals(source.id(), relation.sourceId());
        assertEquals(target.id(), relation.targetId());
        assertEquals("HAS_SUBTITLE", relation.relationType());
    }

    @Test
    void relateArtifactsThrowsForUnknownSource() {
        Artifact target = service.registerArtifact("rj_1", "prj_1", "uri", "mp4", "1080p", 10L);
        assertThrows(PlatformException.class,
                () -> service.relateArtifacts("art-nonexistent", target.id(), "DEPENDS_ON"));
    }

    @Test
    void relateArtifactsThrowsForUnknownTarget() {
        Artifact source = service.registerArtifact("rj_1", "prj_1", "uri", "mp4", "1080p", 10L);
        assertThrows(PlatformException.class,
                () -> service.relateArtifacts(source.id(), "art-nonexistent", "DEPENDS_ON"));
    }

    @Test
    void overviewReturnsModuleInfo() {
        Map<String, Object> overview = service.overview();
        assertEquals("artifact-catalog-module", overview.get("module"));
        assertEquals("active", overview.get("status"));
        assertEquals(true, overview.get("persistent"));
    }

    @Test
    void overviewIncludesCounts() {
        service.registerArtifact("rj_1", "prj_1", "uri1", "mp4", "1080p", 10L);
        service.registerArtifact("rj_2", "prj_1", "uri2", "mp4", "720p", 20L);
        Map<String, Object> overview = service.overview();
        assertEquals(2, overview.get("artifactCount"));
    }

    @Test
    void artifactStatusEnumValues() {
        com.example.platform.artifact.domain.ArtifactStatus[] values =
                com.example.platform.artifact.domain.ArtifactStatus.values();
        assertTrue(values.length >= 3);
        assertEquals(com.example.platform.artifact.domain.ArtifactStatus.ACTIVE,
                com.example.platform.artifact.domain.ArtifactStatus.valueOf("ACTIVE"));
    }
}
