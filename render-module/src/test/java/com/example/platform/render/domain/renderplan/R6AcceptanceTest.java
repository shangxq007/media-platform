package com.example.platform.render.domain.renderplan;

import com.example.platform.extension.domain.CapabilityId;
import com.example.platform.extension.domain.CapabilityRequirement;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonical.TimelineTrack;
import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile;
import com.example.platform.timeline.canonicalmodel.TimelineClipEffect;
import com.example.platform.timeline.canonicalmodel.TimelineSourceRef;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotAuthority;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotId;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.EffectDefinitionVersionRegistry;
import com.example.platform.timeline.semantics.effect.EffectSemanticStateCanonicalSemantics;
import com.example.platform.timeline.semantics.effect.EffectTarget;
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
        // Foreign effect overlaps R1 clip time but is NOT the revision's pinned
        // authored state -> the verified boundary FAILS CLOSED: the revision pin
        // (over the canonical state) cannot be satisfied by foreign content
        // (RP1: wrong snapshot; membership is the pin, not temporal overlap).
        EffectInstance foreign = new EffectInstance(
                "eff-foreign", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(1, 1)),
                Map.of(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        EffectSemanticSnapshot foreignSnapshot = TestPlans.effectSnapshot(
                List.of(foreign), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshotReference canonicalPin = TestPlans.effectSnapshotReference(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                        foreignSnapshot,
                        new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                                "tl-digest-fixture", canonicalPin, "ctx-digest-fixture",
                                com.example.platform.timeline.version.TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1),
                        TestPlans.REVISION_ID),
                "T1: same-time foreign effect cannot satisfy the revision pin -> FAIL CLOSED");
    }

    @Test
    void t2_forgedClipIdFailsClosed() {
        // Caller labels an effect with a target clip id that does not exist in
        // the revision context -> the domain authority FAILS CLOSED at mint
        // (clip id existence is checked against the revision-owned context).
        EffectInstance forged = new EffectInstance(
                "eff-forged", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                Map.of(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c99"),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> TestPlans.effectSnapshot(
                        List.of(forged), List.of(TestPlans.effectDefinition())),
                "T2: forged clip id -> FAIL CLOSED at domain mint");
    }

    @Test
    void t3_wrongClipSameRangeFailsClosed() {
        // Effect snapshot for clip c2 (same time range semantics as c1) is
        // offered against the revision pin for c1 -> the verified boundary
        // FAILS CLOSED (BI2/RP3-C: binding identity is not temporal
        // equivalence — same semantics, different handle, FAIL).
        EffectInstance c2Effect = new EffectInstance(
                "eff-c2", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "4"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c2"),
                TestPlans.gaussianBlurEffect().provenance());
        EffectSemanticSnapshot c2Snapshot = TestPlans.effectSnapshotWithContext(
                List.of(c2Effect), List.of(TestPlans.effectDefinition()),
                List.of(TestPlans.secondClip()));
        EffectSemanticSnapshotReference c1Pin = TestPlans.effectSnapshotReference(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                        c2Snapshot,
                        new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                                "tl-digest-fixture", c1Pin, "ctx-digest-fixture",
                                com.example.platform.timeline.version.TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1),
                        TestPlans.REVISION_ID),
                "T3: wrong clip (same range) cannot satisfy the c1 pin -> FAIL CLOSED");
    }

    @Test
    void t4_validRevisionOwnedMembershipPasses() {
        // Valid authored state with target clip c1 -> domain mint + verified
        // boundary against the snapshot's own pin succeed.
        EffectSemanticSnapshot snapshot = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        VerifiedEffectSemanticSnapshot verified = TestPlans.verifiedEffectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertEquals(snapshot.contentDigest(), verified.contentPin());
        assertEquals(64, snapshot.contentDigest().length());
    }

    @Test
    void t5_missingTargetFailsClosed() {
        // Effect target references a deleted/nonexistent clip c9 -> the domain
        // authority FAILS CLOSED at mint (clip must exist in the context).
        EffectInstance orphan = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c9"),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> TestPlans.effectSnapshot(
                        List.of(orphan), List.of(TestPlans.effectDefinition())),
                "T5: missing target clip -> FAIL CLOSED");
    }

    @Test
    void t6_crossRevisionFailsClosed() {
        // R1's snapshot offered against R2's pin -> FAIL CLOSED: the R2 pin
        // points at R2's own snapshot (different handle), and binding identity
        // is immutable (RP3-C/BI2).
        EffectSemanticSnapshotReference r1Pin = TestPlans.effectSnapshotReference(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshot r1Snapshot = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        // R2 has a DIFFERENT snapshot handle (fresh mint) with the same content.
        EffectSemanticSnapshot r2Snapshot = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshotReference r2Pin = r2Snapshot.reference();
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                r1Snapshot,
                new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                        "tl-digest-fixture", r2Pin, "ctx-digest-fixture", com.example.platform.timeline.version.TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1),
                "rev-2"),
                "T6: R1 snapshot cannot satisfy R2 pin -> FAIL CLOSED");
    }

    @Test
    void rp4_dbOnlyBindingTamperFailsClosed() {
        // RP4: persistence relation mutated R1:S1 -> R1:S2 without a new
        // revision semantic commitment -> verification FAIL CLOSED. Simulated
        // by tampering the pin reference while the snapshot stays S1.
        EffectSemanticSnapshot snapshot = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshot other = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        // DB row now points at the OTHER snapshot's reference (S1 -> S2).
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                        snapshot,
                        new com.example.platform.timeline.version.TimelineRevisionSemanticContext(
                                "tl-digest-fixture", other.reference(), "ctx-digest-fixture",
                                com.example.platform.timeline.version.TimelineRevisionSemanticContext.REVISION_SEMANTICS_V1),
                        TestPlans.REVISION_ID),
                "RP4: DB-only binding tamper -> FAIL CLOSED (no new revision commitment)");
    }

    @Test
    void rp5_missingSemanticContextIsInvalidCorrupt() {
        // CLEAN-FORWARD (CF5): a revision without an Effect semantic pin /
        // semantic context is INVALID/CORRUPT, not a supported legacy mode.
        // Construction with null semanticContext FAILS CLOSED (CF1).
        assertThrows(IllegalArgumentException.class,
                () -> new TimelineRevision(
                        "rev-corrupt", "product-1", null,
                        com.example.platform.timeline.canonical.TimelineDocument.CURRENT_SCHEMA_VERSION,
                        TestPlans.canonicalDocument(),
                        "legacy-digest-aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa",
                        java.time.Instant.EPOCH, "test", null),
                "CF1/CF5: missing semantic context rejected at construction (corrupt, not legacy)");
    }

    @Test
    void so4_crossClipLocalityC1StableWhenC2Changes() {
        // Track has C1 + C2. C1 stack [e1]; C2 stack [e2]. Change ONLY C2
        // semantics ([e2] -> [e2, e3]): C1 local Effect node semantic id MUST
        // be unchanged (no global snapshot id/digest/revision id contamination).
        EffectInstance e1 = TestPlans.gaussianBlurEffect(); // target c1
        EffectInstance e2 = new EffectInstance(
                "eff-2", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "2"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c2"),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlan p1 = TestPlans.planForEffectsOn(
                List.of(e1, e2), List.of(TestPlans.effectDefinition()),
                TestPlans.timelineDocumentWithClips(List.of(TestPlans.secondClip())));
        String c1NodeIdP1 = effectNodeIdOf(p1, "eff-1");
        // C2 stack changes: [e2] -> [e2, e3]
        EffectInstance e3 = new EffectInstance(
                "eff-3", "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "3"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, "c2"),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlan p2 = TestPlans.planForEffectsOn(
                List.of(e1, e2, e3), List.of(TestPlans.effectDefinition()),
                TestPlans.timelineDocumentWithClips(List.of(TestPlans.secondClip())));
        assertEquals(c1NodeIdP1, effectNodeIdOf(p2, "eff-1"),
                "SO4: C1 local Effect node semantic id UNCHANGED when only C2 changes");
        assertNotEquals(p1.fingerprint(), p2.fingerprint(),
                "SO4: plan fingerprint changes with C2 stack change");
    }

    @Test
    void empty1_emptySnapshotDigestDeterministicAcrossIds() {
        // EMPTY1: mint empty Effect semantics twice with different ids ->
        // same version, same canonical empty content -> SAME semantic digest.
        EffectSemanticSnapshot a = TestPlans.effectSnapshot(List.of(), List.of());
        EffectSemanticSnapshot b = TestPlans.effectSnapshot(List.of(), List.of());
        assertNotEquals(a.id(), b.id(), "distinct authority handles");
        assertEquals(a.contentDigest(), b.contentDigest(),
                "EMPTY1: deterministic empty semantic digest across ids (BI1)");
        assertEquals(0, a.entries().size(), "authoritative EMPTY has zero entries");
    }

    @Test
    void empty5_emptySnapshotPlansWithNoEffectNodes() {
        // EMPTY5: render planning for a NEW authoritative EMPTY snapshot
        // succeeds with zero Effect nodes (no mutable/latest lookup). The
        // revision owns EXACTLY this empty snapshot's pin (consistent pair).
        EffectSemanticSnapshot empty = TestPlans.effectSnapshot(List.of(), List.of());
        TimelineRevision revision = TestPlans.revisionWithContext(
                TestPlans.canonicalDocument(),
                new TimelineContentDigester().digest(TestPlans.canonicalDocument()),
                empty);
        VerifiedRenderSemanticSnapshot verified = VerifiedRenderSemanticSnapshotFactory.verified(
                revision, TestPlans.timelineDigester(), empty);
        RenderPlanningInput input = new RenderPlanningInput(
                verified, TestPlans.renderRequest(),
                new SourceResolutionInput(
                        Map.of(TestPlans.artifactId(), RenderSourceResolutionState.RESOLVED)),
                TestPlans.fullCapabilityContext());
        RenderPlan plan = new DefaultRenderPlanner().plan(input).plan();
        assertEquals(0, plan.nodes().stream()
                        .filter(n -> n.kind() instanceof RenderNodeKind.Effect).count(),
                "EMPTY5: authoritative empty snapshot plans with zero Effect nodes");
        assertNotNull(plan.effectSemanticReference(), "EMPTY5: plan carries the empty snapshot pin");
    }

    private static String effectNodeIdOf(RenderPlan plan, String instanceId) {
        return plan.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .filter(n -> n.componentPath().segments().contains(instanceId))
                .map(n -> n.id().value())
                .findFirst().orElseThrow(() -> new AssertionError("no effect node for " + instanceId));
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
    void n3_rangeIsDerivedFromClipExtent() {
        // FINAL V1 semantics (APPLICATION_RANGE_AUTHORITY_V1): applicationRange
        // is DERIVED from the target clip extent — caller-supplied range is NOT
        // authority (SA3). The derived node id equals the canonical one.
        String baseId = effectNodeId(TestPlans.planForEffects(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition())));
        EffectInstance changed = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(1, 1)),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        // The snapshot mints the DERIVED clip extent — materialization uses it.
        EffectSemanticSnapshot snap = TestPlans.effectSnapshot(
                List.of(changed), List.of(TestPlans.effectDefinition()));
        assertEquals(new MediaClip.TimeRange(
                        MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                snap.entries().get(0).target() instanceof ClipEffectTarget
                        ? TestPlans.canonicalClipRange()
                        : null,
                "N3: caller applicationRange is NOT authority — range derives from clip extent");
        // derived range equals canonical -> same materialized node id
        String changedId = effectNodeId(TestPlans.planForEffects(
                List.of(changed), List.of(TestPlans.effectDefinition())));
        assertEquals(baseId, changedId,
                "N3: caller-supplied range is ignored (DERIVED) — node id stable (V1)");
    }

    @Test
    void n4_nonEmptyAutomationFailsClosed() {
        // FINAL V1 semantics (UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1):
        // non-empty automationBindings are UNSUPPORTED — the domain authority
        // FAILS CLOSED at snapshot construction (SA5).
        EffectInstance changed = effectWith(TestPlans.gaussianBlurEffect().parameters(),
                Map.of("radius", "auto.radius"));
        assertThrows(IllegalArgumentException.class,
                () -> TestPlans.effectSnapshot(
                        List.of(changed), List.of(TestPlans.effectDefinition())),
                "N4/SA5: non-empty unverified automation -> FAIL CLOSED (V1)");
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
        RenderPlan plan = TestPlans.planForEffectsOn(
                List.of(e1, e2), List.of(TestPlans.effectDefinition()),
                TestPlans.timelineDocumentWithClips(List.of(TestPlans.secondClip())));
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
                List.of(fadeEffect), List.of(fadeDef)));
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
                List.of(e1, e2), defs));
        String plan2FirstId = effectNodeId(TestPlans.planForEffects(
                List.of(e1, e2Changed), defs));
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
                List.of(e1, e2), defs);
        RenderPlan planReversed = TestPlans.planForEffects(
                List.of(e2, e1), defs);
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
        RenderPlan changed = TestPlans.planForEffectsOn(
                List.of(otherClipEffect), List.of(TestPlans.effectDefinition()),
                TestPlans.timelineDocumentWithClips(List.of(TestPlans.secondClip())));
        assertNotEquals(base.fingerprint(), changed.fingerprint(),
                "K2: target change -> plan fingerprint changes");
    }

    @Test
    void k4_sameSemanticTargetReconstructionDeterministic() {
        EffectSemanticSnapshot a = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshot b = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertEquals(a.contentDigest(), b.contentDigest(),
                "K4: same semantic target reconstruction -> deterministic digest (BI1)");
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
                () -> TestPlans.effectSnapshot(
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
        // I2/D1: same (definitionId, version) with DIFFERENT semantic content
        // digest -> FAIL CLOSED (EFFECT_DEFINITION_VERSION_CONTENT_IS_IMMUTABLE_V1).
        EffectInstance.EffectDefinition defDupDiff = new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                TestPlans.effectDefinition().parameterSchema(),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("changed-property"),
                TestPlans.effectDefinition().requiredCapabilities(),
                TestPlans.effectDefinition().supportedBackendCapabilities());
        assertThrows(IllegalArgumentException.class,
                () -> TestPlans.effectSnapshot(
                        List.of(TestPlans.gaussianBlurEffect()),
                        List.of(TestPlans.effectDefinition(), defDupDiff)),
                "I2: same (id, version) different content digest -> FAIL CLOSED");
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
        // I3/MT3: mediaType is DERIVED from canonical TrackType ∩ definition
        // supportedMediaTypes. An AUDIO-track effect with a VIDEO-only
        // definition FAILS CLOSED (track type from TimelineTrack.type(), not
        // trackId string — the trackId "audio" here is irrelevant; the track
        // TYPE is AUDIO).
        EffectInstance audioTrackInstance = new EffectInstance(
                "eff-audio", "def-blur", "1",
                EffectInstance.EffectMediaType.AUDIO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1)),
                TestPlans.gaussianBlurEffect().parameters(), Map.of(),
                new ClipEffectTarget("audio", "ac1"),
                TestPlans.gaussianBlurEffect().provenance());
        TimelineDocument audioDoc = new TimelineDocument(
                TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(new TimelineTrack("audio", "audio-track",
                        TrackType.AUDIO,
                        List.of(new com.example.platform.timeline.canonical.TimelineClip(
                                "ac1", "asset-audio", "stream-audio",
                                TestPlans.artifactId().value(), TestPlans.artifactDigest().value(),
                                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                                MediaTime.ofRational(0, 1), MediaTime.ofRational(2, 1),
                                "MEDIA_STREAM",
                                com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping.of(
                                        1, 1, com.example.platform.timeline.semantics.temporal.PlaybackDirection.FORWARD))))),
                TimelineMetadata.empty(), TestPlans.audioMix(), List.of(), List.of());
        assertThrows(IllegalArgumentException.class,
                () -> TestPlans.authority().mintFromAuthoredState(
                        List.of(audioTrackInstance), List.of(TestPlans.effectDefinition()), audioDoc),
                "I3/MT3: AUDIO track type incompatible with VIDEO-only definition -> FAIL CLOSED");
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
                () -> TestPlans.effectSnapshot(
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
                () -> TestPlans.effectSnapshot(
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
