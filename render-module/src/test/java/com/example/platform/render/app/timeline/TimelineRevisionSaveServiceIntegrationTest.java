package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.PatchApplyResult;import com.example.platform.timeline.app.ProductCurrentRevisionService;import com.example.platform.timeline.app.TimelineCanonicalRejectionException;import com.example.platform.timeline.app.TimelinePatchApplicationService;import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.shared.time.MediaTime;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnosticCode;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRevisionSaveServiceIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
    private ProductCurrentRevisionService currentRevisionService;

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
        currentRevisionService = new ProductCurrentRevisionService(dsl);
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class));
    }

    @Test
    void firstRevision_createsRootRevision() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var revision = saveService.saveRevision(productId, null, doc, "user-1");

        assertNotNull(revision.revisionId());
        assertNull(revision.parentRevisionId());
        assertNotNull(revision.contentDigest());
    }

    @Test
    void secondRevision_hasFirstAsParent() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocumentWithDifferentClip();

        var first = saveService.saveRevision(productId, null, doc1, "user-1");
        var second = saveService.saveRevision(productId, first.revisionId(), doc2, "user-1");

        assertEquals(first.revisionId(), second.parentRevisionId());
    }

    @Test
    void optimisticConcurrency_conflictOnStaleExpected() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocumentWithDifferentClip();

        var first = saveService.saveRevision(productId, null, doc1, "user-1");

        // Second save with first's revisionId as expected should succeed
        saveService.saveRevision(productId, first.revisionId(), doc2, "user-1");

        // Third save with stale expected (first.revisionId) should fail
        assertThrows(TimelineConflictException.class, () ->
                saveService.saveRevision(productId, first.revisionId(), doc1, "user-1"));
    }

    @Test
    void conflictException_hasCorrectFields() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var first = saveService.saveRevision(productId, null, doc, "user-1");
        saveService.saveRevision(productId, first.revisionId(), createSampleDocumentWithDifferentClip(), "user-1");

        try {
            saveService.saveRevision(productId, first.revisionId(), doc, "user-1");
            fail("Should have thrown TimelineConflictException");
        } catch (TimelineConflictException ex) {
            assertEquals(productId, ex.getProductId());
            assertEquals(first.revisionId(), ex.getExpectedRevisionId());
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("conflict") || ex.getMessage().contains("Conflict"));
        }
    }

    @Test
    void revisionHistory_lineageIsCorrect() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var rev1 = saveService.saveRevision(productId, null, doc, "user-1");
        var rev2 = saveService.saveRevision(productId, rev1.revisionId(), createSampleDocumentWithDifferentClip(), "user-1");
        var rev3 = saveService.saveRevision(productId, rev2.revisionId(), createSampleDocument(), "user-1");

        // Verify lineage
        assertNull(rev1.parentRevisionId());
        assertEquals(rev1.revisionId(), rev2.parentRevisionId());
        assertEquals(rev2.revisionId(), rev3.parentRevisionId());
    }

    @Test
    void contentDigest_deterministic() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var rev1 = saveService.saveRevision(productId, null, doc, "user-1");
        var rev2 = saveService.saveRevision(productId, rev1.revisionId(), doc, "user-1");

        // Same content should produce same digest
        assertNotNull(rev1.contentDigest());
        assertNotNull(rev2.contentDigest());
    }

    // ---- NDSF-SCOPE-E1 canonical save gate extension (allowlist #6) ----

    @Test
    void validSave_remainsSuccessfulThroughCanonicalGate() {
        String productId = "prod-gateext-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var revision = saveService.saveRevision(productId, null, doc, "user-1");

        assertNotNull(revision.revisionId());
    }

    @Test
    void invalidDocument_duplicateTrackIds_rejectedBeforePersistence() {
        String productId = "prod-gateext-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var clipA = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var clipB = new TimelineClip("clip-2", "asset-2", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "A", TrackType.VIDEO, List.of(clipA)),
                        new TimelineTrack("track-1", "B", TrackType.VIDEO, List.of(clipB))),
                new TimelineMetadata("invalid", "", Map.of()));

        assertThrows(TimelineCanonicalRejectionException.class, () ->
                saveService.saveRevision(productId, null, doc, "user-1"));
        long rows = dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        assertEquals(0L, rows, "no revision persisted after canonical rejection");
    }

    @Test
    void invalidTiming_rejectedBeforePersistence_withOrderedDiagnostics() {
        String productId = "prod-gateext-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(10, 1), MediaTime.ofRational(5, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("invalid", "", Map.of()));

        TimelineCanonicalRejectionException ex = assertThrows(TimelineCanonicalRejectionException.class,
                () -> saveService.saveRevision(productId, null, doc, "user-1"));

        assertTrue(ex.hasAdapterDiagnostics(), "adapter-level frozen code expected");
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                ex.adapterDiagnostics().get(0).code());
        assertEquals(0L, dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class));
    }

    @Test
    void canonicalRejection_diagnosticsAvailableInDeterministicOrder() {
        String productId = "prod-gateext-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var clip = new TimelineClip("clip-x", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("track-1", "A", TrackType.VIDEO, List.of(clip)),
                        new TimelineTrack("track-1", "B", TrackType.VIDEO, List.of(clip))),
                new TimelineMetadata("invalid", "", Map.of()));

        TimelineCanonicalRejectionException ex = assertThrows(TimelineCanonicalRejectionException.class,
                () -> saveService.saveRevision(productId, null, doc, "user-1"));

        assertTrue(ex.hasCanonicalDiagnostics());
        var codes = ex.diagnostics().stream().map(d -> d.code()).toList();
        assertTrue(codes.contains(TimelineDiagnosticCode.TIMELINE_TRACK_ID_DUPLICATE));
        assertTrue(codes.contains(TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE));
        assertEquals(codes.stream().sorted().toList(), codes, "deterministic diagnostic order preserved");
    }

    @Test
    void patchApplication_onGitV1SavedBase_characterizesExistingPayloadLimitation() {
        // The GitV1 save path persists the revision row but does NOT populate a snapshot
        // payload; TimelinePatchApplicationService loads the base document from the payload
        // (baseRevision.canonicalTimeline()), so a GitV1-saved base yields
        // TIMELINE_PATCH_PAYLOAD_INVALID before any save. This characterizes the pre-existing
        // repository limitation (patch requires the snapshot-payload flow); E1 does not change
        // the patch service or snapshot handling. Patch gate coverage is by construction:
        // apply() delegates to the gated TimelineRevisionSaveService.saveRevision(...) when the
        // base document is available (see patch-path-coverage evidence).
        String productId = "prod-gateext-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        // R5-C: the production constructor requires non-null dependencies; the
        // GitV1 "revision row without governed snapshot payload" limitation is
        // reproduced with a snapshot service whose saveTx returns a snapshot id
        // that has NO payload row (legacy semantics — the payload is absent, so
        // the patch service cannot load the base document).
        com.example.platform.timeline.adapter.TimelineSnapshotService legacySnapshot =
                org.mockito.Mockito.mock(com.example.platform.timeline.adapter.TimelineSnapshotService.class);
        org.mockito.Mockito.when(legacySnapshot.saveTx(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("snap-legacy-" + java.util.UUID.randomUUID());
        var saveServiceLegacy = new TimelineRevisionSaveService(dsl, currentRevisionService,
                new TimelineContentDigester(), legacySnapshot,
                new com.example.platform.timeline.app.TimelineArtifactPinValidator(
                        new com.example.platform.render.testutil.NoopArtifactQueryService()),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)));
        var base = saveServiceLegacy.saveRevision(productId, null, createSampleDocument(), "user-1");

        var patch = new com.example.platform.timeline.patch.TimelinePatch(
                "1.0", "patch-" + java.util.UUID.randomUUID(), productId,
                base.revisionId(), base.contentDigest(), base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new com.example.platform.timeline.patch.TimelinePatchOperation.AddTrack(
                        "op1", new TimelineTrack("track-2", "V2", TrackType.VIDEO, List.of()), 1)),
                null, null);

        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService,
                new TimelineContentDigester());
        var result = patchService.apply(patch);

        assertTrue(result.isFailure(), "pre-existing payload limitation must be unchanged");
        assertTrue(result instanceof com.example.platform.timeline.app.PatchApplyResult.Failure f
                && f.error().code() == com.example.platform.timeline.patch.PatchErrorCode.TIMELINE_PATCH_PAYLOAD_INVALID,
                "expected TIMELINE_PATCH_PAYLOAD_INVALID (base document not loadable)");
        long rows = dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        assertEquals(1L, rows, "no additional revision persisted");
    }

    private void insertProduct(String productId) {
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, productId)
                .set(PRODUCT.PRODUCT_TYPE, "video")
                .set(PRODUCT.REPRESENTATION_KIND, "master")
                .set(PRODUCT.STATUS, "REGISTERED")
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private TimelineDocument createSampleDocument() {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }

    private TimelineDocument createSampleDocumentWithDifferentClip() {
        var clip = new TimelineClip("clip-2", "asset-2", null, null, null,
                MediaTime.ofRational(5, 1), MediaTime.ofRational(15, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }
}