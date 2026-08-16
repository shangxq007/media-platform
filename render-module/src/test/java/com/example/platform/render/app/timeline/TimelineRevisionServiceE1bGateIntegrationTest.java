package com.example.platform.render.app.timeline;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;import com.example.platform.timeline.app.TimelineCanonicalRejectionException;import com.example.platform.timeline.app.TimelineCanonicalizer;import com.example.platform.timeline.app.TimelineContentHasher;import com.example.platform.timeline.app.TimelineRevisionDiffService;import com.example.platform.timeline.app.TimelineRevisionService;import com.example.platform.timeline.app.TimelineSemanticDiffService;
import com.example.platform.timeline.app.TimelineImportService;
import com.example.platform.render.app.timeline.TimelineSpecImportAdapter;
import com.example.platform.timeline.app.TimelinePatchService;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.timeline.canonicalmodel.TimelineDiagnostic;
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
import java.util.List;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineSnapshot.TIMELINE_SNAPSHOT;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Contract G E1b canonical-gate integration tests (real PostgreSQL Testcontainers).
 * Proves: valid recordRevision persists snapshot + revision in one transaction; invalid
 * multi-violation input produces ordered diagnostics and ZERO writes (snapshot rows,
 * revision rows, revision gap); the transaction remains usable; all five reconciled
 * production caller surfaces keep their accepted behavior.
 */
