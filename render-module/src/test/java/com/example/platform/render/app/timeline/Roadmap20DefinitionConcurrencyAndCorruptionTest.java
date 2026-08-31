package com.example.platform.render.app.timeline;

import com.example.platform.shared.test.PostgresTestContainerSupport;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.app.TimelineRevisionRefMutation;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.semantics.effect.EffectDefinitionSnapshot;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import org.jooq.impl.DSL;
import org.jooq.DSLContext;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.example.platform.timeline.app.DefaultTimelineRevisionPersistence;
import com.example.platform.timeline.app.TimelineRevisionRefHeadUpdateAdapter;

import javax.sql.DataSource;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 authority-integration durable-integrity tests (AI18/AI19, §12-14):
 *
 * AI18: two CONCURRENT canonical Effect snapshot writes with the same
 * (definitionId, version) but different semantic content — exactly ONE wins,
 * the other FAILS CLOSED (advisory-lock serialized definition identity).
 *
 * AI19: a corrupt authoritative esnap_ row FAILS CLOSED during definition-
 * version identity verification — never silently skipped.
 */
class Roadmap20DefinitionConcurrencyAndCorruptionTest extends PostgresTestContainerSupport {

    private static DataSource dataSource;
    private static DSLContext dsl;
    private TimelineRevisionSaveService saveService;
    private TimelineRevisionRefMutation currentRevisionService;

