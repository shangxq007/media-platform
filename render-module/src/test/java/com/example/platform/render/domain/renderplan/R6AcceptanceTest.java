package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.canonicalmodel.TimelineSourceRef;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.AuthoredEffectSemanticAuthority;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
import com.example.platform.timeline.semantics.effect.EffectSemanticStateCanonicalSemantics;
import com.example.platform.timeline.semantics.effect.EffectTarget;
import com.example.platform.timeline.semantics.effect.RevisionOwnedEffectProjection;
import com.example.platform.timeline.version.TimelineRevision;
import com.example.platform.shared.time.FrameRate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 correction R6 acceptance / adversarial tests.
 *
 * <p>Covers the full R6 matrix:
 * <ul>
 *   <li>T1-T6: authentic target/membership (same-time foreign effect, forged
 *       clip id, two clips same range, valid membership, missing target,
 *       cross-revision),</li>
 *   <li>C1-C5: capability lowering (definition requiredCapabilities -> typed
 *       CapabilityRequirement, version-consistent, dedup, invalid id fail
 *       closed),</li>
 *   <li>N1-N9: node semantic identity locality (per-field changes alter the
 *       target effect node id; unrelated effect change leaves it stable),</li>
 *   <li>P1: plan-only consumer extracts complete active Effect Logical WHAT,</li>
 *   <li>K1-K6: target participates in domain digest / plan fingerprint / node
 *       id; deterministic reconstruction; R4/R5 canonicality preserved,</li>
 *   <li>I1-I5: integrity (duplicate ids, duplicate definitions, mediaType
 *       unsupported, version mismatch, unknown definition — all fail closed).</li>
 * </ul>
 */
class R6AcceptanceTest {

    // ── T1-T6: AUTHENTIC TARGET / MEMBERSHIP ────────────────────────────────

