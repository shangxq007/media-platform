package com.example.platform.identity.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.LinkedHashMap;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.example.platform.identity.api.dto.*;
import com.example.platform.shared.web.TenantContext;

/**
 * Multi-tenancy isolation tests.
 */
class MultiTenancyIsolationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;

    private IdentityAccessService identityAccessService;
    private TenantProjectService tenantProjectService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS tenant ("
                + "id varchar(64) primary key,"
                + "name varchar(255) not null,"
                + "status varchar(32) not null,"
                + "created_at timestamp not null"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS project ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64) not null,"
                + "name varchar(255) not null,"
                + "description text,"
                + "status varchar(32) not null,"
                + "created_at timestamp not null"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS \"user\" ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64) not null,"
                + "username varchar(128) not null,"
                + "email varchar(255) not null,"
                + "role varchar(32) not null,"
                + "status varchar(32) not null,"
                + "created_at timestamp not null"
                + ")");
        jdbc.execute("CREATE TABLE IF NOT EXISTS api_key ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64),"
                + "fingerprint varchar(32) not null,"
                + "hashed_key varchar(128) not null unique,"
                + "principal varchar(255) not null,"
                + "created_at timestamp not null,"
                + "last_used_at timestamp,"
                + "revoked_at timestamp"
                + ")");

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE api_key CASCADE");
        dsl.execute("TRUNCATE TABLE \"user\" CASCADE");
        dsl.execute("TRUNCATE TABLE project CASCADE");
        dsl.execute("TRUNCATE TABLE tenant CASCADE");
        TenantContext.clear();

        ApiKeyRepository apiKeyRepository = new ApiKeyRepository(dsl);
        ProjectRepository projectRepository = new ProjectRepository(dsl);
        TenantRepository tenantRepository = new TenantRepository(dsl);
        UserRepository userRepository = new UserRepository(dsl);

        IdentityProperties properties = new IdentityProperties();
        properties.setApiKeyAuthEnabled(false);
        properties.setApiKeys(new LinkedHashMap<>());
        identityAccessService = new IdentityAccessService(properties, apiKeyRepository);
        tenantProjectService = new TenantProjectService(tenantRepository, projectRepository,
                userRepository, identityAccessService);
    }

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

    @Test
    void tenantA_cannotAccessTenantB_project() {
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var projectA = tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project A", "desc"));

        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));
        var projectB = tenantProjectService.createProject(tenantB.id(),
                new CreateProjectRequest("Project B", "desc"));

        TenantContext.set(tenantA.id());

        assertThrows(IllegalArgumentException.class, () -> {
            tenantProjectService.getProject(projectB.id());
        });
    }

    @Test
    void tenantA_cannotListTenantB_projects() {
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project A", "desc"));

        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));
        tenantProjectService.createProject(tenantB.id(),
                new CreateProjectRequest("Project B", "desc"));

        TenantContext.set(tenantA.id());

        var projects = tenantProjectService.listProjects(tenantA.id());
        assertEquals(1, projects.size());
        assertEquals("Project A", projects.get(0).name());
    }

    @Test
    void sameTenant_canAccessOwnProject() {
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var project = tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("My Project", "desc"));

        TenantContext.set(tenantA.id());

        var retrieved = tenantProjectService.getProject(project.id());
        assertEquals("My Project", retrieved.name());
        assertEquals(tenantA.id(), retrieved.tenantId());
    }

    @Test
    void sameTenant_canListOwnProjects() {
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project 1", "desc"));
        tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project 2", "desc"));

        TenantContext.set(tenantA.id());

        var projects = tenantProjectService.listProjects(tenantA.id());
        assertEquals(2, projects.size());
    }

    @Test
    void noTenantContext_allowsAccessToAnyTenant() {
        TenantContext.clear();

        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var project = tenantProjectService.createProject(tenantA.id(),
                new CreateProjectRequest("Project A", "desc"));

        var retrieved = tenantProjectService.getProject(project.id());
        assertEquals("Project A", retrieved.name());
    }

    @Test
    void tenantA_cannotCreateUserForTenantB() {
        TenantContext.clear();
        var tenantA = tenantProjectService.createTenant(new CreateTenantRequest("Tenant A"));
        var tenantB = tenantProjectService.createTenant(new CreateTenantRequest("Tenant B"));

        TenantContext.set(tenantA.id());

        assertThrows(IllegalArgumentException.class, () -> {
            tenantProjectService.createUser(tenantB.id(),
                    new CreateUserRequest("hacker", "h@evil.com", "ADMIN"));
        });
    }
}
