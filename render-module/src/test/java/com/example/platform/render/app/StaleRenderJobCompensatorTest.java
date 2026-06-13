package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Duration;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class StaleRenderJobCompensatorTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private RenderJobStatusHistoryRepository historyRepository;
    private ApplicationEventPublisher eventPublisher;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
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
    void compensatesStaleSelectingProviderJob() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(1));
        insertJob("stale_sp", "proj_1", RenderJobStatus.SELECTING_PROVIDER, old);

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("stale_sp")).fetchMaps();
        assertEquals(1, rows.size());
        assertEquals("FAILED", rows.get(0).get("status"));

        var history = historyRepository.findByJobId("stale_sp");
        assertEquals(1, history.size());
        assertEquals("SELECTING_PROVIDER", history.get(0).fromStatus());
        assertEquals("FAILED", history.get(0).toStatus());
        assertEquals("stale_timeout", history.get(0).reason());
        assertEquals("STALE_TIMEOUT", history.get(0).errorCode());
    }

    @Test
    void compensatesStaleExecutingJob() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(2));
        insertJob("stale_exec", "proj_1", RenderJobStatus.EXECUTING, old);

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("stale_exec")).fetchMaps();
        assertEquals("FAILED", rows.get(0).get("status"));
    }

    @Test
    void doesNotCompensateRecentJobs() {
        OffsetDateTime recent = OffsetDateTime.now().minus(Duration.ofMinutes(5));
        insertJob("recent_job", "proj_1", RenderJobStatus.SELECTING_PROVIDER, recent);

        StaleRenderJobCompensator compensator = createCompensator(true, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("recent_job")).fetchMaps();
        assertEquals("SELECTING_PROVIDER", rows.get(0).get("status"));
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
        insertJob("stale_disabled", "proj_1", RenderJobStatus.SELECTING_PROVIDER, old);

        StaleRenderJobCompensator compensator = createCompensator(false, Duration.ofMinutes(30));
        compensator.compensateStaleJobs();

        var rows = dsl.select().from(table("render_job"))
                .where(field("id").eq("stale_disabled")).fetchMaps();
        assertEquals("SELECTING_PROVIDER", rows.get(0).get("status"));
        verify(eventPublisher, never()).publishEvent(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void compensatesMultipleStaleJobs() {
        OffsetDateTime old = OffsetDateTime.now().minus(Duration.ofHours(1));
        insertJob("stale_1", "proj_1", RenderJobStatus.SELECTING_PROVIDER, old);
        insertJob("stale_2", "proj_1", RenderJobStatus.EXECUTING, old);
        insertJob("fresh_1", "proj_1", RenderJobStatus.SELECTING_PROVIDER, OffsetDateTime.now());

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
        assertEquals("SELECTING_PROVIDER", fresh.get(0).get("status"));
    }
}
