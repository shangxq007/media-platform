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

class RenderWorkerRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private RenderWorkerRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "workertest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table render_worker ("
                    + "id varchar(64) primary key,"
                    + "worker_id varchar(128) not null unique,"
                    + "worker_type varchar(32) not null default 'RENDER',"
                    + "status varchar(32) not null default 'STARTING',"
                    + "version varchar(64),"
                    + "image_tag varchar(128),"
                    + "hostname varchar(256),"
                    + "zone varchar(64),"
                    + "provider_ids text,"
                    + "capabilities_json text,"
                    + "max_concurrent_jobs int not null default 1,"
                    + "active_job_count int not null default 0,"
                    + "cpu_cores int,"
                    + "memory_mb int,"
                    + "gpu_count int not null default 0,"
                    + "gpu_type varchar(64),"
                    + "disk_free_mb bigint,"
                    + "last_heartbeat_at timestamp not null,"
                    + "registered_at timestamp not null,"
                    + "expires_at timestamp,"
                    + "metadata_json text,"
                    + "created_at timestamp not null default current_timestamp,"
                    + "updated_at timestamp not null default current_timestamp"
                    + ")");
        }

        repository = new RenderWorkerRepository(dsl);
    }

    private RenderWorkerRegistration reg(String workerId) {
        return new RenderWorkerRegistration(
                workerId, "RENDER", "1.0.0", "img:latest",
                "host-1", "zone-a", "[\"ffmpeg\"]", "{}",
                4, 8, 16384, 0, null, null);
    }

    @Test
    void registerNewWorker() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);

        Optional<RenderWorkerRecord> found = repository.findByWorkerId("worker-1");
        assertTrue(found.isPresent());
        assertEquals("worker-1", found.get().workerId());
        assertEquals(RenderWorkerStatus.STARTING, found.get().status());
        assertEquals("RENDER", found.get().workerType());
        assertEquals("[\"ffmpeg\"]", found.get().providerIds());
    }

    @Test
    void registerExistingWorkerUpdates() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);

        // Re-register with different version
        var updated = new RenderWorkerRegistration(
                "worker-1", "RENDER", "2.0.0", "img:v2",
                "host-2", "zone-b", "[\"ffmpeg\",\"mlt\"]", "{}",
                8, 16, 32768, 1, "nvidia-a100", null);
        repository.register(updated, Instant.now());

        Optional<RenderWorkerRecord> found = repository.findByWorkerId("worker-1");
        assertTrue(found.isPresent());
        assertEquals("2.0.0", found.get().version());
        assertEquals("img:v2", found.get().imageTag());
        assertEquals("[\"ffmpeg\",\"mlt\"]", found.get().providerIds());
    }

    @Test
    void heartbeatUpdatesStatus() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);
        repository.markIdle("worker-1", now);

        var hb = new RenderWorkerHeartbeat("worker-1", RenderWorkerStatus.BUSY, 2,
                8, 16384, 0, null, null, null);
        boolean accepted = repository.heartbeat(hb, Instant.now());

        assertTrue(accepted);
        Optional<RenderWorkerRecord> found = repository.findByWorkerId("worker-1");
        assertTrue(found.isPresent());
        assertEquals(RenderWorkerStatus.BUSY, found.get().status());
        assertEquals(2, found.get().activeJobCount());
    }

    @Test
    void heartbeatRejectedForOfflineWorker() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);
        repository.markOffline("worker-1", now);

        var hb = new RenderWorkerHeartbeat("worker-1", RenderWorkerStatus.IDLE, 0,
                null, null, 0, null, null, null);
        boolean accepted = repository.heartbeat(hb, Instant.now());

        assertFalse(accepted);
    }

    @Test
    void markDrainingNotSelected() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);
        repository.markIdle("worker-1", now);
        repository.register(reg("worker-2"), now);
        repository.markIdle("worker-2", now);
        repository.markDraining("worker-1", now);

        List<RenderWorkerRecord> available = repository.findAvailableWorkers();
        assertEquals(1, available.size());
        assertEquals("worker-2", available.get(0).workerId());
    }

    @Test
    void markOfflineNotSelected() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);
        repository.markIdle("worker-1", now);
        repository.markOffline("worker-1", now);

        List<RenderWorkerRecord> available = repository.findAvailableWorkers();
        assertTrue(available.isEmpty());
    }

    @Test
    void findStaleWorkers() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);
        repository.register(reg("worker-2"), now);

        // Worker-1 has old heartbeat
        var oldHb = new RenderWorkerHeartbeat("worker-1", RenderWorkerStatus.IDLE, 0,
                null, null, 0, null, null, null);
        // Manually set old heartbeat
        dsl.update(DSL.table("render_worker"))
                .set(DSL.field("last_heartbeat_at"), OffsetDateTime.now().minusHours(1))
                .where(DSL.field("worker_id").eq("worker-1"))
                .execute();

        List<RenderWorkerRecord> stale = repository.findStaleWorkers(now.minusSeconds(60));
        assertEquals(1, stale.size());
        assertEquals("worker-1", stale.get(0).workerId());
    }

    @Test
    void incrementDecrementActiveJobs() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);
        repository.markIdle("worker-1", now);

        repository.incrementActiveJobs("worker-1");
        repository.incrementActiveJobs("worker-1");

        Optional<RenderWorkerRecord> found = repository.findByWorkerId("worker-1");
        assertTrue(found.isPresent());
        assertEquals(2, found.get().activeJobCount());

        repository.decrementActiveJobs("worker-1");
        found = repository.findByWorkerId("worker-1");
        assertTrue(found.isPresent());
        assertEquals(1, found.get().activeJobCount());
    }

    @Test
    void decrementDoesNotGoBelowZero() {
        Instant now = Instant.now();
        repository.register(reg("worker-1"), now);

        repository.decrementActiveJobs("worker-1");

        Optional<RenderWorkerRecord> found = repository.findByWorkerId("worker-1");
        assertTrue(found.isPresent());
        assertEquals(0, found.get().activeJobCount());
    }
}
