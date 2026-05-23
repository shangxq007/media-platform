package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.springframework.context.ApplicationEventPublisher;

import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactStatus;
import com.example.platform.artifact.infrastructure.ArtifactGcProperties;
import com.example.platform.shared.web.ErrorCodeRegistry;
import com.example.platform.storage.domain.BlobStorage;
import java.time.Instant;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.Statement;

class ArtifactGcServiceTest {

    private ArtifactCatalogRepository repository;
    private ArtifactGcService gcService;
    private BlobStorage blobStorage;

    @BeforeEach
    void setUp() throws Exception {
        org.h2.jdbcx.JdbcDataSource ds = new org.h2.jdbcx.JdbcDataSource();
        ds.setURL("jdbc:h2:mem:artifactGc;MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute("DROP TABLE IF EXISTS artifact_relation");
            stmt.execute("DROP TABLE IF EXISTS render_job");
            stmt.execute("DROP TABLE IF EXISTS artifact");
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
        repository = new ArtifactCatalogRepository(dsl);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        ArtifactCatalogService catalog = new ArtifactCatalogService(repository, null, registry);
        ApplicationEventPublisher events = mock(org.springframework.context.ApplicationEventPublisher.class);
        ArtifactLifecycleService lifecycle =
                new ArtifactLifecycleService(repository, catalog, dsl, registry, events, java.util.List.of());
        blobStorage = mock(BlobStorage.class);
        ArtifactGcProperties props = new ArtifactGcProperties();
        props.setRetentionDays(1);
        props.setBatchSize(10);
        gcService = new ArtifactGcService(repository, lifecycle, blobStorage, props);
    }

    @Test
    void purgesOldTombstonedArtifacts() {
        Artifact artifact = repository.save(new Artifact(
                "art_gc1", "rj_1", "prj_1", "s3://bucket/old.mp4",
                "mp4", "1080p", 10L, ArtifactStatus.TOMBSTONED,
                Instant.now().minusSeconds(86400 * 10), Instant.now()));
        when(blobStorage.deleteStorageUri(anyString())).thenReturn(true);

        ArtifactGcService.GcResult result = gcService.runGc(1);
        assertEquals(1, result.purged());

        Artifact updated = repository.findById(artifact.id()).orElseThrow();
        assertEquals(ArtifactStatus.PURGED, updated.status());
        verify(blobStorage).deleteStorageUri("s3://bucket/old.mp4");
    }
}
