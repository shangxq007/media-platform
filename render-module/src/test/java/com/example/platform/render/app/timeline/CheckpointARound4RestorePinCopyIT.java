package com.example.platform.render.app.timeline;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;

import com.example.platform.artifact.app.ArtifactPinService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.app.TimelineRevisionRefMutation;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CHECKPOINT_A Round 4 (R4-D1): restoreRevision REAL-PostgreSQL pin-copy proof.
 *
 * <p>A restored revision is a DISTINCT revision identity; per
 * (revisionId, artifactId) protection semantics it MUST gain its own pin rows,
 * copied from the historical revision's immutable pins in the SAME transaction.
 *
 * <p>Failure: pin-copy failure rolls back the whole restore (new revision,
 * copied pins, head, snapshot state) — nothing partial remains.
 */
class CheckpointARound4RestorePinCopyIT extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-r4-restore";
    private static final String DIGEST_HEX = "a".repeat(64);

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
    private TimelineRevisionRefMutation currentRevisionService;
    private ArtifactPinRepository pinRepository;
    private ArtifactPinService pinService;
    private TimelineSnapshotService snapshotService;

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
        currentRevisionService = new TimelineRevisionRefMutation(dsl);
        snapshotService = new TimelineSnapshotService(dsl);
        pinRepository = new ArtifactPinRepository(dsl);
        pinService = new ArtifactPinService(pinRepository);
        TenantContext.set(TENANT);
    }

    private void insertProduct(String productId) {
        RenderTestSchemaFixture.insertCanonicalProject(dsl, TENANT, productId);
        dsl.insertInto(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT)
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.PRODUCT_ID, productId)
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.PRODUCT_TYPE, "video")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.REPRESENTATION_KIND, "master")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.STATUS, "REGISTERED")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.TENANT_ID, TENANT)
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.PROJECT_ID, productId)
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
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

    private static TimelineDocument pinnedDoc(String artifactId, String digest) {
        TimelineClip clip = new TimelineClip(
                "c1", "asset-1", "stream-1", artifactId, digest,
                MediaTime.ZERO, MediaTime.ofTicks(30, 1),
                MediaTime.ZERO, MediaTime.ofTicks(30, 1), "MEDIA_STREAM", null);
        TimelineTrack track = new TimelineTrack("v1", "v1", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());
    }

    private ArtifactQueryService validQuery() {
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = new Artifact(new ArtifactId("art-1"), TENANT,
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, DIGEST_HEX), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                1, java.time.Instant.EPOCH);
        when(query.getArtifact(anyString(), any())).thenReturn(Optional.of(artifact));
        return query;
    }

    private void buildSaveService(ArtifactQueryService query, ArtifactPinService pinSvc) {
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService,
                new TimelineContentDigester(), snapshotService,
                new TimelineArtifactPinValidator(query), pinSvc, effectAuthority(), revisionSemanticContextStore(), new DefaultTimelineRevisionPersistence(), new TimelineRevisionRefHeadUpdateAdapter(currentRevisionService));
    }

    private long countPinsFor(String projectId, String revisionId) {
        return dsl.fetchCount(DSL.selectFrom(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.PROJECT_ID.eq(projectId))
                .and(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.REVISION_ID.eq(revisionId)));
    }

    @Test
    void restoreCopiesExactPinsToNewRevision() {
        String productId = "prod-restore-" + java.util.UUID.randomUUID();
        String artifactId = java.util.UUID.randomUUID().toString();
        insertProduct(productId);
        insertArtifact(artifactId, DIGEST_HEX);
        buildSaveService(validQuery(), pinService);

        // historical pinned revision
        var historical = saveService.saveRevision(
                TENANT, productId, null, pinnedDoc(artifactId, DIGEST_HEX),
                RenderTestSchemaFixture.SERVER_ACTOR);
        assertEquals(1, countPinsFor(productId, historical.revisionId()),
                "historical revision must have its pin");
        String historicalPin = dsl.select(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.ARTIFACT_ID)
                .from(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.REVISION_ID.eq(historical.revisionId()))
                .fetchOne(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.ARTIFACT_ID);

        // restore → NEW revision identity
        var restored = saveService.restoreRevision(
                TENANT, productId, historical.revisionId(), historical.revisionId(),
                RenderTestSchemaFixture.SERVER_ACTOR);
        assertNotNull(restored);
        assertTrue(!restored.revisionId().equals(historical.revisionId()),
                "restore must create a NEW revision id");

        // new revision has its own exact copied pin
        assertEquals(1, countPinsFor(productId, restored.revisionId()),
                "restored revision must gain its own pin rows");
        String restoredPin = dsl.select(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.ARTIFACT_ID)
                .from(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.REVISION_ID.eq(restored.revisionId()))
                .fetchOne(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.ARTIFACT_ID);
        assertEquals(historicalPin, restoredPin, "exact pin contract copied (immutable, no re-resolution)");
        String restoredPinId = dsl.select(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.PIN_ID)
                .from(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.REVISION_ID.eq(restored.revisionId()))
                .fetchOne(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.PIN_ID);
        assertNotNull(restoredPinId);
        assertTrue(restoredPinId.length() <= 64,
                "typed deterministic pin identity must fit canonical varchar(64)");

        // old pins remain
        assertEquals(1, countPinsFor(productId, historical.revisionId()),
                "historical pins must remain");

        // head points to restored revision
        assertEquals(restored.revisionId(), currentRevisionService.currentHead(dsl, com.example.platform.timeline.revisioncommand.RevisionRef.main(com.example.platform.shared.web.TenantContext.get(), productId)),
                "head must point to the restored revision");
    }

    @Test
    void restorePinCopyFailureRollsBackEverything() {
        String productId = "prod-restore-fail-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        insertArtifact("art-1", DIGEST_HEX);
        buildSaveService(validQuery(), pinService);
        var historical = saveService.saveRevision(
                TENANT, productId, null, pinnedDoc("art-1", DIGEST_HEX),
                RenderTestSchemaFixture.SERVER_ACTOR);
        String headBefore = currentRevisionService.currentHead(dsl, com.example.platform.timeline.revisioncommand.RevisionRef.main(com.example.platform.shared.web.TenantContext.get(), productId));

        // pin-copy failure: a service whose copyRevisionPinsTx throws
        ArtifactPinService failing = mock(ArtifactPinService.class);
        org.mockito.Mockito.doThrow(new IllegalStateException("simulated pin copy failure"))
                .when(failing).copyRevisionPinsTx(
                        any(), anyString(), anyString(), anyString(), anyString());

        buildSaveService(validQuery(), failing);

        assertThrows(IllegalStateException.class,
                () -> saveService.restoreRevision(
                        TENANT, productId, historical.revisionId(), historical.revisionId(),
                        RenderTestSchemaFixture.SERVER_ACTOR),
                "pin-copy failure must fail the restore");

        // no new restore revision
        assertEquals(1, dsl.fetchCount(DSL.selectFrom(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))),
                "only the historical revision may remain");
        // head unchanged
        assertEquals(headBefore, currentRevisionService.currentHead(dsl, com.example.platform.timeline.revisioncommand.RevisionRef.main(com.example.platform.shared.web.TenantContext.get(), productId)),
                "head must be unchanged after restore rollback");
        // no partial copied pins for any other revision
        assertEquals(1, dsl.fetchCount(DSL.selectFrom(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.PROJECT_ID.eq(productId))),
                "only the historical pin row may remain (no partial new pins)");
    }
    private com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority effectAuthority() {
        // AI14/AI15: production authority wiring — durable Jdbc store + registry.
        return new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority(
                new com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry(dsl),
                new com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore(dsl));
    }

    private static com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore revisionSemanticContextStore() {
        return new com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore(dsl);
    }


}
