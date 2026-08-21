package com.example.platform.render.app.timeline;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.ProductCurrentRevisionHeadUpdateAdapter;

import com.example.platform.artifact.app.ArtifactPinService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimelineClip;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.render.testsupport.RenderTestSchemaFixture;
import com.example.platform.shared.test.PostgresTestContainerSupport;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CHECKPOINT_A (Round 3) — SAME-PATH real-PostgreSQL rollback evidence for the
 * corrected TimelineRevisionSaveService write surface (Blocker C, pin Case 4):
 *
 * pin registration failure inside the real transaction must leave:
 * - no durable revision row
 * - head/current unchanged
 * - no partial pin rows
 */
class CheckpointAPinRegistrationRollbackIT extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-it";
    private static final String DIGEST_HEX = "a".repeat(64);

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
    private ProductCurrentRevisionService currentRevisionService;
    private TimelineSnapshotService snapshotService;

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
        snapshotService = new TimelineSnapshotService(dsl);
        TenantContext.set(TENANT);
    }

    private void insertProduct(String productId) {
        dsl.insertInto(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT)
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.PRODUCT_ID, productId)
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.PRODUCT_TYPE, "video")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.REPRESENTATION_KIND, "master")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.STATUS, "REGISTERED")
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.CREATED_AT, java.time.LocalDateTime.now())
                .set(com.example.platform.typedschema.jooq.generated.tables.Product.PRODUCT.UPDATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private static TimelineDocument pinnedDoc() {
        TimelineClip clip = new TimelineClip(
                "c1", "asset-1", "stream-1", "art-1", DIGEST_HEX,
                MediaTime.ZERO, MediaTime.ofTicks(30, 1),
                MediaTime.ZERO, MediaTime.ofTicks(30, 1), "MEDIA_STREAM", null);
        TimelineTrack track = new TimelineTrack("v1", "v1", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());
    }

    @Test
    void pinRegistrationFailureRollsBackWholeTransaction() {
        String productId = "prod-rollback-" + java.util.UUID.randomUUID();
        insertProduct(productId);

        // artifact exists + digest matches → validator passes
        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = new Artifact(new ArtifactId("art-1"), TENANT,
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, DIGEST_HEX), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                1, java.time.Instant.EPOCH);
        when(query.getArtifact(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(artifact));

        // pin registration FAILS inside the same transaction
        ArtifactPinService pinService = mock(ArtifactPinService.class);
        doThrow(new IllegalStateException("simulated pin registration failure"))
                .when(pinService).registerRevisionPinsTx(
                        org.mockito.ArgumentMatchers.any(org.jooq.DSLContext.class),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyList());

        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService,
                new TimelineContentDigester(), snapshotService,
                new TimelineArtifactPinValidator(query), pinService, effectAuthority(), revisionSemanticContextStore(), new DefaultTimelineRevisionPersistence(), new ProductCurrentRevisionHeadUpdateAdapter(currentRevisionService));

        assertThrows(IllegalStateException.class,
                () -> saveService.saveRevision(productId, null, pinnedDoc(), "user-1"),
                "pin registration failure must fail the save");

        // no durable revision row (transaction rolled back)
        int revisionRows = dsl.fetchCount(DSL.selectFrom(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId)));
        assertEquals(0, revisionRows, "no revision may be durable after pin registration failure");

        // head/current unchanged (no current revision ever set)
        assertTrue(currentRevisionService.getCurrentRevisionId(productId) == null
                        || currentRevisionService.getCurrentRevisionId(productId).isBlank(),
                "head/current must remain unchanged");

        // no partial pin rows for the product
        int pinRows = dsl.fetchCount(DSL.selectFrom(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.PROJECT_ID.eq(productId)));
        assertEquals(0, pinRows, "no partial pin rows after rollback");
    }

    @Test
    void validPinsCommitRevisionAndPinsAndHead() {
        String productId = "prod-commit-" + java.util.UUID.randomUUID();
        insertProduct(productId);

        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = new Artifact(new ArtifactId("art-1"), TENANT,
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, DIGEST_HEX), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                1, java.time.Instant.EPOCH);
        when(query.getArtifact(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.any())).thenReturn(Optional.of(artifact));

        ArtifactPinService pinService = mock(ArtifactPinService.class);

        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService,
                new TimelineContentDigester(), snapshotService,
                new TimelineArtifactPinValidator(query), pinService, effectAuthority(), revisionSemanticContextStore(), new DefaultTimelineRevisionPersistence(), new ProductCurrentRevisionHeadUpdateAdapter(currentRevisionService));

        var revision = saveService.saveRevision(productId, null, pinnedDoc(), "user-1");
        assertEquals(1, dsl.fetchCount(DSL.selectFrom(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))), "revision committed");
        assertEquals(revision.revisionId(), currentRevisionService.getCurrentRevisionId(productId),
                "head updated");
        org.mockito.Mockito.verify(pinService).registerRevisionPinsTx(
                org.mockito.ArgumentMatchers.any(org.jooq.DSLContext.class),
                org.mockito.ArgumentMatchers.eq(productId),
                org.mockito.ArgumentMatchers.eq(revision.revisionId()),
                org.mockito.ArgumentMatchers.eq(TENANT),
                org.mockito.ArgumentMatchers.anyList());
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
