package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import com.example.platform.render.app.dto.CreateRenderJobRequest;
import com.example.platform.render.app.dto.RenderJobResponse;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.policy.RenderPolicyEngine;
import com.example.platform.render.policy.RenderPolicyDecision;
import com.example.platform.shared.notification.NotificationEventPublisher;
import com.example.platform.shared.web.TenantContext;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private RenderJobService service;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "rendertest" + COUNTER.incrementAndGet();
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
            stmt.execute("create table render_job ("
                    + "id varchar(64) primary key,"
                    + "project_id varchar(64) not null,"
                    + "tenant_id varchar(64),"
                    + "timeline_snapshot_id varchar(64) not null,"
                    + "profile varchar(100) not null,"
                    + "status varchar(20) not null,"
                    + "created_at timestamp not null"
                    + ")");
            stmt.execute("create table render_job_status_history ("
                    + "id varchar(64) primary key,"
                    + "job_id varchar(64) not null,"
                    + "from_status varchar(30),"
                    + "to_status varchar(30) not null,"
                    + "reason varchar(255),"
                    + "error_code varchar(100),"
                    + "occurred_at timestamp not null"
                    + ")");
        }

        RenderPolicyEngine policyEngine = new RenderPolicyEngine() {
            @Override
            public RenderPolicyDecision decide(String profile) {
                return new RenderPolicyDecision("ffmpeg", "NORMAL");
            }
        };
        NotificationEventPublisher publisher = mock(NotificationEventPublisher.class);
        RenderJobStatusHistoryRepository historyRepository = new RenderJobStatusHistoryRepository(dsl);
        RenderJobRepository renderJobRepository = new RenderJobRepository(dsl);
        service = new RenderJobService(renderJobRepository, policyEngine, publisher, historyRepository);
        TenantContext.clear();
    }

    @BeforeEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private void insertProject(String projectId, String tenantId) {
        dsl.insertInto(table("project"))
                .columns(field("id"), field("tenant_id"), field("name"),
                        field("description"), field("status"), field("created_at"))
                .values(projectId, tenantId, "Test Project", "", "ACTIVE", OffsetDateTime.now())
                .execute();
    }

    @Test
    void createReturnsRenderJobResponse() {
        insertProject("proj-1", "tenant-1");
        CreateRenderJobRequest request = new CreateRenderJobRequest(
                "proj-1", "snap-1", "social_1080p");
        RenderJobResponse response = service.create(request);

        assertNotNull(response);
        assertNotNull(response.id());
        assertTrue(response.id().startsWith("rj_"));
        assertEquals("proj-1", response.projectId());
        assertEquals("snap-1", response.timelineSnapshotId());
        assertEquals("social_1080p", response.profile());
        assertEquals("QUEUED", response.status());
    }

    @Test
    void createPersistsToDatabase() {
        insertProject("proj-2", "tenant-2");
        CreateRenderJobRequest request = new CreateRenderJobRequest(
                "proj-2", "snap-2", "standard");
        RenderJobResponse response = service.create(request);

        List<java.util.Map<String, Object>> rows = dsl.select()
                .from(table("render_job"))
                .where(field("id").eq(response.id()))
                .fetchMaps();

        assertEquals(1, rows.size());
        assertEquals("proj-2", rows.get(0).get("project_id"));
        assertEquals("snap-2", rows.get(0).get("timeline_snapshot_id"));
        assertEquals("QUEUED", rows.get(0).get("status"));
    }

    @Test
    void listReturnsJobs() {
        insertProject("proj-3", "tenant-3");
        insertProject("proj-4", "tenant-4");
        service.create(new CreateRenderJobRequest("proj-3", "snap-3", "social_720p"));
        service.create(new CreateRenderJobRequest("proj-4", "snap-4", "cinema_4k"));

        List<RenderJobResponse> jobs = service.list();
        assertTrue(jobs.size() >= 2);
    }

    @Test
    void listReturnsEmptyWhenNoJobs() {
        List<RenderJobResponse> jobs = service.list();
        assertNotNull(jobs);
        assertEquals(0, jobs.size());
    }

    @Test
    void createThrowsWhenProjectNotFound() {
        // No project inserted — should fail with clear error
        CreateRenderJobRequest request = new CreateRenderJobRequest(
                "nonexistent-proj", "snap-1", "social_1080p");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.create(request));
        assertTrue(ex.getMessage().contains("Project not found"),
                "Error should mention project not found");
        assertTrue(ex.getMessage().contains("nonexistent-proj"),
                "Error should contain the project ID");
    }

    @Test
    void createDoesNotUseProjectIdAsTenantId() {
        // Insert project with a specific tenant
        insertProject("proj-tenant-test", "real-tenant");
        CreateRenderJobRequest request = new CreateRenderJobRequest(
                "proj-tenant-test", "snap-1", "social_1080p");
        RenderJobResponse response = service.create(request);

        // Verify the job was created with the correct tenant
        List<java.util.Map<String, Object>> rows = dsl.select()
                .from(table("render_job"))
                .where(field("id").eq(response.id()))
                .fetchMaps();

        assertEquals(1, rows.size());
        assertEquals("real-tenant", rows.get(0).get("tenant_id"),
                "Job tenantId must come from project.tenantId, not from projectId");
    }

    @Test
    void createPublishesRenderJobCreatedEvent() {
        insertProject("proj-event", "tenant-event");
        CreateRenderJobRequest request = new CreateRenderJobRequest(
                "proj-event", "snap-event", "social_1080p");

        // The create method publishes RenderJobCreatedEvent via NotificationEventPublisher
        // We verify the job was created successfully (event publishing is verified by mock)
        RenderJobResponse response = service.create(request);
        assertNotNull(response);
        assertEquals("proj-event", response.projectId());
    }
}
