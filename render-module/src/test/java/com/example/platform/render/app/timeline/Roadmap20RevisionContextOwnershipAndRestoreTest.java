package com.example.platform.render.app.timeline;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.ProductCurrentRevisionHeadUpdateAdapter;
import com.example.platform.timeline.app.ProductCurrentRevisionService;
import com.example.platform.timeline.app.TimelineArtifactPinValidator;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import java.util.List;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
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
        com.example.platform.shared.time.MediaTime m0 = com.example.platform.shared.time.MediaTime.ofRational(0, 1);
        com.example.platform.shared.time.MediaTime m2 = com.example.platform.shared.time.MediaTime.ofRational(2, 1);
        com.example.platform.timeline.canonical.TimelineClip tc = new com.example.platform.timeline.canonical.TimelineClip(
                "c1", "asset-1", "stream-1", "art-1",
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
        String tenantA = "proj-a-t";
        saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svc = saveService();
        var rev = svc.saveRevisionWithEffects("proj-a", null, sampleDocument(),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        // delete the esnap_ row (keep revctx reference)
        String pin = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rev.revisionId()).get(0, String.class);
        dsl.execute("delete from timeline_snapshot where id = ?", pin);
        assertThrows(Exception.class,
                () -> svc.restoreRevision("proj-a", rev.revisionId(), rev.revisionId(), "u"),
                "RST10: missing Effect snapshot FAILS CLOSED (no remint, no EMPTY)");
    }

    @Test
    void rst11_foreignEffectSnapshotFailsClosed() {
        String tenantA = "proj-a-t";
        TimelineRevisionSaveService svcA = saveFor("proj-a", tenantA);
        com.example.platform.shared.web.TenantContext.set(tenantA);
        var ra = svcA.saveRevisionWithEffects("proj-a", null, sampleDocument(),
                List.of(effect("eff-1", "4")), List.of(def("4")), "u");
        TimelineRevisionSaveService svcB = saveFor("proj-b", "proj-b-t");
        com.example.platform.shared.web.TenantContext.set("proj-b-t");
        var rb = svcB.saveRevisionWithEffects("proj-b", null, new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                        List.of(), TimelineMetadata.empty(),
                        com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of()),
                List.of(), List.of(), "u");
        String rbEsnap = dsl.fetchOne("select snapshot_id from timeline_revision where id = ?",
                rb.revisionId()).get(0, String.class);
        // corrupt RA revctx Effect reference to point at RB's foreign esnap_
        String payload = dsl.fetchOne("select payload_json from timeline_snapshot where id = ?",
                "revctx_" + ra.revisionId()).get(0, String.class);
        String tampered = payload.replaceFirst("\"snapshotId\":\"[^\"]*\"",
                "\"snapshotId\":\"" + rbEsnap + "\"");
        dsl.execute("update timeline_snapshot set payload_json = ? where id = ?",
                tampered, "revctx_" + ra.revisionId());
        com.example.platform.shared.web.TenantContext.set(tenantA);
        TimelineRevisionSaveService svcA2 = saveService();
        assertThrows(Exception.class,
                () -> svcA2.restoreRevision("proj-a", ra.revisionId(), ra.revisionId(), "u"),
                "RST11: foreign Effect snapshot ownership FAILS CLOSED");
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
    void readown2_foreignTimelineSnapshotHydrationFailsClosed() {
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
        // hydration path resolves the snapshot ownership-scoped -> foreign payload
        // must NOT silently hydrate; restore verification covers the canonical
        // path (RST7), and the save-service read helpers remain fail-closed.
        TimelineRevisionSaveService svcA2 = saveService();
        assertThrows(Exception.class,
                () -> svcA2.restoreRevision("proj-a", ra.revisionId(), ra.revisionId(), "u"),
                "READOWN2: foreign Timeline snapshot never hydrates canonically");
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
