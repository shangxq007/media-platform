package com.example.platform.storage.app;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private ArtifactRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "artifacttest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table artifact ("
                    + "id varchar(64) primary key,"
                    + "render_job_id varchar(64) not null,"
                    + "project_id varchar(64) not null,"
                    + "storage_uri text not null,"
                    + "format varchar(32),"
                    + "resolution varchar(32),"
                    + "duration bigint,"
                    + "created_at timestamp not null"
                    + ")");
        }

        repository = new ArtifactRepository(dsl);
    }

    @Test
    void saveAndFindById() {
        ArtifactRepository.ArtifactMetadata metadata = new ArtifactRepository.ArtifactMetadata(
                "art_1", "job_1", "prj_1", "bucket/key.mp4", "mp4", "1920x1080", 30L, Instant.now());
        repository.save(metadata);

        Optional<ArtifactRepository.ArtifactMetadata> found = repository.findById("art_1");
        assertTrue(found.isPresent());
        assertEquals("job_1", found.get().renderJobId());
        assertEquals("prj_1", found.get().projectId());
        assertEquals("bucket/key.mp4", found.get().storageUri());
    }

    @Test
    void findByIdReturnsEmptyForUnknown() {
        Optional<ArtifactRepository.ArtifactMetadata> found = repository.findById("art_nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByRenderJobIdReturnsMatchingArtifacts() {
        repository.save(new ArtifactRepository.ArtifactMetadata("art_1", "job_1", "prj_1", "b/k1.mp4", "mp4", "1920x1080", 30L, Instant.now()));
        repository.save(new ArtifactRepository.ArtifactMetadata("art_2", "job_1", "prj_1", "b/k2.mp4", "mp4", "1920x1080", 60L, Instant.now()));
        repository.save(new ArtifactRepository.ArtifactMetadata("art_3", "job_2", "prj_2", "b/k3.mp4", "mp4", "3840x2160", 120L, Instant.now()));

        List<ArtifactRepository.ArtifactMetadata> job1Artifacts = repository.findByRenderJobId("job_1");
        assertEquals(2, job1Artifacts.size());

        List<ArtifactRepository.ArtifactMetadata> job2Artifacts = repository.findByRenderJobId("job_2");
        assertEquals(1, job2Artifacts.size());
        assertEquals("art_3", job2Artifacts.get(0).id());
    }

    @Test
    void findByProjectIdReturnsMatchingArtifacts() {
        repository.save(new ArtifactRepository.ArtifactMetadata("art_1", "job_1", "prj_1", "b/k1.mp4", "mp4", "1920x1080", 30L, Instant.now()));
        repository.save(new ArtifactRepository.ArtifactMetadata("art_2", "job_2", "prj_2", "b/k2.mp4", "mp4", "1920x1080", 60L, Instant.now()));

        List<ArtifactRepository.ArtifactMetadata> prj1Artifacts = repository.findByProjectId("prj_1");
        assertEquals(1, prj1Artifacts.size());
        assertEquals("art_1", prj1Artifacts.get(0).id());
    }
}
