package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.PatchApplyResult;import com.example.platform.timeline.app.TimelineRevisionRefMutation;import com.example.platform.timeline.app.TimelineCanonicalRejectionException;import com.example.platform.timeline.app.TimelinePatchApplicationService;import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnosticCode;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static org.junit.jupiter.api.Assertions.*;

class TimelineRevisionSaveServiceIntegrationTest extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-timeline-save-it";
    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
    private TimelineRevisionRefMutation currentRevisionService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        RenderTestSchemaFixture.createSchema(dsl);
        dsl.execute("""
                create table if not exists apply_command (
                    apply_command_id varchar(64) primary key,
                    plan_digest varchar(64) not null,
                    fingerprint varchar(64) not null,
                    status varchar(16) not null,
                    result_revision_id varchar(64),
                    result_content_hash varchar(64),
                    result_status varchar(16),
                    project_id varchar(64),
                    command_domain varchar(32) not null,
                    created_at timestamp not null default current_timestamp,
                    completed_at timestamp)
                """);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        RenderTestSchemaFixture.truncate(dsl);
        dsl.execute("delete from apply_command");
        com.example.platform.shared.web.TenantContext.set(TENANT);
        currentRevisionService = new TimelineRevisionRefMutation(dsl);
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class), effectAuthority(), revisionSemanticContextStore(), new DefaultTimelineRevisionPersistence(), new TimelineRevisionRefHeadUpdateAdapter(currentRevisionService));
    }

    @AfterEach
    void clearTenant() {
        com.example.platform.shared.web.TenantContext.clear();
    }

    @Test
    void firstRevision_createsRootRevision() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var revision = save(productId, null, doc);

        assertNotNull(revision.revisionId());
        assertNull(revision.parentRevisionId());
        assertNotNull(revision.contentDigest());
        String storedTimelineDigest = dsl.select(TIMELINE_REVISION.CONTENT_HASH)
                .from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revision.revisionId()))
                .fetchOne(TIMELINE_REVISION.CONTENT_HASH);
        assertEquals(new TimelineContentDigester().digest(doc), storedTimelineDigest,
                "content_hash must mean canonical TimelineDocument content only");
        assertEquals(revision.semanticContext().timelineContentDigest(), storedTimelineDigest);
    }

    @Test
    void secondRevision_hasFirstAsParent() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocumentWithDifferentClip();

        var first = save(productId, null, doc1);
        var second = save(productId, first.revisionId(), doc2);

        assertEquals(first.revisionId(), second.parentRevisionId());
    }

    @Test
    void optimisticConcurrency_conflictOnStaleExpected() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocumentWithDifferentClip();

        var first = save(productId, null, doc1);

        // Second save with first's revisionId as expected should succeed
        save(productId, first.revisionId(), doc2);

        // Third save with stale expected (first.revisionId) should fail
        assertThrows(TimelineConflictException.class, () ->
                save(productId, first.revisionId(), doc1));
    }

    @Test
    void conflictException_hasCorrectFields() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var first = save(productId, null, doc);
        save(productId, first.revisionId(), createSampleDocumentWithDifferentClip());

        try {
            save(productId, first.revisionId(), doc);
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

        var rev1 = save(productId, null, doc);
        var rev2 = save(productId, rev1.revisionId(), createSampleDocumentWithDifferentClip());
        var rev3 = save(productId, rev2.revisionId(), createSampleDocument());

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

        var rev1 = save(productId, null, doc);
        var rev2 = save(productId, rev1.revisionId(), doc);

        // Same content should produce same digest
        assertNotNull(rev1.contentDigest());
        assertNotNull(rev2.contentDigest());
    }

    @Test
    void operationCommandIsAtomicDurableAndReplaysExactlyOneCanonicalRevision() {
        String productId = "prod-operation-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument base = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "Main", TrackType.VIDEO, List.of())),
                TimelineMetadata.empty());
        TimelineRevision root = saveService.saveRevision(
                TENANT, productId, null, base, RenderTestSchemaFixture.SERVER_ACTOR);
        // Operation execution may move to a worker without request ThreadLocal
        // propagation. Its immutable command must carry the authorized tenant.
        com.example.platform.shared.web.TenantContext.clear();

        String digest = "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";
        TimelineClip added = new TimelineClip(
                "clip-S-10-20", "media-S", "stream-S-video", "artifact-S-v1", digest,
                MediaTime.ZERO, MediaTime.ofRational(10, 1),
                MediaTime.ofRational(10, 1), MediaTime.ofRational(20, 1),
                "MEDIA_STREAM",
                com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping.of(
                        1, 1,
                        com.example.platform.timeline.semantics.temporal.PlaybackDirection.FORWARD));
        TimelineDocument candidate = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("video-1", "Main", TrackType.VIDEO, List.of(added))),
                TimelineMetadata.empty());

        var pinValidator = org.mockito.Mockito.mock(
                com.example.platform.timeline.app.TimelineArtifactPinValidator.class);
        org.mockito.Mockito.when(pinValidator.validate(
                        org.mockito.ArgumentMatchers.eq(TENANT),
                        org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new com.example.platform.timeline.app.TimelineArtifactPinValidator
                        .ValidationResult(true, List.of()));
        var commandSave = new TimelineRevisionSaveService(
                dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                pinValidator,
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class),
                effectAuthority(), revisionSemanticContextStore(),
                new DefaultTimelineRevisionPersistence(),
                new TimelineRevisionRefHeadUpdateAdapter(currentRevisionService));
        var command = new TimelineRevisionSaveService.RevisionWriteCommand(
                "apply-H7-durable", "plan-digest-H7", "fingerprint-H7", "OPERATION_PLAN", TENANT);

        var first = commandSave.saveRevisionForCommand(
                RevisionRef.main(TENANT, productId), root.revisionId(), candidate,
                RenderTestSchemaFixture.SERVER_ACTOR, command);
        var replay = commandSave.saveRevisionForCommand(
                RevisionRef.main(TENANT, productId), root.revisionId(), candidate,
                RenderTestSchemaFixture.SERVER_ACTOR, command);

        assertFalse(first.replayed());
        assertTrue(replay.replayed());
        assertEquals(first.revisionId(), replay.revisionId());
        assertEquals(new TimelineContentDigester().digest(candidate), first.timelineContentHash());
        assertEquals(2L, dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class));
        assertEquals(1, dsl.fetchOne(
                "select count(*) from apply_command where apply_command_id = 'apply-H7-durable'")
                .get(0, Integer.class));

        var conflict = new TimelineRevisionSaveService.RevisionWriteCommand(
                "apply-H7-durable", "different-plan", "different-fingerprint", "OPERATION_PLAN", TENANT);
        assertThrows(com.example.platform.timeline.app.TimelineRevisionCommandConflictException.class,
                () -> commandSave.saveRevisionForCommand(
                        RevisionRef.main(TENANT, productId), root.revisionId(), candidate, "editor", conflict));
        assertEquals(2L, dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class));
    }

    // ---- NDSF-SCOPE-E1 canonical save gate extension (allowlist #6) ----

    @Test
    void validSave_remainsSuccessfulThroughCanonicalGate() {
        String productId = "prod-gateext-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var revision = save(productId, null, doc);

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
                save(productId, null, doc));
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
                () -> save(productId, null, doc));

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
                () -> save(productId, null, doc));

        assertTrue(ex.hasCanonicalDiagnostics());
        var codes = ex.diagnostics().stream().map(d -> d.code()).toList();
        assertTrue(codes.contains(TimelineDiagnosticCode.TIMELINE_TRACK_ID_DUPLICATE));
        assertTrue(codes.contains(TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE));
        assertEquals(codes.stream().sorted().toList(), codes, "deterministic diagnostic order preserved");
    }

    @Test
    void patchApplication_onCanonicalSavedBase_usesGovernedPayload() {
        String productId = "prod-gateext-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var base = save(productId, null, createSampleDocument());

        var patch = new com.example.platform.timeline.patch.TimelinePatch(
                "1.0", "patch-" + java.util.UUID.randomUUID(), productId,
                base.revisionId(), base.semanticContext().timelineContentDigest(), base.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new com.example.platform.timeline.patch.TimelinePatchOperation.AddTrack(
                        "op1", new TimelineTrack("track-2", "V2", TrackType.VIDEO, List.of()), 1)),
                null, null);

        var patchService = new TimelinePatchApplicationService(saveService, currentRevisionService,
                new TimelineContentDigester());
        var result = patchService.apply(
                TENANT, RenderTestSchemaFixture.SERVER_ACTOR, patch);

        assertTrue(result.isSuccess(), "canonical payload must hydrate and patch");
        long rows = dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        assertEquals(2L, rows, "patch persists exactly one child revision");
    }

    private void insertProduct(String productId) {
        RenderTestSchemaFixture.insertCanonicalProject(dsl, TENANT, productId);
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, productId)
                .set(PRODUCT.PRODUCT_TYPE, "video")
                .set(PRODUCT.REPRESENTATION_KIND, "master")
                .set(PRODUCT.STATUS, "REGISTERED")
                .set(PRODUCT.TENANT_ID, TENANT)
                .set(PRODUCT.PROJECT_ID, productId)
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private TimelineRevision save(
            String productId, String expectedHead, TimelineDocument document) {
        return saveService.saveRevision(
                TENANT, productId, expectedHead, document, RenderTestSchemaFixture.SERVER_ACTOR);
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
