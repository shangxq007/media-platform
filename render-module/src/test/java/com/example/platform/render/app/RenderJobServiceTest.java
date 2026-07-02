package com.example.platform.render.app;

import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.domain.RenderJobStateMachine;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.policy.RenderPolicyDecision;
import com.example.platform.render.policy.RenderPolicyEngine;
import com.example.platform.shared.events.RenderJobCreatedEvent;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.*;

import org.junit.jupiter.api.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VS.1 application service tests for {@link RenderJobService}.
 *
 * <p>Tests the core render job lifecycle operations using hand-written fakes.
 * No Mockito, no database, no H2, no Spring context.
 */
class RenderJobServiceTest {

    private FakeJobRepository fakeRepo;
    private FakePolicyEngine fakePolicy;
    private FakeEventPublisher fakePublisher;
    private FakeHistoryRepository fakeHistory;
    private RenderJobService service;

    @BeforeEach
    void setUp() {
        fakeRepo = new FakeJobRepository();
        fakePolicy = new FakePolicyEngine();
        fakePublisher = new FakeEventPublisher();
        fakeHistory = new FakeHistoryRepository();
        service = new RenderJobService(fakeRepo, fakePolicy, fakePublisher, fakeHistory);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ========== Job creation ==========

    @Nested
    @DisplayName("Job creation")
    class JobCreation {

        @Test
        @DisplayName("create() produces QUEUED job and publishes event")
        void createProducesQueuedJob() {
            fakeRepo.projectTenants.put("proj-1", "t-1");

            RenderJobResponse result = service.create(
                    new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p"));

            assertNotNull(result);
            assertEquals("QUEUED", result.status());
            assertEquals("proj-1", result.projectId());
            assertTrue(result.id().startsWith("rj"), "Job ID must have rj prefix");
            assertEquals(1, fakeRepo.createCalls.size());
            assertEquals(1, fakeHistory.records.size());
            assertEquals(1, fakePublisher.events.size());
        }

        @Test
        @DisplayName("createForProject() validates tenant-project ownership")
        void createForProjectValidatesOwnership() {
            fakeRepo.projectTenants.put("proj-1", "t-1");

            RenderJobResponse result = service.createForProject("t-1", "proj-1",
                    new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p"));

            assertNotNull(result);
            assertEquals("QUEUED", result.status());
        }

        @Test
        @DisplayName("createForProject() throws when project not found")
        void createForProjectThrowsWhenProjectNotFound() {
            assertThrows(IllegalArgumentException.class,
                    () -> service.createForProject("t-1", "proj-missing",
                            new CreateRenderJobRequest("proj-missing", "snap-1", "default_1080p")));
        }

        @Test
        @DisplayName("createForProject() throws when tenant does not own project")
        void createForProjectThrowsWhenTenantMismatch() {
            fakeRepo.projectTenants.put("proj-1", "t-other");

            assertThrows(IllegalArgumentException.class,
                    () -> service.createForProject("t-1", "proj-1",
                            new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p")));
        }

        @Test
        @DisplayName("create() uses policy engine to determine backend")
        void createUsesPolicyEngine() {
            fakeRepo.projectTenants.put("proj-1", "t-1");

            service.create(new CreateRenderJobRequest("proj-1", "snap-1", "social_1080p"));

            assertEquals("social_1080p", fakePolicy.lastProfile);
            assertEquals("ffmpeg", fakePublisher.events.get(0).primaryBackend());
        }

        @Test
        @DisplayName("create() records status history")
        void createRecordsStatusHistory() {
            fakeRepo.projectTenants.put("proj-1", "t-1");

            service.create(new CreateRenderJobRequest("proj-1", "snap-1", "default_1080p"));

            assertEquals(1, fakeHistory.records.size());
            assertEquals("QUEUED", fakeHistory.records.get(0).toStatus);
        }
    }

    // ========== Job retrieval ==========

    @Nested
    @DisplayName("Job retrieval")
    class JobRetrieval {

        @Test
        @DisplayName("getById() returns job when found and tenant matches")
        void getByIdReturnsJob() {
            TenantContext.set("t-1");
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));
            fakeRepo.tenants.put("rj-1", "t-1");

            RenderJobResponse result = service.getById("rj-1");

            assertEquals("rj-1", result.id());
        }

        @Test
        @DisplayName("getById() throws when job not found")
        void getByIdThrowsWhenNotFound() {
            assertThrows(IllegalArgumentException.class, () -> service.getById("rj-missing"));
        }

        @Test
        @DisplayName("getById() throws when tenant context mismatches")
        void getByIdThrowsWhenTenantMismatch() {
            TenantContext.set("t-1");
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));
            fakeRepo.tenants.put("rj-1", "t-other");

            assertThrows(IllegalArgumentException.class, () -> service.getById("rj-1"));
        }

        @Test
        @DisplayName("getByIdAndProject() delegates to repository with tenant filter")
        void getByIdAndProjectDelegates() {
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED"));

            RenderJobResponse result = service.getByIdAndProject("t-1", "proj-1", "rj-1");

            assertEquals("COMPLETED", result.status());
        }
    }

    // ========== Job listing ==========

    @Nested
    @DisplayName("Job listing")
    class JobListing {

        @Test
        @DisplayName("list() returns tenant-scoped results when context set")
        void listReturnsTenantScoped() {
            TenantContext.set("t-1");
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));
            fakeRepo.tenants.put("rj-1", "t-1");

            List<RenderJobResponse> result = service.list();

            assertEquals(1, result.size());
        }

