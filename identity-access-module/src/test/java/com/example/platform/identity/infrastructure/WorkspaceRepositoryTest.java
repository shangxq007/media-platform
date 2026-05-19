package com.example.platform.identity.infrastructure;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.Workspace;
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

class WorkspaceRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private WorkspaceRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "wsrepo" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table workspace ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64) not null,"
                    + "name varchar(255) not null,"
                    + "description text,"
                    + "plan_tier varchar(64) not null default 'FREE',"
                    + "status varchar(32) not null default 'ACTIVE',"
                    + "created_at timestamp not null,"
                    + "updated_at timestamp not null"
                    + ")");
        }

        repository = new WorkspaceRepository(dsl);
    }

    @Test
    void saveAndFindById() {
        Instant now = Instant.now();
        Workspace ws = new Workspace("ws_abc", "ten_1", "Acme Workspace",
                "Test workspace", "PRO", Workspace.WorkspaceStatus.ACTIVE, now, now);
        repository.save(ws);

        Optional<Workspace> found = repository.findById("ws_abc");
        assertTrue(found.isPresent());
        assertEquals("Acme Workspace", found.get().name());
        assertEquals("ten_1", found.get().tenantId());
        assertEquals("PRO", found.get().planTier());
        assertEquals(Workspace.WorkspaceStatus.ACTIVE, found.get().status());
    }

    @Test
    void findByIdReturnsEmptyForUnknown() {
        Optional<Workspace> found = repository.findById("ws_nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByTenantIdReturnsOnlyMatchingWorkspaces() {
        Instant now = Instant.now();
        repository.save(new Workspace("ws_1", "ten_1", "WS A", null, "FREE", Workspace.WorkspaceStatus.ACTIVE, now, now));
        repository.save(new Workspace("ws_2", "ten_1", "WS B", null, "PRO", Workspace.WorkspaceStatus.ACTIVE, now, now));
        repository.save(new Workspace("ws_3", "ten_2", "WS C", null, "FREE", Workspace.WorkspaceStatus.SUSPENDED, now, now));

        List<Workspace> tenant1 = repository.findByTenantId("ten_1");
        assertEquals(2, tenant1.size());

        List<Workspace> tenant2 = repository.findByTenantId("ten_2");
        assertEquals(1, tenant2.size());
        assertEquals("WS C", tenant2.get(0).name());
    }

    @Test
    void findAllReturnsAllWorkspaces() {
        Instant now = Instant.now();
        repository.save(new Workspace("ws_1", "ten_1", "WS A", null, "FREE", Workspace.WorkspaceStatus.ACTIVE, now, now));
        repository.save(new Workspace("ws_2", "ten_2", "WS B", null, "PRO", Workspace.WorkspaceStatus.ACTIVE, now, now));

        List<Workspace> all = repository.findAll();
        assertEquals(2, all.size());
    }

    @Test
    void updateStatusChangesStatus() {
        Instant now = Instant.now();
        Workspace ws = new Workspace("ws_abc", "ten_1", "Acme", null, "FREE",
                Workspace.WorkspaceStatus.ACTIVE, now, now);
        repository.save(ws);

        repository.updateStatus("ws_abc", Workspace.WorkspaceStatus.SUSPENDED, java.time.OffsetDateTime.now());

        Optional<Workspace> found = repository.findById("ws_abc");
        assertTrue(found.isPresent());
        assertEquals(Workspace.WorkspaceStatus.SUSPENDED, found.get().status());
    }
}
