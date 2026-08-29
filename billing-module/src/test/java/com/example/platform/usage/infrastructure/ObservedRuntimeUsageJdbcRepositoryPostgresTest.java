package com.example.platform.usage.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.ObservedRuntimeUsage;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;
import com.example.platform.shared.usage.RuntimeOutcome;
import com.example.platform.shared.usage.UsageDimension;
import com.example.platform.shared.usage.UsageProvenance;
import com.example.platform.shared.usage.UsageQuantity;
import com.example.platform.shared.usage.UsageUnit;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class ObservedRuntimeUsageJdbcRepositoryPostgresTest extends PostgresTestContainerSupport {

    private static final Instant OCCURRED = Instant.parse("2026-08-29T08:00:00Z");
    private static final Instant OBSERVED = Instant.parse("2026-08-29T08:00:01Z");
    private static final Instant RECORDED = Instant.parse("2026-08-29T08:00:02Z");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private ObservedRuntimeUsageJdbcRepository repository;

    @BeforeAll
    static void createSchema() {
        dataSource = createDataSource();
        jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS observed_runtime_usage (
                    observed_usage_id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    project_id varchar(64),
                    principal_type varchar(32) not null,
                    principal_id varchar(128) not null,
                    operation_ref varchar(128) not null,
                    attempt_ref varchar(128) not null,
                    execution_ref varchar(128),
                    provider_ref varchar(128) not null,
                    capability varchar(128) not null,
                    dimension varchar(64) not null,
                    quantity_base_units bigint not null check (quantity_base_units >= 0),
                    quantity_unit varchar(32) not null,
                    operation_outcome varchar(32) not null,
                    occurred_at timestamptz not null,
                    observed_at timestamptz not null,
                    recorded_at timestamptz not null,
                    provenance varchar(32) not null,
                    source varchar(128) not null,
                    source_reference varchar(255) not null,
                    trace_id varchar(128) not null,
                    idempotency_key varchar(255) not null,
                    unique (tenant_id, idempotency_key)
                )
                """);
    }

    @AfterAll
    static void closeDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE observed_runtime_usage");
        repository = new ObservedRuntimeUsageJdbcRepository(jdbc);
    }

    @Test
    void exactReplayReturnsPriorObservationAndOneRow() {
        ObservedRuntimeUsage first = repository.append(observation(
                "tenant-a", "attempt-1", "idem-1", 1_501, RuntimeOutcome.SUCCEEDED));
        ObservedRuntimeUsage replay = repository.append(observation(
                "tenant-a", "attempt-1", "idem-1", 1_501, RuntimeOutcome.SUCCEEDED));

        assertEquals(first, replay);
        assertEquals(1L, countRows());
    }

    @Test
    void sameKeyWithDifferentSemanticPayloadFailsClosed() {
        repository.append(observation(
                "tenant-a", "attempt-1", "idem-1", 1_501, RuntimeOutcome.SUCCEEDED));

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> repository.append(observation(
                        "tenant-a", "attempt-1", "idem-1", 1_502, RuntimeOutcome.SUCCEEDED)));

        assertEquals("Idempotency key reused with different observed usage payload",
                failure.getMessage());
        assertEquals(1L, countRows());
    }

    @Test
    void everyObservationFieldRoundTrips() {
        ObservedRuntimeUsage expected = observation(
                "tenant-a", "attempt-1", "round-trip", 1_501, RuntimeOutcome.FAILED);
        ObservedRuntimeUsage saved = repository.append(expected);
        ObservedRuntimeUsage loaded = repository.findByTenantAndId(
                "tenant-a", saved.observedUsageId()).orElseThrow();

        assertEquals(saved, loaded);
        assertEquals("project-a", loaded.projectId());
        assertEquals("execution-a", loaded.executionRef());
        assertEquals(OCCURRED, loaded.occurredAt());
        assertEquals(RuntimeOutcome.FAILED, loaded.outcome());
        assertEquals("source-ref-a", loaded.sourceReference());
        assertEquals("trace-a", loaded.traceId());
    }

    @Test
    void readsAreTenantScoped() {
        ObservedRuntimeUsage saved = repository.append(observation(
                "tenant-a", "attempt-1", "tenant-key", 1, RuntimeOutcome.SUCCEEDED));

        assertTrue(repository.findByTenantAndId("tenant-b", saved.observedUsageId()).isEmpty());
        assertTrue(repository.findByTenantAndIdempotencyKey("tenant-b", "tenant-key").isEmpty());
        assertEquals(1, repository.findByTenant("tenant-a").size());
        assertEquals(0, repository.findByTenant("tenant-b").size());
    }

    @Test
    void concurrentDuplicateCreatesOneRow() throws Exception {
        List<ObservedRuntimeUsage> results = concurrently(12, () -> repository.append(observation(
                "tenant-a", "attempt-1", "concurrent-key", 9, RuntimeOutcome.SUCCEEDED)));

        assertEquals(1, results.stream().distinct().count());
        assertEquals(1L, countRows());
    }

    @Test
    void sameOperationWithNewAttemptIsDistinct() {
        ObservedRuntimeUsage first = repository.append(observation(
                "tenant-a", "attempt-1", "attempt-key-1", 1, RuntimeOutcome.SUCCEEDED));
        ObservedRuntimeUsage second = repository.append(observation(
                "tenant-a", "attempt-2", "attempt-key-2", 1, RuntimeOutcome.SUCCEEDED));

        assertNotEquals(first.observedUsageId(), second.observedUsageId());
        assertEquals(2L, countRows());
    }

    @Test
    void failedButConsumedOperationIsAccepted() {
        ObservedRuntimeUsage saved = repository.append(observation(
                "tenant-a", "attempt-failed", "failed-key", 800, RuntimeOutcome.FAILED));

        assertEquals(RuntimeOutcome.FAILED, saved.outcome());
        assertEquals(800, saved.quantity().baseUnits());
        assertEquals(1L, countRows());
    }

    private static ObservedRuntimeUsage observation(
            String tenantId, String attemptId, String idempotencyKey, long quantity,
            RuntimeOutcome outcome) {
        return ObservedRuntimeUsage.observe(
                tenantId,
                "project-a",
                new CanonicalActorRef("user-a", "USER"),
                OperationRef.of("operation-a", attemptId),
                "execution-a",
                new ProviderRef("provider-a"),
                "capability-a",
                UsageDimension.DURATION,
                UsageQuantity.fromBaseUnits(quantity, UsageUnit.MILLISECONDS),
                outcome,
                OCCURRED,
                OBSERVED,
                RECORDED,
                UsageProvenance.REPORTED,
                "runtime",
                "source-ref-a",
                "trace-a",
                idempotencyKey);
    }

    private static long countRows() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM observed_runtime_usage", Long.class);
    }

    private static <T> List<T> concurrently(int count, ThrowingSupplier<T> supplier)
            throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<T>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return supplier.get();
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<T> results = new ArrayList<>();
            for (Future<T> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }
}
