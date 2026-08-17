package com.example.platform.render.app.timeline;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;import com.example.platform.timeline.app.TimelineCanonicalizer;import com.example.platform.timeline.app.TimelineContentHasher;import com.example.platform.timeline.app.TimelineRevisionDiffService;import com.example.platform.timeline.app.TimelineRevisionService;import com.example.platform.timeline.app.TimelineSemanticDiffService;
import com.example.platform.timeline.app.TimelineImportService;
import com.example.platform.render.app.timeline.TimelineSpecImportAdapter;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelinePatchService;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.render.domain.interchange.TimelineExtensionsReader;
import com.example.platform.render.domain.interchange.TimelineOutputSpec;
import com.example.platform.render.domain.interchange.TimelineScriptParser;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.web.TenantContext;
import java.util.List;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineRevisionServiceTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionService revisionService;
    private TimelineSnapshotService snapshotService;
    private TimelineSpecImportAdapter importAdapter;
    private TimelineImportService importService;

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
        TenantContext.set("ten-1");
        RenderTestSchemaFixture.truncate(dsl);
        snapshotService = new TimelineSnapshotService(dsl);
        TimelineCanonicalizer canonicalizer = new TimelineCanonicalizer();
        TimelineSpecResolver resolver =
                new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), new TimelineScriptParser());
        importAdapter = new TimelineSpecImportAdapter(new TimelineExtensionsReader());
        importService = new TimelineImportService();
        TimelineConversionService conversionService = new TimelineConversionService(resolver, importAdapter, importService);
        revisionService = new TimelineRevisionService(
                new TimelineRevisionRepository(dsl),
                snapshotService,
                new TimelineContentHasher(canonicalizer),
                new TimelineRevisionDiffService(),
                new RenderTimelinePayloadCodec(conversionService, new InternalTimelineToEditorConverter()),
                new TimelinePatchService(canonicalizer),
                new TimelineSemanticDiffService(canonicalizer),
                new TimelineArtifactPinValidator(
                        new com.example.platform.artifact.infrastructure.JooqArtifactQueryService(
                                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl),
                                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl))),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)));
    }

    @Test
    void recordsRevisionChainAndRestore() {
        TimelineSpec spec = TimelineSpec.create("tl-rev", "Rev", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));

        String snap1 = snapshotService.save("prj-rev", "ten-1", v1, "internal-1.0");
        TimelineRevisionService.RevisionInfo r1 =
                revisionService.recordRevision("prj-rev", "ten-1", v1, "sync", null, null, "initial");

        TimelineSpec spec2 = TimelineSpec.create("tl-rev", "Rev2", TimelineOutputSpec.mp4_1080p30());
        String v2 = importService.importTimeline(importAdapter.toRequest(spec2));
        String snap2 = snapshotService.save("prj-rev", "ten-1", v2, "internal-1.0");
        TimelineRevisionService.RevisionInfo r2 =
                revisionService.recordRevision("prj-rev", "ten-1", v2, "sync", null, null, "edit");

        assertEquals(1, r1.revisionNumber());
        assertEquals(2, r2.revisionNumber());
        assertEquals(r1.id(), r2.parentRevisionId());

        TimelineRevisionService.RestoreResult restored =
                revisionService.restore("prj-rev", "ten-1", r1.id(), "user-1");
        assertTrue(restored.newRevision().revisionNumber() >= 3);
        assertEquals("rollback", restored.newRevision().source());
    }

    @Test
    void previewPatchReplayRequiresStoredOps() {
        TimelineSpec spec = TimelineSpec.create("tl-patch", "Patch", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-patch", "ten-1", v1, "internal-1.0");
        TimelineRevisionService.RevisionInfo head =
                revisionService.recordRevision("prj-patch", "ten-1", v1, "sync", null, null, "base");

        var noOps = revisionService.previewPatchReplay(head.id());
        assertFalse(noOps.hasPatchOps());
    }

    @Test
    void listHistoryFiltersBySourceAndAuthor() {
        TimelineSpec spec = TimelineSpec.create("tl-filter", "F", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-filter", "ten-1", v1, "internal-1.0");
        revisionService.recordRevision("prj-filter", "ten-1", v1, "sync", "alice", null, "alice edit");

        TimelineSpec spec2 = TimelineSpec.create("tl-filter-2", "F2", TimelineOutputSpec.mp4_1080p30());
        String v2 = importService.importTimeline(importAdapter.toRequest(spec2));
        String snap2 = snapshotService.save("prj-filter", "ten-1", v2, "internal-1.0");
        revisionService.recordRevision("prj-filter", "ten-1", v2, "ai-adopt", "bob", null, "bob adopt");

        assertEquals(1, revisionService.listHistory("prj-filter", null, "alice", null, 10).size());
        assertEquals(1, revisionService.listHistory("prj-filter", null, null, "ai-adopt", 10).size());
        assertEquals(2, revisionService.listHistory("prj-filter", null, null, null, 10).size());
    }

    @Test
    void listFacetsReturnsSourcesAndAuthors() {
        TimelineSpec spec = TimelineSpec.create("tl-facet", "F", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-facet", "ten-1", v1, "internal-1.0");
        revisionService.recordRevision("prj-facet", "ten-1", v1, "sync", "alice", null, "a");
        TimelineSpec spec2 = TimelineSpec.create("tl-facet-2", "F2", TimelineOutputSpec.mp4_1080p30());
        String v2 = importService.importTimeline(importAdapter.toRequest(spec2));
        String snap2 = snapshotService.save("prj-facet", "ten-1", v2, "internal-1.0");
        revisionService.recordRevision("prj-facet", "ten-1", v2, "ai-adopt", "bob", null, "b");

        var facets = revisionService.listFacets("prj-facet");
        assertTrue(facets.sources().contains("sync"));
        assertTrue(facets.sources().contains("ai-adopt"));
        assertEquals(2, facets.authors().size());
    }

    @Test
    void updateAnnotationPersistsMessage() {
        TimelineSpec spec = TimelineSpec.create("tl-note", "Note", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-note", "ten-1", v1, "internal-1.0");
        TimelineRevisionService.RevisionInfo head =
                revisionService.recordRevision("prj-note", "ten-1", v1, "sync", null, null, "before");

        var updated = revisionService.updateAnnotation(
                "prj-note", head.id(), "  release candidate  ", List.of("review", "release"));
        assertTrue(updated.isPresent());
        assertEquals("release candidate", updated.get().message());
        assertEquals(List.of("review", "release"), updated.get().labels());

        var cleared = revisionService.updateAnnotation("prj-note", head.id(), "   ", List.of());
        assertTrue(cleared.isPresent());
        assertTrue(cleared.get().message() == null || cleared.get().message().isBlank());
    }

    @Test
    void previewPatchStepsReturnsEmptyWhenNoOps() {
        TimelineSpec spec = TimelineSpec.create("tl-steps", "Steps", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-steps", "ten-1", v1, "internal-1.0");
        TimelineRevisionService.RevisionInfo head =
                revisionService.recordRevision("prj-steps", "ten-1", v1, "sync", null, null, "base");

        var steps = revisionService.previewPatchSteps(head.id());
        assertFalse(steps.hasPatchOps());
        assertTrue(steps.steps().isEmpty());
    }

    @Test
    void t6_successfulRevisionRegistersPinProtectionRows() {
        // Seed a canonical Artifact that a revision's sourceBinding will pin.
        dsl.execute("TRUNCATE TABLE artifact_pin CASCADE");
        dsl.execute("TRUNCATE TABLE artifact_replica CASCADE");
        dsl.execute("TRUNCATE TABLE artifact CASCADE");
        var artifactRepo = new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl);
        var digest = com.example.platform.shared.digest.ContentDigest.sha256("d".repeat(64));
        artifactRepo.insertRaw(new com.example.platform.shared.identity.ArtifactId("art-t6"),
                "ten-1", digest, 512L,
                com.example.platform.artifact.domain.ArtifactMediaType.VIDEO,
                com.example.platform.artifact.domain.ArtifactKind.RENDER_MASTER,
                com.example.platform.artifact.domain.ArtifactState.AVAILABLE, null);

        // Build internal timeline JSON whose clip sourceBinding pins art-t6
        // (E1b-valid: assetId + timelineRange/sourceRange + sourceBinding).
        String json = "{\"schemaVersion\":1,\"id\":\"tl-t6\",\"revision\":1,\"composition\":{\"tracks\":["
                + "{\"id\":\"t1\",\"type\":\"VIDEO\",\"clips\":[{\"id\":\"c1\",\"assetId\":\"ast-t6\","
                + "\"timelineRange\":{\"start\":{\"frame\":0,\"rate\":{\"num\":30,\"den\":1}},\"duration\":{\"frame\":30,\"rate\":{\"num\":30,\"den\":1}}},"
                + "\"sourceRange\":{\"start\":{\"frame\":0,\"rate\":{\"num\":30,\"den\":1}},\"duration\":{\"frame\":30,\"rate\":{\"num\":30,\"den\":1}}},"
                + "\"sourceBinding\":{"
                + "\"artifactId\":\"art-t6\",\"contentDigest\":{\"algorithm\":\"SHA256\",\"value\":\""
                + digest.value() + "\"}}}]}]}}";
        TimelineRevisionService.RevisionInfo info =
                revisionService.recordRevision("prj-t6", "ten-1", json, "sync", null, null, "pin test");
        assertNotNull(info.id());

        long pins = dsl.fetchCount(dsl.selectFrom(
                com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.REVISION_ID.eq(info.id())));
        assertEquals(1, pins, "successful revision must register all required artifact_pin protection rows");
    }

    @Test
    void t2b_missingArtifactPinFailsClosedWithoutRevision() {
        dsl.execute("TRUNCATE TABLE artifact_pin CASCADE");
        dsl.execute("TRUNCATE TABLE artifact_replica CASCADE");
        dsl.execute("TRUNCATE TABLE artifact CASCADE");
        long revisionsBefore = dsl.fetchCount(dsl.selectFrom(
                com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION));

        String json = "{\"schemaVersion\":1,\"id\":\"tl-t2b\",\"revision\":1,\"composition\":{\"tracks\":["
                + "{\"id\":\"t1\",\"type\":\"VIDEO\",\"clips\":[{\"id\":\"c1\",\"assetId\":\"ast-t2b\","
                + "\"timelineRange\":{\"start\":{\"frame\":0,\"rate\":{\"num\":30,\"den\":1}},\"duration\":{\"frame\":30,\"rate\":{\"num\":30,\"den\":1}}},"
                + "\"sourceRange\":{\"start\":{\"frame\":0,\"rate\":{\"num\":30,\"den\":1}},\"duration\":{\"frame\":30,\"rate\":{\"num\":30,\"den\":1}}},"
                + "\"sourceBinding\":{"
                + "\"artifactId\":\"art-missing\",\"contentDigest\":{\"algorithm\":\"SHA256\",\"value\":\""
                + "e".repeat(64) + "\"}}}]}]}}";

        assertThrows(com.example.platform.timeline.app.TimelineCanonicalRejectionException.class,
                () -> revisionService.recordRevision("prj-t2b", "ten-1", json, "sync", null, null, "bad pin"));

        long revisionsAfter = dsl.fetchCount(dsl.selectFrom(
                com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION));
        assertEquals(revisionsBefore, revisionsAfter, "invalid pin must not create a revision");
        long pinsAfter = dsl.fetchCount(dsl.selectFrom(
                com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN));
        assertEquals(0, pinsAfter, "invalid pin must not create protection rows");
    }
}
