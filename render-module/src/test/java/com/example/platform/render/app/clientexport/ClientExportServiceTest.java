package com.example.platform.render.app.clientexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.domain.clientexport.ClientExportSession;
import com.example.platform.render.infrastructure.ExportPolicyService;
import com.example.platform.render.infrastructure.clientexport.ClientExportSessionRepository;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;

class ClientExportServiceTest {

    private ClientExportSessionRepository repository;
    private ClientExportService service;
    private ExportPolicyService exportPolicy;

    @TempDir
    java.nio.file.Path tempDir;

    @BeforeEach
    void setUp() {
        var ds = new DriverManagerDataSource();
        ds.setDriverClassName("org.h2.Driver");
        ds.setUrl("jdbc:h2:mem:cex_test_" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1");
        var jdbc = new JdbcTemplate(ds);

        jdbc.execute("""
            create table if not exists client_export_session (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                workspace_id varchar(64),
                project_id varchar(64) not null,
                user_id varchar(128),
                timeline_snapshot_id varchar(64),
                export_type varchar(32) not null default 'CLIENT_BROWSER',
                preset varchar(64),
                status varchar(32) not null default 'CREATED',
                progress int not null default 0,
                resolution varchar(32) default '1280x720',
                fps int default 30,
                format varchar(16) default 'webm',
                watermark_enabled boolean default true,
                video_bitrate int,
                audio_bitrate int,
                max_duration_sec int,
                output_uri varchar(512),
                artifact_id varchar(64),
                download_path varchar(512),
                error_code varchar(64),
                error_message varchar(1024),
                created_at timestamp not null default current_timestamp,
                updated_at timestamp not null default current_timestamp,
                expires_at timestamp
            )
        """);

        repository = new ClientExportSessionRepository(jdbc);
        exportPolicy = new ExportPolicyService();
        service = new ClientExportService(tempDir.toString(), repository, exportPolicy, null);
    }

    @Test
    void freeTierCreates720pWatermarkedSession() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", "client_720p_watermarked", "snap-1");

        assertNotNull(config.sessionId());
        assertEquals("1280x720", config.resolution());
        assertTrue(config.watermarkEnabled());
        assertEquals("CLIENT", config.renderLocation());
        assertEquals("mp4", config.format());

        var session = service.findSession(config.sessionId()).orElseThrow();
        assertEquals("CREATED", session.status());
        assertEquals("tenant-1", session.tenantId());
    }

    @Test
    void proTierCreates1080pNoWatermark() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "PRO", "pro_1080p", "snap-1");

        assertEquals("1920x1080", config.resolution());
        assertFalse(config.watermarkEnabled());
        assertEquals("SERVER", config.renderLocation());
    }

    @Test
    void freeTierCannotUsePreset() {
        assertThrows(IllegalArgumentException.class, () ->
                service.createSessionWithConfig(
                        "tenant-1", "ws-1", "proj-1", "user-1",
                        "FREE", "team_4k", "snap-1"));
    }

    @Test
    void sessionSurvivesSimulatedRestart() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        var newService = new ClientExportService(tempDir.toString(), repository, exportPolicy, null);
        var found = newService.findSession(config.sessionId());
        assertTrue(found.isPresent());
        assertEquals("CREATED", found.get().status());
    }

    @Test
    void updateProgressPersists() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        service.updateProgress(config.sessionId(), ClientExportSession.STATUS_EXPORTING, 50);

        var updated = service.findSession(config.sessionId()).orElseThrow();
        assertEquals("EXPORTING", updated.status());
        assertEquals(50, updated.progress());
    }

    @Test
    void invalidTransitionThrows() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        service.updateProgress(config.sessionId(), ClientExportSession.STATUS_EXPORTING, 50);
        service.updateProgress(config.sessionId(), ClientExportSession.STATUS_COMPLETED, 100);

        assertThrows(IllegalStateException.class, () ->
                service.updateProgress(config.sessionId(), ClientExportSession.STATUS_EXPORTING, 0));
    }

    @Test
    void tenantIsolationPreventsAccess() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        assertThrows(SecurityException.class, () ->
                service.findSessionForTenant(config.sessionId(), "tenant-2"));
    }

    @Test
    void uploadAndCompleteUsesTenantPath() throws Exception {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", "client_720p_watermarked", "snap-1");

        byte[] payload = new byte[]{1, 2, 3, 4};
        var file = new MockMultipartFile("file", "out.mp4", "video/mp4", payload);
        var completed = service.uploadAndComplete(config.sessionId(), file, 10L, null, false);

        assertEquals("COMPLETED", completed.status());
        assertTrue(completed.outputUri().contains("tenant/tenant-1"));
        assertTrue(completed.outputUri().contains("project/proj-1"));

        Path expectedFile = tempDir
                .resolve("tenant").resolve("tenant-1")
                .resolve("workspace").resolve("ws-1")
                .resolve("project").resolve("proj-1")
                .resolve("exports").resolve(config.sessionId())
                .resolve("output.mp4");
        assertTrue(Files.exists(expectedFile));
    }

    @Test
    void failSessionRecordsError() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        var failed = service.failSession(config.sessionId(), "BROWSER_CRASH", "MediaRecorder stopped");
        assertEquals("FAILED", failed.status());
        assertEquals("BROWSER_CRASH", failed.errorCode());
    }

    @Test
    void cancelSessionWorks() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        service.updateProgress(config.sessionId(), ClientExportSession.STATUS_EXPORTING, 30);
        var cancelled = service.cancelSession(config.sessionId());
        assertEquals("CANCELLED", cancelled.status());
    }

    @Test
    void cancelTerminalSessionThrows() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        service.failSession(config.sessionId(), "ERR", "test");
        assertThrows(IllegalStateException.class, () ->
                service.cancelSession(config.sessionId()));
    }

    @Test
    void listByTenantRespectsIsolation() {
        service.createSessionWithConfig("tenant-1", "ws-1", "proj-1", "user-1", "FREE", null, null);
        service.createSessionWithConfig("tenant-1", "ws-1", "proj-2", "user-1", "FREE", null, null);
        service.createSessionWithConfig("tenant-2", "ws-2", "proj-3", "user-2", "FREE", null, null);

        assertEquals(2, service.listByTenant("tenant-1", 100, 0).size());
        assertEquals(1, service.listByTenant("tenant-2", 100, 0).size());
    }

    @Test
    void listByTenantAndProject() {
        service.createSessionWithConfig("tenant-1", "ws-1", "proj-1", "user-1", "FREE", null, null);
        service.createSessionWithConfig("tenant-1", "ws-1", "proj-1", "user-1", "FREE", null, null);
        service.createSessionWithConfig("tenant-1", "ws-1", "proj-2", "user-1", "FREE", null, null);

        assertEquals(2, service.listByTenantAndProject("tenant-1", "proj-1", 100, 0).size());
    }

    @Test
    void exportConfigIncludesAvailablePresets() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        assertFalse(config.availablePresets().isEmpty());
        assertTrue(config.availablePresets().stream()
                .anyMatch(p -> p.get("name").equals("free_720p_watermarked")));
    }
}