    @BeforeAll
    static void setUpDatabase() {
        dataSource = createDataSource();
        dsl = DSL.using(dataSource, org.jooq.SQLDialect.POSTGRES);
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
        currentRevisionService = new TimelineRevisionRefMutation(dsl);
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
                authority, new JdbcTimelineRevisionSemanticContextStore(dsl), new DefaultTimelineRevisionPersistence(), new TimelineRevisionRefHeadUpdateAdapter(currentRevisionService), com.example.platform.render.testsupport.TimelineMutationTestSupport.ALLOW_ALL);
    }

    private void insertFixtures(String productId) {
        com.example.platform.render.testsupport.RenderTestSchemaFixture.insertCanonicalProject(
                dsl, "tenant-1", productId);
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

    private EffectInstance blurEffect(String effectId, String radius) {
        return new EffectInstance(
                effectId, "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new com.example.platform.timeline.semantics.clip.MediaClip.TimeRange(
                        com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                java.util.Map.of("radiusPixels", radius), java.util.Map.of(),
                new com.example.platform.timeline.semantics.effect.ClipEffectTarget("t1", "c1"),
                EffectInstance.EffectProvenance.untracked());
    }

    private EffectInstance.EffectDefinition blurDef(String radius) {
        return new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                java.util.Map.of("radiusPixels", new EffectInstance.ParameterSchema(
                        "radiusPixels", "string", null, null, radius, List.of())),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("radiusPixels"), List.of("video.effect.gaussian-blur"), List.of());
    }

    @Test
    void ai18_concurrentConflictingDefinitionWritesExactlyOneWins() throws Exception {
        // definition identity is GLOBAL (cross-product): two concurrent writers
        // in DIFFERENT products, same def-blur@1, different semantic content.
        String productA = "prod-ai18a-" + UUID.randomUUID();
        String productB = "prod-ai18b-" + UUID.randomUUID();
        String productC = "prod-ai18c-" + UUID.randomUUID();
        String productD = "prod-ai18d-" + UUID.randomUUID();
        insertFixtures(productA);
        insertFixtures(productB);
        insertFixtures(productC);
        insertFixtures(productD);
        var doc = sampleDocument();

        // serial sanity: a SECOND write (different product) with a conflicting
        // definition digest must FAIL CLOSED (proves registerTx scanning works
        // before we test the concurrent variant)
        com.example.platform.render.testsupport.TimelineMutationTestSupport.saveWithEffects(saveService,
                "tenant-1", productA, null, doc, List.of(blurEffect("eff-a", "4")),
                List.of(blurDef("4")),
                com.example.platform.render.testsupport.RenderTestSchemaFixture.SERVER_ACTOR);
        // DIAGNOSTIC: how many esnap_ rows exist after the first save?
        java.util.List<String> esnapPayloads = dsl.fetch(
                        "select payload_json from timeline_snapshot where id like 'esnap_%'")
                .getValues(0, String.class);
        System.out.println("[AI18-DIAG] esnap rows after first save: " + esnapPayloads.size()
                + " first-payload-prefix=" + (esnapPayloads.isEmpty() ? "NONE"
                : esnapPayloads.get(0).substring(0, Math.min(60, esnapPayloads.get(0).length()))));
        assertThrows(IllegalArgumentException.class,
                () -> com.example.platform.render.testsupport.TimelineMutationTestSupport.saveWithEffects(saveService,
                        "tenant-1", productB, null, doc, List.of(blurEffect("eff-a2", "77")),
                        List.of(blurDef("77")),
                        com.example.platform.render.testsupport.RenderTestSchemaFixture.SERVER_ACTOR),
                "AI18-serial: conflicting definition digest must FAIL CLOSED");

        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            CountDownLatch start = new CountDownLatch(1);
            Future<?> f1 = pool.submit(() -> {
                try {
                    com.example.platform.shared.web.TenantContext.set("tenant-1");
                    start.await();
                    com.example.platform.render.testsupport.TimelineMutationTestSupport.saveWithEffects(saveService,
                            "tenant-1", productC, null, doc, List.of(blurEffect("eff-a", "4")),
                            List.of(blurDef("4")),
                            com.example.platform.render.testsupport.RenderTestSchemaFixture.SERVER_ACTOR);
                    return "A-OK";
                } catch (Exception e) {
                    return "A-FAIL:" + e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()).substring(0, Math.min(60, String.valueOf(e.getMessage()).length()));
                }
            });
            Future<?> f2 = pool.submit(() -> {
                try {
                    com.example.platform.shared.web.TenantContext.set("tenant-1");
                    start.await();
                    com.example.platform.render.testsupport.TimelineMutationTestSupport.saveWithEffects(saveService,
                            "tenant-1", productD, null, doc, List.of(blurEffect("eff-b", "77")),
                            List.of(blurDef("77")),
                            com.example.platform.render.testsupport.RenderTestSchemaFixture.SERVER_ACTOR);
                    return "B-OK";
                } catch (Exception e) {
                    return "B-FAIL:" + e.getClass().getSimpleName() + ":" + String.valueOf(e.getMessage()).substring(0, Math.min(60, String.valueOf(e.getMessage()).length()));
                }
            });
            start.countDown();
            String r1 = String.valueOf(f1.get(90, TimeUnit.SECONDS));
            String r2 = String.valueOf(f2.get(90, TimeUnit.SECONDS));
            // exactly one wins; the other FAILS CLOSED (definition identity)
            int okCount = (r1.startsWith("A-OK") ? 1 : 0) + (r2.startsWith("B-OK") ? 1 : 0);
            assertTrue(okCount == 1,
                    "AI18: exactly ONE authoritative definition identity wins — got " + r1 + " / " + r2);
        } finally {
            pool.shutdownNow();
        }
    }

    @Test
    void ai19_corruptAuthoritativeRowFailsClosed() {
        String productId = "prod-ai19-" + UUID.randomUUID();
        insertFixtures(productId);
        var doc = sampleDocument();
        // persist a valid Effect-bearing revision first
        var revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.saveWithEffects(saveService,
                "tenant-1", productId, null, doc, List.of(blurEffect("eff-1", "4")),
                List.of(blurDef("4")),
                com.example.platform.render.testsupport.RenderTestSchemaFixture.SERVER_ACTOR);
        // build the definition identity BEFORE corrupting (mint registers it
        // while the row is still valid)
        JdbcEffectDefinitionVersionRegistry registry = new JdbcEffectDefinitionVersionRegistry(dsl);
        EffectSemanticSnapshotAuthority authority = new EffectSemanticSnapshotAuthority(
                registry, new JdbcEffectSemanticSnapshotStore(dsl));
        EffectSemanticSnapshot snap = authority.mintFromAuthoredState(
                List.of(blurEffect("eff-1", "4")), List.of(blurDef("4")), sampleDocument());
        EffectDefinitionSnapshot def = snap.entries().get(0).definitionSnapshot();
        // corrupt the authoritative esnap_ row payload
        dsl.execute("update timeline_snapshot set payload_json = '{corrupt-json' where id = ?",
                revision.effectSemanticSnapshotReference().snapshotId().value());

        // subsequent definition-version identity verification MUST FAIL CLOSED
        // (never skip-and-continue)
        assertThrows(IllegalStateException.class,
                () -> registry.register(def),
                "AI19: corrupt authoritative esnap_ row -> FAIL CLOSED during definition identity");
    }
}