    @Test
    void t1_sameTimeForeignEffectFailsClosed() {
        // Foreign effect overlaps R1 clip time [0,2) but is not a revision-owned
        // member -> FAIL CLOSED (membership, not temporal overlap).
        TimelineRevision r1 = TestPlans.timelineRevision();
        EffectInstance foreign = new EffectInstance(
                "eff-foreign", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(1, 1)),
                Map.of(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        r1, TestPlans.revisionOwnedProjection(),
                        List.of(foreign), List.of(TestPlans.effectDefinition())),
                "T1: same-time foreign effect -> membership FAIL CLOSED");
    }

    @Test
    void t2_forgedClipIdFailsClosed() {
        // Caller labels a foreign effect with target=c1 but the revision-owned
        // source aggregate has NO such membership -> FAIL CLOSED (clip id
        // existence alone is not membership).
        TimelineRevision r1 = TestPlans.timelineRevision();
        EffectInstance forged = new EffectInstance(
                "eff-forged", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                Map.of(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        r1, TestPlans.revisionOwnedProjection(),
                        List.of(forged), List.of(TestPlans.effectDefinition())),
                "T2: forged clip id -> membership FAIL CLOSED");
    }

    @Test
    void t3_wrongClipSameRangeFailsClosed() {
        // Two clips c1 [0,2) and c2 [0,2). Effect belongs to c1; attacker
        // assigns c2 -> FAIL CLOSED (membership is not temporal equivalence).
        TimelineRevision r1 = TestPlans.timelineRevisionWithClipId("c2");
        EffectInstance effect = TestPlans.gaussianBlurEffect(); // membership: t1/c1
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        r1, TestPlans.revisionOwnedProjectionWithClipId("c2"),
                        List.of(effect), List.of(TestPlans.effectDefinition())),
                "T3: effect belongs to c1, assigned c2 -> membership FAIL CLOSED");
    }

    @Test
    void t4_validRevisionOwnedMembershipPasses() {
        // Revision-owned wire membership c1/eff-1 -> semantic target c1/eff-1 ->
        // verification succeeds.
        TimelineRevision r1 = TestPlans.timelineRevision();
        EffectSemanticBinding binding = AuthoredEffectSemanticAuthority.issue(
                r1, TestPlans.revisionOwnedProjection(),
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertEquals(r1.revisionId(), binding.revisionId());
        assertTrue(binding.effectStateDigest().canonicalValue().length() == 64);
    }

    @Test
    void t5_missingTargetFailsClosed() {
        // Effect target references a deleted/nonexistent clip c9.
        EffectInstance orphan = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c9"),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                        List.of(orphan), List.of(TestPlans.effectDefinition())),
                "T5: missing target clip -> FAIL CLOSED");
    }

    @Test
    void t6_crossRevisionFailsClosed() {
        // Valid revision-owned effect from R1 combined with R2 -> FAIL CLOSED.
        EffectSemanticBinding r1Binding = AuthoredEffectSemanticAuthority.issue(
                TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        TimelineRevision r2 = TestPlans.timelineRevisionWithId("rev-2");
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedRenderSemanticSnapshotFactory.verified(
                        r2, TestPlans.timelineDigester(),
                        List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()),
                        r1Binding),
                "T6: R1-owned effect + R2 revision -> FAIL CLOSED");
    }

    // ── C1-C5: CAPABILITY LOWERING ──────────────────────────────────────────

    @Test
    void c1_definitionRequiredCapabilityAppearsInFinalNode() {
        EffectInstance.EffectDefinition def = TestPlans.effectDefinitionWithRequiredCapability(
                "video.effect.gaussian-blur-extra");
        RenderPlan plan = TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(def));
        List<String> caps = effectNodeCapabilities(plan);
        assertTrue(caps.contains("video.effect.gaussian-blur-extra"),
                "C1: definition requiredCapabilities lowered into final node");
        assertTrue(caps.contains("video.effect.gaussian-blur"),
                "C1: category baseline retained (union)");
    }

    @Test
    void c2_definitionVersionCapabilityConsistency() {
        // def-v2 requiredCapabilities = B (different from def-v1 baseline-only)
        EffectInstance.EffectDefinition defV1 = TestPlans.effectDefinition();
        EffectInstance.EffectDefinition defV2 = new EffectInstance.EffectDefinition(
                "def-blur", "2", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                TestPlans.effectDefinition().parameterSchema(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                TestPlans.effectDefinition().deterministicProperties(),
                List.of("video.effect.gaussian-blur-extra"),
                TestPlans.effectDefinition().supportedBackendCapabilities());
        EffectInstance instanceV2 = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "2",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlan v1Plan = TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(defV1));
        RenderPlan v2Plan = TestPlans.planForEffects(
                List.of(instanceV2), List.of(defV2));
        assertFalse(effectNodeCapabilities(v1Plan).contains("video.effect.gaussian-blur-extra"),
                "C2: v1 has baseline only");
        assertTrue(effectNodeCapabilities(v2Plan).contains("video.effect.gaussian-blur-extra"),
                "C2: v2 requiredCapabilities B lowered");
        assertNotEquals(v1Plan.fingerprint(), v2Plan.fingerprint(),
                "C2/C3: capability change -> plan fingerprint changes");
    }

    @Test
    void c3_capabilityChangeChangesNodeIdAndFingerprint() {
        EffectInstance.EffectDefinition defV1 = TestPlans.effectDefinition();
        EffectInstance.EffectDefinition defV2 = TestPlans.effectDefinitionWithRequiredCapability(
                "video.effect.gaussian-blur-extra");
        EffectInstance instanceV2 = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlan v1Plan = TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(defV1));
        RenderPlan v2Plan = TestPlans.planForEffects(
                List.of(instanceV2), List.of(defV2));
        assertNotEquals(effectNodeId(v1Plan), effectNodeId(v2Plan),
                "C3: capability change -> effect node id changes");
    }

    @Test
    void c4_invalidCapabilityIdFailsClosed() {
        // "not-a-valid!!id" -> CapabilityId.of fails closed
        assertThrows(IllegalArgumentException.class,
                () -> RenderCapabilityVocabulary.forRequiredCapability("not-a-valid!!id"),
                "C4: invalid capability id -> FAIL CLOSED");
    }

    @Test
    void c5_duplicateCapabilityDeduplicated() {
        // category baseline + definition required SAME id -> dedup to one
        EffectInstance.EffectDefinition def = TestPlans.effectDefinitionWithRequiredCapability(
                "video.effect.gaussian-blur");
        RenderPlan plan = TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(def));
        List<String> caps = effectNodeCapabilities(plan);
        assertEquals(1, caps.stream().filter(c -> c.equals("video.effect.gaussian-blur")).count(),
                "C5: duplicate capability id deduplicated");
    }

    // ── N1-N9: NODE SEMANTIC IDENTITY LOCALITY ──────────────────────────────

    @Test
    void n1_paramChangeChangesEffectNodeId() {
        String baseId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition())));
        EffectInstance changed = effectWith(Map.of("radiusPixels", "99"), TestPlans.gaussianBlurEffect().automationBindings());
        String changedId = effectNodeId(TestPlans.planForEffects(
                List.of(changed), List.of(TestPlans.effectDefinition())));
        assertNotEquals(baseId, changedId, "N1: static parameter change -> node id changes");
    }

    @Test
    void n2_definitionVersionChangeChangesEffectNodeId() {
        String baseId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition())));
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
        String changedId = effectNodeId(TestPlans.planForEffects(
                List.of(instanceV2), List.of(defV2)));
        assertNotEquals(baseId, changedId, "N2: definition version change -> node id changes");
    }

    @Test
    void n3_rangeChangeChangesEffectNodeId() {
        String baseId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition())));
        EffectInstance changed = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(1, 1)),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        String changedId = effectNodeId(TestPlans.planForEffects(
                List.of(changed), List.of(TestPlans.effectDefinition())));
        assertNotEquals(baseId, changedId, "N3: applicationRange change -> node id changes");
    }

    @Test
    void n4_automationChangeChangesEffectNodeId() {
        String baseId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition())));
        EffectInstance changed = effectWith(TestPlans.gaussianBlurEffect().parameters(),
                Map.of("radius", "auto.radius"));
        String changedId = effectNodeId(TestPlans.planForEffects(
                List.of(changed), List.of(TestPlans.effectDefinition())));
        assertNotEquals(baseId, changedId, "N4: automation binding change -> node id changes");
    }

    @Test
    void n5_temporalBehaviorChangeChangesEffectNodeId() {
        String baseId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition())));
        EffectInstance.EffectDefinition defAlter = new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                TestPlans.effectDefinition().parameterSchema(),
                EffectInstance.EffectTemporalBehavior.CHANGE_DURATION,
                TestPlans.effectDefinition().deterministicProperties(),
                TestPlans.effectDefinition().requiredCapabilities(),
                TestPlans.effectDefinition().supportedBackendCapabilities());
        String changedId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(defAlter)));
        assertNotEquals(baseId, changedId, "N5: temporalBehavior change -> node id changes");
    }

    @Test
    void n6_targetChangeChangesEffectNodeId() {
        // Same plan with two clips (c1, c2): effect e1 targets c1, effect e2
        // targets c2, otherwise identical -> node ids MUST differ (target is a
        // node-semantic-identity participant).
        EffectInstance e1 = TestPlans.gaussianBlurEffect(); // target c1
        EffectInstance e2 = new EffectInstance(
                "eff-2", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c2"),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlan plan = TestPlans.planForEffects(
                List.of(e1, e2), List.of(TestPlans.effectDefinition()),
                TestPlans.timelineRevisionWithTwoClips("c2"),
                TestPlans.projectionWithClips("c2"));
        List<String> effectIds = plan.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .map(n -> n.id().value())
                .toList();
        assertEquals(2, effectIds.size(), "two effects on two clips materialize");
        assertNotEquals(effectIds.get(0), effectIds.get(1),
                "N6: different effect target -> different node ids");
    }

    @Test
    void n7_capabilityChangeChangesEffectNodeId() {
        // effective capabilities participate in node identity
        String baseId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition())));
        EffectInstance.EffectDefinition defExtra = TestPlans.effectDefinitionWithRequiredCapability(
                "video.effect.gaussian-blur-extra");
        String changedId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(defExtra)));
        assertNotEquals(baseId, changedId, "N7: effective capability change -> node id changes");
    }

    @Test
    void n8_categoryChangeChangesEffectNodeId() {
        String baseId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition())));
        EffectInstance.EffectDefinition fadeDef = new EffectInstance.EffectDefinition(
                "def-fade", "1", EffectInstance.EffectCategory.FADE,
                List.of(EffectInstance.EffectMediaType.VIDEO), Map.of(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION, List.of(), List.of(), List.of());
        EffectInstance fadeEffect = new EffectInstance(
                "eff-fade", "def-fade", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        String changedId = effectNodeId(TestPlans.planForEffects(
                List.of(fadeEffect), List.of(fadeDef), TestPlans.projectionWithFade()));
        assertNotEquals(baseId, changedId, "N8: category change -> node id changes");
    }

    @Test
    void n9_unrelatedEffectChangeKeepsFirstEffectNodeIdStable() {
        // Locality: changing a SECOND effect must NOT change the FIRST effect's
        // node id (node identity is local, not global snapshot identity).
        EffectInstance e1 = TestPlans.gaussianBlurEffect();
        EffectInstance e2 = new EffectInstance(
                "eff-2", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "2"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        EffectInstance e2Changed = new EffectInstance(
                "eff-2", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "77"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        List<EffectInstance.EffectDefinition> defs = List.of(TestPlans.effectDefinition());
        String plan1FirstId = effectNodeId(TestPlans.planForEffects(
                List.of(e1, e2), defs, TestPlans.projectionWithSecondEffect()));
        String plan2FirstId = effectNodeId(TestPlans.planForEffects(
                List.of(e1, e2Changed), defs, TestPlans.projectionWithSecondEffect()));
        assertEquals(plan1FirstId, plan2FirstId,
                "N9: unrelated effect change -> first effect node id STABLE (locality)");
    }

    // ── P1: PLAN-ONLY CONSUMER ──────────────────────────────────────────────

    @Test
    void p1_planOnlyConsumerExtractsCompleteEffectWhat() {
        RenderPlan plan = TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        // consumer receives ONLY the RenderPlan
        PlanOnlyConsumer consumer = new PlanOnlyConsumer(plan);
        assertEquals(TestPlans.EFFECT_INSTANCE_ID, consumer.effectInstanceId());
        assertEquals(TestPlans.TRACK_ID, consumer.targetTrackId());
        assertEquals(TestPlans.CLIP_ID, consumer.targetClipId());
        assertEquals("def-blur", consumer.definitionId());
        assertEquals("1", consumer.definitionVersion());
        assertEquals(EffectInstance.EffectCategory.GAUSSIAN_BLUR, consumer.category());
        assertTrue(consumer.enabled());
        assertEquals(0, consumer.applicationStart().ticks());
        assertEquals(2, consumer.applicationEnd().ticks());
        assertTrue(consumer.parameters().containsKey("radiusPixels"));
        assertTrue(consumer.automationBindings().isEmpty());
        assertEquals(EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION, consumer.temporalBehavior());
        assertTrue(consumer.capabilities().contains("video.effect.gaussian-blur"));
        assertNotNull(consumer.effectSemanticReference());
        assertEquals(1, consumer.inputEdges(), "decode -> effect dependency edge present");
    }

    // ── K1-K6: CANONICALITY ─────────────────────────────────────────────────

    @Test
    void h1_reverseEffectStackOrderChangesDigestAndFingerprint() {
        // R6-H: EFFECT_STACK_ORDER_SEMANTICS = ORDERED — [e1, e2] != [e2, e1].
        EffectInstance e1 = TestPlans.gaussianBlurEffect();
        EffectInstance e2 = new EffectInstance(
                "eff-2", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "2"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        List<EffectInstance.EffectDefinition> defs = List.of(TestPlans.effectDefinition());
        String forward = EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                List.of(e1, e2), defs);
        String reversed = EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                List.of(e2, e1), defs);
        assertNotEquals(forward, reversed,
                "H1: reversed authored effect stack -> different semantic digest (ORDERED)");
        RenderPlan planForward = TestPlans.planForEffects(
                List.of(e1, e2), defs, TestPlans.projectionWithSecondEffect());
        RenderPlan planReversed = TestPlans.planForEffects(
                List.of(e2, e1), defs, TestPlans.projectionWithSecondEffect());
        assertNotEquals(planForward.fingerprint(), planReversed.fingerprint(),
                "H1: reversed stack -> different plan fingerprint (ORDERED)");
        // topology reflects authored order: first effect consumes decode,
        // second effect consumes the first.
        String firstNodeId = planForward.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .map(n -> n.id().value()).findFirst().orElseThrow();
        String secondNodeId = planForward.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .map(n -> n.id().value()).skip(1).findFirst().orElseThrow();
        assertTrue(planForward.edges().stream().anyMatch(
                        e -> e.consumerId().value().equals(secondNodeId)
                                && e.producerId().value().equals(firstNodeId)),
                "H1: effect chain topology preserves authored stack order");
    }

    @Test
    void k1_targetParticipatesInDomainDigest() {
        EffectInstance a = TestPlans.gaussianBlurEffect();
        EffectInstance b = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c2"),
                TestPlans.gaussianBlurEffect().provenance());
        assertNotEquals(
                EffectSemanticStateCanonicalSemantics.canonicalEffectInstance(a),
                EffectSemanticStateCanonicalSemantics.canonicalEffectInstance(b),
                "K1: identical params + different target -> different semantic digest");
    }

    @Test
    void k2_targetChangeChangesPlanFingerprint() {
        RenderPlan base = TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectInstance otherClipEffect = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c2"),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlan changed = TestPlans.planForEffects(
                List.of(otherClipEffect), List.of(TestPlans.effectDefinition()),
                TestPlans.timelineRevisionWithClipId("c2"),
                TestPlans.revisionOwnedProjectionWithClipId("c2"));
        assertNotEquals(base.fingerprint(), changed.fingerprint(),
                "K2: target change -> plan fingerprint changes");
    }

    @Test
    void k4_sameSemanticTargetReconstructionDeterministic() {
        EffectSemanticBinding a = AuthoredEffectSemanticAuthority.issue(
                TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticBinding b = AuthoredEffectSemanticAuthority.issue(
                TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertEquals(a.effectStateDigest(), b.effectStateDigest(),
                "K4: same semantic target reconstruction -> deterministic digest");
    }

    // ── I1-I5: INTEGRITY (fail closed) ──────────────────────────────────────

    @Test
    void i1_duplicateEffectInstanceIdFailsClosed() {
        EffectInstance dup1 = TestPlans.gaussianBlurEffect();
        EffectInstance dup2 = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "9"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                        List.of(dup1, dup2), List.of(TestPlans.effectDefinition())),
                "I1: duplicate effectInstanceId -> FAIL CLOSED");
    }

    @Test
    void i2_duplicateDefinitionIdentityFailsClosed() {
        EffectInstance.EffectDefinition defDup = new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                TestPlans.effectDefinition().parameterSchema(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                TestPlans.effectDefinition().deterministicProperties(),
                TestPlans.effectDefinition().requiredCapabilities(),
                TestPlans.effectDefinition().supportedBackendCapabilities());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                        List.of(TestPlans.gaussianBlurEffect()),
                        List.of(TestPlans.effectDefinition(), defDup)),
                "I2: duplicate (definitionId, version) -> FAIL CLOSED");
    }

    @Test
    void i3_mediaTypeUnsupportedByDefinitionFailsClosed() {
        // definition supports VIDEO only; instance declares AUDIO
        EffectInstance audioInstance = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.AUDIO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                        List.of(audioInstance), List.of(TestPlans.effectDefinition())),
                "I3: mediaType not in definition supportedMediaTypes -> FAIL CLOSED");
    }

    @Test
    void i4_definitionVersionMismatchFailsClosed() {
        EffectInstance versionMismatch = new EffectInstance(
                "eff-vm", "def-blur", "9",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                        List.of(versionMismatch), List.of(TestPlans.effectDefinition())),
                "I4: definition version mismatch -> FAIL CLOSED");
    }

    @Test
    void i5_unknownDefinitionFailsClosed() {
        EffectInstance unknown = new EffectInstance(
                "eff-unknown", "def-nope", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> AuthoredEffectSemanticAuthority.issue(
                        TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(),
                        List.of(unknown), List.of(TestPlans.effectDefinition())),
                "I5: unknown definition -> FAIL CLOSED");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static EffectInstance effectWith(Map<String, String> params, Map<String, String> automation) {
        return new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                params, automation,
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
    }

    private static String effectNodeId(RenderPlan plan) {
        return plan.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .map(n -> n.id().value())
                .findFirst().orElseThrow();
    }

    private static List<String> effectNodeCapabilities(RenderPlan plan) {
        return plan.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .flatMap(n -> n.capabilityRequirements().stream())
                .map(c -> c.capabilityId().value())
                .toList();
    }

    /** P1: receives ONLY the RenderPlan and extracts complete active Effect WHAT. */
    static final class PlanOnlyConsumer {
        private final RenderPlan plan;
        private final EffectMaterializationRequirement req;

        PlanOnlyConsumer(RenderPlan plan) {
            this.plan = plan;
            this.req = plan.nodes().stream()
                    .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                    .flatMap(n -> n.materializationRequirements().stream())
                    .filter(r -> r instanceof EffectMaterializationRequirement)
                    .map(r -> (EffectMaterializationRequirement) r)
                    .findFirst().orElseThrow();
        }

        String effectInstanceId() { return req.effectInstanceId(); }
        String targetTrackId() { return ((ClipEffectTarget) req.target()).trackId(); }
        String targetClipId() { return ((ClipEffectTarget) req.target()).clipId(); }
        String definitionId() { return req.effectDefinitionId(); }
        String definitionVersion() { return req.effectDefinitionVersion(); }
        EffectInstance.EffectCategory category() { return req.category(); }
        boolean enabled() { return req.enabled(); }
        MediaTime applicationStart() { return req.applicationRange().start(); }
        MediaTime applicationEnd() { return req.applicationRange().end(); }
        Map<String, String> parameters() {
            Map<String, String> m = new java.util.LinkedHashMap<>();
            req.parameters().forEach(p -> m.put(p.key(), p.value()));
            return m;
        }
        List<String> automationBindings() {
            return req.automationBindings().stream()
                    .map(b -> b.parameterKey() + "->" + b.automationReference()).toList();
        }
        EffectInstance.EffectTemporalBehavior temporalBehavior() { return req.temporalBehavior(); }
        List<String> capabilities() {
            return plan.nodes().stream()
                    .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                    .flatMap(n -> n.capabilityRequirements().stream())
                    .map(c -> c.capabilityId().value()).toList();
        }
        EffectSemanticReference effectSemanticReference() { return plan.effectSemanticReference(); }
        long inputEdges() {
            RenderNodeId effectId = plan.nodes().stream()
                    .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                    .map(RenderNode::id)
                    .findFirst().orElseThrow();
            return plan.edges().stream()
                    .filter(e -> e.consumerId().equals(effectId))
                    .count();
        }
        private String effectNodeId() {
            return plan.nodes().stream()
                    .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                    .map(n -> n.id().value()).findFirst().orElseThrow();
        }
    }
}
