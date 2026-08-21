package com.example.platform.render.app.timeline;

import static com.example.platform.typedschema.jooq.generated.tables.TimelineRevision.TIMELINE_REVISION;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.artifact.app.ArtifactPinService;
import com.example.platform.artifact.domain.Artifact;
import com.example.platform.artifact.domain.ArtifactKind;
import com.example.platform.artifact.domain.ArtifactMediaType;
import com.example.platform.artifact.domain.ArtifactQueryService;
import com.example.platform.artifact.domain.ArtifactState;
import com.example.platform.artifact.infrastructure.ArtifactPinRepository;
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
 * CHECKPOINT_A Round 4 (R4-D2): REAL ArtifactPinRepository / ArtifactPinService
 * PostgreSQL atomicity proof — no mocked pin service.
 *
 * <p>Success: saveRevision(pinned doc) with the REAL repository chain commits
 * timeline_revision + artifact_pin + head atomically; the pin row carries the
 * NEW revision id + exact artifact id + exact content digest.
 *
 * <p>Failure: an ACTUAL DB constraint violation during pin persistence (the
 * artifact FK on artifact_pin rejects a ghost artifact) rolls back the WHOLE
 * save — no revision row, no pin rows, head unchanged. This proves
 * registerRevisionPinsTx(tx.dsl(), ...) joins the SAME physical transaction
 * as the revision insert (no Spring proxy assumption).
 */
class CheckpointARound4RealPinAtomicityIT extends PostgresTestContainerSupport {

    private static final String TENANT = "tenant-r4-it";
    private static final String DIGEST_HEX = "a".repeat(64);

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
    private ProductCurrentRevisionService currentRevisionService;
    private ArtifactPinRepository pinRepository;
    private ArtifactPinService pinService;
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
        // REAL repository + REAL service (no mocks)
        pinRepository = new ArtifactPinRepository(dsl);
        pinService = new ArtifactPinService(pinRepository);
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

