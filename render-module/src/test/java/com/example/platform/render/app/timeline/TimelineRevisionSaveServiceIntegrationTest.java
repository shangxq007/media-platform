package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.canonical.TimelineContentDigester;
import com.example.platform.render.domain.timeline.canonical.TimelineDocument;
import com.example.platform.render.domain.timeline.canonical.TimelineClip;
import com.example.platform.render.domain.timeline.canonical.TimelineTrack;
import com.example.platform.render.domain.timeline.canonical.TimelineMetadata;
import com.example.platform.render.domain.timeline.canonical.TrackType;
import com.example.platform.render.domain.timeline.version.TimelineRevision;
import com.example.platform.render.domain.timeline.version.TimelineConflictException;
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
import static org.junit.jupiter.api.Assertions.*;

class TimelineRevisionSaveServiceIntegrationTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
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
        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService, new TimelineContentDigester());
    }

    @Test
    void firstRevision_createsRootRevision() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var revision = saveService.saveRevision(productId, null, doc, "user-1");

        assertNotNull(revision.revisionId());
        assertNull(revision.parentRevisionId());
        assertNotNull(revision.contentDigest());
    }

    @Test
    void secondRevision_hasFirstAsParent() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocumentWithDifferentClip();

        var first = saveService.saveRevision(productId, null, doc1, "user-1");
        var second = saveService.saveRevision(productId, first.revisionId(), doc2, "user-1");

        assertEquals(first.revisionId(), second.parentRevisionId());
    }

    @Test
    void optimisticConcurrency_conflictOnStaleExpected() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc1 = createSampleDocument();
        var doc2 = createSampleDocumentWithDifferentClip();

        var first = saveService.saveRevision(productId, null, doc1, "user-1");

        // Second save with first's revisionId as expected should succeed
        saveService.saveRevision(productId, first.revisionId(), doc2, "user-1");

        // Third save with stale expected (first.revisionId) should fail
        assertThrows(TimelineConflictException.class, () ->
                saveService.saveRevision(productId, first.revisionId(), doc1, "user-1"));
    }

    @Test
    void conflictException_hasCorrectFields() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var first = saveService.saveRevision(productId, null, doc, "user-1");
        saveService.saveRevision(productId, first.revisionId(), createSampleDocumentWithDifferentClip(), "user-1");

        try {
            saveService.saveRevision(productId, first.revisionId(), doc, "user-1");
            fail("Should have thrown TimelineConflictException");
        } catch (TimelineConflictException ex) {
            assertEquals(productId, ex.getProductId());
            assertEquals(first.revisionId(), ex.getExpectedRevisionId());
            assertNotNull(ex.getMessage());
            assertTrue(ex.getMessage().contains("conflict") || ex.getMessage().contains("Conflict"));
        }
    }

    @Test
    void revisionHistory_lineageIsCorrect() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var rev1 = saveService.saveRevision(productId, null, doc, "user-1");
        var rev2 = saveService.saveRevision(productId, rev1.revisionId(), createSampleDocumentWithDifferentClip(), "user-1");
        var rev3 = saveService.saveRevision(productId, rev2.revisionId(), createSampleDocument(), "user-1");

        // Verify lineage
        assertNull(rev1.parentRevisionId());
        assertEquals(rev1.revisionId(), rev2.parentRevisionId());
        assertEquals(rev2.revisionId(), rev3.parentRevisionId());
    }

    @Test
    void contentDigest_deterministic() {
        String productId = "prod-test-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        var doc = createSampleDocument();

        var rev1 = saveService.saveRevision(productId, null, doc, "user-1");
        var rev2 = saveService.saveRevision(productId, rev1.revisionId(), doc, "user-1");

        // Same content should produce same digest
        assertNotNull(rev1.contentDigest());
        assertNotNull(rev2.contentDigest());
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
        var clip = new TimelineClip("clip-1", "asset-1",
                Duration.ofSeconds(0), Duration.ofSeconds(10),
                Duration.ZERO, Duration.ZERO);
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }

    private TimelineDocument createSampleDocumentWithDifferentClip() {
        var clip = new TimelineClip("clip-2", "asset-2",
                Duration.ofSeconds(5), Duration.ofSeconds(15),
                Duration.ZERO, Duration.ZERO);
        var track = new TimelineTrack("track-1", "Main", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), new TimelineMetadata("Test", "", Map.of()));
    }
}
