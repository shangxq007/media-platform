package com.example.platform.render.infrastructure.clientexport;

import com.example.platform.render.domain.clientexport.ClientExportSession;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class ClientExportSessionRepository {

    private static final String TABLE = "client_export_session";

    private static final String ALL_COLUMNS =
            "id, tenant_id, workspace_id, project_id, user_id, timeline_snapshot_id, " +
            "export_type, preset, status, progress, resolution, fps, format, watermark_enabled, " +
            "video_bitrate, audio_bitrate, max_duration_sec, output_uri, artifact_id, download_path, " +
            "error_code, error_message, created_at, updated_at, expires_at";

    private static final String INSERT_COLUMNS =
            "id, tenant_id, workspace_id, project_id, user_id, timeline_snapshot_id, " +
            "export_type, preset, status, progress, resolution, fps, format, watermark_enabled, " +
            "video_bitrate, audio_bitrate, max_duration_sec, output_uri, artifact_id, download_path, " +
            "error_code, error_message, created_at, updated_at, expires_at";

    private static final RowMapper<ClientExportSession> ROW_MAPPER = ClientExportSessionRepository::mapRow;

    private final JdbcTemplate jdbc;

    public ClientExportSessionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public ClientExportSession insert(ClientExportSession s) {
        jdbc.update(
                "insert into " + TABLE + " (" + INSERT_COLUMNS + ") values (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",
                s.id(), s.tenantId(), s.workspaceId(),
                s.projectId(), s.userId(), s.timelineSnapshotId(),
                s.exportType(), s.preset(), s.status(),
                s.progress(), s.resolution(), s.fps(),
                s.format(), s.watermarkEnabled(),
                s.videoBitrate(), s.audioBitrate(),
                s.maxDurationSec(), s.outputUri(),
                s.artifactId(), s.downloadPath(),
                s.errorCode(), s.errorMessage(),
                ts(s.createdAt()), ts(s.updatedAt()), ts(s.expiresAt()));
        return s;
    }

    public Optional<ClientExportSession> findById(String id) {
        List<ClientExportSession> results = jdbc.query(
                "select " + ALL_COLUMNS + " from " + TABLE + " where id = ?", ROW_MAPPER, id);
        return results.stream().findFirst();
    }

    public List<ClientExportSession> findByTenant(String tenantId, int limit, int offset) {
        return jdbc.query(
                "select " + ALL_COLUMNS + " from " + TABLE + " where tenant_id = ? order by created_at desc limit ? offset ?",
                ROW_MAPPER, tenantId, limit, offset);
    }

    public List<ClientExportSession> findByTenantAndProject(String tenantId, String projectId, int limit, int offset) {
        return jdbc.query(
                "select " + ALL_COLUMNS + " from " + TABLE + " where tenant_id = ? and project_id = ? order by created_at desc limit ? offset ?",
                ROW_MAPPER, tenantId, projectId, limit, offset);
    }

    public List<ClientExportSession> findActiveByTenant(String tenantId) {
        return jdbc.query(
                "select " + ALL_COLUMNS + " from " + TABLE + " where tenant_id = ? and status in (?,?,?) order by created_at desc",
                ROW_MAPPER, tenantId,
                ClientExportSession.STATUS_CREATED,
                ClientExportSession.STATUS_PREPARING,
                ClientExportSession.STATUS_EXPORTING);
    }

    public void updateStatus(String id, String status, int progress,
                              String outputUri, String artifactId, String downloadPath,
                              String errorCode, String errorMessage) {
        jdbc.update(
                "update " + TABLE + " set status=?, progress=?, output_uri=?, artifact_id=?, " +
                "download_path=?, error_code=?, error_message=?, updated_at=? where id=?",
                status, progress, outputUri, artifactId, downloadPath, errorCode, errorMessage,
                ts(Instant.now()), id);
    }

    public void updateProgress(String id, String status, int progress) {
        jdbc.update(
                "update " + TABLE + " set status=?, progress=?, updated_at=? where id=?",
                status, progress, ts(Instant.now()), id);
    }

    public int deleteExpired(Instant before) {
        return jdbc.update(
                "delete from " + TABLE + " where expires_at is not null and expires_at < ? " +
                "and status in (?,?,?)",
                ts(before),
                ClientExportSession.STATUS_COMPLETED,
                ClientExportSession.STATUS_FAILED,
                ClientExportSession.STATUS_CANCELLED);
    }

    private static ClientExportSession mapRow(ResultSet rs, int rowNum) throws SQLException {
        return new ClientExportSession(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("workspace_id"),
                rs.getString("project_id"),
                rs.getString("user_id"),
                rs.getString("timeline_snapshot_id"),
                rs.getString("export_type"),
                rs.getString("preset"),
                rs.getString("status"),
                rs.getInt("progress"),
                rs.getString("resolution"),
                rs.getInt("fps"),
                rs.getString("format"),
                rs.getBoolean("watermark_enabled"),
                getNullableInt(rs, "video_bitrate"),
                getNullableInt(rs, "audio_bitrate"),
                getNullableInt(rs, "max_duration_sec"),
                rs.getString("output_uri"),
                rs.getString("artifact_id"),
                rs.getString("download_path"),
                rs.getString("error_code"),
                rs.getString("error_message"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at")),
                toInstant(rs.getTimestamp("expires_at"))
        );
    }

    private static Integer getNullableInt(ResultSet rs, String col) throws SQLException {
        int v = rs.getInt(col);
        return rs.wasNull() ? null : v;
    }

    private static Timestamp ts(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static Instant toInstant(Timestamp ts) {
        return ts != null ? ts.toInstant() : null;
    }
}