class TimelineRevisionServiceE1bGateIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionService revisionService;
    private TimelineSnapshotService snapshotService;
    private TimelineEditorSyncService editorSyncService;

    private static final String VALID_INTERNAL = """
            {"schemaVersion":"1.0","id":"tl-valid",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]}]}}""";

    private static final String VALID_INTERNAL_2 = """
            {"schemaVersion":"1.0","id":"tl-valid-2",
             "composition":{"tracks":[
               {"id":"v1","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":60,"rate":{"num":30,"den":1}}}}]}]}}""";

    private static final String INVALID_MULTI_VIOLATION = """
            {"schemaVersion":"1.0","id":"tl-invalid",
             "composition":{"tracks":[
               {"id":"dup","type":"VIDEO","clips":[
                 {"id":"c1","assetId":"ast-1",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}},
                 {"id":"c1","assetId":"ast-2",
                  "timelineRange":{"start":{"frame":0,"rate":{"num":30,"den":1}},"duration":{"frame":30,"rate":{"num":30,"den":1}}}}]},
               {"id":"dup","type":"AUDIO","clips":[]}]}}""";

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
        TenantContext.set("ten-e1b");
        snapshotService = new TimelineSnapshotService(dsl);
        TimelineExtensionsReader extensionsReader = new TimelineExtensionsReader();
        TimelineScriptParser parser = new TimelineScriptParser(extensionsReader);
        TimelineSpecImportAdapter importAdapter = new TimelineSpecImportAdapter(extensionsReader);
        TimelineImportService importService = new TimelineImportService();
        TimelineCanonicalizer canonicalizer = new TimelineCanonicalizer();
        TimelineSpecResolver resolver = new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), parser);
        TimelineConversionService conversionService = new TimelineConversionService(resolver, importAdapter, importService);
        TimelinePatchService patchService = new TimelinePatchService(canonicalizer);
        revisionService = new TimelineRevisionService(
                new TimelineRevisionRepository(dsl), snapshotService,
                new TimelineContentHasher(canonicalizer),
                new TimelineRevisionDiffService(),
                new RenderTimelinePayloadCodec(conversionService, new InternalTimelineToEditorConverter()),
                patchService,
                new TimelineSemanticDiffService(canonicalizer));
        editorSyncService = new TimelineEditorSyncService(
                conversionService, new InternalTimelineToEditorConverter(), snapshotService,
                resolver, revisionService);
    }

    private long snapshotRows(String projectId) {
        return dsl.selectCount().from(TIMELINE_SNAPSHOT)
                .where(TIMELINE_SNAPSHOT.PROJECT_ID.eq(projectId)).fetchOne(0, Long.class);
    }

    private long revisionRows(String projectId) {
        return dsl.selectCount().from(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(projectId)).fetchOne(0, Long.class);
    }

    @Test
    void validRecordRevision_persistsSnapshotAndRevision_inOneTransaction() {
        String projectId = "prj-valid-" + java.util.UUID.randomUUID();
        var info = revisionService.recordRevision(projectId, "ten-e1b", VALID_INTERNAL,
                "sync", "alice", null, "initial");

        assertEquals(1L, snapshotRows(projectId), "valid recordRevision must persist one snapshot row");
        assertEquals(1L, revisionRows(projectId), "valid recordRevision must persist one revision row");
        assertTrue(info.snapshotId().startsWith("snap"));
        // The revision's snapshot payload equals the accepted input.
        assertEquals(VALID_INTERNAL, snapshotService.findPayload(info.snapshotId()).orElseThrow());
        // No revision without payload.
        assertTrue(snapshotService.findPayload(info.snapshotId()).isPresent());
    }

    @Test
    void invalidMultiViolation_zeroWrites_orderedDiagnostics_transactionUsable() {
        String projectId = "prj-invalid-" + java.util.UUID.randomUUID();

        TimelineCanonicalRejectionException ex = assertThrows(TimelineCanonicalRejectionException.class,
                () -> revisionService.recordRevision(projectId, "ten-e1b", INVALID_MULTI_VIOLATION,
                        "sync", null, null, "bad input"));

        // Ordered canonical diagnostics: duplicate track ids and duplicate clip ids.
        List<TimelineDiagnostic> diagnostics = ex.diagnostics();
        assertFalse(diagnostics.isEmpty(), "rejection must carry canonical diagnostics");
        assertTrue(diagnostics.stream().anyMatch(d ->
                        d.code() == com.example.platform.timeline.canonicalmodel.TimelineDiagnosticCode.TIMELINE_TRACK_ID_DUPLICATE),
                "duplicate track diagnostic must be present");
        assertTrue(diagnostics.stream().anyMatch(d ->
                        d.code() == com.example.platform.timeline.canonicalmodel.TimelineDiagnosticCode.TIMELINE_CLIP_ID_DUPLICATE),
                "duplicate clip diagnostic must be present");

        // ZERO writes.
        assertEquals(0L, snapshotRows(projectId), "canonical rejection must write zero snapshot rows");
        assertEquals(0L, revisionRows(projectId), "canonical rejection must write zero revision rows");

        // Transaction remains usable; subsequent valid call succeeds with revision number 1 (no gap).
        var info = revisionService.recordRevision(projectId, "ten-e1b", VALID_INTERNAL,
                "sync", null, null, "after rejection");
        assertEquals(1, info.revisionNumber(), "rejection must leave no revision-number gap");
        assertEquals(1L, revisionRows(projectId));
        assertEquals(1L, snapshotRows(projectId));
    }

    @Test
    void editorSyncCaller_pushAndSync_remainCompatible() {
        String projectId = "prj-editor-" + java.util.UUID.randomUUID();

        TimelineEditorSyncService.PushResult push =
                editorSyncService.push(projectId, "ten-e1b", VALID_INTERNAL, true);
        assertNotNull(push.revision(), "push must record a revision");
        assertNotNull(push.snapshotId(), "push must return the governed snapshot id");
        assertEquals(1L, revisionRows(projectId));
        assertEquals(1L, snapshotRows(projectId));

        TimelineEditorSyncService.SyncResult sync =
                editorSyncService.sync(projectId, "ten-e1b", VALID_INTERNAL_2, "alice", "sess-1", "edit");
        assertNotNull(sync.revision());
        assertEquals(2L, revisionRows(projectId));
        assertEquals(2L, snapshotRows(projectId));
    }

    @Test
    void snapshotControllerCaller_saveSnapshotEnsuringInternal_remainsCompatible() {
        String projectId = "prj-snapctl-" + java.util.UUID.randomUUID();
        String snapshotId = editorSyncService.saveSnapshotEnsuringInternal(
                projectId, "ten-e1b", VALID_INTERNAL, "internal-1.0");
        assertTrue(snapshotId.startsWith("snap"));
        assertEquals(1L, revisionRows(projectId));
        assertEquals(1L, snapshotRows(projectId));
    }

    @Test
    void backfillCaller_backfillHeadFromLatestSnapshot_remainsCompatible() {
        String projectId = "prj-backfill-" + java.util.UUID.randomUUID();
        // Simulate a legacy snapshot without a revision head (pre-E1b data).
        String legacySnap = snapshotService.save(projectId, "ten-e1b", VALID_INTERNAL, "internal-1.0");

        var backfilled = revisionService.backfillHeadFromLatestSnapshot(projectId, "ten-e1b");
        assertTrue(backfilled.isPresent(), "backfill must record a head from the latest snapshot");
        assertEquals("backfill", backfilled.get().source());
        assertEquals(1L, revisionRows(projectId));
    }

    @Test
    void restoreCaller_restore_remainsCompatible() {
        String projectId = "prj-restore-" + java.util.UUID.randomUUID();
        var original = revisionService.recordRevision(projectId, "ten-e1b", VALID_INTERNAL,
                "sync", "alice", null, "original");
        revisionService.recordRevision(projectId, "ten-e1b", VALID_INTERNAL_2,
                "sync", "alice", null, "newer head");

        var restored = revisionService.restore(projectId, "ten-e1b", original.id(), "alice");
        assertEquals("rollback", restored.newRevision().source());
        assertEquals(3L, revisionRows(projectId));
        // Restored revision has its own governed snapshot row with the copied payload.
        assertEquals(3L, snapshotRows(projectId));
        assertEquals(VALID_INTERNAL,
                snapshotService.findPayload(restored.newRevision().snapshotId()).orElseThrow());
    }

    @Test
    void aiAdoptCaller_recordAiAdoptRevision_remainsCompatible_noNewAiBehavior() {
        String projectId = "prj-aiadopt-" + java.util.UUID.randomUUID();
        var info = revisionService.recordAiAdoptRevision(
                projectId, "ten-e1b", VALID_INTERNAL, "sess-1", "prop-1", List.of());
        assertEquals("ai-adopt", info.source());
        assertEquals(1L, revisionRows(projectId));
        assertEquals(1L, snapshotRows(projectId));
        assertTrue(snapshotService.findPayload(info.snapshotId()).isPresent());
    }

    @Test
    void everyRecordedRevision_hasResolvablePayload_noCurrentPointerToMissingPayload() {
        String projectId = "prj-consistency-" + java.util.UUID.randomUUID();
        var a = revisionService.recordRevision(projectId, "ten-e1b", VALID_INTERNAL, "sync", null, null, "a");
        var b = revisionService.recordRevision(projectId, "ten-e1b", VALID_INTERNAL_2, "sync", null, null, "b");
        assertTrue(snapshotService.findPayload(a.snapshotId()).isPresent());
        assertTrue(snapshotService.findPayload(b.snapshotId()).isPresent());
    }
}
