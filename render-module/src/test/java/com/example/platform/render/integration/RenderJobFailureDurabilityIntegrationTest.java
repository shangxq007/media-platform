package com.example.platform.render.integration;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.render.app.RenderJobFailureService;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;

import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.OffsetDateTime;

/**
 * Proves Provider failure durability with real PostgreSQL, real Spring transactions,
 * and real {@link RenderJobRepository}.
 *
 * <p>Key invariant: {@code recordDurableFailure} uses {@code REQUIRES_NEW} so the
 * FAILED status survives even when the outer transaction rolls back.
 */
@SpringBootTest(
    classes = RenderJobFailureDurabilityIntegrationTest.TestConfig.class,
    properties = {
        "spring.flyway.enabled=false",
        "spring.sql.init.mode=never"
    }
)
class RenderJobFailureDurabilityIntegrationTest extends PostgresTestContainerSupport {

    @Autowired private RenderJobRepository renderJobRepository;
    @Autowired private RenderJobFailureService failureService;
    @Autowired private PlatformTransactionManager transactionManager;

    private static DSLContext dsl;

    @BeforeAll
    static void setUpSchema() {
        dsl = DSL.using(createDataSource(), org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @BeforeEach
    void cleanUp() {
        RenderTestSchemaFixture.truncate(dsl);
    }

    // ---- Test 1: recordDurableFailure persists FAILED to real PostgreSQL ----

    @Test
    void recordDurableFailure_persistsFailedToRealPostgres() {
        // given: job in EXECUTING (active state)
        String jobId = "job-dur-1";
        insertJob(jobId, "EXECUTING");

        // when: durable failure is recorded
        failureService.recordDurableFailure(jobId, "provider timeout");

        // then: job is FAILED with error message persisted
        JobRow row = loadJob(jobId);
        assertEquals("FAILED", row.status());
        assertEquals("provider timeout", row.errorMessage());
    }

    // ---- Test 2: REQUIRES_NEW commits independently — outer rollback preserves FAILED ----

    @Test
    void requiresNew_commitsIndependently_outerRollbackPreservesFailed() {
        // given: job in EXECUTING
        String jobId = "job-dur-2";
        insertJob(jobId, "EXECUTING");

        // when: failure recorded inside a transaction that rolls back
        TransactionTemplate outerTx = new TransactionTemplate(transactionManager);
        try {
            outerTx.execute(status -> {
                failureService.recordDurableFailure(jobId, "provider crash");
                // simulate outer failure — should roll back outer TX only
                throw new RuntimeException("simulated outer failure");
            });
        } catch (RuntimeException ex) {
            // expected: outer TX rolled back
            assertEquals("simulated outer failure", ex.getMessage());
        }

        // then: FAILED persists because REQUIRES_NEW committed in its own transaction
        JobRow row = loadJob(jobId);
        assertEquals("FAILED", row.status());
        assertEquals("provider crash", row.errorMessage());
    }

    // ---- Test 3: CAS rejects terminal state COMPLETED ----

    @Test
    void cas_rejectsTerminalStateCompleted() {
        // given: job already COMPLETED (terminal)
        String jobId = "job-dur-3";
        insertJob(jobId, "COMPLETED");

        // when: attempt to record failure
        failureService.recordDurableFailure(jobId, "too late");

        // then: status unchanged — CAS rejected
        JobRow row = loadJob(jobId);
        assertEquals("COMPLETED", row.status());
    }

    // ---- Test 4: CAS rejects already-FAILED state (no overwrite) ----

    @Test
    void cas_rejectsAlreadyFailed_noOverwrite() {
        // given: job already FAILED
        String jobId = "job-dur-4";
        insertJob(jobId, "FAILED");
        // pre-set an error message to verify it is NOT overwritten
        dsl.update(DSL.table("render_job"))
                .set(DSL.field("error_message"), "original reason")
                .where(DSL.field("id").eq(jobId))
                .execute();

        // when: attempt to record a different failure
        failureService.recordDurableFailure(jobId, "new reason");

        // then: original error message preserved — CAS rejected
        JobRow row = loadJob(jobId);
        assertEquals("FAILED", row.status());
        assertEquals("original reason", row.errorMessage());
    }

    // ---- Test 5: CAS accepts SELECTING_PROVIDER (active state) ----

    @Test
    void cas_acceptsSelectingProvider() {
        String jobId = "job-dur-5";
        insertJob(jobId, "SELECTING_PROVIDER");

        failureService.recordDurableFailure(jobId, "provider discovery failed");

        JobRow row = loadJob(jobId);
        assertEquals("FAILED", row.status());
        assertEquals("provider discovery failed", row.errorMessage());
    }

    // ---- Test 6: CAS accepts PROVIDER_SELECTED (active state) ----

    @Test
    void cas_acceptsProviderSelected() {
        String jobId = "job-dur-6";
        insertJob(jobId, "PROVIDER_SELECTED");

        failureService.recordDurableFailure(jobId, "provider rejected job");

        JobRow row = loadJob(jobId);
        assertEquals("FAILED", row.status());
        assertEquals("provider rejected job", row.errorMessage());
    }

    // ---- Test 7: CAS accepts COMPLETING (active state) ----

    @Test
    void cas_acceptsCompleting() {
        String jobId = "job-dur-7";
        insertJob(jobId, "COMPLETING");

        failureService.recordDurableFailure(jobId, "output upload failed");

        JobRow row = loadJob(jobId);
        assertEquals("FAILED", row.status());
        assertEquals("output upload failed", row.errorMessage());
    }

    // ---- Test 8: CAS rejects QUEUED (not yet claimed) ----

    @Test
    void cas_rejectsQueued() {
        String jobId = "job-dur-8";
        insertJob(jobId, "QUEUED");

        failureService.recordDurableFailure(jobId, "premature failure");

        JobRow row = loadJob(jobId);
        assertEquals("QUEUED", row.status());
    }

    // ---- Helpers ----

    private void insertJob(String jobId, String status) {
        dsl.insertInto(DSL.table("render_job"))
                .columns(DSL.field("id"), DSL.field("project_id"), DSL.field("tenant_id"),
                        DSL.field("timeline_snapshot_id"), DSL.field("profile"),
                        DSL.field("status"), DSL.field("created_at"))
                .values(jobId, "proj-1", "tenant-1", "snap-1", "default_1080p",
                        status, OffsetDateTime.now())
                .execute();
    }

    private record JobRow(String status, String errorMessage) {}

    private JobRow loadJob(String jobId) {
        var r = dsl.select(DSL.field("status"), DSL.field("error_message"))
                .from(DSL.table("render_job"))
                .where(DSL.field("id").eq(jobId))
                .fetchOne();
        assertNotNull(r, "Job not found: " + jobId);
        return new JobRow(
                r.get(DSL.field("status"), String.class),
                r.get(DSL.field("error_message"), String.class));
    }

    // ---- Spring Test Configuration ----

    @Configuration
    @EnableTransactionManagement
    static class TestConfig {
        @Bean
        public javax.sql.DataSource testDataSource() {
            return createDataSource();
        }

        @Bean
        public DSLContext dslContext(javax.sql.DataSource ds) {
            return DSL.using(ds, org.jooq.SQLDialect.POSTGRES);
        }

        @Bean
        public org.springframework.jdbc.datasource.DataSourceTransactionManager transactionManager(
                javax.sql.DataSource ds) {
            return new org.springframework.jdbc.datasource.DataSourceTransactionManager(ds);
        }

        @Bean
        public RenderJobRepository renderJobRepository(DSLContext dsl) {
            return new RenderJobRepository(dsl);
        }

        @Bean
        public RenderJobFailureService failureService(RenderJobRepository repo) {
            return new RenderJobFailureService(repo);
        }
    }
}
