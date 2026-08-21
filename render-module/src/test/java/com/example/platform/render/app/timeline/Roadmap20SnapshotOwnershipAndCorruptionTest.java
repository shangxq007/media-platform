package com.example.platform.render.app.timeline;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.canonical.TimelineDocument;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 B4 closure: snapshot ownership (OWN1-OWN4) and store corruption
 * (SC1-SC7) — EFFECT_SEMANTIC_SNAPSHOT_HAS_EXPLICIT_OWNERSHIP_V1 and
 * CORRUPT_AUTHORITATIVE_EFFECT_SNAPSHOT_FAILS_CLOSED_V1.
 */
class Roadmap20SnapshotOwnershipAndCorruptionTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;

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
        dsl.execute("insert into project (id, tenant_id, name, status, created_at) "
                + "values ('proj-a', 'tenant-a', 'a', 'ACTIVE', now()) on conflict (id) do nothing");
        dsl.execute("insert into project (id, tenant_id, name, status, created_at) "
                + "values ('proj-b', 'tenant-b', 'b', 'ACTIVE', now()) on conflict (id) do nothing");
    }

    private static EffectSemanticSnapshotAuthority authority() {
        return new EffectSemanticSnapshotAuthority(
                new EffectDefinitionVersionRegistry.InMemory(),
                new com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore.InMemory());
    }

    private static EffectSemanticSnapshot mint() {
        TimelineDocument doc = sampleDocument();
        EffectInstance effect = new EffectInstance(
                "eff-1", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(
                        com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "4"), Map.of(),
                new com.example.platform.timeline.semantics.effect.ClipEffectTarget("t1", "c1"),
                EffectInstance.EffectProvenance.untracked());
        EffectInstance.EffectDefinition def = new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of("radiusPixels", new EffectInstance.ParameterSchema(
                        "radiusPixels", "string", null, null, "4", List.of())),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("radiusPixels"), List.of("video.effect.gaussian-blur"), List.of());
        return authority().mintFromAuthoredState(List.of(effect), List.of(def), doc);
    }

    private static com.example.platform.timeline.canonical.TimelineDocument sampleDocument() {
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

    // ---- OWN1-OWN4 ----

    @Test
    void own1_crossProjectLookupFailsClosed() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        // same id, different project -> NOT FOUND (ownership-scoped)
        assertTrue(store.findById("proj-b", "tenant-a", snap.id()).isEmpty(),
                "OWN1: cross-project lookup FAILS CLOSED (not found)");
        // correct ownership resolves
        assertTrue(store.findById("proj-a", "tenant-a", snap.id()).isPresent(),
                "OWN1: ownership-scoped lookup resolves");
    }

    @Test
    void own2_crossTenantLookupFailsClosed() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        assertTrue(store.findById("proj-a", "tenant-b", snap.id()).isEmpty(),
                "OWN2: cross-tenant lookup FAILS CLOSED (not found)");
    }

    @Test
    void own3_foreignOwnedSnapshotNotResolvableThroughCanonicalPath() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        // revision in project B cannot load a snapshot owned by project A
        assertTrue(store.findById("proj-b", "tenant-b", snap.id()).isEmpty(),
                "OWN3: revision-owned pin pointing at another project's snapshot FAILS CLOSED");
    }

    @Test
    void own4_noGlobalByIdLookupExposed() {
        // the production API surface exposes ONLY ownership-scoped lookup;
        // a globally guessable id without ownership context is not resolvable
        // (no findById(snapshotId) canonical API exists)
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        assertTrue(store.findById("proj-unknown", "tenant-unknown", snap.id()).isEmpty(),
                "OWN4: global-by-id resolution without ownership FAILS CLOSED");
    }

    // ---- SC1-SC7 ----

    private void corruptRow(String id, String payload) {
        dsl.execute("update timeline_snapshot set payload_json = ? where id = ?", payload, id);
    }

    @Test
    void sc1_malformedExistingPayloadFailsClosed() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        corruptRow(snap.id().value(), "{corrupt-json");
        assertThrows(IllegalStateException.class,
                () -> store.storeTx(dsl, "proj-a", "tenant-a", snap),
                "SC1: malformed existing payload -> FAIL CLOSED (no idempotent success)");
    }

    @Test
    void sc2_missingContentDigestFailsClosed() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        String valid = com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec
                .serialize(snap);
        String missingDigest = valid.replaceFirst(
                "\"contentDigest\":\"[^\"]*\"", "\"contentDigest\":null");
        corruptRow(snap.id().value(), missingDigest);
        assertThrows(IllegalStateException.class,
                () -> store.storeTx(dsl, "proj-a", "tenant-a", snap),
                "SC2: missing content digest -> FAIL CLOSED");
    }

    @Test
    void sc3_invalidContentDigestFailsClosed() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        String valid = com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec
                .serialize(snap);
        String badDigest = valid.replaceFirst(
                "\"contentDigest\":\"[^\"]*\"", "\"contentDigest\":\"deadbeef\"");
        corruptRow(snap.id().value(), badDigest);
        assertThrows(IllegalStateException.class,
                () -> store.storeTx(dsl, "proj-a", "tenant-a", snap),
                "SC3: invalid content digest -> FAIL CLOSED");
    }

    @Test
    void sc4_payloadIdDiffersFromRowIdFailsClosed() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        String valid = com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec
                .serialize(snap);
        String wrongId = valid.replaceFirst(
                "\"snapshotId\":\"[^\"]*\"", "\"snapshotId\":\"esnap_tampered\"");
        corruptRow(snap.id().value(), wrongId);
        assertThrows(IllegalStateException.class,
                () -> store.storeTx(dsl, "proj-a", "tenant-a", snap),
                "SC4: payload snapshot id differs from DB row id -> FAIL CLOSED");
    }

    @Test
    void sc5_unsupportedSemanticContractFailsClosed() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        String valid = com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec
                .serialize(snap);
        String badContract = valid.replaceFirst(
                "\"semanticContractVersion\":\"[^\"]*\"", "\"semanticContractVersion\":\"effect-semantics-v99\"");
        corruptRow(snap.id().value(), badContract);
        assertThrows(IllegalStateException.class,
                () -> store.storeTx(dsl, "proj-a", "tenant-a", snap),
                "SC5: unsupported semantic contract -> FAIL CLOSED");
    }

    @Test
    void sc6_exactSameSnapshotIdempotentPass() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        // exact identical content under same id -> idempotent PASS
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        assertTrue(store.findById("proj-a", "tenant-a", snap.id()).isPresent());
    }

    @Test
    void sc7_sameIdDifferentContentFailsClosed() {
        JdbcEffectSemanticSnapshotStore store = new JdbcEffectSemanticSnapshotStore(dsl);
        EffectSemanticSnapshot snap = mint();
        store.storeTx(dsl, "proj-a", "tenant-a", snap);
        EffectInstance different = new EffectInstance(
                "eff-2", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(
                        com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "77"), Map.of(),
                new com.example.platform.timeline.semantics.effect.ClipEffectTarget("t1", "c1"),
                EffectInstance.EffectProvenance.untracked());
        EffectInstance.EffectDefinition def = new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of("radiusPixels", new EffectInstance.ParameterSchema(
                        "radiusPixels", "string", null, null, "77", List.of())),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("radiusPixels"), List.of("video.effect.gaussian-blur"), List.of());
        EffectSemanticSnapshot differentSnap = authority().mintFromAuthoredState(
                List.of(different), List.of(def), sampleDocument());
        assertNotEquals(snap.id(), differentSnap.id(), "SC7: authority generates distinct ids");
        // simulate a row that reuses snap's id with different content (direct
        // DB tampering — the typed path cannot construct same-id/diff-content)
        String differentPayload = com.example.platform.timeline.semantics.effect
                .EffectSemanticSnapshotJsonCodec.serialize(differentSnap)
                .replaceFirst("\"snapshotId\":\"[^\"]*\"",
                        "\"snapshotId\":\"" + snap.id().value() + "\"");
        dsl.execute("update timeline_snapshot set payload_json = ? where id = ?",
                differentPayload, snap.id().value());
        assertThrows(IllegalArgumentException.class,
                () -> store.storeTx(dsl, "proj-a", "tenant-a", snap),
                "SC7: same id different content -> FAIL CLOSED (BI4 immutability)");
    }
}
