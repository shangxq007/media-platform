package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.cache.RenderCacheTenantGuard;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.api.revision.TimelineSnapshotQueries;
import java.time.OffsetDateTime;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AUTH-IDOR-FIX-001 — the base-job timeline loader must bind the addressed base job to the
 * project the caller addressed.
 *
 * <p>Before the fix the loader resolved the base job by {@code (jobId, tenantId)} only, so an
 * actor authorized for project A could read project B's job timeline through the free
 * {@code baseJobId} input of ai-edit / incremental. This DB-backed test exercises the real
 * {@link RenderJobRepository} + {@link RenderCacheTenantGuard} project predicate.</p>
 */
class BaseJobTimelineProjectIsolationTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-idor";
    private static final String PROJECT_A = "prj_a";
    private static final String PROJECT_B = "prj_b";
    private static final String JOB_A = "rj_a";
    private static final String JOB_B = "rj_b";
    private static final String INTERNAL_TIMELINE = "{\"schemaVersion\":\"internal-1.0\",\"tracks\":[]}";

    private static DataSource dataSource;
    private static DSLContext dsl;

    private RenderJobRepository repository;
    private BaseJobTimelineLoader loader;

    @BeforeAll
    static void createSchema() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
        RenderTestSchemaFixture.insertCanonicalProject(dsl, TENANT, PROJECT_A);
        RenderTestSchemaFixture.insertCanonicalProject(dsl, TENANT, PROJECT_B);
        repository = new RenderJobRepository(dsl);
        RenderInitiator initiator = RenderInitiator.restore(ActorType.USER, "user-1", TENANT);
        repository.create(JOB_A, PROJECT_A, TENANT, "snap_a", "default_1080p", "COMPLETED", initiator,
                OffsetDateTime.now());
        repository.create(JOB_B, PROJECT_B, TENANT, "snap_b", "default_1080p", "COMPLETED", initiator,
                OffsetDateTime.now());
        repository.updateAiScript(JOB_A, INTERNAL_TIMELINE);
        repository.updateAiScript(JOB_B, INTERNAL_TIMELINE);

        TimelineSpecResolver resolver = mock(TimelineSpecResolver.class);
        when(resolver.isInternalTimelineJson(anyString())).thenReturn(true);
        loader = new BaseJobTimelineLoader(
                repository, mock(TimelineSnapshotQueries.class), resolver, new RenderCacheTenantGuard(dsl));
    }

    @Test
    void baseJobTimelineLoadsWithinTheAddressedProject() {
        assertEquals(INTERNAL_TIMELINE,
                loader.loadInternalTimelineJson(JOB_A, TENANT, PROJECT_A).orElseThrow());
    }

    @Test
    void baseJobTimelineOfAnotherProjectIsNotReadable() {
        // The IDOR assertion: job A belongs to project A, so addressing it as project B must fail
        // closed instead of returning project A's timeline.
        assertTrue(loader.loadInternalTimelineJson(JOB_A, TENANT, PROJECT_B).isEmpty());
        assertTrue(loader.loadInternalTimelineJson(JOB_B, TENANT, PROJECT_A).isEmpty());
    }

    @Test
    void missingProjectScopeFailsClosed() {
        assertTrue(loader.loadInternalTimelineJson(JOB_A, TENANT, "").isEmpty(),
                "a blank project scope must not degrade to a tenant-wide read");
        assertTrue(loader.loadInternalTimelineJson(JOB_A, TENANT, " ").isEmpty());
    }

    @Test
    void unknownJobIsEmpty() {
        assertTrue(loader.loadInternalTimelineJson("rj_missing", TENANT, PROJECT_A).isEmpty());
    }
}
