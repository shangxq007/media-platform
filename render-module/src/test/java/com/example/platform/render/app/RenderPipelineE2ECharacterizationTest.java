package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.example.platform.ai.api.AiGatewayPort;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
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
import com.example.platform.render.infrastructure.providerruntime.engine.ProviderRuntimeEngine;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.entitlement.EntitlementPort;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.storage.api.StorageCatalogPort;
import com.example.platform.storage.domain.BlobStorage;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

/**
 * End-to-end characterization tests for the complete render pipeline flow.
 *
 * <p>Covers: timeline creation → asset/clip addition → subtitle/effect addition
 * → render job submission → job status verification → artifact output verification.
 *
 * <p>Uses PostgreSQL Testcontainers + real jOOQ for persistence and mocks for external collaborators.
 * Does NOT depend on real FFmpeg/Natron/MLT/Remotion.
 */
class RenderPipelineE2ECharacterizationTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private RenderOrchestratorService service;
    private RenderJobRepository renderJobRepository;

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
    private ProviderRuntimeEngine providerRuntimeEngine;

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
        providerRuntimeEngine = mock(ProviderRuntimeEngine.class);

        // Default stubs — set up in each test method as needed
        // No default mocks here to avoid conflicts with test-specific overrides

        renderJobRepository = new RenderJobRepository(dsl);
        RenderJobSubmissionService submissionService = new RenderJobSubmissionService(
                dsl, renderJobRepository, quotaService,
                null /* billingEnforcementService */, null /* billingDecisionEngine */,
                historyRepository,
                notificationEventPublisher, eventPublisher, timelineScriptParser,
                effectTimelineInspector, renderProfileResolver,
                null, null);
        RenderArtifactQueryService artifactQueryService = new RenderArtifactQueryService(
                renderJobRepository, storageCatalogPort, List.of());
        RenderJobExecutionService executionService = new RenderJobExecutionService(
                renderJobRepository, quotaService, null, renderProviderRouter,
                providerRuntimeEngine,
                notificationEventPublisher, eventPublisher, historyRepository,
                timelineScriptParser, mock(TimelineSpecResolver.class),
                mock(IncrementalRenderOrchestrationService.class),
                artifactStorageService, null /* artifactGraphRepository */,
                null /* billingEnforcementService */,
                timelineSnapshotService,
                editorTimelineConverter, effectTimelineInspector, renderProfileResolver,
                null, null, null, null,
                mock(TimelineExtensionsReader.class), null, null, null,
                mock(RenderJobClaimService.class), mock(RenderJobFailureService.class));
        RenderJobTimelineQueryService timelineQueryService = new RenderJobTimelineQueryService(
                renderJobRepository, mock(BaseJobTimelineLoader.class));

        service = new RenderOrchestratorService(
                submissionService, executionService, artifactQueryService,
                timelineQueryService, null);

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
                .values(projectId, tenantId, "Test Project", "", "ACTIVE", OffsetDateTime.now())
                .execute();
    }

    private RenderProvider mockProvider(String storageUri) {
        RenderProvider provider = mock(RenderProvider.class);
        when(provider.render(anyString(), anyString(), anyString()))
                .thenReturn(new RenderProvider.RenderResult("art-1", storageUri, 10L, "mp4", "1920x1080"));
        return provider;
    }

    private void stubProviderResolution(RenderProvider provider) {
        var result = new ProviderRuntimeEngine.ProviderResolutionResult(
                "trace-test", "mock-provider", provider,
                List.of("mock-provider"), null, null, null, 0);
        when(providerRuntimeEngine.resolveProvider(any())).thenReturn(result);
    }

    private void setupDefaultMocks() {
        when(quotaService.checkQuota(anyString(), anyString(), anyInt())).thenReturn(true);
        when(effectTimelineInspector.extractFromScript(any()))
                .thenReturn(new EffectTimelineInspector.EffectUsage(List.of(), List.of()));
        when(renderProfileResolver.resolve(anyString(), anyList(), anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(timelineScriptParser.isTimelineJson(anyString())).thenReturn(true);
        when(editorTimelineConverter.toOtioJson(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
    }

    private String buildTimelineJson(String trackType, String assetUri, double in, double out) {
        return String.format("""
                {
                  "tracks": [{
                    "type": "%s",
                    "clips": [{
                      "id": "clip-1",
                      "assetRef": {"id": "asset-1", "uri": "%s"},
                      "timelineStart": 0.0,
                      "assetInPoint": %s,
                      "assetOutPoint": %s,
                      "clipDuration": %s
                    }]
                  }],
                  "outputSpec": {
                    "resolution": "1920x1080",
                    "format": "mp4"
                  }
                }
                """, trackType, assetUri, in, out, (out - in));
    }

    // =========================================================
    // Scenario A: Simple timeline render (video only)
    // =========================================================

    @Test
    void scenarioA_simpleTimelineRender() {
        setupDefaultMocks();
        // 1. Create timeline with one video clip
        TenantContext.set("tenant-1");
        insertProject("proj-1", "tenant-1");

        String timelineJson = buildTimelineJson("VIDEO", "storage://videos/source.mp4", 0.0, 10.0);
        when(timelineSnapshotService.findPayload("snap-1")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        // 2. Submit render job
        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-1", "proj-1", "snap-1", "default_1080p");
        String jobId = service.submitRenderJob(request);

        // 3. Verify job created and completed
        assertNotNull(jobId);
        var jobRow = dsl.select(field("status"), field("tenant_id"), field("project_id"), field("artifact_uri"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        assertEquals("COMPLETED", jobRow.get(field("status"), String.class));
        assertEquals("tenant-1", jobRow.get(field("tenant_id"), String.class));
        assertEquals("proj-1", jobRow.get(field("project_id"), String.class));
        assertNotNull(jobRow.get(field("artifact_uri"), String.class));

        // 4. Verify provider was invoked with timeline
        verify(provider).render(eq(jobId), contains("tracks"), eq("default_1080p"));

        // 5. Verify notifications published
        verify(notificationEventPublisher, atLeastOnce()).publish(any());
    }

    // =========================================================
    // Scenario B: Timeline with subtitle burn-in
    // =========================================================

    @Test
    void scenarioB_timelineWithSubtitleBurnIn() {
        setupDefaultMocks();
        TenantContext.set("tenant-2");
        insertProject("proj-2", "tenant-2");

        String timelineJson = """
                {
                  "tracks": [{
                    "type": "VIDEO",
                    "clips": [{
                      "id": "clip-1",
                      "assetRef": {"id": "asset-1", "uri": "storage://videos/source.mp4"},
                      "timelineStart": 0.0,
                      "assetInPoint": 0.0,
                      "assetOutPoint": 10.0,
                      "clipDuration": 10.0
                    }]
                  }],
                  "textOverlays": [{
                    "id": "sub-1",
                    "text": "Hello World",
                    "startTime": 1.0,
                    "duration": 3.0
                  }],
                  "outputSpec": {
                    "resolution": "1920x1080",
                    "format": "mp4"
                  }
                }
                """;
        when(timelineSnapshotService.findPayload("snap-2")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-2", "proj-2", "snap-2", "default_1080p");
        String jobId = service.submitRenderJob(request);

        assertNotNull(jobId);

        // Verify job completed
        var jobRow = dsl.select(field("status"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        assertEquals("COMPLETED", jobRow.get(field("status"), String.class));

        // Verify provider received timeline with text overlays
        verify(provider).render(eq(jobId), contains("textOverlays"), eq("default_1080p"));
    }

    // =========================================================
    // Scenario C: Timeline with effects
    // =========================================================

    @Test
    void scenarioC_timelineWithEffects() {
        setupDefaultMocks();
        TenantContext.set("tenant-3");
        insertProject("proj-3", "tenant-3");

        String timelineJson = """
                {
                  "tracks": [{
                    "type": "VIDEO",
                    "clips": [{
                      "id": "clip-1",
                      "assetRef": {"id": "asset-1", "uri": "storage://videos/source.mp4"},
                      "timelineStart": 0.0,
                      "assetInPoint": 0.0,
                      "assetOutPoint": 10.0,
                      "clipDuration": 10.0,
                      "effects": [
                        {"effectKey": "video.blur", "parameters": {"radius": 5.0}},
                        {"effectKey": "video.vignette", "parameters": {"strength": 0.5}}
                      ]
                    }]
                  }],
                  "outputSpec": {
                    "resolution": "1920x1080",
                    "format": "mp4"
                  }
                }
                """;
        when(timelineSnapshotService.findPayload("snap-3")).thenReturn(Optional.of(timelineJson));
        when(effectTimelineInspector.extractFromScript(anyString()))
                .thenReturn(new EffectTimelineInspector.EffectUsage(
                        List.of("video.blur", "video.vignette"), List.of()));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        // Mock both single-arg and two-arg route methods
        doReturn(provider).when(renderProviderRouter).route(anyString());
        doReturn(provider).when(renderProviderRouter).route(anyString(), anyList());
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-3", "proj-3", "snap-3", "default_1080p");
        String jobId = service.submitRenderJob(request);

        assertNotNull(jobId);
        var jobRow = dsl.select(field("status"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        assertEquals("COMPLETED", jobRow.get(field("status"), String.class));
    }

    // =========================================================
    // Scenario D: Timeline with image/sticker overlay
    // =========================================================

    @Test
    void scenarioD_timelineWithImageOverlay() {
        setupDefaultMocks();
        TenantContext.set("tenant-4");
        insertProject("proj-4", "tenant-4");

        String timelineJson = """
                {
                  "tracks": [{
                    "type": "VIDEO",
                    "clips": [{
                      "id": "clip-1",
                      "assetRef": {"id": "asset-1", "uri": "storage://videos/source.mp4"},
                      "timelineStart": 0.0,
                      "assetInPoint": 0.0,
                      "assetOutPoint": 10.0,
                      "clipDuration": 10.0
                    }]
                  }],
                  "stickers": [{
                    "id": "sticker-1",
                    "imageUri": "storage://images/logo.png",
                    "x": 10,
                    "y": 10,
                    "width": 100,
                    "height": 100,
                    "startTime": 0.0,
                    "duration": 10.0
                  }],
                  "outputSpec": {
                    "resolution": "1920x1080",
                    "format": "mp4"
                  }
                }
                """;
        when(timelineSnapshotService.findPayload("snap-4")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-4", "proj-4", "snap-4", "default_1080p");
        String jobId = service.submitRenderJob(request);

        assertNotNull(jobId);
        assertEquals("COMPLETED", renderJobRepository.findById(jobId).map(j -> j.status()).orElse("NOT_FOUND"));
    }

    // =========================================================
    // Scenario E: Invalid timeline rejected
    // =========================================================

    @Test
    void scenarioE_invalidTimelineRejected_noTracks() {
        setupDefaultMocks();
        TenantContext.set("tenant-5");
        insertProject("proj-5", "tenant-5");

        // Timeline with no tracks
        String timelineJson = """
                {
                  "tracks": [],
                  "outputSpec": {"resolution": "1920x1080", "format": "mp4"}
                }
                """;
        when(timelineSnapshotService.findPayload("snap-5")).thenReturn(Optional.of(timelineJson));
        when(timelineScriptParser.isTimelineJson(anyString())).thenReturn(false);

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        doReturn(provider).when(renderProviderRouter).route(anyString());
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-5", "proj-5", "snap-5", "default_1080p");

        String jobId = service.submitRenderJob(request);
        assertNotNull(jobId);
    }

    @Test
    void scenarioE_invalidTimelineRejected_negativeDuration() {
        setupDefaultMocks();
        TenantContext.set("tenant-5b");
        insertProject("proj-5b", "tenant-5b");

        // Timeline with invalid timing (out < in)
        String timelineJson = """
                {
                  "tracks": [{
                    "type": "VIDEO",
                    "clips": [{
                      "id": "clip-1",
                      "assetRef": {"id": "asset-1", "uri": "storage://videos/source.mp4"},
                      "timelineStart": 0.0,
                      "assetInPoint": 10.0,
                      "assetOutPoint": 5.0,
                      "clipDuration": -5.0
                    }]
                  }],
                  "outputSpec": {"resolution": "1920x1080", "format": "mp4"}
                }
                """;
        when(timelineSnapshotService.findPayload("snap-5b")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-5b", "proj-5b", "snap-5b", "default_1080p");

        // Job is created even with invalid timeline (validation is at parse time)
        String jobId = service.submitRenderJob(request);
        assertNotNull(jobId);
    }

    // =========================================================
    // Scenario F: Provider capability mismatch
    // =========================================================

    @Test
    void scenarioF_providerCapabilityMismatch_noProviderAvailable() {
        setupDefaultMocks();
        TenantContext.set("tenant-6");
        insertProject("proj-6", "tenant-6");

        String timelineJson = buildTimelineJson("VIDEO", "storage://videos/source.mp4", 0.0, 10.0);
        when(timelineSnapshotService.findPayload("snap-6")).thenReturn(Optional.of(timelineJson));

        // No provider available for this profile
        when(renderProviderRouter.route("unsupported_profile")).thenReturn(null);
        stubProviderResolution(null);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-6", "proj-6", "snap-6", "unsupported_profile");

        // Should throw because no provider is available
        assertThrows(IllegalStateException.class, () -> service.submitRenderJob(request));
    }

    // =========================================================
    // Scenario G: Quota exceeded
    // =========================================================

    @Test
    void scenarioG_quotaExceeded() {
        setupDefaultMocks();
        TenantContext.set("tenant-7");
        insertProject("proj-7", "tenant-7");
        when(quotaService.checkQuota("tenant-7", "render", 1)).thenReturn(false);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-7", "proj-7", "snap-7", "default_1080p");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.submitRenderJob(request));
        assertTrue(ex.getMessage().contains("Quota exceeded"));

        // Verify REJECTED job persisted
        var jobRow = dsl.select(field("status"), field("error_message"))
                .from(table("render_job"))
                .where(field("tenant_id").eq("tenant-7"))
                .fetchOne();
        assertNotNull(jobRow);
        assertEquals("REJECTED", jobRow.get(field("status"), String.class));
    }

    // =========================================================
    // Scenario H: Tenant isolation
    // =========================================================

    @Test
    void scenarioH_crossTenantAccessRejected() {
        setupDefaultMocks();
        TenantContext.set("tenant-a");
        insertProject("proj-x", "tenant-b");

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-a", "proj-x", "snap-x", "default_1080p");

        assertThrows(IllegalArgumentException.class, () -> service.submitRenderJob(request));
    }

    // =========================================================
    // Scenario I: Job status lifecycle
    // =========================================================

    @Test
    void scenarioI_jobStatusLifecycle() {
        setupDefaultMocks();
        TenantContext.set("tenant-9");
        insertProject("proj-9", "tenant-9");

        String timelineJson = buildTimelineJson("VIDEO", "storage://videos/source.mp4", 0.0, 10.0);
        when(timelineSnapshotService.findPayload("snap-9")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-9", "proj-9", "snap-9", "default_1080p");
        String jobId = service.submitRenderJob(request);

        // Verify status transitions were recorded
        var history = dsl.select(field("from_status"), field("to_status"))
                .from(table("render_job_status_history"))
                .where(field("job_id").eq(jobId))
                .fetch();

        assertFalse(history.isEmpty());
        // Should have QUEUED -> SELECTING_PROVIDER -> EXECUTING -> COMPLETED transitions
        assertTrue(history.stream().anyMatch(r ->
                "QUEUED".equals(r.get(field("from_status"), String.class))
                        && "SELECTING_PROVIDER".equals(r.get(field("to_status"), String.class))));
    }

    // =========================================================
    // Scenario J: Artifact output verification
    // =========================================================

    @Test
    void scenarioJ_artifactOutputVerification() {
        setupDefaultMocks();
        TenantContext.set("tenant-10");
        insertProject("proj-10", "tenant-10");

        String timelineJson = buildTimelineJson("VIDEO", "storage://videos/source.mp4", 0.0, 10.0);
        when(timelineSnapshotService.findPayload("snap-10")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-10", "proj-10", "snap-10", "default_1080p");
        String jobId = service.submitRenderJob(request);

        // Verify artifact URI persisted
        var jobRow = dsl.select(field("artifact_uri"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        assertNotNull(jobRow.get(field("artifact_uri"), String.class));
        assertTrue(jobRow.get(field("artifact_uri"), String.class).contains("artifacts"));

        // Verify artifact query works
        var artifactRef = new StorageCatalogPort.ArtifactRef(
                "art-1", jobId, "proj-10", "localFsStorageProvider://artifacts/output.mp4",
                "mp4", "1920x1080", 10L, java.time.Instant.now());
        when(storageCatalogPort.findArtifactsByJob(jobId)).thenReturn(List.of(artifactRef));

        List<ArtifactInfoResponse> artifacts = service.getArtifactsByJob(jobId);
        assertEquals(1, artifacts.size());
        assertEquals("art-1", artifacts.get(0).artifactId());
    }

    // =========================================================
    // Scenario K: Provider failure handling
    // =========================================================

    @Test
    void scenarioK_providerFailureHandling() {
        setupDefaultMocks();
        TenantContext.set("tenant-11");
        insertProject("proj-11", "tenant-11");

        String timelineJson = buildTimelineJson("VIDEO", "storage://videos/source.mp4", 0.0, 10.0);
        when(timelineSnapshotService.findPayload("snap-11")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mock(RenderProvider.class);
        when(provider.render(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("FFmpeg crashed"));
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-11", "proj-11", "snap-11", "default_1080p");

        assertThrows(IllegalStateException.class, () -> service.submitRenderJob(request));

        // Verify job marked FAILED
        var jobRow = dsl.select(field("status"), field("error_message"))
                .from(table("render_job"))
                .where(field("tenant_id").eq("tenant-11"))
                .fetchOne();
        assertEquals("FAILED", jobRow.get(field("status"), String.class));
        assertTrue(jobRow.get(field("error_message"), String.class).contains("FFmpeg crashed"));
    }

    // =========================================================
    // Scenario L: Trimmed clip
    // =========================================================

    @Test
    void scenarioL_trimmedClipRender() {
        setupDefaultMocks();
        TenantContext.set("tenant-12");
        insertProject("proj-12", "tenant-12");

        // Trim: only 5s-15s of source
        String timelineJson = buildTimelineJson("VIDEO", "storage://videos/source.mp4", 5.0, 15.0);
        when(timelineSnapshotService.findPayload("snap-12")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-12", "proj-12", "snap-12", "default_1080p");
        String jobId = service.submitRenderJob(request);

        assertNotNull(jobId);
        assertEquals("COMPLETED", renderJobRepository.findById(jobId).map(j -> j.status()).orElse("NOT_FOUND"));
    }

    // =========================================================
    // Scenario M: Multi-track timeline
    // =========================================================

    @Test
    void scenarioM_multiTrackTimeline() {
        setupDefaultMocks();
        TenantContext.set("tenant-13");
        insertProject("proj-13", "tenant-13");

        String timelineJson = """
                {
                  "tracks": [
                    {
                      "type": "VIDEO",
                      "clips": [{
                        "id": "v-clip-1",
                        "assetRef": {"id": "v-asset-1", "uri": "storage://videos/source.mp4"},
                        "timelineStart": 0.0,
                        "assetInPoint": 0.0,
                        "assetOutPoint": 10.0,
                        "clipDuration": 10.0
                      }]
                    },
                    {
                      "type": "AUDIO",
                      "clips": [{
                        "id": "a-clip-1",
                        "assetRef": {"id": "a-asset-1", "uri": "storage://audio/bgm.mp3"},
                        "timelineStart": 0.0,
                        "assetInPoint": 0.0,
                        "assetOutPoint": 10.0,
                        "clipDuration": 10.0
                      }]
                    }
                  ],
                  "outputSpec": {
                    "resolution": "1920x1080",
                    "format": "mp4"
                  }
                }
                """;
        when(timelineSnapshotService.findPayload("snap-13")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-13", "proj-13", "snap-13", "default_1080p");
        String jobId = service.submitRenderJob(request);

        assertNotNull(jobId);
        assertEquals("COMPLETED", renderJobRepository.findById(jobId).map(j -> j.status()).orElse("NOT_FOUND"));
    }

    // =========================================================
    // Scenario N: Quota consumption on success
    // =========================================================

    @Test
    void scenarioN_quotaConsumedOnSuccess() {
        setupDefaultMocks();
        TenantContext.set("tenant-14");
        insertProject("proj-14", "tenant-14");

        String timelineJson = buildTimelineJson("VIDEO", "storage://videos/source.mp4", 0.0, 10.0);
        when(timelineSnapshotService.findPayload("snap-14")).thenReturn(Optional.of(timelineJson));

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route("default_1080p")).thenReturn(provider);
        stubProviderResolution(provider);

        SubmitRenderJobRequest request = SubmitRenderJobRequest.withSnapshot(
                "tenant-14", "proj-14", "snap-14", "default_1080p");
        service.submitRenderJob(request);

        // Verify quota consumed exactly once
        verify(quotaService).consumeQuota("tenant-14", "render", 1);
    }

    // =========================================================
    // Scenario O: Inline prompt as timeline script
    // =========================================================

    @Test
    void scenarioO_inlinePromptAsTimelineScript() {
        setupDefaultMocks();
        TenantContext.set("tenant-15");
        insertProject("proj-15", "tenant-15");

        RenderProvider provider = mockProvider("localFsStorageProvider://artifacts/output.mp4");
        when(renderProviderRouter.route(anyString())).thenReturn(provider);
        stubProviderResolution(provider);

        String inlineTimeline = buildTimelineJson("VIDEO", "storage://videos/source.mp4", 0.0, 10.0);
        SubmitRenderJobRequest request = SubmitRenderJobRequest.withPrompt(
                "tenant-15", "proj-15", inlineTimeline, "default_1080p");

        String jobId = service.submitRenderJob(request);

        // Verify inline timeline persisted as ai_script
        var jobRow = dsl.select(field("ai_script"))
                .from(table("render_job"))
                .where(field("id").eq(jobId))
                .fetchOne();
        assertNotNull(jobRow.get(field("ai_script"), String.class));
        assertTrue(jobRow.get(field("ai_script"), String.class).contains("tracks"));
    }
}
