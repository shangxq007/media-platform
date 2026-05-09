package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.app.dto.StatusHistoryResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobStatusHistoryRepositoryTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private DSLContext dsl;
    private RenderJobStatusHistoryRepository repository;
    private Connection conn;

    @BeforeEach
    void setUp() throws Exception {
        String dbName = "historytest" + COUNTER.incrementAndGet();
        conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + dbName + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);

        try (Statement stmt = conn.createStatement()) {
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

        repository = new RenderJobStatusHistoryRepository(dsl);
    }

    @Test
    void recordInsertsHistoryEntry() {
        repository.record("job_1", null, "QUEUED", "Job created", null);

        List<StatusHistoryResponse> history = repository.findByJobId("job_1");
        assertEquals(1, history.size());
        assertEquals("job_1", history.get(0).jobId());
        assertNull(history.get(0).fromStatus());
        assertEquals("QUEUED", history.get(0).toStatus());
        assertEquals("Job created", history.get(0).reason());
        assertNull(history.get(0).errorCode());
        assertNotNull(history.get(0).occurredAt());
    }

    @Test
    void recordWithErrorCode() {
        repository.record("job_1", "AI_PROCESSING", "FAILED", "AI generation failed", "AI_GENERATION_FAILED");

        List<StatusHistoryResponse> history = repository.findByJobId("job_1");
        assertEquals(1, history.size());
        assertEquals("AI_PROCESSING", history.get(0).fromStatus());
        assertEquals("FAILED", history.get(0).toStatus());
        assertEquals("AI generation failed", history.get(0).reason());
        assertEquals("AI_GENERATION_FAILED", history.get(0).errorCode());
    }

    @Test
    void findByJobIdReturnsOrderedByTime() {
        repository.record("job_1", null, "QUEUED", "created", null);
        repository.record("job_1", "QUEUED", "AI_PROCESSING", "started AI", null);
        repository.record("job_1", "AI_PROCESSING", "RENDERING", "started render", null);
        repository.record("job_1", "RENDERING", "COMPLETED", "done", null);

        List<StatusHistoryResponse> history = repository.findByJobId("job_1");
        assertEquals(4, history.size());
        assertEquals("QUEUED", history.get(0).toStatus());
        assertEquals("AI_PROCESSING", history.get(1).toStatus());
        assertEquals("RENDERING", history.get(2).toStatus());
        assertEquals("COMPLETED", history.get(3).toStatus());
    }

    @Test
    void findByJobIdReturnsEmptyForUnknownJob() {
        List<StatusHistoryResponse> history = repository.findByJobId("nonexistent");
        assertNotNull(history);
        assertTrue(history.isEmpty());
    }

    @Test
    void findByJobIdOnlyReturnsMatchingJob() {
        repository.record("job_1", null, "QUEUED", "created", null);
        repository.record("job_2", null, "QUEUED", "created", null);

        List<StatusHistoryResponse> history = repository.findByJobId("job_1");
        assertEquals(1, history.size());
        assertEquals("job_1", history.get(0).jobId());
    }
}
