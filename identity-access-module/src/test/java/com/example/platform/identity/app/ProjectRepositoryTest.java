package com.example.platform.identity.app;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.Project;
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

class ProjectRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private ProjectRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "projecttest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table project ("
                    + "id varchar(64) primary key,"
                    + "tenant_id varchar(64) not null,"
                    + "name varchar(255) not null,"
                    + "description text,"
                    + "status varchar(32) not null,"
                    + "created_at timestamp not null"
                    + ")");
        }

        repository = new ProjectRepository(dsl);
    }

    @Test
    void saveAndFindById() {
        Project project = new Project("prj_abc", "ten_1", "My Project", "A test project",
                Project.ProjectStatus.ACTIVE, Instant.now());
        repository.save(project);

        Optional<Project> found = repository.findById("prj_abc");
        assertTrue(found.isPresent());
        assertEquals("My Project", found.get().name());
        assertEquals("ten_1", found.get().tenantId());
        assertEquals(Project.ProjectStatus.ACTIVE, found.get().status());
    }

    @Test
    void findByIdReturnsEmptyForUnknown() {
        Optional<Project> found = repository.findById("prj_nonexistent");
        assertTrue(found.isEmpty());
    }

    @Test
    void findByTenantIdReturnsOnlyMatchingProjects() {
        repository.save(new Project("prj_1", "ten_1", "Project A", "", Project.ProjectStatus.ACTIVE, Instant.now()));
        repository.save(new Project("prj_2", "ten_1", "Project B", "", Project.ProjectStatus.ACTIVE, Instant.now()));
        repository.save(new Project("prj_3", "ten_2", "Project C", "", Project.ProjectStatus.ACTIVE, Instant.now()));

        List<Project> tenant1Projects = repository.findByTenantId("ten_1");
        assertEquals(2, tenant1Projects.size());

        List<Project> tenant2Projects = repository.findByTenantId("ten_2");
        assertEquals(1, tenant2Projects.size());
        assertEquals("prj_3", tenant2Projects.get(0).id());
    }

    @Test
    void findByTenantIdReturnsEmptyForUnknownTenant() {
        List<Project> projects = repository.findByTenantId("ten_nonexistent");
        assertTrue(projects.isEmpty());
    }
}
