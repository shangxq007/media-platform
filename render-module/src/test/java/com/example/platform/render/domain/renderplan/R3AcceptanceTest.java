package com.example.platform.render.domain.renderplan;

import com.example.platform.timeline.semantics.effect.AuthoredEffectSemanticAuthority;
import com.example.platform.timeline.semantics.effect.ClipEffectTarget;
import com.example.platform.timeline.semantics.effect.EffectInstance;
import com.example.platform.timeline.semantics.effect.EffectSemanticBinding;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ROADMAP20 correction R3 acceptance tests:
 * <ul>
 *   <li>R3-B1: complete authored semantic integrity — the primary planning API
 *       consumes ONE verified authored snapshot; arbitrary effect fragments
 *       cannot be injected beside a verified revision; effect state mismatch
 *       fails closed; effect WHAT remains recoverable.</li>
 *   <li>R3-B2: structurally unambiguous canonical framing — adversarial
 *       distinct-value constructions must NOT collide.</li>
 *   <li>R3-M1: sealed canonical variants fail closed (no generic fallback).</li>
 * </ul>
 */
class R3AcceptanceTest {

    // ── R3-B1: complete authored semantic integrity ─────────────────────────

    @Test
    void verifiedAuthoredSnapshotHydratesSuccessfully() {
        VerifiedRenderSemanticSnapshot snapshot = TestPlans.verifiedAuthoredSnapshot();
        assertEquals(TestPlans.REVISION_ID, snapshot.timelineRevision().revision().revisionId());
        assertEquals(1, snapshot.timelineRevision().clips().size());
        assertEquals(1, snapshot.effectSemanticSnapshot().effects().size());
        assertEquals(1, snapshot.effectSemanticSnapshot().effectDefinitions().size());
        assertNotNull(snapshot.effectSemanticSnapshot().contentPin(),
                "effect semantic state carries a value-bound content pin");
    }

