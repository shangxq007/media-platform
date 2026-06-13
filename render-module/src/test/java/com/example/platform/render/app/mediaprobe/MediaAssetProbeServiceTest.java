package com.example.platform.render.app.mediaprobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.media.MediaProbePort;
import com.example.platform.shared.media.MediaProbePort.MediaProbeResult;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.List;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class MediaAssetProbeServiceTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private MediaAssetProbeService service;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
        var jdbc = new JdbcTemplate(dataSource);

        MediaProbePort stubProbePort = new MediaProbePort() {
            @Override
            public MediaProbeResult probe(String assetUri) {
                return new MediaProbeResult(
                        assetUri, true, "mp4", 10_000_000, 60000,
                        1920, 1080, 30.0, "h264", "aac",
                        44100, 2, 0, "bt709", 8_000_000, false, 2,
                        true, false, List.of(), null);
            }

            @Override
            public MediaProbeResult probe(String assetUri, String storageRoot) {
                return probe(assetUri);
            }
        };

        service = new MediaAssetProbeService(stubProbePort, jdbc);
    }

    @Test
    void probeAndPersistSavesMetadata() {
        var result = service.probeAndPersist(
                "tenant-1", "proj-1", "asset-1",
                "/tmp/test-video.mp4");

        assertNotNull(result.metadataId());
        assertTrue(result.probeResult().valid());
        assertEquals(1920, result.probeResult().width());
        assertEquals(1080, result.probeResult().height());
        assertTrue(result.probeResult().clientExportCompatible());
        assertFalse(result.probeResult().normalizeRequired());
    }

    @Test
    void getLatestProbeReturnsPersistedData() {
        service.probeAndPersist("tenant-1", "proj-1", "asset-1", "/tmp/test.mp4");

        var probe = service.getLatestProbe("tenant-1", "asset-1");

        assertNotNull(probe);
        assertTrue(probe.valid());
        assertEquals("h264", probe.videoCodec());
        assertTrue(probe.hasUsableAudio());
    }

    @Test
    void getLatestProbeReturnsNullForUnknown() {
        var probe = service.getLatestProbe("tenant-1", "nonexistent-asset");
        assertEquals(null, probe);
    }
}
