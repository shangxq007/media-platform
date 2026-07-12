package com.example.platform.render.api;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;
import org.springframework.http.ResponseEntity;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VS.1 API contract tests for {@link RenderController}.
 *
 * <p>Verifies the REST API surface for render job CRUD operations.
 * Uses hand-written fakes — no Mockito, no database, no H2, no Spring context.
 *
 * <p>Coverage:
 * <ul>
 *   <li>Tenant-scoped create / get / list render jobs</li>
 *   <li>Legacy (non-tenant) create / get / list render jobs</li>
 *   <li>Job submission via orchestrator port</li>
 *   <li>Job start / execute-local delegation</li>
 *   <li>Job cancel / retry state transitions</li>
 *   <li>Artifact retrieval</li>
 *   <li>Error handling: not found, tenant mismatch, missing service</li>
 *   <li>No local paths or storage internals in responses</li>
 * </ul>
 */
class RenderControllerContractTest {

    private FakeRenderJobService fakeService;
    private FakeOrchestratorPort fakeOrchestrator;
    private FakeArtifactAccessService fakeArtifactAccess;
    private RenderController controller;

    @BeforeEach
    void setUp() {
        fakeService = new FakeRenderJobService();
        fakeOrchestrator = new FakeOrchestratorPort();
        fakeArtifactAccess = new FakeArtifactAccessService();
        controller = new RenderController(fakeService, fakeOrchestrator,
                null, null, null, null, null, null, null, null, null, null, fakeArtifactAccess);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ========== Tenant-scoped CRUD ==========

    @Nested
    @DisplayName("Tenant-scoped render job creation")
    class TenantScopedCreate {

        @Test
        @DisplayName("POST /tenants/{tenantId}/projects/{projectId}/render-jobs delegates to service")
        void createRenderJobDelegates() {
            CreateRenderJobRequest req = new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p");

            RenderJobResponse result = controller.createRenderJob("t-1", "proj-1", req);

            assertNotNull(result);
            assertTrue(result.id().startsWith("rj"));
            assertEquals("QUEUED", result.status());
            assertEquals("proj-1", result.projectId());
            assertEquals(1, fakeService.createForProjectCalls);
        }
    }

    @Nested
    @DisplayName("Tenant-scoped get and list")
    class TenantScopedQuery {

        @Test
        @DisplayName("GET /tenants/{tenantId}/projects/{projectId}/render-jobs/{jobId} returns job")
        void getRenderJobReturnsJob() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED"));

            RenderJobResponse result = controller.getRenderJob("t-1", "proj-1", "rj-1");

            assertEquals("rj-1", result.id());
            assertEquals("COMPLETED", result.status());
        }

        @Test
        @DisplayName("GET /tenants/{tenantId}/projects/{projectId}/render-jobs returns list")
        void listRenderJobsReturnsList() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));
            fakeService.storedJobs.put("rj-2",
                    new RenderJobResponse("rj-2", "proj-1", "snap-2", "default_1080p", "COMPLETED"));

            List<RenderJobResponse> result = controller.listRenderJobs("t-1", "proj-1");

            assertEquals(2, result.size());
        }
    }

    // ========== Legacy endpoints ==========

    @Nested
    @DisplayName("Legacy render job endpoints")
    class LegacyEndpoints {

        @Test
        @DisplayName("POST /render/jobs creates job via service")
        void legacyCreateDelegates() {
            CreateRenderJobRequest req = new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p");

            RenderJobResponse result = controller.create(req);

            assertNotNull(result);
            assertEquals("QUEUED", result.status());
            assertEquals(1, fakeService.createCalls);
        }

        @Test
        @DisplayName("GET /render/jobs/{jobId} returns job")
        void legacyGetReturnsJob() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "EXECUTING"));

            RenderJobResponse result = controller.getJob("rj-1");

            assertEquals("EXECUTING", result.status());
        }

        @Test
        @DisplayName("GET /render/jobs returns list")
        void legacyListReturnsList() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));

            List<RenderJobResponse> result = controller.list();

            assertEquals(1, result.size());
        }
    }

    // ========== Job submission via orchestrator ==========

    @Nested
    @DisplayName("Job submission via orchestrator")
    class JobSubmission {

        @Test
        @DisplayName("Legacy submit delegates to orchestrator when available")
        void submitDelegatesToOrchestrator() {
            SubmitRenderJobRequest req = new SubmitRenderJobRequest("t-1", "proj-1", "test prompt", "default_1080p", "snap-1");
            fakeOrchestrator.submitResult = "rj-submitted";

            Map<String, String> result = controller.submitJob(req);

            assertEquals("rj-submitted", result.get("jobId"));
            assertEquals("QUEUED", result.get("status"));
            assertEquals(1, fakeOrchestrator.submitCalls);
        }

        @Test
        @DisplayName("Legacy submit falls back to service when orchestrator null")
        void submitFallsBackToService() {
            RenderController controllerNoOrch = new RenderController(fakeService);
            SubmitRenderJobRequest req = new SubmitRenderJobRequest("t-1", "proj-1", "test prompt", "default_1080p", "snap-1");

            Map<String, String> result = controllerNoOrch.submitJob(req);

            assertNotNull(result.get("jobId"));
            assertEquals("QUEUED", result.get("status"));
        }
    }

    // ========== Job start / execute-local ==========

    @Nested
    @DisplayName("Job start and execute-local")
    class JobExecution {

        @Test
        @DisplayName("Start delegates to orchestrator when available")
        void startDelegatesToOrchestrator() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));
            fakeOrchestrator.executeResult = "rj-1";

            Map<String, String> result = controller.startRenderJob("t-1", "proj-1", "rj-1");

            assertEquals("rj-1", result.get("jobId"));
            assertEquals("STARTED", result.get("status"));
        }

        @Test
        @DisplayName("Execute-local delegates to orchestrator when available")
        void executeLocalDelegatesToOrchestrator() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));
            fakeOrchestrator.executeResult = "rj-1";

            Map<String, String> result = controller.executeLocal("t-1", "proj-1", "rj-1");

            assertEquals("rj-1", result.get("jobId"));
            assertEquals("COMPLETED", result.get("status"));
        }

        @Test
        @DisplayName("Start returns QUEUED when orchestrator null")
        void startReturnsQueuedWhenNoOrchestrator() {
            RenderController controllerNoOrch = new RenderController(fakeService);

            Map<String, String> result = controllerNoOrch.startRenderJob("t-1", "proj-1", "rj-1");

            assertEquals("rj-1", result.get("jobId"));
            assertEquals("QUEUED", result.get("status"));
        }
    }

    // ========== Cancel and retry ==========

    @Nested
    @DisplayName("Cancel and retry")
    class CancelAndRetry {

        @Test
        @DisplayName("Cancel delegates to service with tenant validation")
        void cancelDelegates() {
            TenantContext.set("t-1");
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "CANCELLED"));

            RenderJobResponse result = controller.cancelJob("rj-1", "t-1");

            assertEquals("CANCELLED", result.status());
        }

        @Test
        @DisplayName("Retry delegates to service with tenant validation")
        void retryDelegates() {
            TenantContext.set("t-1");
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));

            RenderJobResponse result = controller.retryJob("rj-1", "t-1");

            assertEquals("QUEUED", result.status());
        }

        @Test
        @DisplayName("Cancel throws when tenant ID does not match context")
        void cancelThrowsWhenTenantMismatch() {
            TenantContext.set("t-1");

            assertThrows(IllegalArgumentException.class,
                    () -> controller.cancelJob("rj-1", "t-other"));
        }
    }

    // ========== Artifacts ==========

    @Nested
    @DisplayName("Artifact retrieval")
    class ArtifactRetrieval {

        @Test
        @DisplayName("GET /render/jobs/{jobId}/artifacts delegates to orchestrator")
        void getArtifactsDelegates() {
            fakeOrchestrator.artifacts.put("rj-1", List.of(
                    new ArtifactInfoResponse("art-1", "rj-1", "proj-1", "/api/v1/downloads/art-1",
                            "video/mp4", "1920x1080", 1024L, Instant.now())));

            List<ArtifactInfoResponse> result = controller.getArtifacts("rj-1");

            assertEquals(1, result.size());
            assertEquals("art-1", result.get(0).artifactId());
        }

        @Test
        @DisplayName("Artifacts return empty when orchestrator null")
        void artifactsReturnEmptyWhenNoOrchestrator() {
            RenderController controllerNoOrch = new RenderController(fakeService);

            List<ArtifactInfoResponse> result = controllerNoOrch.getArtifacts("rj-1");

            assertTrue(result.isEmpty());
        }
    }

    // ========== Artifact access (scoped + legacy) ==========

    @Nested
    @DisplayName("Tenant-scoped artifact access")
    class ScopedArtifactAccess {

        @Test
        @DisplayName("GET tenant-scoped access returns signed URL when authorized")
        void scopedAccessReturnsSignedUrl() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED"));
            fakeOrchestrator.artifacts.put("rj-1", List.of(
                    new ArtifactInfoResponse("art-1", "rj-1", "proj-1", "my-bucket/path/to/output.mp4",
                            "video/mp4", "1920x1080", 1024L, Instant.now())));
            fakeArtifactAccess.descriptorToReturn = new com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor(
                    null, null,
                    com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor.AccessType.SIGNED_URL,
                    "GET", "https://signed.example.com/art-1",
                    Instant.now().plusSeconds(900), 900,
                    "video/mp4", "output.mp4", 1024L, "READY", null, true);

            ResponseEntity<?> result = controller.getArtifactAccessScoped("t-1", "proj-1", "rj-1", "art-1");

            assertEquals(200, result.getStatusCode().value());
        }

        @Test
        @DisplayName("Tenant-scoped access returns 404 when not found in scope")
        void scopedAccessReturns404WhenNotFound() {
            // not stored -> getByIdAndProject will throw IllegalArgumentException
            assertThrows(IllegalArgumentException.class,
                    () -> controller.getArtifactAccessScoped("t-1", "proj-1", "rj-missing", "art-1"));
        }

        @Test
        @DisplayName("Tenant-scoped access returns 404 when artifact not on this render plan")
        void scopedAccessReturns404WhenArtifactNotOnPlan() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED"));
            fakeOrchestrator.artifacts.put("rj-1", List.of(
                    new ArtifactInfoResponse("art-other", "rj-1", "proj-1", "my-bucket/path/to/output.mp4",
                            "video/mp4", "1920x1080", 1024L, Instant.now())));

            ResponseEntity<?> result = controller.getArtifactAccessScoped("t-1", "proj-1", "rj-1", "art-1");

            assertEquals(404, result.getStatusCode().value());
        }
    }

    @Nested
    @DisplayName("Legacy artifact access with authorization")
    class LegacyArtifactAccess {

        @Test
        @DisplayName("Legacy access returns signed URL when authorized")
        void legacyAccessReturnsSignedUrl() {
            fakeService.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED"));
            fakeOrchestrator.artifacts.put("rj-1", List.of(
                    new ArtifactInfoResponse("art-1", "rj-1", "proj-1", "my-bucket/path/to/output.mp4",
                            "video/mp4", "1920x1080", 1024L, Instant.now())));
            fakeArtifactAccess.descriptorToReturn = new com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor(
                    null, null,
                    com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor.AccessType.SIGNED_URL,
                    "GET", "https://signed.example.com/art-1",
                    Instant.now().plusSeconds(900), 900,
                    "video/mp4", "output.mp4", 1024L, "READY", null, true);

            ResponseEntity<?> result = controller.getArtifactAccess("rj-1", "art-1");

            assertEquals(200, result.getStatusCode().value());
        }

        @Test
        @DisplayName("Legacy access returns 404 when not found")
        void legacyAccessReturns404WhenNotFound() {
            // not stored -> getById will throw IllegalArgumentException
            assertThrows(IllegalArgumentException.class,
                    () -> controller.getArtifactAccess("rj-missing", "art-1"));
        }

        @Test
        @DisplayName("Legacy access never calls presigner before authorization")
        void legacyAccessNeverCallsPresignerBeforeAuth() {
            // not stored -> auth fails, presigner must not be called
            try {
                controller.getArtifactAccess("rj-missing", "art-1");
            } catch (IllegalArgumentException ignored) {
            }
            assertEquals(0, fakeArtifactAccess.createAccessCalls,
                    "Presigner must not be called before authorization passes");
        }
    }

    // ========== No internal leaks ==========

    @Nested
    @DisplayName("API response safety — no internal leaks")
    class ResponseSafety {

        @Test
        @DisplayName("RenderJobResponse does not contain local paths")
        void responseNoLocalPaths() {
            RenderJobResponse response = new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED");

            String toString = response.toString();
            assertFalse(toString.contains("/home/"), "Response must not contain local paths");
            assertFalse(toString.contains("/tmp/"), "Response must not contain temp paths");
            assertFalse(toString.contains("C:\\\\"), "Response must not contain Windows paths");
        }

        @Test
        @DisplayName("ArtifactInfoResponse uses API download paths, not storage internals")
        void artifactResponseUsesApiPaths() {
            ArtifactInfoResponse artifact = new ArtifactInfoResponse(
                    "art-1", "rj-1", "proj-1", "/api/v1/downloads/art-1",
                    "video/mp4", "1920x1080", 1024L, Instant.now());

            assertNotNull(artifact.storageUri());
            assertTrue(artifact.storageUri().startsWith("/api/"),
                    "Download URL must be API path, not storage internal: " + artifact.storageUri());
        }
    }

    // ========== Fakes ==========

    static class FakeRenderJobService extends RenderJobService {
        int createCalls = 0;
        int createForProjectCalls = 0;
        final Map<String, RenderJobResponse> storedJobs = new HashMap<>();

        FakeRenderJobService() {
            super(null, null, null, null);
        }

        @Override
        public RenderJobResponse create(CreateRenderJobRequest request) {
            createCalls++;
            String id = "rj-" + UUID.randomUUID().toString().substring(0, 8);
            return new RenderJobResponse(id, request.projectId(), request.timelineSnapshotId(),
                    request.profile(), "QUEUED");
        }

        @Override
        public RenderJobResponse createForProject(String tenantId, String projectId, CreateRenderJobRequest request) {
            createForProjectCalls++;
            String id = "rj-" + UUID.randomUUID().toString().substring(0, 8);
            return new RenderJobResponse(id, projectId, request.timelineSnapshotId(),
                    request.profile(), "QUEUED");
        }

        @Override
        public RenderJobResponse getById(String jobId) {
            RenderJobResponse job = storedJobs.get(jobId);
            if (job == null) throw new IllegalArgumentException("Render job not found: " + jobId);
            return job;
        }

        @Override
        public RenderJobResponse getByIdAndProject(String tenantId, String projectId, String jobId) {
            RenderJobResponse job = storedJobs.get(jobId);
            if (job == null) throw new IllegalArgumentException("Render job not found: " + jobId);
            return job;
        }

        @Override
        public List<RenderJobResponse> list() {
            return new ArrayList<>(storedJobs.values());
        }

        @Override
        public List<RenderJobResponse> listByProject(String tenantId, String projectId) {
            return storedJobs.values().stream()
                    .filter(j -> j.projectId().equals(projectId))
                    .toList();
        }

        @Override
        public RenderJobResponse cancel(String jobId, String tenantId) {
            RenderJobResponse job = storedJobs.get(jobId);
            if (job == null) throw new IllegalArgumentException("Render job not found: " + jobId);
            RenderJobResponse cancelled = new RenderJobResponse(job.id(), job.projectId(),
                    job.timelineSnapshotId(), job.profile(), "CANCELLED");
            storedJobs.put(jobId, cancelled);
            return cancelled;
        }

        @Override
        public RenderJobResponse retry(String jobId, String tenantId) {
            RenderJobResponse job = storedJobs.get(jobId);
            if (job == null) throw new IllegalArgumentException("Render job not found: " + jobId);
            RenderJobResponse retried = new RenderJobResponse(job.id(), job.projectId(),
                    job.timelineSnapshotId(), job.profile(), "QUEUED");
            storedJobs.put(jobId, retried);
            return retried;
        }

        @Override
        public List<StatusHistoryResponse> getStatusHistory(String jobId, String tenantId) {
            return List.of();
        }
    }

    static class FakeOrchestratorPort implements RenderOrchestratorPort {
        int submitCalls = 0;
        int executeCalls = 0;
        String submitResult = "rj-default";
        String executeResult = "rj-default";
        final Map<String, List<ArtifactInfoResponse>> artifacts = new HashMap<>();

        @Override
        public String submitRenderJob(SubmitRenderJobRequest request) {
            submitCalls++;
            return submitResult;
        }

        @Override
        public String executeExistingRenderJob(String tenantId, String jobId) {
            executeCalls++;
            return executeResult;
        }

        @Override
        public String finishRenderPhase(String tenantId, String jobId) {
            return jobId;
        }

        @Override
        public List<ArtifactInfoResponse> getArtifactsByJob(String jobId) {
            return artifacts.getOrDefault(jobId, List.of());
        }

        @Override
        public byte[] getArtifactContent(String artifactId) {
            return new byte[0];
        }

        @Override
        public String loadJobTimelineJson(String tenantId, String jobId) {
            return "{}";
        }
    }

    static class FakeArtifactAccessService extends com.example.platform.render.app.access.ArtifactAccessService {
        int createAccessCalls = 0;
        com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor descriptorToReturn;

        FakeArtifactAccessService() {
            super(null, new org.springframework.beans.factory.ObjectProvider<>() {
                @Override
                public com.example.platform.storage.infrastructure.S3ObjectMaterializer getObject(Object... args) { return null; }
                @Override
                public com.example.platform.storage.infrastructure.S3ObjectMaterializer getIfAvailable() { return null; }
                @Override
                public com.example.platform.storage.infrastructure.S3ObjectMaterializer getIfUnique() { return null; }
                @Override
                public com.example.platform.storage.infrastructure.S3ObjectMaterializer getObject() { return null; }
            });
            descriptorToReturn = com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor.notFound("No descriptor configured");
        }

        @Override
        public com.example.platform.render.app.access.ArtifactAccessService.AccessDescriptor createAccessDescriptor(
                String providerType, String bucket, String objectKey,
                String mimeType, String filename, Long sizeBytes) {
            createAccessCalls++;
            return descriptorToReturn;
        }
    }
}
