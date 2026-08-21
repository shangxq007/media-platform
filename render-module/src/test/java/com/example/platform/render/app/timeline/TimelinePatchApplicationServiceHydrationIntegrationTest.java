package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.PatchApplyResult;import com.example.platform.timeline.app.PatchPreviewResult;import com.example.platform.timeline.app.ProductCurrentRevisionService;import com.example.platform.timeline.app.TimelineCanonicalRejectionException;import com.example.platform.timeline.app.TimelineDocumentJsonSerializer;import com.example.platform.timeline.app.TimelinePatchApplicationService;import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.ProductCurrentRevisionHeadUpdateAdapter;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.patch.PatchApplicationResult;
import com.example.platform.timeline.patch.PatchErrorCode;
import com.example.platform.timeline.patch.TimelinePatch;
import com.example.platform.timeline.patch.TimelinePatchEngine;
import com.example.platform.timeline.patch.TimelinePatchOperation;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * PPHR-BIC authentic Patch payload-hydration integration test (real PostgreSQL
 * Testcontainers, real production/application boundary).
 *
 * <p>RED phase (pristine base 640f34b8): the valid-hydration scenarios fail with
 * TIMELINE_PATCH_PAYLOAD_INVALID — the exact confirmed defect
 * (PATCH_APPLICATION_PAYLOAD_HYDRATION_DEFECT: findById returns canonicalTimeline()==null
 * although the governed snapshot payload exists). The defect-characterization test
 * proves payload existence, findById null state, and zero durable writes.</p>
 *
 * <p>GREEN phase (after remediation): preview hydrates the persisted payload and performs
 * no writes; apply hydrates, passes the E1 canonical save gate, persists a new snapshot
 * and revision with the semantic change (clip startTime PT0S -> PT2S), updates the current
 * revision, and every rejection class remains zero-write.</p>
 */
class TimelinePatchApplicationServiceHydrationIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
    private TimelineSnapshotService snapshotService;
    private ProductCurrentRevisionService currentRevisionService;
    private TimelineContentDigester digester;

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
        TenantContext.set("ten-pphr");
        digester = new TimelineContentDigester();
        currentRevisionService = new ProductCurrentRevisionService(dsl);
        snapshotService = new TimelineSnapshotService(dsl);
        // PRODUCTION wiring: 4-arg constructor enables the Contract P snapshot payload write.
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService, digester, snapshotService,
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class), effectAuthority(), revisionSemanticContextStore(), new DefaultTimelineRevisionPersistence(), new ProductCurrentRevisionHeadUpdateAdapter(currentRevisionService));
    }

    // =====================================================================
    // GREEN: preview valid hydration (after remediation)
    // =====================================================================

    @Test
    void preview_validPersistedPayload_hydratesAndReturnsSemanticResult_noWrites() {
        String productId = "prod-prev-green-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = saveService.saveRevision(productId, null, docBase, "pphr-user");

        String baseSnapshotId = snapshotIdOf(base.revisionId());
        assertTrue(snapshotService.findPayload(baseSnapshotId).isPresent(),
                "governed payload exists (precondition)");

        TimelinePatch patch = validPatch(productId, base, docBase, "2/1");
        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);
        PatchPreviewResult result = patchService.preview(patch);

        assertTrue(result instanceof PatchPreviewResult.Success,
                "preview must hydrate the persisted payload and succeed (observed "
                        + (result instanceof PatchPreviewResult.Failure f ? f.error().code() : "?") + ")");
        String resultDigest = ((PatchPreviewResult.Success) result).resultDigest();
        assertNotNull(resultDigest);
        assertNotEquals(digester.digest(docBase), resultDigest,
                "semantic change (startTime PT0S -> PT2S) reflected in the result digest");

        // A027: preview performs no durable writes
        assertEquals(3L, countSnapshots(productId), "preview writes zero snapshot rows (base save's 3 governed rows)");
        assertEquals(1L, countRevisions(productId), "preview writes zero revision rows");
        assertEquals(base.revisionId(), currentRevisionService.getCurrentRevisionId(productId),
                "preview leaves the current revision untouched");
    }

    // =====================================================================
    // GREEN: apply valid hydration (after remediation)
    // =====================================================================

    @Test
    void apply_validPersistedPayload_hydratesAndPersistsThroughE1Gate() throws Exception {
        String productId = "prod-apply-green-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = saveService.saveRevision(productId, null, docBase, "pphr-user");
        String baseSnapshotId = snapshotIdOf(base.revisionId());
        String basePayload = snapshotService.findPayload(baseSnapshotId).orElseThrow();
        assertTrue(snapshotService.findPayload(baseSnapshotId).isPresent(), "base payload readable");

        TimelinePatch patch = validPatch(productId, base, docBase, "2/1");
        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);
        PatchApplyResult result = patchService.apply(patch);

        assertTrue(result instanceof PatchApplyResult.Success,
                "apply must hydrate and succeed through the E1 gate (observed "
                        + (result instanceof PatchApplyResult.Failure f ? f.error().code() : "?") + ")");
        PatchApplyResult.Success success = (PatchApplyResult.Success) result;
        String newRevisionId = success.newRevisionId();
        assertFalse(newRevisionId.equals(base.revisionId()), "new revision differs from base");
        assertEquals(base.revisionId(), success.parentRevisionId(), "parent lineage recorded");

        // A030: new snapshot persists (2 total); A031: new revision persists (2 total)
        assertEquals(6L, countSnapshots(productId), "new snapshot payload row persisted (base 3 + snap/revctx/esnap 3)");
        assertEquals(2L, countRevisions(productId), "new revision row persisted");
        // A032: current revision behavior
        assertEquals(newRevisionId, currentRevisionService.getCurrentRevisionId(productId),
                "current revision points to the accepted new revision");
        // A033: persisted payload contains the semantic change
        String newSnapshotId = snapshotIdOf(newRevisionId);
        assertNotNull(newSnapshotId);
        String newPayload = snapshotService.findPayload(newSnapshotId).orElseThrow();
        TimelineDocument persisted = TimelineDocumentJsonSerializer.mapper()
                .readValue(newPayload, TimelineDocument.class);
        assertEquals(MediaTime.ofRational(2, 1), persisted.getTracks().get(0).clips().get(0).getStartTime(),
                "persisted snapshot payload contains startTime PT2S");
        // A034: tenant/project identity preserved
        assertEquals(productId, persistedRowProject(newRevisionId), "project identity preserved");
        assertEquals("ten-pphr", persistedRowTenant(newRevisionId), "tenant identity preserved");
        // base revision and base snapshot remain readable
        assertNotNull(saveService.findById(base.revisionId()));
        assertTrue(snapshotService.findPayload(baseSnapshotId).isPresent(), "base payload still readable");
    }

    // =====================================================================
    // Rejection classes: zero writes + transaction usable afterwards
    // =====================================================================

    @Test
    void apply_missingBaseRevision_failsClosedZeroWrites() {
        String productId = "prod-miss-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = saveService.saveRevision(productId, null, docBase, "pphr-user");
        TimelinePatch patch = new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productId,
                "rev-does-not-exist", digester.digest(docBase), "rev-does-not-exist",
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "clip-1", "startTime", "0", "2/1")),
                null, null);
        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);
        PatchApplyResult result = patchService.apply(patch);
        assertTrue(result instanceof PatchApplyResult.Failure f
                && f.error().code() == PatchErrorCode.TIMELINE_PATCH_REVISION_NOT_FOUND);
        assertZeroWrites(productId, base.revisionId());
    }

    @Test
    void apply_missingSnapshotPayload_failsClosedZeroWrites() {
        // R5-C: the production constructor requires non-null dependencies; the
        // legacy "no snapshot authority -> no payload row" semantics are
        // reproduced with a snapshot service whose saveTx returns a snapshot id
        // that has NO payload row. A revision without a governed payload must
        // remain TIMELINE_PATCH_PAYLOAD_INVALID.
        com.example.platform.timeline.adapter.TimelineSnapshotService legacySnapshot =
                org.mockito.Mockito.mock(com.example.platform.timeline.adapter.TimelineSnapshotService.class);
        org.mockito.Mockito.when(legacySnapshot.saveTx(org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                        org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
                .thenReturn("snap-legacy-" + java.util.UUID.randomUUID());
        var legacySave = new TimelineRevisionSaveService(dsl, currentRevisionService, digester,
                legacySnapshot,
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class), effectAuthority(), revisionSemanticContextStore(), new DefaultTimelineRevisionPersistence(), new ProductCurrentRevisionHeadUpdateAdapter(currentRevisionService));
        String productId = "prod-nopay-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = legacySave.saveRevision(productId, null, docBase, "pphr-user");

        TimelinePatch patch = validPatch(productId, base, docBase, "2/1");
        var patchService = new TimelinePatchApplicationService(legacySave, currentRevisionService, digester);
        PatchApplyResult result = patchService.apply(patch);
        assertTrue(result instanceof PatchApplyResult.Failure f
                && f.error().code() == PatchErrorCode.TIMELINE_PATCH_PAYLOAD_INVALID,
                "missing governed payload must remain fail-closed PAYLOAD_INVALID");
        // Legacy 3-arg wiring never writes a snapshot payload row: zero snapshot rows,
        // one revision row, current unchanged (zero durable mutations).
        // Legacy snapshot mock never writes a payload row: only the revision
        // row + revctx row exist; patch failure adds NO new rows.
        assertEquals(2L, countSnapshots(productId), "zero NEW snapshot rows (legacy path adds none)");
        assertEquals(1L, countRevisions(productId), "zero new revision rows");
        assertEquals(base.revisionId(), currentRevisionService.getCurrentRevisionId(productId),
                "current revision unchanged");
    }

    @Test
    void apply_malformedSnapshotPayload_failsClosedZeroWrites() {
        String productId = "prod-badpay-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = saveService.saveRevision(productId, null, docBase, "pphr-user");
        // Corrupt the governed payload row: malformed JSON must fail closed.
        String snapshotId = snapshotIdOf(base.revisionId());
        dsl.update(TIMELINE_SNAPSHOT)
                .set(TIMELINE_SNAPSHOT.PAYLOAD_JSON, "{not-valid-json")
                .where(TIMELINE_SNAPSHOT.ID.eq(snapshotId))
                .execute();

        TimelinePatch patch = validPatch(productId, base, docBase, "2/1");
        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);
        PatchApplyResult result = patchService.apply(patch);
        assertTrue(result instanceof PatchApplyResult.Failure f
                && f.error().code() == PatchErrorCode.TIMELINE_PATCH_PAYLOAD_INVALID,
                "malformed governed payload must fail closed PAYLOAD_INVALID");
        assertZeroWrites(productId, base.revisionId());
    }

    @Test
    void apply_digestMismatch_failsClosedZeroWrites() {
        String productId = "prod-dig-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = saveService.saveRevision(productId, null, docBase, "pphr-user");
        TimelinePatch patch = new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productId,
                base.revisionId(), "wrong-digest", base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "clip-1", "startTime", "0", "2/1")),
                null, null);
        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);
        PatchApplyResult result = patchService.apply(patch);
        assertTrue(result instanceof PatchApplyResult.Failure f
                && f.error().code() == PatchErrorCode.TIMELINE_PATCH_BASE_DIGEST_MISMATCH);
        assertZeroWrites(productId, base.revisionId());
    }

    @Test
    void apply_crossProduct_failsClosedZeroWrites() {
        String productA = "prod-a-" + UUID.randomUUID();
        String productB = "prod-b-" + UUID.randomUUID();
        insertProduct(productA);
        insertProduct(productB);
        TimelineRevision base = saveService.saveRevision(productA, null,
                sampleDocument("clip-1", "0/1", "10/1"), "pphr-user");
        TimelinePatch patch = new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productB,
                base.revisionId(), digester.digest(sampleDocument("clip-1", "0/1", "10/1")), base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "clip-1", "startTime", "0", "2/1")),
                null, null);
        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);
        PatchApplyResult result = patchService.apply(patch);
        assertTrue(result instanceof PatchApplyResult.Failure f
                && f.error().code() == PatchErrorCode.TIMELINE_PATCH_CROSS_PRODUCT_NOT_ALLOWED);
        assertEquals(1L, countRevisions(productA));
        assertEquals(0L, countRevisions(productB));
    }

    @Test
    void apply_staleBaseAndExpectedCurrentConflict_failClosedZeroWrites() {
        String productId = "prod-stale-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision r1 = saveService.saveRevision(productId, null, docBase, "pphr-user");
        TimelineRevision r2 = saveService.saveRevision(productId, r1.revisionId(),
                sampleDocument("clip-1", "0/1", "5/1"), "pphr-user");
        assertEquals(r2.revisionId(), currentRevisionService.getCurrentRevisionId(productId));

        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);

        TimelinePatch stalePatch = new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productId,
                r1.revisionId(), digester.digest(docBase), r1.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "clip-1", "startTime", "0", "2/1")),
                null, null);
        PatchApplyResult stale = patchService.apply(stalePatch);
        assertTrue(stale instanceof PatchApplyResult.Failure f
                && f.error().code() == PatchErrorCode.TIMELINE_PATCH_BASE_NOT_CURRENT);
        assertEquals(2L, countRevisions(productId));

        TimelinePatch conflictPatch = new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productId,
                r2.revisionId(), digester.digest(sampleDocument("clip-1", "0/1", "5/1")), r1.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "clip-1", "startTime", "0", "2/1")),
                null, null);
        PatchApplyResult conflict = patchService.apply(conflictPatch);
        assertTrue(conflict instanceof PatchApplyResult.Failure f
                && f.error().code() == PatchErrorCode.TIMELINE_PATCH_REVISION_CONFLICT);
        assertEquals(2L, countRevisions(productId));
    }

    @Test
    void apply_canonicalInvalidResult_failsClosedZeroWrites() throws Exception {
        // Component-level (same as PE1C G1-G5): the mutation passes the ENGINE but is
        // canonically invalid (startTime PT0S -> PT15S while endTime stays PT10S).
        String productId = "prod-inv-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = saveService.saveRevision(productId, null, docBase, "pphr-user");

        TimelinePatch patch = new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productId,
                base.revisionId(), digester.digest(docBase), base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "clip-1", "startTime", "0", "15/1")),
                null, null);
        TimelineDocument baseDoc = TimelineDocumentJsonSerializer.mapper()
                .readValue(snapshotService.findPayload(snapshotIdOf(base.revisionId())).orElseThrow(),
                        TimelineDocument.class);
        PatchApplicationResult engineResult = TimelinePatchEngine.apply(baseDoc, patch);
        assertTrue(engineResult.isSuccess(), "engine applies the timing op (canonically invalid result)");
        TimelineDocument invalidDoc = ((PatchApplicationResult.Success) engineResult).document();
        assertEquals(MediaTime.ofRational(15, 1),
                invalidDoc.getTracks().get(0).clips().get(0).getStartTime());

        assertThrows(TimelineCanonicalRejectionException.class,
                () -> saveService.saveRevision(productId, base.revisionId(), invalidDoc, "patch-service"));
        assertZeroWrites(productId, base.revisionId());
    }

    @Test
    void apply_malformedOperation_failsClosedZeroWrites() {
        String productId = "prod-mal-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = saveService.saveRevision(productId, null, docBase, "pphr-user");
        TimelinePatch patch = new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productId,
                base.revisionId(), digester.digest(docBase), base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "clip-NOPE", "startTime", "0", "2/1")),
                null, null);
        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);
        PatchApplyResult result = patchService.apply(patch);
        assertTrue(result.isFailure(), "malformed op (unknown clip) must be a failure");
        assertZeroWrites(productId, base.revisionId());
    }

    @Test
    void subsequentValidApply_afterRejection_succeedsWithoutGap() {
        String productId = "prod-usable-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument docBase = sampleDocument("clip-1", "0/1", "10/1");
        TimelineRevision base = saveService.saveRevision(productId, null, docBase, "pphr-user");

        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService, digester);
        TimelinePatch badPatch = new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productId,
                base.revisionId(), "wrong-digest", base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "clip-1", "startTime", "0", "2/1")),
                null, null);
        assertTrue(patchService.apply(badPatch).isFailure(), "rejected request first");

        TimelinePatch goodPatch = validPatch(productId, base, docBase, "2/1");
        PatchApplyResult result = patchService.apply(goodPatch);
        assertTrue(result instanceof PatchApplyResult.Success,
                "subsequent valid Patch succeeds after rejection (observed "
                        + (result instanceof PatchApplyResult.Failure f ? f.error().code() : "?") + ")");
        assertEquals(2L, countRevisions(productId), "no revision gap");
        assertEquals(((PatchApplyResult.Success) result).newRevisionId(),
                currentRevisionService.getCurrentRevisionId(productId));
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private TimelinePatch validPatch(String productId, TimelineRevision base,
                                     TimelineDocument docBase, String newStart) {
        return new TimelinePatch("1.0", "patch-" + UUID.randomUUID(), productId,
                base.revisionId(), digester.digest(docBase), base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty(
                        "op1", "clip-1", "startTime", docBase.getTracks().get(0).clips().get(0)
                                .getStartTime().toString(), newStart)),
                null, null);
    }

    private void assertZeroWrites(String productId, String currentRevisionId) {
        assertEquals(3L, countSnapshots(productId), "zero new snapshot rows (3 governed rows from the base save)");
        assertEquals(1L, countRevisions(productId), "zero new revision rows");
        assertEquals(currentRevisionId, currentRevisionService.getCurrentRevisionId(productId),
                "current revision unchanged");
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

    private TimelineDocument sampleDocument(String clipId, String start, String end) {
        TimelineClip clip = new TimelineClip(clipId, "asset-1", null, null, null,
                parseMediaTime(start), parseMediaTime(end), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineTrack track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }

    private String snapshotIdOf(String revisionId) {
        return dsl.select(TIMELINE_REVISION.SNAPSHOT_ID).from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId)).fetchOne(TIMELINE_REVISION.SNAPSHOT_ID);
    }

    private long countSnapshots(String productId) {
        return dsl.selectCount().from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
    }

    private long countRevisions(String productId) {
        return dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
    }

    private String persistedRowProject(String revisionId) {
        return dsl.select(TIMELINE_REVISION.PROJECT_ID).from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId)).fetchOne(TIMELINE_REVISION.PROJECT_ID);
    }

    private String persistedRowTenant(String revisionId) {
        return dsl.select(TIMELINE_REVISION.TENANT_ID).from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId)).fetchOne(TIMELINE_REVISION.TENANT_ID);
    }


    private static MediaTime parseMediaTime(String text) {
        if ("0".equals(text)) {
            return MediaTime.ZERO;
        }
        int slash = text.indexOf('/');
        if (slash < 1 || slash == text.length() - 1) {
            throw new IllegalArgumentException("Invalid exact MediaTime: " + text);
        }
        long num = Long.parseLong(text.substring(0, slash).trim());
        long den = Long.parseLong(text.substring(slash + 1).trim());
        if (den <= 0) {
            throw new IllegalArgumentException("MediaTime denominator must be > 0: " + text);
        }
        return MediaTime.ofRational(num, den);
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
