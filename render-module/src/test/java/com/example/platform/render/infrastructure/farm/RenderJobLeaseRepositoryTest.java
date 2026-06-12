package com.example.platform.render.infrastructure.farm;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobLeaseRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private RenderJobLeaseRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "leasetest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table render_job_lease ("
                    + "id varchar(64) primary key,"
                    + "lease_id varchar(128) not null unique,"
                    + "job_id varchar(64) not null,"
                    + "tenant_id varchar(64) not null,"
                    + "worker_id varchar(128) not null,"
                    + "provider_id varchar(64),"
                    + "status varchar(32) not null default 'CLAIMED',"
                    + "lease_version bigint not null default 1,"
                    + "claimed_at timestamp not null,"
                    + "lease_until timestamp not null,"
                    + "renewed_at timestamp,"
                    + "released_at timestamp,"
                    + "attempt int not null default 1,"
                    + "max_attempts int not null default 3,"
                    + "heartbeat_token_hash varchar(128),"
                    + "failure_reason text,"
                    + "failure_error_code varchar(64),"
                    + "created_by_scheduler varchar(64),"
                    + "created_at timestamp not null default current_timestamp,"
                    + "updated_at timestamp not null default current_timestamp"
                    + ")");
        }

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
