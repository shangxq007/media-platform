package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.EffectEntitlementPort;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.app.EffectTimelineInspector;
import com.example.platform.render.app.timeline.AiRenderScriptNormalizer;
import com.example.platform.render.app.timeline.BaseJobTimelineLoader;
import com.example.platform.render.app.timeline.IncrementalRenderOrchestrationService;
import com.example.platform.render.app.timeline.TimelineSpecResolver;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.infrastructure.RenderArtifactStorageService;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.RenderProvider;
import com.example.platform.render.infrastructure.RenderProviderRouter;
import com.example.platform.render.infrastructure.timeline.EditorTimelineConverter;
import com.example.platform.shared.entitlement.EntitlementPort;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageCatalogPort;
import com.example.platform.storage.domain.BlobStorage;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Characterization tests for RenderOrchestratorService.
 *
 * <p>These tests lock the current behavior of submit, execute, artifact query,
 * tenant isolation, and timeline/script handling. They use H2 + real jOOQ for
 * render_job persistence and mocks for external collaborators.
 *
 * <p>These tests are a safety net for future staged extraction — not a refactoring tool.
 */
class RenderOrchestratorServiceCharacterizationTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private Connection conn;
    private RenderOrchestratorService service;

    // Mocks
    private RenderQuotaService quotaService;
    private RenderProviderRouter renderProviderRouter;
    private NotificationEventPublisher notificationEventPublisher;
    private ApplicationEventPublisher eventPublisher;
    private RenderJobStatusHistoryRepository historyRepository;
    private TimelineScriptParser timelineScriptParser;
    private RenderArtifactStorageService artifactStorageService;
    private TimelineSnapshotService timelineSnapshotService;
    private EffectTimelineInspector effectTimelineInspector;
    private RenderProfileResolver renderProfileResolver;
    private StorageCatalogPort storageCatalogPort;
    private EditorTimelineConverter editorTimelineConverter;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "orchtest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table project ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64) not null,"
                    + "name varchar(255) not null,"
                    + "description text,"
                    + "status varchar(32) not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table render_job ("
                    + "id varchar(64) primary key,"
                    + "project_id varchar(64) not null,"
                    + "tenant_id varchar(64),"
                    + "timeline_snapshot_id varchar(64) not null,"
                    + "profile varchar(100) not null,"
                    + "status varchar(20) not null,"
                    + "created_at timestamp not null,"
                    + "ai_script text,"
                    + "artifact_uri text,"
                    + "error_message text,"
                    + "pipeline_plan_json text,"
                    + "pipeline_execution_json text,"
                    + "base_job_id varchar(64)"
                    + ")");
            stmt.execute("create table render_job_status_history ("
                    + "id varchar(64) primary key,"
                    + "job_id varchar(64) not null,"
                    + "from_status varchar(30),"
                    + "to_status varchar(30) not null,"
                    + "reason varchar(255),"
                    + "error_code varchar(100),"
                    + "occurred_at timestamp not null"
                    + ")");
            stmt.execute("create table timeline_snapshot ("
                    + "id varchar(64) primary key,"
                    + "project_id varchar(64) not null,"
                    + "tenant_id varchar(64),"
                    + "payload_json text not null,"
                    + "schema_version varchar(32),"
                    + "created_at timestamp not null default CURRENT_TIMESTAMP"
                    + ")");
        }

        quotaService = mock(RenderQuotaService.class);
        renderProviderRouter = mock(RenderProviderRouter.class);
        notificationEventPublisher = mock(NotificationEventPublisher.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        historyRepository = new RenderJobStatusHistoryRepository(dsl);
        timelineScriptParser = mock(TimelineScriptParser.class);
        artifactStorageService = mock(RenderArtifactStorageService.class);
        timelineSnapshotService = mock(TimelineSnapshotService.class);
        effectTimelineInspector = mock(EffectTimelineInspector.class);
        renderProfileResolver = mock(RenderProfileResolver.class);
        storageCatalogPort = mock(StorageCatalogPort.class);
        editorTimelineConverter = mock(EditorTimelineConverter.class);

        // Default: quota allows, effect inspector returns empty, profile resolver returns input
        when(quotaService.checkQuota(anyString(), anyString(), anyInt())).thenReturn(true);
        when(effectTimelineInspector.extractFromScript(anyString()))
                .thenReturn(new EffectTimelineInspector.EffectUsage(List.of(), List.of()));
        when(renderProfileResolver.resolve(anyString(), anyList(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(timelineScriptParser.isTimelineJson(anyString())).thenReturn(true);

        RenderJobRepository renderJobRepository = new RenderJobRepository(dsl);
        RenderJobSubmissionService submissionService = new RenderJobSubmissionService(
                dsl, renderJobRepository, quotaService, historyRepository,
                notificationEventPublisher, eventPublisher, timelineScriptParser,
                effectTimelineInspector, renderProfileResolver,
                null /* aiTimelineEditService */, null /* cacheTenantGuard */);
        RenderArtifactQueryService artifactQueryService = new RenderArtifactQueryService(
                renderJobRepository, storageCatalogPort);
        RenderJobExecutionService executionService = new RenderJobExecutionService(
                renderJobRepository, quotaService, null /* aiGatewayPort */, renderProviderRouter,
                notificationEventPublisher, eventPublisher, historyRepository,
                timelineScriptParser, mock(TimelineSpecResolver.class),
                mock(IncrementalRenderOrchestrationService.class),
                artifactStorageService, timelineSnapshotService,
                editorTimelineConverter, effectTimelineInspector, renderProfileResolver,
                null /* effectEntitlementPort */, null /* renderWorkerQueueService */,
                null /* renderWorkerQueueProperties */, null /* pipelineDagExecutorService */,
                mock(TimelineExtensionsReader.class), null /* entitlementPort */,
                null /* hashInvalidationNotifier */, null /* aiRenderScriptNormalizer */);
        RenderJobTimelineQueryService timelineQueryService = new RenderJobTimelineQueryService(
                renderJobRepository, mock(BaseJobTimelineLoader.class), null /* cacheTenantGuard */);

        service = new RenderOrchestratorService(
                submissionService, executionService, artifactQueryService,
                timelineQueryService, null /* submitContinuation */);

        TenantContext.clear();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // --- Helpers ---

    private void insertProject(String projectId, String tenantId) {
        dsl.insertInto(table("project"))
                .columns(field("id"), field("tenant_id"), field("name"),
                        field("description"), field("status"), field("created_at"))
                .values(projectId, tenantId, "Test", "", "ACTIVE", OffsetDateTime.now())
                .execute();
    }

    private void insertRenderJob(String jobId, String projectId, String tenantId,
            String snapshotId, String profile, String status) {
        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"), field("profile"), field("status"), field("created_at"))
                .values(jobId, projectId, tenantId, snapshotId, profile, status, OffsetDateTime.now())
                .execute();
    }

    private RenderProvider mockProvider(String storageUri) {
        RenderProvider provider = mock(RenderProvider.class);
        when(provider.render(anyString(), anyString(), anyString()))
                .thenReturn(new RenderProvider.RenderResult("art-1", storageUri, 10L, "mp4", "1920x1080"));
        return provider;
    }

    // --- 4.1 submitRenderJob creates job and routes provider ---

    @Test
    void submitRenderJobCreatesJobAndRoutesProvider() {
        TenantContext.set("tenant-1");
        insertProject("proj-1", "tenant-1");

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/rj-1/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        when(timelineSnapshotService.findPayload(anyString())).thenReturn(Optional.of("{\"tracks\":[]}"));

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-1", "proj-1", "snap-1", "default_1080p");

        String jobId = service.submitRenderJob(request);

        assertNotNull(jobId);
        assertTrue(jobId.startsWith("rj_"));

        // Verify job was persisted
        var jobRow = dsl.select(field("status"), field("tenant_id"), field("project_id"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        assertNotNull(jobRow);
        assertEquals("COMPLETED", jobRow.get(field("status"), String.class));
        assertEquals("tenant-1", jobRow.get(field("tenant_id"), String.class));
        assertEquals("proj-1", jobRow.get(field("project_id"), String.class));

        // Verify provider was invoked
        verify(provider).render(eq(jobId), anyString(), anyString());

        // Verify notification was published (at least once — RenderJobCreatedEvent + status changes)
        verify(notificationEventPublisher, atLeastOnce()).publish(any());
    }

    // --- 4.2 submitRenderJob rejects when quota denied ---

    @Test
    void submitRenderJobRejectsWhenQuotaDenied() {
        TenantContext.set("tenant-2");
        insertProject("proj-2", "tenant-2");
        when(quotaService.checkQuota("tenant-2", "render", 1)).thenReturn(false);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-2", "proj-2", "snap-2", "default_1080p");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitRenderJob(request));
        assertTrue(ex.getMessage().contains("Quota exceeded"));

        // Verify rejected job was persisted
        var rejectedJob = dsl.select(field("status"), field("error_message"))
                .from(table("render_job"))
                .where(field("tenant_id").eq("tenant-2"))
                .fetchOne();
        assertNotNull(rejectedJob);
        assertEquals("REJECTED", rejectedJob.get(field("status"), String.class));
        assertEquals("Quota exceeded", rejectedJob.get(field("error_message"), String.class));

        // Verify provider was NOT invoked
        verifyNoInteractions(renderProviderRouter);
    }

    // --- 4.3 executeExistingRenderJob succeeds ---

    @Test
    void executeExistingRenderJobSucceeds() {
        TenantContext.set("tenant-3");
        insertProject("proj-3", "tenant-3");
        insertRenderJob("rj-3", "proj-3", "tenant-3", "snap-3", "default_1080p", "QUEUED");

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/rj-3/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        when(timelineSnapshotService.findPayload("snap-3"))
                .thenReturn(Optional.of("{\"tracks\":[]}"));

        String result = service.executeExistingRenderJob("tenant-3", "rj-3");

        assertEquals("rj-3", result);

        // Verify job completed
        var jobRow = dsl.select(field("status"), field("artifact_uri"))
                .from(table("render_job"))
                .where(field("id").eq("rj-3"))
                .fetchOne();
        assertEquals("COMPLETED", jobRow.get(field("status"), String.class));
        assertNotNull(jobRow.get(field("artifact_uri"), String.class));

        // Verify quota consumed
        verify(quotaService).consumeQuota("tenant-3", "render", 1);
    }

    // --- 4.4 executeExistingRenderJob handles provider failure ---

    @Test
    void executeExistingRenderJobHandlesProviderFailure() {
        TenantContext.set("tenant-4");
        insertProject("proj-4", "tenant-4");
        insertRenderJob("rj-4", "proj-4", "tenant-4", "snap-4", "default_1080p", "QUEUED");

        RenderProvider provider = mock(RenderProvider.class);
        when(provider.render(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("FFmpeg crashed"));
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        when(timelineSnapshotService.findPayload("snap-4"))
                .thenReturn(Optional.of("{\"tracks\":[]}"));

        assertThrows(IllegalStateException.class,
                () -> service.executeExistingRenderJob("tenant-4", "rj-4"));

        // Verify job marked failed
        var jobRow = dsl.select(field("status"), field("error_message"))
                .from(table("render_job"))
                .where(field("id").eq("rj-4"))
                .fetchOne();
        assertEquals("FAILED", jobRow.get(field("status"), String.class));
        assertTrue(jobRow.get(field("error_message"), String.class).contains("FFmpeg crashed"));
    }

    // --- 4.5 tenant isolation ---

    @Test
    void submitRenderJobRejectsCrossTenantAccess() {
        TenantContext.set("tenant-a");
        insertProject("proj-x", "tenant-b");

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-a", "proj-x", "snap-x", "default_1080p");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.submitRenderJob(request));
        assertTrue(ex.getMessage().contains("Project not found for tenant"));
    }

    @Test
    void executeExistingRenderJobRejectsCrossTenantAccess() {
        TenantContext.set("tenant-a");
        insertProject("proj-y", "tenant-b");
        insertRenderJob("rj-y", "proj-y", "tenant-b", "snap-y", "default_1080p", "QUEUED");

        assertThrows(IllegalArgumentException.class,
                () -> service.executeExistingRenderJob("tenant-a", "rj-y"));
    }

    @Test
    void getArtifactsByJobRejectsCrossTenantAccess() {
        TenantContext.set("tenant-a");
        insertRenderJob("rj-z", "proj-z", "tenant-b", "snap-z", "default_1080p", "COMPLETED");

        assertThrows(IllegalArgumentException.class,
                () -> service.getArtifactsByJob("rj-z"));
    }

    // --- 4.6 timeline snapshot/script behavior ---

    @Test
    void submitRenderJobUsesSnapshotPayload() {
        TenantContext.set("tenant-5");
        insertProject("proj-5", "tenant-5");

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/rj-5/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        when(timelineSnapshotService.findPayload("snap-5"))
                .thenReturn(Optional.of("{\"tracks\":[{\"type\":\"VIDEO\"}]}"));

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-5", "proj-5", "snap-5", "default_1080p");

        String jobId = service.submitRenderJob(request);

        // Verify snapshot was used (provider received timeline JSON)
        verify(provider).render(eq(jobId), contains("tracks"), eq("default_1080p"));
    }

    @Test
    void submitRenderJobUsesInlinePromptAsScript() {
        TenantContext.set("tenant-6");
        insertProject("proj-6", "tenant-6");

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/rj-6/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);

        String inlineTimeline = "{\"tracks\":[{\"type\":\"VIDEO\",\"clips\":[]}]}";
        SubmitRenderJobRequest request = SubmitRenderJobRequest.withPrompt(
                "tenant-6", "proj-6", inlineTimeline, "default_1080p");

        String jobId = service.submitRenderJob(request);

        // Verify inline timeline was persisted as ai_script
        var jobRow = dsl.select(field("ai_script"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        assertEquals(inlineTimeline, jobRow.get(field("ai_script"), String.class));
    }

    // --- 4.7 artifact query behavior ---

    @Test
    void getArtifactsByJobReturnsArtifacts() {
        TenantContext.set("tenant-7");
        insertRenderJob("rj-7", "proj-7", "tenant-7", "snap-7", "default_1080p", "COMPLETED");

        var artifactRef = new StorageCatalogPort.ArtifactRef(
                "art-1", "rj-7", "proj-7", "localFsStorageProvider://artifacts/rj-7/output.mp4",
                "mp4", "1920x1080", 10L, java.time.Instant.now());
        when(storageCatalogPort.findArtifactsByJob("rj-7")).thenReturn(List.of(artifactRef));

        List<ArtifactInfoResponse> artifacts = service.getArtifactsByJob("rj-7");

        assertEquals(1, artifacts.size());
        assertEquals("art-1", artifacts.get(0).artifactId());
        assertEquals("rj-7", artifacts.get(0).renderJobId());
    }

    @Test
    void getArtifactsByJobThrowsWhenJobNotFound() {
        assertThrows(IllegalArgumentException.class,
                () -> service.getArtifactsByJob("nonexistent"));
    }

    // --- 4.8 already completed job returns immediately ---

    @Test
    void executeExistingRenderJobReturnsImmediatelyForCompletedJob() {
        TenantContext.set("tenant-8");
        insertProject("proj-8", "tenant-8");
        insertRenderJob("rj-8", "proj-8", "tenant-8", "snap-8", "default_1080p", "COMPLETED");

        String result = service.executeExistingRenderJob("tenant-8", "rj-8");

        assertEquals("rj-8", result);
        // Provider should NOT be called for already-completed jobs
        verifyNoInteractions(renderProviderRouter);
    }
}
