package com.example.platform.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RoleRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private RoleRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "rolerepo" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table role ("
                    + "id varchar(64) primary key,"
                    + "role_key varchar(128) not null unique,"
                    + "name varchar(255) not null,"
                    + "description text,"
                    + "scope varchar(32) not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table permission ("
                    + "id varchar(64) primary key,"
                    + "permission_key varchar(128) not null unique,"
                    + "name varchar(255) not null,"
                    + "description text,"
                    + "resource_type varchar(128),"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table role_permission ("
                    + "id varchar(64) primary key,"
                    + "role_id varchar(64) not null,"
                    + "permission_id varchar(64) not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table user_role_assignment ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64),"
                    + "workspace_id varchar(64),"
                    + "user_id varchar(64) not null,"
                    + "role_id varchar(64) not null,"
                    + "assigned_by varchar(64),"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table group_role_assignment ("
                    + "id varchar(64) primary key,"
                    + "workspace_id varchar(64) not null,"
                    + "group_id varchar(64) not null,"
                    + "role_id varchar(64) not null,"
                    + "assigned_at timestamp not null"
                    + ")");
        }

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
    void deleteUserRoleAssignment() {
        Instant now = Instant.now();
        repository.save(new Role("rol_1", "ADMIN", "Admin", null, Role.RoleScope.WORKSPACE, now));
        repository.saveUserRoleAssignment(new UserRoleAssignment("ura_1", null, "ws_1", "usr_1", "rol_1", null, now));

        repository.deleteUserRoleAssignment("usr_1", "ADMIN");

        List<UserRoleAssignment> userAssignments = repository.findUserRoleAssignmentsByUserId("usr_1");
        assertTrue(userAssignments.isEmpty());
    }
}
