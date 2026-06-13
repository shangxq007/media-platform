package com.example.platform.identity.app;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.Project;
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

class ProjectRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private ProjectRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS project ("
                + "id varchar(64) primary key,"
                + "tenant_id varchar(64) not null,"
                + "name varchar(255) not null,"
                + "description text,"
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
        dsl.execute("TRUNCATE TABLE project CASCADE");
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
