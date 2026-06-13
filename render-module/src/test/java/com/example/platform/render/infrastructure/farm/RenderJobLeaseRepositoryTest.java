package com.example.platform.render.infrastructure.farm;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobLeaseRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private RenderJobLeaseRepository repository;

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
        repository = new RenderJobLeaseRepository(dsl);
    }

    private RenderJobLeaseRecord lease(String leaseId, String jobId, String workerId, int attempt) {
        Instant now = Instant.now();
        return new RenderJobLeaseRecord(
                leaseId, leaseId, jobId, "tenant-1", workerId, "ffmpeg",
                RenderJobLeaseStatus.CLAIMED, 1, now, now.plusSeconds(600),
                null, null, attempt, 3, null, null, null, "scheduler",
                now, now);
    }

    @Test
    void createAndFindByLeaseId() {
        RenderJobLeaseRecord l = lease("lease-1", "job-1", "worker-1", 1);
        repository.create(l);

        Optional<RenderJobLeaseRecord> found = repository.findByLeaseId("lease-1");
        assertTrue(found.isPresent());
        assertEquals("lease-1", found.get().leaseId());
        assertEquals("job-1", found.get().jobId());
        assertEquals("worker-1", found.get().workerId());
        assertEquals(RenderJobLeaseStatus.CLAIMED, found.get().status());
    }

    @Test
    void findActiveLeaseByJobId() {
        RenderJobLeaseRecord l = lease("lease-1", "job-1", "worker-1", 1);
        repository.create(l);

        Optional<RenderJobLeaseRecord> found = repository.findActiveLeaseByJobId("job-1");
        assertTrue(found.isPresent());
        assertEquals("lease-1", found.get().leaseId());
    }

    @Test
    void findActiveLeaseByJobIdReturnsEmptyForReleased() {
        RenderJobLeaseRecord l = lease("lease-1", "job-1", "worker-1", 1);
        repository.create(l);
        repository.release("lease-1", "worker-1", 1, Instant.now());

        Optional<RenderJobLeaseRecord> found = repository.findActiveLeaseByJobId("job-1");
        assertFalse(found.isPresent());
    }

    @Test
    void renewByOwner() {
        RenderJobLeaseRecord l = lease("lease-1", "job-1", "worker-1", 1);
        repository.create(l);

        Instant newUntil = Instant.now().plusSeconds(900);
        boolean renewed = repository.renew("lease-1", "worker-1", 1, newUntil, Instant.now());
        assertTrue(renewed);

        Optional<RenderJobLeaseRecord> found = repository.findByLeaseId("lease-1");
        assertTrue(found.isPresent());
        assertEquals(RenderJobLeaseStatus.RENEWED, found.get().status());
        assertEquals(2, found.get().leaseVersion());
    }

    @Test
    void renewByWrongWorkerRejected() {
        RenderJobLeaseRecord l = lease("lease-1", "job-1", "worker-1", 1);
        repository.create(l);

        boolean renewed = repository.renew("lease-1", "worker-2", 1, Instant.now().plusSeconds(900), Instant.now());
        assertFalse(renewed);
    }

    @Test
    void renewByVersionConflictRejected() {
        RenderJobLeaseRecord l = lease("lease-1", "job-1", "worker-1", 1);
        repository.create(l);

        boolean renewed = repository.renew("lease-1", "worker-1", 999, Instant.now().plusSeconds(900), Instant.now());
        assertFalse(renewed);
    }

    @Test
    void releaseByOwner() {
        RenderJobLeaseRecord l = lease("lease-1", "job-1", "worker-1", 1);
        repository.create(l);

        boolean released = repository.release("lease-1", "worker-1", 1, Instant.now());
        assertTrue(released);

        Optional<RenderJobLeaseRecord> found = repository.findByLeaseId("lease-1");
        assertTrue(found.isPresent());
        assertEquals(RenderJobLeaseStatus.RELEASED, found.get().status());
    }

    @Test
    void failByOwner() {
        RenderJobLeaseRecord l = lease("lease-1", "job-1", "worker-1", 1);
        repository.create(l);

        boolean failed = repository.fail("lease-1", "worker-1", 1, "FFmpeg crashed", "RENDER_FAILED", Instant.now());
        assertTrue(failed);

        Optional<RenderJobLeaseRecord> found = repository.findByLeaseId("lease-1");
        assertTrue(found.isPresent());
        assertEquals(RenderJobLeaseStatus.FAILED, found.get().status());
        assertEquals("FFmpeg crashed", found.get().failureReason());
    }

    @Test
    void expireStaleLeases() {
        // Create a lease with lease_until in the past
        Instant past = Instant.now().minusSeconds(3600);
        RenderJobLeaseRecord l = new RenderJobLeaseRecord(
                "lease-1", "lease-1", "job-1", "tenant-1", "worker-1", "ffmpeg",
                RenderJobLeaseStatus.CLAIMED, 1, past, past.plusSeconds(600),
                null, null, 1, 3, null, null, null, "scheduler",
                past, past);
        repository.create(l);

        // Manually set lease_until to past
        dsl.update(DSL.table("render_job_lease"))
                .set(DSL.field("lease_until"), OffsetDateTime.now().minusHours(2))
                .where(DSL.field("lease_id").eq("lease-1"))
                .execute();

        List<RenderJobLeaseRecord> expired = repository.expireStaleLeases(Instant.now());
        assertEquals(1, expired.size());
        assertEquals("lease-1", expired.get(0).leaseId());

        // Verify status is now EXPIRED
        Optional<RenderJobLeaseRecord> found = repository.findByLeaseId("lease-1");
        assertTrue(found.isPresent());
        assertEquals(RenderJobLeaseStatus.EXPIRED, found.get().status());
    }

    @Test
    void getMaxAttemptForJob() {
        repository.create(lease("lease-1", "job-1", "worker-1", 1));
        repository.create(lease("lease-2", "job-1", "worker-2", 2));

        int maxAttempt = repository.getMaxAttemptForJob("job-1");
        assertEquals(2, maxAttempt);
    }

    @Test
    void getMaxAttemptForJobReturnsZeroWhenNoLease() {
        int maxAttempt = repository.getMaxAttemptForJob("nonexistent");
        assertEquals(0, maxAttempt);
    }
}
