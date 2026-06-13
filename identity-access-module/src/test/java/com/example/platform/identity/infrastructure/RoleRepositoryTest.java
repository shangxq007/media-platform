package com.example.platform.identity.infrastructure;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.*;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

class RoleRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private RoleRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS role ("
                + "id varchar(64) primary key,"
                + "role_key varchar(128) not null unique,"
                + "name varchar(255) not null,"
                + "description text,"
                + "scope varchar(32) not null,"
                + "created_at timestamp not null"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS permission ("
                + "id varchar(64) primary key,"
                + "permission_key varchar(128) not null unique,"
                + "name varchar(255) not null,"
                + "description text,"
                + "resource_type varchar(128),"
                + "created_at timestamp not null"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS role_permission ("
                + "id varchar(64) primary key,"
                + "role_id varchar(64) not null,"
                + "permission_id varchar(64) not null,"
                + "created_at timestamp not null"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS user_role_assignment ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64),"
                + "workspace_id varchar(64),"
                + "user_id varchar(64) not null,"
                + "role_id varchar(64) not null,"
                + "assigned_by varchar(64),"
                + "created_at timestamp not null"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS group_role_assignment ("
                + "id varchar(64) primary key,"
                + "workspace_id varchar(64) not null,"
                + "group_id varchar(64) not null,"
                + "role_id varchar(64) not null,"
                + "assigned_at timestamp not null"
                + ")");

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE group_role_assignment CASCADE");
        dsl.execute("TRUNCATE TABLE user_role_assignment CASCADE");
        dsl.execute("TRUNCATE TABLE role_permission CASCADE");
        dsl.execute("TRUNCATE TABLE permission CASCADE");
        dsl.execute("TRUNCATE TABLE role CASCADE");
        repository = new RoleRepository(dsl);
    }

    @Test
    void saveAndFindRoleByKey() {
        Instant now = Instant.now();
        Role role = new Role("rol_1", "ADMIN", "Admin", "Admin role", Role.RoleScope.WORKSPACE, now);
        repository.save(role);

        Optional<Role> found = repository.findByKey("ADMIN");
        assertTrue(found.isPresent());
        assertEquals("Admin", found.get().name());
        assertEquals(Role.RoleScope.WORKSPACE, found.get().scope());
    }

    @Test
    void findByKeyReturnsEmptyForUnknown() {
        Optional<Role> found = repository.findByKey("NONEXISTENT");
        assertTrue(found.isEmpty());
    }

    @Test
    void findAllReturnsAllRoles() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "OWNER", "Owner", null, Role.RoleScope.WORKSPACE, now));
        repository.save(new Role("rol_2", "VIEWER", "Viewer", null, Role.RoleScope.WORKSPACE, now));

        List<Role> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void findByScopeFiltersCorrectly() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "OWNER", "Owner", null, Role.RoleScope.WORKSPACE, now));
        repository.save(new Role("rol_2", "GLOBAL_ADMIN", "Global Admin", null, Role.RoleScope.GLOBAL, now));

        List<Role> workspaceRoles = repository.findByScope(Role.RoleScope.WORKSPACE);
        assertEquals(1, workspaceRoles.size());
        assertEquals("OWNER", workspaceRoles.get(0).roleKey());
    }

    @Test
    void saveAndFindPermission() {
        Instant now = Instant.now();
        Permission perm = new Permission("perm_1", "render.submit", "Submit Render", "Submit render job", "RENDER", now);
        repository.savePermission(perm);

        Optional<Permission> found = repository.findPermissionByKey("render.submit");
        assertTrue(found.isPresent());
        assertEquals("Submit Render", found.get().name());
        assertEquals("RENDER", found.get().resourceType());
    }

    @Test
    void saveRolePermissionAndFindByRoleId() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "EDITOR", "Editor", null, Role.RoleScope.WORKSPACE, now));
        repository.savePermission(new Permission("perm_1", "render.submit", "Submit", null, "RENDER", now));
        repository.savePermission(new Permission("perm_2", "render.cancel", "Cancel", null, "RENDER", now));
        repository.saveRolePermission(new RolePermission("rp_1", "rol_1", "perm_1", now));
        repository.saveRolePermission(new RolePermission("rp_2", "rol_1", "perm_2", now));

        List<Permission> perms = repository.findPermissionsByRoleId("rol_1");
        assertEquals(2, perms.size());
    }

    @Test
    void saveUserRoleAssignment() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, now));
        UserRoleAssignment assignment = new UserRoleAssignment("ura_1", "ten_1", "ws_1", "usr_1", "rol_1", "usr_admin", now);
        repository.saveUserRoleAssignment(assignment);

        List<UserRoleAssignment> userAssignments = repository.findUserRoleAssignmentsByUserId("usr_1");
        assertEquals(1, userAssignments.size());
        assertEquals("ws_1", userAssignments.get(0).workspaceId());
    }

    @Test
    void deleteUserRoleAssignment_byWorkspaceScope_onlyDeletesInTargetWorkspace() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_2", null, "ws_2", "usr_1", "rol_1", null, now));

        repository.deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1");

        List<UserRoleAssignment> ws1Assignments = repository.findUserRoleAssignmentsByWorkspaceId("ws_1");
        assertTrue(ws1Assignments.isEmpty(), "ws_1 ADMIN should be deleted");

        List<UserRoleAssignment> ws2Assignments = repository.findUserRoleAssignmentsByWorkspaceId("ws_2");
        assertEquals(1, ws2Assignments.size(), "ws_2 ADMIN should still exist");
        assertEquals("usr_1", ws2Assignments.get(0).userId());
    }

    @Test
    void deleteUserRoleAssignment_byWorkspaceScope_doesNotAffectOtherUsers() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_2", null, "ws_1", "usr_2", "rol_1", null, now));

        repository.deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1");

        List<UserRoleAssignment> usr1Assignments = repository.findUserRoleAssignmentsByUserId("usr_1");
        assertTrue(usr1Assignments.isEmpty(), "usr_1 should have no assignments");

        List<UserRoleAssignment> usr2Assignments = repository.findUserRoleAssignmentsByUserId("usr_2");
        assertEquals(1, usr2Assignments.size(), "usr_2 should still have ADMIN");
    }

    @Test
    void deleteUserRoleAssignment_byWorkspaceScope_idempotent() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, now));

        repository.deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1");
        assertDoesNotThrow(() -> repository.deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1"));

        List<UserRoleAssignment> assignments = repository.findUserRoleAssignmentsByUserId("usr_1");
        assertTrue(assignments.isEmpty());
    }

    @Test
    void deleteUserRoleAssignment_byWorkspaceScope_doesNotAffectOtherRoles() {
        Instant now = Instant.now();
        repository.save(new Role("rol_admin", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, now));
        repository.save(new Role("rol_editor", "EDITOR", "Editor", null, Role.RoleScope.WORKSPACE, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_admin", null, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_2", null, "ws_1", "usr_1", "rol_editor", null, now));

        repository.deleteUserRoleAssignmentByWorkspace("usr_1", "ADMIN", "ws_1");

        List<UserRoleAssignment> remaining = repository.findUserRoleAssignmentsByUserId("usr_1");
        assertEquals(1, remaining.size(), "EDITOR role should remain");
        assertEquals("rol_editor", remaining.get(0).roleId());
    }

    @Test
    void deleteUserRoleAssignment_deprecatedGlobalMethod_stillWorks() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_2", null, "ws_2", "usr_1", "rol_1", null, now));

        repository.deleteUserRoleAssignment("usr_1", "ADMIN");

        List<UserRoleAssignment> assignments = repository.findUserRoleAssignmentsByUserId("usr_1");
        assertTrue(assignments.isEmpty(), "Global delete should remove all workspaces");
    }
}
