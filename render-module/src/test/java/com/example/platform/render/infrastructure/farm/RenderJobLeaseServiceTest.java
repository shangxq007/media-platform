package com.example.platform.render.infrastructure.farm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import javax.sql.DataSource;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobLeaseServiceTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private RenderJobLeaseService leaseService;
    private RenderJobLeaseRepository leaseRepository;
    private RenderWorkerRepository workerRepository;
    private RenderWorkerRegistryService workerRegistry;

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
        leaseRepository = new RenderJobLeaseRepository(dsl);
        workerRepository = new RenderWorkerRepository(dsl);
        workerRegistry = new RenderWorkerRegistryService(workerRepository);
        var jobRepository = new com.example.platform.render.infrastructure.RenderJobRepository(dsl);
        leaseService = new RenderJobLeaseService(leaseRepository, jobRepository, workerRegistry);
    }

    private void insertWorker(String workerId, String providerIds) {
        var reg = new RenderWorkerRegistration(
                workerId, "RENDER", "1.0.0", "img:latest",
                "host-1", "zone-a", providerIds, "{}",
                4, 8, 16384, 0, null, null);
        workerRegistry.registerWorker(reg);
        workerRegistry.markIdle(workerId);
    }

    private void insertJob(String jobId, String status) {
        dsl.insertInto(DSL.table("render_job"))
                .columns(DSL.field("id"), DSL.field("project_id"), DSL.field("tenant_id"),
                        DSL.field("timeline_snapshot_id"), DSL.field("profile"),
                        DSL.field("status"), DSL.field("created_at"))
                .values(jobId, "proj-1", "tenant-1", "snap-1", "default_1080p", status, OffsetDateTime.now())
                .execute();
    }

    // --- Claim tests ---

    @Test
    void claimQueuedJobWithEligibleProvider() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");

        assertTrue(result.isClaimed());
        assertEquals("job-1", result.jobId());
        assertEquals("ffmpeg", result.providerId());
        assertEquals(1, result.attempt());
        assertNotNull(result.leaseId());
        assertNotNull(result.leaseUntil());
    }

    @Test
    void claimRejectsStubProvider() {
        insertWorker("worker-1", "[\"blender\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("blender"), false, "PRODUCTION");

        assertFalse(result.isClaimed());
        assertTrue(result.failureReason().contains("No eligible provider"));
    }

    @Test
    void claimRejectsSkeletonProvider() {
        insertWorker("worker-1", "[\"natron\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("natron"), false, "PRODUCTION");

        assertFalse(result.isClaimed());
    }

    @Test
    void claimRejectsDeprecatedProvider() {
        insertWorker("worker-1", "[\"javacv\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("javacv"), false, "PRODUCTION");

        assertFalse(result.isClaimed());
    }

    @Test
    void claimRejectsMockProvider() {
        insertWorker("worker-1", "[\"mock\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("mock"), false, "PRODUCTION");

        assertFalse(result.isClaimed());
    }

    @Test
    void claimRejectsPocProviderWithoutAllow() {
        insertWorker("worker-1", "[\"mlt\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("mlt"), false, "PRODUCTION");

        assertFalse(result.isClaimed());
    }

    @Test
    void claimAllowsPocProviderWithAllowPoc() {
        insertWorker("worker-1", "[\"mlt\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("mlt"), true, "PRODUCTION");

        assertTrue(result.isClaimed());
        assertEquals("mlt", result.providerId());
    }

    @Test
    void claimAllowsPocProviderInExperimentMode() {
        insertWorker("worker-1", "[\"mlt\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("mlt"), false, "EXPERIMENT");

        assertTrue(result.isClaimed());
    }

    @Test
    void claimRejectsUnknownProvider() {
        insertWorker("worker-1", "[\"unknown-provider\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("unknown-provider"), false, "PRODUCTION");

        assertFalse(result.isClaimed());
    }

    @Test
    void claimReturnsJobDetails() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");

        assertTrue(result.isClaimed());
        assertEquals("default_1080p", result.renderProfile());
        assertEquals("tenant-1", result.tenantId());
        assertEquals(3, result.maxAttempts());
    }

    @Test
    void claimNoQueuedJobsReturnsFailure() {
        insertWorker("worker-1", "[\"ffmpeg\"]");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");

        assertFalse(result.isClaimed());
    }

    @Test
    void claimUnavailableWorkerReturnsFailure() {
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult result = leaseService.claimNextJob(
                "unknown-worker", List.of("ffmpeg"), false, "PRODUCTION");

        assertFalse(result.isClaimed());
    }

    @Test
    void duplicateClaimSameJobPrevented() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertWorker("worker-2", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult first = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
        assertTrue(first.isClaimed());

        RenderFarmClaimResult second = leaseService.claimNextJob(
                "worker-2", List.of("ffmpeg"), false, "PRODUCTION");
        assertFalse(second.isClaimed());
    }

    // --- Renew tests ---

    @Test
    void renewByOwnerSucceeds() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult claimed = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
        boolean renewed = leaseService.renewLease(claimed.leaseId(), "worker-1");
        assertTrue(renewed);
    }

    @Test
    void renewByWrongWorkerRejected() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertWorker("worker-2", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult claimed = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
        boolean renewed = leaseService.renewLease(claimed.leaseId(), "worker-2");
        assertFalse(renewed);
    }

    // --- Complete tests ---

    @Test
    void completeLeaseByOwnerSucceeds() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult claimed = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
        LeaseReleaseResult released = leaseService.completeLease(
                claimed.leaseId(), "worker-1", "s3://bucket/output.mp4", "abc123", 5000L);

        assertTrue(released.isReleased());
        assertEquals("job-1", released.jobId());
    }

    @Test
    void completeLeaseByWrongWorkerRejected() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertWorker("worker-2", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult claimed = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
        LeaseReleaseResult released = leaseService.completeLease(
                claimed.leaseId(), "worker-2", "s3://bucket/output.mp4", "abc123", 5000L);

        assertFalse(released.isReleased());
    }

    @Test
    void completeLeaseUpdatesArtifactUri() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult claimed = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
        leaseService.completeLease(claimed.leaseId(), "worker-1", "s3://bucket/output.mp4", "abc123", 5000L);

        var jobRecord = new com.example.platform.render.infrastructure.RenderJobRepository(dsl)
                .requireJobRecord("job-1");
        assertEquals("COMPLETED", jobRecord.get("status", String.class));
        assertEquals("s3://bucket/output.mp4", jobRecord.get("artifact_uri", String.class));
    }

    // --- Fail tests ---

    @Test
    void failLeaseRetryableRequeuesJob() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult claimed = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
        boolean failed = leaseService.failLease(
                claimed.leaseId(), "worker-1", "FFmpeg crashed", "RENDER_FAILED", true);

        assertTrue(failed);
        var jobRecord = new com.example.platform.render.infrastructure.RenderJobRepository(dsl)
                .requireJobRecord("job-1");
        assertEquals("QUEUED", jobRecord.get("status", String.class));
    }

    @Test
    void failLeaseNonRetryableMarksJobFailed() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult claimed = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
        boolean failed = leaseService.failLease(
                claimed.leaseId(), "worker-1", "Bad input", "VALIDATION_FAILED", false);

        assertTrue(failed);
        var jobRecord = new com.example.platform.render.infrastructure.RenderJobRepository(dsl)
                .requireJobRecord("job-1");
        assertEquals("FAILED", jobRecord.get("status", String.class));
    }

    @Test
    void failLeaseMarksFailedWhenMaxAttemptsExhausted() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        // Claim and fail 3 times (all retryable)
        for (int i = 1; i <= 3; i++) {
            RenderFarmClaimResult claimed = leaseService.claimNextJob(
                    "worker-1", List.of("ffmpeg"), false, "PRODUCTION");
            if (claimed.isClaimed()) {
                leaseService.failLease(claimed.leaseId(), "worker-1", "Error " + i, "RENDER_FAILED", true);
            }
        }

        var jobRecord = new com.example.platform.render.infrastructure.RenderJobRepository(dsl)
                .requireJobRecord("job-1");
        assertEquals("FAILED", jobRecord.get("status", String.class));
    }

    // --- Expire tests ---

    @Test
    void expireStaleLeasesRequeuesJob() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        RenderFarmClaimResult claimed = leaseService.claimNextJob(
                "worker-1", List.of("ffmpeg"), false, "PRODUCTION");

        // Set lease_until to past
        dsl.update(DSL.table("render_job_lease"))
                .set(DSL.field("lease_until"), OffsetDateTime.now().minusHours(2))
                .where(DSL.field("lease_id").eq(claimed.leaseId()))
                .execute();

        int expired = leaseService.expireStaleLeases();
        assertEquals(1, expired);

        var jobRecord = new com.example.platform.render.infrastructure.RenderJobRepository(dsl)
                .requireJobRecord("job-1");
        assertEquals("QUEUED", jobRecord.get("status", String.class));
    }

    // --- Worker active count tests ---

    @Test
    void workerActiveJobCountUpdatedOnClaimAndComplete() {
        insertWorker("worker-1", "[\"ffmpeg\"]");
        insertJob("job-1", "QUEUED");

        leaseService.claimNextJob("worker-1", List.of("ffmpeg"), false, "PRODUCTION");

        var worker = workerRepository.findByWorkerId("worker-1").orElseThrow();
        assertEquals(1, worker.activeJobCount());

        var lease = leaseRepository.findActiveLeaseByJobId("job-1").orElseThrow();
        leaseService.completeLease(lease.leaseId(), "worker-1", "s3://bucket/out.mp4", "abc", 1000L);

        worker = workerRepository.findByWorkerId("worker-1").orElseThrow();
        assertEquals(0, worker.activeJobCount());
    }
}