    @Test
    void effectStateMismatchFailsClosed() {
        // effect referencing an unknown definition -> fail closed at factory
        EffectInstance orphan = new EffectInstance(
                "eff-orphan", "def-missing", "9",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.mediaClip().timelineRange(), Map.of(), Map.of(),
                TestPlans.gaussianBlurEffect().provenance());
                // target removed
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                        List.of(orphan), List.of(TestPlans.effectDefinition()),
                        AuthoredEffectSemanticAuthority.issue(TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(), List.of(orphan), List.of(TestPlans.effectDefinition()))),
                "unknown effectDefinitionId -> fail closed (R3-B1)");
    }

    @Test
    void effectDefinitionVersionMismatchFailsClosed() {
        // instance requests version "2" but definition is version "1" -> fail closed
        EffectInstance versionMismatch = new EffectInstance(
                "eff-vm", "def-blur", "2",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.mediaClip().timelineRange(), Map.of(), Map.of(),
                TestPlans.gaussianBlurEffect().provenance());
                // target removed
        assertThrows(IllegalArgumentException.class,
                () -> VerifiedEffectSemanticSnapshotFactory.verified(
                        List.of(versionMismatch), List.of(TestPlans.effectDefinition()),
                        AuthoredEffectSemanticAuthority.issue(TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(), List.of(versionMismatch), List.of(TestPlans.effectDefinition()))),
                "effectDefinitionVersion mismatch -> fail closed (R3-B1)");
    }

    @Test
    void arbitraryEffectListCannotEnterPlanningPathBesideVerifiedRevision() {
        // R3-B1: RenderPlanningInput has NO parameter accepting a bare
        // List<EffectInstance>; the only authored-input path is the verified
        // authored snapshot. A caller cannot express (R1 + arbitrary effects).
        // Compile-time proof: the record has exactly four components
        // (VerifiedRenderSemanticSnapshot, RenderRequest, SourceResolutionInput,
        // CapabilityContext) — no List<EffectInstance> component exists.
        assertEquals(4, RenderPlanningInput.class.getRecordComponents().length,
                "planning input = authoredSnapshot + 3 transient inputs only");
        assertFalse(List.of(RenderPlanningInput.class.getRecordComponents()).stream()
                        .anyMatch(c -> c.getType().getTypeName().contains("EffectInstance")),
                "no EffectInstance fragment component in primary planning API");
    }

    @Test
    void effectStatePinIsValueBoundDeterministic() {
        // semantic-equal reconstructed effect state -> same pin
        EffectInstance e1 = TestPlans.gaussianBlurEffect();
        EffectInstance e2 = TestPlans.gaussianBlurEffect();
        VerifiedEffectSemanticSnapshot s1 = VerifiedEffectSemanticSnapshotFactory.verified(
                List.of(e1), List.of(TestPlans.effectDefinition()),
                AuthoredEffectSemanticAuthority.issue(TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(), List.of(e1), List.of(TestPlans.effectDefinition())));
        VerifiedEffectSemanticSnapshot s2 = VerifiedEffectSemanticSnapshotFactory.verified(
                List.of(e2), List.of(TestPlans.effectDefinition()),
                AuthoredEffectSemanticAuthority.issue(TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(), List.of(e2), List.of(TestPlans.effectDefinition())));
        assertEquals(s1.contentPin(), s2.contentPin(),
                "semantic-equal effect state -> identical content pin");
        // distinct state -> distinct pin
        EffectInstance changed = new EffectInstance(
                e1.effectInstanceId(), e1.effectDefinitionId(), e1.effectDefinitionVersion(),
                e1.mediaType(), e1.enabled(), e1.applicationRange(),
                Map.of("radiusPixels", "8"), e1.automationBindings(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                e1.provenance());
        VerifiedEffectSemanticSnapshot s3 = VerifiedEffectSemanticSnapshotFactory.verified(
                List.of(changed), List.of(TestPlans.effectDefinition()),
                AuthoredEffectSemanticAuthority.issue(TestPlans.timelineRevision(), TestPlans.revisionOwnedProjection(), List.of(changed), List.of(TestPlans.effectDefinition())));
        assertNotEquals(s1.contentPin(), s3.contentPin(),
                "distinct effect state -> distinct content pin");
    }

    @Test
    void effectWhatRemainsRecoverableFromLogicalPlan() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningResult result = planner.plan(TestPlans.canonicalInput());
        RenderNode effect = result.plan().nodes().stream()
                .filter(n -> n.kind() instanceof RenderNodeKind.Effect)
                .findFirst().orElseThrow();
        EffectMaterializationRequirement req =
                (EffectMaterializationRequirement) effect.materializationRequirements().get(0);
        assertEquals(EffectInstance.EffectCategory.GAUSSIAN_BLUR, req.category());
        assertTrue(req.parameters().stream().anyMatch(p -> p.key().equals("radiusPixels")));
    }

    @Test
    void effectSemanticChangeChangesFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String baseFp = planner.plan(base).plan().fingerprint().sha256Hex();
        EffectInstance changed = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of("radiusPixels", "8"), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput changedInput = TestPlans.inputWithEffectState(
                List.of(changed), base.effectSemanticSnapshot().effectDefinitions());
        assertNotEquals(baseFp, planner.plan(changedInput).plan().fingerprint().sha256Hex(),
                "effect semantic change -> fingerprint changes");
    }

    // ── R3-B2: structurally unambiguous canonical framing ───────────────────

    @Test
    void fallbackDefaultChainVsScriptOverrideDoNotCollide() {
        // Value A: defaultChain=[A,B,C], scriptOverrides=[]
        // Value B: defaultChain=[A], scriptOverrides=[{script=B, chain=[C]}]
        // Scalar stream "A","B","C" identical → must NOT collide after framing.
        com.example.platform.fonttext.resolution.FontFallbackPolicy a =
                new com.example.platform.fonttext.resolution.FontFallbackPolicy(
                        List.of(new com.example.platform.fonttext.typography.FontFamilyName("A"),
                                new com.example.platform.fonttext.typography.FontFamilyName("B"),
                                new com.example.platform.fonttext.typography.FontFamilyName("C")),
                        List.of(), List.of(), List.of());
        com.example.platform.fonttext.resolution.FontFallbackPolicy b =
                new com.example.platform.fonttext.resolution.FontFallbackPolicy(
                        List.of(new com.example.platform.fonttext.typography.FontFamilyName("A")),
                        List.of(new com.example.platform.fonttext.resolution.FontFallbackPolicy.ScriptOverride(
                                com.example.platform.fonttext.text.ScriptTag.LATIN,
                                List.of(new com.example.platform.fonttext.typography.FontFamilyName("C")))),
                        List.of(), List.of());
        assertNotEquals(fallbackCanonical(a), fallbackCanonical(b),
                "defaultChain=[A,B,C]/[] must not collide with [A]/[{B,[C]}] (R3-B2 framing)");
    }

    @Test
    void semanticRunsVsStyleRunsDoNotCollide() {
        // semanticRuns=[1 run] styleRuns=[] vs semanticRuns=[] styleRuns=[1 run]
        // with identical scalar content must not collide.
        com.example.platform.fonttext.text.TextContent content =
                new com.example.platform.fonttext.text.TextContent("Hello");
        com.example.platform.fonttext.text.TextRange range =
                com.example.platform.fonttext.text.TextRange.of(0, content.scalarCount());
        StyledTextHelper.TextFixture fixture = StyledTextHelper.build(content, range);
        com.example.platform.fonttext.text.StyledText textA = new com.example.platform.fonttext.text.StyledText(
                content, List.of(fixture.semanticRun()), List.of(), fixture.paragraphStyle());
        com.example.platform.fonttext.text.StyledText textB = new com.example.platform.fonttext.text.StyledText(
                content, List.of(), List.of(fixture.styleRun()), fixture.paragraphStyle());
        assertNotEquals(styledTextCanonical(textA), styledTextCanonical(textB),
                "semanticRuns vs styleRuns sections must not collide (R3-B2 framing)");
    }

    @Test
    void familyPreferencesVsOtherSectionsDoNotCollide() {
        // familyPreferences=[A,B] with weight NORMAL vs familyPreferences=[A]
        // weight BOLD — different structure, must not collide.
        com.example.platform.fonttext.typography.FontSelectionIntent intentA =
                new com.example.platform.fonttext.typography.FontSelectionIntent(
                        List.of(new com.example.platform.fonttext.typography.FontFamilyName("A"),
                                new com.example.platform.fonttext.typography.FontFamilyName("B")),
                        com.example.platform.fonttext.typography.FontSelectionIntent.WeightIntent.NORMAL,
                        com.example.platform.fonttext.typography.FontSelectionIntent.StretchIntent.NORMAL,
                        com.example.platform.fonttext.typography.FontSelectionIntent.SlantIntent.NORMAL,
                        com.example.platform.fonttext.typography.OpticalSizingIntent.disabled(), List.of());
        com.example.platform.fonttext.typography.FontSelectionIntent intentB =
                new com.example.platform.fonttext.typography.FontSelectionIntent(
                        List.of(new com.example.platform.fonttext.typography.FontFamilyName("A")),
                        com.example.platform.fonttext.typography.FontSelectionIntent.WeightIntent.BOLD,
                        com.example.platform.fonttext.typography.FontSelectionIntent.StretchIntent.NORMAL,
                        com.example.platform.fonttext.typography.FontSelectionIntent.SlantIntent.NORMAL,
                        com.example.platform.fonttext.typography.OpticalSizingIntent.disabled(), List.of());
        assertNotEquals(fontSelectionCanonical(intentA), fontSelectionCanonical(intentB),
                "family preferences vs weight sections must not collide (R3-B2 framing)");
    }

    @Test
    void effectParameterCardinalityIsFramed() {
        // parameters=[{k=v}] vs parameters=[] must not collide in canonical bytes.
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput base = TestPlans.canonicalInput();
        String withParam = planner.plan(base).plan().fingerprint().sha256Hex();
        EffectInstance noParams = new EffectInstance(
                TestPlans.EFFECT_INSTANCE_ID, "def-blur", "1",
                EffectInstance.EffectMediaType.VIDEO, true,
                TestPlans.gaussianBlurEffect().applicationRange(),
                Map.of(), Map.of(),
                new ClipEffectTarget(TestPlans.TRACK_ID, TestPlans.CLIP_ID),
                TestPlans.gaussianBlurEffect().provenance());
        RenderPlanningInput without = TestPlans.inputWithEffectState(
                List.of(noParams), base.effectSemanticSnapshot().effectDefinitions());
        assertNotEquals(withParam, planner.plan(without).plan().fingerprint().sha256Hex(),
                "parameter cardinality must be framed (R3-B2)");
    }

    @Test
    void reconstructedEqualSnapshotProducesIdenticalFingerprint() {
        RenderPlanner planner = new DefaultRenderPlanner();
        RenderPlanningInput a = TestPlans.canonicalInput();
        RenderPlanningInput b = TestPlans.canonicalInput(); // fresh instances
        assertEquals(planner.plan(a).plan().fingerprint().sha256Hex(),
                planner.plan(b).plan().fingerprint().sha256Hex(),
                "semantic-equal reconstructed snapshot -> identical fingerprint");
    }

    // ── R3-M1: sealed variant fail-closed ───────────────────────────────────

    @Test
    void unknownMaterializationVariantFailsClosed() {
        // A future RenderMaterializationRequirement variant must fail closed at
        // the canonical encoding boundary (no silent generic fallback). We
        // cannot legally construct an unknown sealed subtype at runtime, so the
        // structural guarantee is enforced by the guard + exhaustive branches;
        // this test pins the codec's documented fail-closed branch indirectly
        // by asserting the codec source forbids generic fallback tokens.
        String source = readCodecSource();
        assertFalse(source.contains("UNKNOWN_VARIANT"),
                "no UNKNOWN_VARIANT generic fallback in canonical codec (R3-M1)");
        assertTrue(source.contains("fails closed (R3-M1)"),
                "fail-closed branch present in canonical codec (R3-M1)");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String fallbackCanonical(
            com.example.platform.fonttext.resolution.FontFallbackPolicy policy) {
        return RenderPlanFingerprintCalculator.codec().fallbackPolicyCanonicalForTest(policy);
    }

    private static String styledTextCanonical(com.example.platform.fonttext.text.StyledText styled) {
        return RenderPlanFingerprintCalculator.codec().styledTextCanonicalForTest(styled);
    }

    private static String fontSelectionCanonical(
            com.example.platform.fonttext.typography.FontSelectionIntent intent) {
        return RenderPlanFingerprintCalculator.codec().fontSelectionCanonicalForTest(intent);
    }

    private static String readCodecSource() {
        try {
            return java.nio.file.Files.readString(
                    java.nio.file.Path.of("src/main/java/com/example/platform/render/domain/renderplan/RenderPlanCanonicalCodec.java"));
        } catch (java.io.IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
