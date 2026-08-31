package com.example.platform.artifact.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.artifact.infrastructure.ArtifactRepository;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.ErrorCodeRegistry;
import java.time.Instant;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.SQLDialect;
import org.jooq.conf.RenderNameCase;
import org.jooq.conf.Settings;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.mock;

/**
 * GCR-2 TEST GROUPS R + S — historical reproducibility protection and
 * catalog/storage separation.
 *
 * R1 pinned Artifact logical delete → REJECT
 * R2 pinned last usable replica delete → REJECT
 * R3 pinned multi-replica delete → conservative REJECT (documented policy)
 * S2 catalog projection rebuild/delete does not mutate canonical Artifact
 */
class ArtifactPinProtectionTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;

    private ArtifactRepository artifactRepository;
    private ArtifactPinRepository pinRepository;
    private ArtifactLifecycleService lifecycleService;

    private static final String TENANT = "tenant-r1";
    private static final String ARTIFACT_ID = "art-hist-1";
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));

    @BeforeAll
    static void createDataSourceFixture() {
        dataSource = createDataSource();
        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES, settings);
        com.example.platform.artifact.testutil.ArtifactSchemaFixture.createCanonicalTables(
                new org.springframework.jdbc.core.JdbcTemplate(dataSource));
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE artifact_pin CASCADE");
        dsl.execute("TRUNCATE TABLE artifact_replica CASCADE");
        dsl.execute("TRUNCATE TABLE artifact CASCADE");

        artifactRepository = new ArtifactRepository(dsl);
        pinRepository = new ArtifactPinRepository(dsl);
        ErrorCodeRegistry registry = new ErrorCodeRegistry();
        registry.loadErrorCodes();
        ArtifactCatalogRepository catalogRepo = new ArtifactCatalogRepository(dsl);
        ArtifactRelationRepository relationRepo = new ArtifactRelationRepository(dsl);
        com.example.platform.artifact.infrastructure.JooqArtifactCommitService commitService =
                new com.example.platform.artifact.infrastructure.JooqArtifactCommitService(
                        artifactRepository, relationRepo, dsl);
        ArtifactCatalogService catalog = new ArtifactCatalogService(catalogRepo, relationRepo, commitService, registry);
        ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);
        lifecycleService = new ArtifactLifecycleService(
                catalogRepo, catalog, artifactRepository, pinRepository, dsl, registry, events, java.util.List.of());
    }

    private void seedPinnedArtifactWithReplica(String replicaId) {
        artifactRepository.insertRaw(new ArtifactId(ARTIFACT_ID), TENANT, DIGEST, 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, ArtifactState.AVAILABLE, null);
        artifactRepository.insertReplica(new com.example.platform.artifact.domain.ArtifactReplicaBinding(
                ARTIFACT_ID + ":" + replicaId, new ArtifactId(ARTIFACT_ID),
                new com.example.platform.storage.contract.StorageObjectId("s3://bucket/" + replicaId + ".mp4"),
                new com.example.platform.storage.contract.StorageReplicaId(replicaId),
                new com.example.platform.storage.contract.StorageProviderId("s3"),
                com.example.platform.artifact.domain.ReplicaRole.PRIMARY, "default", Instant.now()));
        pinRepository.insert("pin-" + replicaId, "trev-hist-1", "proj-1", TENANT,
                ARTIFACT_ID, DIGEST, Instant.now());
    }

    @Test
    void r1_pinnedArtifactLogicalDeleteRejected() {
        seedPinnedArtifactWithReplica("rep-1");

        var check = lifecycleService.deleteCheck(ARTIFACT_ID);
        assertFalse(check.deletable());
        assertTrue(check.references().stream().anyMatch(r -> "PINNED_BY_HISTORICAL_REVISION".equals(r.get("reason"))));
    }

    @Test
    void r2_pinnedLastUsableReplicaDeleteRejected() {
        seedPinnedArtifactWithReplica("rep-1");

        var check = lifecycleService.replicaDeleteCheck(ARTIFACT_ID, "rep-1");
        assertFalse(check.deletable());
        assertEquals("PINNED_LAST_USABLE_REPLICA", check.reason());
    }

    @Test
    void r3_pinnedMultiReplicaPolicyConservativeReject() {
        seedPinnedArtifactWithReplica("rep-1");
        artifactRepository.insertReplica(new com.example.platform.artifact.domain.ArtifactReplicaBinding(
                ARTIFACT_ID + ":rep-2", new ArtifactId(ARTIFACT_ID),
                new com.example.platform.storage.contract.StorageObjectId("s3://bucket/rep-2.mp4"),
                new com.example.platform.storage.contract.StorageReplicaId("rep-2"),
                new com.example.platform.storage.contract.StorageProviderId("s3"),
                com.example.platform.artifact.domain.ReplicaRole.SECONDARY, "default", Instant.now()));

        // Documented bounded policy: pinned artifacts keep all replicas (no full
        // replica lifecycle tier in GCR-2) -> conservative REJECT.
        var check = lifecycleService.replicaDeleteCheck(ARTIFACT_ID, "rep-1");
        assertFalse(check.deletable());
        assertEquals("PINNED_MULTI_REPLICA_CONSERVATIVE", check.reason());
    }

    @Test
    void r5_pinProtectionRequiresCanonicalArtifactPresence() {
        // DB FK (fk_artifact_pin_artifact) rejects a pin for a nonexistent Artifact —
        // canonical existence is database-enforced, not just application-validated.
        assertThrows(org.jooq.exception.DataAccessException.class, () ->
                pinRepository.insert("pin-x", "trev-x", "proj-x", "tenant-1",
                        "art-phantom", DIGEST, Instant.now()));
        assertFalse(pinRepository.isPinned("art-phantom"));
    }

    @Test
    void r6_pinProtectionRejectsCrossTenantArtifactOwnership() {
        artifactRepository.insertRaw(new ArtifactId(ARTIFACT_ID), TENANT, DIGEST, 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, ArtifactState.AVAILABLE, null);

        var failure = assertThrows(org.jooq.exception.DataAccessException.class, () ->
                pinRepository.insert("pin-cross-owner", "trev-x", "proj-x", "tenant-other",
                        ARTIFACT_ID, DIGEST, Instant.now()));

        var postgresFailure = (org.postgresql.util.PSQLException) failure.getCause();
        assertEquals("23503", postgresFailure.getSQLState());
        assertFalse(pinRepository.isPinned(ARTIFACT_ID));
    }

    @Test
    void s2_catalogProjectionDoesNotMutateCanonicalArtifact() {
        artifactRepository.insertRaw(new ArtifactId(ARTIFACT_ID), TENANT, DIGEST, 2048L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, ArtifactState.AVAILABLE, null);

        // Projection read reflects canonical truth; no canonical mutation via catalog.
        var catalog = new ArtifactCatalogRepository(dsl);
        var entries = catalog.findAll();
        assertEquals(1, entries.size());
        assertEquals(ARTIFACT_ID, entries.get(0).id());
        assertEquals(DIGEST.canonicalValue(), entries.get(0).checksum());

        // Canonical record unchanged after projection reads.
        var canonical = artifactRepository.findById(TENANT, new ArtifactId(ARTIFACT_ID)).orElseThrow();
        assertEquals(2048L, canonical.byteLength());
        assertEquals(DIGEST, canonical.contentDigest());
    }
}
