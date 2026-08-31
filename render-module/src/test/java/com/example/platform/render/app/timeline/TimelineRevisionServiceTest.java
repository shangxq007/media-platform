package com.example.platform.render.app.timeline;

import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.app.TimelineRevisionDiffService;
import com.example.platform.timeline.app.TimelineRevisionQueryService;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;
import com.example.platform.timeline.app.TimelineRevisionRefMutation;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.revisioncommand.RevisionRef;
import java.util.List;
import java.util.Map;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;

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
    private TimelineSnapshotService snapshotService;
    private TimelineRevisionRefMutation revisionRefMutation;
    private TimelineRevisionSaveService revisionSaveService;

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
        var repo = new TimelineRevisionRepository(dsl);
        var diffService = new TimelineRevisionDiffService();
        revisionRefMutation = new TimelineRevisionRefMutation(dsl);
        revisionSaveService = new TimelineRevisionSaveService(
                dsl, revisionRefMutation, new TimelineContentDigester(), snapshotService,
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class),
                new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority(
                        new com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry.InMemory(),
                        new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore.InMemory()),
                org.mockito.Mockito.mock(
                        com.example.platform.timeline.version.TimelineRevisionSemanticContextStore.class),
                new DefaultTimelineRevisionPersistence(),
                new TimelineRevisionRefHeadUpdateAdapter(revisionRefMutation));
        revisionQueryService = new TimelineRevisionQueryService(
                repo, snapshotService, diffService);
    }

    @Test
    void listHistoryFiltersBySourceAndAuthor() throws Exception {
        saveRevision("prj-filter", "ten-1", "sync", "alice", null, "alice edit");
        saveRevision("prj-filter", "ten-1", "ai-adopt", "bob", null, "bob adopt");

        assertEquals(1, revisionQueryService.listHistory("prj-filter", "ten-1", null, "alice", null, 10).size());
        assertEquals(1, revisionQueryService.listHistory("prj-filter", "ten-1", null, null, "ai-adopt", 10).size());
        assertEquals(2, revisionQueryService.listHistory("prj-filter", "ten-1", null, null, null, 10).size());
    }

    @Test
    void listHistoryIsTenantIsolated() throws Exception {
        saveRevision("prj-iso", "ten-1", "sync", "alice", null, "a");

        // tenant-2 cannot see tenant-1 revisions even for the same projectId
        assertEquals(0, revisionQueryService.listHistory("prj-iso", "ten-2", null, null, null, 10).size());
    }

    @Test
    void listFacetsReturnsSourcesAndAuthors() throws Exception {
        saveRevision("prj-facet", "ten-1", "sync", "alice", null, "a");
        saveRevision("prj-facet", "ten-1", "ai-adopt", "bob", null, "b");

        var facets = revisionQueryService.listFacets("prj-facet", "ten-1");
        assertTrue(facets.sources().contains("sync"));
        assertTrue(facets.sources().contains("ai-adopt"));
        assertEquals(2, facets.authors().size());
    }

    @Test
    void updateAnnotationPersistsMessage() throws Exception {
        String headId = saveRevision("prj-note", "ten-1", "sync", null, null, "before");

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
        String headId = saveRevision("prj-note2", "ten-1", "sync", null, null, "before");

        // wrong project: no update, no leak
        assertTrue(revisionQueryService.updateAnnotation(
                "prj-other", "ten-1", headId, "x", List.of()).isEmpty());
        // wrong tenant: no update, no leak
        assertTrue(revisionQueryService.updateAnnotation(
                "prj-note2", "ten-9", headId, "x", List.of()).isEmpty());
    }

    @Test
    void findByIdRequiresExactProjectAndTenant() throws Exception {
        String headId = saveRevision("prj-find", "ten-1", "sync", null, null, "a");

        // correct ownership -> found
        assertTrue(revisionQueryService.findById("prj-find", "ten-1", headId).isPresent());
        // wrong project -> empty (no load-then-check leak)
        assertTrue(revisionQueryService.findById("prj-other", "ten-1", headId).isEmpty());
        // wrong tenant -> empty
        assertTrue(revisionQueryService.findById("prj-find", "ten-9", headId).isEmpty());
    }

    @Test
    void getDetailIsOwnershipScopedNoLoadThenCheck() throws Exception {
        String headId = saveRevision("prj-det", "ten-1", "sync", null, null, "a");

        assertTrue(revisionQueryService.getDetail("prj-det", "ten-1", headId).isPresent());
        // foreign project -> empty (persistence predicate excludes)
        assertTrue(revisionQueryService.getDetail("prj-other", "ten-1", headId).isEmpty());
        // foreign tenant -> empty
        assertTrue(revisionQueryService.getDetail("prj-det", "ten-9", headId).isEmpty());
    }

    /**
     * Seed query projections through the sole canonical revision save authority.
     * Source, author, session, and message are non-canonical query annotations,
     * so the fixture sets them only after the canonical snapshot/revision/ref
     * transition has completed.
     */
    private String saveRevision(
            String projectId, String tenantId, String source,
            String authorUserId, String editSessionId, String message) {
        dsl.execute("insert into project (id, tenant_id, name, status, created_at) "
                        + "values (?, ?, ?, 'ACTIVE', now()) on conflict (id) do nothing",
                projectId, tenantId, projectId);
        dsl.execute("insert into product (product_id, tenant_id, project_id, product_type, "
                        + "representation_kind, status, created_at, updated_at) "
                        + "values (?, ?, ?, 'video', 'master', 'REGISTERED', now(), now()) "
                        + "on conflict (product_id) do nothing",
                projectId, tenantId, projectId);

        TimelineDocument document = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack(
                        "track-" + projectId, "Main", TrackType.VIDEO,
                        List.of(new TimelineClip(
                                "clip-" + java.util.UUID.randomUUID(), "asset-test",
                                null, null, null,
                                MediaTime.ZERO, MediaTime.ofRational(1, 1),
                                MediaTime.ZERO, MediaTime.ZERO, "MEDIA_STREAM")))),
                new TimelineMetadata(projectId, "", Map.of()));
        RevisionRef mainRef = RevisionRef.main(tenantId, projectId);
        String expectedHead = revisionRefMutation.currentHead(mainRef);
        var revision = revisionSaveService.saveRevision(
                projectId, expectedHead, document,
                authorUserId != null ? authorUserId : "query-fixture");

        dsl.update(TIMELINE_REVISION)
                .set(TIMELINE_REVISION.SOURCE, source)
                .set(TIMELINE_REVISION.AUTHOR_USER_ID, authorUserId)
                .set(TIMELINE_REVISION.EDIT_SESSION_ID, editSessionId)
                .set(TIMELINE_REVISION.MESSAGE, message)
                .where(TIMELINE_REVISION.ID.eq(revision.revisionId()))
                .and(TIMELINE_REVISION.PROJECT_ID.eq(projectId))
                .and(TIMELINE_REVISION.TENANT_ID.eq(tenantId))
                .execute();
        return revision.revisionId();
    }
}
