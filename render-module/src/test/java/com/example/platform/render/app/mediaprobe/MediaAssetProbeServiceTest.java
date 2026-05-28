package com.example.platform.render.app.mediaprobe;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.media.MediaProbePort;
import com.example.platform.shared.media.MediaProbePort.MediaProbeResult;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class MediaAssetProbeServiceTest {

    private MediaAssetProbeService service;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:probe_test_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);

        jdbc.execute("""
            create table if not exists media_asset_metadata (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                project_id varchar(64) not null,
                asset_id varchar(64) not null,
                asset_uri varchar(1024) not null,
                valid boolean not null default false,
                container varchar(32),
                file_size_bytes bigint default 0,
                duration_ms double default 0,
                width int default 0,
                height int default 0,
                fps double default 0,
                video_codec varchar(64),
                audio_codec varchar(64),
                audio_sample_rate int default 0,
                audio_channels int default 0,
                has_audio boolean default false,
                rotation int default 0,
                color_space varchar(32),
                bitrate bigint default 0,
                is_vfr boolean default false,
                stream_count int default 0,
                client_export_compatible boolean default false,
                normalize_required boolean default true,
                warnings varchar(4096),
                error_message varchar(1024),
                probed_at timestamp not null default current_timestamp
            )
        """);

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
