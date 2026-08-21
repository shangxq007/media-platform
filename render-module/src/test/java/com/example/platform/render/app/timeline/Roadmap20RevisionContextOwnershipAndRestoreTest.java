package com.example.platform.render.app.timeline;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.PatchApplyResult;
import com.example.platform.timeline.app.PatchPreviewResult;
import com.example.platform.timeline.app.ProductCurrentRevisionHeadUpdateAdapter;
import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelinePatchApplicationService;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import java.util.List;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.patch.PatchErrorCode;
import com.example.platform.timeline.patch.TimelinePatch;
import com.example.platform.timeline.patch.TimelinePatchOperation;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec;
import com.example.platform.timeline.version.TimelineRevisionSemanticContext;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 FINAL F1/F4: revision semantic context ownership attacks
 * (RCOWN1-6) and restore completeness (RST1-6, RSTOWN1-2, RST_TX_HEAD).
 */
class Roadmap20RevisionContextOwnershipAndRestoreTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
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
        for (String p : new String[]{"proj-a", "proj-b"}) {
            dsl.execute("insert into project (id, tenant_id, name, status, created_at) "
                    + "values ('" + p + "', '" + p + "-t', '" + p + "', 'ACTIVE', now()) "
                    + "on conflict (id) do nothing");
        }
        currentRevisionService = new ProductCurrentRevisionService(dsl);
        com.example.platform.shared.web.TenantContext.set("proj-a-t");
        dsl.execute("insert into product (product_id, tenant_id, project_id, product_type, "
                + "representation_kind, status, created_at, updated_at) "
                + "values ('proj-a', 'proj-a-t', 'proj-a', 'video', 'master', 'REGISTERED', now(), now()) "
                + "on conflict (product_id) do nothing");
        dsl.execute("insert into product (product_id, tenant_id, project_id, product_type, "
                + "representation_kind, status, created_at, updated_at) "
                + "values ('proj-b', 'proj-b-t', 'proj-b', 'video', 'master', 'REGISTERED', now(), now()) "
                + "on conflict (product_id) do nothing");
        dsl.execute("insert into artifact (id, tenant_id, content_digest, byte_length, media_type, "
                + "artifact_kind, state, schema_version, created_at) "
                + "values ('art-1', 'proj-a-t', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', "
                + "100, 'VIDEO', 'SOURCE_MEDIA', 'AVAILABLE', 1, now()) "
                + "on conflict (id) do nothing");
    }

    private TimelineRevisionSaveService saveService() {
        return new TimelineRevisionSaveService(
                dsl, currentRevisionService,
                new com.example.platform.timeline.canonical.TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                new TimelineArtifactPinValidator(
                        new com.example.platform.artifact.infrastructure.JooqArtifactQueryService(
                                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl),
                                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl))),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(dsl),
                        new JdbcEffectSemanticSnapshotStore(dsl)),
                new JdbcTimelineRevisionSemanticContextStore(dsl),
                new DefaultTimelineRevisionPersistence(),
                new ProductCurrentRevisionHeadUpdateAdapter(currentRevisionService));
    }

    private static TimelineDocument sampleDocument() {
        return sampleDocument("c1", "asset-1", "stream-1", "art-1");
    }

    /** Sample document with an explicit clip artifact id (per-tenant fixtures). */
    private static TimelineDocument sampleDocument(String clipId, String assetId,
                                                   String streamId, String artifactId) {
        com.example.platform.shared.time.MediaTime m0 = com.example.platform.shared.time.MediaTime.ofRational(0, 1);
        com.example.platform.shared.time.MediaTime m2 = com.example.platform.shared.time.MediaTime.ofRational(2, 1);
        com.example.platform.timeline.canonical.TimelineClip tc = new com.example.platform.timeline.canonical.TimelineClip(
                clipId, assetId, streamId, artifactId,
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                m0, m2, m0, m2, "MEDIA_STREAM",
                com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping.of(
                        1, 1, com.example.platform.timeline.semantics.temporal.PlaybackDirection.FORWARD));
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "v1", TrackType.VIDEO, List.of(tc))),
                TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of());
    }

    private com.example.platform.timeline.version.TimelineRevisionSemanticContext storeRevctx(
            String project, String tenant, String revisionId, TimelineRevisionSemanticContext ctx) {
        new JdbcTimelineRevisionSemanticContextStore(dsl).storeTx(
                dsl, project, tenant, revisionId, ctx);
        return ctx;
    }

    private static TimelineRevisionSemanticContext ctx(String revisionId) {
        com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference ref =
                new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference(
                        EffectSemanticSnapshotId.of("esnap_ctx_" + revisionId),
                        "digest-" + revisionId,
                        com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion.current());
        String timelineDigest = "timeline-" + revisionId;
        String revDigest = com.example.platform.timeline.semantics.effect.TimelineRevisionEffectSemanticCommitment
                .revisionEffectSemanticDigest(timelineDigest, ref);
        return new TimelineRevisionSemanticContext(
                timelineDigest, ref, revDigest, TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1);
    }

    // ---- RCOWN1-6 (F1) ----

    @Test
    void rcown1_crossProjectLookupFailsClosed() {
        storeRevctx("proj-a", "proj-a-t", "rev-1", ctx("rev-1"));
        var store = new JdbcTimelineRevisionSemanticContextStore(dsl);
        assertTrue(store.findByRevisionId(dsl, "proj-b", "proj-a-t", "rev-1").isEmpty(),
                "RCOWN1: cross-project revctx lookup NOT FOUND");
        assertTrue(store.findByRevisionId(dsl, "proj-a", "proj-a-t", "rev-1").isPresent(),
                "RCOWN1: ownership-scoped lookup resolves");
    }

    @Test
    void rcown2_crossTenantLookupFailsClosed() {
        storeRevctx("proj-a", "proj-a-t", "rev-1", ctx("rev-1"));
        var store = new JdbcTimelineRevisionSemanticContextStore(dsl);
        assertTrue(store.findByRevisionId(dsl, "proj-a", "other-t", "rev-1").isEmpty(),
                "RCOWN2: cross-tenant revctx lookup NOT FOUND");
    }

    @Test
    void rcown3_conflictingOwnershipStoreFailsClosed() {
        storeRevctx("proj-a", "proj-a-t", "rev-1", ctx("rev-1"));
        var store = new JdbcTimelineRevisionSemanticContextStore(dsl);
        assertThrows(IllegalStateException.class,
                () -> store.storeTx(dsl, "proj-b", "proj-b-t", "rev-1", ctx("rev-1")),
                "RCOWN3: same revisionId different ownership FAILS CLOSED");
    }

    @Test
    void rcown4_exactSameOwnershipAndContextIdempotent() {
        storeRevctx("proj-a", "proj-a-t", "rev-1", ctx("rev-1"));
        var store = new JdbcTimelineRevisionSemanticContextStore(dsl);
        store.storeTx(dsl, "proj-a", "proj-a-t", "rev-1", ctx("rev-1"));
        assertTrue(store.findByRevisionId(dsl, "proj-a", "proj-a-t", "rev-1").isPresent(),
                "RCOWN4: exact identical context idempotent PASS");
    }

    @Test
    void rcown5_digestCorruptionFailsClosed() {
        storeRevctx("proj-a", "proj-a-t", "rev-1", ctx("rev-1"));

        // corrupt the revctx payload digest via raw JSON tamper
        String stored = dsl.fetchOne("select payload_json from timeline_snapshot where id = ?",
                "revctx_rev-1").get(0, String.class);
        String tampered = stored.replaceFirst(
                "\"revisionSemanticDigest\":\"[^\"]*\"", "\"revisionSemanticDigest\":\"deadbeef\"");
        dsl.execute("update timeline_snapshot set payload_json = ? where id = ?", tampered, "revctx_rev-1");
        var store = new JdbcTimelineRevisionSemanticContextStore(dsl);
        assertThrows(IllegalStateException.class,
                () -> store.storeTx(dsl, "proj-a", "proj-a-t", "rev-1", ctx("rev-1")),
                "RCOWN5: corrupt revctx payload FAILS CLOSED");
    }

    @Test
    void rcown6_effectReferenceTamperFailsClosed() {
        storeRevctx("proj-a", "proj-a-t", "rev-1", ctx("rev-1"));
        String stored = dsl.fetchOne("select payload_json from timeline_snapshot where id = ?",
                "revctx_rev-1").get(0, String.class);
        String tampered = stored.replaceFirst(
                "\"snapshotId\":\"[^\"]*\"", "\"snapshotId\":\"esnap_tampered\"");
        dsl.execute("update timeline_snapshot set payload_json = ? where id = ?", tampered, "revctx_rev-1");
        var store = new JdbcTimelineRevisionSemanticContextStore(dsl);
        assertThrows(Exception.class,
                () -> store.storeTx(dsl, "proj-a", "proj-a-t", "rev-1", ctx("rev-1")),
                "RCOWN6: Effect reference tamper FAILS CLOSED");
    }

    // ---- RST1-6 / RSTOWN1-2 / RST_TX_HEAD (F4) ----

    @Test
    void rst5_validCompleteRevisionRestoresPASS() {
        String productId = "proj-a";
        TimelineRevisionSaveService svc = saveService();
        com.example.platform.timeline.version.TimelineRevision first = svc.saveRevisionWithEffects(
                productId, null, sampleDocument(),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        TimelineRevisionSaveService svc2 = saveService();
        var restored = svc2.restoreRevision(productId, first.revisionId(), first.revisionId(), "u");
        assertNotNull(restored, "RST5: valid complete revision restores");
        assertNotEquals(first.revisionId(), restored.revisionId(), "RST5: new revision identity");
        assertEquals(2L, countRevisions(productId), "RST5: original + restored");
    }

    @Test
    void rst1_nullHistoricalSnapshotIdFailsClosed() {
        String productId = "proj-a";
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects(productId, null, sampleDocument(), List.of(), List.of(), "u");
        // RST1: the DB itself enforces SNAPSHOT_ID NOT NULL — a null snapshot id
        // cannot exist in a final-model revision (constraint = fail-closed at
        // the storage boundary; the restore path additionally fails closed on
        // any missing governed payload — RST2).
        assertThrows(org.jooq.exception.DataAccessException.class,
                () -> dsl.execute("update timeline_revision set snapshot_id = null where id = ?",
                        rev.revisionId()),
                "RST1: SNAPSHOT_ID NOT NULL constraint — null snapshot id is unrepresentable");
    }

    @Test
    void rst2_missingSnapshotPayloadRowFailsClosed() {
        String productId = "proj-a";
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects(productId, null, sampleDocument(), List.of(), List.of(), "u");
        String snapId = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rev.revisionId()).get(0, String.class);
        dsl.execute("delete from timeline_snapshot where id = ?", snapId);
        assertThrows(IllegalStateException.class,
                () -> svc.restoreRevision(productId, rev.revisionId(), rev.revisionId(), "u"),
                "RST2: missing snapshot payload row FAILS CLOSED");
    }

    @Test
    void rst3_missingSemanticContextFailsClosed() {
        String productId = "proj-a";
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects(productId, null, sampleDocument(), List.of(), List.of(), "u");
        dsl.execute("delete from timeline_snapshot where id = ?", "revctx_" + rev.revisionId());
        assertThrows(Exception.class,
                () -> svc.restoreRevision(productId, rev.revisionId(), rev.revisionId(), "u"),
                "RST3: missing revctx FAILS CLOSED");
    }

    @Test
    void rst4_wrongRevctxOwnershipFailsClosed() {
        String productId = "proj-a";
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects(productId, null, sampleDocument(), List.of(), List.of(), "u");
        dsl.execute("update timeline_snapshot set project_id = 'proj-b', tenant_id = 'proj-b-t' "
                + "where id = ?", "revctx_" + rev.revisionId());
        assertThrows(Exception.class,
                () -> svc.restoreRevision(productId, rev.revisionId(), rev.revisionId(), "u"),
                "RST4: revctx wrong ownership FAILS CLOSED");
    }

    @Test
    void rstown1_crossProjectRestoreFailsClosed() {
        String productId = "proj-a";
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects(productId, null, sampleDocument(), List.of(), List.of(), "u");
        // historical revision row is owned by proj-a; restore as proj-b
        com.example.platform.shared.web.TenantContext.set("proj-b-t");
        assertThrows(Exception.class,
                () -> svc.restoreRevision("proj-b", rev.revisionId(), null, "u"),
                "RSTOWN1: cross-project restore FAILS CLOSED");
    }

    @Test
    void rstown2_crossTenantRestoreFailsClosed() {
        String productId = "proj-a";
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects(productId, null, sampleDocument(), List.of(), List.of(), "u");
        com.example.platform.shared.web.TenantContext.set("other-tenant");
        assertThrows(Exception.class,
                () -> svc.restoreRevision("proj-a", rev.revisionId(), rev.revisionId(), "u"),
                "RSTOWN2: cross-tenant restore FAILS CLOSED");
    }

    @Test
    void rstTxHead_restoreHeadFailureRollsBackWholeTransition() {
        String productId = "proj-a";
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects(productId, null, sampleDocument(), List.of(), List.of(), "u");
        long revisionsBefore = countRevisions(productId);
        long snapsBefore = countSnapshots(productId);
        TimelineRevisionSaveService failingRestore = new TimelineRevisionSaveService(
                dsl, currentRevisionService,
                new com.example.platform.timeline.canonical.TimelineContentDigester(),
                new com.example.platform.timeline.adapter.TimelineSnapshotService(dsl),
                new TimelineArtifactPinValidator(
                        new com.example.platform.artifact.infrastructure.JooqArtifactQueryService(
                                new com.example.platform.artifact.infrastructure.ArtifactRepository(dsl),
                                new com.example.platform.artifact.app.ArtifactRelationRepository(dsl))),
                new com.example.platform.artifact.app.ArtifactPinService(
                        new com.example.platform.artifact.infrastructure.ArtifactPinRepository(dsl)),
                new EffectSemanticSnapshotAuthority(
                        new JdbcEffectDefinitionVersionRegistry(dsl),
                        new JdbcEffectSemanticSnapshotStore(dsl)),
                new JdbcTimelineRevisionSemanticContextStore(dsl),
                new DefaultTimelineRevisionPersistence(),
                (tx, project, expected, newRevisionId) -> {
                    throw new IllegalStateException("RST_TX_HEAD injected head failure");
                });
        assertThrows(IllegalStateException.class,
                () -> failingRestore.restoreRevision(productId, rev.revisionId(), rev.revisionId(), "u"));
        assertEquals(revisionsBefore, countRevisions(productId), "RST_TX_HEAD: no new revision committed");
        assertEquals(snapsBefore, countSnapshots(productId), "RST_TX_HEAD: no new snapshot rows committed");
        assertEquals(rev.revisionId(), currentRevision(productId), "RST_TX_HEAD: head unchanged");
    }

    // ---- R1/R2: restore semantic closure attacks (RST7-RST12) ----

    private TimelineRevisionSaveService saveFor(String productId, String tenant) {
        com.example.platform.shared.web.TenantContext.set(tenant);
        dsl.execute("insert into product (product_id, tenant_id, project_id, product_type, "
                + "representation_kind, status, created_at, updated_at) "
                + "values ('" + productId + "', '" + tenant + "', '" + productId + "', 'video', "
                + "'master', 'REGISTERED', now(), now()) on conflict (product_id) do nothing");
        dsl.execute("insert into artifact (id, tenant_id, content_digest, byte_length, media_type, "
                + "artifact_kind, state, schema_version, created_at) "
                + "values ('art-1', '" + tenant + "', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', "
                + "100, 'VIDEO', 'SOURCE_MEDIA', 'AVAILABLE', 1, now()) "
                + "on conflict (id) do nothing");
        return saveService();
    }

    @Test
    void rst7_foreignTimelineSnapshotFailsClosed() {
        // R1: RA (project A) corrupted to point at RB's (project B) snapshot
        String tenantA = "proj-a-t";
        TimelineRevisionSaveService svcA = saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        var ra = svcA.saveRevisionWithEffects("proj-a", null, sampleDocument(), List.of(), List.of(), "u");
        TimelineRevisionSaveService svcB = saveFor("proj-b", "proj-b-t");
        com.example.platform.shared.web.TenantContext.set("proj-b-t");
        var rb = svcB.saveRevisionWithEffects("proj-b", null, new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                        List.of(), TimelineMetadata.empty(),
                        com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of()), List.of(), List.of(), "u");
        String rbSnap = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rb.revisionId()).get(0, String.class);
        // corrupt RA to point at RB's snapshot
        dsl.execute("update timeline_revision set snapshot_id = ? where id = ?", rbSnap, ra.revisionId());
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svcA2 = saveService();
        assertThrows(Exception.class,
                () -> svcA2.restoreRevision("proj-a", ra.revisionId(), ra.revisionId(), "u"),
                "RST7: foreign Timeline snapshot in restore FAILS CLOSED");
        assertEquals(ra.revisionId(), currentRevision("proj-a"), "RST7: head unchanged");
    }

    @Test
    void rst8_revisionContentHashMismatchFailsClosed() {
        String tenantA = "proj-a-t";
        saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects("proj-a", null, sampleDocument(), List.of(), List.of(), "u");
        dsl.execute("update timeline_revision set content_hash = 'tampered' where id = ?", rev.revisionId());
        assertThrows(Exception.class,
                () -> svc.restoreRevision("proj-a", rev.revisionId(), rev.revisionId(), "u"),
                "RST8: content_hash mismatch FAILS CLOSED (3-way digest violated)");
    }

    @Test
    void rst9_timelinePayloadDigestMismatchFailsClosed() {
        String tenantA = "proj-a-t";
        saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects("proj-a", null, sampleDocument(), List.of(), List.of(), "u");
        // replace the governed Timeline payload with a DIFFERENT valid document
        String snapId = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rev.revisionId()).get(0, String.class);
        String other = com.example.platform.timeline.app.TimelineDocumentJsonSerializer
                .serializeWithCaptions(new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                        List.of(new TimelineTrack("t9", "v9", TrackType.VIDEO, List.of())),
                        TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY,
                        List.of(), List.of()));
        dsl.execute("update timeline_snapshot set payload_json = ? where id = ?", other, snapId);
        assertThrows(Exception.class,
                () -> svc.restoreRevision("proj-a", rev.revisionId(), rev.revisionId(), "u"),
                "RST9: Timeline payload digest mismatch FAILS CLOSED");
    }

    @Test
    void rst10_missingEffectSnapshotFailsClosed() {
        // FINAL (C3): RST10 MUST target the REAL Effect snapshot authority —
        // the exact esnap_ id pinned in the historical revctx effectReference
        // (RESTORE_EFFECT_SNAPSHOT_TESTS_MUST_TARGET_REAL_EFFECT_SNAPSHOT_AUTHORITY_V1).
        // The Timeline governed snapshot and the revctx row stay INTACT; ONLY
        // the Effect snapshot row is deleted, proving the failure boundary is
        // the missing Effect snapshot — not an earlier boundary.
        String tenantA = "proj-a-t";
        saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects("proj-a", null, sampleDocument(),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        // load the EXACT Effect snapshot id from the persisted revctx
        // (JdbcTimelineRevisionSemanticContextStore — the final production
        // store API, ownership-scoped) — never infer from
        // timeline_revision.snapshot_id.
        var revctxStore = new JdbcTimelineRevisionSemanticContextStore(dsl);
        var revctx = revctxStore.findByRevisionId(dsl, "proj-a", tenantA, rev.revisionId())
                .orElseThrow(() -> new AssertionError("RST10: revctx must exist"));
        String esnapId = revctx.effectReference().snapshotId().value();
        assertTrue(esnapId.startsWith("esnap_"),
                "RST10: effectReference.snapshotId is a real esnap_ identity: " + esnapId);
        // precondition: the esnap_ row EXISTS (and is the only object removed)
        assertEquals(1L,
                dsl.fetchOne("select count(1) from timeline_snapshot where id = ?", esnapId)
                        .get(0, Long.class),
                "RST10: real esnap_ Effect snapshot row must exist before deletion");
        // preconditions proving the intended failure boundary (EVIDENCE_MUST_PROVE_THE_INTENDED_BOUNDARY_V1):
        // Timeline governed snapshot intact, revctx intact, historical revision intact.
        String timelineSnapId = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rev.revisionId()).get(0, String.class);
        assertEquals(1L,
                dsl.fetchOne("select count(1) from timeline_snapshot where id = ?", timelineSnapId)
                        .get(0, Long.class),
                "RST10 precondition: Timeline governed snapshot still exists");
        assertEquals(1L,
                dsl.fetchOne("select count(1) from timeline_snapshot where id = ?",
                        "revctx_" + rev.revisionId()).get(0, Long.class),
                "RST10 precondition: revctx row still exists");
        assertEquals(1L,
                dsl.fetchOne("select count(1) from timeline_revision where id = ?", rev.revisionId())
                        .get(0, Long.class),
                "RST10 precondition: historical revision still exists");
        // delete THAT esnap_ row ONLY — Timeline snapshot + revctx untouched
        dsl.execute("delete from timeline_snapshot where id = ?", esnapId);
        Exception ex = assertThrows(Exception.class,
                () -> svc.restoreRevision("proj-a", rev.revisionId(), rev.revisionId(), "u"),
                "RST10: missing Effect snapshot FAILS CLOSED (no remint, no EMPTY)");
        // §40: the diagnostic must indicate the intended boundary — the
        // Effect snapshot, not an earlier Timeline/revctx failure.
        assertTrue(ex.getMessage() != null
                        && (ex.getMessage().contains("Effect snapshot")
                            || ex.getMessage().contains("RST10/RST11")
                            || ex.getMessage().contains("effect")),
                "RST10: failure diagnostic points at the Effect snapshot boundary: "
                        + ex.getMessage());
    }

    @Test
    void rst11_foreignEffectSnapshotFailsClosed() {
        // FINAL (C3): RST11 MUST use a REAL foreign Effect snapshot (a valid
        // esnap_ owned by B) referenced from a SELF-CONSISTENT RA revctx.
        // The only violated invariant is OWNERSHIP — revctx internal integrity,
        // full digest relationship and both Timeline/Effect objects are all
        // otherwise valid (EVIDENCE_MUST_PROVE_THE_INTENDED_BOUNDARY_V1).
        String tenantA = "proj-a-t";
        TimelineRevisionSaveService svcA = saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        var ra = svcA.saveRevisionWithEffects("proj-a", null, sampleDocument(),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        TimelineRevisionSaveService svcB = saveFor("proj-b", "proj-b-t");
        // B needs its own artifact under proj-b-t (pin validation is
        // tenant-scoped; art-1 already belongs to proj-a-t).
        dsl.execute("insert into artifact (id, tenant_id, content_digest, byte_length, media_type, "
                + "artifact_kind, state, schema_version, created_at) "
                + "values ('art-b-1', 'proj-b-t', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', "
                + "100, 'VIDEO', 'SOURCE_MEDIA', 'AVAILABLE', 1, now()) "
                + "on conflict (id) do nothing");
        com.example.platform.shared.web.TenantContext.set("proj-b-t");
        var rb = svcB.saveRevisionWithEffects("proj-b", null,
                sampleDocument("c1", "asset-1", "stream-1", "art-b-1"),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        // load RB's REAL Effect snapshot reference (exact esnap_ identity,
        // digest, contract version) from the persisted revctx — the final
        // production store API.
        var revctxStore = new JdbcTimelineRevisionSemanticContextStore(dsl);
        var rbCtx = revctxStore.findByRevisionId(dsl, "proj-b", "proj-b-t", rb.revisionId())
                .orElseThrow(() -> new AssertionError("RST11: RB revctx must exist"));
        var rbRef = rbCtx.effectReference();
        String rbEsnapId = rbRef.snapshotId().value();
        assertTrue(rbEsnapId.startsWith("esnap_"),
                "RST11: RB effectReference is a real esnap_ identity: " + rbEsnapId);
        // confirm RB's owned Effect snapshot EXISTS and is valid under B
        assertEquals(1L,
                dsl.fetchOne("select count(1) from timeline_snapshot where id = ? and project_id = 'proj-b' and tenant_id = 'proj-b-t'",
                        rbEsnapId).get(0, Long.class),
                "RST11: foreign esnap_ exists and is valid under B");
        // build a SELF-CONSISTENT foreign-effect reference for RA: same
        // timeline digest as RA, RB's REAL Effect reference, recomputed full
        // semantic digest — internally valid revctx (passes codec validation).
        String tenantA2 = tenantA;
        com.example.platform.shared.web.TenantContext.set(tenantA2);
        var raCtx = revctxStore.findByRevisionId(dsl, "proj-a", tenantA2, ra.revisionId())
                .orElseThrow(() -> new AssertionError("RST11: RA revctx must exist"));
        String raTimelineDigest = raCtx.timelineContentDigest();
        String recomputedFull = com.example.platform.timeline.semantics.effect
                .TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(
                        raTimelineDigest, rbRef);
        var tamperedRaCtx = new TimelineRevisionSemanticContext(
                raTimelineDigest, rbRef, recomputedFull,
                TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1);
        // persist the internally-valid revctx for RA via the production codec
        // (self-consistency guaranteed by serialization), and align RA's
        // timeline_revision.content_hash with the recomputed full digest so
        // the full digest relationship is valid too.
        String tamperedPayload = com.example.platform.timeline.version
                .TimelineRevisionSemanticContextJsonCodec.serialize(tamperedRaCtx);
        dsl.execute("update timeline_snapshot set payload_json = ? where id = ?",
                tamperedPayload, "revctx_" + ra.revisionId());
        dsl.execute("update timeline_revision set content_hash = ? where id = ?",
                recomputedFull, ra.revisionId());
        TimelineRevisionSaveService svcA2 = saveService();
        Exception ex = assertThrows(Exception.class,
                () -> svcA2.restoreRevision("proj-a", ra.revisionId(), ra.revisionId(), "u"),
                "RST11: foreign Effect snapshot ownership FAILS CLOSED");
        // §40/§31: failure must come from the ownership-scoped Effect lookup
        // (Effect snapshot missing/not-owned for A because it belongs to B) —
        // NOT from malformed revctx JSON, digest mismatch, or Timeline/revctx
        // integrity (all of those are valid by construction here).
        assertTrue(ex.getMessage() != null
                        && (ex.getMessage().contains("Effect snapshot")
                            || ex.getMessage().contains("RST10/RST11")
                            || ex.getMessage().contains("not owned")),
                "RST11: failure diagnostic points at the Effect ownership boundary: "
                        + ex.getMessage());
    }

    @Test
    void rst12_effectReferenceDigestMismatchFailsClosed() {
        String tenantA = "proj-a-t";
        saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects("proj-a", null, sampleDocument(),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        // tamper revctx Effect reference contentDigest (keep snapshotId real)
        String payload = dsl.fetchOne("select payload_json from timeline_snapshot where id = ?",
                "revctx_" + rev.revisionId()).get(0, String.class);
        String tampered = payload.replaceFirst("\"contentDigest\":\"[^\"]*\"",
                "\"contentDigest\":\"tampered\"");
        dsl.execute("update timeline_snapshot set payload_json = ? where id = ?",
                tampered, "revctx_" + rev.revisionId());
        assertThrows(Exception.class,
                () -> svc.restoreRevision("proj-a", rev.revisionId(), rev.revisionId(), "u"),
                "RST12: Effect reference digest mismatch FAILS CLOSED");
    }

    // ---- R3: findById read ownership (READOWN1-2) ----

    @Test
    void readown1_crossTenantFindByIdNotVisible() {
        String tenantA = "proj-a-t";
        saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects("proj-a", null, sampleDocument(), List.of(), List.of(), "u");
        // switch tenant -> findById must NOT resolve the revision
        com.example.platform.shared.web.TenantContext.set("other-tenant");
        assertNull(svc.findById(rev.revisionId()),
                "READOWN1: cross-tenant findById NOT FOUND (no existence leakage)");
    }

    @Test
    void readown2_foreignTimelineSnapshotCannotHydrateCanonicalRevision() {
        // FINAL (C2): READOWN2 MUST exercise the ACTUAL hydration helper —
        // TimelineRevisionSaveService.findPayloadDocument — not a second
        // restore invocation (which duplicates RST7). A foreign Timeline
        // snapshot must NEVER hydrate a canonical revision payload.
        String tenantA = "proj-a-t";
        TimelineRevisionSaveService svcA = saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        var ra = svcA.saveRevisionWithEffects("proj-a", null, sampleDocument(), List.of(), List.of(), "u");
        TimelineRevisionSaveService svcB = saveFor("proj-b", "proj-b-t");
        com.example.platform.shared.web.TenantContext.set("proj-b-t");
        var rb = svcB.saveRevisionWithEffects("proj-b", null, new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                        List.of(), TimelineMetadata.empty(),
                        com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of()), List.of(), List.of(), "u");
        String rbSnap = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rb.revisionId()).get(0, String.class);
        // corrupt RA.snapshot_id → RB.snapshot_id (foreign snapshot)
        dsl.execute("update timeline_revision set snapshot_id = ? where id = ?", rbSnap, ra.revisionId());
        com.example.platform.shared.web.TenantContext.set(tenantA);
        // DIRECT hydration-helper call: the foreign payload must NOT be
        // returned — Optional.empty / fail closed (no foreign payload).
        TimelineRevisionSaveService svcA2 = saveService();
        assertTrue(svcA2.findPayloadDocument(ra.revisionId()).isEmpty(),
                "READOWN2: foreign Timeline snapshot never hydrates the canonical payload "
                        + "(findPayloadDocument returns empty)");
    }

    @Test
    void readown3_patchApplyForeignSnapshotFailsClosed() {
        // FINAL (C2): patch APPLY must hydrate the base payload ownership-
        // scoped; a foreign snapshot → PAYLOAD_INVALID, zero writes, no head
        // movement (TIMELINE_PATCH_PAYLOAD_INVALID).
        String tenantA = "proj-a-t";
        TimelineRevisionSaveService svcA = saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        var ra = svcA.saveRevisionWithEffects("proj-a", null, sampleDocument(), List.of(), List.of(), "u");
        TimelineRevisionSaveService svcB = saveFor("proj-b", "proj-b-t");
        com.example.platform.shared.web.TenantContext.set("proj-b-t");
        var rb = svcB.saveRevisionWithEffects("proj-b", null, new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                        List.of(), TimelineMetadata.empty(),
                        com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of()), List.of(), List.of(), "u");
        String rbSnap = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rb.revisionId()).get(0, String.class);
        dsl.execute("update timeline_revision set snapshot_id = ? where id = ?", rbSnap, ra.revisionId());
        com.example.platform.shared.web.TenantContext.set(tenantA);
        // construct a VALID patch based on RA (base digest = RA timeline digest)
        com.example.platform.timeline.canonical.TimelineContentDigester digester =
                new com.example.platform.timeline.canonical.TimelineContentDigester();
        var raCtx = new JdbcTimelineRevisionSemanticContextStore(dsl)
                .findByRevisionId(dsl, "proj-a", tenantA, ra.revisionId())
                .orElseThrow();
        TimelinePatch patch = new TimelinePatch("1.0", "patch-" + java.util.UUID.randomUUID(), "proj-a",
                ra.revisionId(), raCtx.timelineContentDigest(), ra.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "c1", "startTime", "0/1", "1/1")),
                null, null);
        var patchService = new TimelinePatchApplicationService(
                saveService(), currentRevisionService, digester);
        PatchApplyResult result = patchService.apply(patch);
        assertTrue(result instanceof PatchApplyResult.Failure f
                        && f.error().code() == PatchErrorCode.TIMELINE_PATCH_PAYLOAD_INVALID,
                "READOWN3: patch apply with foreign snapshot FAILS CLOSED with PAYLOAD_INVALID, got: " + result);
        // no new revision, no head movement
        assertEquals(1L, countRevisions("proj-a"), "READOWN3: no new revision");
        assertEquals(ra.revisionId(), currentRevision("proj-a"), "READOWN3: head unchanged");
    }

    @Test
    void readown4_patchPreviewForeignSnapshotFailsClosed() {
        // FINAL (C2): patch PREVIEW is NOT a weaker path — same ownership
        // semantics as apply; foreign snapshot → PAYLOAD_INVALID, no mutation.
        String tenantA = "proj-a-t";
        TimelineRevisionSaveService svcA = saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        var ra = svcA.saveRevisionWithEffects("proj-a", null, sampleDocument(), List.of(), List.of(), "u");
        TimelineRevisionSaveService svcB = saveFor("proj-b", "proj-b-t");
        com.example.platform.shared.web.TenantContext.set("proj-b-t");
        var rb = svcB.saveRevisionWithEffects("proj-b", null, new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                        List.of(), TimelineMetadata.empty(),
                        com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of()), List.of(), List.of(), "u");
        String rbSnap = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rb.revisionId()).get(0, String.class);
        dsl.execute("update timeline_revision set snapshot_id = ? where id = ?", rbSnap, ra.revisionId());
        com.example.platform.shared.web.TenantContext.set(tenantA);
        com.example.platform.timeline.canonical.TimelineContentDigester digester =
                new com.example.platform.timeline.canonical.TimelineContentDigester();
        var raCtx = new JdbcTimelineRevisionSemanticContextStore(dsl)
                .findByRevisionId(dsl, "proj-a", tenantA, ra.revisionId())
                .orElseThrow();
        TimelinePatch patch = new TimelinePatch("1.0", "patch-" + java.util.UUID.randomUUID(), "proj-a",
                ra.revisionId(), raCtx.timelineContentDigest(), ra.revisionId(),
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelinePatchOperation.UpdateClipProperty("op1", "c1", "startTime", "0/1", "1/1")),
                null, null);
        var patchService = new TimelinePatchApplicationService(
                saveService(), currentRevisionService, digester);
        PatchPreviewResult result = patchService.preview(patch);
        assertTrue(result instanceof PatchPreviewResult.Failure f
                        && f.error().code() == PatchErrorCode.TIMELINE_PATCH_PAYLOAD_INVALID,
                "READOWN4: patch preview with foreign snapshot FAILS CLOSED with PAYLOAD_INVALID, got: " + result);
        // no data mutation
        assertEquals(1L, countRevisions("proj-a"), "READOWN4: no new revision");
        assertEquals(ra.revisionId(), currentRevision("proj-a"), "READOWN4: head unchanged");
    }

    @Test
    void rst13_verifiedTimelinePayloadIsExactlyReissued() {
        // FINAL (C1, RESTORE_REISSUES_EXACTLY_THE_VERIFIED_TIMELINE_PAYLOAD_V1):
        // restore must reissue the EXACT verified Timeline payload — the new
        // restored snapshot's canonical payload and digest must equal the
        // verified historical values byte-for-byte (no reread, no substitution).
        String tenantA = "proj-a-t";
        saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects("proj-a", null, sampleDocument(),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        // historical owned snapshot payload (exact bytes)
        String historicalSnapId = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rev.revisionId()).get(0, String.class);
        String historicalPayload = dsl.fetchOne("select payload_json from timeline_snapshot where id = ?",
                historicalSnapId).get(0, String.class);
        var revctxStore = new JdbcTimelineRevisionSemanticContextStore(dsl);
        var histCtx = revctxStore.findByRevisionId(dsl, "proj-a", tenantA, rev.revisionId())
                .orElseThrow();
        // run restore
        TimelineRevisionSaveService svc2 = saveService();
        var restored = svc2.restoreRevision("proj-a", rev.revisionId(), rev.revisionId(), "u");
        // load the NEW restored owned snapshot
        String restoredSnapId = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                restored.revisionId()).get(0, String.class);
        String restoredPayload = dsl.fetchOne("select payload_json from timeline_snapshot where id = ?",
                restoredSnapId).get(0, String.class);
        // exact byte equality of the canonical payload
        assertEquals(historicalPayload, restoredPayload,
                "RST13: restored canonical payload bytes EXACTLY equal the verified historical payload");
        // new restored timeline digest == verified historical timeline digest
        var restoredCtx = revctxStore.findByRevisionId(dsl, "proj-a", tenantA, restored.revisionId())
                .orElseThrow();
        assertEquals(histCtx.timelineContentDigest(), restoredCtx.timelineContentDigest(),
                "RST13: restored Timeline digest equals verified historical Timeline digest");
        // full semantic commitment preserved
        assertEquals(histCtx.revisionSemanticDigest(), restoredCtx.revisionSemanticDigest(),
                "RST13: restored full semantic digest equals verified historical digest");
    }

    @Test
    void rst14_effectBearingExactRestorePass() {
        // FINAL (C3 positive complement, RST14_EFFECT_BEARING_EXACT_RESTORE_PASS):
        // a valid Effect-bearing historical revision restores with the EXACT
        // Effect reference — same snapshotId, same contentDigest, same semantic
        // contract version. No remint, no substitution.
        String tenantA = "proj-a-t";
        saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects("proj-a", null, sampleDocument(),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        var revctxStore = new JdbcTimelineRevisionSemanticContextStore(dsl);
        var histCtx = revctxStore.findByRevisionId(dsl, "proj-a", tenantA, rev.revisionId())
                .orElseThrow();
        var histRef = histCtx.effectReference();
        // run restore
        TimelineRevisionSaveService svc2 = saveService();
        var restored = svc2.restoreRevision("proj-a", rev.revisionId(), rev.revisionId(), "u");
        var restoredCtx = revctxStore.findByRevisionId(dsl, "proj-a", tenantA, restored.revisionId())
                .orElseThrow();
        var restoredRef = restoredCtx.effectReference();
        assertEquals(histRef.snapshotId(), restoredRef.snapshotId(),
                "RST14: restored Effect snapshotId EXACTLY equals historical");
        assertEquals(histRef.contentDigest(), restoredRef.contentDigest(),
                "RST14: restored Effect contentDigest EXACTLY equals historical");
        assertEquals(histRef.semanticContractVersion(), restoredRef.semanticContractVersion(),
                "RST14: restored Effect semantic contract version EXACTLY equals historical");
    }

    private long countRevisions(String productId) {
        return dsl.fetchOne("select count(1) from timeline_revision where project_id = ?", productId).get(0, Long.class);
    }

    private long countSnapshots(String productId) {
        return dsl.fetchOne("select count(1) from timeline_snapshot where project_id = ?", productId).get(0, Long.class);
    }

    private String currentRevision(String productId) {
        return dsl.fetchOne("select current_revision_id from product where product_id = ?", productId)
                .get(0, String.class);
    }

    private static com.example.platform.timeline.semantics.effect.EffectInstance effect(String id, String radius) {
        return new com.example.platform.timeline.semantics.effect.EffectInstance(
                id, "def-blur", "1", com.example.platform.timeline.semantics.effect.EffectInstance.EffectMediaType.VIDEO, true,
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(
                        com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                java.util.Map.of("radiusPixels", radius), java.util.Map.of(),
                new com.example.platform.timeline.semantics.effect.ClipEffectTarget("t1", "c1"),
                com.example.platform.timeline.semantics.effect.EffectInstance.EffectProvenance.untracked());
    }

    private static com.example.platform.timeline.semantics.effect.EffectInstance.EffectDefinition def(String radius) {
        return new com.example.platform.timeline.semantics.effect.EffectInstance.EffectDefinition(
                "def-blur", "1", com.example.platform.timeline.semantics.effect.EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(com.example.platform.timeline.semantics.effect.EffectInstance.EffectMediaType.VIDEO),
                java.util.Map.of("radiusPixels", new com.example.platform.timeline.semantics.effect.EffectInstance.ParameterSchema(
                        "radiusPixels", "string", null, null, radius, List.of())),
                com.example.platform.timeline.semantics.effect.EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("radiusPixels"), List.of("video.effect.gaussian-blur"), List.of());
    }
}
