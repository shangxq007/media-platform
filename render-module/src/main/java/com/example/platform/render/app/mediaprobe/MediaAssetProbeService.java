package com.example.platform.render.app.mediaprobe;

import com.example.platform.shared.Ids;
import com.example.platform.shared.media.MediaProbePort;
import com.example.platform.shared.media.MediaProbePort.MediaProbeResult;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class MediaAssetProbeService {

    private static final Logger log = LoggerFactory.getLogger(MediaAssetProbeService.class);

    private final MediaProbePort probePort;
    private final JdbcTemplate jdbc;

    public MediaAssetProbeService(MediaProbePort probePort, JdbcTemplate jdbc) {
        this.probePort = probePort;
        this.jdbc = jdbc;
    }

    public ProbeAndPersistResult probeAndPersist(
            String tenantId, String projectId, String assetId, String assetUri) {

        log.info("Probing asset: tenant={} project={} asset={} uri={}",
                tenantId, projectId, assetId, assetUri);

        MediaProbeResult result = probePort.probe(assetUri);

        String metadataId = Ids.newId("pmd");
        jdbc.update("""
                insert into media_asset_metadata
                (id, tenant_id, project_id, asset_id, asset_uri,
                 valid, container, file_size_bytes, duration_ms,
                 width, height, fps, video_codec, audio_codec,
                 audio_sample_rate, audio_channels, has_audio,
                 rotation, color_space, bitrate, is_vfr, stream_count,
                 client_export_compatible, normalize_required,
                 warnings, error_message, probed_at)
                values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)
                """,
                metadataId, tenantId, projectId, assetId, assetUri,
                result.valid(), result.container(), result.fileSizeBytes(), result.durationMs(),
                result.width(), result.height(), result.fps(), result.videoCodec(), result.audioCodec(),
                result.audioSampleRate(), result.audioChannels(), result.hasUsableAudio(),
                result.rotation(), result.colorSpace(), result.bitrate(), result.isVfr(), result.streamCount(),
                result.clientExportCompatible(), result.normalizeRequired(),
                String.join("|", result.warnings()), result.error(),
                OffsetDateTime.now());

        log.info("Probe result: asset={} valid={} {}x{} codec={} clientExport={} normalize={}",
                assetId, result.valid(), result.width(), result.height(),
                result.videoCodec(), result.clientExportCompatible(), result.normalizeRequired());

        return new ProbeAndPersistResult(assetId, result, metadataId);
    }

    public MediaProbeResult getLatestProbe(String tenantId, String assetId) {
        var rows = jdbc.queryForList("""
                select * from media_asset_metadata
                where tenant_id = ? and asset_id = ?
                order by probed_at desc limit 1
                """, tenantId, assetId);

        if (rows.isEmpty()) {
            return null;
        }

        var row = rows.get(0);
        return new MediaProbeResult(
                (String) row.get("asset_uri"),
                (Boolean) row.get("valid"),
                (String) row.get("container"),
                ((Number) row.get("file_size_bytes")).longValue(),
                ((Number) row.get("duration_ms")).doubleValue(),
                ((Number) row.get("width")).intValue(),
                ((Number) row.get("height")).intValue(),
                ((Number) row.get("fps")).doubleValue(),
                (String) row.get("video_codec"),
                (String) row.get("audio_codec"),
                ((Number) row.get("audio_sample_rate")).intValue(),
                ((Number) row.get("audio_channels")).intValue(),
                ((Number) row.get("rotation")).intValue(),
                (String) row.get("color_space"),
                ((Number) row.get("bitrate")).longValue(),
                (Boolean) row.get("is_vfr"),
                ((Number) row.get("stream_count")).intValue(),
                (Boolean) row.get("client_export_compatible"),
                (Boolean) row.get("normalize_required"),
                List.of(((String) row.get("warnings")).split("\\|")),
                (String) row.get("error_message"));
    }

    public record ProbeAndPersistResult(
            String assetId,
            MediaProbeResult probeResult,
            String metadataId) {}
}
