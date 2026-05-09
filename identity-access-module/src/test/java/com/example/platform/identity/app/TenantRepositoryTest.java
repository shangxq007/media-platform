package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.Tenant;
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

class TenantRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private TenantRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "tenanttest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table tenant ("
                    + "id varchar(64) primary key,"
                    + "name varchar(255) not null,"
                    + "status varchar(32) not null,"
                    + "created_at timestamp not null"
                    + ")");
        }

        repository = new TenantRepository(dsl);
    }

    @Test
    void saveAndFindById() {
        Tenant tenant = new Tenant("ten_abc123", "Acme Corp", Tenant.TenantStatus.ACTIVE, Instant.now());
        repository.save(tenant);

        Optional<Tenant> found = repository.findById("ten_abc123");
        assertTrue(found.isPresent());
        assertEquals("Acme Corp", found.get().name());
        assertEquals(Tenant.TenantStatus.ACTIVE, found.get().status());
    }

    @Test
    void findByIdReturnsEmptyForUnknown() {
        Optional<Tenant> found = repository.findById("ten_nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void findAllReturnsAllTenants() {
        repository.save(new Tenant("ten_1", "Tenant A", Tenant.TenantStatus.ACTIVE, Instant.now()));
        repository.save(new Tenant("ten_2", "Tenant B", Tenant.TenantStatus.SUSPENDED, Instant.now()));

        List<Tenant> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void findAllReturnsEmptyWhenNoTenants() {
        List<Tenant> all = repository.findAll();
        assertTrue(all.isEmpty());
    }
}
