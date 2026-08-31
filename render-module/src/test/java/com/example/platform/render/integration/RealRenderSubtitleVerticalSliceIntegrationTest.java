package com.example.platform.render.integration;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;
import com.example.platform.timeline.app.TimelineRevisionRefMutation;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Retained non-Provider portion of the timeline vertical slice.
 *
 * <p>Render-command and media-probe assertions moved with the deleted legacy authority;
 * governed timeline save, snapshot payload, and fail-closed invalid-save coverage remain.</p>
 */
class RealRenderSubtitleVerticalSliceIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;

    private TimelineRevisionSaveService saveService;

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
        com.example.platform.shared.web.TenantContext.clear();
        TimelineSnapshotService snapshotService = new TimelineSnapshotService(dsl);
        TimelineRevisionRefMutation currentRevisionService = new TimelineRevisionRefMutation(dsl);
        saveService = new TimelineRevisionSaveService(
                dsl,
                currentRevisionService,
                new com.example.platform.timeline.canonical.TimelineContentDigester(),
                snapshotService,
                new TimelineArtifactPinValidator(
                        new com.example.platform.render.testutil.NoopArtifactQueryService()),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)),
                effectAuthority(),
                new com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore(dsl),
                new DefaultTimelineRevisionPersistence(),
                new TimelineRevisionRefHeadUpdateAdapter(currentRevisionService), com.example.platform.render.testsupport.TimelineMutationTestSupport.ALLOW_ALL);
    }

    @Test
    void e1SavedTimeline_persistsGovernedCaptionSnapshotPayload() {
        String projectId = "prj-vslice-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        insertProduct(projectId, "ten-vslice");

        TimelineDocument document = createDocumentWithCaptions("clip-vslice", "asset-vslice");
        var revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(saveService,
                "ten-vslice", projectId, null, document, RenderTestSchemaFixture.SERVER_ACTOR);

        String snapshotId = dsl.select(
                        com.example.platform.typedschema.jooq.generated.tables.TimelineRevision
                                .TIMELINE_REVISION.SNAPSHOT_ID)
                .from(com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION)
                .where(com.example.platform.typedschema.jooq.generated.tables.TimelineRevision
                        .TIMELINE_REVISION.ID.eq(revision.revisionId()))
                .fetchOne(com.example.platform.typedschema.jooq.generated.tables.TimelineRevision
                        .TIMELINE_REVISION.SNAPSHOT_ID);
        Optional<String> payload = payloadOf(snapshotId);

        assertTrue(payload.isPresent(), "E1 save must persist the governed snapshot payload");
        assertTrue(payload.get().contains("\"textOverlays\""),
                "payload must carry the caption expansion");
    }

    @Test
    void invalidSave_renderNeverReachesReady() {
        String projectId = "prj-bad-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        insertProduct(projectId, "ten-vslice");
        TimelineDocument invalid = createDocumentWithDuplicateTrackIds();

        assertThrows(com.example.platform.timeline.app.TimelineCanonicalRejectionException.class,
                () -> com.example.platform.render.testsupport.TimelineMutationTestSupport.save(saveService,
                        "ten-vslice", projectId, null, invalid,
                        RenderTestSchemaFixture.SERVER_ACTOR));
        assertEquals(0L, dsl.selectCount()
                .from(com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT)
                .where(com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot
                        .TIMELINE_SNAPSHOT.PROJECT_ID.eq(projectId))
                .fetchOne(0, Long.class));
    }

    private void insertProduct(String productId, String tenantId) {
        RenderTestSchemaFixture.insertCanonicalProject(dsl, tenantId, productId);
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, productId)
                .set(PRODUCT.TENANT_ID, tenantId)
                .set(PRODUCT.PROJECT_ID, productId)
                .set(PRODUCT.PRODUCT_TYPE, "OUTPUT")
                .set(PRODUCT.REPRESENTATION_KIND, "MEDIA_FILE")
                .set(PRODUCT.STATUS, "REGISTERED")
                .set(PRODUCT.VERSION, 1)
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private TimelineDocument createDocumentWithCaptions(String clipId, String assetId) {
        String captions = """
                [
                  {"id":"cue-1","text":"Welcome to the Media Platform","startMs":1000,"durationMs":1000},
                  {"id":"cue-2","text":"你好，字幕验证","startMs":2000,"durationMs":800}
                ]""";
        TimelineClip clip = new TimelineClip(
                clipId, assetId, null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(3, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineTrack track = new TimelineTrack(
                "track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track),
                new TimelineMetadata(
                        "Vertical Slice",
                        "",
                        Map.of(com.example.platform.timeline.app.TimelineDocumentJsonSerializer
                                .CAPTIONS_V1_METADATA_KEY, captions)));
    }

    private TimelineDocument createDocumentWithDuplicateTrackIds() {
        TimelineClip clip = new TimelineClip(
                "clip-1", "asset-1", null, null, null,
                MediaTime.ofRational(0, 1), MediaTime.ofRational(10, 1),
                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM");
        TimelineTrack trackA = new TimelineTrack(
                "dup-track", "A", TrackType.VIDEO, List.of(clip));
        TimelineTrack trackB = new TimelineTrack(
                "dup-track", "B", TrackType.AUDIO, List.of(clip));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(trackA, trackB),
                new TimelineMetadata("Test", "", Map.of()));
    }

    private Optional<String> payloadOf(String snapshotId) {
        return Optional.ofNullable(dsl.select(
                        com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot
                                .TIMELINE_SNAPSHOT.PAYLOAD_JSON)
                .from(com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT)
                .where(com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot
                        .TIMELINE_SNAPSHOT.ID.eq(snapshotId))
                .fetchOne(com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot
                        .TIMELINE_SNAPSHOT.PAYLOAD_JSON));
    }

    private static com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority
            effectAuthority() {
        return new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority(
                new com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry.InMemory(),
                new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore.InMemory());
    }
}
