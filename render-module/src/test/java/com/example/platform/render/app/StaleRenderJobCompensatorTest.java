package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.platform.render.domain.RenderJobStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class StaleRenderJobCompensatorTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private RenderJobStatusHistoryRepository historyRepository;
    private ApplicationEventPublisher eventPublisher;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "staletest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table render_job ("
                    + "id varchar(64) primary key,"
                    + "project_id varchar(64) not null,"
                    + "tenant_id varchar(64),"
                    + "timeline_snapshot_id varchar(64) not null,"
                    + "profile varchar(100) not null,"
                    + "status varchar(30) not null,"
                    + "ai_script text,"
                    + "artifact_uri text,"
                    + "error_message text,"
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

        historyRepository = new RenderJobStatusHistoryRepository(dsl);
        eventPublisher = mock(ApplicationEventPublisher.class);
    }

    private StaleRenderJobCompensator createCompensator(boolean enabled, Duration threshold) {
        var service = new StaleRenderJobCompensationService(dsl, historyRepository, eventPublisher);
        return new StaleRenderJobCompensator(service, enabled, threshold);
    }

    private void insertJob(String jobId, String projectId, RenderJobStatus status, OffsetDateTime createdAt) {
        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"), field("profile"),
                        field("status"), field("created_at"))
                .values(jobId, projectId, "tenant_1",
                        "snap_" + jobId, "default_1080p",
                        status.name(), createdAt)
                .execute();
    }

    @Test
    void compensatesStaleAiProcessingJob() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(1));
        insertJob("stale_ai", "proj_1", RenderJobStatus.AI_PROCESSING, old);

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("stale_ai")).fetchMaps();
        assertEquals(1, rows.size());
        assertEquals("FAILED", rows.get(0).get("status"));

        var history = historyRepository.findByJobId("stale_ai");
        assertEquals(1, history.size());
        assertEquals("AI_PROCESSING", history.get(0).fromStatus());
        assertEquals("FAILED", history.get(0).toStatus());
        assertEquals("stale_timeout", history.get(0).reason());
        assertEquals("STALE_TIMEOUT", history.get(0).errorCode());
    }

    @Test
    void compensatesStaleRenderingJob() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(2));
        insertJob("stale_render", "proj_1", RenderJobStatus.RENDERING, old);

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("stale_render")).fetchMaps();
        assertEquals("FAILED", rows.get(0).get("status"));
    }

    @Test
    void doesNotCompensateRecentJobs() {
        OffsetDateTime recent = OffsetDateTime.now().minus(Duration.ofMinutes(5));
        insertJob("recent_job", "proj_1", RenderJobStatus.AI_PROCESSING, recent);

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("recent_job")).fetchMaps();
        assertEquals("AI_PROCESSING", rows.get(0).get("status"));
    }

    @Test
    void doesNotCompensateCompletedJobs() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(1));
        insertJob("completed_job", "proj_1", RenderJobStatus.COMPLETED, old);

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("completed_job")).fetchMaps();
        assertEquals("COMPLETED", rows.get(0).get("status"));
    }

    @Test
    void doesNotCompensateFailedJobs() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(1));
        insertJob("failed_job", "proj_1", RenderJobStatus.FAILED, old);

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("failed_job")).fetchMaps();
        assertEquals("FAILED", rows.get(0).get("status"));
    }

    @Test
    void disabledCompensatorDoesNothing() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(1));
        insertJob("stale_disabled", "proj_1", RenderJobStatus.AI_PROCESSING, old);

        StaleRenderJobCompensator compensator = createCompensator(false, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("stale_disabled")).fetchMaps();
        assertEquals("AI_PROCESSING", rows.get(0).get("status"));
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void compensatesMultipleStaleJobs() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(1));
        insertJob("stale_1", "proj_1", RenderJobStatus.AI_PROCESSING, old);
        insertJob("stale_2", "proj_1", RenderJobStatus.RENDERING, old);
        insertJob("fresh_1", "proj_1", RenderJobStatus.AI_PROCESSING, OffsetDateTime.now());

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var stale1 = dsl.select().from(table("render_job"))
                .where(field("id").eq("stale_1")).fetchMaps();
        var stale2 = dsl.select().from(table("render_job"))
                .where(field("id").eq("stale_2")).fetchMaps();
        var fresh = dsl.select().from(table("render_job"))
                .where(field("id").eq("fresh_1")).fetchMaps();

        assertEquals("FAILED", stale1.get(0).get("status"));
        assertEquals("FAILED", stale2.get(0).get("status"));
        assertEquals("AI_PROCESSING", fresh.get(0).get("status"));
    }
}
