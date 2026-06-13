package com.example.platform.identity.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.Tenant;
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

class TenantRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TenantRepository repository;

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

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE tenant CASCADE");
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
