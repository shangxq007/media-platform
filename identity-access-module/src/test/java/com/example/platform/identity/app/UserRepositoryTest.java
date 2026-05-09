package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.User;
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

class UserRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private UserRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "usertest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table \"user\" ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64) not null,"
                    + "username varchar(128) not null,"
                    + "email varchar(255) not null,"
                    + "role varchar(32) not null,"
                    + "status varchar(32) not null,"
                    + "created_at timestamp not null"
                    + ")");
        }

        repository = new UserRepository(dsl);
    }

    @Test
    void saveAndFindById() {
        User user = new User("usr_abc", "ten_1", "admin", "admin@example.com",
                User.UserRole.ADMIN, User.UserStatus.ACTIVE, Instant.now());
        repository.save(user);

        Optional<User> found = repository.findById("usr_abc");
        assertTrue(found.isPresent());
        assertEquals("admin", found.get().username());
        assertEquals("admin@example.com", found.get().email());
        assertEquals(User.UserRole.ADMIN, found.get().role());
    }

    @Test
    void findByIdReturnsEmptyForUnknown() {
        Optional<User> found = repository.findById("usr_nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByTenantIdReturnsOnlyMatchingUsers() {
        repository.save(new User("usr_1", "ten_1", "alice", "a@ex.com", User.UserRole.ADMIN, User.UserStatus.ACTIVE, Instant.now()));
        repository.save(new User("usr_2", "ten_1", "bob", "b@ex.com", User.UserRole.MEMBER, User.UserStatus.ACTIVE, Instant.now()));
        repository.save(new User("usr_3", "ten_2", "carol", "c@ex.com", User.UserRole.VIEWER, User.UserStatus.INACTIVE, Instant.now()));

        List<User> tenant1Users = repository.findByTenantId("ten_1");
        assertEquals(2, tenant1Users.size());

        List<User> tenant2Users = repository.findByTenantId("ten_2");
        assertEquals(1, tenant2Users.size());
        assertEquals("carol", tenant2Users.get(0).username());
    }

    @Test
    void findByTenantIdReturnsEmptyForUnknownTenant() {
        List<User> users = repository.findByTenantId("ten_nonexistent");
        assertTrue(users.isEmpty());
    }
}
