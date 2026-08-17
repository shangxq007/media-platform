package com.example.platform.artifact.infrastructure;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.artifact.app.ArtifactRelationRepository;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactReplicaBinding;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.domain.ReplicaRole;
import com.example.platform.artifact.testutil.ArtifactSchemaFixture;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.storage.contract.StorageObjectId;
import com.example.platform.storage.contract.StorageProviderId;
import com.example.platform.storage.contract.StorageReplicaId;
import java.time.Instant;
import java.util.List;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * GCR2-CORRECTION-V1 (ARTIFACT_QUERY_TENANT_ARGUMENT_IS_SEMANTIC_NOT_DECORATIVE_V1):
 * real-PostgreSQL cross-tenant isolation conformance tests for the production
 * jOOQ query adapter. Matrix Q1–Q10 + malformed cross-tenant relation defense.
 */
class JooqArtifactQueryServiceTenantIsolationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;

    private ArtifactRepository artifactRepository;
    private ArtifactRelationRepository relationRepository;
    private JooqArtifactQueryService queryService;

    private static final String TENANT_A = "tenant-a";
    private static final String TENANT_B = "tenant-b";
    private static final ContentDigest DIGEST = ContentDigest.sha256("a".repeat(64));
    private static final ContentDigest DIGEST_B = ContentDigest.sha256("b".repeat(64));

    @BeforeAll
    static void createDataSourceFixture() {
        dataSource = createDataSource();
        var settings = new Settings().withRenderNameCase(RenderNameCase.LOWER);
        dsl = DSL.using(dataSource, SQLDialect.POSTGRES, settings);
        ArtifactSchemaFixture.createCanonicalTables(new JdbcTemplate(dataSource));
        // artifact_relation table with FK constraints matching canonical V1.
        dsl.execute("CREATE TABLE IF NOT EXISTS artifact_relation ("
                + "id varchar(64) primary key,"
                + "source_artifact_id varchar(64) not null,"
                + "target_artifact_id varchar(64) not null,"
                + "relation_type varchar(64) not null,"
                + "created_at timestamp not null"
                + ")");
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        dsl.execute("TRUNCATE TABLE artifact_relation CASCADE");
        dsl.execute("TRUNCATE TABLE artifact_replica CASCADE");
        dsl.execute("TRUNCATE TABLE artifact CASCADE");

        artifactRepository = new ArtifactRepository(dsl);
        relationRepository = new ArtifactRelationRepository(dsl);
        queryService = new JooqArtifactQueryService(artifactRepository, relationRepository);
    }

    private void seedArtifact(String id, String tenantId, ContentDigest digest) {
        artifactRepository.insertRaw(new ArtifactId(id), tenantId, digest, 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.RENDER_MASTER, ArtifactState.AVAILABLE, null);
    }

    private void seedReplica(String artifactId, String replicaId) {
        artifactRepository.insertReplica(new ArtifactReplicaBinding(
                artifactId + ":" + replicaId, new ArtifactId(artifactId),
                new StorageObjectId("s3://bucket/" + artifactId + "-" + replicaId + ".mp4"),
                new StorageReplicaId(replicaId),
                new StorageProviderId("s3"),
                ReplicaRole.PRIMARY, "us-east-1", Instant.now()));
    }

    private void seedRelation(String id, String source, String target, String type) {
        relationRepository.save(new com.example.platform.artifact.domain.ArtifactRelation(
                id, source, target, type));
    }

    // ─── Q1: getArtifact cross tenant ───
    @Test
    void q1_getArtifactCrossTenantEmpty() {
        seedArtifact("art-A", TENANT_A, DIGEST);
        assertTrue(queryService.getArtifact(TENANT_A, new ArtifactId("art-A")).isPresent());
        assertFalse(queryService.getArtifact(TENANT_B, new ArtifactId("art-A")).isPresent(),
                "cross-tenant getArtifact must be EMPTY");
    }

    // ─── Q2: listReplicas cross tenant ───
    @Test
    void q2_listReplicasCrossTenantEmpty() {
        seedArtifact("art-A", TENANT_A, DIGEST);
        seedReplica("art-A", "rep-1");
        assertEquals(1, queryService.listReplicas(TENANT_A, new ArtifactId("art-A")).size());
        assertEquals(0, queryService.listReplicas(TENANT_B, new ArtifactId("art-A")).size(),
                "cross-tenant replica metadata must not be observable");
    }

    // ─── Q3: findReplica cross tenant ───
    @Test
    void q3_findReplicaCrossTenantEmpty() {
        seedArtifact("art-A", TENANT_A, DIGEST);
        seedReplica("art-A", "rep-1");
        assertTrue(queryService.findReplica(TENANT_A, new ArtifactId("art-A"), new StorageReplicaId("rep-1")).isPresent());
        assertFalse(queryService.findReplica(TENANT_B, new ArtifactId("art-A"), new StorageReplicaId("rep-1")).isPresent(),
                "cross-tenant findReplica must be EMPTY");
    }

    // ─── Q4: listParents cross tenant ───
    @Test
    void q4_listParentsCrossTenantEmpty() {
        seedArtifact("art-P", TENANT_A, DIGEST);
        seedArtifact("art-C", TENANT_A, DIGEST_B);
        seedRelation("rel-1", "art-P", "art-C", "GENERATED_FROM");
        assertEquals(1, queryService.listParents(TENANT_A, new ArtifactId("art-C")).size());
        assertEquals(0, queryService.listParents(TENANT_B, new ArtifactId("art-C")).size(),
                "cross-tenant parents must be EMPTY");
    }

    // ─── Q5: listChildren cross tenant ───
    @Test
    void q5_listChildrenCrossTenantEmpty() {
        seedArtifact("art-P", TENANT_A, DIGEST);
        seedArtifact("art-C", TENANT_A, DIGEST_B);
        seedRelation("rel-2", "art-P", "art-C", "GENERATED_FROM");
        assertEquals(1, queryService.listChildren(TENANT_A, new ArtifactId("art-P")).size());
        assertEquals(0, queryService.listChildren(TENANT_B, new ArtifactId("art-P")).size(),
                "cross-tenant children must be EMPTY");
    }

    // ─── Q6: getDirectProvenance cross tenant ───
    @Test
    void q6_directProvenanceCrossTenantEmpty() {
        seedArtifact("art-P", TENANT_A, DIGEST);
        seedArtifact("art-C", TENANT_A, DIGEST_B);
        seedRelation("rel-3", "art-P", "art-C", "GENERATED_FROM");
        assertEquals(1, queryService.getDirectProvenance(TENANT_A, new ArtifactId("art-P")).size());
        assertEquals(1, queryService.getDirectProvenance(TENANT_A, new ArtifactId("art-C")).size());
        assertEquals(0, queryService.getDirectProvenance(TENANT_B, new ArtifactId("art-C")).size(),
                "cross-tenant provenance must be EMPTY");
    }

    // ─── Q7: boundedAncestorTraversal cross tenant ───
    @Test
    void q7_ancestorTraversalCrossTenantEmpty() {
        seedArtifact("art-GP", TENANT_A, DIGEST);
        seedArtifact("art-P", TENANT_A, DIGEST_B);
        seedArtifact("art-C", TENANT_A, DIGEST);
        seedRelation("rel-4", "art-GP", "art-P", "GENERATED_FROM");
        seedRelation("rel-5", "art-P", "art-C", "GENERATED_FROM");
        assertEquals(2, queryService.boundedAncestorTraversal(TENANT_A, new ArtifactId("art-C"), 5).size());
        assertEquals(0, queryService.boundedAncestorTraversal(TENANT_B, new ArtifactId("art-C"), 5).size(),
                "cross-tenant ancestor traversal must be EMPTY");
    }

    // ─── Q8: boundedDescendantTraversal cross tenant ───
    @Test
    void q8_descendantTraversalCrossTenantEmpty() {
        seedArtifact("art-GP", TENANT_A, DIGEST);
        seedArtifact("art-P", TENANT_A, DIGEST_B);
        seedArtifact("art-C", TENANT_A, DIGEST);
        seedRelation("rel-6", "art-GP", "art-P", "GENERATED_FROM");
        seedRelation("rel-7", "art-P", "art-C", "GENERATED_FROM");
        assertEquals(2, queryService.boundedDescendantTraversal(TENANT_A, new ArtifactId("art-GP"), 5).size());
        assertEquals(0, queryService.boundedDescendantTraversal(TENANT_B, new ArtifactId("art-GP"), 5).size(),
                "cross-tenant descendant traversal must be EMPTY");
    }

    // ─── Q9: findByContentDigest cross tenant ───
    @Test
    void q9_contentDigestQueryCrossTenant() {
        seedArtifact("art-A1", TENANT_A, DIGEST);
        seedArtifact("art-A2", TENANT_A, DIGEST);
        seedArtifact("art-B1", TENANT_B, DIGEST);
        List<Artifact> forA = queryService.findByContentDigest(TENANT_A, DIGEST, 10);
        List<Artifact> forB = queryService.findByContentDigest(TENANT_B, DIGEST, 10);
        assertTrue(forA.stream().noneMatch(a -> a.tenantId().equals(TENANT_B)),
                "tenant-a digest query must not return tenant-b artifacts");
        assertTrue(forB.stream().noneMatch(a -> a.tenantId().equals(TENANT_A)),
                "tenant-b digest query must not return tenant-a artifacts");
        assertEquals(2, forA.size());
        assertEquals(1, forB.size());
    }

    // ─── Q10: same-tenant positive behavior ───
    @Test
    void q10_sameTenantPositiveBehavior() {
        seedArtifact("art-GP", TENANT_A, DIGEST);
        seedArtifact("art-P", TENANT_A, DIGEST_B);
        seedArtifact("art-C", TENANT_A, DIGEST);
        seedReplica("art-C", "rep-1");
        seedRelation("rel-8", "art-GP", "art-P", "GENERATED_FROM");
        seedRelation("rel-9", "art-P", "art-C", "GENERATED_FROM");

        assertTrue(queryService.getArtifact(TENANT_A, new ArtifactId("art-C")).isPresent());
        assertEquals(1, queryService.listReplicas(TENANT_A, new ArtifactId("art-C")).size());
        assertTrue(queryService.findReplica(TENANT_A, new ArtifactId("art-C"), new StorageReplicaId("rep-1")).isPresent());
        assertEquals(1, queryService.listParents(TENANT_A, new ArtifactId("art-C")).size());
        assertEquals(1, queryService.listChildren(TENANT_A, new ArtifactId("art-GP")).size());
        assertEquals(2, queryService.boundedAncestorTraversal(TENANT_A, new ArtifactId("art-C"), 5).size());
        assertEquals(2, queryService.boundedDescendantTraversal(TENANT_A, new ArtifactId("art-GP"), 5).size());
    }

    // ─── maxDepth contract: < 1 → empty (matches InMemory) ───
    @Test
    void maxDepthBelowOneReturnsEmpty() {
        seedArtifact("art-P", TENANT_A, DIGEST);
        seedArtifact("art-C", TENANT_A, DIGEST_B);
        seedRelation("rel-10", "art-P", "art-C", "GENERATED_FROM");
        assertEquals(0, queryService.boundedAncestorTraversal(TENANT_A, new ArtifactId("art-C"), 0).size());
        assertEquals(0, queryService.boundedDescendantTraversal(TENANT_A, new ArtifactId("art-P"), -1).size());
    }

    // ─── MALFORMED CROSS-TENANT RELATION: B1 must never surface ───
    @Test
    void malformedCrossTenantRelationNeverSurfaces() {
        seedArtifact("A1", TENANT_A, DIGEST);
        seedArtifact("A2", TENANT_A, DIGEST_B);
        seedArtifact("B1", TENANT_B, DIGEST);
        // malformed relation: A2 (tenant-a) → B1 (tenant-b)
        seedRelation("rel-bad", "A2", "B1", "GENERATED_FROM");

        // tenant-a traversal from A1 → A2 must NOT surface B1.
        seedRelation("rel-ok", "A1", "A2", "GENERATED_FROM");
        var descendants = queryService.boundedDescendantTraversal(TENANT_A, new ArtifactId("A1"), 5);
        assertTrue(descendants.stream().noneMatch(id -> id.value().equals("B1")),
                "B1 must not appear in tenant-a traversal: " + descendants);

        // Direct relation queries must also be tenant-scoped.
        assertEquals(0, queryService.listChildren(TENANT_A, new ArtifactId("A2")).stream()
                        .filter(id -> id.value().equals("B1")).count(),
                "B1 must not appear in tenant-a listChildren");
        assertEquals(0, queryService.getDirectProvenance(TENANT_A, new ArtifactId("A2")).stream()
                        .filter(e -> e.parentArtifactId().value().equals("B1")
                                || e.childArtifactId().value().equals("B1")).count(),
                "B1 must not appear in tenant-a provenance");

        // And the reverse direction: tenant-b must not see A2 via B1.
        var bDescendants = queryService.boundedDescendantTraversal(TENANT_B, new ArtifactId("B1"), 5);
        assertTrue(bDescendants.stream().noneMatch(id -> id.value().equals("A2")),
                "A2 must not appear in tenant-b traversal");
    }
}
