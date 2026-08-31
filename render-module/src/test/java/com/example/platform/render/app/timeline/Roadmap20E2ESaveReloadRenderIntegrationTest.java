package com.example.platform.render.app.timeline;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.adapter.JdbcEffectDefinitionVersionRegistry;
import com.example.platform.timeline.adapter.JdbcEffectSemanticSnapshotStore;
import com.example.platform.timeline.adapter.JdbcTimelineRevisionSemanticContextStore;
import com.example.platform.timeline.app.TimelineRevisionRefMutation;
import com.example.platform.timeline.app.TimelineRevisionSaveService;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotStore;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.render.domain.renderplan.DefaultRenderPlanner;
import com.example.platform.render.domain.renderplan.RenderNodeKind;
import com.example.platform.render.domain.renderplan.RenderPlan;
import com.example.platform.render.domain.renderplan.RenderPlanningInput;
import com.example.platform.render.domain.renderplan.RenderSourceResolutionState;
import com.example.platform.render.domain.renderplan.SourceResolutionInput;
import com.example.platform.render.domain.renderplan.VerifiedRenderSemanticSnapshot;
import com.example.platform.render.domain.renderplan.VerifiedRenderSemanticSnapshotFactory;
import com.example.platform.render.domain.renderplan.VerifiedTimelineRevisionFactory;
import com.example.platform.shared.test.PostgresTestContainerSupport;
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
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 authority-integration E2E (save → reload → render), §38/E2E-A/E2E-B.
 *
 * E2E-A: no-Effect authoring → authoritative EMPTY snapshot → revision pin →
 * reload → exact verification → RenderPlan with ZERO Effect nodes.
 *
 * E2E-B: typed Effect-bearing authoring → non-empty EffectSemanticSnapshot →
 * revision-owned exact reference → full revision semantic digest → reload →
 * exact snapshot resolution → verification → RenderPlan with complete Effect
 * WHAT. No helper-only shortcut, no manually assembled expectedReference.
 */
class Roadmap20E2ESaveReloadRenderIntegrationTest extends PostgresTestContainerSupport {

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
        com.example.platform.shared.web.TenantContext.clear();
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

    private void insertProduct(String productId) {
        com.example.platform.render.testsupport.RenderTestSchemaFixture.insertCanonicalProject(
                dsl, "tenant-1", productId);
        dsl.execute("insert into product (product_id, tenant_id, project_id, product_type, representation_kind, status, created_at, updated_at) "
                + "values (?, 'tenant-1', ?, 'video', 'master', 'REGISTERED', now(), now()) on conflict (product_id) do nothing",
                productId, productId);
        // artifact pin reference-integrity: the fixture artifact must exist
        dsl.execute("insert into artifact (id, tenant_id, content_digest, byte_length, media_type, artifact_kind, state, schema_version, created_at) "
                + "values ('art-1', 'tenant-1', 'aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa', 100, 'VIDEO', 'SOURCE_MEDIA', 'AVAILABLE', 1, now()) "
                + "on conflict (id) do nothing");
    }

