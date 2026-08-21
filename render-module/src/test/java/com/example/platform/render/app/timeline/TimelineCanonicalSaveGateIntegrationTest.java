package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.ProductCurrentRevisionService;import com.example.platform.timeline.app.TimelineCanonicalRejectionException;import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.shared.time.MediaTime;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnosticCode;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnosticSeverity;
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

/**
 * Testcontainers-backed production save gate integration test (NDSF-SCOPE-E1 F025-F027).
 * Real save service -> real mapper -> real validator -> real normalizer -> real jOOQ -> real
 * PostgreSQL. No mocks for the principal chain.
 */
class TimelineCanonicalSaveGateIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
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
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService, digester,
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class), effectAuthority(), revisionSemanticContextStore());
    }

    @Test
    void validPath_saveSucceedsThroughCanonicalGate_p1Persistence() {
        String productId = "prod-gate-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var revision = saveService.saveRevision(productId, null, doc, "gate-user");

        assertNotNull(revision.revisionId());
        assertNull(revision.parentRevisionId());
        long rowCount = dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
        assertEquals(1L, rowCount, "exactly one revision row");
        // ownership
        String persistedProject = dsl.select(TIMELINE_REVISION.PROJECT_ID).from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.ID.eq(revision.revisionId())).fetchOne(TIMELINE_REVISION.PROJECT_ID);
        assertEquals(productId, persistedProject);
        // P1: original document digest persisted in the revision semantic
        // context (contentDigest is the full revision semantic digest)
        assertEquals(digester.digest(doc), revision.semanticContext().timelineContentDigest());
        // current-revision updated after acceptance
        assertEquals(revision.revisionId(), currentRevisionService.getCurrentRevisionId(productId));
    }

    @Test
    void invalidPath_duplicateTrackAndClipIds_rejectedBeforePersistence() {
        String productId = "prod-gate-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        // Multiple deterministic canonical violations: duplicate track ids AND duplicate clip ids.
        var clipA = new TimelineClip("clip-x", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var clipB = new TimelineClip("clip-x", "asset-2", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(
                        new TimelineTrack("track-1", "A", TrackType.VIDEO, List.of(clipA)),
                        new TimelineTrack("track-1", "B", TrackType.VIDEO, List.of(clipB))),
                new TimelineMetadata("invalid", "", Map.of()));

        TimelineCanonicalRejectionException ex =
                assertThrows(TimelineCanonicalRejectionException.class, () ->
                        saveService.saveRevision(productId, null, doc, "gate-user"));

        assertTrue(ex.hasCanonicalDiagnostics());
        List<TimelineDiagnosticCode> codes = ex.diagnostics().stream()
                .map(d -> d.code()).toList();
        assertTrue(codes.contains(TimelineDiagnosticCode.TIMELINE_TRACK_ID_DUPLICATE),
                "expected TIMELINE_TRACK_ID_DUPLICATE, got " + codes);
        assertTrue(codes.contains(TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE),
                "expected TIMELINE_CLIP_ID_DUPLICATE, got " + codes);
        // deterministic ordering: diagnostics are sorted by severity/code/path
        List<TimelineDiagnosticCode> sorted = codes.stream().sorted().toList();
        assertEquals(sorted, codes, "diagnostic order must be deterministic");
        assertTrue(ex.diagnostics().stream()
                .allMatch(d -> d.severity() == TimelineDiagnosticSeverity.ERROR));
        // zero durable writes
        assertEquals(0L, revisionRowCount(productId), "no revision row after rejection");
        assertNull(currentRevisionService.getCurrentRevisionId(productId), "current revision unchanged");
        // transaction remains usable; subsequent valid save succeeds
        var valid = saveService.saveRevision(productId, null, createSampleDocument(), "gate-user");
        assertNotNull(valid.revisionId());
        assertEquals(1L, revisionRowCount(productId));
    }

    @Test
    void invalidPath_negativeTiming_rejectedByAdapterWithFrozenCode() {
        String productId = "prod-gate-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var clip = new TimelineClip("clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(10, 1), MediaTime.ofRational(5, 1), MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        var doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("invalid", "", Map.of()));

        TimelineCanonicalRejectionException ex =
                assertThrows(TimelineCanonicalRejectionException.class, () ->
                        saveService.saveRevision(productId, null, doc, "gate-user"));

        assertTrue(ex.hasAdapterDiagnostics());
        assertEquals(TimelineCanonicalRejectionException.Code.TIMELINE_TIMING_INVALID,
                ex.adapterDiagnostics().get(0).code());
        assertEquals(0L, revisionRowCount(productId), "no revision row after rejection");
        assertNull(currentRevisionService.getCurrentRevisionId(productId));
    }

    private long revisionRowCount(String productId) {
        return dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)).fetchOne(0, Long.class);
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