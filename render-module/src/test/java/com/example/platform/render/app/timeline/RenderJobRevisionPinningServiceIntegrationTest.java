package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.TimelineRevisionRefMutation;import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.render.testsupport.RenderInitiatorFixtures;
import com.example.platform.render.infrastructure.RenderJobRepository;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT;
import static org.junit.jupiter.api.Assertions.*;

class RenderJobRevisionPinningServiceIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private RenderJobRevisionPinningService pinningService;
    private TimelineRevisionSaveService revisionSaveService;
    private TimelineRevisionRefMutation currentRevisionService;

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
        com.example.platform.shared.web.TenantContext.set("tenant-1");
        RenderTestSchemaFixture.truncate(dsl);
        currentRevisionService = new TimelineRevisionRefMutation(dsl);
        revisionSaveService = new TimelineRevisionSaveService(dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class), effectAuthority(), revisionSemanticContextStore(), new DefaultTimelineRevisionPersistence(), new TimelineRevisionRefHeadUpdateAdapter(currentRevisionService), com.example.platform.render.testsupport.TimelineMutationTestSupport.ALLOW_ALL);
        pinningService = new RenderJobRevisionPinningService(dsl, new RenderJobRepository(dsl));
    }

    @Test
    void renderJob_pinnedToRevision() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        String jobId = "job-test-" + UUID.randomUUID();
        var doc = createSampleDocument();

        var revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(revisionSaveService,
                "tenant-1", productId, null, doc, RenderTestSchemaFixture.SERVER_ACTOR);

        pinningService.createRenderJobWithRevision(jobId, productId, revision.revisionId(), "provider-a",
                RenderInitiatorFixtures.user("tenant-1"));

        String pinnedRevision = pinningService.getPinnedRevisionId(jobId);
        assertEquals(revision.revisionId(), pinnedRevision);
    }

    @Test
    void arbitraryBoundBackend_acceptedStructurally() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        String jobId = "job-test-" + UUID.randomUUID();
        var doc = createSampleDocument();

        var revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(revisionSaveService,
                "tenant-1", productId, null, doc, RenderTestSchemaFixture.SERVER_ACTOR);

        assertDoesNotThrow(() ->
                pinningService.createRenderJobWithRevision(jobId, productId, revision.revisionId(), "unknown-backend",
                        RenderInitiatorFixtures.user("tenant-1")));
    }

    @Test
    void unboundBackendIdentities_areRejected() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        var revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(revisionSaveService,
                "tenant-1", productId, null, createSampleDocument(),
                RenderTestSchemaFixture.SERVER_ACTOR);

        for (String backend : new String[] {null, "", "   ", "provider", "Provider", " provider "}) {
            String jobId = "job-test-" + UUID.randomUUID();
            assertThrows(IllegalArgumentException.class, () ->
                    pinningService.createRenderJobWithRevision(
                            jobId, productId, revision.revisionId(), backend,
                            RenderInitiatorFixtures.user("tenant-1")));
        }
    }

    @Test
    void crossProductRevision_rejected() {
        String productId1 = "prod-test-" + UUID.randomUUID();
        String productId2 = "prod-test-" + UUID.randomUUID();
        insertProduct(productId1);
        insertProduct(productId2);
        String jobId = "job-test-" + UUID.randomUUID();
        var doc = createSampleDocument();

        var revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(revisionSaveService,
                "tenant-1", productId1, null, doc, RenderTestSchemaFixture.SERVER_ACTOR);

        // Try to pin job from product2 to product1's revision
        assertThrows(IllegalArgumentException.class, () ->
                pinningService.createRenderJobWithRevision(jobId, productId2, revision.revisionId(), "provider-a",
                        RenderInitiatorFixtures.user("tenant-1")));
    }

    @Test
    void retry_retainsOriginalRevisionPinning() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        String originalJobId = "job-test-" + UUID.randomUUID();
        String retryJobId = "job-retry-" + UUID.randomUUID();
        var doc = createSampleDocument();

        var revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(revisionSaveService,
                "tenant-1", productId, null, doc, RenderTestSchemaFixture.SERVER_ACTOR);

        pinningService.createRenderJobWithRevision(originalJobId, productId, revision.revisionId(), "provider-a",
                RenderInitiatorFixtures.user("tenant-1"));

        // Create retry
        String retryPinned = pinningService.createRetryJob(originalJobId, retryJobId);

        assertEquals(revision.revisionId(), retryPinned);
        assertEquals(revision.revisionId(), pinningService.getPinnedRevisionId(retryJobId));
    }

    @Test
    void productCurrentChange_doesNotAffectPinnedJob() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        String jobId = "job-test-" + UUID.randomUUID();
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocumentWithDifferentClip();

        var rev1 = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(revisionSaveService,
                "tenant-1", productId, null, doc1, RenderTestSchemaFixture.SERVER_ACTOR);

        pinningService.createRenderJobWithRevision(jobId, productId, rev1.revisionId(), "provider-a",
                RenderInitiatorFixtures.user("tenant-1"));

        // Save new revision (changes product current)
        var rev2 = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(revisionSaveService,
                "tenant-1", productId, rev1.revisionId(), doc2,
                RenderTestSchemaFixture.SERVER_ACTOR);

        // RenderJob should still be pinned to rev1
        String pinnedRevision = pinningService.getPinnedRevisionId(jobId);
        assertEquals(rev1.revisionId(), pinnedRevision);
        assertNotEquals(rev2.revisionId(), pinnedRevision);
    }

    @Test
    void boundIdentityValidationHasNoConcreteAllowlist() {
        assertTrue(RenderJobRevisionPinningService.isBoundBackendIdentity("provider-a"));
        assertTrue(RenderJobRevisionPinningService.isBoundBackendIdentity("unknown-backend"));
        assertFalse(RenderJobRevisionPinningService.isBoundBackendIdentity(null));
        assertFalse(RenderJobRevisionPinningService.isBoundBackendIdentity(""));
        assertFalse(RenderJobRevisionPinningService.isBoundBackendIdentity("   "));
        assertFalse(RenderJobRevisionPinningService.isBoundBackendIdentity("provider"));
        assertFalse(RenderJobRevisionPinningService.isBoundBackendIdentity("Provider"));
        assertFalse(RenderJobRevisionPinningService.isBoundBackendIdentity(" provider "));
    }

    private void insertProduct(String productId) {
        RenderTestSchemaFixture.insertCanonicalProject(dsl, "tenant-1", productId);
        dsl.insertInto(PRODUCT)
                .set(PRODUCT.PRODUCT_ID, productId)
                .set(PRODUCT.TENANT_ID, "tenant-1")
                .set(PRODUCT.PRODUCT_TYPE, "video")
                .set(PRODUCT.REPRESENTATION_KIND, "master")
                .set(PRODUCT.STATUS, "REGISTERED")
                .set(PRODUCT.TENANT_ID, "tenant-1")
                .set(PRODUCT.PROJECT_ID, productId)
                .set(PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
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
