package com.example.platform.billing.usage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.example.platform.outbox.app.OutboxEventService;
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
import com.example.platform.usage.app.ObservedRuntimeUsageOutboxPublisher;
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
import java.time.Instant;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class UsageOutboxEventAtomicityTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static ObservedRuntimeUsageOutboxPublisher publisher;

    @BeforeAll
    static void createSchema() {
        dataSource = createDataSource();
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        publisher = context.getBean(ObservedRuntimeUsageOutboxPublisher.class);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS observed_runtime_usage (
                    observed_usage_id varchar(64) primary key,
                    tenant_id varchar(64) not null, project_id varchar(64),
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    operation_ref varchar(128) not null, attempt_ref varchar(128) not null,
                    execution_ref varchar(128), provider_ref varchar(128) not null,
                    capability varchar(128) not null, dimension varchar(64) not null,
                    quantity_base_units bigint not null, quantity_unit varchar(32) not null,
                    operation_outcome varchar(32) not null, occurred_at timestamptz not null,
                    observed_at timestamptz not null, recorded_at timestamptz not null,
                    provenance varchar(32) not null, source varchar(128) not null,
                    source_reference varchar(255) not null, trace_id varchar(128) not null,
                    idempotency_key varchar(255) not null, unique (tenant_id, idempotency_key)
                )
                """);
    }

    @AfterAll
    static void closeDatabase() {
        if (context != null) {
            context.close();
        }
        closeDataSource(dataSource);
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE TABLE observed_runtime_usage");
    }

    @Test
    void outboxFailureRollsBackObservationWithoutHalfState() {
        assertThrows(RuntimeException.class, () -> publisher.appendWithOutbox(observation()));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM observed_runtime_usage", Long.class));
    }

    private static ObservedRuntimeUsage observation() {
        Instant now = Instant.parse("2026-08-29T08:00:00Z");
        return ObservedRuntimeUsage.observe(
                "tenant-a", "project-a", new CanonicalActorRef("user-a", "USER"),
                OperationRef.of("operation-a", "attempt-a"), "execution-a",
                new ProviderRef("provider-a"), "capability-a", UsageDimension.DURATION,
                UsageQuantity.fromBaseUnits(10, UsageUnit.MILLISECONDS),
                RuntimeOutcome.SUCCEEDED, now, now, now, UsageProvenance.REPORTED,
                "runtime", "source-a", "trace-a", "idem-a");
    }

    static class ThrowingOutbox extends OutboxEventService {
        ThrowingOutbox() { super(null, 3, null); }

        @Override
        public String appendEvent(
                String aggregateType, String aggregateId, String eventType, int eventVersion,
                Object payload, String idempotencyKey) {
            throw new RuntimeException("forced outbox failure");
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        private static DataSource dataSource;
        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbc(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean ObservedRuntimeUsageJdbcRepository repository(JdbcTemplate jdbc) {
            return new ObservedRuntimeUsageJdbcRepository(jdbc);
        }
        @Bean OutboxEventService outbox() { return new ThrowingOutbox(); }
        @Bean ObservedRuntimeUsageOutboxPublisher publisher(
                ObservedRuntimeUsageJdbcRepository repository, OutboxEventService outbox) {
            return new ObservedRuntimeUsageOutboxPublisher(repository, outbox);
        }
    }
}
