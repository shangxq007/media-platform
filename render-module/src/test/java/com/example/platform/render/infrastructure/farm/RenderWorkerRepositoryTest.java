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

class RenderWorkerRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private RenderWorkerRepository repository;

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
