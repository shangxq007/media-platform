package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.example.platform.render.domain.RenderJobStatus;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.OffsetDateTime;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

class StaleRenderJobCompensationServiceTest extends PostgresTestContainerSupport {

    private static javax.sql.DataSource dataSource;
    private static DSLContext dsl;
    private StaleRenderJobCompensationService service;

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
        service = new StaleRenderJobCompensationService(
                dsl, new RenderJobStatusHistoryRepository(dsl), mock(ApplicationEventPublisher.class));
    }

    @Test
    void startupLocalModeCompensatesRecentInFlightJobs() {
        insertJob("job1", RenderJobStatus.EXECUTING, OffsetDateTime.now());
        var result = service.compensate(
                StaleRenderJobCompensationService.CompensationRequest.startup(false, "local"));
        assertEquals(1, result.compensated());
        assertEquals("FAILED", dsl.select(field("status", String.class))
                .from(table("render_job"))
                .where(field("id").eq("job1"))
                .fetchOne()
                .value1());
    }

    private void insertJob(String id, RenderJobStatus status, OffsetDateTime createdAt) {
        dsl.insertInto(table("render_job"))
                .columns(field("id"), field("project_id"), field("tenant_id"),
                        field("timeline_snapshot_id"), field("profile"), field("status"), field("created_at"))
                .values(id, "p1", "t1", "snap", "prof", status.name(), createdAt)
                .execute();
    }
}
