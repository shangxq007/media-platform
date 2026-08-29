package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.QuotaOperationKind;
import com.example.platform.entitlement.domain.QuotaUsageCommand;
import com.example.platform.entitlement.domain.QuotaUsageOutcome;
import com.example.platform.entitlement.domain.QuotaUsageQuery;
import com.example.platform.entitlement.domain.QuotaUsageRejectionReason;
import com.example.platform.entitlement.domain.QuotaUsageResult;
import com.example.platform.entitlement.infrastructure.QuotaUsageJdbcRepository;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuotaUsageAuthorityPostgresTest extends PostgresTestContainerSupport {

    private static final Instant PERIOD_START = Instant.parse("2026-08-01T00:00:00Z");
    private static final Instant PERIOD_END = Instant.parse("2026-09-01T00:00:00Z");
    private static final Instant OCCURRED_AT = Instant.parse("2026-08-29T08:00:00Z");

    private static DataSource dataSource;
    private static JdbcTemplate jdbc;
    private static AnnotationConfigApplicationContext context;
    private static QuotaUsageAuthority authority;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        authority = context.getBean(QuotaUsageAuthority.class);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS quota_usage (
                    id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    principal_type varchar(32) not null,
                    principal_id varchar(128) not null,
                    workspace_scope varchar(64) not null default '',
                    organization_scope varchar(64) not null default '',
                    quota_key varchar(128) not null,
                    period_start timestamptz not null,
                    period_end timestamptz not null,
                    usage_value bigint not null default 0 check (usage_value >= 0),
                    created_at timestamptz not null,
                    updated_at timestamptz not null,
                    unique (tenant_id, principal_type, principal_id, workspace_scope,
                            organization_scope, quota_key, period_start, period_end)
                )
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS quota_usage_operation (
                    id varchar(64) primary key,
                    tenant_id varchar(64) not null,
                    principal_type varchar(32) not null,
                    principal_id varchar(128) not null,
                    workspace_scope varchar(64) not null default '',
                    organization_scope varchar(64) not null default '',
                    quota_key varchar(128) not null,
                    period_start timestamptz not null,
                    period_end timestamptz not null,
                    signed_delta bigint not null,
                    limit_value bigint not null check (limit_value >= 0),
                    idempotency_key varchar(255) not null,
                    operation_kind varchar(32) not null check (
                        operation_kind in ('CONSUMPTION', 'ADJUSTMENT', 'REVERSAL', 'RECONCILIATION')),
                    outcome varchar(32) not null check (outcome in ('PENDING', 'APPLIED', 'REJECTED')),
                    usage_before bigint,
                    usage_after bigint,
                    rejection_reason varchar(64),
                    trace_id varchar(128) not null,
                    reason varchar(512) not null,
                    occurred_at timestamptz not null,
                    created_at timestamptz not null,
                    unique (tenant_id, principal_type, principal_id, workspace_scope,
                            organization_scope, idempotency_key)
                )
                """);
    }

    @AfterAll
    static void tearDownDatabase() {
        if (context != null) {
            context.close();
        }
        closeDataSource(dataSource);
    }

    @BeforeEach
    void resetTables() {
        jdbc.execute("DROP TRIGGER IF EXISTS quota_audit_forced_failure ON quota_usage_operation");
        jdbc.execute("DROP FUNCTION IF EXISTS fail_quota_audit_for_test()");
        jdbc.execute("TRUNCATE TABLE quota_usage_operation, quota_usage");
    }

    @Test
    void atomicConcurrentIncrementsHaveNoLostUpdates() throws Exception {
        PrincipalRef principal = principal("tenant-a", "user-a");

        List<QuotaUsageResult> results = concurrently(20,
                index -> command(principal, "increment-" + index, 1, 100,
                        QuotaOperationKind.CONSUMPTION));

        assertEquals(20, results.stream().filter(QuotaUsageResult::applied).count());
        assertEquals(20, usage(principal, PERIOD_START, PERIOD_END));
        assertEquals(20, auditCount());
    }

    @Test
    void duplicateIdempotencyKeyMutatesOnceAndReturnsSameResult() {
        QuotaUsageCommand command = command(principal("tenant-a", "user-a"),
                "same-key", 7, 100, QuotaOperationKind.CONSUMPTION);

        QuotaUsageResult first = authority.execute(command);
        QuotaUsageResult replay = authority.execute(command);

        assertEquals(first, replay);
        assertEquals(7, usage(command.principal(), PERIOD_START, PERIOD_END));
        assertEquals(1, auditCount());
    }

    @Test
    void reusedIdempotencyKeyWithDifferentSemanticPayloadFailsClosed() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        QuotaUsageCommand original = command(principal,
                "payload-bound-key", 7, 100, QuotaOperationKind.CONSUMPTION);
        authority.execute(original);

        List<QuotaUsageCommand> mismatchedCommands = List.of(
                semanticCommand(principal, "payload-bound-key", "storage",
                        PERIOD_START, PERIOD_END, 7, 100, QuotaOperationKind.CONSUMPTION),
                semanticCommand(principal, "payload-bound-key", "render",
                        PERIOD_START.plus(1, ChronoUnit.DAYS),
                        PERIOD_END.plus(1, ChronoUnit.DAYS),
                        7, 100, QuotaOperationKind.CONSUMPTION),
                semanticCommand(principal, "payload-bound-key", "render",
                        PERIOD_START, PERIOD_END, 8, 100, QuotaOperationKind.CONSUMPTION),
                semanticCommand(principal, "payload-bound-key", "render",
                        PERIOD_START, PERIOD_END, 7, 101, QuotaOperationKind.CONSUMPTION),
                semanticCommand(principal, "payload-bound-key", "render",
                        PERIOD_START, PERIOD_END, 7, 100, QuotaOperationKind.ADJUSTMENT));

        assertAll("every canonical semantic field is bound to the idempotency key",
                mismatchedCommands.stream().map(mismatch -> () -> {
                    IllegalStateException failure = assertThrows(
                            IllegalStateException.class, () -> authority.execute(mismatch));
                    assertEquals("Idempotency key reused with different quota command payload",
                            failure.getMessage());
                }));
        assertEquals(7, usage(principal, PERIOD_START, PERIOD_END));
        assertEquals(1, auditCount());
    }

    @Test
    void concurrentDuplicateKeyHasOneAuditRowAndOneCommittedResult() throws Exception {
        PrincipalRef principal = principal("tenant-a", "user-a");

        List<QuotaUsageResult> results = concurrently(12,
                ignored -> command(principal, "concurrent-same", 9, 100,
                        QuotaOperationKind.CONSUMPTION));

        assertEquals(1, results.stream().distinct().count());
        assertEquals(9, usage(principal, PERIOD_START, PERIOD_END));
        assertEquals(1, auditCount());
    }

    @Test
    void overLimitRaceAdmitsOnlyAllowedWinnersAndNeverExceedsLimit() throws Exception {
        PrincipalRef principal = principal("tenant-a", "user-a");

        List<QuotaUsageResult> results = concurrently(10,
                index -> command(principal, "limit-race-" + index, 3, 10,
                        QuotaOperationKind.CONSUMPTION));

        assertEquals(3, results.stream().filter(QuotaUsageResult::applied).count());
        assertEquals(7, results.stream()
                .filter(result -> result.outcome() == QuotaUsageOutcome.REJECTED)
                .filter(result -> result.rejectionReason() == QuotaUsageRejectionReason.LIMIT_EXCEEDED)
                .count());
        assertEquals(9, usage(principal, PERIOD_START, PERIOD_END));
        assertEquals(10, auditCount());
    }

    @Test
    void negativeResultIsRejectedAndAuditedWithoutUsageMutation() {
        PrincipalRef principal = principal("tenant-a", "user-a");

        QuotaUsageResult result = authority.execute(command(principal, "negative", -1, 100,
                QuotaOperationKind.ADJUSTMENT));

        assertEquals(QuotaUsageOutcome.REJECTED, result.outcome());
        assertEquals(QuotaUsageRejectionReason.NEGATIVE_RESULT, result.rejectionReason());
        assertEquals(0, usage(principal, PERIOD_START, PERIOD_END));
        assertEquals(1, auditCount());
    }

    @Test
    void adjustmentAndReversalAreAppendOnlyAndIdempotent() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        authority.execute(command(principal, "consume", 20, 100, QuotaOperationKind.CONSUMPTION));
        QuotaUsageResult adjustment = authority.execute(
                command(principal, "adjust", 5, 100, QuotaOperationKind.ADJUSTMENT));
        QuotaUsageCommand reversalCommand = command(
                principal, "reverse", -8, 100, QuotaOperationKind.REVERSAL);
        QuotaUsageResult reversal = authority.execute(reversalCommand);
        QuotaUsageResult replay = authority.execute(reversalCommand);

        assertTrue(adjustment.applied());
        assertTrue(reversal.applied());
        assertEquals(reversal, replay);
        assertEquals(17, usage(principal, PERIOD_START, PERIOD_END));
        List<String> operationKinds = jdbc.queryForList(
                "SELECT operation_kind FROM quota_usage_operation", String.class);
        assertEquals(3, operationKinds.size());
        assertTrue(operationKinds.containsAll(
                List.of("CONSUMPTION", "ADJUSTMENT", "REVERSAL")));
        assertEquals(3, auditCount());
    }

    @Test
    void distinctTenantsPrincipalsAndPeriodsAreIsolated() {
        PrincipalRef tenantAUserA = principal("tenant-a", "user-a");
        PrincipalRef tenantAUserB = principal("tenant-a", "user-b");
        PrincipalRef tenantBUserA = principal("tenant-b", "user-a");
        Instant nextStart = PERIOD_END;
        Instant nextEnd = PERIOD_END.plus(30, ChronoUnit.DAYS);

        authority.execute(command(tenantAUserA, "a-a", 1, 100, QuotaOperationKind.CONSUMPTION));
        authority.execute(command(tenantAUserB, "a-b", 2, 100, QuotaOperationKind.CONSUMPTION));
        authority.execute(command(tenantBUserA, "b-a", 3, 100, QuotaOperationKind.CONSUMPTION));
        authority.execute(command(tenantAUserA, "next-period", 4, 100,
                QuotaOperationKind.CONSUMPTION, nextStart, nextEnd));

        assertEquals(1, usage(tenantAUserA, PERIOD_START, PERIOD_END));
        assertEquals(2, usage(tenantAUserB, PERIOD_START, PERIOD_END));
        assertEquals(3, usage(tenantBUserA, PERIOD_START, PERIOD_END));
        assertEquals(4, usage(tenantAUserA, nextStart, nextEnd));
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM quota_usage", Long.class));
    }

    @Test
    void crossTenantReadFailsClosed() {
        PrincipalRef tenantA = principal("tenant-a", "shared-user");
        PrincipalRef tenantB = principal("tenant-b", "shared-user");
        authority.execute(command(tenantA, "tenant-a-only", 11, 100,
                QuotaOperationKind.CONSUMPTION));

        assertEquals(11, usage(tenantA, PERIOD_START, PERIOD_END));
        assertEquals(0, usage(tenantB, PERIOD_START, PERIOD_END));
        assertNotEquals(usage(tenantA, PERIOD_START, PERIOD_END),
                usage(tenantB, PERIOD_START, PERIOD_END));
    }

    @Test
    void transactionRollbackLeavesNeitherUsageNorAuditPartialState() {
        jdbc.execute("""
                CREATE FUNCTION fail_quota_audit_for_test() RETURNS trigger AS $$
                BEGIN
                    IF NEW.trace_id = 'force-rollback' AND NEW.outcome = 'APPLIED' THEN
                        RAISE EXCEPTION 'forced audit failure';
                    END IF;
                    RETURN NEW;
                END;
                $$ LANGUAGE plpgsql
                """);
        jdbc.execute("""
                CREATE TRIGGER quota_audit_forced_failure
                BEFORE UPDATE ON quota_usage_operation
                FOR EACH ROW EXECUTE FUNCTION fail_quota_audit_for_test()
                """);
        PrincipalRef principal = principal("tenant-a", "user-a");
        QuotaUsageCommand command = new QuotaUsageCommand(
                principal, "render", PERIOD_START, PERIOD_END, 5, 100,
                "rollback", QuotaOperationKind.CONSUMPTION, "force-rollback",
                "rollback proof", OCCURRED_AT);

        assertThrows(RuntimeException.class, () -> authority.execute(command));
        assertEquals(0, usage(principal, PERIOD_START, PERIOD_END));
        assertEquals(0, auditCount());
    }

    private static PrincipalRef principal(String tenantId, String principalId) {
        return PrincipalRef.tenantScoped(tenantId, PrincipalType.USER, principalId);
    }

    private static QuotaUsageCommand command(
            PrincipalRef principal, String idempotencyKey, long delta, long limit,
            QuotaOperationKind kind) {
        return command(principal, idempotencyKey, delta, limit, kind, PERIOD_START, PERIOD_END);
    }

    private static QuotaUsageCommand command(
            PrincipalRef principal, String idempotencyKey, long delta, long limit,
            QuotaOperationKind kind, Instant periodStart, Instant periodEnd) {
        return semanticCommand(principal, idempotencyKey, "render", periodStart, periodEnd,
                delta, limit, kind);
    }

    private static QuotaUsageCommand semanticCommand(
            PrincipalRef principal, String idempotencyKey, String quotaKey,
            Instant periodStart, Instant periodEnd, long delta, long limit,
            QuotaOperationKind kind) {
        return new QuotaUsageCommand(principal, quotaKey, periodStart, periodEnd,
                delta, limit, idempotencyKey, kind, "trace-" + idempotencyKey,
                "test operation", OCCURRED_AT);
    }

    private static long usage(PrincipalRef principal, Instant periodStart, Instant periodEnd) {
        return authority.currentUsage(new QuotaUsageQuery(
                principal, "render", periodStart, periodEnd, 0, 100,
                "read-trace", OCCURRED_AT));
    }

    private static long auditCount() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM quota_usage_operation", Long.class);
    }

    private static List<QuotaUsageResult> concurrently(
            int count, IntFunction<QuotaUsageCommand> commandFactory) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<QuotaUsageResult>> futures = new ArrayList<>();
            for (int index = 0; index < count; index++) {
                int commandIndex = index;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return authority.execute(commandFactory.apply(commandIndex));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<QuotaUsageResult> results = new ArrayList<>();
            for (Future<QuotaUsageResult> future : futures) {
                results.add(future.get(30, TimeUnit.SECONDS));
            }
            return results;
        } finally {
            executor.shutdownNow();
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        private static DataSource dataSource;

        @Bean
        DataSource dataSource() {
            return dataSource;
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource source) {
            return new JdbcTemplate(source);
        }

        @Bean
        QuotaUsageJdbcRepository quotaUsageJdbcRepository(JdbcTemplate template) {
            return new QuotaUsageJdbcRepository(template);
        }

        @Bean
        QuotaUsageAuthority quotaUsageAuthority(QuotaUsageJdbcRepository repository) {
            return new QuotaUsageAuthority(repository);
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }
}
