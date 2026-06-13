package com.example.platform.storage.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.testsupport.StorageTestSchemaFixture;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ArtifactRepositoryTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private ArtifactRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        StorageTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        StorageTestSchemaFixture.truncate(dsl);
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