    private void insertArtifact(String artifactId, String digest) {
        dsl.insertInto(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.ID, artifactId)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.TENANT_ID, TENANT)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.CONTENT_DIGEST, digest)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.BYTE_LENGTH, 1024L)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.MEDIA_TYPE, "VIDEO")
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.ARTIFACT_KIND, "SOURCE_MEDIA")
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.STATE, "AVAILABLE")
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.SCHEMA_VERSION, 1)
                .set(com.example.platform.typedschema.jooq.generated.tables.Artifact.ARTIFACT.CREATED_AT, java.time.LocalDateTime.now())
                .execute();
    }

    private static TimelineDocument pinnedDoc(String artifactId, String digest) {
        TimelineClip clip = new TimelineClip(
                "c1", "asset-1", "stream-1", artifactId, digest,
                MediaTime.ZERO, MediaTime.ofTicks(30, 1),
                MediaTime.ZERO, MediaTime.ofTicks(30, 1), "MEDIA_STREAM", null);
        TimelineTrack track = new TimelineTrack("v1", "v1", TrackType.VIDEO, List.of(clip));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());
    }

    @Test
    void realPinRepositorySuccessCommitsRevisionPinsAndHead() {
        String productId = "prod-r4-real-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        insertArtifact("art-1", DIGEST_HEX);

        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = new Artifact(new ArtifactId("art-1"), TENANT,
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, DIGEST_HEX), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                1, java.time.Instant.EPOCH);
        when(query.getArtifact(anyString(), any())).thenReturn(Optional.of(artifact));

        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService,
                new TimelineContentDigester(), snapshotService,
                new TimelineArtifactPinValidator(query), pinService, effectAuthority(), revisionSemanticContextStore());

        var revision = saveService.saveRevision(productId, null, pinnedDoc("art-1", DIGEST_HEX), "user-1");
        assertNotNull(revision);

        // timeline_revision row = 1
        assertEquals(1, dsl.fetchCount(DSL.selectFrom(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))), "revision committed");

        // artifact_pin row = 1 with EXACT revision id / artifact id / digest
        var pinRow = dsl.selectFrom(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.PROJECT_ID.eq(productId))
                .fetchOne();
        assertNotNull(pinRow, "real pin row must exist");
        assertEquals(revision.revisionId(), pinRow.get(
                com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.REVISION_ID),
                "pin must be registered for the NEW revision id");
        assertEquals("art-1", pinRow.get(
                com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.ARTIFACT_ID),
                "exact artifact id");
        assertEquals(DIGEST_HEX, pinRow.get(
                com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.CONTENT_DIGEST),
                "exact content digest");

        // head
        assertEquals(revision.revisionId(), currentRevisionService.getCurrentRevisionId(productId),
                "head updated to the new revision");
    }

    @Test
    void realPinRepositoryFkViolationRollsBackWholeSave() {
        // R5-C6 (CORRECTED): the validator MUST return VALID — the failure must
        // occur at the REAL ArtifactPinRepository INSERT inside the save
        // transaction, NOT at the validation layer. The ArtifactQueryService
        // mock answers as if the artifact exists (validator passes); the DB
        // artifact row is intentionally absent, so the artifact_pin FK
        // constraint (fk_pin_artifact → artifact.id) fires on the real INSERT.
        String productId = "prod-r5-fk-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        // NOTE: no artifact row inserted — the artifact_pin FK rejects the pin
        // insert at statement time (fk_pin_artifact).

        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = new Artifact(new ArtifactId("ghost-art"), TENANT,
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, DIGEST_HEX), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                1, java.time.Instant.EPOCH);
        // Validator returns VALID for the pinned artifact (existence + tenant +
        // digest all pass through the mock query).
        when(query.getArtifact(anyString(), any())).thenReturn(Optional.of(artifact));

        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService,
                new TimelineContentDigester(), snapshotService,
                new TimelineArtifactPinValidator(query), pinService, effectAuthority(), revisionSemanticContextStore());

        // The pin FK constraint (artifact_pin.artifact_id → artifact.id) fires
        // INSIDE the save transaction: ghost artifact id → statement failure →
        // the whole dsl.transactionResult rolls back. Validation already
        // passed; this is a REAL database persistence failure.
        assertThrows(Exception.class,
                () -> saveService.saveRevision(productId, null, pinnedDoc("ghost-art", DIGEST_HEX), "user-1"),
                "ghost artifact must fail the real pin INSERT inside the save transaction");

        // no revision row
        assertEquals(0, dsl.fetchCount(DSL.selectFrom(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))),
                "no revision may be durable after real pin failure");
        // no pin rows
        assertEquals(0, dsl.fetchCount(DSL.selectFrom(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.PROJECT_ID.eq(productId))),
                "no partial pin rows after rollback");
        // head unchanged (never set)
        assertTrue(currentRevisionService.getCurrentRevisionId(productId) == null
                        || currentRevisionService.getCurrentRevisionId(productId).isBlank(),
                "head must remain unchanged");
    }

    @Test
    void realPinRepositoryPartialPinWriteRollsBackEntirely() {
        // R5-C7 (strong proof): TWO pins — pin 1 references a REAL artifact row
        // (its INSERT would succeed), pin 2 references a ghost artifact (its
        // INSERT fails the FK). The validator passes BOTH (mock query returns
        // VALID for both). The failure therefore occurs at the SECOND real pin
        // INSERT, AFTER the first pin row was written inside the transaction.
        // Rollback must remove BOTH — artifact_pin count for the new revision
        // must be ZERO (no partial pin set survives).
        String productId = "prod-r5-partial-" + java.util.UUID.randomUUID();
        insertProduct(productId);
        insertArtifact("art-1", DIGEST_HEX); // real row → pin 1 insertable

        ArtifactQueryService query = mock(ArtifactQueryService.class);
        Artifact artifact = new Artifact(new ArtifactId("art-1"), TENANT,
                new ContentDigest(ContentDigest.DigestAlgorithm.SHA_256, DIGEST_HEX), 1024L,
                ArtifactMediaType.VIDEO, ArtifactKind.SOURCE_MEDIA, ArtifactState.AVAILABLE,
                1, java.time.Instant.EPOCH);
        // Validator answers VALID for EVERY artifact id (both pins pass
        // validation — the DB layer decides).
        when(query.getArtifact(anyString(), any())).thenReturn(Optional.of(artifact));

        saveService = new TimelineRevisionSaveService(dsl, currentRevisionService,
                new TimelineContentDigester(), snapshotService,
                new TimelineArtifactPinValidator(query), pinService, effectAuthority(), revisionSemanticContextStore());

        // Two pinned clips: art-1 (real artifact row) + ghost-art (no row).
        TimelineClip clip1 = new TimelineClip(
                "c1", "asset-1", "stream-1", "art-1", DIGEST_HEX,
                MediaTime.ZERO, MediaTime.ofTicks(30, 1),
                MediaTime.ZERO, MediaTime.ofTicks(30, 1), "MEDIA_STREAM", null);
        TimelineClip clip2 = new TimelineClip(
                "c2", "asset-2", "stream-2", "ghost-art", DIGEST_HEX,
                MediaTime.ofTicks(30, 1), MediaTime.ofTicks(60, 1),
                MediaTime.ZERO, MediaTime.ofTicks(30, 1), "MEDIA_STREAM", null);
        TimelineTrack track = new TimelineTrack("v1", "v1", TrackType.VIDEO, List.of(clip1, clip2));
        TimelineDocument doc = new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(track), TimelineMetadata.empty());

        assertThrows(Exception.class,
                () -> saveService.saveRevision(productId, null, doc, "user-1"),
                "second pin INSERT (ghost artifact) must fail the whole save");

        // No revision row.
        assertEquals(0, dsl.fetchCount(DSL.selectFrom(TIMELINE_REVISION)
                .where(TIMELINE_REVISION.PROJECT_ID.eq(productId))),
                "no revision may be durable after partial pin failure");
        // artifact_pin count for the project = 0 — pin 1's successful INSERT
        // was rolled back together with pin 2's failure.
        assertEquals(0, dsl.fetchCount(DSL.selectFrom(
                        com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN)
                .where(com.example.platform.typedschema.jooq.generated.tables.ArtifactPin.ARTIFACT_PIN.PROJECT_ID.eq(productId))),
                "no partial pin set may survive (pin 1 rolled back with pin 2 failure)");
        // Head unchanged.
        assertTrue(currentRevisionService.getCurrentRevisionId(productId) == null
                        || currentRevisionService.getCurrentRevisionId(productId).isBlank(),
                "head must remain unchanged");
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
