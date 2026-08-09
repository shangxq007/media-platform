package com.example.platform.billing.usage;

import com.example.platform.billing.infrastructure.UsageRecordJdbcRepository;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Outbox atomicity RED (MANDATORY): usage persistence succeeds but outbox persistence
 * fails -> the transaction leaves NO half-state. The usage insert must roll back when the
 * outbox append throws.
 */
@SpringBootTest(
        classes = UsageOutboxEventAtomicityTest.TestConfig.class,
        properties = {
            "spring.flyway.enabled=false",
            "spring.sql.init.mode=never"
        })
class UsageOutboxEventAtomicityTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");

    private static DataSource dataSource;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsageOutboxEventPublisher publisher;

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

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute("TRUNCATE TABLE usage_record CASCADE");
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
    void outboxFailure_rollsBackUsage_noHalfState() {
        UsageRecord record = record("tenant-1", "idem-atomicity");

        // Outbox append is forced to fail inside the publisher's transaction.
        assertThrows(RuntimeException.class, () -> publisher.persistUsageWithOutbox(record));

        // The usage insert must have been rolled back — no half-state.
        assertEquals(0L, countUsage(),
                "When outbox append fails, the usage insert must roll back (no half-state)");
    }

    // ── Outbox atomicity RED (MANDATORY): referenced, not duplicated ──
    // USAGE-RED outbox atomicity: a forced outbox failure inside the @Transactional boundary must
    // roll the usage insert back, leaving no half-state. This RED-named method references the
    // outboxFailure_rollsBackUsage_noHalfState() proof above rather than re-implementing the
    // throwing-outbox @SpringBootTest context.

    @Test
    void usageRed_outboxAtomicity_forcedFailure_noHalfState() {
        outboxFailure_rollsBackUsage_noHalfState();
    }

    private long countUsage() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_record", Long.class);
    }

    /**
     * OutboxEventService stub whose appendEvent always throws, simulating an outbox
     * persistence failure within the same transaction as the usage insert.
     */
    static class ThrowingOutboxEventService extends OutboxEventService {
        ThrowingOutboxEventService() {
            super(null, 3, null);
        }

        @Override
        public String appendEvent(String aggregateType, String aggregateId, String eventType,
                int eventVersion, Object payload, String idempotencyKey) {
            throw new RuntimeException("simulated outbox persistence failure");
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return createDataSource();
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public OutboxEventService outboxEventService() {
            return new ThrowingOutboxEventService();
        }

        @Bean
        public UsageRecordJdbcRepository usageRecordJdbcRepository(JdbcTemplate jdbcTemplate) {
            return new UsageRecordJdbcRepository(jdbcTemplate);
        }

        @Bean
        public UsageOutboxEventPublisher usageOutboxEventPublisher(
                UsageRecordJdbcRepository usageRecordJdbcRepository,
                OutboxEventService outboxEventService) {
            return new UsageOutboxEventPublisher(usageRecordJdbcRepository, outboxEventService);
        }
    }
}
