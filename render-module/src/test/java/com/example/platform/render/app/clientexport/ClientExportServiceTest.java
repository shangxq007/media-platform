package com.example.platform.render.app.clientexport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.platform.render.domain.clientexport.ClientExportSession;
import com.example.platform.render.infrastructure.ExportPolicyService;
import com.example.platform.render.infrastructure.clientexport.ClientExportSessionRepository;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.commercial.CommercialAdmissionPort;
import com.example.platform.shared.commercial.CommercialDecision;
import com.example.platform.shared.commercial.CommercialDecisionReason;
import com.example.platform.shared.authorization.AuthorizationDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;

class ClientExportServiceTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private ClientExportSessionRepository repository;
    private ClientExportService service;
    private ExportPolicyService exportPolicy;
    private CommercialAdmissionPort commercialAdmission;

    @TempDir
    java.nio.file.Path tempDir;

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
        repository = new ClientExportSessionRepository(jdbc);
        exportPolicy = new ExportPolicyService();
        commercialAdmission = mock(CommercialAdmissionPort.class);
        when(commercialAdmission.decide(any())).thenAnswer(invocation -> {
            var request = invocation.getArgument(0, com.example.platform.shared.commercial.CommercialAdmissionRequest.class);
            boolean allowed = !request.entitlementKey().endsWith("team_4k");
            return new CommercialDecision(request.principal(), request.action(), allowed,
                    allowed ? CommercialDecisionReason.ALLOWED : CommercialDecisionReason.NOT_ENTITLED,
                    java.util.List.of(), "test-v1", request.traceId(), request.decidedAt());
        });
        service = new ClientExportService(
                tempDir.toString(), repository, exportPolicy, commercialAdmission, null);
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
    void unresolvedServerProviderPresetPlansServerLocationWithoutNpe() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "PRO", "pro_1080p", "snap-1");

        assertEquals("1920x1080", config.resolution());
        assertFalse(config.watermarkEnabled());
        assertEquals("SERVER", config.renderLocation());
        assertTrue(config.availablePresets().stream()
                .anyMatch(p -> p.get("name").equals("pro_1080p")
                        && p.get("renderLocation").equals("SERVER")));
    }

    @Test
    void commercialAdmissionDenialRejectsPresetRegardlessOfTierLabel() {
        assertThrows(IllegalArgumentException.class, () ->
                service.createSessionWithConfig(
                        "tenant-1", "ws-1", "proj-1", "user-1",
                        "ENTERPRISE", "team_4k", "snap-1"));
    }

    @Test
    void sessionSurvivesSimulatedRestart() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        var newService = new ClientExportService(
                tempDir.toString(), repository, exportPolicy, commercialAdmission, null);
        var found = newService.findSession(config.sessionId());
        assertTrue(found.isPresent());
        assertEquals("CREATED", found.get().status());
    }

    @Test
    void clientProgressCannotMutateExecutionTruth() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        AuthorizationDeniedException failure = assertThrows(AuthorizationDeniedException.class, () ->
                service.updateProgress(config.sessionId(), ClientExportSession.STATUS_EXPORTING, 50));

        var updated = service.findSession(config.sessionId()).orElseThrow();
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        assertEquals("CREATED", updated.status());
        assertEquals(0, updated.progress());
    }

    @Test
    void clientCannotAssertCompletedStatusThroughProgress() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        AuthorizationDeniedException failure = assertThrows(AuthorizationDeniedException.class, () ->
                service.updateProgress(config.sessionId(), ClientExportSession.STATUS_COMPLETED, 100));
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        assertEquals("CREATED", service.findSession(config.sessionId()).orElseThrow().status());
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
    void uploadCannotCompleteOrStoreAnAbsoluteLocalPath() throws Exception {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", "client_720p_watermarked", "snap-1");

        byte[] payload = new byte[]{1, 2, 3, 4};
        var file = new MockMultipartFile("file", "out.mp4", "video/mp4", payload);
        AuthorizationDeniedException failure = assertThrows(AuthorizationDeniedException.class, () ->
                service.uploadAndComplete(config.sessionId(), file, 10L, null, false));
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        var unchanged = service.findSession(config.sessionId()).orElseThrow();
        assertEquals("CREATED", unchanged.status());
        assertNull(unchanged.outputUri());

        Path expectedFile = tempDir
                .resolve("tenant").resolve("tenant-1")
                .resolve("workspace").resolve("ws-1")
                .resolve("project").resolve("proj-1")
                .resolve("exports").resolve(config.sessionId())
                .resolve("output.mp4");
        assertFalse(Files.exists(expectedFile));
    }

    @Test
    void clientFailureCannotMutateExecutionTruth() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        AuthorizationDeniedException failure = assertThrows(AuthorizationDeniedException.class, () ->
                service.failSession(config.sessionId(), "BROWSER_CRASH", "MediaRecorder stopped"));
        assertEquals("AUTHORIZATION_UNAVAILABLE", failure.decision().reasonCode());
        var unchanged = service.findSession(config.sessionId()).orElseThrow();
        assertEquals("CREATED", unchanged.status());
        assertNull(unchanged.errorCode());
    }

    @Test
    void cancelSessionWorks() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        var cancelled = service.cancelSession(config.sessionId());
        assertEquals("CANCELLED", cancelled.status());
    }

    @Test
    void cancelTerminalSessionThrows() {
        var config = service.createSessionWithConfig(
                "tenant-1", "ws-1", "proj-1", "user-1",
                "FREE", null, null);

        repository.updateStatus(config.sessionId(), ClientExportSession.STATUS_FAILED, 0,
                null, null, null, "ERR", "test");
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
                "UNRECOGNIZED_PRESENTATION_TIER", null, null);

        assertFalse(config.availablePresets().isEmpty());
        assertTrue(config.availablePresets().stream()
                .anyMatch(p -> p.get("name").equals("free_720p_watermarked")));
        assertFalse(config.availablePresets().stream()
                .anyMatch(p -> p.get("name").equals("team_4k")));
        verify(commercialAdmission).decide(argThat(request ->
                request.entitlementKey().equals("export.preset.free_720p_watermarked")));
    }
}
