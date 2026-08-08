package com.example.platform.billing.infrastructure;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.ProviderRef;
import com.example.platform.billing.usage.UsageDimension;
import com.example.platform.billing.usage.UsageQuantity;
import com.example.platform.billing.usage.UsageRecord;
import com.example.platform.billing.usage.UsageUnit;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class UsageRecordJdbcRepositoryTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    private static javax.sql.DataSource dataSource;
    private UsageRecordJdbcRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS usage_record (
                id varchar(64) primary key,
                tenant_id varchar(64),
                workspace_id varchar(64),
                user_id varchar(64),
                meter_key varchar(128) not null,
                quantity double precision not null,
                unit varchar(64) not null,
                recorded_at timestamp not null,
                idempotency_key varchar(255) unique,
                created_at timestamp not null default now(),
                operation_ref varchar(128),
                attempt_ref varchar(128),
                dimension varchar(64),
                quantity_base_units bigint,
                quantity_unit varchar(32),
                actor_type varchar(32),
                actor_ref varchar(128),
                provider_ref varchar(128),
                capability varchar(128),
                provenance varchar(32),
                source varchar(128),
                observed_at timestamp
            )
        """);
    }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE usage_record CASCADE");
        repository = new UsageRecordJdbcRepository(jdbc);
    }

    private static UsageRecord record(String tenant, String idemKey) {
        return UsageRecord.record(
                tenant,
                null,
                new CanonicalActorRef("user-1", "USER"),
                OperationRef.of("op-1", "attempt-1"),
                null,
                new ProviderRef("provider-1"),
                null,
                UsageDimension.TOKEN_INPUT,
                UsageQuantity.fromBaseUnits(42, UsageUnit.TOKEN),
                NOW,
                NOW,
                NOW,
                idemKey,
                "REPORTED",
                "test");
    }

    @Test
    void insert_persistsCanonicalRecord() {
        UsageRecord saved = repository.insert(record("tenant-1", "idem-1"));

        assertEquals("tenant-1", saved.tenantId());
        assertEquals(42, saved.quantity().baseUnits());
        assertEquals(UsageUnit.TOKEN, saved.quantity().unit());

        UsageRecord reloaded = repository.findById(saved.recordId()).orElseThrow();
        assertEquals(saved.recordId(), reloaded.recordId());
        assertEquals(42, reloaded.quantity().baseUnits());
        assertEquals(UsageDimension.TOKEN_INPUT, reloaded.dimension());
        assertEquals("op-1", reloaded.operationRef().operationId());
        assertEquals("REPORTED", reloaded.provenance());
    }

    @Test
    void insert_idempotentByExistingKey() {
        UsageRecord first = repository.insert(record("tenant-1", "idem-dup"));
        UsageRecord second = repository.insert(record("tenant-1", "idem-dup"));

        assertEquals(first.recordId(), second.recordId());
        assertEquals(1, repository.findByTenant("tenant-1").size());
    }

    @Test
    void insert_concurrentDuplicateResultsInExactlyOneRow() throws Exception {
        String idemKey = "idem-concurrent";
        int threads = 2;
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        AtomicReference<UsageRecord> first = new AtomicReference<>();
        AtomicReference<Throwable> error = new AtomicReference<>();

        for (int i = 0; i < threads; i++) {
            pool.submit(() -> {
                try {
                    start.await();
                    UsageRecord saved = repository.insert(record("tenant-1", idemKey));
                    first.compareAndSet(null, saved);
                } catch (Throwable t) {
                    error.compareAndSet(null, t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        assertTrue(done.await(15, TimeUnit.SECONDS));
        pool.shutdown();

        assertNull(error.get(), () -> "Concurrent insert failed: " + error.get());
        assertEquals(1, countRows(), "Concurrent duplicate must yield exactly one row");
        assertNotNull(first.get());
    }

    @Test
    void findByTenant_isTenantConstrained() {
        repository.insert(record("tenant-1", "idem-a"));
        repository.insert(record("tenant-2", "idem-b"));

        List<UsageRecord> t1 = repository.findByTenant("tenant-1");
        assertEquals(1, t1.size());
        assertEquals("tenant-1", t1.get(0).tenantId());

        List<UsageRecord> t2 = repository.findByTenant("tenant-2");
        assertEquals(1, t2.size());

        // Cross-tenant lookup must not leak.
        assertTrue(repository.findByTenant("tenant-1").stream()
                .noneMatch(r -> "tenant-2".equals(r.tenantId())));
    }

    @Test
    void findByIdempotencyKey_returnsMatchingRow() {
        UsageRecord saved = repository.insert(record("tenant-1", "idem-lookup"));
        assertTrue(repository.findByIdempotencyKey("idem-lookup").isPresent());
        assertEquals(saved.recordId(), repository.findByIdempotencyKey("idem-lookup").get().recordId());
    }

    @Test
    void findById_missingReturnsEmpty() {
        assertTrue(repository.findById("does-not-exist").isEmpty());
    }

    private long countRows() {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM usage_record", Long.class);
    }
}
