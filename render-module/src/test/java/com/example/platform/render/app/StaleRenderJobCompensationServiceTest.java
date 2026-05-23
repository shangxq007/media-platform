package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

import com.example.platform.render.domain.RenderJobStatus;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;

class StaleRenderJobCompensationServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private StaleRenderJobCompensationService service;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "stalesvc" + COUNTER.incrementAndGet();
        Connection conn = DriverManager.getConnection(
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
        service = new StaleRenderJobCompensationService(
                dsl, new RenderJobStatusHistoryRepository(dsl), mock(ApplicationEventPublisher.class));
    }

    @Test
    void startupLocalModeCompensatesRecentInFlightJobs() {
        insertJob("job1", RenderJobStatus.RENDERING, OffsetDateTime.now());
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
