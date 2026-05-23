package com.example.platform.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.identity.app.TenantRepository;
import com.example.platform.identity.app.UserRepository;
import com.example.platform.identity.domain.Tenant;
import com.example.platform.identity.domain.User;
import com.example.platform.identity.infrastructure.RoleRepository;
import java.sql.Statement;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

class OidcIdentityProvisioningServiceTest {

    private OidcIdentityProvisioningService service;
    private UserRepository userRepository;
    private TenantRepository tenantRepository;

    @BeforeEach
    void setUp() throws Exception {
        var conn = java.sql.DriverManager.getConnection(
                "jdbc:h2:mem:oidcjit" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
                "sa",
                "");
        DSLContext dsl = DSL.using(conn, org.jooq.SQLDialect.H2);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("""
                    create table tenant (
                      id varchar(64) primary key,
                      name varchar(255) not null,
                      status varchar(32) not null,
                      created_at timestamp not null
                    )
                    """);
            stmt.execute("""
                    create table "user" (
                      id varchar(64) primary key,
                      tenant_id varchar(64) not null,
                      username varchar(128) not null,
                      email varchar(255) not null,
                      role varchar(32) not null,
                      status varchar(32) not null,
                      created_at timestamp not null
                    )
                    """);
            stmt.execute("""
                    create table role (
                      id varchar(64) primary key,
                      role_key varchar(64) not null,
                      name varchar(255) not null,
                      description varchar(512),
                      scope varchar(32) not null,
                      created_at timestamp not null
                    )
                    """);
            stmt.execute("""
                    create table user_role_assignment (
                      id varchar(64) primary key,
                      tenant_id varchar(64),
                      workspace_id varchar(64) not null,
                      user_id varchar(64) not null,
                      role_id varchar(64) not null,
                      assigned_by varchar(64),
                      created_at timestamp not null
                    )
                    """);
        }

        tenantRepository = new TenantRepository(dsl);
        userRepository = new UserRepository(dsl);
        RoleRepository roleRepository = new RoleRepository(dsl);
        new com.example.platform.identity.app.BuiltinDataInitializer(roleRepository).init();

        OAuth2SecurityProperties props = new OAuth2SecurityProperties(
                true, "https://auth.example/", null, "tenantId", "roles", "platform_user_id",
                false, true, true, "tenant-1");
        service = new OidcIdentityProvisioningService(props, tenantRepository, userRepository, roleRepository);

        tenantRepository.save(new Tenant("tenant-1", "T1", Tenant.TenantStatus.ACTIVE, Instant.now()));
    }

    @Test
    void createsUserAndAssignmentsOnFirstLogin() {
        Jwt jwt = Jwt.withTokenValue("t")
                .header("alg", "none")
                .subject("oidc-user-99")
                .claim("tenantId", "tenant-1")
                .claim("email", "oidc@example.com")
                .claim("roles", List.of("ADMIN"))
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plus(1, ChronoUnit.HOURS))
                .build();

        service.provisionFromJwt(jwt);

        User user = userRepository.findById("oidc-user-99").orElseThrow();
        assertEquals("tenant-1", user.tenantId());
        assertEquals(User.UserRole.ADMIN, user.role());
        assertTrue(userRepository.findByTenantIdAndEmail("tenant-1", "oidc@example.com").isPresent());
    }
}
