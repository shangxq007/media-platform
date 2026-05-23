package com.example.platform.render.app.timeline;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.render.app.TimelinePatchService;
import com.example.platform.render.app.TimelineSnapshotService;
import com.example.platform.render.app.TimelineValidationService;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineScriptParser;
import com.example.platform.render.domain.timeline.TimelineSpec;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class TimelineRevisionServiceTest {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private TimelineRevisionService revisionService;
    private TimelineSnapshotService snapshotService;
    private DSLContext dsl;

    @BeforeEach
    void setUp() throws Exception {
        String db = "trev" + COUNTER.incrementAndGet();
        Connection conn = DriverManager.getConnection(
                "jdbc:h2:mem:" + db + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE", "sa", "");
        dsl = DSL.using(conn, org.jooq.SQLDialect.H2);
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("create table timeline_snapshot ("
                    + "id varchar(64) primary key, project_id varchar(64), tenant_id varchar(64),"
                    + "payload_json clob not null, schema_version varchar(32),"
                    + "content_hash varchar(64), revision_number int, created_at timestamp)");
            stmt.execute("create table timeline_revision ("
                    + "id varchar(64) primary key, project_id varchar(64) not null, tenant_id varchar(64),"
                    + "parent_revision_id varchar(64), revision_number int not null, snapshot_id varchar(64) not null,"
                    + "internal_revision int not null, content_hash varchar(64) not null, schema_version varchar(32),"
                    + "source varchar(32) not null, author_user_id varchar(64), edit_session_id varchar(64),"
                    + "message varchar(512), change_summary_json clob, patch_ops_json clob,"
                    + "labels_json varchar(512), created_at timestamp not null)");
        }
        snapshotService = new TimelineSnapshotService(dsl);
        TimelineCanonicalizer canonicalizer = new TimelineCanonicalizer();
        TimelineSpecResolver resolver =
                new TimelineSpecResolver(TimelineTestSupport.internalTimelineAdapter(), new TimelineScriptParser());
        InternalTimelineWriter writer = new InternalTimelineWriter(new TimelineExtensionsReader());
        TimelineConversionService conversionService = new TimelineConversionService(resolver, writer);
        revisionService = new TimelineRevisionService(
                new TimelineRevisionRepository(dsl),
                snapshotService,
                new TimelineContentHasher(canonicalizer),
                new TimelineRevisionDiffService(),
                new InternalTimelineToEditorConverter(),
                conversionService,
                new TimelinePatchService(
                        new TimelineValidationService(new InternalTimelineValidationService()),
                        TimelineTestSupport.internalTimelineAdapter(),
                        canonicalizer));
    }

    @Test
    void recordsRevisionChainAndRestore() {
        TimelineSpec spec = TimelineSpec.create("tl-rev", "Rev", TimelineOutputSpec.mp4_1080p30());
        InternalTimelineWriter writer = new InternalTimelineWriter(new TimelineExtensionsReader());
        String v1 = writer.toJson(spec);

        String snap1 = snapshotService.save("prj-rev", "ten-1", v1, "internal-1.0");
        TimelineRevisionService.RevisionInfo r1 =
                revisionService.recordRevision("prj-rev", "ten-1", snap1, v1, "sync", null, null, "initial");

        TimelineSpec spec2 = TimelineSpec.create("tl-rev", "Rev2", TimelineOutputSpec.mp4_1080p30());
        String v2 = writer.toJson(spec2);
        String snap2 = snapshotService.save("prj-rev", "ten-1", v2, "internal-1.0");
        TimelineRevisionService.RevisionInfo r2 =
                revisionService.recordRevision("prj-rev", "ten-1", snap2, v2, "sync", null, null, "edit");

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
        InternalTimelineWriter writer = new InternalTimelineWriter(new TimelineExtensionsReader());
        String v1 = writer.toJson(spec);
        String snap1 = snapshotService.save("prj-patch", "ten-1", v1, "internal-1.0");
        TimelineRevisionService.RevisionInfo head =
                revisionService.recordRevision("prj-patch", "ten-1", snap1, v1, "sync", null, null, "base");

        var noOps = revisionService.previewPatchReplay(head.id());
        assertFalse(noOps.hasPatchOps());
    }

    @Test
    void listHistoryFiltersBySourceAndAuthor() {
        TimelineSpec spec = TimelineSpec.create("tl-filter", "F", TimelineOutputSpec.mp4_1080p30());
        InternalTimelineWriter writer = new InternalTimelineWriter(new TimelineExtensionsReader());
        String v1 = writer.toJson(spec);
        String snap1 = snapshotService.save("prj-filter", "ten-1", v1, "internal-1.0");
        revisionService.recordRevision("prj-filter", "ten-1", snap1, v1, "sync", "alice", null, "alice edit");

        TimelineSpec spec2 = TimelineSpec.create("tl-filter-2", "F2", TimelineOutputSpec.mp4_1080p30());
        String v2 = writer.toJson(spec2);
        String snap2 = snapshotService.save("prj-filter", "ten-1", v2, "internal-1.0");
        revisionService.recordRevision("prj-filter", "ten-1", snap2, v2, "ai-adopt", "bob", null, "bob adopt");

        assertEquals(1, revisionService.listHistory("prj-filter", null, "alice", null, 10).size());
        assertEquals(1, revisionService.listHistory("prj-filter", null, null, "ai-adopt", 10).size());
        assertEquals(2, revisionService.listHistory("prj-filter", null, null, null, 10).size());
    }

    @Test
    void listFacetsReturnsSourcesAndAuthors() {
        TimelineSpec spec = TimelineSpec.create("tl-facet", "F", TimelineOutputSpec.mp4_1080p30());
        String v1 = new InternalTimelineWriter(new TimelineExtensionsReader()).toJson(spec);
        String snap1 = snapshotService.save("prj-facet", "ten-1", v1, "internal-1.0");
        revisionService.recordRevision("prj-facet", "ten-1", snap1, v1, "sync", "alice", null, "a");
        TimelineSpec spec2 = TimelineSpec.create("tl-facet-2", "F2", TimelineOutputSpec.mp4_1080p30());
        String v2 = new InternalTimelineWriter(new TimelineExtensionsReader()).toJson(spec2);
        String snap2 = snapshotService.save("prj-facet", "ten-1", v2, "internal-1.0");
        revisionService.recordRevision("prj-facet", "ten-1", snap2, v2, "ai-adopt", "bob", null, "b");

        var facets = revisionService.listFacets("prj-facet");
        assertTrue(facets.sources().contains("sync"));
        assertTrue(facets.sources().contains("ai-adopt"));
        assertEquals(2, facets.authors().size());
    }

    @Test
    void updateAnnotationPersistsMessage() {
        TimelineSpec spec = TimelineSpec.create("tl-note", "Note", TimelineOutputSpec.mp4_1080p30());
        String v1 = new InternalTimelineWriter(new TimelineExtensionsReader()).toJson(spec);
        String snap1 = snapshotService.save("prj-note", "ten-1", v1, "internal-1.0");
        TimelineRevisionService.RevisionInfo head =
                revisionService.recordRevision("prj-note", "ten-1", snap1, v1, "sync", null, null, "before");

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
        String v1 = new InternalTimelineWriter(new TimelineExtensionsReader()).toJson(spec);
        String snap1 = snapshotService.save("prj-steps", "ten-1", v1, "internal-1.0");
        TimelineRevisionService.RevisionInfo head =
                revisionService.recordRevision("prj-steps", "ten-1", snap1, v1, "sync", null, null, "base");

        var steps = revisionService.previewPatchSteps(head.id());
        assertFalse(steps.hasPatchOps());
        assertTrue(steps.steps().isEmpty());
    }
}
