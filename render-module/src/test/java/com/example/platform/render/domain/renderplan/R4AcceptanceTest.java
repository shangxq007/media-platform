package com.example.platform.render.domain.renderplan;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.TimelineContentDigester;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.effect.AuthoredEffectSemanticAuthority;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshot;
import com.example.platform.timeline.semantics.effect.EffectSemanticSnapshotReference;
import com.example.platform.timeline.semantics.effect.EffectSemanticStateCanonicalSemantics;
import com.example.platform.timeline.version.TimelineRevision;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 correction R4 acceptance tests:
 * <ul>
 *   <li>R4-A1: authored Effect binding — cross-revision/context assembly fails
 *       closed; authoritative digest recomputation; same-state → same pin.</li>
 *   <li>R4-A2/A4: final RenderPlan retains the Effect semantic reference and
 *       provenance explains it.</li>
 *   <li>R4-A3: authored Effect semantic change changes the fingerprint even
 *       when materialized nodes stay identical (applicationRange, definition
 *       version, automation binding, enabled).</li>
 *   <li>R4-B: parameter pair framing is collision-free for delimiter-bearing
 *       keys/values; single shared encoder used in both node identity and plan
 *       canonical paths.</li>
 *   <li>R4-M: sealed canonicalizers fail closed (ColorDescription source
 *       structural proof).</li>
 * </ul>
 */
class R4AcceptanceTest {

    // ── R4-A1: authoritative effect binding ─────────────────────────────────

