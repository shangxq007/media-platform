package com.example.platform.render.integration;

import com.example.platform.render.api.RenderController;
import com.example.platform.render.api.Vs1PreviewRenderSmokeFixture;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.app.output.RenderOutputRegistrationException;
import com.example.platform.render.app.preview.*;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.domain.previewjob.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.StorageClass;
import com.example.platform.render.domain.storage.StorageProviderType;
import com.example.platform.render.domain.storage.StorageReference;
import com.example.platform.render.testsupport.fakes.*;
import com.example.platform.shared.web.TenantContext;

import java.time.Instant;
import java.util.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VS.1 Preview Render Smoke Integration Test — end-to-end flow for the complete
 * preview render vertical slice including contract validation and exposure safety.
 *
 * <p>Proves the VS.1 smoke harness works:
 * <ol>
 *   <li>Render job creation → retrieval → lifecycle via RenderController</li>
 *   <li>Preview job creation → lifecycle → artifact via PreviewRenderJobService</li>
 *   <li>Contract validation: jobId, status, productId, previewArtifact, error</li>
 *   <li>Exposure safety: no local paths, no storage internals, no secrets</li>
 * </ol>
 *
 * <p>Does NOT use H2, Spring context, or real database.
 * Uses hand-written fakes for all infrastructure dependencies.
 */
@DisplayName("VS.1 Preview Render Smoke Integration")
class Vs1PreviewRenderSmokeIntegrationTest {

    private FakeRenderJobService fakeJobService;
    private FakeProductRepository fakeProductRepo;
    private FakeProductDependencyRepository fakeDepRepo;
    private FakeStorageReferenceRepository fakeStorageRepo;
    private ProductRuntimeService productRuntimeService;
    private StorageRuntimeService storageRuntimeService;
    private FakeRenderOrchestratorPort fakeOrchestrator;
    private RenderController renderController;
    private RenderJobStateMachine stateMachine;
    private InMemoryPreviewRenderJobRepository previewRepo;
    private PreviewRenderJobService previewService;