    private TimelineDocument sampleDocument() {
        MediaClip.TimeRange range = new MediaClip.TimeRange(
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1));
        com.example.platform.timeline.canonical.TimelineClip tc = new com.example.platform.timeline.canonical.TimelineClip(
                "c1", "asset-1", "stream-1", "art-1",
                "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                "MEDIA_STREAM",
                com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping.of(
                        1, 1, com.example.platform.timeline.semantics.temporal.PlaybackDirection.FORWARD));
        return new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("t1", "v1", TrackType.VIDEO, List.of(tc))),
                TimelineMetadata.empty(),
                com.example.platform.audio.domain.mix.AudioMix.EMPTY, List.of(), List.of());
    }

    private EffectInstance blurEffect() {
        return new EffectInstance(
                "eff-1", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "4"), Map.of(),
                new ClipEffectTarget("t1", "c1"), EffectInstance.EffectProvenance.untracked());
    }

    private EffectInstance.EffectDefinition blurDef() {
        return new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of("radiusPixels", new EffectInstance.ParameterSchema(
                        "radiusPixels", "string", null, null, "4", List.of())),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("radiusPixels"), List.of("video.effect.gaussian-blur"), List.of());
    }

    @Test
    void e2eA_noEffectSaveReloadRenderProducesZeroEffectNodes() {
        String productId = "prod-e2ea-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument doc = sampleDocument();

        // 1. REAL canonical save path (no-Effect authoring)
        TimelineRevision revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.save(saveService,
                "tenant-1", productId, null, doc,
                com.example.platform.render.testsupport.RenderTestSchemaFixture.SERVER_ACTOR);
        // 2. authoritative EMPTY pinned + persisted
        assertNotNull(revision.effectSemanticSnapshotReference(),
                "E2E-A: new no-Effect revision must own an exact Effect pin (never MISSING)");
        // 3. discard service objects — recreate the application layer
        EffectSemanticSnapshotStore freshStore = new JdbcEffectSemanticSnapshotStore(dsl);
        // 4. reload revision
        TimelineRevision reloaded = saveService.findById("tenant-1", revision.revisionId());
        assertNotNull(reloaded, "E2E-A: reload");
        // hydrate the canonical document from the governed payload (production
        // read path) and rebuild the revision with its OWN persisted context
        TimelineRevision hydrated = reloaded.hydrate(
                saveService.findPayloadDocument("tenant-1", reloaded.revisionId()).orElseThrow());
        // 5. exact pin comes FROM the revision
        var pin = hydrated.effectSemanticSnapshotReference();
        // 6. load exact snapshot from durable store
        EffectSemanticSnapshot snapshot = freshStore.findById(productId, "tenant-1", pin.snapshotId()).orElseThrow();
        // 7. verify id/digest/version/recomputed digest
        assertEquals(pin.snapshotId(), snapshot.id());
        assertEquals(pin.contentDigest(), snapshot.contentDigest());
        assertEquals(pin.semanticContractVersion(), snapshot.semanticContractVersion());
        assertEquals(0, snapshot.entries().size(), "E2E-A: authoritative EMPTY");
        // 8. verified render semantic snapshot (pin FROM revision, not caller)
        VerifiedRenderSemanticSnapshot verified = VerifiedRenderSemanticSnapshotFactory.verified(
                hydrated, new TimelineContentDigester(), snapshot);
        // 9. materialize RenderPlan
        RenderPlanningInput input = new RenderPlanningInput(
                verified, new com.example.platform.render.domain.renderplan.RenderRequest(
                        new com.example.platform.render.domain.renderplan.RenderRequestId("req-1"),
                        new com.example.platform.render.domain.renderplan.RenderExtent(
                                com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                                com.example.platform.shared.time.MediaTime.ofRational(2, 1),
                                com.example.platform.shared.time.FrameRate.of(30, 1)),
                        List.of(com.example.platform.render.domain.renderplan.RenderOutputRequirement.of(com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER))),
                new SourceResolutionInput(Map.of(
                        new com.example.platform.shared.identity.ArtifactId("art-1"),
                        RenderSourceResolutionState.RESOLVED)),
                new com.example.platform.render.domain.renderplan.CapabilityContext(java.util.Set.of()));
        RenderPlan plan = new DefaultRenderPlanner().plan(input).plan();
        // 10. zero Effect nodes
        assertEquals(0, plan.nodes().stream()
                        .filter(n -> n.kind() instanceof RenderNodeKind.Effect).count(),
                "E2E-A: RenderPlan has zero Effect nodes");
    }

    @Test
    void e2eB_effectSaveReloadRenderProducesCompleteEffectWhat() {
        String productId = "prod-e2eb-" + UUID.randomUUID();
        insertProduct(productId);
        TimelineDocument doc = sampleDocument();

        // 1. REAL typed Effect-bearing canonical save path
        TimelineRevision revision = com.example.platform.render.testsupport.TimelineMutationTestSupport.saveWithEffects(saveService,
                "tenant-1", productId, null, doc, List.of(blurEffect()), List.of(blurDef()),
                com.example.platform.render.testsupport.RenderTestSchemaFixture.SERVER_ACTOR);
        // 2. NON-EMPTY snapshot persisted + exact pin owned by the revision
        assertNotNull(revision.effectSemanticSnapshotReference());
        // 3. recreate application layer
        EffectSemanticSnapshotStore freshStore = new JdbcEffectSemanticSnapshotStore(dsl);
        // 4. reload revision
        TimelineRevision reloaded = saveService.findById("tenant-1", revision.revisionId());
        TimelineRevision hydrated = reloaded.hydrate(
                saveService.findPayloadDocument("tenant-1", reloaded.revisionId()).orElseThrow());
        // 5. pin FROM revision-owned persisted state
        var pin = hydrated.effectSemanticSnapshotReference();
        // 6. load exact snapshot by pin
        EffectSemanticSnapshot snapshot = freshStore.findById(productId, "tenant-1", pin.snapshotId()).orElseThrow();
        // 7. verify id/digest/version
        assertEquals(pin.snapshotId(), snapshot.id());
        assertEquals(pin.contentDigest(), snapshot.contentDigest());
        assertEquals(1, snapshot.entries().size(), "E2E-B: non-empty authoritative snapshot");
        assertEquals("eff-1", snapshot.entries().get(0).effectInstanceId());
        assertEquals("def-blur", snapshot.entries().get(0).definitionSnapshot().definitionId());
        // 8. verified render semantic snapshot
        VerifiedRenderSemanticSnapshot verified = VerifiedRenderSemanticSnapshotFactory.verified(
                hydrated, new TimelineContentDigester(), snapshot);
        // 9. RenderPlan
        RenderPlanningInput input = new RenderPlanningInput(
                verified, new com.example.platform.render.domain.renderplan.RenderRequest(
                        new com.example.platform.render.domain.renderplan.RenderRequestId("req-1"),
                        new com.example.platform.render.domain.renderplan.RenderExtent(
                                com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                                com.example.platform.shared.time.MediaTime.ofRational(2, 1),
                                com.example.platform.shared.time.FrameRate.of(30, 1)),
                        List.of(com.example.platform.render.domain.renderplan.RenderOutputRequirement.of(com.example.platform.render.domain.renderplan.RenderOutputRole.RENDER_MASTER))),
                new SourceResolutionInput(Map.of(
                        new com.example.platform.shared.identity.ArtifactId("art-1"),
                        RenderSourceResolutionState.RESOLVED)),
                new com.example.platform.render.domain.renderplan.CapabilityContext(java.util.Set.of()));
        RenderPlan plan = new DefaultRenderPlanner().plan(input).plan();
        // 10. complete Effect WHAT
        var effectNodes = plan.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .toList();
        assertEquals(1, effectNodes.size(), "E2E-B: exactly one Effect node");
        assertTrue(effectNodes.get(0).id().value().contains("eff-1"),
                "E2E-B: Effect node carries the authored instance id");
        assertNotNull(plan.effectSemanticReference(), "E2E-B: plan carries the revision's Effect pin");
    }
}
