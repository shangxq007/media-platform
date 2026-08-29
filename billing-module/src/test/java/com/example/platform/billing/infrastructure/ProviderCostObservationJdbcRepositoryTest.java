package com.example.platform.billing.infrastructure;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.billing.usage.CostType;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.billing.usage.ProviderCostObservation;
import com.example.platform.shared.usage.ProviderRef;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProviderCostObservationJdbcRepositoryTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    private static javax.sql.DataSource dataSource;
    private ProviderCostObservationJdbcRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS provider_cost_observation (
                id varchar(64) primary key,
                tenant_id varchar(64) not null,
                project_id varchar(64),
                actor_type varchar(32),
                actor_ref varchar(128),
                operation_ref varchar(128),
                execution_ref varchar(128),
                provider_ref varchar(128),
                capability varchar(128),
                amount_minor bigint not null,
                currency_code varchar(8) not null,
                cost_type varchar(32) not null,
                source varchar(128),
                observed_at timestamp not null,
                usage_record_id varchar(64),
                idempotency_key varchar(255) unique,
                created_at timestamp not null default now()
            )
        """);
    }

    @BeforeEach
    void setUp() {
        var jdbc = new JdbcTemplate(dataSource);
        jdbc.execute("TRUNCATE TABLE provider_cost_observation CASCADE");
        repository = new ProviderCostObservationJdbcRepository(jdbc);
    }

    private static ProviderCostObservation observation(String tenant, String idemKey, CostType costType) {
        return ProviderCostObservation.record(
                tenant,
                "project-1",
                new CanonicalActorRef("user-1", "USER"),
                OperationRef.of("op-1", "attempt-1"),
                null,
                new ProviderRef("provider-1"),
                "capability-1",
                new BigDecimal("1999"),
                "USD",
                costType,
                "provider-report",
                NOW,
                null,
                idemKey);
    }

    @Test
    void insert_preservesProvenance() {
        ProviderCostObservation saved = repository.insert(
                observation("tenant-1", "idem-prov-1", CostType.REPORTED));

        assertEquals(new BigDecimal("1999"), saved.amountMinor());
        assertEquals("USD", saved.currencyCode());
        assertEquals(CostType.REPORTED, saved.costType());
        assertEquals(0, saved.amountMinor().scale());

        ProviderCostObservation reloaded = repository.findByIdempotencyKey("idem-prov-1").orElseThrow();
        assertEquals(CostType.REPORTED, reloaded.costType());
        assertEquals(new BigDecimal("1999"), reloaded.amountMinor());
        assertEquals("provider-report", reloaded.source());
    }

    @Test
    void insert_estimatedProvenancePreserved() {
        ProviderCostObservation saved = repository.insert(
                observation("tenant-1", "idem-est-1", CostType.ESTIMATED));
        assertEquals(CostType.ESTIMATED, repository.findByIdempotencyKey("idem-est-1")
                .orElseThrow().costType());
    }

    @Test
    void insert_idempotentByKey() {
        ProviderCostObservation first = repository.insert(observation("tenant-1", "idem-dup", CostType.REPORTED));
        ProviderCostObservation second = repository.insert(observation("tenant-1", "idem-dup", CostType.REPORTED));
        assertEquals(first.observationId(), second.observationId());
        assertEquals(1, countRows());
    }

    @Test
    void findByTenant_isTenantConstrained() {
        repository.insert(observation("tenant-1", "idem-t1", CostType.REPORTED));
        repository.insert(observation("tenant-2", "idem-t2", CostType.ESTIMATED));

        List<ProviderCostObservation> t1 = repository.findByTenant("tenant-1");
        assertEquals(1, t1.size());
        assertEquals("tenant-1", t1.get(0).tenantId());
        assertTrue(repository.findByTenant("tenant-1").stream()
                .noneMatch(o -> "tenant-2".equals(o.tenantId())));
    }

    @Test
    void findByOperationRef_returnsMatchingObservations() {
        repository.insert(observation("tenant-1", "idem-op-1", CostType.REPORTED));
        List<ProviderCostObservation> found = repository.findByOperationRef("op-1");
        assertEquals(1, found.size());
        assertEquals("op-1", found.get(0).operationRef().operationId());
    }

    @Test
    void findByIdempotencyKey_missingReturnsEmpty() {
        assertTrue(repository.findByIdempotencyKey("nope").isEmpty());
    }

    private long countRows() {
        return new JdbcTemplate(dataSource).queryForObject(
                "SELECT COUNT(*) FROM provider_cost_observation", Long.class);
    }
}
