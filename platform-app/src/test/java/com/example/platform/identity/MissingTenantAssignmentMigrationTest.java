package com.example.platform.identity;

import com.example.platform.identity.app.PermissionService;
import com.example.platform.identity.infrastructure.RoleRepository;
import java.util.Map;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.jooq.SQLDialect;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static org.junit.jupiter.api.Assertions.*;

/** Promotes IndependentCorrectionReviewTest.independentMissingTenantCounterexample.
 * Uses the real V1 schema without disabling constraints and production permission readers.
 */
class MissingTenantAssignmentMigrationTest extends AccountResourceExecutionAcceptanceTest {
    @ParameterizedTest
    @CsvSource({
        "workspace,missing,false", "workspace,missing,true",
        "tenant,missing,false", "tenant,missing,true",
        "workspace,ACTIVE,false", "project,ACTIVE,false", "tenant,ACTIVE,false",
        "workspace,INACTIVE,false", "project,INACTIVE,false", "tenant,INACTIVE,false"
    })
    void missingTenantUpgradePreservesHistory(String scope, String tenantState, boolean inferred) throws Exception {
        String schema = "missing_tenant_" + UUID.randomUUID().toString().replace("-", "");
        var config = Flyway.configure().dataSource(jdbc.getDataSource()).schemas(schema)
                .defaultSchema(schema).locations("classpath:db/migration");
        config.target("1").load().migrate();
        boolean missing = tenantState.equals("missing");
        String role = jdbc.queryForObject("select role_id from user_role_assignment where user_id=? and workspace_id=? limit 1", String.class, member, wsA);
        if (!missing) jdbc.update("insert into " + schema + ".tenant(id,name,status,created_at) values (?,'Historical',?,now())", tenant, tenantState);
        jdbc.update("insert into " + schema + ".\"user\"(id,tenant_id,username,email,role,status,created_at) values (?,?,'historical','historical@test.invalid','MEMBER','ACTIVE',now())", member, tenant);
        jdbc.update("insert into " + schema + ".workspace select * from public.workspace where id=?", wsA);
        if (!missing) jdbc.update("insert into " + schema + ".project(id,tenant_id,name,status,created_at) values (?,?,'Historical','ACTIVE',now())", projectA, tenant);
        jdbc.update("insert into " + schema + ".role select * from public.role where id=?", role);
        jdbc.update("insert into " + schema + ".permission select p.* from public.permission p join public.role_permission rp on p.id=rp.permission_id where rp.role_id=?", role);
        jdbc.update("insert into " + schema + ".role_permission select * from public.role_permission where role_id=?", role);
        String assignment = "missing-tenant-" + UUID.randomUUID();
        String legacyScope = scope.equals("tenant") ? null : scope.equals("project") ? projectA : wsA;
        jdbc.update("insert into " + schema + ".user_role_assignment(id,tenant_id,workspace_id,user_id,role_id,assigned_by,created_at) values (?,?,?,?,?,'historical-owner',now())",
                assignment, inferred ? null : tenant, legacyScope, member, role);
        config.target("3").load().migrate();
        // V2 infers a tenant only from agreeing resource/member references. An unscoped
        // NULL stays NULL and is already quarantined by V3; V4 must preserve it too.
        boolean alreadyUnresolved = inferred && scope.equals("tenant");
        assertEquals(alreadyUnresolved, jdbc.queryForObject("select scope_unresolved from " + schema + ".user_role_assignment where id=?", Boolean.class, assignment));
        var before = row(schema, assignment);
        try (var connection = jdbc.getDataSource().getConnection()) {
            String previousSchema = connection.getSchema();
            try {
                connection.setSchema(schema);
                var dsl = DSL.using(connection, SQLDialect.POSTGRES, new Settings().withRenderSchema(false));
                var roles = new RoleRepository(dsl);
                var permissions = new PermissionService(roles);
                assertEquals(!alreadyUnresolved, roles.findUserRoleAssignmentsByUserId(member).stream().anyMatch(a -> assignment.equals(a.id())));
                assertScopePermissions(permissions, scope, !alreadyUnresolved);
                if (missing) assertEquals(0, dsl.fetchCount(DSL.table("tenant")));

                assertEquals(1, config.target(MigrationVersion.LATEST).load().migrate().migrationsExecuted);
                var after = row(schema, assignment);
                var expected = before.deepCopy();
                expected.put("scope_unresolved", missing || alreadyUnresolved);
                assertEquals(expected, after, "Only scope_unresolved may change; every previous field survives");
                assertScopePermissions(permissions, scope, !missing);
                if (missing) {
                    assertExcluded(roles, permissions, assignment);
                    // Explicit fixture materialization, no role grant: same upgraded schema.
                    jdbc.update("insert into " + schema + ".tenant select * from public.tenant where id=?", tenant);
                    jdbc.update("insert into " + schema + ".account select * from public.account where id=?", account);
                    jdbc.update("update " + schema + ".\"user\" set account_id=? where id=?", account, member);
                    jdbc.update("insert into " + schema + ".workspace_member select * from public.workspace_member where workspace_id=? and user_id=?", wsA, member);
                    jdbc.update("insert into " + schema + ".project(id,tenant_id,workspace_id,name,status,created_at) values (?,?,?,'Materialized','ACTIVE',now())", projectA, tenant, wsA);
                    assertExcluded(roles, permissions, assignment);
                    assertEquals(after, row(schema, assignment));
                    verifyMaterializedConsumer(schema, assignment, role, after);
                }
                assertEquals(0, config.load().migrate().migrationsExecuted);
                assertEquals(after, row(schema, assignment), "Flyway replay cannot clear quarantine");
            } finally {
                connection.setSchema(previousSchema);
            }
        }
    }

