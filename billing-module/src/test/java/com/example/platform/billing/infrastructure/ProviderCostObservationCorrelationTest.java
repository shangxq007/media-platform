package com.example.platform.billing.infrastructure;

import com.example.platform.billing.usage.CanonicalActorRef;
import com.example.platform.billing.usage.CostType;
import com.example.platform.billing.usage.OperationRef;
import com.example.platform.billing.usage.ProviderCostObservation;
import com.example.platform.billing.usage.ProviderRef;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies cost↔usage correlation: a {@link ProviderCostObservation} linked to a usage
 * record via {@code usage_record_id} is retrievable by that usage record id, and costs are
 * queryable by operation.
 */
class ProviderCostObservationCorrelationTest extends PostgresTestContainerSupport {

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

    private static ProviderCostObservation observation(String idemKey, String operationRef,
            String usageRecordId, CostType costType) {
        return ProviderCostObservation.record(
                "tenant-1",
                "project-1",
                new CanonicalActorRef("user-1", "USER"),
                OperationRef.of(operationRef, "attempt-1"),
                null,
                new ProviderRef("provider-1"),
                "capability-1",
                new BigDecimal("1999"),
                "USD",
                costType,
                "provider-report",
                NOW,
                usageRecordId,
                idemKey);
    }

    @Test
    void findByUsageRecordId_returnsLinkedCostObservations() {
        repository.insert(observation("idem-corr-1", "op-1", "usg-100", CostType.REPORTED));
        repository.insert(observation("idem-corr-2", "op-2", "usg-200", CostType.ESTIMATED));
        // Unrelated observation linked to a different usage record.
        repository.insert(observation("idem-corr-3", "op-3", "usg-300", CostType.DERIVED));

        List<ProviderCostObservation> found = repository.findByUsageRecordId("usg-100");

        assertEquals(1, found.size());
        assertEquals("usg-100", found.get(0).usageRecordId());
        assertEquals("op-1", found.get(0).operationRef().operationId());
        assertEquals(CostType.REPORTED, found.get(0).costType());
    }

    @Test
    void findByUsageRecordId_noneReturnsEmpty() {
        assertTrue(repository.findByUsageRecordId("usg-absent").isEmpty());
    }

    @Test
    void findByOperationRef_returnsCostObservationsForOperation() {
        repository.insert(observation("idem-op-1", "op-shared", "usg-100", CostType.REPORTED));
        repository.insert(observation("idem-op-2", "op-shared", "usg-101", CostType.ESTIMATED));
        repository.insert(observation("idem-op-3", "op-other", "usg-200", CostType.DERIVED));

        List<ProviderCostObservation> found = repository.findByOperationRef("op-shared");

        assertEquals(2, found.size());
        assertTrue(found.stream().allMatch(o -> "op-shared".equals(o.operationRef().operationId())));
    }

    @Test
    void findByOperationRef_noneReturnsEmpty() {
        assertTrue(repository.findByOperationRef("op-absent").isEmpty());
    }
}
