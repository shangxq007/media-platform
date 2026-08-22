package com.example.platform.render.app.timeline;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.app.TimelineCanonicalizer;
import com.example.platform.timeline.app.TimelineContentHasher;
import com.example.platform.timeline.app.TimelineRevisionDiffService;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.TimelineRevisionDiffQuery;
import com.example.platform.timeline.app.TimelineSemanticDiffService;
import com.example.platform.timeline.app.InternalTimelineJson;
import com.example.platform.timeline.app.TimelineImportService;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * CFRH-I2 ownership-scoped query authority tests (replaces the retired
 * TimelineRevisionService query tests). All reads carry explicit
 * (projectId, tenantId) and ownership participates in the persistence query:
 * wrong project / wrong tenant return empty (fail closed, no leak).
 */
class TimelineRevisionServiceTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionQueryService revisionQueryService;
    private TimelineRevisionDiffQuery revisionDiffQuery;
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
        var repo = new TimelineRevisionRepository(dsl);
        var diffService = new TimelineRevisionDiffService();
        var hasher = new TimelineContentHasher(canonicalizer);
        var payloadCodec = new RenderTimelinePayloadCodec(conversionService, new InternalTimelineToEditorConverter());
        revisionQueryService = new TimelineRevisionQueryService(
                repo, snapshotService, diffService, payloadCodec);
        revisionDiffQuery = new TimelineRevisionDiffQuery(
                repo, snapshotService, hasher, diffService,
                new TimelinePatchService(canonicalizer), new TimelineSemanticDiffService(canonicalizer));
    }

    @Test
    void previewPatchReplayRequiresStoredOps() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-patch", "Patch", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-patch", "ten-1", v1, "internal-1.0");
        String headId = insertRevisionRow("prj-patch", "ten-1", snap1, v1, 1, null, "sync", null, null, "base");

        var noOps = revisionDiffQuery.previewPatchReplay("prj-patch", "ten-1", headId);
        assertFalse(noOps.hasPatchOps());
    }

    @Test
    void listHistoryFiltersBySourceAndAuthor() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-filter", "F", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-filter", "ten-1", v1, "internal-1.0");
        insertRevisionRow("prj-filter", "ten-1", snap1, v1, 1, null, "sync", "alice", null, "alice edit");

        TimelineSpec spec2 = TimelineSpec.create("tl-filter-2", "F2", TimelineOutputSpec.mp4_1080p30());
        String v2 = importService.importTimeline(importAdapter.toRequest(spec2));
        String snap2 = snapshotService.save("prj-filter", "ten-1", v2, "internal-1.0");
        insertRevisionRow("prj-filter", "ten-1", snap2, v2, 2, null, "ai-adopt", "bob", null, "bob adopt");

        assertEquals(1, revisionQueryService.listHistory("prj-filter", "ten-1", null, "alice", null, 10).size());
        assertEquals(1, revisionQueryService.listHistory("prj-filter", "ten-1", null, null, "ai-adopt", 10).size());
        assertEquals(2, revisionQueryService.listHistory("prj-filter", "ten-1", null, null, null, 10).size());
    }

    @Test
    void listHistoryIsTenantIsolated() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-iso", "I", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-iso", "ten-1", v1, "internal-1.0");
        insertRevisionRow("prj-iso", "ten-1", snap1, v1, 1, null, "sync", "alice", null, "a");

        // tenant-2 cannot see tenant-1 revisions even for the same projectId
        assertEquals(0, revisionQueryService.listHistory("prj-iso", "ten-2", null, null, null, 10).size());
    }

    @Test
    void listFacetsReturnsSourcesAndAuthors() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-facet", "F", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-facet", "ten-1", v1, "internal-1.0");
        insertRevisionRow("prj-facet", "ten-1", snap1, v1, 1, null, "sync", "alice", null, "a");
        TimelineSpec spec2 = TimelineSpec.create("tl-facet-2", "F2", TimelineOutputSpec.mp4_1080p30());
        String v2 = importService.importTimeline(importAdapter.toRequest(spec2));
        String snap2 = snapshotService.save("prj-facet", "ten-1", v2, "internal-1.0");
        insertRevisionRow("prj-facet", "ten-1", snap2, v2, 2, null, "ai-adopt", "bob", null, "b");

        var facets = revisionQueryService.listFacets("prj-facet", "ten-1");
        assertTrue(facets.sources().contains("sync"));
        assertTrue(facets.sources().contains("ai-adopt"));
        assertEquals(2, facets.authors().size());
    }

    @Test
    void updateAnnotationPersistsMessage() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-note", "Note", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-note", "ten-1", v1, "internal-1.0");
        String headId = insertRevisionRow("prj-note", "ten-1", snap1, v1, 1, null, "sync", null, null, "before");

        var updated = revisionQueryService.updateAnnotation(
                "prj-note", "ten-1", headId, "  release candidate  ", List.of("review", "release"));
        assertTrue(updated.isPresent());
        assertEquals("release candidate", updated.get().message());
        assertEquals(List.of("review", "release"), updated.get().labels());

        var cleared = revisionQueryService.updateAnnotation("prj-note", "ten-1", headId, "   ", List.of());
        assertTrue(cleared.isPresent());
        assertTrue(cleared.get().message() == null || cleared.get().message().isBlank());
    }

    @Test
    void updateAnnotationIsOwnershipScoped() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-note2", "N", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-note2", "ten-1", v1, "internal-1.0");
        String headId = insertRevisionRow("prj-note2", "ten-1", snap1, v1, 1, null, "sync", null, null, "before");

        // wrong project: no update, no leak
        assertTrue(revisionQueryService.updateAnnotation(
                "prj-other", "ten-1", headId, "x", List.of()).isEmpty());
        // wrong tenant: no update, no leak
        assertTrue(revisionQueryService.updateAnnotation(
                "prj-note2", "ten-9", headId, "x", List.of()).isEmpty());
    }

    @Test
    void previewPatchStepsReturnsEmptyWhenNoOps() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-steps", "Steps", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-steps", "ten-1", v1, "internal-1.0");
        String headId = insertRevisionRow("prj-steps", "ten-1", snap1, v1, 1, null, "sync", null, null, "base");

        var steps = revisionDiffQuery.previewPatchSteps("prj-steps", "ten-1", headId);
        assertFalse(steps.hasPatchOps());
        assertTrue(steps.steps().isEmpty());
    }

    @Test
    void findByIdRequiresExactProjectAndTenant() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-find", "F", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-find", "ten-1", v1, "internal-1.0");
        String headId = insertRevisionRow("prj-find", "ten-1", snap1, v1, 1, null, "sync", null, null, "a");

        // correct ownership -> found
        assertTrue(revisionQueryService.findById("prj-find", "ten-1", headId).isPresent());
        // wrong project -> empty (no load-then-check leak)
        assertTrue(revisionQueryService.findById("prj-other", "ten-1", headId).isEmpty());
        // wrong tenant -> empty
        assertTrue(revisionQueryService.findById("prj-find", "ten-9", headId).isEmpty());
    }

    @Test
    void getDetailIsOwnershipScopedNoLoadThenCheck() throws Exception {
        TimelineSpec spec = TimelineSpec.create("tl-det", "D", TimelineOutputSpec.mp4_1080p30());
        String v1 = importService.importTimeline(importAdapter.toRequest(spec));
        String snap1 = snapshotService.save("prj-det", "ten-1", v1, "internal-1.0");
        String headId = insertRevisionRow("prj-det", "ten-1", snap1, v1, 1, null, "sync", null, null, "a");

        assertTrue(revisionQueryService.getDetail("prj-det", "ten-1", headId).isPresent());
        // foreign project -> empty (persistence predicate excludes)
        assertTrue(revisionQueryService.getDetail("prj-other", "ten-1", headId).isEmpty());
        // foreign tenant -> empty
        assertTrue(revisionQueryService.getDetail("prj-det", "ten-9", headId).isEmpty());
    }

    /**
     * Test data preparation: inserts a timeline_revision row directly.
     * CFRH-I1 removed the legacy recordRevision write authority; query tests
     * seed rows through the repository to exercise ownership-scoped query
     * projections (CFRH-I2).
     */
    private String insertRevisionRow(
            String projectId, String tenantId, String snapshotId, String payload,
            int revisionNumber, String parentId, String source,
            String authorUserId, String editSessionId, String message) throws java.io.IOException {
        String id = "rev-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        var hasher = new TimelineContentHasher(new TimelineCanonicalizer());
        new TimelineRevisionRepository(dsl).insert(new TimelineRevisionRepository.RevisionRow(
                id, projectId, tenantId, parentId, revisionNumber, snapshotId,
                InternalTimelineJson.revision(InternalTimelineJson.parse(payload)),
                hasher.hashInternalTimeline(payload), "1.0", source, authorUserId,
                editSessionId, message, null, null, "{}", false, null, null,
                java.time.OffsetDateTime.now()));
        return id;
    }
}
