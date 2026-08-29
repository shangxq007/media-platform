package com.example.platform.entitlement.app;

import com.example.platform.entitlement.domain.EntitlementCommandResult;
import com.example.platform.entitlement.domain.EntitlementCommandType;
import com.example.platform.entitlement.domain.EntitlementGrantCommand;
import com.example.platform.entitlement.infrastructure.EntitlementCommandAuditRepository;
import com.example.platform.entitlement.infrastructure.EntitlementGrantRepository;
import com.example.platform.entitlement.infrastructure.InMemoryEntitlementCache;
import com.example.platform.entitlement.infrastructure.WorkspaceMemberEntitlementGrantRepository;
import com.example.platform.shared.commercial.PrincipalRef;
import com.example.platform.shared.commercial.PrincipalType;
import com.example.platform.shared.test.PostgresTestContainerSupport;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntitlementCommandAuthorityPostgresTest extends PostgresTestContainerSupport {

    private static final Instant EFFECTIVE = Instant.parse("2026-08-29T00:00:00Z");
    private static final Instant EXPIRY = Instant.parse("2026-09-29T00:00:00Z");
    private static DataSource dataSource;
    private static AnnotationConfigApplicationContext context;
    private static JdbcTemplate jdbc;
    private static EntitlementService authority;
    private static InMemoryEntitlementCache cache;

    @BeforeAll
    static void startDatabase() {
        dataSource = createDataSource(TX_HEAVY_MAX_POOL_SIZE);
        TestConfiguration.dataSource = dataSource;
        context = new AnnotationConfigApplicationContext(TestConfiguration.class);
        jdbc = context.getBean(JdbcTemplate.class);
        authority = context.getBean(EntitlementService.class);
        cache = context.getBean(InMemoryEntitlementCache.class);
        createTables();
    }

    @AfterAll
    static void stopDatabase() {
        if (context != null) context.close();
        closeDataSource(dataSource);
    }

    @BeforeEach
    void reset() {
        createTables();
        jdbc.execute("TRUNCATE entitlement_command_audit, workspace_member_entitlement_grant, entitlement_grant CASCADE");
        cache.clear();
    }

    @Test
    void exactDuplicateReplaysAndDifferentPayloadFailsClosed() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        EntitlementGrantCommand command = grant(principal, "grant-a", "feature-a", "same-key", "actor");

        EntitlementCommandResult first = authority.execute(command);
        EntitlementCommandResult replay = authority.execute(command);

        assertEquals(first.commandId(), replay.commandId());
        assertEquals(first.grant(), replay.grant());
        assertEquals(1L, count("entitlement_grant"));
        assertEquals(1L, count("entitlement_command_audit"));
        assertThrows(IllegalStateException.class,
                () -> authority.execute(grant(principal, "grant-b", "feature-b", "same-key", "actor")));
    }

    @Test
    void grantExtendRevokeTransitionsAreExplicitAndIllegalTransitionsFailClosed() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        EntitlementCommandResult granted = authority.execute(
                grant(principal, "grant-a", "feature-a", "grant-key", "actor"));
        EntitlementCommandResult extended = authority.execute(transition(principal,
                EntitlementCommandType.EXTEND, "grant-a", granted.grant().version(),
                "extend-key", Instant.parse("2026-10-29T00:00:00Z"), "actor"));
        EntitlementCommandResult revoked = authority.execute(transition(principal,
                EntitlementCommandType.REVOKE, "grant-a", extended.grant().version(),
                "revoke-key", null, "actor"));

        assertEquals("REVOKED", revoked.grant().status());
        assertThrows(IllegalStateException.class, () -> authority.execute(transition(principal,
                EntitlementCommandType.EXTEND, "grant-a", revoked.grant().version(),
                "illegal-extend", EXPIRY.plusSeconds(1), "actor")));
        assertThrows(IllegalStateException.class, () -> authority.execute(transition(principal,
                EntitlementCommandType.REVOKE, "grant-a", 0, "stale-revoke", null, "actor")));
    }

    @Test
    void crossTenantReadAndMutationFailClosed() {
        PrincipalRef tenantA = principal("tenant-a", "same-user");
        PrincipalRef tenantB = principal("tenant-b", "same-user");
        authority.execute(grant(tenantA, "grant-a", "feature-a", "grant-a-key", "actor"));

        assertTrue(authority.findGrant(tenantA, "grant-a").isPresent());
        assertTrue(authority.findGrant(tenantB, "grant-a").isEmpty());
        assertFalse(authority.checkFeature(tenantB, "feature-a").allowed());
        assertThrows(IllegalArgumentException.class, () -> authority.execute(transition(tenantB,
                EntitlementCommandType.REVOKE, "grant-a", 0, "cross-tenant", null, "actor")));
    }

    @Test
    void concurrentDuplicateReplaysAndConcurrentTransitionHasOneWinner() throws Exception {
        PrincipalRef principal = principal("tenant-a", "user-a");
        EntitlementGrantCommand duplicate = grant(principal, "grant-a", "feature-a", "dup", "actor");
        List<EntitlementCommandResult> duplicateResults = concurrently(2, index -> duplicate);
        assertEquals(duplicateResults.get(0).commandId(), duplicateResults.get(1).commandId());
        assertEquals(1L, count("entitlement_grant"));

        long version = duplicateResults.get(0).grant().version();
        List<Boolean> winners = concurrentlyOutcome(2, index -> transition(principal,
                EntitlementCommandType.EXTEND, "grant-a", version, "race-" + index,
                EXPIRY.plusSeconds(index + 1L), "actor"));
        assertEquals(1L, winners.stream().filter(Boolean::booleanValue).count());
    }

    @Test
    void forcedAuditCompletionFailureRollsBackGrantAndCommandClaim() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        assertThrows(RuntimeException.class, () -> authority.execute(
                grant(principal, "grant-a", "feature-a", "rollback", "force-audit-failure")));
        assertEquals(0L, count("entitlement_grant"));
        assertEquals(0L, count("entitlement_command_audit"));
    }

    @Test
    void persistenceFailureDoesNotMutateCacheOrGrantAccess() {
        PrincipalRef principal = principal("tenant-a", "user-a");
        jdbc.execute("DROP TABLE entitlement_grant");

        assertThrows(RuntimeException.class, () -> authority.execute(
                grant(principal, "grant-a", "feature-a", "db-down", "actor")));
        assertNull(cache.get(principal.tenantId() + ":" + principal.principalId()));
        assertFalse(authority.checkFeature(principal, "feature-a").allowed());
    }

    @Test
    void workspaceMemberGrantUsesSameCommandBoundaryAndTenantScope() {
        PrincipalRef member = new PrincipalRef(
                "tenant-a", PrincipalType.USER, "member-a", "workspace-a", null);
        EntitlementCommandResult granted = authority.execute(new EntitlementGrantCommand(
                EntitlementCommandType.WORKSPACE_GRANT, member, "workspace-grant-a", "feature-a", null,
                "ADMIN", "allocation-a", "workspace-key", "actor", "allocation", "trace-workspace",
                EFFECTIVE, EXPIRY, 0));
        assertTrue(granted.grant().workspaceGrant());
        assertTrue(authority.checkFeature(member, "feature-a").allowed());
        PrincipalRef wrongTenant = new PrincipalRef(
                "tenant-b", PrincipalType.USER, "member-a", "workspace-a", null);
        assertFalse(authority.checkFeature(wrongTenant, "feature-a").allowed());
    }

    private static void createTables() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS entitlement_grant (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    subject_type varchar(32) not null, subject_id varchar(128) not null,
                    bundle_code varchar(128) not null, quota_profile_code varchar(128),
                    source_type varchar(32) not null, source_ref varchar(255) not null,
                    grant_status varchar(32) not null, effective_at timestamptz not null,
                    expires_at timestamptz, version bigint not null,
                    created_at timestamptz not null, updated_at timestamptz not null,
                    unique (tenant_id, subject_type, subject_id, bundle_code, source_type, source_ref))
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS workspace_member_entitlement_grant (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    workspace_id varchar(64) not null, principal_type varchar(32) not null,
                    member_id varchar(128) not null, feature_key varchar(128) not null,
                    quota_amount bigint not null default 0,
                    source_type varchar(32) not null, source_ref varchar(255) not null,
                    starts_at timestamptz not null, expires_at timestamptz,
                    status varchar(32) not null, version bigint not null,
                    granted_by varchar(128) not null, created_at timestamptz not null,
                    updated_at timestamptz not null,
                    unique (tenant_id, workspace_id, principal_type, member_id, feature_key, source_type, source_ref))
                """);
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS entitlement_command_audit (
                    id varchar(64) primary key, tenant_id varchar(64) not null,
                    principal_type varchar(32) not null, principal_id varchar(128) not null,
                    idempotency_key varchar(255) not null, command_type varchar(32) not null,
                    payload_fingerprint text not null, result_snapshot text,
                    actor varchar(128) not null, reason varchar(512) not null,
                    trace_id varchar(128) not null, created_at timestamptz not null,
                    completed_at timestamptz,
                    unique (tenant_id, idempotency_key),
                    check (completed_at is null or actor <> 'force-audit-failure'))
                """);
    }

    private static EntitlementGrantCommand grant(
            PrincipalRef principal, String grantId, String feature, String key, String actor) {
        return new EntitlementGrantCommand(EntitlementCommandType.GRANT, principal, grantId, feature,
                "quota-a", "ADMIN", "source-a", key, actor, "test", "trace-" + key,
                EFFECTIVE, EXPIRY, 0);
    }

    private static EntitlementGrantCommand transition(
            PrincipalRef principal, EntitlementCommandType type, String grantId, long version,
            String key, Instant expiresAt, String actor) {
        return new EntitlementGrantCommand(type, principal, grantId, null, null,
                "ADMIN", "source-a", key, actor, "test", "trace-" + key,
                EFFECTIVE, expiresAt, version);
    }

    private static PrincipalRef principal(String tenant, String user) {
        return PrincipalRef.tenantScoped(tenant, PrincipalType.USER, user);
    }

    private static long count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }

    private static List<EntitlementCommandResult> concurrently(
            int count, java.util.function.IntFunction<EntitlementGrantCommand> commands) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<EntitlementCommandResult>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    return authority.execute(commands.apply(index));
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<EntitlementCommandResult> results = new ArrayList<>();
            for (Future<EntitlementCommandResult> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { executor.shutdownNow(); }
    }

    private static List<Boolean> concurrentlyOutcome(
            int count, java.util.function.IntFunction<EntitlementGrantCommand> commands) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(count);
        CountDownLatch ready = new CountDownLatch(count);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                int index = i;
                futures.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try { authority.execute(commands.apply(index)); return true; }
                    catch (IllegalStateException expected) { return false; }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            List<Boolean> results = new ArrayList<>();
            for (Future<Boolean> future : futures) results.add(future.get(30, TimeUnit.SECONDS));
            return results;
        } finally { executor.shutdownNow(); }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = true)
    static class TestConfiguration {
        private static DataSource dataSource;
        @Bean DataSource dataSource() { return dataSource; }
        @Bean JdbcTemplate jdbcTemplate(DataSource source) { return new JdbcTemplate(source); }
        @Bean InMemoryEntitlementCache cache() { return new InMemoryEntitlementCache(); }
        @Bean EntitlementGrantRepository grantRepository(JdbcTemplate template) {
            return new EntitlementGrantRepository(template);
        }
        @Bean WorkspaceMemberEntitlementGrantRepository workspaceRepository(JdbcTemplate template) {
            return new WorkspaceMemberEntitlementGrantRepository(template);
        }
        @Bean EntitlementCommandAuditRepository auditRepository(JdbcTemplate template) {
            return new EntitlementCommandAuditRepository(template);
        }
        @Bean EntitlementService entitlementService(
                EntitlementGrantRepository grants, WorkspaceMemberEntitlementGrantRepository workspace,
                EntitlementCommandAuditRepository audit, InMemoryEntitlementCache cache) {
            return new EntitlementService(grants, workspace, audit, cache);
        }
        @Bean PlatformTransactionManager transactionManager(DataSource source) {
            return new DataSourceTransactionManager(source);
        }
    }
}