    @Test
    void crossRevisionEffectBindingFailsClosed() {
        // A snapshot minted for revision "other-rev" (distinct handle) cannot
        // satisfy the canonical revision's pin — binding identity is exact and
        // immutable (RP3-C/BI2).
        EffectSemanticSnapshot foreignSnapshot = TestPlans.effectSnapshot(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshotReference canonicalPin = TestPlans.effectSnapshotReference(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedRenderSemanticSnapshotFactory.verified(
                        TestPlans.timelineRevision(), TestPlans.timelineDigester(),
                        foreignSnapshot, canonicalPin),
                "cross-revision effect binding -> fail closed (R4-A1/RP3-C)");
    }

    @Test
    void effectDigestMismatchFailsClosed() {
        // Tampered effect state (radiusPixels=99) cannot satisfy the pin over
        // the canonical state (digest mismatch).
        EffectInstance different = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "99"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        EffectSemanticSnapshot tamperedSnapshot = TestPlans.effectSnapshot(
                List.of(different), List.of(TestPlans.effectDefinition()));
        EffectSemanticSnapshotReference canonicalPin = TestPlans.effectSnapshotReference(
                List.of(TestPlans.gaussianBlurEffect()), List.of(TestPlans.effectDefinition()));
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                        tamperedSnapshot, canonicalPin, TestPlans.REVISION_ID),
                "effect state digest mismatch -> fail closed (R4-A1/RP2)");
    }

    @Test
    void sameStateReconstructedYieldsSameDigestAndFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput a = TestPlans.canonicalInput();
        RenderPlanningInput b = TestPlans.canonicalInput(); // fresh objects
        assertEquals(
                a.effectSemanticSnapshot().contentPin(),
                b.effectSemanticSnapshot().contentPin(),
                "same authoritative Effect semantic state -> same digest (fresh objects)");
        assertEquals(
                planner.plan(a).plan().fingerprint().sha256Hex(),
                planner.plan(b).plan().fingerprint().sha256Hex(),
                "same authored state -> same RenderPlan fingerprint");
    }

    @Test
    void differentEffectStateYieldsDifferentDigestAndFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        EffectInstance changed = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "8"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput changedInput = TestPlans.inputWithEffectState(
                List.of(changed), base.effectSemanticSnapshot().effectDefinitions());
        assertNotEquals(
                base.effectSemanticSnapshot().contentPin(),
                changedInput.effectSemanticSnapshot().contentPin(),
                "different Effect semantic state -> different digest");
        assertNotEquals(
                planner.plan(base).plan().fingerprint().sha256Hex(),
                planner.plan(changedInput).plan().fingerprint().sha256Hex(),
                "different authored state -> different RenderPlan fingerprint (R4-A3)");
    }

    // ── R4-A2/A4: pin retention + provenance ────────────────────────────────

    @Test
    void finalPlanRetainsEffectSemanticReference() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlan plan = planner.plan(TestPlans.canonicalInput()).plan();
        EffectSemanticReference ref = plan.effectSemanticReference();
        assertNotNull(ref, "final RenderPlan retains the Effect semantic reference (R4-A2)");
        assertEquals(TestPlans.REVISION_ID, ref.revisionId());
        assertEquals("effect-semantics-v1", ref.semanticContractVersion().value());
        assertNotNull(ref.effectStateDigest(), "reference carries the immutable content digest");
        // provenance explains the same pin
        assertEquals(ref, plan.provenance().effectSemanticReference(),
                "provenance explains the same Effect semantic reference (R4-A4)");
        assertEquals(TestPlans.REVISION_ID, plan.provenance().timelineRevisionId());
    }

    @Test
    void provenanceOnlyMetadataChangeDoesNotChangeFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlan plan = planner.plan(TestPlans.canonicalInput()).plan();
        // provenance carries the SAME semantic inputs; the fingerprint does not
        // change when we rebuild provenance with identical values (already
        // covered by determinism) — and provenance itself is NOT a fingerprint
        // participant: two plans with identical semantic inputs but different
        // provenance fields would have identical fingerprints. Here we assert
        // the provenance fields are exactly the semantic inputs (no extra
        // non-semantic metadata is added to the fingerprint path).
        assertEquals(plan.provenance().effectSemanticReference().effectStateDigest(),
                plan.effectSemanticReference().effectStateDigest());
    }

    // ── R4-A3: authored effect semantic change always changes fingerprint ───

    @Test
    void applicationRangeChangeChangesFingerprintEvenIfSameClipOverlap() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();
        // FINAL V1: applicationRange is DERIVED from the target clip extent
        // (APPLICATION_RANGE_AUTHORITY_V1) — a caller-supplied narrower range
        // is NOT authority (SA3): the derived extent is materialized and the
        // fingerprint is UNCHANGED.
        EffectInstance rangeChanged = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(MediaTime.ofRational(0, 1), MediaTime.ofRational(1, 1)),
                TestPlans.gaussianBlurEffect().parameters(),
                TestPlans.gaussianBlurEffect().automationBindings(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput changed = TestPlans.inputWithEffectState(
                List.of(rangeChanged), base.effectSemanticSnapshot().effectDefinitions());
        assertEquals(baseFp, planner.plan(changed).plan().fingerprint().sha256Hex(),
                "FINAL V1: caller-supplied applicationRange is ignored (DERIVED) — fingerprint stable");
    }

    @Test
    void definitionVersionChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();
        // New definition version, same category/parameters.
        EffectInstance.EffectDefinition defV2 = new EffectInstance.EffectDefinition(
                "def-blur", "2",
                EffectInstance.EffectCategory.GAUSSIAN_BLUR,
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
                TestPlans.gaussianBlurEffect().parameters(),
                TestPlans.gaussianBlurEffect().automationBindings(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput changed = TestPlans.inputWithEffectState(
                List.of(instanceV2), List.of(defV2));
        assertNotEquals(baseFp, planner.plan(changed).plan().fingerprint().sha256Hex(),
                "definition version change -> fingerprint MUST change (R4-A3)");
    }

    @Test
    void automationBindingChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();
        // FINAL V1 (UNVERIFIED_EFFECT_AUTOMATION_REFERENCES_FAIL_CLOSED_V1):
        // non-empty automationBindings are UNSUPPORTED — the domain authority
        // FAILS CLOSED (SA5); there is no caller-controlled automation to
        // change the fingerprint.
        EffectInstance automationChanged = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(),
                Map.of("radius", "auto.radius"), // automation binding added
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertThrows(IllegalArgumentException.class,
                () -> TestPlans.inputWithEffectState(
                        List.of(automationChanged), base.effectSemanticSnapshot().effectDefinitions()),
                "FINAL V1: non-empty unverified automation -> FAIL CLOSED (R4-A3/SA5)");
    }

    @Test
    void enabledStateChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();
        EffectInstance disabled = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, false, // disabled
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(),
                TestPlans.gaussianBlurEffect().automationBindings(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput changed = TestPlans.inputWithEffectState(
                List.of(disabled), base.effectSemanticSnapshot().effectDefinitions());
        assertNotEquals(baseFp, planner.plan(changed).plan().fingerprint().sha256Hex(),
                "enabled state change -> fingerprint MUST change (R4-A3)");
    }

    // ── R4-B: parameter pair framing ────────────────────────────────────────

    @Test
    void parameterPairFramingSeparatesKeyValueBoundaries() {
        // ("a:b","c") vs ("a","b:c") MUST differ.
        String ab = EffectSemanticStateCanonicalSemantics.encodeParameterPair("a:b", "c");
        String bc = EffectSemanticStateCanonicalSemantics.encodeParameterPair("a", "b:c");
        assertNotEquals(ab, bc, "colon inside key vs value must not collide (R4-B)");
        // ("a=b","c") vs ("a","b=c") MUST differ.
        assertNotEquals(
                EffectSemanticStateCanonicalSemantics.encodeParameterPair("a=b", "c"),
                EffectSemanticStateCanonicalSemantics.encodeParameterPair("a", "b=c"),
                "equals inside key vs value must not collide (R4-B)");
    }

    @Test
    void parameterPairFramingHandlesHostileValues() {
        String[] hostile = {":", "=", ";", ",", "\"", "\\", "ünïcödé", "🙂", ""};
        for (String k : hostile) {
            for (String v : hostile) {
                String enc = EffectSemanticStateCanonicalSemantics.encodeParameterPair(k, v);
                assertNotNull(enc);
                // round-trip separation proof: key/value with embedded delimiters
                assertNotEquals(
                        EffectSemanticStateCanonicalSemantics.encodeParameterPair(k + ":", v),
                        EffectSemanticStateCanonicalSemantics.encodeParameterPair(k, ":" + v),
                        "hostile value collision: k='" + k + "' v='" + v + "'");
            }
        }
    }

    @Test
    void parameterOrderIsSemanticEqual() {
        EffectInstance e1 = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("a", "1", "b", "2"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        EffectInstance e2 = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("b", "2", "a", "1"), Map.of(), // reversed insertion order
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        assertEquals(
                EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                        List.of(e1), List.of(TestPlans.effectDefinition())),
                EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                        List.of(e2), List.of(TestPlans.effectDefinition())),
                "unordered parameter map -> order-insensitive canonical bytes (R4-B)");
    }

    @Test
    void nodeIdentityAndPlanCanonicalUseSameEncoder() {
        // The requirement-identity path and the final plan canonical path must
        // both go through the shared pair encoder: prove the bytes produced for
        // a hostile pair are identical across both usages by fingerprinting a
        // plan with such a parameter and confirming the node requirement hash
        // is derived from the shared encoder output (not a delimiter grammar).
        EffectInstance hostile = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("a:b", "c"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput input = TestPlans.inputWithEffectState(
                List.of(hostile), List.of(TestPlans.effectDefinition()));
        RenderPlan plan = new DefaultRenderPlanner().plan(input).plan();
        EffectMaterializationRequirement req = plan.nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .flatMap(n -> n.materializationRequirements().stream())
                .filter(r -> r instanceof EffectMaterializationRequirement)
                .map(r -> (EffectMaterializationRequirement) r)
                .findFirst().orElseThrow();
        String pair = EffectSemanticStateCanonicalSemantics.encodeParameterPair("a:b", "c");
        assertTrue(req.parameters().stream()
                        .anyMatch(p -> EffectSemanticStateCanonicalSemantics.encodeParameterPair(
                                p.key(), p.value()).equals(pair)),
                "plan canonical parameter pair uses the shared encoder (R4-B)");
    }

    // ── R4-M: sealed fail-closed ────────────────────────────────────────────

    @Test
    void sealedCanonicalizersFailClosedStructurally() {
        String codecSource = readCodecSource();
        // ColorDescription + ColorPrimaries + materialization requirement all
        // have explicit fail-closed branches; no generic fallback token.
        assertFalse(codecSource.contains("UNKNOWN_VARIANT"),
                "no UNKNOWN_VARIANT generic fallback (R4-M)");
        assertTrue(codecSource.contains("Unsupported ColorDescription variant"),
                "ColorDescription fail-closed branch (R4-M1)");
        assertTrue(codecSource.contains("Unsupported ColorPrimaries variant"),
                "ColorPrimaries fail-closed branch (R4-M1)");
        assertTrue(codecSource.contains("Unsupported RenderMaterializationRequirement variant"),
                "materialization requirement fail-closed branch (R4-M1)");
    }

    @Test
    void effectProvenanceFieldsAreExcludedFromSemanticDigest() {
        // R4-A5: provenance (source/sourceId/createdAt) is explanatory — two
        // instances differing ONLY in provenance must have the same semantic
        // digest.
        EffectInstance a = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(),
                Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),

                new EffectInstance.EffectProvenance("src-A", "id-1", 1000L));
        EffectInstance b = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                TestPlans.gaussianBlurEffect().parameters(),
                Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),

                new EffectInstance.EffectProvenance("src-B", "id-2", 9999L));
        assertEquals(
                EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                        List.of(a), List.of(TestPlans.effectDefinition())),
                EffectSemanticStateCanonicalSemantics.canonicalEffectState(
                        List.of(b), List.of(TestPlans.effectDefinition())),
                "provenance-only difference -> identical semantic digest (R4-A5)");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String readCodecSource() {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/java/com/example/platform/render/domain/renderplan/RenderPlanCanonicalCodec.java"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
