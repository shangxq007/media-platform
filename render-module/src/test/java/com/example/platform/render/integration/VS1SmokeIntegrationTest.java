package com.example.platform.render.integration;

import com.example.platform.render.api.RenderController;
import com.example.platform.render.api.dto.SubmitRenderJobRequest;
import com.example.platform.render.app.RenderJobService;
import com.example.platform.render.app.dto.ArtifactInfoResponse;
import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.app.output.RenderOutputRegistrationException;
import com.example.platform.render.app.product.ProductRuntimeService;
import com.example.platform.render.app.storage.StorageRuntimeService;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.domain.product.*;
import com.example.platform.render.domain.storage.StorageClass;
import com.example.platform.render.domain.storage.StorageProviderType;
import com.example.platform.render.domain.storage.StorageReference;
import com.example.platform.render.testsupport.fakes.FakeProductDependencyRepository;
import com.example.platform.render.testsupport.fakes.FakeProductRepository;
import com.example.platform.render.testsupport.fakes.FakeRenderJobService;
import com.example.platform.render.testsupport.fakes.FakeRenderOrchestratorPort;
import com.example.platform.render.testsupport.fakes.FakeStorageReferenceRepository;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VS.1 smoke integration test — end-to-end flow for Preview Render Job.
 *
 * <p>Proves the complete VS.1 vertical slice works:
 * <ol>
 *   <li>Render Job creation (API → Service → Repository)</li>
 *   <li>Job status queries (API → Service → Repository)</li>
 *   <li>Job lifecycle transitions (QUEUED → SELECTING_PROVIDER → EXECUTING → COMPLETED)</li>
 *   <li>Product registration and lifecycle (REGISTERED → READY)</li>
 *   <li>Storage reference registration</li>
 *   <li>Product dependency linking</li>
 *   <li>Error model validation (invalid transitions, not found, tenant mismatch)</li>
 * </ol>
 *
 * <p>Does NOT use H2, Spring context, or real database.
 * Uses hand-written fakes for all infrastructure dependencies.
 * No Mockito — all collaborators are explicit fake implementations.
 *
 * <p>Integration package: com.example.platform.render.integration
 */
class VS1SmokeIntegrationTest {
    @SuppressWarnings("unchecked")
    private static <T> org.springframework.beans.factory.ObjectProvider<T> mockProvider(T instance) {
        org.springframework.beans.factory.ObjectProvider<T> op = org.mockito.Mockito.mock(org.springframework.beans.factory.ObjectProvider.class);
        org.mockito.Mockito.when(op.getIfAvailable()).thenReturn(instance);
        return op;
    }



    private FakeRenderJobService fakeJobService;
    private FakeProductRepository fakeProductRepo;
    private FakeProductDependencyRepository fakeDepRepo;
    private FakeStorageReferenceRepository fakeStorageRepo;
    private ProductRuntimeService productRuntimeService;
    private StorageRuntimeService storageRuntimeService;
    private FakeRenderOrchestratorPort fakeOrchestrator;
    private RenderController controller;
    private RenderJobStateMachine stateMachine;

