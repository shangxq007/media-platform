package com.example.platform.artifact.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.artifact.app.ArtifactCatalogRepository;
import com.example.platform.artifact.app.ArtifactCatalogService;
import com.example.platform.artifact.app.ArtifactGcService;
import com.example.platform.artifact.app.ArtifactLifecycleService;
import com.example.platform.artifact.app.ArtifactProjectAuthorizationPort;
import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.artifact.testutil.ArtifactSchemaFixture;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.PlatformException;
import com.example.platform.shared.web.TenantContext;
import java.util.HashSet;
import java.util.Set;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;

/**
 * AUTH-ARTIFACT-BOUNDARY-FIX-002 — DB-backed cross-project isolation.
 *
 * <p>The authorized project must be bound to the addressed Artifact. This test runs
 * the REAL controller + service + repository chain against a real PostgreSQL, with a
 * port that authorizes a configurable set of projects, and proves:</p>
 * <ul>
 *   <li>an actor authorized for project A can read/tombstone project A's Artifact;</li>
 *   <li>the same actor CANNOT read or tombstone project B's Artifact (fail closed,
 *       no state disclosed, no mutation);</li>
 *   <li>authorization for project B does not grant access to project A's Artifact.</li>
 * </ul>
 *
 * <p>On the pre-fix baseline ({@code e8eba4d5}) the cross-project cases FAIL: the
 * lifecycle service resolved the Artifact by tenant only, so the caller-supplied
 * {@code projectId} authorized one project while the operation acted on another.</p>
 */
class CrossProjectIsolationTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-xproj";
    private static final String PROJECT_A = "prj_a";
    private static final String PROJECT_B = "prj_b";
    private static final String ARTIFACT_A = "art_a";
    private static final String ARTIFACT_B = "art_b";
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    private static DataSource dataSource;
    private static DSLContext dsl;

    private ArtifactRepository artifactRepository;
    private ArtifactLifecycleService lifecycleService;
    private ArtifactLifecycleController controller;
    private PortStub projectAuthorization;

    @BeforeAll
    static void createDataSourceFixture() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES,
                new Settings().withRenderNameCase(RenderNameCase.LOWER));
        ArtifactSchemaFixture.createCanonicalTables(new JdbcTemplate(dataSource));
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        dsl.execute("TRUNCATE TABLE artifact_relation CASCADE");
        dsl.execute("TRUNCATE TABLE artifact_pin CASCADE");
        dsl.execute("TRUNCATE TABLE artifact_replica CASCADE");
        dsl.execute("TRUNCATE TABLE artifact CASCADE");

        artifactRepository = new ArtifactRepository(dsl);
        ArtifactCatalogRepository catalogRepository = new ArtifactCatalogRepository(dsl);
        ArtifactRelationRepository relationRepository = new ArtifactRelationRepository(dsl);
        ArtifactCatalogService catalogService =
                new ArtifactCatalogService(catalogRepository, relationRepository);
        ArtifactPinRepository pinRepository = new ArtifactPinRepository(dsl);
        lifecycleService = new ArtifactLifecycleService(catalogService, artifactRepository, pinRepository);
        projectAuthorization = new PortStub();
        controller = new ArtifactLifecycleController(
                lifecycleService, org.mockito.Mockito.mock(ArtifactGcService.class), projectAuthorization);

        seedArtifact(ARTIFACT_A, PROJECT_A);
        seedArtifact(ARTIFACT_B, PROJECT_B);
    }

    @AfterEach
    void clearTenantContext() {
        TenantContext.clear();
    }

    private void seedArtifact(String id, String projectId) {
        artifactRepository.insertRaw(new ArtifactId(id), TENANT, projectId, DIGEST, 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, ArtifactState.AVAILABLE, null);
    }

    private ArtifactState stateOf(String artifactId) {
        return artifactRepository.findById(TENANT, new ArtifactId(artifactId))
                .orElseThrow()
                .state();
    }

    @Test
    void actorAuthorizedForProjectACanOperateOnItsOwnProjectArtifact() {
        projectAuthorization.authorize(PROJECT_A);

        var check = controller.deleteCheck(ARTIFACT_A, PROJECT_A);
        assertTrue(check.deletable());
        assertEquals(PROJECT_A, check.projectId());

        var tombstoned = controller.tombstone(ARTIFACT_A, PROJECT_A);
        assertEquals(PROJECT_A, tombstoned.projectId());
        assertEquals(ArtifactState.DELETING, stateOf(ARTIFACT_A));
    }

    @Test
    void actorAuthorizedForProjectACannotTombstoneProjectBArtifact() {
        projectAuthorization.authorize(PROJECT_A);

        assertThrows(PlatformException.class, () -> controller.tombstone(ARTIFACT_B, PROJECT_A));
        assertEquals(ArtifactState.AVAILABLE, stateOf(ARTIFACT_B),
                "a cross-project tombstone must not mutate project B's Artifact");
    }

    @Test
    void deleteCheckDoesNotLeakAnotherProjectsArtifactState() {
        projectAuthorization.authorize(PROJECT_A);

        assertThrows(PlatformException.class, () -> controller.deleteCheck(ARTIFACT_B, PROJECT_A));
        assertEquals(ArtifactState.AVAILABLE, stateOf(ARTIFACT_B));
    }

    @Test
    void authorizationForProjectBDoesNotReachProjectAArtifact() {
        projectAuthorization.authorize(PROJECT_B);

        assertThrows(PlatformException.class, () -> controller.deleteCheck(ARTIFACT_A, PROJECT_B));
        assertThrows(PlatformException.class, () -> controller.tombstone(ARTIFACT_A, PROJECT_B));
        assertEquals(ArtifactState.AVAILABLE, stateOf(ARTIFACT_A));
    }

    @Test
    void unauthorizedProjectIsRejectedBeforeAnyLookup() {
        projectAuthorization.authorize(PROJECT_A);

        assertThrows(ResponseStatusException.class, () -> controller.deleteCheck(ARTIFACT_A, "prj_c"));
        assertEquals(ArtifactState.AVAILABLE, stateOf(ARTIFACT_A));
    }

    /** Authorization stub: allows only the configured projects. */
    private static final class PortStub implements ArtifactProjectAuthorizationPort {
        private final Set<String> authorized = new HashSet<>();

        void authorize(String projectId) {
            authorized.add(projectId);
        }

        @Override
        public void requireRead(String tenantId, String projectId) {
            require(projectId);
        }

        @Override
        public void requireWrite(String tenantId, String projectId) {
            require(projectId);
        }

        private void require(String projectId) {
            if (!authorized.contains(projectId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "project not authorized");
            }
        }
    }
}
