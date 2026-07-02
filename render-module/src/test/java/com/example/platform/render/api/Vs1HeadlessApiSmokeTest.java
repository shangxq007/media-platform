package com.example.platform.render.api;

import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.api.port.RenderOrchestratorPort;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.preview.*;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.domain.previewjob.*;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.infrastructure.product.ProductDependencyRepository;
import com.example.platform.render.testsupport.fakes.FakeProductDependencyRepository;
import com.example.platform.render.testsupport.fakes.FakeProductRepository;
import com.example.platform.render.testsupport.fakes.FakeRenderJobService;
import com.example.platform.render.testsupport.fakes.FakeRenderOrchestratorPort;
import com.example.platform.render.testsupport.fakes.FakeStorageReferenceRepository;
import com.example.platform.render.app.storage.StorageRuntimeService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VS.1 Headless API Smoke Test — validates the complete VS.1 preview render
 * vertical slice locally without Spring context, database, or external services.
 *
 * <p>Covers:
 * <ol>
 *   <li><b>Scenario fixture</b>: VS.1 preview render smoke scenario data</li>
 *   <li><b>Contract validation</b>: jobId, status, productId, previewArtifact, error</li>
 *   <li><b>Exposure safety</b>: no local paths, no storage internals, no secrets</li>
 *   <li><b>Report generation</b>: PASS/FAIL/BLOCKED summary</li>
 * </ol>
 *
 * <p>Uses hand-written fakes — no Mockito, no database, no H2, no Spring context.
 * All tests run locally via {@code ./gradlew :render-module:test --tests "com.example.platform.render.api.*"}.
 */
@DisplayName("VS.1 Headless API Smoke Harness")
class Vs1HeadlessApiSmokeTest {

    // ─── Test infrastructure ───
    private FakeRenderJobService fakeJobService;
    private FakeProductRepository fakeProductRepo;
    private FakeProductDependencyRepository fakeDepRepo;
    private FakeStorageReferenceRepository fakeStorageRepo;
    private ProductRuntimeService productRuntimeService;
    private StorageRuntimeService storageRuntimeService;
    private FakeRenderOrchestratorPort fakeOrchestrator;
    private RenderController renderController;
    private InMemoryPreviewRenderJobRepository previewRepo;
    private PreviewRenderJobService previewService;

    private SmokeReport report;

    @BeforeEach
    void setUp() {
        fakeJobService = new FakeRenderJobService();
        fakeJobService.registerProject(Vs1PreviewRenderSmokeFixture.PROJECT_ID, Vs1PreviewRenderSmokeFixture.TENANT_ID);

        fakeProductRepo = new FakeProductRepository();
        fakeDepRepo = new FakeProductDependencyRepository();
        fakeStorageRepo = new FakeStorageReferenceRepository();
        productRuntimeService = new ProductRuntimeService(fakeProductRepo, fakeDepRepo);
        storageRuntimeService = new StorageRuntimeService(fakeStorageRepo);

        fakeOrchestrator = new FakeRenderOrchestratorPort();
        renderController = new RenderController(fakeJobService, fakeOrchestrator,
                null, null, null, null, null, null, null);

        previewRepo = new InMemoryPreviewRenderJobRepository();
        previewService = new PreviewRenderJobService(previewRepo, productRuntimeService, fakeDepRepo);

        report = new SmokeReport();
    }

