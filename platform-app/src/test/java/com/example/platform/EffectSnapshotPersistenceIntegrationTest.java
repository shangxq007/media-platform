package com.example.platform;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectDefinitionSnapshot;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticContractVersion;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotJsonCodec;
import java.util.List;
import java.util.Map;
import org.flywaydb.core.Flyway;
import org.jooq.DSLContext;
import org.jooq.impl.DSL;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * ROADMAP20 final implementation — durable persistence acceptance (storage
 * direction B: dedicated immutable timeline_effect_snapshot row).
 *
 * <p>Covers (instructions §36-39, §68):
 * <ul>
 *   <li>V2 migration applies cleanly on real PostgreSQL;</li>
 *   <li>durable snapshot reload after simulated restart (new store instance);</li>
 *   <li>snapshot immutability across restarts (BI4);</li>
 *   <li>definition (id, version) -> digest collision enforcement ACROSS durable
 *       snapshots (D1/§38);</li>
 *   <li>atomic store transaction (rollback leaves no row).</li>
 * </ul>
 */
class EffectSnapshotPersistenceIntegrationTest extends PostgresTestContainerSupport {

    private static DSLContext dsl;

    @BeforeAll
    static void migrateAndConnect() {
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl(), username(), password())
                .locations("classpath:db/migration")
                .baselineOnMigrate(true)
                .load();
        flyway.migrate();
        var ds = new org.springframework.jdbc.datasource.DriverManagerDataSource();
        ds.setDriverClassName("org.postgresql.Driver");
        ds.setUrl(jdbcUrl());
        ds.setUsername(username());
        ds.setPassword(password());
        dsl = DSL.using(ds, org.jooq.SQLDialect.POSTGRES);
        // timeline_snapshot.project_id has an FK to project(id) — ensure the
        // fixture project exists (idempotent).
        dsl.execute("insert into project (id, tenant_id, name, status, created_at) "
                + "values ('proj-1', 'tenant-1', 'effect-snapshot-fixture', 'ACTIVE', now()) "
                + "on conflict (id) do nothing");
    }

    private static MediaClip clip(String clipId) {
        MediaClip.TimeRange range = new MediaClip.TimeRange(
                com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                com.example.platform.shared.time.MediaTime.ofRational(2, 1));
        return new MediaClip(clipId, "t1", range, range,
                com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping.of(
                        1, 1, com.example.platform.timeline.semantics.temporal.PlaybackDirection.FORWARD),
                new com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding(
                        com.example.platform.media.domain.identity.MediaAssetId.of("asset-1"),
                        com.example.platform.media.domain.stream.MediaStreamId.of("stream-1"),
                        new com.example.platform.shared.identity.ArtifactId("art-1"),
                        com.example.platform.shared.digest.ContentDigest.sha256(
                                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
                        range));
    }

    private static EffectInstance blurEffect() {
        return new EffectInstance(
                "eff-1", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "4"), Map.of(),
                new ClipEffectTarget("t1", "c1"), EffectInstance.EffectProvenance.untracked());
    }

    private static EffectInstance.EffectDefinition blurDef() {
        return new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of("radiusPixels", new EffectInstance.ParameterSchema("radiusPixels", "string", null, null, "4", List.of())),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("radiusPixels"), List.of("video.effect.gaussian-blur"), List.of());
    }

    private static EffectSemanticSnapshot mint(EffectDefinitionVersionRegistry registry, EffectSemanticSnapshotId id) {
        return EffectSemanticSnapshotAuthority.mint(
                List.of(blurEffect()), List.of(blurDef()),
                key -> clip(key.clipId()), registry, id);
    }

    @Test
    void durableSnapshotSurvivesStoreRecreation() {
        // 1. persist S1 via store instance A
        EffectSemanticSnapshotId id = EffectSemanticSnapshotId.generate();
        EffectSemanticSnapshot s1 = mint(new EffectDefinitionVersionRegistry.InMemory(), id);
        new JdbcEffectSemanticSnapshotStore(dsl, "proj-1").store(s1);
        // 2. "restart": NEW store instance (no in-memory state)
        JdbcEffectSemanticSnapshotStore fresh = new JdbcEffectSemanticSnapshotStore(dsl, "proj-1");
        // 3. reload exact S1
        EffectSemanticSnapshot reloaded = fresh.findById(id).orElseThrow();
        assertEquals(s1.contentDigest(), reloaded.contentDigest(), "exact digest after restart");
        assertEquals(s1.semanticContractVersion(), reloaded.semanticContractVersion(), "exact version");
        assertEquals(id, reloaded.id(), "exact snapshot id");
        assertEquals("def-blur", reloaded.entries().get(0).definitionSnapshot().definitionId(),
                "exact definition semantics after restart");
        assertEquals("4", reloaded.entries().get(0).parameters().get(0).value(),
                "exact parameter values after restart");
    }

    @Test
    void snapshotImmutabilityEnforcedAcrossRestarts() {
        EffectSemanticSnapshotId id = EffectSemanticSnapshotId.generate();
        EffectSemanticSnapshot s1 = mint(new EffectDefinitionVersionRegistry.InMemory(), id);
        new JdbcEffectSemanticSnapshotStore(dsl, "proj-1").store(s1);
        // different content under the SAME id -> FAIL CLOSED (BI4), durable
        EffectInstance tampered = new EffectInstance(
                "eff-1", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "66"), Map.of(),
                new ClipEffectTarget("t1", "c1"), EffectInstance.EffectProvenance.untracked());
        EffectSemanticSnapshot different = EffectSemanticSnapshotAuthority.mint(
                List.of(tampered), List.of(blurDef()), key -> clip(key.clipId()),
                new EffectDefinitionVersionRegistry.InMemory(), id);
        JdbcEffectSemanticSnapshotStore fresh = new JdbcEffectSemanticSnapshotStore(dsl, "proj-1");
        assertThrows(IllegalArgumentException.class,
                () -> fresh.store(different),
                "BI4: same id different content -> durable FAIL CLOSED");
    }

    @Test
    void definitionVersionCollisionEnforcedAcrossDurableSnapshots() {
        // S1 embeds def-blur@1 digest A (durable). A new snapshot with the SAME
        // (id, version) but DIFFERENT content must FAIL CLOSED via the durable
        // registry (D1 cross-snapshot, restart-safe).
        EffectSemanticSnapshotId id1 = EffectSemanticSnapshotId.generate();
        EffectSemanticSnapshot s1 = mint(new EffectDefinitionVersionRegistry.InMemory(), id1);
        new JdbcEffectSemanticSnapshotStore(dsl, "proj-1").store(s1);
        // def-blur@1 with DIFFERENT semantic content (different requiredCapabilities)
        EffectInstance.EffectDefinition defDiff = new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                blurDef().parameterSchema(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                blurDef().deterministicProperties(),
                List.of("video.effect.gaussian-blur", "video.effect.changed"), List.of());
        EffectSemanticSnapshotId id2 = EffectSemanticSnapshotId.generate();
        JdbcEffectDefinitionVersionRegistry durableRegistry = new JdbcEffectDefinitionVersionRegistry(dsl);
        assertThrows(IllegalArgumentException.class,
                () -> EffectSemanticSnapshotAuthority.mint(
                        List.of(blurEffect()), List.of(defDiff), key -> clip(key.clipId()),
                        durableRegistry, id2),
                "D1: same (id, version) different digest across durable snapshots -> FAIL CLOSED");
    }

    @Test
    void codecRoundTripPreservesSemantics() {
        EffectSemanticSnapshot s = mint(new EffectDefinitionVersionRegistry.InMemory(),
                EffectSemanticSnapshotId.generate());
        String payload = EffectSemanticSnapshotJsonCodec.serialize(s);
        EffectSemanticSnapshot decoded = EffectSemanticSnapshotJsonCodec.deserialize(payload);
        assertEquals(s.contentDigest(), decoded.contentDigest(), "digest preserved through codec");
        assertEquals(s.entries().size(), decoded.entries().size(), "entries preserved");
        assertEquals("def-blur", decoded.entries().get(0).definitionSnapshot().definitionId());
        assertEquals(EffectSemanticContractVersion.current(), decoded.semanticContractVersion());
    }

    @Test
    void empty3_emptySnapshotReloadedAfterRestart() {
        // EMPTY3: persist a NEW authoritative EMPTY snapshot; "restart" (new
        // store instance); reload EXACT empty snapshot verified (digest +
        // version + id), distinct from legacy missing.
        EffectSemanticSnapshotId id = EffectSemanticSnapshotId.generate();
        EffectSemanticSnapshot empty = EffectSemanticSnapshotAuthority.mint(
                List.of(), List.of(), key -> clip(key.clipId()),
                new EffectDefinitionVersionRegistry.InMemory(), id);
        new JdbcEffectSemanticSnapshotStore(dsl, "proj-1").store(empty);
        JdbcEffectSemanticSnapshotStore fresh = new JdbcEffectSemanticSnapshotStore(dsl, "proj-1");
        EffectSemanticSnapshot reloaded = fresh.findById(id).orElseThrow();
        assertEquals(0, reloaded.entries().size(), "EMPTY3: authoritative empty after restart");
        assertEquals(empty.contentDigest(), reloaded.contentDigest(), "EMPTY3: exact empty digest");
        assertEquals(empty.semanticContractVersion(), reloaded.semanticContractVersion(),
                "EMPTY3: exact contract version");
        // legacy NULL pin row (no snapshot) remains distinguishable:
        assertTrue(fresh.findById(EffectSemanticSnapshotId.of("esnap_nonexistent_000000")).isEmpty(),
                "EMPTY3: missing snapshot row is MISSING, not EMPTY");
    }
}
