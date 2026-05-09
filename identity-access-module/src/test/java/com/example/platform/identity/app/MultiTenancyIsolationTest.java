package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.web.TenantContext;

/**
 * Multi-tenancy isolation tests.
 * <p>
 * Verifies that tenant A cannot access tenant B's resources, and that
 * cross-tenant access returns 404 (via IllegalArgumentException) without
 * leaking resource existence.
 * </p>
 */
class MultiTenancyIsolationTest {

    private IdentityAccessService identityAccessService;
    private TenantProjectService tenantProjectService;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "mtisoltest" + System.nanoTime();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        DSLContext dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table tenant ("
                    + "id varchar(64) primary key,"
                    + "name varchar(255) not null,"
                    + "status varchar(32) not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table project ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64) not null,"
                    + "name varchar(255) not null,"
                    + "description text,"
                    + "status varchar(32) not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table \"user\" ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64) not null,"
                    + "username varchar(128) not null,"
                    + "email varchar(255) not null,"
                    + "role varchar(32) not null,"
                    + "status varchar(32) not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table api_key ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64),"
                    + "fingerprint varchar(32) not null,"
                    + "hashed_key varchar(128) not null unique,"
                    + "principal varchar(255) not null,"
                    + "created_at timestamp not null,"
                    + "last_used_at timestamp,"
                    + "revoked_at timestamp"
                    + ")");
        }

        ApiKeyRepository apiKeyRepository = new ApiKeyRepository(dsl);
        ProjectRepository projectRepository = new ProjectRepository(dsl);
        TenantRepository tenantRepository = new TenantRepository(dsl);
        UserRepository userRepository = new UserRepository(dsl);

        // No bootstrap keys — we test with explicit TenantContext
        IdentityProperties properties = new IdentityProperties();
        properties.setApiKeyAuthEnabled(false);
        properties.setApiKeys(new LinkedHashMap<>());
        identityAccessService = new IdentityAccessService(properties, apiKeyRepository);
        tenantProjectService = new TenantProjectService(tenantRepository, projectRepository,
                userRepository, identityAccessService);
    }

    @BeforeEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    // ===== TenantContext tests =====

    @Test
    void tenantContext_storesAndRetrievesTenantId() {
        TenantContext.set("tenant-1");
        assertEquals("tenant-1", TenantContext.get());
    }

    @Test
    void tenantContext_clearRemovesTenantId() {
        TenantContext.set("tenant-1");
        TenantContext.clear();
        assertNull(TenantContext.get());
    }

    @Test
    void tenantContext_getReturnsNullWhenNotSet() {
        assertNull(TenantContext.get());
    }

    // ===== API Key tenantId binding =====

    @Test
    void apiKey_storesAndResolvesTenantId() {
        // Create a tenant
        TenantContext.clear();
        var tenant = tenantProjectService.createTenant(new CreateTenantRequest("Tenant X"));

        // Create an API key for this tenant (directly via identityAccessService)
        ApiKeyRecord record = new ApiKeyRecord("ak_test", tenant.id(), "fp123456",
                "hash123", "my-service", Instant.now(), null, null);
        identityAccessService.storeRecord(record);

        // Verify the stored record has the correct tenantId
        ApiKeyRecord found = identityAccessService.findRecordByFingerprint("fp123456");
        assertNotNull(found);
        assertEquals(tenant.id(), found.tenantId());
        assertEquals("my-service", found.principal());
    }

    // ===== Cross-tenant project access =====

    @Test
    void tenantA_cannotAccessTenantB_project() {
        // Create tenant A and its project (no TenantContext = admin mode)
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var projectA = tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project A", "desc"));

        // Create tenant B and its project
        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));
        var projectB = tenantProjectService.createProject(tenantB.id(),
                new CreateProjectRequest("Project B", "desc"));

        // Set TenantContext to tenant A
        TenantContext.set(tenantA.id());

        // Tenant A tries to access Tenant B's project → should throw (404)
        assertThrows(IllegalArgumentException.class, () -> {
            tenantProjectService.getProject(projectB.id());
        });
    }

    @Test
    void tenantA_cannotListTenantB_projects() {
        // Create tenants and projects (no TenantContext = admin mode)
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project A", "desc"));

        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));
        tenantProjectService.createProject(tenantB.id(),
                new CreateProjectRequest("Project B", "desc"));

        // Set TenantContext to tenant A
        TenantContext.set(tenantA.id());

        // Tenant A lists projects → should only see tenant A's project
        var projects = tenantProjectService.listProjects(tenantA.id());
        assertEquals(1, projects.size());
        assertEquals("Project A", projects.get(0).name());
    }

    @Test
    void tenantA_cannotCreateProjectForTenantB() {
        // Create tenant B (no TenantContext = admin mode)
        TenantContext.clear();
        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));

        // Set TenantContext to a different tenant
        TenantContext.set("some-other-tenant");

        // Tenant A tries to create a project under tenant B → should throw (404)
        assertThrows(IllegalArgumentException.class, () -> {
            tenantProjectService.createProject(tenantB.id(),
                    new CreateProjectRequest("Hacked Project", "desc"));
        });
    }

    // ===== Same-tenant access works =====

    @Test
    void sameTenant_canAccessOwnProject() {
        // Create tenant and project (no TenantContext = admin mode)
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var project = tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("My Project", "desc"));

        // Set TenantContext to the tenant
        TenantContext.set(tenantA.id());

        var retrieved = tenantProjectService.getProject(project.id());
        assertEquals("My Project", retrieved.name());
        assertEquals(tenantA.id(), retrieved.tenantId());
    }

    @Test
    void sameTenant_canListOwnProjects() {
        // Create tenant and projects (no TenantContext = admin mode)
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project 1", "desc"));
        tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project 2", "desc"));

        // Set TenantContext to the tenant
        TenantContext.set(tenantA.id());

        var projects = tenantProjectService.listProjects(tenantA.id());
        assertEquals(2, projects.size());
    }

    // ===== API Key tenant-scoped listing =====

    @Test
    void listApiKeys_onlyReturnsOwnTenantKeys() {
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));

        // Create API keys for each tenant
        identityAccessService.storeRecord(new ApiKeyRecord("ak_a", tenantA.id(), "fp_a",
                "hash_a", "svc-a", Instant.now(), null, null));
        identityAccessService.storeRecord(new ApiKeyRecord("ak_b", tenantB.id(), "fp_b",
                "hash_b", "svc-b", Instant.now(), null, null));

        // Tenant A lists API keys → only sees tenant A's key
        TenantContext.set(tenantA.id());
        var keys = tenantProjectService.listApiKeys(tenantA.id());
        assertEquals(1, keys.size());
        assertEquals("svc-a", keys.get(0).principal());
    }

    // ===== Revoked key tests =====

    @Test
    void apiKeyRecord_isRevoked() {
        ApiKeyRecord record = new ApiKeyRecord("ak_1", "tenant-1", "abc12345",
                "hash", "principal-x", Instant.now(), null, null);
        assertFalse(record.isRevoked());

        ApiKeyRecord revoked = record.withRevokedAt(Instant.now());
        assertTrue(revoked.isRevoked());
    }

    @Test
    void apiKeyRecord_withLastUsedAt() {
        ApiKeyRecord record = new ApiKeyRecord("ak_1", "tenant-1", "abc12345",
                "hash", "principal-x", Instant.now(), null, null);
        assertNull(record.lastUsedAt());

        Instant now = Instant.now();
        ApiKeyRecord updated = record.withLastUsedAt(now);
        assertEquals(now, updated.lastUsedAt());
    }

    // ===== No TenantContext (auth disabled) allows all access =====

    @Test
    void noTenantContext_allowsAccessToAnyTenant() {
        // Don't set TenantContext — simulates auth-disabled mode
        TenantContext.clear();

        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var project = tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project A", "desc"));

        // Without TenantContext, any access is allowed
        var retrieved = tenantProjectService.getProject(project.id());
        assertEquals("Project A", retrieved.name());
    }

    // ===== Cross-tenant user access =====

    @Test
    void tenantA_cannotCreateUserForTenantB() {
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));

        // Set TenantContext to tenant A
        TenantContext.set(tenantA.id());

        // Tenant A tries to create user under tenant B → should throw
        assertThrows(IllegalArgumentException.class, () -> {
            tenantProjectService.createUser(tenantB.id(),
                    new CreateUserRequest("hacker", "h@evil.com", "ADMIN"));
        });
    }

    @Test
    void tenantA_cannotListTenantB_users() {
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));
        tenantProjectService.createUser(tenantB.id(),
                new CreateUserRequest("user-b", "b@tenant.com", "MEMBER"));

        // Set TenantContext to tenant A
        TenantContext.set(tenantA.id());

        // Tenant A tries to list tenant B's users → should throw
        assertThrows(IllegalArgumentException.class, () -> {
            tenantProjectService.listUsers(tenantB.id());
        });
    }
}
