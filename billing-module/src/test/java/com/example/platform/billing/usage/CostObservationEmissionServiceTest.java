package com.example.platform.billing.usage;

import com.example.platform.shared.usage.CanonicalActorRef;
import com.example.platform.shared.usage.OperationRef;
import com.example.platform.shared.usage.ProviderRef;

import com.example.platform.billing.infrastructure.ProviderCostObservationJdbcRepository;
import com.example.platform.outbox.app.OutboxEventService;
import com.example.platform.outbox.app.PostgresNotificationService;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
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
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(
        classes = CostObservationEmissionServiceTest.TestConfig.class,
        properties = {
            "spring.flyway.enabled=false",
            "spring.sql.init.mode=never"
        })
class CostObservationEmissionServiceTest extends PostgresTestContainerSupport {

    private static final Instant NOW = Instant.parse("2026-08-09T10:00:00Z");
    private static final TypeReference<Map<String, Object>> MAP_REF = new TypeReference<>() {};
    private static final ObjectMapper PAYLOAD_MAPPER = new ObjectMapper().registerModule(new JavaTimeModule())
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    private static DataSource dataSource;
    private static DSLContext dsl;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CostObservationEmissionService emissionService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
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
        jdbcTemplate.execute("TRUNCATE TABLE provider_cost_observation CASCADE");
    }

    private static ProviderCostObservation observation(String tenant, String idemKey,
            CostType costType, String usageRecordId) {
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
                usageRecordId,
                idemKey);
    }

    @Test
    void persistCostWithOutbox_persistsCostAndOutboxEvent() throws Exception {
        ProviderCostObservation saved = emissionService.persistCostWithOutbox(
                observation("tenant-1", "idem-cost-happy", CostType.REPORTED, "usg-123"));

        // Cost row present with provenance preserved.
        assertEquals(1L, countCost());
        assertEquals(new BigDecimal("1999"), saved.amountMinor());
        assertEquals("USD", saved.currencyCode());
        assertEquals(CostType.REPORTED, saved.costType());

        // Outbox event present with correct shape.
        assertEquals(1L, countOutbox());
        Map<String, Object> event = dsl.select().from("outbox_events").fetchMaps().get(0);
        assertEquals("PROVIDER_COST", event.get("aggregate_type"));
        assertEquals(saved.observationId(), event.get("aggregate_id"));
        assertEquals("COST_OBSERVED", event.get("event_type"));

        // Bounded payload carries stable, non-secret data only.
        Map<String, Object> payload = PAYLOAD_MAPPER.readValue((String) event.get("payload"), MAP_REF);
        assertEquals(saved.observationId(), payload.get("costObservationId"));
        assertEquals("tenant-1", payload.get("tenantId"));
        assertEquals("op-1", payload.get("operationRef"));
        assertEquals("REPORTED", payload.get("costType"));
        assertEquals("USD", payload.get("currencyCode"));
        // JSON number deserializes to Integer in a raw Map; compare by numeric value.
        assertEquals(new BigDecimal("1999"), new BigDecimal(String.valueOf(payload.get("amountMinor"))));
    }

    @Test
    void persistCostWithOutbox_idempotentByKey() {
        ProviderCostObservation first = emissionService.persistCostWithOutbox(
                observation("tenant-1", "idem-cost-dup", CostType.REPORTED, null));
        ProviderCostObservation second = emissionService.persistCostWithOutbox(
                observation("tenant-1", "idem-cost-dup", CostType.REPORTED, null));

        assertEquals(first.observationId(), second.observationId());
        assertEquals(1L, countCost(), "Duplicate idempotency key must yield exactly one cost row");
        assertEquals(1L, countOutbox(), "Outbox idempotency key must suppress duplicate events");
    }

    @Test
    void persistCostWithOutbox_provenanceFieldsPreserved() {
        ProviderCostObservation saved = emissionService.persistCostWithOutbox(
                observation("tenant-1", "idem-cost-prov", CostType.ESTIMATED, "usg-456"));

        Map<String, Object> reloaded = jdbcTemplate.queryForMap(
                "SELECT * FROM provider_cost_observation WHERE id = ?", saved.observationId());

        assertEquals("ESTIMATED", reloaded.get("cost_type"));
        assertEquals("provider-report", reloaded.get("source"));
        assertEquals("USD", reloaded.get("currency_code"));
        // amount_minor is a bigint column -> JdbcTemplate returns Long; compare by numeric value.
        assertEquals(new BigDecimal("1999"), new BigDecimal(String.valueOf(reloaded.get("amount_minor"))));
        assertEquals("usg-456", reloaded.get("usage_record_id"));
    }

    private long countCost() {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM provider_cost_observation", Long.class);
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
        public ProviderCostObservationJdbcRepository providerCostObservationJdbcRepository(
                JdbcTemplate jdbcTemplate) {
            return new ProviderCostObservationJdbcRepository(jdbcTemplate);
        }

        @Bean
        public CostObservationEmissionService costObservationEmissionService(
                ProviderCostObservationJdbcRepository providerCostObservationJdbcRepository,
                OutboxEventService outboxEventService) {
            return new CostObservationEmissionService(providerCostObservationJdbcRepository, outboxEventService);
        }
    }
}