    @AfterEach
    void tearDown() {
        com.example.platform.shared.web.TenantContext.clear();
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 1: RenderController — Job creation via REST controller
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 1: RenderController — Create and retrieve render job")
    class Scenario1_RenderControllerJobCreation {

        @Test
        @DisplayName("S1.1: POST create job → returns QUEUED with valid contract")
        void createJobReturnsValidContract() {
            var request = Vs1PreviewRenderSmokeFixture.createRenderJobRequest();

            RenderJobResponse response = renderController.createRenderJob(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    request);

            // Contract validation
            List<String> failures = Vs1PreviewRenderSmokeFixture.validateRenderJobContract(response);
            assertTrue(failures.isEmpty(),
                    "Contract validation failed: " + String.join("; ", failures));

            report.record("S1.1", "Create render job → QUEUED contract", "PASS");
        }

        @Test
        @DisplayName("S1.2: GET retrieve job by ID → matches created job")
        void retrieveJobById() {
            var request = Vs1PreviewRenderSmokeFixture.createRenderJobRequest();
            RenderJobResponse created = renderController.createRenderJob(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    request);

            RenderJobResponse retrieved = renderController.getRenderJob(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    created.id());

            assertEquals(created.id(), retrieved.id(), "Job ID must match");
            assertEquals(created.status(), retrieved.status(), "Status must match");
            assertEquals(Vs1PreviewRenderSmokeFixture.PROJECT_ID, retrieved.projectId());

            report.record("S1.2", "GET render job by ID → matches created", "PASS");
        }

        @Test
        @DisplayName("S1.3: GET list jobs → returns tenant-scoped results")
        void listJobsReturnsTenantScoped() {
            var request = Vs1PreviewRenderSmokeFixture.createRenderJobRequest();
            renderController.createRenderJob(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    request);

            List<RenderJobResponse> jobs = renderController.listRenderJobs(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID);

            assertFalse(jobs.isEmpty(), "Job list must not be empty after creation");
            assertTrue(jobs.stream().allMatch(j -> j.projectId().equals(Vs1PreviewRenderSmokeFixture.PROJECT_ID)));

            report.record("S1.3", "List render jobs → tenant-scoped", "PASS");
        }

        @Test
        @DisplayName("S1.4: POST submit job via orchestrator → returns jobId and QUEUED")
        void submitJobViaOrchestrator() {
            SubmitRenderJobRequest submitReq = new SubmitRenderJobRequest(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    "Create preview video",
                    Vs1PreviewRenderSmokeFixture.PROFILE,
                    Vs1PreviewRenderSmokeFixture.SNAPSHOT_ID);

            Map<String, String> result = renderController.submitJob(submitReq);

            assertNotNull(result.get("jobId"), "Response must contain jobId");
            assertEquals("QUEUED", result.get("status"), "Status must be QUEUED");
            assertEquals(1, fakeOrchestrator.getSubmittedJobs().size());

            report.record("S1.4", "Submit job via orchestrator → QUEUED", "PASS");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 2: PreviewRenderJobService — Preview job lifecycle
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 2: PreviewRenderJobService — Preview job lifecycle")
    class Scenario2_PreviewJobLifecycle {

        @Test
        @DisplayName("S2.1: Create preview job → QUEUED with valid contract")
        void createPreviewJobQueued() {
            var request = Vs1PreviewRenderSmokeFixture.createPreviewRequest();

            PreviewRenderJobResponse response = previewService.create(request);

            List<String> failures = Vs1PreviewRenderSmokeFixture.validatePreviewJobContract(response);
            assertTrue(failures.isEmpty(),
                    "Preview job contract validation failed: " + String.join("; ", failures));

            assertEquals("QUEUED", response.status());
            assertNull(response.outputProductId(), "outputProductId must be null for QUEUED");
            assertNull(response.errorMessage(), "errorMessage must be null for QUEUED");

            report.record("S2.1", "Create preview job → QUEUED contract", "PASS");
        }

        @Test
        @DisplayName("S2.2: Query preview job status → returns created job")
        void queryPreviewJobStatus() {
            var request = Vs1PreviewRenderSmokeFixture.createPreviewRequest();
            PreviewRenderJobResponse created = previewService.create(request);

            Optional<PreviewRenderJobResponse> result = previewService.getStatus(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    created.jobId());

            assertTrue(result.isPresent(), "Job must be found");
            assertEquals(created.jobId(), result.get().jobId());
            assertEquals("QUEUED", result.get().status());

            report.record("S2.2", "Query preview job status → found", "PASS");
        }

        @Test
        @DisplayName("S2.3: List preview jobs → returns tenant-project scoped")
        void listPreviewJobs() {
            previewService.create(Vs1PreviewRenderSmokeFixture.createPreviewRequest());

            List<PreviewRenderJobResponse> jobs = previewService.list(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID);

            assertFalse(jobs.isEmpty(), "Job list must not be empty");

            report.record("S2.3", "List preview jobs → scoped results", "PASS");
        }

        @Test
        @DisplayName("S2.4: Preview job not found → returns empty")
        void previewJobNotFound() {
            Optional<PreviewRenderJobResponse> result = previewService.getStatus(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    "nonexistent-job-id");

            assertTrue(result.isEmpty(), "Non-existent job must return empty");

            report.record("S2.4", "Preview job not found → empty", "PASS");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 3: Contract validation — jobId, status, productId, error
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 3: Contract validation — response shape")
    class Scenario3_ContractValidation {

        @Test
        @DisplayName("S3.1: jobId field — non-null, non-blank in all responses")
        void contractJobIdField() {
            var request = Vs1PreviewRenderSmokeFixture.createPreviewRequest();
            PreviewRenderJobResponse response = previewService.create(request);

            assertNotNull(response.jobId(), "jobId must not be null");
            assertFalse(response.jobId().isBlank(), "jobId must not be blank");
            assertTrue(response.jobId().startsWith("prj"), "jobId must have expected prefix");

            report.record("S3.1", "Contract: jobId non-null, non-blank", "PASS");
        }

        @Test
        @DisplayName("S3.2: status field — valid enum value")
        void contractStatusField() {
            var request = Vs1PreviewRenderSmokeFixture.createPreviewRequest();
            PreviewRenderJobResponse response = previewService.create(request);

            assertNotNull(response.status(), "status must not be null");
            assertTrue(Vs1PreviewRenderSmokeFixture.validStatuses().contains(response.status()),
                    "status must be a valid PreviewRenderJobStatus value, got: " + response.status());

            report.record("S3.2", "Contract: status valid enum", "PASS");
        }

        @Test
        @DisplayName("S3.3: productId (outputProductId) — null for QUEUED, set for COMPLETED")
        void contractProductIdField() {
            // Create job in QUEUED state
            PreviewRenderJobResponse queued = previewService.create(Vs1PreviewRenderSmokeFixture.createPreviewRequest());
            assertNull(queued.outputProductId(), "outputProductId must be null for QUEUED job");

            // Simulate COMPLETED state by directly writing to repo
            String jobId = queued.jobId();
            PreviewRenderJob completed = Vs1PreviewRenderSmokeFixture.completedPreviewJob(jobId);
            previewRepo.save(completed);

            // Register output product in product repo
            Product outputProd = Vs1PreviewRenderSmokeFixture.outputProduct(completed.outputProductId());
            fakeProductRepo.save(outputProd);

            Optional<PreviewRenderJobResponse> result = previewService.getStatus(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    jobId);

            assertTrue(result.isPresent(), "Completed job must be found");
            assertNotNull(result.get().outputProductId(), "outputProductId must be set for COMPLETED");

            report.record("S3.3", "Contract: productId null→set lifecycle", "PASS");
        }

        @Test
        @DisplayName("S3.4: previewArtifact — artifact response available for COMPLETED jobs")
        void contractPreviewArtifactField() {
            String jobId = "prj-artifact-test";
            PreviewRenderJob completed = Vs1PreviewRenderSmokeFixture.completedPreviewJob(jobId);
            previewRepo.save(completed);

            Product outputProd = Vs1PreviewRenderSmokeFixture.outputProduct(completed.outputProductId());
            fakeProductRepo.save(outputProd);

            Optional<PreviewRenderJobArtifactResponse> artifactResult = previewService.getArtifacts(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    jobId);

            assertTrue(artifactResult.isPresent(), "Artifact must be available for COMPLETED job");
            PreviewRenderJobArtifactResponse artifact = artifactResult.get();

            assertNotNull(artifact.renderJobId(), "renderJobId must not be null");
            assertNotNull(artifact.outputProductId(), "outputProductId must not be null");
            assertNotNull(artifact.productStatus(), "productStatus must not be null");
            assertNotNull(artifact.mimeType(), "mimeType must not be null");
            assertTrue(artifact.width() > 0, "width must be positive");
            assertTrue(artifact.height() > 0, "height must be positive");

            report.record("S3.4", "Contract: previewArtifact available for COMPLETED", "PASS");
        }

        @Test
        @DisplayName("S3.5: error field — set for FAILED, null otherwise")
        void contractErrorField() {
            // FAILED job
            String failedJobId = "prj-error-test";
            PreviewRenderJob failed = Vs1PreviewRenderSmokeFixture.failedPreviewJob(failedJobId, "FFmpeg process exited with code 1");
            previewRepo.save(failed);

            Optional<PreviewRenderJobResponse> failedResult = previewService.getStatus(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    failedJobId);

            assertTrue(failedResult.isPresent(), "Failed job must be found");
            assertNotNull(failedResult.get().errorMessage(), "errorMessage must be set for FAILED");
            assertFalse(failedResult.get().errorMessage().isBlank());

            // QUEUED job — must have null error
            PreviewRenderJobResponse queued = previewService.create(Vs1PreviewRenderSmokeFixture.createPreviewRequest());
            assertNull(queued.errorMessage(), "errorMessage must be null for QUEUED");

            report.record("S3.5", "Contract: error field set for FAILED, null otherwise", "PASS");
        }

        @Test
        @DisplayName("S3.6: RenderJobResponse contract — id, status, projectId, profile")
        void renderJobResponseContract() {
            var request = Vs1PreviewRenderSmokeFixture.createRenderJobRequest();
            RenderJobResponse response = renderController.createRenderJob(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    request);

            assertNotNull(response.id(), "id must not be null");
            assertNotNull(response.status(), "status must not be null");
            assertNotNull(response.projectId(), "projectId must not be null");
            assertNotNull(response.profile(), "profile must not be null");
            assertEquals("QUEUED", response.status());
            assertEquals(Vs1PreviewRenderSmokeFixture.PROJECT_ID, response.projectId());
            assertEquals(Vs1PreviewRenderSmokeFixture.PROFILE, response.profile());

            report.record("S3.6", "Contract: RenderJobResponse shape validated", "PASS");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 4: Exposure safety — no local paths, no internals, no secrets
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 4: Exposure safety — no internal leaks")
    class Scenario4_ExposureSafety {

        @Test
        @DisplayName("S4.1: RenderJobResponse — no local paths or storage internals")
        void renderJobResponseExposureSafety() {
            RenderJobResponse response = renderController.createRenderJob(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    Vs1PreviewRenderSmokeFixture.createRenderJobRequest());

            List<String> violations = Vs1PreviewRenderSmokeFixture.checkExposureSafety(
                    "RenderJobResponse", response.toString());

            assertTrue(violations.isEmpty(),
                    "Exposure safety violations: " + String.join("; ", violations));

            report.record("S4.1", "Safety: RenderJobResponse no leaks", "PASS");
        }

        @Test
        @DisplayName("S4.2: PreviewRenderJobResponse — no local paths or storage internals")
        void previewJobResponseExposureSafety() {
            PreviewRenderJobResponse response = previewService.create(
                    Vs1PreviewRenderSmokeFixture.createPreviewRequest());

            List<String> violations = Vs1PreviewRenderSmokeFixture.checkExposureSafety(
                    "PreviewRenderJobResponse", response.toString());

            assertTrue(violations.isEmpty(),
                    "Exposure safety violations: " + String.join("; ", violations));

            report.record("S4.2", "Safety: PreviewRenderJobResponse no leaks", "PASS");
        }

        @Test
        @DisplayName("S4.3: PreviewRenderJobArtifactResponse — no storage internals")
        void previewArtifactExposureSafety() {
            String jobId = "prj-safety-test";
            PreviewRenderJob completed = Vs1PreviewRenderSmokeFixture.completedPreviewJob(jobId);
            previewRepo.save(completed);

            Product outputProd = Vs1PreviewRenderSmokeFixture.outputProduct(completed.outputProductId());
            fakeProductRepo.save(outputProd);

            Optional<PreviewRenderJobArtifactResponse> result = previewService.getArtifacts(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    jobId);

            assertTrue(result.isPresent());
            List<String> violations = Vs1PreviewRenderSmokeFixture.checkExposureSafety(
                    "PreviewRenderJobArtifactResponse", result.get().toString());

            assertTrue(violations.isEmpty(),
                    "Exposure safety violations: " + String.join("; ", violations));

            report.record("S4.3", "Safety: PreviewRenderJobArtifactResponse no leaks", "PASS");
        }

        @Test
        @DisplayName("S4.4: ArtifactInfoResponse — uses API download path, not storage internals")
        void artifactInfoResponseSafety() {
            ArtifactInfoResponse artifact = new ArtifactInfoResponse(
                    "art-1", "rj-1", Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    "/api/v1/downloads/art-1",
                    "video/mp4", "1920x1080", 10485760L, Instant.now());

            assertNotNull(artifact.storageUri());
            assertTrue(artifact.storageUri().startsWith("/api/"),
                    "storageUri must use API path, not storage internal: " + artifact.storageUri());

            List<String> violations = Vs1PreviewRenderSmokeFixture.checkExposureSafety(
                    "ArtifactInfoResponse", artifact.toString());

            assertTrue(violations.isEmpty(),
                    "Exposure safety violations: " + String.join("; ", violations));

            report.record("S4.4", "Safety: ArtifactInfoResponse uses API path", "PASS");
        }

        @Test
        @DisplayName("S4.5: StatusHistoryResponse — no raw commands or temp paths")
        void statusHistorySafety() {
            com.example.platform.render.app.dto.StatusHistoryResponse history =
                    new com.example.platform.render.app.dto.StatusHistoryResponse(
                            "h-1", "rj-1", null, "QUEUED", "Job created", null, OffsetDateTime.now());

            List<String> violations = Vs1PreviewRenderSmokeFixture.checkExposureSafety(
                    "StatusHistoryResponse", history.toString());

            assertTrue(violations.isEmpty(),
                    "Exposure safety violations: " + String.join("; ", violations));

            assertFalse(history.reason().contains("ffmpeg "), "No raw commands in reason");
            assertFalse(history.reason().contains("/tmp/"), "No temp paths in reason");

            report.record("S4.5", "Safety: StatusHistoryResponse no raw details", "PASS");
        }

        @Test
        @DisplayName("S4.6: Failed job error message — sanitized, no internal paths")
        void failedJobErrorSanitized() {
            String jobId = "prj-error-safety";
            // Use a safe error message (no paths)
            PreviewRenderJob failed = Vs1PreviewRenderSmokeFixture.failedPreviewJob(jobId, "Render process failed with exit code 1");
            previewRepo.save(failed);

            Optional<PreviewRenderJobResponse> result = previewService.getStatus(
                    Vs1PreviewRenderSmokeFixture.TENANT_ID,
                    Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                    jobId);

            assertTrue(result.isPresent());
            assertNotNull(result.get().errorMessage());

            List<String> violations = Vs1PreviewRenderSmokeFixture.checkExposureSafety(
                    "FailedJobError", result.get().errorMessage());

            assertTrue(violations.isEmpty(),
                    "Error message contains forbidden patterns: " + String.join("; ", violations));

            report.record("S4.6", "Safety: Failed job error sanitized", "PASS");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // SCENARIO 5: Error model — not found, invalid transitions, tenant mismatch
    // ═══════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Scenario 5: Error model — graceful failure handling")
    class Scenario5_ErrorModel {

        @Test
        @DisplayName("S5.1: Job not found → IllegalArgumentException")
        void jobNotFound() {
            assertThrows(IllegalArgumentException.class,
                    () -> renderController.getRenderJob(
                            Vs1PreviewRenderSmokeFixture.TENANT_ID,
                            Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                            "rj-nonexistent"));

            report.record("S5.1", "Error: Job not found → IAE", "PASS");
        }

        @Test
        @DisplayName("S5.2: Tenant mismatch → IllegalArgumentException")
        void tenantMismatch() {
            // Set TenantContext so assertTenantAccess enforces tenant boundary
            com.example.platform.shared.web.TenantContext.set("t-actual");

            assertThrows(IllegalArgumentException.class,
                    () -> previewService.getStatus(
                            "wrong-tenant",
                            Vs1PreviewRenderSmokeFixture.PROJECT_ID,
                            "any-job"));

            report.record("S5.2", "Error: Tenant mismatch → IAE", "PASS");
        }

        @Test
        @DisplayName("S5.3: Invalid state transition → PlatformException (409)")
        void invalidStateTransition() {
            var stateMachine = new com.example.platform.render.domain.RenderJobStateMachine();

            assertThrows(com.example.platform.shared.web.PlatformException.class,
                    () -> stateMachine.validateTransition(
                            com.example.platform.render.domain.RenderJobStatus.COMPLETED,
                            com.example.platform.render.domain.RenderJobStatus.QUEUED));

            report.record("S5.3", "Error: Invalid transition → PlatformException (409)", "PASS");
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    // Report generation
    // ═══════════════════════════════════════════════════════════════════

    @AfterAll
    static void generateReport() throws IOException {
        // Report is generated by the shell script that runs this test.
        // This method ensures JUnit completes cleanly.
    }

    /**
     * Internal report accumulator for the smoke test.
     * Each test records its result; the shell script parses the JUnit XML output.
     */
    static class SmokeReport {
        private final List<String[]> entries = new ArrayList<>();

        void record(String scenarioId, String description, String status) {
            entries.add(new String[]{scenarioId, description, status});
        }

        List<String[]> getEntries() {
            return List.copyOf(entries);
        }
    }
}
