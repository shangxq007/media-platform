package com.example.platform.identity.infrastructure;

import com.example.platform.shared.test.PostgresTestContainerSupport;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.identity.domain.WorkspaceMember;
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

class WorkspaceMemberRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private WorkspaceMemberRepository repository;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        var jdbc = new JdbcTemplate(dataSource);

        jdbc.execute("CREATE TABLE IF NOT EXISTS workspace_member ("
                + "id varchar(64) primary key,"
                + "workspace_id varchar(64) not null,"
                + "user_id varchar(64) not null,"
                + "role varchar(64) not null,"
                + "status varchar(32) not null default 'ACTIVE',"
                + "joined_at timestamp not null,"
                + "updated_at timestamp not null"
                + ")");

        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE workspace_member CASCADE");
        repository = new WorkspaceMemberRepository(dsl);
    }

    @Test
    void saveAndFindById() {
        Instant now = Instant.now();
        WorkspaceMember member = new WorkspaceMember("wsm_1", "ws_1", "usr_1",
                "EDITOR", WorkspaceMember.MemberStatus.ACTIVE, now, now);
        repository.save(member);

        Optional<WorkspaceMember> found = repository.findById("wsm_1");
        assertTrue(found.isPresent());
        assertEquals("ws_1", found.get().workspaceId());
        assertEquals("usr_1", found.get().userId());
        assertEquals("EDITOR", found.get().role());
    }

    @Test
    void findByWorkspaceIdReturnsMembers() {
        Instant now = Instant.now();
        repository.save(new WorkspaceMember("wsm_1", "ws_1", "usr_1", "EDITOR", WorkspaceMember.MemberStatus.ACTIVE, now, now));
        repository.save(new WorkspaceMember("wsm_2", "ws_1", "usr_2", "VIEWER", WorkspaceMember.MemberStatus.ACTIVE, now, now));
        repository.save(new WorkspaceMember("wsm_3", "ws_2", "usr_3", "ADMIN", WorkspaceMember.MemberStatus.ACTIVE, now, now));

        List<WorkspaceMember> members = repository.findByWorkspaceId("ws_1");
        assertEquals(2, members.size());
    }

    @Test
    void findByWorkspaceIdAndUserIdReturnsMatch() {
        Instant now = Instant.now();
        repository.save(new WorkspaceMember("wsm_1", "ws_1", "usr_1", "EDITOR", WorkspaceMember.MemberStatus.ACTIVE, now, now));

        Optional<WorkspaceMember> found = repository.findByWorkspaceIdAndUserId("ws_1", "usr_1");
        assertTrue(found.isPresent());
        assertEquals("EDITOR", found.get().role());

        Optional<WorkspaceMember> notFound = repository.findByWorkspaceIdAndUserId("ws_1", "usr_999");
        assertTrue(notFound.isEmpty());
    }

    @Test
    void updateRoleChangesRole() {
        Instant now = Instant.now();
        repository.save(new WorkspaceMember("wsm_1", "ws_1", "usr_1", "EDITOR", WorkspaceMember.MemberStatus.ACTIVE, now, now));

        repository.updateRole("wsm_1", "ADMIN", java.time.OffsetDateTime.now());

        Optional<WorkspaceMember> found = repository.findById("wsm_1");
        assertTrue(found.isPresent());
        assertEquals("ADMIN", found.get().role());
    }

    @Test
    void updateStatusChangesStatus() {
        Instant now = Instant.now();
        repository.save(new WorkspaceMember("wsm_1", "ws_1", "usr_1", "EDITOR", WorkspaceMember.MemberStatus.ACTIVE, now, now));

        repository.updateStatus("wsm_1", WorkspaceMember.MemberStatus.REMOVED, java.time.OffsetDateTime.now());

        Optional<WorkspaceMember> found = repository.findById("wsm_1");
        assertTrue(found.isPresent());
        assertEquals(WorkspaceMember.MemberStatus.REMOVED, found.get().status());
    }
}
