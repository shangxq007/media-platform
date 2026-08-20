package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 correction F1/F2/F3/F4 acceptance tests:
 * <ul>
 *   <li>F1: logical WHAT is typed, immutable, self-contained (no opaque hashes
 *       as the semantic representation).</li>
 *   <li>F2: TimedText materialization is typed and graph-connected; font
 *       resolution is consumed, not recomputed; text semantics stay Timeline
 *       authority.</li>
 *   <li>F3: logical nodes carry platform CapabilityRequirement; capability
 *       availability never alters the logical fingerprint.</li>
 *   <li>F4: primary planner API consumes one coherent immutable revision
 *       projection; fragments cannot be casually mixed with a revision ref.</li>
 * </ul>
 */
class LogicalWhatClosureAcceptanceTest {

    // ── F1: typed self-contained logical WHAT ────────────────────────────────

    @Test
    void effectWhatIsTypedAndRecoverableFromPlan() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode effect = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.Effect());
        EffectMaterializationRequirement req =
                (EffectMaterializationRequirement) effect.materializationRequirements().get(0);
        assertEquals(EffectInstance.EffectCategory.GAUSSIAN_BLUR, req.category());
        // typed parameter list: radiusPixels key exists and value is recoverable
        assertTrue(req.parameters().stream().anyMatch(p -> p.key().equals("radiusPixels")),
                "radiusPixels parameter key present");
        assertEquals("4", parameterValue(req, "radiusPixels"), "radiusPixels value recoverable");
        // deterministic ordering by key
        List<String> keys = req.parameters().stream().map(EffectMaterializationRequirement.EffectParameter::key).toList();
        assertEquals(keys.stream().sorted().toList(), keys, "parameter ordering deterministic (sorted by key)");
        // typed, not a Map<String,Object> blob
        assertTrue(req instanceof RenderMaterializationRequirement);
    }

    private static String parameterValue(EffectMaterializationRequirement req, String key) {
        return req.parameters().stream()
                .filter(p -> p.key().equals(key))
                .map(EffectMaterializationRequirement.EffectParameter::value)
                .findFirst()
                .orElse(null);
    }

    @Test
    void audioWhatIsTypedAndRecoverableFromPlan() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode audio = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.AudioProcess());
        AudioProcessMaterializationRequirement req =
                (AudioProcessMaterializationRequirement) audio.materializationRequirements().get(0);
        assertEquals(0.8, req.gain().linear());
        assertFalse(req.mute().muted());
        assertEquals(0.0, req.balance().value());
    }

    @Test
    void timedTextWhatIsTypedAndRecoverableFromPlan() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode text = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.TimedText());
        TimedTextMaterializationRequirement req =
                (TimedTextMaterializationRequirement) text.materializationRequirements().get(0);
        assertEquals("Hello", req.textContent().value());
        assertEquals(com.example.platform.fonttext.typography.FontRational.whole(0), req.start());
        assertEquals(com.example.platform.fonttext.typography.FontRational.whole(5), req.duration());
        assertFalse(req.resolvedFontRuns().isEmpty(), "resolved font runs consumed (not recomputed)");
    }

    @Test
    void parameterSemanticChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();

        // change effect parameter: radiusPixels 4 -> 8
        EffectInstance changedEffect = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "8"), Map.of(),
                TestPlans.gaussianBlurEffect().provenance());
        HydratedTimelineRevision rev = TestPlans.hydratedRevision();
        HydratedTimelineRevision changedRev = new HydratedTimelineRevision(
                rev.revision(), rev.clips(), List.of(changedEffect), rev.effectDefinitions(),
                rev.audioMix(), rev.textElements());
        RenderPlanningInput changed = new RenderPlanningInput(
                changedRev, base.request(), base.resolution(), base.capabilities());
        String changedFp = planner.plan(changed).plan().fingerprint().sha256Hex();

        assertNotEquals(baseFp, changedFp, "effect parameter semantic change -> fingerprint changes");
    }

    @Test
    void effectCategoryChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();

        // category FADE instead of GAUSSIAN_BLUR (definition catalog change)
        EffectInstance.EffectDefinition fadeDef = new EffectInstance.EffectDefinition(
                "def-fade", "1", EffectInstance.EffectCategory.FADE,
                List.of(EffectInstance.EffectMediaType.VIDEO), Map.of(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION, List.of(), List.of(), List.of());
        EffectInstance fadeEffect = new EffectInstance(
                "eff-fade", "def-fade", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of(), Map.of(), TestPlans.gaussianBlurEffect().provenance());
        HydratedTimelineRevision rev = TestPlans.hydratedRevision();
        HydratedTimelineRevision changedRev = new HydratedTimelineRevision(
                rev.revision(), rev.clips(), List.of(fadeEffect), List.of(fadeDef),
                rev.audioMix(), rev.textElements());
        RenderPlanningInput changed = new RenderPlanningInput(
                changedRev, base.request(), base.resolution(), base.capabilities());
        String changedFp = planner.plan(changed).plan().fingerprint().sha256Hex();

        assertNotEquals(baseFp, changedFp, "effect category change -> fingerprint changes");
    }

    @Test
    void audioSemanticChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();

        HydratedTimelineRevision rev = TestPlans.hydratedRevision();
        HydratedTimelineRevision changedRev = new HydratedTimelineRevision(
                rev.revision(), rev.clips(), rev.effects(), rev.effectDefinitions(),
                TestPlans.audioMixWithGain(0.5), rev.textElements());
        RenderPlanningInput changed = new RenderPlanningInput(
                changedRev, base.request(), base.resolution(), base.capabilities());
        String changedFp = planner.plan(changed).plan().fingerprint().sha256Hex();

        assertNotEquals(baseFp, changedFp, "audio gain change -> fingerprint changes");
    }

    @Test
    void timedTextContentChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();

        HydratedTimelineRevision rev = TestPlans.hydratedRevision();
        HydratedTimelineRevision changedRev = new HydratedTimelineRevision(
                rev.revision(), rev.clips(), rev.effects(), rev.effectDefinitions(),
                rev.audioMix(), List.of(TestPlans.textElementWithContent("Goodbye")));
        RenderPlanningInput changed = new RenderPlanningInput(
                changedRev, base.request(), base.resolution(), base.capabilities());
        String changedFp = planner.plan(changed).plan().fingerprint().sha256Hex();

        assertNotEquals(baseFp, changedFp, "timed text content change -> fingerprint changes");
    }

    @Test
    void missingEffectDefinitionFailsClosed() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        // remove the effect definition catalog entry
        HydratedTimelineRevision rev = TestPlans.hydratedRevision();
        HydratedTimelineRevision missingDef = new HydratedTimelineRevision(
                rev.revision(), rev.clips(), rev.effects(), List.of(),
                rev.audioMix(), rev.textElements());
        RenderPlanningInput input = new RenderPlanningInput(
                missingDef, base.request(), base.resolution(), base.capabilities());
        RenderPlanningResult result = planner.plan(input);

        assertTrue(result.diagnostics().stream().anyMatch(
                        d -> d.code() == RenderPlanningDiagnosticCode.PLANNING_UNSUPPORTED),
                "missing EffectDefinition -> PLANNING_UNSUPPORTED (fail closed)");
    }

    // ── F2: TimedText connectivity ──────────────────────────────────────────

    @Test
    void timedTextIsNotOrphanedAndReachesOutput() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode text = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.TimedText());
        RenderNode composite = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.Composite());
        RenderNode output = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.Output());

        // typed path: TIMED_TEXT -> COMPOSITE -> OUTPUT
        assertTrue(hasEdge(result.plan().edges(), text.id(), composite.id(), "COMPOSITE_INPUT"),
                "TIMED_TEXT --CompositeInput--> COMPOSITE");
        assertTrue(hasEdge(result.plan().edges(), composite.id(), output.id(), "COMPOSITE_INPUT"),
                "COMPOSITE --CompositeInput--> OUTPUT");

        // not orphaned: outgoing edge exists
        assertTrue(result.plan().edges().stream().anyMatch(e -> e.producerId().equals(text.id())),
                "TIMED_TEXT has an outgoing edge");
    }

    @Test
    void fontResolutionIsConsumedNotRecomputed() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode text = firstNodeOfKind(result.plan().nodes(), new RenderNodeKind.TimedText());
        TimedTextMaterializationRequirement req =
                (TimedTextMaterializationRequirement) text.materializationRequirements().get(0);
        // The typed projection carries the SAME resolved font runs as the authored
        // TextElement — render consumes, never recomputes.
        assertEquals(TestPlans.textElement().resolvedFontRuns(), req.resolvedFontRuns(),
                "font resolution consumed from TextElement, not recomputed");
    }

    @Test
    void noProviderRasterCommandInTimedText() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        for (RenderNode node : result.plan().nodes()) {
            assertFalse(node.operationKey().toLowerCase().contains("libass"),
                    "no libass in operation key");
            assertFalse(node.operationKey().toLowerCase().contains("ffmpeg"),
                    "no ffmpeg in operation key");
            assertFalse(node.operationKey().toLowerCase().contains("subtitle_filter"),
                    "no subtitle filter command");
        }
    }

    // ── F3: platform capability authority ───────────────────────────────────

    @Test
    void logicalNodesCarryPlatformCapabilityRequirements() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        assertFalse(result.plan().nodes().isEmpty());
        for (RenderNode node : result.plan().nodes()) {
            for (var cap : node.capabilityRequirements()) {
                assertNotNull(cap.capabilityId());
                assertNotNull(cap.contractRange());
                assertTrue(cap.capabilityId().isPlatformReserved(),
                        "capability id is platform-reserved: " + cap.capabilityId());
            }
        }
    }

    @Test
    void capabilityAvailabilityDoesNotAlterFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput full = TestPlans.canonicalInput();
        RenderPlanningInput minimal = TestPlans.inputWithCapabilities(new CapabilityContext(Set.of()));
        assertEquals(planner.plan(full).plan().fingerprint().sha256Hex(),
                planner.plan(minimal).plan().fingerprint().sha256Hex(),
                "capability availability is transient — never in the logical fingerprint");
    }

    @Test
    void noProviderIdentityInLogicalCapability() {
        for (CapabilityId id : RenderCapabilityVocabularyIds.all()) {
            String v = id.value().toLowerCase();
            assertFalse(v.contains("ffmpeg"));
            assertFalse(v.contains("plugin"));
            assertFalse(v.contains("worker"));
            assertFalse(v.contains("device"));
            assertFalse(v.contains("tier"));
            assertFalse(v.contains("price"));
        }
    }

    // ── F4: revision integrity ──────────────────────────────────────────────

    @Test
    void primaryApiConsumesCoherentRevisionProjection() {
        // The primary planning API takes a HydratedTimelineRevision — callers
        // cannot pass revision identity plus independently assembled fragments.
        RenderPlanningInput input = TestPlans.canonicalInput();
        HydratedTimelineRevision projection = input.hydratedRevision();
        assertNotNull(projection.revision());
        assertEquals(TestPlans.REVISION_ID, projection.revision().revisionId());
        assertNotNull(projection.revision().contentDigest());
        assertEquals(1, projection.clips().size());
        assertEquals(1, projection.effects().size());
        assertEquals(1, projection.textElements().size());
        // one coherent revision: identity + content + fragments all in one object
        assertEquals(projection.clips().get(0).clipId(), TestPlans.CLIP_ID);
    }

    @Test
    void revisionProjectionIsImmutable() {
        HydratedTimelineRevision projection = TestPlans.hydratedRevision();
        // mutating the returned lists must fail (List.copyOf defensive copy)
        assertThrows(UnsupportedOperationException.class,
                () -> projection.clips().add(null));
        assertThrows(UnsupportedOperationException.class,
                () -> projection.textElements().add(null));
    }

    @Test
    void revisionChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();
        HydratedTimelineRevision other = new HydratedTimelineRevision(
                new TimelineRevisionReference("rev-2", ContentDigest.sha256(TestPlans.REVISION_DIGEST_HEX)),
                base.hydratedRevision().clips(), base.hydratedRevision().effects(),
                base.hydratedRevision().effectDefinitions(), base.hydratedRevision().audioMix(),
                base.hydratedRevision().textElements());
        RenderPlanningInput changed = new RenderPlanningInput(
                other, base.request(), base.resolution(), base.capabilities());
        assertNotEquals(baseFp, planner.plan(changed).plan().fingerprint().sha256Hex(),
                "revision identity/content change -> fingerprint changes");
    }

    @Test
    void noMapStringObjectSemanticEscapeHatch() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        for (RenderNode node : result.plan().nodes()) {
            for (RenderMaterializationRequirement req : node.materializationRequirements()) {
                // sealed typed variants only
                assertTrue(req instanceof EffectMaterializationRequirement
                                || req instanceof AudioProcessMaterializationRequirement
                                || req instanceof TimedTextMaterializationRequirement,
                        "materialization requirement is a sealed typed variant");
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private RenderNode firstNodeOfKind(List<RenderNode> nodes, RenderNodeKind kind) {
        return nodes.stream()
                .filter(n -> n.kind().canonicalName().equals(kind.canonicalName()))
                .findFirst()
                .orElseThrow();
    }

    private boolean hasEdge(List<RenderDependencyEdge> edges, RenderNodeId producer, RenderNodeId consumer, String variant) {
        return edges.stream().anyMatch(e ->
                e.producerId().equals(producer) && e.consumerId().equals(consumer)
                        && e.dependency().variantKey().startsWith(variant));
    }
}