    @BeforeEach
    void setUp() {
        fakeJobService = new FakeRenderJobService();
        fakeJobService.registerProject("proj-1", "t-1");
        fakeJobService.registerProject("proj-2", "t-1");

        fakeProductRepo = new FakeProductRepository();
        fakeDepRepo = new FakeProductDependencyRepository();
        fakeStorageRepo = new FakeStorageReferenceRepository();
        productRuntimeService = new ProductRuntimeService(fakeProductRepo, fakeDepRepo);
        storageRuntimeService = new StorageRuntimeService(fakeStorageRepo);

        fakeOrchestrator = new FakeRenderOrchestratorPort();
        renderController = new RenderController(fakeJobService, fakeOrchestrator,
                null, null, null, null, null, null, null);
        stateMachine = new RenderJobStateMachine();

        previewRepo = new InMemoryPreviewRenderJobRepository();
        previewService = new PreviewRenderJobService(previewRepo, productRuntimeService, fakeDepRepo);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Full VS.1 flow: Render job creation → retrieval → lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VS.1 Full Flow: Render job lifecycle")
    class FullRenderJobLifecycle {

        @Test
        @DisplayName("Create → Get → Verify → Submit → Start → Execute → Artifacts")
        void fullFlow() {
            // Step 1: Create job via controller
            var createReq = Vs1PreviewRenderSmokeFixture.createRenderJobRequest();
            RenderJobResponse created = renderController.createRenderJob("t-1", "proj-1", createReq);

            assertNotNull(created.id(), "Created job must have ID");
            assertEquals("QUEUED", created.status());
            assertEquals("proj-1", created.projectId());

            // Step 2: Retrieve by ID
            RenderJobResponse retrieved = renderController.getRenderJob("t-1", "proj-1", created.id());
            assertEquals(created.id(), retrieved.id());
            assertEquals("QUEUED", retrieved.status());

            // Step 3: List jobs
            List<RenderJobResponse> jobs = renderController.listRenderJobs("t-1", "proj-1");
            assertTrue(jobs.size() >= 1, "Must have at least 1 job");

            // Step 4: Submit via orchestrator
            SubmitRenderJobRequest submitReq = new SubmitRenderJobRequest(
                    "t-1", "proj-1", "Generate preview", "default_1080p", "snap-1");
            Map<String, String> submitResult = renderController.submitJob(submitReq);
            assertNotNull(submitResult.get("jobId"));
            assertEquals("QUEUED", submitResult.get("status"));

            // Step 5: Start job
            fakeJobService.seedJob("rj-start", "proj-1", "t-1", "snap-1", "default_1080p", "QUEUED");
            Map<String, String> startResult = renderController.startRenderJob("t-1", "proj-1", "rj-start");
            assertEquals("STARTED", startResult.get("status"));

            // Step 6: Execute local
            fakeJobService.seedJob("rj-local", "proj-1", "t-1", "snap-1", "default_1080p", "QUEUED");
            Map<String, String> execResult = renderController.executeLocal("t-1", "proj-1", "rj-local");
            assertEquals("COMPLETED", execResult.get("status"));
        }

        @Test
        @DisplayName("Lifecycle state machine: QUEUED → SELECTING → PROVIDER_SELECTED → EXECUTING → COMPLETING → COMPLETED")
        void lifecycleStateTransitions() {
            assertEquals(RenderJobStatus.SELECTING_PROVIDER,
                    stateMachine.transition("rj-1", RenderJobStatus.QUEUED,
                            RenderJobStatus.SELECTING_PROVIDER, "Provider resolution", "Orch"));

            assertEquals(RenderJobStatus.PROVIDER_SELECTED,
                    stateMachine.transition("rj-1", RenderJobStatus.SELECTING_PROVIDER,
                            RenderJobStatus.PROVIDER_SELECTED, "Provider resolved", "Orch"));

            assertEquals(RenderJobStatus.EXECUTING,
                    stateMachine.transition("rj-1", RenderJobStatus.PROVIDER_SELECTED,
                            RenderJobStatus.EXECUTING, "Render started", "Orch"));

            assertEquals(RenderJobStatus.COMPLETING,
                    stateMachine.transition("rj-1", RenderJobStatus.EXECUTING,
                            RenderJobStatus.COMPLETING, "Render finished", "Orch"));

            assertEquals(RenderJobStatus.COMPLETED,
                    stateMachine.transition("rj-1", RenderJobStatus.COMPLETING,
                            RenderJobStatus.COMPLETED, "Artifacts registered", "Orch"));

            assertEquals(5, stateMachine.getTransitionHistory("rj-1").size());
            assertEquals(RenderJobStatus.COMPLETED, stateMachine.getCurrentState("rj-1"));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Preview job flow: Create → Execute → Complete → Artifacts
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("VS.1 Preview Job Flow: Create → Lifecycle → Artifacts")
    class PreviewJobFlow {

        @Test
        @DisplayName("Create preview job → complete → get artifacts")
        void previewJobFullFlow() {
            // Step 1: Create
            PreviewRenderJobResponse created = previewService.create(
                    Vs1PreviewRenderSmokeFixture.createPreviewRequest());

            assertNotNull(created.jobId());
            assertEquals("QUEUED", created.status());
            assertNull(created.outputProductId());

            // Step 2: Simulate execution and completion
            PreviewRenderJob completed = Vs1PreviewRenderSmokeFixture.completedPreviewJob(created.jobId());
            previewRepo.save(completed);

            // Step 3: Register output product
            Product outputProd = Vs1PreviewRenderSmokeFixture.outputProduct(completed.outputProductId());
            fakeProductRepo.save(outputProd);

            // Step 4: Query completed status
            Optional<PreviewRenderJobResponse> completedResult = previewService.getStatus(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    created.jobId());

            assertTrue(completedResult.isPresent());
            assertEquals("COMPLETED", completedResult.get().status());
            assertNotNull(completedResult.get().outputProductId(),
                    "Completed job must have outputProductId (productId)");

            // Step 5: Get artifacts
            Optional<PreviewRenderJobArtifactResponse> artifacts = previewService.getArtifacts(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    created.jobId());

            assertTrue(artifacts.isPresent(), "Completed job must have artifacts");
            assertEquals(created.jobId(), artifacts.get().renderJobId());
            assertNotNull(artifacts.get().outputProductId());
            assertNotNull(artifacts.get().mimeType());
        }

        @Test
        @DisplayName("Failed preview job → error message available")
        void failedPreviewJob() {
            String jobId = "prj-failed-flow";
            PreviewRenderJob failed = Vs1PreviewRenderSmokeFixture.failedPreviewJob(jobId,
                    "FFmpeg encoder not available");
            previewRepo.save(failed);

            Optional<PreviewRenderJobResponse> result = previewService.getStatus(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    jobId);

            assertTrue(result.isPresent());
            assertEquals("FAILED", result.get().status());
            assertNotNull(result.get().errorMessage());
            assertEquals("FFmpeg encoder not available", result.get().errorMessage());

            // Artifacts should be empty for failed jobs
            Optional<PreviewRenderJobArtifactResponse> artifacts = previewService.getArtifacts(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    jobId);
            assertTrue(artifacts.isEmpty(), "Failed job must not have artifacts");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Contract validation
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Contract validation: response shape")
    class ContractValidation {

        @Test
        @DisplayName("RenderJobResponse contract: id, projectId, timelineSnapshotId, profile, status")
        void renderJobResponseContract() {
            RenderJobResponse response = renderController.createRenderJob(
                    "t-1", "proj-1",
                    new com.example.platform.render.app.dto.CreateRenderJobRequest("proj-1", "snap-1", "default_1080p"));

            // All fields present
            assertNotNull(response.id(), "id must not be null");
            assertNotNull(response.projectId(), "projectId must not be null");
            assertNotNull(response.timelineSnapshotId(), "timelineSnapshotId must not be null");
            assertNotNull(response.profile(), "profile must not be null");
            assertNotNull(response.status(), "status must not be null");

            // Correct values
            assertEquals("proj-1", response.projectId());
            assertEquals("snap-1", response.timelineSnapshotId());
            assertEquals("default_1080p", response.profile());
            assertEquals("QUEUED", response.status());
        }

        @Test
        @DisplayName("PreviewRenderJobResponse contract: jobId, status, outputProductId, errorMessage")
        void previewJobResponseContract() {
            PreviewRenderJobResponse response = previewService.create(
                    Vs1PreviewRenderSmokeFixture.createPreviewRequest());

            // jobId
            assertNotNull(response.jobId());
            assertFalse(response.jobId().isBlank());

            // status
            assertNotNull(response.status());
            assertEquals("QUEUED", response.status());

            // outputProductId (null for non-completed)
            assertNull(response.outputProductId());

            // errorMessage (null for non-failed)
            assertNull(response.errorMessage());

            // Required metadata
            assertEquals(Vs1PreviewRenderSmokeFixture.TENANT_ID, response.tenantId());
            assertEquals(Vs1PreviewRenderSmokeFixture.PROJECT_ID, response.projectId());
            assertEquals(Vs1PreviewRenderSmokeFixture.SNAPSHOT_ID, response.snapshotId());
            assertEquals(Vs1PreviewRenderSmokeFixture.PROFILE, response.profile());
        }

        @Test
        @DisplayName("PreviewRenderJobArtifactResponse contract: renderJobId, outputProductId, mimeType, dimensions")
        void previewArtifactResponseContract() {
            String jobId = "prj-contract-test";
            PreviewRenderJob completed = Vs1PreviewRenderSmokeFixture.completedPreviewJob(jobId);
            previewRepo.save(completed);
            fakeProductRepo.save(Vs1PreviewRenderSmokeFixture.outputProduct(completed.outputProductId()));

            Optional<PreviewRenderJobArtifactResponse> result = previewService.getArtifacts(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    jobId);

            assertTrue(result.isPresent());
            PreviewRenderJobArtifactResponse artifact = result.get();

            assertEquals(jobId, artifact.renderJobId());
            assertNotNull(artifact.outputProductId());
            assertNotNull(artifact.productStatus());
            assertNotNull(artifact.mimeType());
            assertTrue(artifact.width() > 0);
            assertTrue(artifact.height() > 0);
            assertTrue(artifact.fps() > 0);
            assertTrue(artifact.durationSeconds() > 0);
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Exposure safety: no leaks in any response
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Exposure safety: no internal leaks")
    class ExposureSafety {

        @Test
        @DisplayName("All response DTOs are free of local paths and storage internals")
        void allResponseDtosSafe() {
            // RenderJobResponse
            RenderJobResponse renderJob = renderController.createRenderJob(
                    "t-1", "proj-1",
                    new com.example.platform.render.app.dto.CreateRenderJobRequest("proj-1", "snap-1", "default_1080p"));
            assertNoLeaks("RenderJobResponse", renderJob.toString());

            // PreviewRenderJobResponse
            PreviewRenderJobResponse previewJob = previewService.create(
                    Vs1PreviewRenderSmokeFixture.createPreviewRequest());
            assertNoLeaks("PreviewRenderJobResponse", previewJob.toString());

            // ArtifactInfoResponse
            ArtifactInfoResponse artifactInfo = new ArtifactInfoResponse(
                    "art-1", "rj-1", "proj-1", "/api/v1/downloads/art-1",
                    "video/mp4", "1920x1080", 10485760L, Instant.now());
            assertNoLeaks("ArtifactInfoResponse", artifactInfo.toString());

            // StatusHistoryResponse
            StatusHistoryResponse history = new StatusHistoryResponse(
                    "h-1", "rj-1", null, "QUEUED", "Job created", null,
                    java.time.OffsetDateTime.now());
            assertNoLeaks("StatusHistoryResponse", history.toString());

            // SubmitRenderJobRequest
            SubmitRenderJobRequest submitReq = new SubmitRenderJobRequest(
                    "t-1", "proj-1", "test", "default_1080p", "snap-1");
            assertNoLeaks("SubmitRenderJobRequest", submitReq.toString());
        }

        @Test
        @DisplayName("Completed preview artifact response has no storage internals")
        void completedArtifactNoStorageInternals() {
            String jobId = "prj-safety-int";
            PreviewRenderJob completed = Vs1PreviewRenderSmokeFixture.completedPreviewJob(jobId);
            previewRepo.save(completed);
            fakeProductRepo.save(Vs1PreviewRenderSmokeFixture.outputProduct(completed.outputProductId()));

            Optional<PreviewRenderJobArtifactResponse> artifacts = previewService.getArtifacts(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    jobId);

            assertTrue(artifacts.isPresent());
            assertNoLeaks("PreviewRenderJobArtifactResponse", artifacts.get().toString());
        }

        @Test
        @DisplayName("StorageReference has no signed URLs or access keys in toString")
        void storageReferenceNoSecrets() {
            StorageReference ref = Vs1PreviewRenderSmokeFixture.outputStorageReference();
            String str = ref.toString();

            assertFalse(str.contains("signedUrl"), "No signed URL in StorageReference");
            assertFalse(str.contains("accessKey"), "No access credentials");
            assertFalse(str.contains("secretKey"), "No secret keys");
        }

        private void assertNoLeaks(String label, String toString) {
            List<String> violations = Vs1PreviewRenderSmokeFixture.checkExposureSafety(label, toString);
            assertTrue(violations.isEmpty(),
                    label + " exposure safety violations: " + String.join("; ", violations));
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Error model
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Error model in integration context")
    class ErrorModel {

        @Test
        @DisplayName("Job not found → 404 via IllegalArgumentException")
        void jobNotFound() {
            assertThrows(IllegalArgumentException.class,
                    () -> renderController.getRenderJob("t-1", "proj-1", "rj-missing"));
        }

        @Test
        @DisplayName("Invalid state transition → 409 via PlatformException")
        void invalidTransition() {
            assertThrows(com.example.platform.shared.web.PlatformException.class,
                    () -> stateMachine.validateTransition(
                            RenderJobStatus.COMPLETED, RenderJobStatus.QUEUED));
        }

        @Test
        @DisplayName("Tenant mismatch on cancel → IAE")
        void tenantMismatch() {
            TenantContext.set("t-1");
            assertThrows(IllegalArgumentException.class,
                    () -> renderController.cancelJob("rj-1", "t-other"));
        }

        @Test
        @DisplayName("Output registration exception carries job ID")
        void outputRegistrationException() {
            var ex = new com.example.platform.render.app.output.RenderOutputRegistrationException(
                    "rj-1", "Path traversal detected", false);
            assertEquals("rj-1", ex.jobId());
            assertFalse(ex.isProductRegistered());
        }
    }
}
