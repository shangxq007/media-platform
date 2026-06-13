package com.example.platform.render.app;

import static org.jooq.impl.DSL.field;
import static org.jooq.impl.DSL.table;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.app.dto.StatusHistoryResponse;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.List;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RenderJobStatusHistoryRepositoryTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private RenderJobStatusHistoryRepository repository;

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
