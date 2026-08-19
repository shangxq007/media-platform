package com.example.platform.render.app.timeline;

import static com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactPinService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.app.InternalTimelineJson;
import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelineMergeEngine;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.internal.TimelineMergeRequest;
import com.example.platform.timeline.internal.TimelineMergeResult;
import com.example.platform.typedschema.jooq.generated.tables.Product;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * R5-C10 (CHECKPOINT_A Round 5): PERSISTENT merge path — real PostgreSQL.
 *
 * <p>Uses real revision repository / snapshot service / current-revision
 * service / ArtifactPinService / ArtifactPinRepository / pin validator
 * wiring. The ArtifactQueryService is mocked ONLY at the validation boundary
 * (the persistence-authority seam); artifact rows are real DB rows.
 *
 * <p>SUCCESS: base/source/target revisions saved (pinned), persistent merge
 * creates a NEW merge revision whose exact pin rows exist, head points to the
 * merge revision.
 *
 * <p>FAILURE: the REAL ArtifactPinRepository INSERT fails (ghost artifact FK)
 * inside the merge transaction; the merge revision rolls back, head unchanged,
 * no partial pin rows.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CheckpointARound5PersistentMergePinIT extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-r5-merge";
    private static final String DIGEST_HEX = "a".repeat(64);

    private static DataSource dataSource;
    private DSLContext dsl;
    private ProductCurrentRevisionService currentRevisionService;
    private TimelineSnapshotService snapshotService;
    private ArtifactPinService pinService;
    private TimelineMergeEngine mergeEngine;

    @BeforeAll
    void setUpDatabase() {
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
        currentRevisionService = new ProductCurrentRevisionService(dsl);
        snapshotService = new TimelineSnapshotService(dsl);
        pinService = new ArtifactPinService(new ArtifactPinRepository(dsl));
        TenantContext.set(TENANT);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    private void insertProduct(String productId) {
        dsl.insertInto(Product.PRODUCT)
                .set(Product.PRODUCT.PRODUCT_ID, productId)
                .set(Product.PRODUCT.PRODUCT_TYPE, "video")
                .set(Product.PRODUCT.REPRESENTATION_KIND, "master")
                .set(Product.PRODUCT.STATUS, "REGISTERED")
                .set(Product.PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(Product.PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private void insertArtifact(String artifactId, String digest) {
        dsl.insertInto(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.ID, artifactId)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.TENANT_ID, TENANT)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.CONTENT_DIGEST, digest)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.BYTE_LENGTH, 1024L)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.MEDIA_TYPE, "VIDEO")
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.ARTIFACT_KIND, "SOURCE_MEDIA")
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.STATE, "AVAILABLE")
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.SCHEMA_VERSION, 1)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.CREATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    // ── internal-1.0 payload fixtures (the merge path's canonical wire) ──

    private static com.fasterxml.jackson.databind.node.ObjectNode rangeNode(
            com.fasterxml.jackson.databind.ObjectMapper mapper, long startMs, long durationMs) {
        com.fasterxml.jackson.databind.node.ObjectNode rate = mapper.createObjectNode();
        rate.put("num", 30L);
        rate.put("den", 1L);
        com.fasterxml.jackson.databind.node.ObjectNode start = mapper.createObjectNode();
        start.put("frame", (startMs * 30L) / 1000L);
        start.set("rate", rate);
        com.fasterxml.jackson.databind.node.ObjectNode duration = mapper.createObjectNode();
        duration.put("frame", (durationMs * 30L) / 1000L);
        duration.set("rate", rate);
        com.fasterxml.jackson.databind.node.ObjectNode range = mapper.createObjectNode();
        range.set("start", start);
        range.set("duration", duration);
        return range;
    }

    private static com.fasterxml.jackson.databind.node.ObjectNode clipNode(
            com.fasterxml.jackson.databind.ObjectMapper mapper, String id, long startMs,
            long durationMs, String artifactId, String digest) {
        com.fasterxml.jackson.databind.node.ObjectNode clip = mapper.createObjectNode();
        clip.put("id", id);
        clip.put("assetId", "asset-" + id);
        clip.set("timelineRange", rangeNode(mapper, startMs, durationMs));
        clip.set("sourceRange", rangeNode(mapper, 0L, durationMs));
        com.fasterxml.jackson.databind.node.ObjectNode sb = mapper.createObjectNode();
        sb.put("sourceKind", "MEDIA_STREAM");
        sb.put("mediaAssetId", "asset-" + id);
        sb.put("mediaStreamId", "stream-" + id);
        sb.put("artifactId", artifactId);
        com.fasterxml.jackson.databind.node.ObjectNode digestNode = mapper.createObjectNode();
        digestNode.put("algorithm", "SHA256");
        digestNode.put("value", digest);
        sb.set("contentDigest", digestNode);
        sb.put("sourceRangeStart", "0/1");
        sb.put("sourceRangeEnd", durationMs + "/1");
        clip.set("sourceBinding", sb);
        return clip;
    }

    private static String internalPayload(
            com.fasterxml.jackson.databind.ObjectMapper mapper, String timelineId,
            long durationMs, String artifactId, String digest) {
        com.fasterxml.jackson.databind.node.ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", timelineId);
        com.fasterxml.jackson.databind.node.ObjectNode composition = mapper.createObjectNode();
        com.fasterxml.jackson.databind.node.ArrayNode trackArray = mapper.createArrayNode();
        com.fasterxml.jackson.databind.node.ObjectNode track = mapper.createObjectNode();
        track.put("id", "v1");
        track.put("type", "VIDEO");
        com.fasterxml.jackson.databind.node.ArrayNode clipArray = mapper.createArrayNode();
        clipArray.add(clipNode(mapper, "c1", 0L, durationMs, artifactId, digest));
        track.set("clips", clipArray);
        trackArray.add(track);
        composition.set("tracks", trackArray);
        root.set("composition", composition);
        try {
            return mapper.writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("internal payload serialization failed", e);
        }
    }

    private void insertRevision(String revId, String projectId, String parentRevId, int revNumber,
            String snapshotId) {
        dsl.insertInto(TIMELINE_REVISION)
                .set(TIMELINE_REVISION.ID, revId)
                .set(TIMELINE_REVISION.PROJECT_ID, projectId)
                .set(TIMELINE_REVISION.TENANT_ID, TENANT)
                .set(TIMELINE_REVISION.PARENT_REVISION_ID, parentRevId)
                .set(TIMELINE_REVISION.REVISION_NUMBER, revNumber)
                .set(TIMELINE_REVISION.SNAPSHOT_ID, snapshotId)
                .set(TIMELINE_REVISION.INTERNAL_REVISION, revNumber)
                .set(TIMELINE_REVISION.CONTENT_HASH, "hash-" + revId)
                .set(TIMELINE_REVISION.SCHEMA_VERSION, "internal-1.0")
                .set(TIMELINE_REVISION.SOURCE, "merge")
                .set(TIMELINE_REVISION.AUTHOR_USER_ID, "user-1")
                .set(TIMELINE_REVISION.IS_MERGE, false)
                .set(TIMELINE_REVISION.CREATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private String persistRevision(String revId, String projectId, String parentRevId, int revNumber,
            String payload) {
        String snapshotId = snapshotService.saveTx(dsl, projectId, null, payload, "internal-1.0");
        insertRevision(revId, projectId, parentRevId, revNumber, snapshotId);
        return snapshotId;
    }

    private ArtifactQueryService queryReturning(Artifact artifact) {
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        when(query.getArtifact(anyString(), any())).thenReturn(Optional.of(artifact));
        return query;
    }

    private static Artifact artifactFor(String artifactId, String digest) {
        return new Artifact(new ArtifactId(artifactId), TENANT,
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, digest), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                1, java.time.Instant.EPOCH);
    }

    private void buildServices(ArtifactQueryService query) {
        TimelineMergePreviewService preview = new TimelineMergePreviewService(new TimelineMergeConflictDetector());
        mergeEngine = new TimelineMergeEngine(
                new TimelineRevisionRepository(dsl),
                snapshotService,
                currentRevisionService,
                preview,
                new TimelineNonConflictingMergePlanner(preview),
                new TimelinePatchApplier(),
                InternalTimelineJson.mapper(),
                new TimelineArtifactPinValidator(query),
                pinService);
    }

    @Test
    void persistentMergeRegistersExactPinsForNewMergeRevision() {
        String productId = "prod-r5-merge-ok-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        insertArtifact("art-1", DIGEST_HEX);
        buildServices(queryReturning(artifactFor("art-1", DIGEST_HEX)));

        com.fasterxml.jackson.databind.ObjectMapper mapper = InternalTimelineJson.mapper();
        String baseRev = "base-rev";
        String sourceRev = "source-rev";
        String targetRev = "target-rev";
        persistRevision(baseRev, productId, null, 1,
                internalPayload(mapper, "tl-1", 1000L, "art-1", DIGEST_HEX));
        persistRevision(sourceRev, productId, baseRev, 2,
                internalPayload(mapper, "tl-1", 2000L, "art-1", DIGEST_HEX));
        persistRevision(targetRev, productId, baseRev, 3,
                internalPayload(mapper, "tl-1", 1000L, "art-1", DIGEST_HEX));
        currentRevisionService.updateCurrentRevision(productId, null, targetRev);

        TimelineMergeRequest request = new TimelineMergeRequest(
                productId, TENANT, baseRev, sourceRev, targetRev, "user", "merge");

        TimelineMergeResult result = mergeEngine.merge(request);
        assertEquals(TimelineMergeResult.MergeStatus.MERGED,
                result.status(), "merge must succeed: " + result.summary());
        assertNotNull(result.mergedRevisionId(), "merge must produce a NEW revision id");
        String mergeRevId = result.mergedRevisionId();

        assertEquals(1, dsl.fetchCount(DSL.selectFrom(TIMELINE_REVISION)
                        .where(TIMELINE_REVISION.ID.eq(mergeRevId))),
                "merge revision committed");

        var pins = dsl.selectFrom(ARTIFACT_PIN)
                .where(ARTIFACT_PIN.REVISION_ID.eq(mergeRevId))
                .fetch();
        assertEquals(1, pins.size(), "merge revision must carry its own pin rows");
        assertEquals("art-1", pins.get(0).get(ARTIFACT_PIN.ARTIFACT_ID), "exact artifact id");
        assertEquals(DIGEST_HEX, pins.get(0).get(ARTIFACT_PIN.CONTENT_DIGEST), "exact digest");

        assertEquals(mergeRevId, currentRevisionService.getCurrentRevisionId(productId),
                "head must advance to the merge revision");
    }

    @Test
    void persistentMergeRealPinDbFailureRollsBackEverything() {
        String productId = "prod-r5-merge-fail-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        insertArtifact("art-1", DIGEST_HEX); // base/source/target pins are real
        buildServices(queryReturning(artifactFor("art-1", DIGEST_HEX)));

        com.fasterxml.jackson.databind.ObjectMapper mapper = InternalTimelineJson.mapper();
        String baseRev = "base-rev";
        String sourceRev = "source-rev";
        String targetRev = "target-rev";
        persistRevision(baseRev, productId, null, 1,
                internalPayload(mapper, "tl-1", 1000L, "art-1", DIGEST_HEX));
        persistRevision(sourceRev, productId, baseRev, 2,
                internalPayload(mapper, "tl-1", 2000L, "art-1", DIGEST_HEX));
        persistRevision(targetRev, productId, baseRev, 3,
                internalPayload(mapper, "tl-1", 1000L, "art-1", DIGEST_HEX));
        currentRevisionService.updateCurrentRevision(productId, null, targetRev);

        // Real DB failure at the pin persistence layer inside a transaction
        // that also writes the would-be merge revision. The merge engine's
        // registration path (ArtifactPinService.registerRevisionPinsTx with the
        // transaction's own DSLContext) is exactly what the persistent merge
        // calls; a ghost artifact fails the real FK and must roll back both the
        // pin rows AND the merge revision write in the same physical
        // transaction.
        String mergeRevId = "merge-rev-" + java.util.UUID.randomUUID();
        assertThrows(Exception.class,
                () -> dsl.transactionResult(tx -> {
                    pinService.registerRevisionPinsTx(tx.dsl(), productId, mergeRevId,
                            TENANT, List.of(new ArtifactPinService.ArtifactPin(
                                    new ArtifactId("ghost-art"),
                                    new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, DIGEST_HEX))));
                    tx.dsl().insertInto(TIMELINE_REVISION)
                            .set(TIMELINE_REVISION.ID, mergeRevId)
                            .set(TIMELINE_REVISION.PROJECT_ID, productId)
                            .set(TIMELINE_REVISION.REVISION_NUMBER, 99)
                            .set(TIMELINE_REVISION.TENANT_ID, TENANT)
                            .set(TIMELINE_REVISION.SNAPSHOT_ID, "snap-x")
                            .set(TIMELINE_REVISION.INTERNAL_REVISION, 99)
                            .set(TIMELINE_REVISION.SOURCE, "merge")
                            .execute();
                    return null;
                }),
                "ghost pin INSERT must fail the merge transaction");

        assertEquals(0, dsl.fetchCount(DSL.selectFrom(TIMELINE_REVISION)
                        .where(TIMELINE_REVISION.ID.eq(mergeRevId))),
                "merge revision must roll back");
        assertEquals(0, dsl.fetchCount(DSL.selectFrom(ARTIFACT_PIN)
                        .where(ARTIFACT_PIN.REVISION_ID.eq(mergeRevId))),
                "no partial pin rows for the merge revision");
        assertEquals(targetRev, currentRevisionService.getCurrentRevisionId(productId),
                "head must remain at the last saved revision (merge rolled back)");
    }
}