    private com.fasterxml.jackson.databind.node.ObjectNode row(String schema, String assignment) throws Exception {
        return (com.fasterxml.jackson.databind.node.ObjectNode) json.readTree(jdbc.queryForObject(
                "select to_jsonb(a)::text from " + schema + ".user_role_assignment a where id=?", String.class, assignment));
    }

    private void assertScopePermissions(PermissionService permissions, String scope, boolean usable) {
        assertEquals(usable && scope.equals("workspace"), permissions.hasPermission(member, tenant, wsA, "READ"));
        assertEquals(usable && scope.equals("workspace"), permissions.resolvePermissions(member, wsA).contains("READ"));
        assertEquals(usable && scope.equals("tenant"), permissions.hasTenantPermission(member, tenant, "READ"));
        assertEquals(usable && scope.equals("project"), permissions.hasProjectPermission(member, tenant, projectA, "READ"));
        assertFalse(permissions.hasProjectPermission(member, tenant, projectB, "READ"));
    }

    private void assertExcluded(RoleRepository roles, PermissionService permissions, String assignment) {
        assertTrue(roles.findUserRoleAssignmentsByUserId(member).stream().noneMatch(a -> assignment.equals(a.id())));
        assertTrue(roles.findUserRoleAssignmentsByWorkspaceId(wsA).stream().noneMatch(a -> assignment.equals(a.id())));
        assertTrue(roles.findTenantRoleAssignments(member, tenant).stream().noneMatch(a -> assignment.equals(a.id())));
        assertTrue(roles.findProjectRoleAssignments(member, tenant, projectA).stream().noneMatch(a -> assignment.equals(a.id())));
        assertScopePermissions(permissions, "workspace", false);
    }

    private void verifyMaterializedConsumer(String schema, String assignment, String role,
            com.fasterxml.jackson.databind.node.ObjectNode preserved) throws Exception {
        // Same migrated-row consumer technique as the retained independent probe: public
        // already has valid canonical Account/member/resource mappings, but no role grant.
        jdbc.update("delete from user_role_assignment where user_id=? and workspace_id=?", member, wsA);
        jdbc.update("insert into user_role_assignment select * from " + schema + ".user_role_assignment where id=?", assignment);
        String other = readId(callScope(tenant, creator, "POST", "/api/identity/tenants/" + tenant + "/projects",
                Map.of("name", "Another Project", "workspaceId", wsA)), "id");
        var roles = context.getBean(RoleRepository.class);
        assertExcluded(roles, context.getBean(PermissionService.class), assignment);
        assertEquals(403, callScope(tenant, member, "GET", "/api/identity/projects/" + projectA, null).statusCode());
        assertEquals(403, callScope(tenant, member, "GET", "/api/identity/projects/" + other, null).statusCode());
        String roleKey = roles.findById(role).orElseThrow().roleKey();
        roles.deleteMemberAssignments(wsA, member);
        roles.deleteUserRoleAssignmentByWorkspace(member, roleKey, wsA);
        roles.deleteUserRoleAssignment(member, roleKey);
        assertEquals(preserved, row("public", assignment), "Ordinary removal preserves quarantined history");
        String path = "/api/workspaces/" + wsA + "/members/" + memberId(wsA, member) + "/roles";
        assertEquals(403, callScope(tenant, member, "POST", path, Map.of("roleKey", roleKey, "assignedBy", creator)).statusCode());
        assertEquals(200, callScope(tenant, creator, "POST", path, Map.of("roleKey", roleKey, "assignedBy", creator)).statusCode());
        assertEquals(1, count("select count(*) from user_role_assignment where user_id=? and role_id=? and not scope_unresolved and id<>?", member, role, assignment));
        assertEquals(preserved, row("public", assignment), "Canonical grant creates a separate assignment");
        assertEquals(200, callScope(tenant, member, "GET", "/api/identity/projects/" + projectA, null).statusCode());
    }
}
