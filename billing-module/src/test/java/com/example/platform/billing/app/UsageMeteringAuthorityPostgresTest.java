package com.example.platform.billing.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.billing.infrastructure.BillableUsageJdbcRepository;
import com.example.platform.billing.usage.BillableUsage;
import com.example.platform.billing.usage.BillableUsageAuditPort;
import com.example.platform.billing.usage.MeterUsageCommand;
import com.example.platform.billing.usage.MeteringRule;
import com.example.platform.billing.usage.MeteringRuleRegistry;
import com.example.platform.billing.usage.MeteringTransformationKind;
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
import com.example.platform.usage.infrastructure.ObservedRuntimeUsageJdbcRepository;
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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

class UsageMeteringAuthorityPostgresTest extends PostgresTestContainerSupport {

    private static final Instant OCCURRED = Instant.parse("2026-08-29T08:00:00Z");
    private static final Instant METERED = Instant.parse("2026-08-29T08:01:00Z");

    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static UsageMeteringService service;
    private static ObservedRuntimeUsageJdbcRepository observations;
    private static BillableUsageJdbcRepository billable;
    private static MeteringRuleRegistry rules;
    private static TestAudit audit;

    @BeforeAll
    static void createSchema() {
        dataSource = createDataSource();
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        service = context.getBean(UsageMeteringService.class);
        observations = context.getBean(ObservedRuntimeUsageJdbcRepository.class);
        billable = context.getBean(BillableUsageJdbcRepository.class);
        rules = context.getBean(MeteringRuleRegistry.class);
        audit = context.getBean(TestAudit.class);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS observed_runtime_usage (
                    observed_usage_id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    project_id varchar(64), principal_type varchar(32) not null,
                    principal_id varchar(128) not null, operation_ref varchar(128) not null,
                    attempt_ref varchar(128) not null, execution_ref varchar(128),
                    provider_ref varchar(128) not null, capability varchar(128) not null,
                    dimension varchar(64) not null, quantity_base_units bigint not null,
                    quantity_unit varchar(32) not null, operation_outcome varchar(32) not null,
                    occurred_at timestamptz not null, observed_at timestamptz not null,
                    recorded_at timestamptz not null, provenance varchar(32) not null,
                    source varchar(128) not null, source_reference varchar(255) not null,
                    trace_id varchar(128) not null, idempotency_key varchar(255) not null,
                    unique (tenant_id, idempotency_key)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS billable_usage (
                    billable_usage_id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    principal_type varchar(32) not null,
                    principal_id varchar(128) not null,
                    observed_usage_id varchar(64) not null,
                    observed_dimension varchar(64) not null,
                    observed_quantity_base_units bigint not null,
                    observed_quantity_unit varchar(32) not null,
                    billable_meter varchar(128) not null,
                    billable_dimension varchar(64) not null,
                    billable_quantity_base_units bigint not null,
                    billable_quantity_unit varchar(32) not null,
                    metering_rule_id varchar(128) not null,
                    metering_rule_version varchar(64) not null,
                    transformation_kind varchar(64) not null,
                    transformation_details text not null,
                    source_observation_timestamp timestamptz not null,
                    metered_at timestamptz not null,
                    idempotency_key varchar(255) not null,
                    trace_id varchar(128) not null,
                    provenance_reference varchar(512) not null,
                    unique (tenant_id, idempotency_key),
                    unique (tenant_id, observed_usage_id, metering_rule_id, metering_rule_version)
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
        jdbc.execute("DROP TRIGGER IF EXISTS fail_billable_insert ON billable_usage");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_billable_insert_for_test()");
        jdbc.execute("TRUNCATE TABLE billable_usage, observed_runtime_usage");
        rules.clear();
        rules.register(millisecondsToSeconds("runtime-duration", "v1"));
        audit.reset();
    }

    @Test
    void deterministicMeteringExactReplayReturnsOneBillableRow() {
        ObservedRuntimeUsage observation = observations.append(observation("observation-key", 1_501));
        MeterUsageCommand command = command(observation, "runtime-duration", "v1");

        BillableUsage first = service.meter(command);
        BillableUsage replay = service.meter(command);

        assertEquals(first, replay);
        assertEquals(1L, billableCount());
        assertEquals(1L, audit.successfulCalls);
        assertEquals(2L, first.billableQuantity().baseUnits());
        assertEquals(UsageUnit.SECONDS, first.billableQuantity().unit());
    }

    @Test
    void sameKeyWithDifferentObservationRuleVersionOrOutputFailsClosed() {
        ObservedRuntimeUsage observation = observations.append(observation("mismatch-key", 1_501));
        BillableUsage saved = service.meter(command(observation, "runtime-duration", "v1"));
        List<BillableUsage> mismatches = List.of(
                copy(saved, "different-observation", saved.meteringRuleId(),
                        saved.meteringRuleVersion(), saved.billableQuantity()),
                copy(saved, saved.observedUsageId(), "different-rule",
                        saved.meteringRuleVersion(), saved.billableQuantity()),
                copy(saved, saved.observedUsageId(), saved.meteringRuleId(),
                        "different-version", saved.billableQuantity()),
                copy(saved, saved.observedUsageId(), saved.meteringRuleId(),
                        saved.meteringRuleVersion(),
                        UsageQuantity.fromBaseUnits(3, UsageUnit.SECONDS)));

        assertAll(mismatches.stream().map(mismatch -> () -> {
            IllegalStateException failure = assertThrows(
                    IllegalStateException.class, () -> billable.append(mismatch));
            assertEquals("Idempotency key reused with different billable usage payload",
                    failure.getMessage());
        }));
        assertEquals(1L, billableCount());
    }

    @Test
    void ruleVersionTransformationAndProvenanceRoundTrip() {
        ObservedRuntimeUsage observation = observations.append(observation("lineage-key", 1_501));
        BillableUsage saved = service.meter(command(observation, "runtime-duration", "v1"));
        BillableUsage loaded = billable.findByTenantAndId(
                "tenant-a", saved.billableUsageId()).orElseThrow();

        assertEquals(saved, loaded);
        assertEquals("runtime-duration", loaded.meteringRuleId());
        assertEquals("v1", loaded.meteringRuleVersion());
        assertEquals(MeteringTransformationKind.ROUND_UP_INCREMENT,
                loaded.transformationKind());
        assertTrue(loaded.transformationDetails().contains("numerator=1"));
        assertTrue(loaded.transformationDetails().contains("denominator=1000"));
        assertEquals(observation.occurredAt(), loaded.sourceObservationTimestamp());
        assertTrue(loaded.provenanceReference().contains(observation.observedUsageId()));
    }

    @Test
    void concurrentDuplicateMeteringCreatesOneRow() throws Exception {
        ObservedRuntimeUsage observation = observations.append(observation("concurrent-key", 1_501));
        MeterUsageCommand command = command(observation, "runtime-duration", "v1");

        List<BillableUsage> results = concurrently(12, () -> service.meter(command));

        assertEquals(1, results.stream().distinct().count());
        assertEquals(1L, billableCount());
        assertEquals(1L, audit.successfulCalls);
    }

    @Test
    void unknownMissingOrMismatchedRuleDataFailsClosed() {
        ObservedRuntimeUsage observation = observations.append(observation("rule-key", 1_501));

        assertThrows(IllegalStateException.class,
                () -> service.meter(command(observation, "unknown", "v1")));
        assertThrows(IllegalArgumentException.class,
                () -> new MeterUsageCommand("tenant-a", observation.observedUsageId(),
                        "runtime-duration", " ", METERED, "trace-meter"));
        assertThrows(NullPointerException.class,
                () -> new MeteringRule("missing-unit", "v1", UsageDimension.DURATION, null,
                        "meter", UsageDimension.DURATION, UsageUnit.SECONDS,
                        1, 1_000, 1, MeteringTransformationKind.ROUND_UP_INCREMENT,
                        "missing unit"));

        rules.register(new MeteringRule(
                "wrong-mapping", "v1", UsageDimension.TOKEN_INPUT, UsageUnit.TOKEN,
                "render_seconds", UsageDimension.DURATION, UsageUnit.SECONDS,
                1, 1, 1, MeteringTransformationKind.IDENTITY, "wrong source"));
        assertThrows(IllegalStateException.class,
                () -> service.meter(command(observation, "wrong-mapping", "v1")));
        assertEquals(0L, billableCount());
    }

    @Test
    void observationPersistenceFailureCannotCreateBillableUsage() {
        assertThrows(IllegalStateException.class,
                () -> service.meter(new MeterUsageCommand(
                        "tenant-a", "missing-observation", "runtime-duration", "v1",
                        METERED, "trace-meter")));
        assertEquals(0L, billableCount());
        assertEquals(0L, audit.successfulCalls);
    }

    @Test
    void meteringCannotReadObservationAcrossTenantBoundary() {
        ObservedRuntimeUsage observation = observations.append(observation("tenant-isolation", 10));

        assertThrows(IllegalStateException.class, () -> service.meter(new MeterUsageCommand(
                "tenant-b", observation.observedUsageId(), "runtime-duration", "v1",
                METERED, "trace-cross-tenant")));

        assertEquals(0L, billableCount());
        assertTrue(billable.findByTenant("tenant-b").isEmpty());
    }

    @Test
    void billableAuditFailureRollsBackInsertedBillableRow() {
        ObservedRuntimeUsage observation = observations.append(observation("audit-key", 1_501));
        audit.fail = true;

        assertThrows(RuntimeException.class,
                () -> service.meter(command(observation, "runtime-duration", "v1")));

        assertEquals(0L, billableCount());
        assertEquals(0L, audit.successfulCalls);
    }

    @Test
    void billablePersistenceFailureCannotCreateAuditOrPartialState() {
        ObservedRuntimeUsage observation = observations.append(observation("persist-key", 1_501));
        jdbc.execute("""
                CREATE FUNCTION fail_billable_insert_for_test() RETURNS trigger AS $$
                BEGIN RAISE EXCEPTION 'forced billable failure'; END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER fail_billable_insert BEFORE INSERT ON billable_usage
                FOR EACH ROW EXECUTE FUNCTION fail_billable_insert_for_test()
                """);

        assertThrows(RuntimeException.class,
                () -> service.meter(command(observation, "runtime-duration", "v1")));

        assertEquals(0L, billableCount());
        assertEquals(0L, audit.successfulCalls);
    }

    private static ObservedRuntimeUsage observation(String idempotencyKey, long quantity) {
        return ObservedRuntimeUsage.observe(
                "tenant-a", "project-a", new CanonicalActorRef("user-a", "USER"),
                OperationRef.of("operation-a", "attempt-a"), "execution-a",
                new ProviderRef("provider-a"), "capability-a", UsageDimension.DURATION,
                UsageQuantity.fromBaseUnits(quantity, UsageUnit.MILLISECONDS),
                RuntimeOutcome.SUCCEEDED, OCCURRED, OCCURRED.plusSeconds(1),
                OCCURRED.plusSeconds(2), UsageProvenance.REPORTED, "runtime",
                "source-ref-a", "trace-observation", idempotencyKey);
    }

    private static MeterUsageCommand command(
            ObservedRuntimeUsage observation, String ruleId, String version) {
        return new MeterUsageCommand(
                observation.tenantId(), observation.observedUsageId(), ruleId, version,
                METERED, "trace-meter");
    }

    private static MeteringRule millisecondsToSeconds(String id, String version) {
        return new MeteringRule(
                id, version, UsageDimension.DURATION, UsageUnit.MILLISECONDS,
                "render_seconds", UsageDimension.DURATION, UsageUnit.SECONDS,
                1, 1_000, 1, MeteringTransformationKind.ROUND_UP_INCREMENT,
                "ceil milliseconds to whole seconds");
    }

    private static BillableUsage copy(
            BillableUsage source,
            String observedUsageId,
            String ruleId,
            String ruleVersion,
            UsageQuantity output) {
        return new BillableUsage(
                "bu-different", source.tenantId(), source.principalRef(), observedUsageId,
                source.observedDimension(), source.observedQuantity(), source.billableMeter(),
                source.billableDimension(), output, ruleId, ruleVersion,
                source.transformationKind(), source.transformationDetails(),
                source.sourceObservationTimestamp(), source.meteredAt(), source.idempotencyKey(),
                source.traceId(), source.provenanceReference());
    }

    private static long billableCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM billable_usage", Long.class);
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

    static final class TestAudit implements BillableUsageAuditPort {
        private volatile boolean fail;
        private volatile long successfulCalls;

        @Override
        public synchronized void recordMetered(BillableUsage usage) {
            if (fail) {
                throw new RuntimeException("forced billable audit failure");
            }
            successfulCalls++;
        }

        void reset() {
            fail = false;
            successfulCalls = 0;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        private static DataSource dataSource;

        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
        @Bean ObservedRuntimeUsageJdbcRepository observations(JdbcTemplate template) {
            return new ObservedRuntimeUsageJdbcRepository(template);
        }
        @Bean BillableUsageJdbcRepository billable(JdbcTemplate template) {
            return new BillableUsageJdbcRepository(template);
        }
        @Bean MeteringRuleRegistry rules() { return new MeteringRuleRegistry(); }
        @Bean TestAudit audit() { return new TestAudit(); }
        @Bean UsageMeteringService service(
                ObservedRuntimeUsageJdbcRepository observations,
                BillableUsageJdbcRepository billable,
                MeteringRuleRegistry rules,
                TestAudit audit) {
            return new UsageMeteringService(observations, billable, rules, audit);
        }
    }
}
