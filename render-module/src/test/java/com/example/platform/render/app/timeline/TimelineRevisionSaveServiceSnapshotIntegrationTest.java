package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.ProductCurrentRevisionService;import com.example.platform.timeline.app.TimelineCanonicalRejectionException;import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.shared.time.MediaTime;

import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.timeline.version.TimelineConflictException;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
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

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract P snapshot-payload integration tests (real PostgreSQL Testcontainers).
 * Proves: valid E1 save writes the governed payload row; rejection leaves zero
 * partial rows; restore copies the payload; the saved payload resolves through
 * the existing snapshot authority and is render-parseable.
 */
class TimelineRevisionSaveServiceSnapshotIntegrationTest extends PostgresTestContainerSupport {

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
        digester = new TimelineContentDigester();
        currentRevisionService = new ProductCurrentRevisionService(dsl);
        snapshotService = new TimelineSnapshotService(dsl);
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService, digester, snapshotService,
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class), effectAuthority(), revisionSemanticContextStore());
    }

    @Test
    void validSave_writesSnapshotPayload_throughSoleAuthority() {
        String productId = "prod-snap-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var revision = saveService.saveRevision(productId, null, doc, "snap-user");

        // Exactly one snapshot payload row for the project.
        long snapshotRows = dsl.selectCount().from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        // ROADMAP20 authority-integration: exactly THREE governed rows — the
        // timeline snapshot payload + the durable Effect snapshot (esnap_) +
        // the revision semantic context (revctx_).
        assertEquals(3L, snapshotRows, "valid E1 save must write exactly 3 governed rows (snap + esnap + revctx)");
        // Revision references the payload row (the payload row id, not the revision id).
        String persistedSnapshotId = dsl.select(TIMELINE_REVISION.SNAPSHOT_ID).from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revision.revisionId())).fetchOne(TIMELINE_REVISION.SNAPSHOT_ID);
        assertTrue(persistedSnapshotId.startsWith("snap"),
                "SNAPSHOT_ID must reference the payload row written by TimelineSnapshotService");
        // Payload present and digest-equivalent.
        Optional<String> payload = snapshotService.findPayload(persistedSnapshotId);
        assertTrue(payload.isPresent(), "revision must never be visible without its payload");
        assertEquals(digester.digest(doc), revision.semanticContext().timelineContentDigest(),
                "contentDigest is the FULL revision semantic digest; the Timeline-only digest lives in the context");
        // One revision row, current pointer updated.
        long revisionRows = dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        assertEquals(1L, revisionRows);
    }

    @Test
    void invalidSave_leavesZeroPartialRows() {
        String productId = "prod-invalid-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var invalid = createDocumentWithDuplicateTrackIds();

        assertThrows(TimelineCanonicalRejectionException.class,
                () -> saveService.saveRevision(productId, null, invalid, "snap-user"));

        long snapshotRows = dsl.selectCount().from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        long revisionRows = dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        assertEquals(0L, snapshotRows, "canonical rejection must write zero snapshot rows");
        assertEquals(0L, revisionRows, "canonical rejection must write zero revision rows");
    }

    @Test
    void conflict_leavesNoOrphanSnapshotPayload() {
        String productId = "prod-conflict-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();
        saveService.saveRevision(productId, null, doc, "snap-user");

        // Expected current revision mismatch -> conflict, no writes.
        assertThrows(TimelineConflictException.class,
                () -> saveService.saveRevision(productId, "stale-revision", doc, "snap-user"));

        long snapshotRows = dsl.selectCount().from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        assertEquals(3L, snapshotRows, "conflict must not leave an orphan snapshot payload (3 governed rows from the first save)");
    }

    @Test
    void restoreRevision_writesCopiedPayload() {
        String productId = "prod-restore-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();
        var original = saveService.saveRevision(productId, null, doc, "snap-user");
        String originalPayload = snapshotService.findPayload(snapshotIdOf(original.revisionId())).orElseThrow();

        var restored = saveService.restoreRevision(productId, original.revisionId(), original.revisionId(), "snap-user");

        // Restored revision carries a NEW snapshot row with the copied payload.
        long snapshotRows = dsl.selectCount().from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        // restore writes a NEW timeline snapshot row + NEW revctx row; the
        // restored revision reuses the SAME immutable Effect snapshot (esnap_
        // idempotent — no new esnap row).
        assertEquals(5L, snapshotRows);
        String restoredPayload = snapshotService.findPayload(snapshotIdOf(restored.revisionId())).orElseThrow();
        assertEquals(originalPayload, restoredPayload, "restore must copy the governed payload");
        long revisionRows = dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        assertEquals(2L, revisionRows);
    }

    @Test
    void savedPayload_isResolvableAndRenderParseable() {
        String productId = "prod-parse-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();
        var revision = saveService.saveRevision(productId, null, doc, "snap-user");

        String payload = snapshotService.findPayload(snapshotIdOf(revision.revisionId())).orElseThrow();
        TimelineScriptParser parser = new TimelineScriptParser();
        Optional<TimelineSpec> spec = parser.parse(payload);
        assertTrue(spec.isPresent(), "saved payload must parse through the production render parser");
        assertFalse(spec.get().tracks().isEmpty(), "parsed spec must carry the document tracks");
    }

    private String snapshotIdOf(String revisionId) {
        return dsl.select(TIMELINE_REVISION.SNAPSHOT_ID).from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revisionId)).fetchOne(TIMELINE_REVISION.SNAPSHOT_ID);
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
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }

    private TimelineDocument createDocumentWithDuplicateTrackIds() {
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var trackA = new TimelineTrack("dup-track", "A", TrackType.VIDEO, List.of(clip));
        var trackB = new TimelineTrack("dup-track", "B", TrackType.AUDIO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(trackA, trackB), new TimelineMetadata("Test", "", Map.of()));
    }
    private com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority effectAuthority() {
        // AI14/AI15: production authority wiring — durable Jdbc store + registry.
        return new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority(
                new com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry(dsl),
                new com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore(dsl));
    }

    private com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore revisionSemanticContextStore() {
        return new com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore(dsl);
    }


}