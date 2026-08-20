package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.AuthoredEffectSemanticAuthority;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
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

    // ── R5-A: authority-issued binding / relabel attack ─────────────────────

    @Test
    void attackerCannotMintBindingThroughPublicApi() {
        // The public issuance path takes a TimelineRevision OBJECT (identity
        // extracted by the authority), never a caller-supplied revision label.
        // The binding constructor is private; there is no
        // EffectSemanticBinding.of(revisionId, effects, defs) anywhere.
        // Structural proof: no public "of" factory exists; constructor is
        // private (reflection accessibility check).
        assertEquals(0, java.util.Arrays.stream(EffectSemanticBinding.class.getDeclaredMethods())
                        .filter(m -> m.getName().equals("of")).count(),
                "no public of(...) factory on EffectSemanticBinding (R5-A)");
        assertEquals(0, java.util.Arrays.stream(EffectSemanticBinding.class.getConstructors()).count(),
                "no public constructor on EffectSemanticBinding (R5-A)");
        // the only public factory is the authority
        assertTrue(java.util.Arrays.stream(AuthoredEffectSemanticAuthority.class.getDeclaredMethods())
                        .anyMatch(m -> m.getName().equals("issue") && m.getParameterCount() == 3),
                "single public issuance path: AuthoredEffectSemanticAuthority.issue (R5-A)");
    }

    @Test
    void relabelAttackFailsClosedOwnershipCheck() {
        // Attacker has timeline revision R1 (clip range [0,2]) and an
        // INDEPENDENT effect state R2 whose application range does not overlap
        // R1's clips (e.g. [5,6) — outside the revision). Relabeling R2 as R1
        // must FAIL CLOSED at the authority issuance boundary (ownership check
        // over the revision's actual clips).
        TimelineRevision r1 = TestPlans.timelineRevision(); // clip [0,2]
        EffectInstance foreign = new EffectInstance(
                "eff-foreign", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(5, 1), MediaTime.ofRational(6, 1)),
                Map.of("radiusPixels", "4"), Map.of(),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        r1, List.of(foreign), List.of(TestPlans.effectDefinition())),
                "R2 effect state with ranges outside R1 clips -> ownership FAIL CLOSED (R5-A)");
    }

    @Test
    void relabelAttackWithOverlappingRangeStillBoundToRevisionObject() {
        // Even with an overlapping range, the issued binding's revision identity
        // comes FROM the TimelineRevision object — the caller cannot label the
        // binding with an arbitrary revision id (no string parameter exists).
        // Combine revision R1 identity with an effect whose range overlaps R1's
        // clip: binding revisionId MUST equal r1.revisionId() (authority
        // extracted), and the caller cannot choose "other-rev".
        TimelineRevision r1 = TestPlans.timelineRevision();
        EffectSemanticBinding binding = AuthoredEffectSemanticAuthority.issue(
                r1, List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertEquals(r1.revisionId(), binding.revisionId(),
                "binding revision identity comes from the authoritative object (R5-A)");
        assertEquals(EffectSemanticBinding.CONTRACT_VERSION, binding.semanticContractVersion());
        assertNotNull(binding.effectStateDigest());
    }

    @Test
    void crossRevisionCombinationFailsClosed() {
        // Valid authority-issued snapshot for R1 combined with timeline revision
        // R2 -> fail closed at the render factory (revision mismatch).
        EffectSemanticBinding r1Binding = AuthoredEffectSemanticAuthority.issue(
                TestPlans.timelineRevision(),
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        TimelineRevision r2 = TestPlans.timelineRevisionWithId("rev-2");
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedRenderSemanticSnapshotFactory.verified(
                        r2, TestPlans.timelineDigester(),
                        List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()),
                        r1Binding),
                "R1 binding + R2 timeline -> fail closed (R5-A cross-revision)");
    }

    @Test
    void stateTamperFailsDigest() {
        // Authority-issued binding for effect state A; verification with
        // tampered state B must fail the digest check.
        EffectSemanticBinding binding = AuthoredEffectSemanticAuthority.issue(
                TestPlans.timelineRevision(),
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectInstance tampered = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "99"), Map.of(),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                        List.of(tampered), List.of(TestPlans.effectDefinition()), binding),
                "state tamper -> digest mismatch fail closed (R5-A)");
    }

    @Test
    void semanticEqualReconstructionDeterministic() {
        // Same authoritative semantic value freshly reconstructed -> same digest.
        TimelineRevision r1 = TestPlans.timelineRevision();
        EffectSemanticBinding a = AuthoredEffectSemanticAuthority.issue(
                r1, List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticBinding b = AuthoredEffectSemanticAuthority.issue(
                TestPlans.timelineRevision(), List.of(TestPlans.gaussianBlurEffect()),
                List.of(TestPlans.effectDefinition()));
        assertEquals(a.effectStateDigest(), b.effectStateDigest(),
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
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput narrowInput = TestPlans.inputWithEffectState(
                List.of(narrow), base.effectSemanticSnapshot().effectDefinitions());
        RenderPlan narrowPlan = planner.plan(narrowInput).plan();
        assertEquals(1, effectRequirementOf(narrowPlan).applicationRange().end().ticks(),
                "exact application range [0,1) recoverable from final plan (R5-B)");
        assertNotEquals(plan.fingerprint(), narrowPlan.fingerprint(),
                "range change -> fingerprint differs");
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
        // base has no automation; add one
        EffectInstance automated = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(),
                Map.of("radius", "auto.radius"),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlan automatedPlan = planner.plan(TestPlans.inputWithEffectState(
                List.of(automated), base.effectSemanticSnapshot().effectDefinitions())).plan();
        EffectMaterializationRequirement req = effectRequirementOf(automatedPlan);
        assertEquals(1, req.automationBindings().size());
        assertEquals("radius", req.automationBindings().get(0).parameterKey());
        assertEquals("auto.radius", req.automationBindings().get(0).automationReference(),
                "automation reference recoverable, not hash-only (R5-B)");
        assertNotEquals(plan.fingerprint(), automatedPlan.fingerprint());
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