        @Test
        @DisplayName("list() returns all when no tenant context")
        void listReturnsAllWhenNoTenant() {
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));
            fakeRepo.storedJobs.put("rj-2",
                    new RenderJobResponse("rj-2", "proj-2", "snap-2", "social_1080p", "COMPLETED"));

            List<RenderJobResponse> result = service.list();

            assertEquals(2, result.size());
        }

        @Test
        @DisplayName("listByProject() delegates with tenant filter")
        void listByProjectDelegates() {
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));

            List<RenderJobResponse> result = service.listByProject("t-1", "proj-1");

            assertEquals(1, result.size());
        }
    }

    // ========== Cancel and retry ==========

    @Nested
    @DisplayName("Cancel and retry transitions")
    class CancelAndRetry {

        @Test
        @DisplayName("cancel() transitions QUEUED → CANCELLED")
        void cancelTransitionsToCancelled() {
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "QUEUED"));
            fakeRepo.tenants.put("rj-1", "t-1");

            RenderJobResponse result = service.cancel("rj-1", "t-1");

            assertEquals("CANCELLED", result.status());
            assertEquals("CANCELLED", fakeRepo.lastUpdatedStatus);
            assertEquals(1, fakeHistory.records.size());
        }

        @Test
        @DisplayName("retry() transitions FAILED → QUEUED")
        void retryTransitionsToQueued() {
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "FAILED"));
            fakeRepo.tenants.put("rj-1", "t-1");

            RenderJobResponse result = service.retry("rj-1", "t-1");

            assertEquals("QUEUED", result.status());
            assertEquals("QUEUED", fakeRepo.lastUpdatedStatus);
        }

        @Test
        @DisplayName("cancel() throws on invalid transition (COMPLETED → CANCELLED)")
        void cancelThrowsOnInvalidTransition() {
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED"));
            fakeRepo.tenants.put("rj-1", "t-1");

            assertThrows(PlatformException.class, () -> service.cancel("rj-1", "t-1"));
        }

        @Test
        @DisplayName("retry() throws on invalid transition (EXECUTING → QUEUED)")
        void retryThrowsOnInvalidTransition() {
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "EXECUTING"));
            fakeRepo.tenants.put("rj-1", "t-1");

            assertThrows(PlatformException.class, () -> service.retry("rj-1", "t-1"));
        }
    }

    // ========== Status history ==========

    @Nested
    @DisplayName("Status history")
    class StatusHistory {

        @Test
        @DisplayName("getStatusHistory() delegates to history repository")
        void getStatusHistoryDelegates() {
            fakeRepo.storedJobs.put("rj-1",
                    new RenderJobResponse("rj-1", "proj-1", "snap-1", "default_1080p", "COMPLETED"));
            fakeRepo.tenants.put("rj-1", "t-1");
            fakeHistory.byJobId.put("rj-1", List.of(
                    new StatusHistoryResponse("h-1", "rj-1", null, "QUEUED", "Job created", null, OffsetDateTime.now())));

            List<StatusHistoryResponse> result = service.getStatusHistory("rj-1", "t-1");

            assertEquals(1, result.size());
        }
    }

    // ========== Fakes ==========

    static class FakeJobRepository extends RenderJobRepository {
        int createCallCount = 0;
        final List<String[]> createCalls = new ArrayList<>();
        final Map<String, RenderJobResponse> storedJobs = new HashMap<>();
        final Map<String, String> tenants = new HashMap<>();
        final Map<String, String> projectTenants = new HashMap<>();
        String lastUpdatedStatus;

        FakeJobRepository() { super(null); }

        @Override
        public void create(String id, String projectId, String tenantId,
                String timelineSnapshotId, String profile, String status, OffsetDateTime createdAt) {
            createCalls.add(new String[]{id, projectId, tenantId});
            storedJobs.put(id, new RenderJobResponse(id, projectId, timelineSnapshotId, profile, status));
            tenants.put(id, tenantId);
        }

        @Override
        public Optional<RenderJobResponse> findById(String jobId) {
            return Optional.ofNullable(storedJobs.get(jobId));
        }

        @Override
        public Optional<RenderJobResponse> findByIdAndProjectAndTenant(String jobId, String projectId, String tenantId) {
            RenderJobResponse job = storedJobs.get(jobId);
            if (job == null || !job.projectId().equals(projectId)) return Optional.empty();
            return Optional.of(job);
        }

        @Override
        public List<RenderJobResponse> listByTenant(String tenantId) {
            return storedJobs.entrySet().stream()
                    .filter(e -> tenantId.equals(tenants.get(e.getKey())))
                    .map(Map.Entry::getValue).toList();
        }

        @Override
        public List<RenderJobResponse> listByProjectAndTenant(String projectId, String tenantId) {
            return storedJobs.values().stream()
                    .filter(j -> j.projectId().equals(projectId)).toList();
        }

        @Override
        public List<RenderJobResponse> listAll() {
            return new ArrayList<>(storedJobs.values());
        }

        @Override
        public void updateStatus(String jobId, String newStatus) {
            lastUpdatedStatus = newStatus;
            RenderJobResponse old = storedJobs.get(jobId);
            if (old != null) storedJobs.put(jobId,
                    new RenderJobResponse(old.id(), old.projectId(), old.timelineSnapshotId(), old.profile(), newStatus));
        }

        @Override
        public void updateStatusAndClearError(String jobId, String newStatus) {
            updateStatus(jobId, newStatus);
        }

        @Override
        public Optional<String> findTenantIdById(String jobId) {
            return Optional.ofNullable(tenants.get(jobId));
        }

        @Override
        public Optional<String> findProjectTenantId(String projectId) {
            return Optional.ofNullable(projectTenants.get(projectId));
        }
    }

    static class FakePolicyEngine implements RenderPolicyEngine {
        String lastProfile;
        @Override
        public RenderPolicyDecision decide(String profile) {
            lastProfile = profile;
            return new RenderPolicyDecision("ffmpeg", "NORMAL");
        }
    }

    static class FakeEventPublisher implements NotificationEventPublisher {
        final List<RenderJobCreatedEvent> events = new ArrayList<>();
        @Override
        public void publish(Object event) {
            if (event instanceof RenderJobCreatedEvent e) events.add(e);
        }
    }

    static class FakeHistoryRepository extends RenderJobStatusHistoryRepository {
        record HistoryRecord(String jobId, String fromStatus, String toStatus, String reason) {}
        final List<HistoryRecord> records = new ArrayList<>();
        final Map<String, List<StatusHistoryResponse>> byJobId = new HashMap<>();

        FakeHistoryRepository() { super(null); }

        @Override
        public void record(String jobId, String fromStatus, String toStatus, String reason, String errorCode) {
            records.add(new HistoryRecord(jobId, fromStatus, toStatus, reason));
        }

        @Override
        public List<StatusHistoryResponse> findByJobId(String jobId) {
            return byJobId.getOrDefault(jobId, List.of());
        }
    }
}
