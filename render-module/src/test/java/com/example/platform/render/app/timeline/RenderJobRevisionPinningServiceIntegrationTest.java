package com.example.platform.render.app.timeline;

import com.example.platform.timeline.app.ProductCurrentRevisionService;import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.shared.time.MediaTime;

import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
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
    private ProductCurrentRevisionService currentRevisionService;

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
        currentRevisionService = new ProductCurrentRevisionService(dsl);
        revisionSaveService = new TimelineRevisionSaveService(dsl, currentRevisionService, new TimelineContentDigester());
        pinningService = new RenderJobRevisionPinningService(dsl);
    }

    @Test
    void renderJob_pinnedToRevision() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        String jobId = "job-test-" + UUID.randomUUID();
        var doc = createSampleDocument();

        var revision = revisionSaveService.saveRevision(productId, null, doc, "user-1");

        pinningService.createRenderJobWithRevision(jobId, productId, revision.revisionId(), "ffmpeg");

        String pinnedRevision = pinningService.getPinnedRevisionId(jobId);
        assertEquals(revision.revisionId(), pinnedRevision);
    }

    @Test
    void unknownBackend_rejected() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        String jobId = "job-test-" + UUID.randomUUID();
        var doc = createSampleDocument();

        var revision = revisionSaveService.saveRevision(productId, null, doc, "user-1");

        assertThrows(IllegalArgumentException.class, () ->
                pinningService.createRenderJobWithRevision(jobId, productId, revision.revisionId(), "unknown-backend"));
    }

    @Test
    void crossProductRevision_rejected() {
        String productId1 = "prod-test-" + UUID.randomUUID();
        String productId2 = "prod-test-" + UUID.randomUUID();
        insertProduct(productId1);
        insertProduct(productId2);
        String jobId = "job-test-" + UUID.randomUUID();
        var doc = createSampleDocument();

        var revision = revisionSaveService.saveRevision(productId1, null, doc, "user-1");

        // Try to pin job from product2 to product1's revision
        assertThrows(IllegalArgumentException.class, () ->
                pinningService.createRenderJobWithRevision(jobId, productId2, revision.revisionId(), "ffmpeg"));
    }

    @Test
    void retry_retainsOriginalRevisionPinning() {
        String productId = "prod-test-" + UUID.randomUUID();
        insertProduct(productId);
        String originalJobId = "job-test-" + UUID.randomUUID();
        String retryJobId = "job-retry-" + UUID.randomUUID();
        var doc = createSampleDocument();

        var revision = revisionSaveService.saveRevision(productId, null, doc, "user-1");

        pinningService.createRenderJobWithRevision(originalJobId, productId, revision.revisionId(), "ffmpeg");

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

        var rev1 = revisionSaveService.saveRevision(productId, null, doc1, "user-1");

        pinningService.createRenderJobWithRevision(jobId, productId, rev1.revisionId(), "ffmpeg");

        // Save new revision (changes product current)
        var rev2 = revisionSaveService.saveRevision(productId, rev1.revisionId(), doc2, "user-1");

        // RenderJob should still be pinned to rev1
        String pinnedRevision = pinningService.getPinnedRevisionId(jobId);
        assertEquals(rev1.revisionId(), pinnedRevision);
        assertNotEquals(rev2.revisionId(), pinnedRevision);
    }

    @Test
    void canonicalBackends_allSupported() {
        var backends = RenderJobRevisionPinningService.getCanonicalBackends();
        assertTrue(backends.contains("ffmpeg"));
        assertTrue(backends.contains("remotion"));
        assertTrue(backends.contains("gpac"));
        assertTrue(backends.contains("blender"));
        assertEquals(4, backends.size());
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
}