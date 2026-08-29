package com.example.platform.billing.app;

import com.example.platform.billing.domain.SubscriptionCommand;
import com.example.platform.billing.domain.SubscriptionCommandResult;
import com.example.platform.billing.domain.SubscriptionCommandType;
import com.example.platform.billing.domain.SubscriptionContractRole;
import com.example.platform.billing.infrastructure.SubscriptionJdbcRepository;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SubscriptionCommandAuthorityPostgresTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static SubscriptionBillingService authority;

    @BeforeAll
    static void startDatabase() {
        dataSource = createDataSource(TX_HEAVY_MAX_POOL_SIZE);
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        authority = context.getBean(SubscriptionBillingService.class);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS subscription_plan (
                    id varchar(64) primary key, plan_key varchar(128) not null unique,
                    name varchar(255) not null, description text, billing_interval varchar(32) not null,
                    base_price_minor bigint not null, currency_code varchar(8) not null,
                    included_quota text, status varchar(32) not null,
                    created_at timestamptz not null, updated_at timestamptz not null)
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS subscription_contract (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    subject_type varchar(32) not null, subject_id varchar(128) not null,
                    canonical_product_code varchar(128) not null, contract_role varchar(32) not null,
                    contract_state varchar(32) not null, period_start_at timestamptz not null,
                    period_end_at timestamptz, created_at timestamptz not null,
                    updated_at timestamptz not null, plan_key varchar(128) not null,
                    included_quota_used text, version bigint not null)
                """);
        jdbc.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_active_base
                ON subscription_contract (tenant_id, subject_type, subject_id)
                WHERE contract_state = 'ACTIVE' AND contract_role = 'BASE'
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS subscription_command (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    idempotency_key varchar(255) not null, command_type varchar(32) not null,
                    payload_fingerprint text not null, result_snapshot text,
                    actor varchar(128) not null, reason varchar(512) not null,
                    trace_id varchar(128) not null, created_at timestamptz not null,
                    completed_at timestamptz,
                    unique (tenant_id, idempotency_key))
                """);
    }

    @AfterAll
    static void stopDatabase() {
        if (context != null) context.close();
        closeDataSource(dataSource);
    }

    @BeforeEach
    void reset() {
        jdbc.execute("TRUNCATE subscription_command, subscription_contract, subscription_plan CASCADE");
        authority.createPlan("basic", "Basic", "", "MONTHLY", 1000, "USD", Map.of());
        authority.createPlan("pro", "Pro", "", "MONTHLY", 3000, "USD", Map.of());
    }

    @Test
    void exactDuplicateReplaysAndDifferentPayloadFailsClosed() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        SubscriptionCommand create = create(principal, "contract-a", "basic", "same-key");

        SubscriptionCommandResult first = authority.execute(create);
        SubscriptionCommandResult replay = authority.execute(create);

        assertEquals(first.commandId(), replay.commandId());
        assertEquals(first.contract(), replay.contract());
        assertEquals(1L, count("subscription_contract"));
        assertEquals(1L, count("subscription_command"));
        assertThrows(IllegalStateException.class,
                () -> authority.execute(create(principal, "contract-b", "pro", "same-key")));
    }

    @Test
    void staleIllegalAndCrossTenantTransitionsFailClosed() {
        PrincipalRef tenantA = principal("tenant-a", "shared-user");
        PrincipalRef tenantB = principal("tenant-b", "shared-user");
        SubscriptionCommandResult created = authority.execute(create(tenantA, "contract-a", "basic", "create-a"));

        assertNull(authority.getContract(tenantB, "contract-a"));
        assertThrows(IllegalArgumentException.class, () -> authority.execute(
                transition(tenantB, SubscriptionCommandType.CANCEL, "contract-a", null, 0, "cross-tenant")));

        SubscriptionCommandResult cancelled = authority.execute(
                transition(tenantA, SubscriptionCommandType.CANCEL, "contract-a", null,
                        created.contract().version(), "cancel-a"));
        assertEquals("CANCELLED", cancelled.contract().lifecycleState());
        assertThrows(IllegalStateException.class, () -> authority.execute(
                transition(tenantA, SubscriptionCommandType.CHANGE, "contract-a", "pro",
                        created.contract().version(), "stale-change")));
        assertThrows(IllegalStateException.class, () -> authority.execute(
                transition(tenantA, SubscriptionCommandType.CANCEL, "contract-a", null,
                        cancelled.contract().version(), "illegal-cancel")));
    }

    @Test
    void concurrentTransitionHasExactlyOneWinner() throws Exception {
        PrincipalRef principal = principal("tenant-a", "user-a");
        SubscriptionCommandResult created = authority.execute(create(principal, "contract-a", "basic", "create-a"));
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try {
                        authority.execute(transition(principal, SubscriptionCommandType.CHANGE,
                                "contract-a", "pro", created.contract().version(), "race-" + index));
                        return true;
                    } catch (IllegalStateException expected) {
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            long winners = 0;
            for (Future<Boolean> future : futures) if (future.get(30, TimeUnit.SECONDS)) winners++;
            assertEquals(1, winners);
            assertEquals(1L, authority.getContract(principal, "contract-a").version());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void baseReplacementIsAtomicAndForcedInsertFailureRollsBackPriorCancellation() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        authority.execute(create(principal, "old-base", "basic", "create-old"));
        jdbc.update("""
                INSERT INTO subscription_contract
                    (id, tenant_id, subject_type, subject_id, canonical_product_code, contract_role,
                     contract_state, period_start_at, period_end_at, created_at, updated_at,
                     plan_key, included_quota_used, version)
                VALUES ('collision', 'tenant-z', 'USER', 'user-z', 'basic', 'ADD_ON',
                        'ACTIVE', now(), now() + interval '30 days', now(), now(), 'basic', '{}', 0)
                """);

        assertThrows(RuntimeException.class,
                () -> authority.execute(create(principal, "collision", "pro", "replace-fails")));

        assertEquals("ACTIVE", authority.getContract(principal, "old-base").lifecycleState());
        assertEquals(1L, authority.listActiveSubscriptions(principal).stream()
                .filter(c -> c.contractRole() == SubscriptionContractRole.BASE).count());
        assertEquals(0L, jdbc.queryForObject(
                "SELECT count(*) FROM subscription_command WHERE idempotency_key = 'replace-fails'", Long.class));
    }

    @Test
    void successfulBaseReplacementCancelsPriorBaseInSameCommand() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        authority.execute(create(principal, "old-base", "basic", "create-old"));
        SubscriptionCommandResult replacement = authority.execute(
                create(principal, "new-base", "pro", "replace-ok"));

        assertEquals("CANCELLED", authority.getContract(principal, "old-base").lifecycleState());
        assertEquals("ACTIVE", replacement.contract().lifecycleState());
        assertNotEquals("old-base", replacement.contract().contractId());
    }

    private static SubscriptionCommand create(
            PrincipalRef principal, String contractId, String planKey, String key) {
        return new SubscriptionCommand(
                SubscriptionCommandType.CREATE, principal, contractId, planKey, planKey,
                30, SubscriptionContractRole.BASE, 0, key, "actor", "test", "trace-" + key,
                Instant.parse("2026-08-29T00:00:00Z"));
    }

    private static SubscriptionCommand transition(
            PrincipalRef principal, SubscriptionCommandType type, String contractId,
            String planKey, long expectedVersion, String key) {
        return new SubscriptionCommand(type, principal, contractId, planKey, planKey,
                30, SubscriptionContractRole.BASE, expectedVersion, key,
                "actor", "test", "trace-" + key, Instant.parse("2026-08-29T00:00:00Z"));
    }

    private static PrincipalRef principal(String tenant, String user) {
        return PrincipalRef.tenantScoped(tenant, PrincipalType.USER, user);
    }

    private static long count(String table) {
        assertNotNull(table);
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        private static DataSource dataSource;

        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean SubscriptionJdbcRepository subscriptionJdbcRepository(JdbcTemplate template) {
            return new SubscriptionJdbcRepository(template);
        }
        @Bean SubscriptionBillingService subscriptionBillingService(SubscriptionJdbcRepository repository) {
            return new SubscriptionBillingService(repository);
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }
}