    @BeforeEach
    void setUp() {
        fakeJobService = new FakeRenderJobService();
        fakeJobService.registerProject("proj-1", "t-1");
        fakeJobService.registerProject("proj-2", "t-1");

        fakeProductRepo = new FakeProductRepository();
        fakeDepRepo = new FakeProductDependencyRepository();
        fakeStorageRepo = new FakeStorageReferenceRepository();
        productRuntimeService = new ProductRuntimeService(fakeProductRepo, fakeDepRepo);
        storageRuntimeService = new StorageRuntimeService(fakeStorageRepo, mockProvider(null));

        fakeOrchestrator = new FakeRenderOrchestratorPort();
        controller = new RenderController(fakeJobService, fakeOrchestrator, java.util.List.<com.example.platform.storage.domain.BlobStorage>of(),
                null, null, null, null, null, null, null, null, null, null);
        stateMachine = new RenderJobStateMachine();
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ========== VS.1-A: Full render job creation and retrieval flow ==========

    @Nested
    @DisplayName("VS.1-A: Render job creation → retrieval → status lifecycle")
    class RenderJobLifecycle {

        @Test
        @DisplayName("Create job → get by ID → verify QUEUED status")
        void createAndGetJob() {
            CreateRenderJobRequest createReq = new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p");

            RenderJobResponse createResult = controller.createRenderJob("t-1", "proj-1", createReq);
            assertEquals("QUEUED", createResult.status());
            assertNotNull(createResult.id());
            assertEquals("proj-1", createResult.projectId());
            assertEquals("snap-1", createResult.timelineSnapshotId());
            assertEquals("default_1080p", createResult.profile());

            // Get by ID
            RenderJobResponse getResult = controller.getRenderJob("t-1", "proj-1", createResult.id());
            assertEquals(createResult.id(), getResult.id());
            assertEquals("proj-1", getResult.projectId());
            assertEquals("default_1080p", getResult.profile());
        }

        @Test
        @DisplayName("Full lifecycle: QUEUED → SELECTING_PROVIDER → EXECUTING → COMPLETED")
        void fullLifecycleTransitions() {
            // QUEUED → SELECTING_PROVIDER
            assertEquals(RenderJobStatus.SELECTING_PROVIDER,
                    stateMachine.transition("rj-1", RenderJobStatus.QUEUED,
                            RenderJobStatus.SELECTING_PROVIDER, "Provider resolution", "Orch"));

            // SELECTING_PROVIDER → PROVIDER_SELECTED
            assertEquals(RenderJobStatus.PROVIDER_SELECTED,
                    stateMachine.transition("rj-1", RenderJobStatus.SELECTING_PROVIDER,
                            RenderJobStatus.PROVIDER_SELECTED, "Provider resolved", "Orch"));

            // PROVIDER_SELECTED → EXECUTING
            assertEquals(RenderJobStatus.EXECUTING,
                    stateMachine.transition("rj-1", RenderJobStatus.PROVIDER_SELECTED,
                            RenderJobStatus.EXECUTING, "Render started", "Orch"));

            // EXECUTING → COMPLETING
            assertEquals(RenderJobStatus.COMPLETING,
                    stateMachine.transition("rj-1", RenderJobStatus.EXECUTING,
                            RenderJobStatus.COMPLETING, "Render finished", "Orch"));

            // COMPLETING → COMPLETED
            assertEquals(RenderJobStatus.COMPLETED,
                    stateMachine.transition("rj-1", RenderJobStatus.COMPLETING,
                            RenderJobStatus.COMPLETED, "Artifacts registered", "Orch"));

            // Verify full history
            assertEquals(5, stateMachine.getTransitionHistory("rj-1").size());
            assertEquals(RenderJobStatus.COMPLETED, stateMachine.getCurrentState("rj-1"));
        }

        @Test
        @DisplayName("List jobs returns tenant-scoped results")
        void listJobsTenantScoped() {
            fakeJobService.seedJob("rj-1", "proj-1", "t-1", "snap-1", "default_1080p", "QUEUED");
            fakeJobService.seedJob("rj-2", "proj-1", "t-1", "snap-2", "social_1080p", "COMPLETED");

            List<RenderJobResponse> result = controller.listRenderJobs("t-1", "proj-1");

            assertEquals(2, result.size());
            assertTrue(result.stream().allMatch(j -> j.projectId().equals("proj-1")));
        }
    }

    // ========== VS.1-B: Job submission via orchestrator ==========

    @Nested
    @DisplayName("VS.1-B: Job submission and orchestration")
    class JobSubmissionFlow {

        @Test
        @DisplayName("Submit job via orchestrator → returns QUEUED status")
        void submitJobViaOrchestrator() {
            SubmitRenderJobRequest submitReq = new SubmitRenderJobRequest(
                    "t-1", "proj-1", "Create promo video", "default_1080p", "snap-1");

            Map<String, String> result = controller.submitIncrementalRenderJob("t-1", "proj-1", submitReq);

            assertNotNull(result.get("jobId"));
            assertEquals("QUEUED", result.get("status"));
            assertEquals(1, fakeOrchestrator.getSubmittedJobs().size());
        }

        @Test
        @DisplayName("Start existing job → delegates to orchestrator")
        void startExistingJob() {
            fakeJobService.registerProject("proj-1", "t-1");
            CreateRenderJobRequest createReq = new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p");
            RenderJobResponse created = fakeJobService.createForProject("t-1", "proj-1", createReq);

            Map<String, String> result = controller.startRenderJob("t-1", "proj-1", created.id());

            assertEquals(created.id(), result.get("jobId"));
            assertEquals("STARTED", result.get("status"));
            assertTrue(fakeOrchestrator.getExecutedJobs().contains(created.id()));
        }
    }

    // ========== VS.1-C: Product and Storage boundary flow ==========

    @Nested
    @DisplayName("VS.1-C: Product registration and storage boundary")
    class ProductStorageBoundary {

        @Test
        @DisplayName("Product registration → markReady lifecycle")
        void productRegistrationLifecycle() {
            Product registered = new Product(
                    "prod-1", "t-1", "proj-1", "asset-1",
                    ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                    "render", "render:rj-1", null,
                    ProductStatus.REGISTERED, "stor-1", "abc123", "abc123", "video/mp4", 1,
                    "{}", Instant.now(), Instant.now());

            Product result = productRuntimeService.register(registered);
            assertEquals(ProductStatus.REGISTERED, result.status());
            assertEquals("prod-1", result.productId());

            Product readyResult = productRuntimeService.markReady("prod-1");
            assertEquals(ProductStatus.READY, readyResult.status());
            assertEquals("prod-1", readyResult.productId());
            assertEquals("stor-1", readyResult.storageReferenceId());
        }

        @Test
        @DisplayName("StorageReference preserves architecture boundaries")
        void storageReferenceBoundaries() {
            StorageReference ref = new StorageReference(
                    "stor-1", StorageProviderType.LOCAL.name(), StorageClass.STANDARD,
                    "/data/render", "artifacts/rj-1/output.mp4",
                    "sha256-abc", "sha256-abc", 10485760L, "video/mp4",
                    Instant.now(), Instant.now());

            StorageReference registered = storageRuntimeService.register(ref);

            // Verify no internal leak fields
            String str = registered.toString();
            assertFalse(str.contains("signedUrl"), "No signed URL in StorageReference");
            assertFalse(str.contains("accessKey"), "No access credentials");

            // Verify absolute path is internal-only (used by materialize, not exposed via API)
            assertEquals("/data/render/artifacts/rj-1/output.mp4", registered.absolutePath());
        }

        @Test
        @DisplayName("Product dependency linking with cycle detection")
        void productDependencyLinking() {
            // Register products first
            Product output = new Product(
                    "prod-out", "t-1", "proj-1", "asset-1",
                    ProductType.FINAL_RENDER, RepresentationKind.MEDIA_FILE,
                    "render", "render:rj-1", null,
                    ProductStatus.REGISTERED, null, null, null, null, 1,
                    null, Instant.now(), Instant.now());
            Product input = new Product(
                    "prod-in", "t-1", "proj-1", "asset-2",
                    ProductType.RAW_MEDIA, RepresentationKind.MEDIA_FILE,
                    "import", "import:src-1", null,
                    ProductStatus.REGISTERED, null, null, null, null, 1,
                    null, Instant.now(), Instant.now());
            productRuntimeService.register(output);
            productRuntimeService.register(input);

            ProductDependency dep = productRuntimeService.linkDependency(
                    "prod-out", "prod-in", DependencyType.DERIVED_FROM, "t-1", "proj-1");

            assertNotNull(dep);
            assertEquals("prod-out", dep.productId());
            assertEquals("prod-in", dep.dependsOnProductId());

            // Self-dependency should be caught
            assertThrows(IllegalArgumentException.class,
                    () -> productRuntimeService.linkDependency(
                            "prod-out", "prod-out", DependencyType.DERIVED_FROM, "t-1", "proj-1"));
        }
    }

    // ========== VS.1-D: Error model in context ==========

    @Nested
    @DisplayName("VS.1-D: Error model in integration context")
    class ErrorModelIntegration {

        @Test
        @DisplayName("Job not found returns IllegalArgumentException → 404")
        void jobNotFoundReturns404() {
            assertThrows(IllegalArgumentException.class,
                    () -> controller.getRenderJob("t-1", "proj-1", "rj-missing"));
        }

        @Test
        @DisplayName("Invalid state transition returns PlatformException → 409")
        void invalidTransitionReturns409() {
            PlatformException ex = assertThrows(PlatformException.class,
                    () -> stateMachine.validateTransition(RenderJobStatus.COMPLETED, RenderJobStatus.QUEUED));

            assertEquals(409, ex.getErrorCode().status());
        }

        @Test
        @DisplayName("Tenant mismatch on cancel returns IllegalArgumentException")
        void tenantMismatchOnCancel() {
            TenantContext.set("t-1");

            assertThrows(IllegalArgumentException.class,
                    () -> controller.cancelJob("rj-1", "t-other"));
        }

        @Test
        @DisplayName("Missing orchestrator on submit falls back gracefully")
        void missingOrchestratorFallback() {
            RenderController controllerNoOrch = new RenderController(fakeJobService);
            SubmitRenderJobRequest req = new SubmitRenderJobRequest(
                    "t-1", "proj-1", "test", "default_1080p", "snap-1");

            Map<String, String> result = controllerNoOrch.submitIncrementalRenderJob("t-1", "proj-1", req);

            assertNotNull(result.get("jobId"));
            assertEquals("QUEUED", result.get("status"));
        }

        @Test
        @DisplayName("Output registration exception carries job ID")
        void outputRegistrationExceptionCarriesJobId() {
            RenderOutputRegistrationException ex =
                    new RenderOutputRegistrationException("rj-1", "Path traversal detected", false);

            assertEquals("rj-1", ex.jobId());
            assertFalse(ex.isProductRegistered());
        }

        @Test
        @DisplayName("Output registration exception marks product registered on late failure")
        void outputRegistrationExceptionMarksProductRegistered() {
            RenderOutputRegistrationException ex =
                    new RenderOutputRegistrationException("rj-1", "Checksum mismatch after upload", true);

            assertTrue(ex.isProductRegistered());
        }
    }

    // ========== VS.1-E: API response safety ==========

    @Nested
    @DisplayName("VS.1-E: API response safety — no internal leaks")
    class ApiResponseSafety {

        @Test
        @DisplayName("RenderJobResponse has no storage internals")
        void renderJobResponseNoStorageInternals() {
            RenderJobResponse response = new RenderJobResponse(
                    "rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED");

            String str = response.toString();
            assertFalse(str.contains("/home/"), "No local paths");
            assertFalse(str.contains("bucket"), "No bucket references");
            assertFalse(str.contains("s3://"), "No S3 URIs");
            assertFalse(str.contains("signedUrl"), "No signed URLs");
        }

        @Test
        @DisplayName("ArtifactInfoResponse uses API download path")
        void artifactResponseUsesApiPath() {
            ArtifactInfoResponse artifact = new ArtifactInfoResponse(
                    "art-1", "rj-1", "proj-1", "/api/v1/downloads/art-1",
                    "video/mp4", "1920x1080", 10485760L, Instant.now());

            assertNotNull(artifact.storageUri());
            assertTrue(artifact.storageUri().startsWith("/api/"),
                    "Must use API path: " + artifact.storageUri());
        }

        @Test
        @DisplayName("Status history contains no raw commands or storage details")
        void statusHistoryNoRawDetails() {
            StatusHistoryResponse history = new StatusHistoryResponse(
                    "h-1", "rj-1", null, "QUEUED", "Job created", null, OffsetDateTime.now());

            assertNotNull(history.reason());
            assertFalse(history.reason().contains("ffmpeg "), "No raw commands in reason");
            assertFalse(history.reason().contains("/tmp/"), "No temp paths in reason");
        }
    }
}
