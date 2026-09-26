package com.example.platform.render.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.identity.api.dto.ProjectResponse;
import com.example.platform.identity.api.project.ProjectReadQuery;
import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.app.event.RenderLifecyclePublisher;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.policy.RenderPolicyEngine;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * AUTH-IDOR-FIX-001 — render job mutations addressed by {@code jobId} alone must be authorized
 * against the project that OWNS the job.
 *
 * <p>Before the fix, {@code POST /api/render/jobs/{jobId}/cancel} checked only the tenant, so any
 * member of tenant T could cancel another project's render job. This DB-backed test runs the real
 * {@link RenderJobRepository} against PostgreSQL with a port that authorizes project {@code prj_a}
 * only.</p>
 */
class RenderJobMutationProjectIsolationTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-idor";
    private static final String PROJECT_A = "prj_a";
    private static final String PROJECT_B = "prj_b";
    private static final String JOB_A = "rj_a";
    private static final String JOB_B = "rj_b";

    private static DataSource dataSource;
    private static DSLContext dsl;

    private RenderJobRepository repository;
    private RenderJobService service;
    private final Set<String> authorizedProjects = new HashSet<>();
    private final CanonicalActor actor = CanonicalActor.user("user-1", TENANT, Set.of("EDITOR"), "test");

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
        repository.create(JOB_A, PROJECT_A, TENANT, "snap_a", "default_1080p", "QUEUED", initiator,
                OffsetDateTime.now());
        repository.create(JOB_B, PROJECT_B, TENANT, "snap_b", "default_1080p", "QUEUED", initiator,
                OffsetDateTime.now());

        authorizedProjects.clear();
        authorizedProjects.add(PROJECT_A);
        TenantContext.set(TENANT);
        service = new RenderJobService(
                repository,
                mock(RenderPolicyEngine.class),
                mock(RenderLifecyclePublisher.class),
                mock(RenderJobStatusHistoryRepository.class),
                null,
                projectQuery(),
                actorResolver(),
                authorizationPort(),
                null);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void actorAuthorizedForProjectACanCancelItsOwnProjectJob() {
        service.cancel(JOB_A, TENANT);

        assertEquals("CANCELLED", repository.findById(JOB_A).orElseThrow().status());
    }

    @Test
    void actorAuthorizedForProjectACannotCancelProjectBJob() {
        assertThrows(AuthorizationDeniedException.class, () -> service.cancel(JOB_B, TENANT));

        assertEquals("QUEUED", repository.findById(JOB_B).orElseThrow().status(),
                "a cross-project cancel must not mutate project B's job");
    }

    @Test
    void actorAuthorizedForProjectACannotRetryProjectBJob() {
        assertThrows(AuthorizationDeniedException.class, () -> service.retry(JOB_B, TENANT));
    }

    @Test
    void unauthorizedProjectBJobIsDeniedEvenWhenAuthorizationWouldAllowProjectA() {
        // The authorization decision is made against the job's OWNING project, not a caller input.
        assertEquals(PROJECT_B, repository.findById(JOB_B).orElseThrow().projectId());
        assertThrows(AuthorizationDeniedException.class, () -> service.cancel(JOB_B, TENANT));
    }

    // ── collaborators ───────────────────────────────────────────────────────

    private ProjectReadQuery projectQuery() {
        return new ProjectReadQuery() {
            @Override
            public List<ProjectResponse> listProjects(String tenantId) {
                return List.of(project(PROJECT_A), project(PROJECT_B));
            }

            @Override
            public ProjectResponse getProject(String tenantId, String projectId) {
                return project(projectId);
            }

            private ProjectResponse project(String projectId) {
                return new ProjectResponse(projectId, TENANT, projectId, null, "ACTIVE", Instant.EPOCH);
            }
        };
    }

    private CanonicalActorResolver actorResolver() {
        return () -> Optional.of(actor);
    }

    private AuthorizationDecisionPort authorizationPort() {
        return request -> authorizedProjects.contains(request.resource().projectId())
                ? AuthorizationDecision.allow("TEST_ALLOW")
                : AuthorizationDecision.deny("TEST_DENY", "TEST", "project not authorized");
    }
}
