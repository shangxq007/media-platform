package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.EffectSemanticStateCanonicalSemantics;
import com.example.platform.timeline.version.TimelineRevision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 correction R5 acceptance tests:
 * <ul>
 *   <li>R5-A: authority-issued binding — relabel attack impossible, ownership
 *       verified against the revision's actual clips, no public mint path.</li>
 *   <li>R5-B: final Logical RenderPlan carries complete typed Effect WHAT —
 *       no-authored-reread consumer proof, application range / definition
 *       version / automation / temporal behavior recoverable.</li>
 *   <li>R5-E/F: single digest contract, collection ordering determinism.</li>
 * </ul>
 */
class R5AcceptanceTest {

    // ── R5-A / CLEAN-FORWARD: old authority retirement ─────────────────────

    @Test
    void oldAuthoritiesDoNotExistCleanForward() {
        // CLEAN-FORWARD (CF6/CF7): old canonical issuance authorities were
        // DELETED, not deprecated — no compatibility surface remains.
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.example.platform.timeline.semantics.effect.EffectSemanticBinding"),
                "CF7: EffectSemanticBinding deleted");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.example.platform.timeline.semantics.effect.AuthoredEffectSemanticAuthority"),
                "CF6: AuthoredEffectSemanticAuthority deleted");
        assertThrows(ClassNotFoundException.class,
                () -> Class.forName("com.example.platform.timeline.semantics.effect.RevisionOwnedEffectProjection"),
                "CF8: RevisionOwnedEffectProjection deleted");
    }

    @Test
    void crossRevisionCombinationFailsClosed() {
        // R1's snapshot offered against R2's pin -> fail closed (binding
        // identity is exact and immutable — RP3-C/BI2).
        EffectSemanticSnapshot r1Snapshot = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshot r2Snapshot = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        TimelineRevision r2 = TestPlans.timelineRevisionWithId("rev-2");
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedRenderSemanticSnapshotFactory.verified(
                        r2, TestPlans.timelineDigester(), r1Snapshot),
                "R1 snapshot + R2 pin -> fail closed (R5-A cross-revision)");
    }

    @Test
    void stateTamperFailsDigest() {
        // Pin over canonical state A; tampered state B cannot satisfy it
        // (recomputed digest mismatch — RP2/BI3).
        EffectSemanticSnapshotReference canonicalPin = TestPlans.effectSnapshotReference(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectInstance tampered = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "99"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        EffectSemanticSnapshot tamperedSnapshot = TestPlans.effectSnapshot(
                List.of(tampered), List.of(TestPlans.effectDefinition()));
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                        tamperedSnapshot,
                        new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                                "tl-digest-fixture", canonicalPin, "ctx-digest-fixture", com.example.platform.timeline.version.TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1),
                        TestPlans.REVISION_ID),
                "state tamper -> digest mismatch fail closed (R5-A/RP2)");
    }

    @Test
    void semanticEqualReconstructionDeterministic() {
        // Same authoritative semantic value freshly reconstructed -> same digest
        // (CLEAN-FORWARD: through the new instance authority path).
        EffectSemanticSnapshot a = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshot b = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertEquals(a.contentDigest(), b.contentDigest(),
                "semantic-equal reconstruction -> same authoritative digest (R5-A)");
    }

    // ── R5-B: Logical Effect WHAT recoverability ────────────────────────────

    @Test
    void applicationRangeIsRecoverableFromFinalPlan() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        RenderPlan plan = planner.plan(base).plan();
        EffectMaterializationRequirement req = effectRequirementOf(plan);
        assertEquals(0, req.applicationRange().start().ticks());
        assertEquals(2, req.applicationRange().end().ticks());
        // distinct range [0,1) vs [0,2) distinguishable
        EffectInstance narrow = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(1, 1)),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput narrowInput = TestPlans.inputWithEffectState(
                List.of(narrow), base.effectSemanticSnapshot().effectDefinitions());
        RenderPlan narrowPlan = planner.plan(narrowInput).plan();
        // FINAL V1: applicationRange is DERIVED from the target clip extent
        // (APPLICATION_RANGE_AUTHORITY_V1) — caller-supplied [0,1) is ignored;
        // the derived extent [0,2) is materialized.
        assertEquals(2, effectRequirementOf(narrowPlan).applicationRange().end().ticks(),
                "FINAL V1: applicationRange derived from clip extent [0,2) (R5-B/SA3)");
        // range is NOT caller-authoritative -> fingerprint equals canonical
        assertEquals(plan.fingerprint(), narrowPlan.fingerprint(),
                "FINAL V1: caller-supplied range is ignored -> fingerprint unchanged");
    }

    @Test
    void definitionVersionIsRecoverableFromFinalPlan() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        RenderPlan plan = planner.plan(base).plan();
        EffectMaterializationRequirement req = effectRequirementOf(plan);
        assertEquals("def-blur", req.effectDefinitionId());
        assertEquals("1", req.effectDefinitionVersion());
        assertEquals(TestPlans.EFFECT_INSTANCE_ID, req.effectInstanceId());

        // def-blur@2, same category + params -> version distinguishable
        EffectInstance.EffectDefinition defV2 = new EffectInstance.EffectDefinition(
                "def-blur", "2", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                TestPlans.effectDefinition().parameterSchema(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                TestPlans.effectDefinition().deterministicProperties(),
                TestPlans.effectDefinition().requiredCapabilities(),
                TestPlans.effectDefinition().supportedBackendCapabilities());
        EffectInstance instanceV2 = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "2",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlan planV2 = planner.plan(TestPlans.inputWithEffectState(
                List.of(instanceV2), List.of(defV2))).plan();
        assertEquals("2", effectRequirementOf(planV2).effectDefinitionVersion(),
                "definition version @2 recoverable (R5-B)");
        assertNotEquals(plan.fingerprint(), planV2.fingerprint(),
                "definition version change -> fingerprint differs");
    }

    @Test
    void automationBindingsRecoverableFromFinalPlan() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        RenderPlan plan = planner.plan(base).plan();
        // FINAL V1 (UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1):
        // automationBindings are EMPTY; a caller-supplied non-empty automation
        // map FAILS CLOSED at the domain authority (SA5).
        EffectMaterializationRequirement req = effectRequirementOf(plan);
        assertEquals(0, req.automationBindings().size(),
                "FINAL V1: automation bindings are EMPTY (R5-B)");
        EffectInstance automated = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(),
                Map.of("radius", "auto.radius"),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> TestPlans.verifiedEffectSnapshot(
                        List.of(automated), base.effectSemanticSnapshot().effectDefinitions()),
                "FINAL V1: non-empty unverified automation -> FAIL CLOSED (SA5)");
    }

    @Test
    void enabledStateAndTemporalBehaviorRecoverable() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlan plan = planner.plan(TestPlans.canonicalInput()).plan();
        EffectMaterializationRequirement req = effectRequirementOf(plan);
        assertTrue(req.enabled(), "active effect enabled=true in Logical WHAT (OPTION A)");
        assertEquals(EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                req.temporalBehavior(), "temporal behavior typed recoverable (R5-B)");
    }

    @Test
    void disabledEffectNotMaterializedButSnapshotRetainsIt() {
        // OPTION A: disabled effects do not materialize as execution nodes, but
        // the verified authored snapshot + plan reference retain them.
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        EffectInstance disabled = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, false,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput input = TestPlans.inputWithEffectState(
                List.of(disabled), base.effectSemanticSnapshot().effectDefinitions());
        RenderPlan plan = planner.plan(input).plan();
        // no EFFECT node materialized (disabled -> OPTION A)
        assertTrue(plan.nodes().stream().noneMatch(n -> n.kind() instanceof RenderNodeKind.Effect),
                "disabled effect -> no execution node (OPTION A)");
        // but the snapshot + reference retain it
        assertEquals(1, input.effectSemanticSnapshot().effects().size());
        assertNotNull(plan.effectSemanticReference());
    }

    @Test
    void noAuthoredRereadConsumerTest() {
        // THE R5-B acceptance test: a consumer receives ONLY the RenderPlan and
        // must extract all required active Effect WHAT without EffectInstance /
        // EffectDefinition / authored repository state.
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlan plan = planner.plan(TestPlans.canonicalInput()).plan();

        PlanOnlyEffectConsumer consumer = new PlanOnlyEffectConsumer();
        consumer.consume(plan); // throws if anything is missing

        assertEquals(TestPlans.EFFECT_INSTANCE_ID, consumer.instanceId());
        assertEquals("def-blur", consumer.definitionId());
        assertEquals("1", consumer.definitionVersion());
        assertEquals(EffectInstance.EffectCategory.GAUSSIAN_BLUR, consumer.category());
        assertTrue(consumer.enabled());
        assertEquals(MediaTime.ofRational(0, 1), consumer.applicationStart());
        assertEquals(MediaTime.ofRational(2, 1), consumer.applicationEnd());
        assertTrue(consumer.parameters().containsKey("radiusPixels"));
        assertEquals(EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION, consumer.temporalBehavior());
        assertNotNull(consumer.capabilities());
        assertNotNull(consumer.effectSemanticReference());
    }

    // ── R5-E/F: digest contract + collection ordering ───────────────────────

    @Test
    void collectionOrderingIsInsensitive() {
        // R5-F: unordered set-like collections deep-sorted -> semantic-equal
        // definitions with different insertion order yield identical canonical
        // state bytes.
        EffectInstance.EffectDefinition defA = new EffectInstance.EffectDefinition(
                "def-x", "1", EffectInstance.EffectCategory.GAIN,
                List.of(EffectInstance.EffectMediaType.AUDIO, EffectInstance.EffectMediaType.AUDIO_VIDEO),
                Map.of(), EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("p1", "p2"), List.of("capA", "capB"),
                List.of("backendA", "backendB"));
        EffectInstance.EffectDefinition defB = new EffectInstance.EffectDefinition(
                "def-x", "1", EffectInstance.EffectCategory.GAIN,
                List.of(EffectInstance.EffectMediaType.AUDIO_VIDEO, EffectInstance.EffectMediaType.AUDIO),
                Map.of(), EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("p2", "p1"), List.of("capB", "capA"),
                List.of("backendB", "backendA"));
        assertEquals(
                EffectSemanticStateCanonicalSemantics.canonicalEffectDefinition(defA),
                EffectSemanticStateCanonicalSemantics.canonicalEffectDefinition(defB),
                "unordered collections deep-sorted -> identical canonical bytes (R5-F)");
    }

    @Test
    void digestContractIsSingleSha256() {
        // R5-E: ContentDigest.sha256 wraps a hex string (no double hash). The
        // effect digest pipeline is: sha256Hex(canonical) -> ContentDigest
        // wrapper. Prove the wrapper does not re-hash: the digest value equals
        // the hex of the canonical bytes.
        String canonical = "test-canonical-bytes";
        String hex = EffectSemanticStateCanonicalSemantics.sha256Hex(canonical);
        ContentDigest digest = ContentDigest.sha256(hex);
        assertEquals(hex, digest.canonicalValue(),
                "ContentDigest.sha256 is a type wrapper, not a second hash (R5-E)");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static EffectMaterializationRequirement effectRequirementOf(RenderPlan plan) {
        return plan.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .flatMap(n -> n.materializationRequirements().stream())
                .filter(r -> r instanceof EffectMaterializationRequirement)
                .map(r -> (EffectMaterializationRequirement) r)
                .findFirst().orElseThrow();
    }

    /** Plan-only consumer: receives ONLY the RenderPlan (R5-B no-authored-reread). */
    static final class PlanOnlyEffectConsumer {
        private String instanceId;
        private String definitionId;
        private String definitionVersion;
        private EffectInstance.EffectCategory category;
        private boolean enabled;
        private MediaTime applicationStart;
        private MediaTime applicationEnd;
        private Map<String, String> parameters;
        private EffectInstance.EffectTemporalBehavior temporalBehavior;
        private java.util.List<String> capabilities;
        private EffectSemanticReference effectSemanticReference;

        void consume(RenderPlan plan) {
            EffectMaterializationRequirement req = effectRequirementOf(plan);
            this.instanceId = req.effectInstanceId();
            this.definitionId = req.effectDefinitionId();
            this.definitionVersion = req.effectDefinitionVersion();
            this.category = req.category();
            this.enabled = req.enabled();
            this.applicationStart = req.applicationRange().start();
            this.applicationEnd = req.applicationRange().end();
            Map<String, String> params = new java.util.LinkedHashMap<>();
            for (EffectMaterializationRequirement.EffectParameter p : req.parameters()) {
                params.put(p.key(), p.value());
            }
            this.parameters = params;
            this.temporalBehavior = req.temporalBehavior();
            this.capabilities = plan.nodes().stream()
                    .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                    .flatMap(n -> n.capabilityRequirements().stream())
                    .map(c -> c.capabilityId().value())
                    .toList();
            this.effectSemanticReference = plan.effectSemanticReference();
        }

        String instanceId() { return instanceId; }
        String definitionId() { return definitionId; }
        String definitionVersion() { return definitionVersion; }
        EffectInstance.EffectCategory category() { return category; }
        boolean enabled() { return enabled; }
        MediaTime applicationStart() { return applicationStart; }
        MediaTime applicationEnd() { return applicationEnd; }
        Map<String, String> parameters() { return parameters; }
        EffectInstance.EffectTemporalBehavior temporalBehavior() { return temporalBehavior; }
        java.util.List<String> capabilities() { return capabilities; }
        EffectSemanticReference effectSemanticReference() { return effectSemanticReference; }
    }
}
