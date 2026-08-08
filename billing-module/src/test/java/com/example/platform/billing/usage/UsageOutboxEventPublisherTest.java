package com.example.platform.billing.usage;

import com.example.platform.billing.infrastructure.UsageRecordJdbcRepository;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.outbox.app.PostgresNotificationService;
import com.example.platform.shared.Jsons;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
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
import org.springframework.jdbc.datasource.TransactionAwareDataSourceProxy;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = UsageOutboxEventPublisherTest.TestConfig.class,
        properties = {
            "spring.flyway.enabled=false",
            "spring.sql.init.mode=never"
        })
class UsageOutboxEventPublisherTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};

    private static DataSource dataSource;
    private static DSLContext dsl;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private UsageOutboxEventPublisher publisher;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
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
        dsl.execute("""
            CREATE TABLE IF NOT EXISTS outbox_events (
                id varchar(64) primary key,
                aggregate_type varchar(100) not null,
                aggregate_id varchar(100) not null,
                event_type varchar(150) not null,
                event_version int not null,
                payload text not null,
                status varchar(50) not null,
                retry_count int not null default 0,
                max_retries int not null default 3,
                next_attempt_at timestamp with time zone,
                idempotency_key varchar(255),
                last_error_code varchar(100),
                last_error_message text,
                locked_at timestamp with time zone,
                locked_by varchar(255),
                created_at timestamp with time zone not null,
                published_at timestamp with time zone
            )
        """);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE outbox_events CASCADE");
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
    void persistUsageWithOutbox_persistsUsageAndOutboxEvent() {
        UsageRecord saved = publisher.persistUsageWithOutbox(record("tenant-1", "idem-happy"));

        // Usage row present.
        assertEquals(1L, countUsage());
        assertEquals("tenant-1", jdbcTemplate.queryForObject(
                "SELECT tenant_id FROM usage_record WHERE id = ?", String.class, saved.recordId()));

        // Outbox event present with correct shape.
        assertEquals(1L, countOutbox());
        Map<String, Object> event = dsl.select().from("outbox_events").fetchMaps().get(0);
        assertEquals("USAGE_RECORD", event.get("aggregate_type"));
        assertEquals(saved.recordId(), event.get("aggregate_id"));
        assertEquals("USAGE_RECORDED", event.get("event_type"));

        // Bounded payload carries stable, non-secret data only.
        Map<String, Object> payload = Jsons.fromJson((String) event.get("payload"), MAP_REF);
        assertEquals(saved.recordId(), payload.get("usageRecordId"));
        assertEquals("tenant-1", payload.get("tenantId"));
        assertEquals("op-1", payload.get("operationRef"));
        assertEquals("TOKEN_INPUT", payload.get("dimension"));
        assertEquals(4, payload.size(), "Payload must remain bounded (stable data only)");
    }

    @Test
    void persistUsageWithOutbox_idempotentByKey() {
        UsageRecord first = publisher.persistUsageWithOutbox(record("tenant-1", "idem-dup"));
        UsageRecord second = publisher.persistUsageWithOutbox(record("tenant-1", "idem-dup"));

        assertEquals(first.recordId(), second.recordId());
        assertEquals(1L, countUsage(), "Duplicate idempotency key must yield exactly one usage row");
        assertEquals(1L, countOutbox(), "Outbox idempotency key must suppress duplicate events");
    }

    private long countUsage() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM usage_record", Long.class);
    }

    private long countOutbox() {
        return dsl.fetchCount(DSL.table("outbox_events"));
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {

        @Bean
        public DataSource dataSource() {
            return createDataSource(TX_HEAVY_MAX_POOL_SIZE);
        }

        @Bean
        public DataSource transactionAwareDataSource(DataSource dataSource) {
            return new TransactionAwareDataSourceProxy(dataSource);
        }

        @Bean
        public JdbcTemplate jdbcTemplate(DataSource dataSource) {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        public DSLContext dslContext(DataSource transactionAwareDataSource) {
            return DSL.using(transactionAwareDataSource, SQLDialect.POSTGRES);
        }

        @Bean
        public DataSourceTransactionManager transactionManager(DataSource dataSource) {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        public PostgresNotificationService postgresNotificationService(JdbcTemplate jdbcTemplate) {
            return new PostgresNotificationService(jdbcTemplate);
        }

        @Bean
        public OutboxEventService outboxEventService(DSLContext dslContext,
                PostgresNotificationService postgresNotificationService) {
            return new OutboxEventService(dslContext, 3, postgresNotificationService);
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
