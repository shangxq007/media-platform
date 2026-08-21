package com.example.platform.render.app.timeline;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 transaction atomicity TX1-TX5 (§10): every canonical write is a
 * single physical transaction — any failure (definition identity, Effect
 * snapshot store, revision insert, semantic context store, head update)
 * leaves NO accepted revision, NO semantic context, NO head move.
 */
class Roadmap20TransactionAtomicityTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
    private ProductCurrentRevisionService currentRevisionService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = org.jooq.impl.DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
        com.example.platform.render.testsupport.RenderTestSchemaFixture.createSchema(dsl);
    }

    @AfterAll
    static void tearDownDatabase() {
        closeDataSource(dataSource);
    }

    @BeforeEach
    void setUp() {
        com.example.platform.render.testsupport.RenderTestSchemaFixture.truncate(dsl);
        com.example.platform.shared.web.TenantContext.set("tenant-1");
        currentRevisionService = new ProductCurrentRevisionService(dsl);
        JdbcEffectSemanticSnapshotStore jdbcEffectStore = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshotAuthority authority = new EffectSemanticSnapshotAuthority(
                new JdbcEffectDefinitionVersionRegistry(dsl), jdbcEffectStore);
        saveService = new TimelineRevisionSaveService(
                dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                new com.example.platform.timeline.app.TimelineArtifactPinValidator(
                        new com.example.platform.artifact.infrastructure.JooqArtifactQueryService(
                                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl),
                                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl))),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)),
                authority, new JdbcTimelineRevisionSemanticContextStore(dsl));
    }

    private void insertFixtures(String productId) {
        dsl.execute("insert into product (product_id, tenant_id, project_id, product_type, representation_kind, status, created_at, updated_at) "
                + "values (?, 'tenant-1', ?, 'video', 'master', 'REGISTERED', now(), now()) on conflict (product_id) do nothing",
                productId, productId);
        dsl.execute("insert into artifact (id, tenant_id, content_digest, byte_length, media_type, artifact_kind, state, schema_version, created_at) "
                + "values ('art-1', 'tenant-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 100, 'VIDEO', 'SOURCE_MEDIA', 'AVAILABLE', 1, now()) "
                + "on conflict (id) do nothing");
    }

    private com.example.platform.timeline.canonical.TimelineDocument sampleDocument() {
        com.example.platform.shared.time.MediaTime m0 = com.example.platform.shared.time.MediaTime.ofRational(0, 1);
        com.example.platform.shared.time.MediaTime m2 = com.example.platform.shared.time.MediaTime.ofRational(2, 1);
        com.example.platform.timeline.canonical.TimelineClip tc = new com.example.platform.timeline.canonical.TimelineClip(
                "c1", "asset-1", "stream-1", "art-1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                m0, m2, m0, m2, "MEDIA_STREAM",
                com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping.of(
                        1, 1, com.example.platform.timeline.semantics.temporal.PlaybackDirection.FORWARD));
        return new com.example.platform.timeline.canonical.TimelineDocument(
                com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new com.example.platform.timeline.canonical.TimelineTrack(
                        "t1", "v1", com.example.platform.timeline.canonical.TrackType.VIDEO, List.of(tc))),
                com.example.platform.timeline.canonical.TimelineMetadata.empty(),
                com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of());
    }

    private EffectInstance effect(String id, String radius) {
        return new EffectInstance(
                id, "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(
                        com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", radius), Map.of(),
                new com.example.platform.timeline.semantics.effect.ClipEffectTarget("t1", "c1"),
                EffectInstance.EffectProvenance.untracked());
    }

    private EffectInstance.EffectDefinition def(String radius) {
        return new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of("radiusPixels", new EffectInstance.ParameterSchema(
                        "radiusPixels", "string", null, null, radius, List.of())),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("radiusPixels"), List.of("video.effect.gaussian-blur"), List.of());
    }

    private long countRevisions(String productId) {
        return dsl.fetchOne("select count(1) from timeline_revision where project_id = ?", productId)
                .get(0, Long.class);
    }

    private long countSnapshots(String productId) {
        return dsl.fetchOne("select count(1) from timeline_snapshot where project_id = ?", productId)
                .get(0, Long.class);
    }

    private String currentRevision(String productId) {
        var row = dsl.fetchOne("select current_revision_id from product where product_id = ?", productId);
        return row == null ? null : row.get(0, String.class);
    }

    @Test
    void tx1_definitionIdentityFailureLeavesNoRevisionOrHead() {
        String p1 = "prod-tx1a-" + UUID.randomUUID();
        String p2 = "prod-tx1b-" + UUID.randomUUID();
        insertFixtures(p1);
        insertFixtures(p2);
        saveService.saveRevisionWithEffects(
                p1, null, sampleDocument(), List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        // conflicting definition identity in a DIFFERENT product
        assertThrows(IllegalArgumentException.class, () ->
                saveService.saveRevisionWithEffects(
                        p2, null, sampleDocument(), List.of(effect("eff-2", "77")), List.of(def("77")), "u"));
        assertEquals(0L, countRevisions(p2), "TX1: no accepted revision in p2");
        assertEquals(0L, countSnapshots(p2), "TX1: no snapshot rows in p2");
        assertNull(currentRevision(p2), "TX1: no head move in p2");
    }

    @Test
    void tx2_snapshotStoreFailureLeavesNoRevisionOrHead() {
        String productId = "prod-tx2-" + UUID.randomUUID();
        insertFixtures(productId);
        // store that fails on EVERY write
        EffectSemanticSnapshotStore failingStore = new EffectSemanticSnapshotStore() {
            @Override
            public void store(EffectSemanticSnapshot snapshot) {
                throw new IllegalStateException("TX2 injected snapshot store failure");
            }

            @Override
            public void storeTx(org.jooq.DSLContext tx, String projectId, String tenantId,
                                EffectSemanticSnapshot snapshot) {
                throw new IllegalStateException("TX2 injected snapshot store failure");
            }

            @Override
            public Optional<EffectSemanticSnapshot> findById(String projectId, String tenantId,
                                                             com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId id) {
                return Optional.empty();
            }
        };
        EffectSemanticSnapshotAuthority failingAuthority = new EffectSemanticSnapshotAuthority(
                new JdbcEffectDefinitionVersionRegistry(dsl), failingStore);
        TimelineRevisionSaveService failingSave = new TimelineRevisionSaveService(
                dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                new com.example.platform.timeline.app.TimelineArtifactPinValidator(
                        new com.example.platform.artifact.infrastructure.JooqArtifactQueryService(
                                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl),
                                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl))),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)),
                failingAuthority, new JdbcTimelineRevisionSemanticContextStore(dsl));
        assertThrows(IllegalStateException.class, () ->
                failingSave.saveRevisionWithEffects(
                        productId, null, sampleDocument(),
                        List.of(effect("eff-1", "4")), List.of(def("4")), "u"));
        assertEquals(0L, countRevisions(productId), "TX2: no accepted revision");
        assertEquals(0L, countSnapshots(productId), "TX2: no snapshot rows");
        assertNull(currentRevision(productId), "TX2: no head move");
    }

    @Test
    void tx3_revisionInsertFailureLeavesNoSnapshotOrHead() {
        String productId = "prod-tx3-" + UUID.randomUUID();
        insertFixtures(productId);
        // REAL failure injection: the revision ROW insert (canonical
        // persistence boundary) throws AFTER the governed Timeline snapshot
        // and Effect snapshot have been written inside the same transaction.
        TimelineRevisionSaveService failingSave = new TimelineRevisionSaveService(
                dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                new com.example.platform.timeline.app.TimelineArtifactPinValidator(
                        new com.example.platform.artifact.infrastructure.JooqArtifactQueryService(
                                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl),
                                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl))),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(dsl),
                        new JdbcEffectSemanticSnapshotStore(dsl)),
                new JdbcTimelineRevisionSemanticContextStore(dsl));
        failingSave.setRevisionPersistencePort((tx, revision, project, snapshotId, schemaVersion,
                                                 revisionNumber, tenantId, source) -> {
            throw new IllegalStateException("TX3 injected revision insert failure");
        });
        assertThrows(IllegalStateException.class, () ->
                failingSave.saveRevisionWithEffects(
                        productId, null, sampleDocument(),
                        List.of(effect("eff-1", "4")), List.of(def("4")), "u"));
        // after rollback: no revision row, no revctx row, no transition
        // snapshot rows, head unchanged
        assertEquals(0L, countRevisions(productId), "TX3: no accepted revision");
        assertEquals(0L, countSnapshots(productId), "TX3: no snapshot rows (snap/esnap/revctx all rolled back)");
        assertNull(currentRevision(productId), "TX3: head unchanged");
    }

    @Test
    void tx4_contextStoreFailureLeavesNoRevisionOrHead() {
        String productId = "prod-tx4-" + UUID.randomUUID();
        insertFixtures(productId);
        // REAL failure injection: the revision semantic context storeTx fails
        // (after the revision row insert has been attempted in the same
        // transaction) -> whole canonical transition rolls back.
        com.example.platform.timeline.version.TimelineRevisionSemanticContextStore failingCtxStore =
                new com.example.platform.timeline.version.TimelineRevisionSemanticContextStore() {
                    @Override
                    public void storeTx(org.jooq.DSLContext tx, String projectId, String revisionId,
                                        com.example.platform.timeline.version.TimelineRevisionSemanticContext semanticContext) {
                        throw new IllegalStateException("TX4 injected context store failure");
                    }

                    @Override
                    public java.util.Optional<com.example.platform.timeline.version.TimelineRevisionSemanticContext>
                    findByRevisionId(String revisionId) {
                        return java.util.Optional.empty();
                    }
                };
        TimelineRevisionSaveService failingSave = new TimelineRevisionSaveService(
                dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                new com.example.platform.timeline.app.TimelineArtifactPinValidator(
                        new com.example.platform.artifact.infrastructure.JooqArtifactQueryService(
                                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl),
                                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl))),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(dsl),
                        new JdbcEffectSemanticSnapshotStore(dsl)),
                failingCtxStore);
        assertThrows(IllegalStateException.class, () ->
                failingSave.saveRevisionWithEffects(
                        productId, null, sampleDocument(),
                        List.of(effect("eff-1", "4")), List.of(def("4")), "u"));
        assertEquals(0L, countRevisions(productId), "TX4: no accepted revision");
        assertEquals(0L, countSnapshots(productId), "TX4: no snapshot rows (snap/esnap/revctx rolled back)");
        assertNull(currentRevision(productId), "TX4: no head move");
    }

    @Test
    void tx5_headUpdateFailureRollsBackWholeTransition() {
        String productId = "prod-tx5-" + UUID.randomUUID();
        insertFixtures(productId);
        // REAL failure injection: the head update boundary fails AFTER
        // snapshot + Effect snapshot + revision + revctx have been written in
        // the same transaction -> whole canonical transition rolls back.
        TimelineRevisionSaveService failingSave = new TimelineRevisionSaveService(
                dsl, currentRevisionService, new TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                new com.example.platform.timeline.app.TimelineArtifactPinValidator(
                        new com.example.platform.artifact.infrastructure.JooqArtifactQueryService(
                                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl),
                                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl))),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(dsl),
                        new JdbcEffectSemanticSnapshotStore(dsl)),
                new JdbcTimelineRevisionSemanticContextStore(dsl));
        failingSave.setHeadUpdatePort((tx, project, expected, newRevisionId) -> {
            throw new IllegalStateException("TX5 injected head update failure");
        });
        assertThrows(IllegalStateException.class, () ->
                failingSave.saveRevisionWithEffects(
                        productId, null, sampleDocument(),
                        List.of(effect("eff-1", "4")), List.of(def("4")), "u"));
        assertEquals(0L, countRevisions(productId), "TX5: no revision committed");
        assertEquals(0L, countSnapshots(productId), "TX5: no snapshot rows committed");
        assertNull(currentRevision(productId), "TX5: head unchanged");
    }
}
