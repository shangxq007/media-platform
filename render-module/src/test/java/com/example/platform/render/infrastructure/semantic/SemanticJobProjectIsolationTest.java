package com.example.platform.render.infrastructure.semantic;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import com.example.platform.identity.api.authorization.AuthorizationDecisionPort;
import com.example.platform.identity.api.authorization.AuthorizationDeniedException;
import com.example.platform.identity.api.authorization.CanonicalActorResolver;
import com.example.platform.render.app.RenderSurfaceAuthorization;
import com.example.platform.render.api.request.RenderInitiator;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.render.infrastructure.unified.UnifiedGraphRepository;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.authorization.ActorType;
import com.example.platform.shared.authorization.AuthorizationDecision;
import com.example.platform.shared.authorization.AuthorizationRequest;
import com.example.platform.shared.authorization.CanonicalActor;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import java.time.OffsetDateTime;
import java.util.HashSet;
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
 * AUTH-UNPROTECTED-FIX-001 — {@code GET /api/semantic/*&#47;{jobId}} is addressed by {@code jobId}
 * alone, so the job's OWNING project (resolved from the persisted row) must authorize the request.
 *
 * <p>DB-backed: real {@link RenderJobRepository} against PostgreSQL with a canonical decision port
 * that authorizes project {@code prj_a} only. A semantic request for project B's job must be denied
 * BEFORE any graph is loaded; project A's job must clear the authorization gate.</p>
 */
class SemanticJobProjectIsolationTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-semantic";
    private static final String PROJECT_A = "prj_a";
    private static final String PROJECT_B = "prj_b";
    private static final String JOB_A = "rj_a";
    private static final String JOB_B = "rj_b";

    private static DataSource dataSource;
    private static DSLContext dsl;

    private SemanticApi api;
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
        RenderJobRepository repository = new RenderJobRepository(dsl);
        RenderInitiator initiator = RenderInitiator.restore(ActorType.USER, "user-1", TENANT);
        repository.create(JOB_A, PROJECT_A, TENANT, "snap_a", "default_1080p", "QUEUED", initiator,
                OffsetDateTime.now());
        repository.create(JOB_B, PROJECT_B, TENANT, "snap_b", "default_1080p", "QUEUED", initiator,
                OffsetDateTime.now());

        authorizedProjects.clear();
        authorizedProjects.add(PROJECT_A);
        TenantContext.set(TENANT);
        api = new SemanticApi(
                mock(NarrativeEngine.class),
                mock(UnifiedGraphRepository.class),
                repository,
                new RenderSurfaceAuthorization(authorizationPort(), actorResolver()));
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    @Test
    void jobInAuthorizedProjectClearsTheAuthorizationGate() {
        // No graph is stubbed, so the call must fail AFTER authorization (a non-authorization error).
        RuntimeException failure = assertThrows(RuntimeException.class, () -> api.explain(JOB_A));
        assertFalse(failure instanceof AuthorizationDeniedException,
                "project A's job must not be denied by authorization: " + failure);
    }

    @Test
    void jobInOtherProjectIsDeniedBeforeAnyGraphLoad() {
        assertThrows(AuthorizationDeniedException.class, () -> api.explain(JOB_B));
    }

    @Test
    void unknownJobFailsClosed() {
        assertThrows(IllegalArgumentException.class, () -> api.explain("rj_missing"));
    }

    // ── collaborators ───────────────────────────────────────────────────────

    private AuthorizationDecisionPort authorizationPort() {
        return (AuthorizationRequest request) -> authorizedProjects.contains(request.resource().projectId())
                ? AuthorizationDecision.allow("TEST_ALLOW")
                : AuthorizationDecision.deny("TEST_DENY", "TEST", "project not authorized");
    }

    private CanonicalActorResolver actorResolver() {
        return () -> Optional.of(actor);
    }
}
