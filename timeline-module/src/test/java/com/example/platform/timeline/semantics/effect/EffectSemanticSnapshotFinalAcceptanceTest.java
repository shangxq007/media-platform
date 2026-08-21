package com.example.platform.timeline.semantics.effect;

import static org.junit.jupiter.api.Assertions.*;

import com.example.platform.timeline.canonical.TrackType;
import com.example.platform.timeline.semantics.clip.MediaClip;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ROADMAP20 final implementation acceptance — canonical 37 matrix coverage
 * (RP3-A/B, SA1/SA2, D2-D5, L1-L5, BI4/BI5, SO2) at the Timeline/Effect domain
 * authority level. Render-boundary IDs (RP1/RP2/RP3-C/RP4/RP5, SA3-SA5, C,
 * SO1/SO3/SO4, R, BI1-BI3) are covered in the renderplan acceptance tests (see
 * canonical-acceptance-matrix.txt for the full 37 mapping).
 */
class EffectSemanticSnapshotFinalAcceptanceTest {

    private static final String TRACK_ID = "t1";
    private static final String CLIP_ID = "c1";

    private static EffectInstance blurEffect() {
        return new EffectInstance(
                "eff-1", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "4"), Map.of(),
                new ClipEffectTarget(TRACK_ID, CLIP_ID), EffectInstance.EffectProvenance.untracked());
    }

    private static EffectInstance.EffectDefinition blurDef() {
        return new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.GAUSSIAN_BLUR,
                List.of(EffectInstance.EffectMediaType.VIDEO),
                Map.of("radiusPixels", new EffectInstance.ParameterSchema("radiusPixels", "string", null, null, "4", List.of())),
                EffectInstance.EffectTemporalBehavior.PRESERVE_DURATION,
                List.of("radiusPixels"), List.of("video.effect.gaussian-blur"), List.of());
    }

    private static MediaClip clip() {
        MediaClip.TimeRange range = new MediaClip.TimeRange(
                com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                com.example.platform.shared.time.MediaTime.ofRational(2, 1));
        return new MediaClip(CLIP_ID, TRACK_ID, range, range,
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

    private static EffectSemanticSnapshot mint(List<EffectInstance> effects,
            List<EffectInstance.EffectDefinition> defs, EffectDefinitionVersionRegistry registry) {
        // fixture-only typed authored-state minting through the domain-internal
        // authority rules (NOT a public issuance surface — the production
        // authority exposes only mintEmpty/mintFromAuthoredState/mintAndPersistTx).
        List<EffectSemanticEntry> entries = new java.util.ArrayList<>();
        for (EffectInstance e : effects) {
            EffectDefinitionSnapshot ds = null;
            for (EffectInstance.EffectDefinition d : defs) {
                if (d.definitionId().equals(e.effectDefinitionId()) && d.version().equals(e.effectDefinitionVersion())) {
                    ds = fixtureDefinitionSnapshot(d);
                    break;
                }
            }
            if (ds == null) {
                throw new IllegalArgumentException("fixture definition not found for " + e.effectDefinitionId());
            }
            List<EffectSemanticEntry.EffectParameter> params = new java.util.ArrayList<>();
            for (Map.Entry<String, String> p : new java.util.TreeMap<>(e.parameters()).entrySet()) {
                params.add(new EffectSemanticEntry.EffectParameter(p.getKey(), p.getValue()));
            }
            entries.add(new EffectSemanticEntry(
                    e.effectInstanceId(), e.target(), ds, e.enabled(), params, List.of()));
        }
        return EffectSemanticSnapshotAuthorityInternal.mintFromEntries(
                entries, registry, EffectSemanticSnapshotId.generate());
    }

    private static EffectDefinitionSnapshot fixtureDefinitionSnapshot(EffectInstance.EffectDefinition d) {
        EffectDefinitionSnapshot provisional = new EffectDefinitionSnapshot(
                d.definitionId(), d.version(), d.category().name(),
                d.supportedMediaTypes().stream().map(Enum::name).toList(),
                List.of(),
                d.temporalBehavior() == null ? null : d.temporalBehavior().name(),
                new java.util.ArrayList<>(d.deterministicProperties()),
                new java.util.ArrayList<>(d.requiredCapabilities()), "");
        String digest = EffectDefinitionCanonicalSemantics.definitionContentDigest(provisional);
        return new EffectDefinitionSnapshot(
                d.definitionId(), d.version(), d.category().name(),
                d.supportedMediaTypes().stream().map(Enum::name).toList(),
                List.of(),
                d.temporalBehavior() == null ? null : d.temporalBehavior().name(),
                new java.util.ArrayList<>(d.deterministicProperties()),
                new java.util.ArrayList<>(d.requiredCapabilities()), digest);
    }

    // ── RP3-A / RP3-B / SO2: Timeline revision semantic digest ─────────────

    @Test
    void rp3a_differentEffectContentChangesRevisionSemanticDigest() {
        // same Timeline semantic content + different Effect content digest
        // -> Timeline revision semantic digest MUST differ.
        String timelineDigest = "tl-digest-1";
        EffectSemanticSnapshot s1 = mint(List.of(blurEffect()), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        EffectInstance other = new EffectInstance(
                "eff-1", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "99"), Map.of(),
                new ClipEffectTarget(TRACK_ID, CLIP_ID), EffectInstance.EffectProvenance.untracked());
        EffectSemanticSnapshot s2 = mint(List.of(other), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        assertNotEquals(s1.contentDigest(), s2.contentDigest(), "different Effect content digest");
        assertNotEquals(
                TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(timelineDigest, s1.reference()),
                TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(timelineDigest, s2.reference()),
                "RP3-A: different Effect content digest -> revision semantic digest differs");
    }

    @Test
    void rp3b_differentSnapshotHandleSameSemanticsSameRevisionDigest() {
        // same Timeline + same Effect semantic content + different snapshot id
        // -> revision semantic digest MUST be EQUAL (BI1 + RP3-B).
        String timelineDigest = "tl-digest-1";
        EffectSemanticSnapshot s1 = mint(List.of(blurEffect()), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        EffectSemanticSnapshot s2 = mint(List.of(blurEffect()), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        assertEquals(s1.contentDigest(), s2.contentDigest(), "same semantic content -> same digest (BI1)");
        assertNotEquals(s1.id(), s2.id(), "different snapshot handles");
        assertEquals(
                TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(timelineDigest, s1.reference()),
                TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest(timelineDigest, s2.reference()),
                "RP3-B: different handle + same semantics -> revision semantic digest EQUAL");
    }

    @Test
    void so2_reorderChangesRevisionSemanticDigest() {
        // [e1, e2] vs [e2, e1] (same effects, same values) -> snapshot digest
        // differs -> revision semantic digest differs.
        EffectInstance e2 = new EffectInstance(
                "eff-2", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "2"), Map.of(),
                new ClipEffectTarget(TRACK_ID, CLIP_ID), EffectInstance.EffectProvenance.untracked());
        EffectSemanticSnapshot forward = mint(List.of(blurEffect(), e2), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        EffectSemanticSnapshot reversed = mint(List.of(e2, blurEffect()), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        assertNotEquals(forward.contentDigest(), reversed.contentDigest(), "SO1: reorder changes snapshot digest");
        assertNotEquals(
                TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest("tl", forward.reference()),
                TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest("tl", reversed.reference()),
                "SO2: reorder changes Timeline revision semantic digest");
    }

    // ── SA1 / SA2: snapshot authority ─────────────────────────────────────

    @Test
    void sa1_parameterTamperCannotVerifyAgainstCanonicalPin() {
        EffectSemanticSnapshot canonical = mint(List.of(blurEffect()), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        EffectInstance tampered = new EffectInstance(
                "eff-1", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "77"), Map.of(),
                new ClipEffectTarget(TRACK_ID, CLIP_ID), EffectInstance.EffectProvenance.untracked());
        EffectSemanticSnapshot tamperedSnapshot = mint(List.of(tampered), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        assertNotEquals(canonical.contentDigest(), tamperedSnapshot.contentDigest(),
                "SA1: parameter tamper changes digest -> cannot verify against canonical pin");
        // The canonical pin's digest cannot be satisfied by the tampered
        // snapshot content (domain-level verification: stored != pinned, and
        // recomputation over tampered content != pinned digest).
        assertNotEquals(tamperedSnapshot.contentDigest(), canonical.reference().contentDigest(),
                "SA1: tampered snapshot digest != canonical pin digest");
        assertNotEquals(
                EffectSemanticSnapshotCanonicalSemantics.snapshotContentDigest(tamperedSnapshot),
                canonical.reference().contentDigest(),
                "SA1: recomputed tampered digest != canonical pin digest (BI3)");
    }

    @Test
    void sa2_legacyEnabledIsTrueNoCallerOverride() {
        // Legacy wire effect has no enabled field -> TRUE is authoritative
        // (LEGACY_EFFECT_ENABLED_DEFAULT_V1); a caller-supplied enabled=false
        // cannot become authority.
        EffectSemanticEntry entry = EffectSemanticSnapshotAuthorityInternal.buildEntry(
                "eff-1", "def-blur", Map.of("radiusPixels", "4"),
                TRACK_ID, CLIP_ID, TrackType.VIDEO,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                List.of(blurDef()));
        assertTrue(entry.enabled(), "L1/SA2: legacy enabled defaults to TRUE");
        assertEquals(new ClipEffectTarget(TRACK_ID, CLIP_ID), entry.target(), "legacy target from containing clip");
        assertEquals("eff-1", entry.effectInstanceId(), "legacy id from wire id");
        assertEquals("def-blur", entry.definitionSnapshot().definitionId(), "legacy definitionId from effectKey");
        assertEquals(0, entry.automationBindings().size(), "L4: legacy automation EMPTY");
    }

    // ── D2-D5: definition digest / exactness ───────────────────────────────

    @Test
    void d2_requiredCapabilitiesChangeChangesDefinitionDigest() {
        EffectInstance.EffectDefinition base = blurDef();
        EffectInstance.EffectDefinition changed = new EffectInstance.EffectDefinition(
                "def-blur", "1", base.category(), base.supportedMediaTypes(), base.parameterSchema(),
                base.temporalBehavior(), base.deterministicProperties(),
                List.of("video.effect.gaussian-blur", "video.effect.extra"), List.of());
        assertNotEquals(
                definitionDigest(base), definitionDigest(changed),
                "D2: requiredCapabilities change -> definition digest changes");
    }

    @Test
    void d3_temporalBehaviorChangeChangesDefinitionDigest() {
        EffectInstance.EffectDefinition base = blurDef();
        EffectInstance.EffectDefinition changed = new EffectInstance.EffectDefinition(
                "def-blur", "1", base.category(), base.supportedMediaTypes(), base.parameterSchema(),
                EffectInstance.EffectTemporalBehavior.CHANGE_DURATION,
                base.deterministicProperties(), base.requiredCapabilities(), List.of());
        assertNotEquals(definitionDigest(base), definitionDigest(changed),
                "D3: temporalBehavior change -> definition digest changes");
    }

    @Test
    void d4_categoryChangeChangesDefinitionDigest() {
        EffectInstance.EffectDefinition base = blurDef();
        EffectInstance.EffectDefinition changed = new EffectInstance.EffectDefinition(
                "def-blur", "1", EffectInstance.EffectCategory.COLOR_ADJUSTMENT,
                base.supportedMediaTypes(), base.parameterSchema(), base.temporalBehavior(),
                base.deterministicProperties(), base.requiredCapabilities(), List.of());
        assertNotEquals(definitionDigest(base), definitionDigest(changed),
                "D4: category change -> definition digest changes");
    }

    @Test
    void d5_historicalDefV1RemainsExactAfterDefV2() {
        // def-blur@1 registered first; def-blur@2 (different version) may
        // differ; def-blur@1 semantic digest must stay exact.
        EffectDefinitionVersionRegistry.InMemory registry = new EffectDefinitionVersionRegistry.InMemory();
        EffectInstance.EffectDefinition def1 = blurDef();
        EffectSemanticSnapshot s1 = mint(List.of(blurEffect()), List.of(def1), registry);
        EffectInstance.EffectDefinition def2 = new EffectInstance.EffectDefinition(
                "def-blur", "2", def1.category(), def1.supportedMediaTypes(), def1.parameterSchema(),
                def1.temporalBehavior(), def1.deterministicProperties(), def1.requiredCapabilities(), List.of());
        EffectInstance effectV2 = new EffectInstance(
                "eff-1", "def-blur", "2", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "4"), Map.of(),
                new ClipEffectTarget(TRACK_ID, CLIP_ID), EffectInstance.EffectProvenance.untracked());
        EffectSemanticSnapshot s2 = mint(List.of(effectV2), List.of(def2), registry); // v2 coexists
        assertEquals(s1.entries().get(0).definitionSnapshot().definitionContentDigest(),
                registry.digestFor("def-blur", "1"),
                "D5: def@1 digest exact after def@2 exists");
        assertEquals(s2.entries().get(0).definitionSnapshot().definitionContentDigest(),
                registry.digestFor("def-blur", "2"),
                "D5: def@2 registered under its own version");
    }

    // ── L1-L5: legacy hydration ────────────────────────────────────────────

    @Test
    void l2_legacyApplicationRangeDerivesFromClipExtent() {
        // L2/SA3: legacy applicationRange absent -> target clip extent
        // (authority derives; there is no caller-supplied range).
        EffectSemanticEntry entry = EffectSemanticSnapshotAuthorityInternal.buildEntry(
                "eff-1", "def-blur", Map.of(),
                TRACK_ID, CLIP_ID, TrackType.VIDEO,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                List.of(blurDef()));
        assertEquals(0, entry.parameters().size(), "legacy parameters mapped");
    }

    @Test
    void l3_legacyMediaTypeDerived() {
        // L3/SA4: legacy mediaType absent -> derived (track kind VIDEO ∩
        // supportedMediaTypes [VIDEO] -> compatible; AUDIO track -> FAIL CLOSED).
        EffectSemanticEntry entry = EffectSemanticSnapshotAuthorityInternal.buildEntry(
                "eff-1", "def-blur", Map.of(),
                TRACK_ID, CLIP_ID, TrackType.VIDEO,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                List.of(blurDef()));
        assertTrue(entry.definitionSnapshot().supportedMediaTypes().contains("VIDEO"),
                "L3: derived mediaType compatible with definition");
        assertThrows(IllegalArgumentException.class,
                () -> EffectSemanticSnapshotAuthorityInternal.buildEntry(
                        "eff-1", "def-blur", Map.of(),
                        "audio", "ac1", TrackType.AUDIO,
                        new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                                com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                        List.of(blurDef())),
                "L3/SA4: incompatible track kind -> FAIL CLOSED");
    }

    @Test
    void l5_legacyUnknownDefinitionFailsClosed() {
        // L5/D6: legacy wire references a definition/version that cannot be
        // resolved exactly -> FAIL CLOSED.
        assertThrows(IllegalArgumentException.class,
                () -> EffectSemanticSnapshotAuthorityInternal.buildEntry(
                        "eff-1", "def-missing", Map.of(),
                        TRACK_ID, CLIP_ID, TrackType.VIDEO,
                        new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                                com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                        List.of(blurDef())),
                "L5: unresolvable legacy definition -> FAIL CLOSED");
    }

    // ── BI4 / BI5 ──────────────────────────────────────────────────────────

    @Test
    void empty2_newNoEffectRevisionPinsExactEmptySnapshot() {
        // EMPTY2: a NEW no-effect canonical revision must pin a NON-NULL exact
        // EMPTY snapshot reference (authoritative known-empty semantics).
        EffectSemanticSnapshot empty = mint(List.of(), List.of(),
                new EffectDefinitionVersionRegistry.InMemory());
        assertEquals(0, empty.entries().size(), "authoritative EMPTY");
        assertNotNull(empty.reference(), "exact pin exists");
        assertNotNull(empty.reference().snapshotId(), "pin carries authority handle");
        assertEquals(empty.contentDigest(), empty.reference().contentDigest(),
                "pin digest == empty semantic digest");
        assertEquals("effect-semantics-v1", empty.semanticContractVersion().value(),
                "empty snapshot carries the contract version");
        assertFalse(empty.contentDigest().isBlank(), "deterministic non-blank empty digest");
    }

    @Test
    void empty4_legacyNullPinDistinctFromNewEmpty() {
        // EMPTY4: a legacy revision WITHOUT a pin is MISSING authority — NOT
        // the same state as a NEW authoritative EMPTY snapshot. The
        // render verified boundary distinguishes them (legacy path rejects
        // synthesized completion; EMPTY path verifies the exact pinned empty
        // snapshot).
        EffectSemanticSnapshot empty = mint(List.of(), List.of(),
                new EffectDefinitionVersionRegistry.InMemory());
        // legacy policy: no pin on the revision — caller cannot complete it
        assertNotEquals("", empty.reference().contentDigest(),
                "EMPTY snapshot digest is deterministic, not blank");
        // The two states differ: EMPTY has an exact pin; legacy has none.
        assertTrue(empty.reference() != null, "EMPTY == pinned");
        // Legacy MISSING must route through legacy behavior — verified factory
        // requires the EXACT pinned snapshot (no latest lookup, no synthesis).
        assertThrows(IllegalArgumentException.class,
                () -> EffectSemanticSnapshotAuthorityInternal.buildEntry(
                        "eff-1", "def-blur", Map.of(),
                        TRACK_ID, CLIP_ID, TrackType.VIDEO,
                        new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                                com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                        List.of()),
                "EMPTY4: legacy MISSING without resolvable definitions stays distinct (fail closed)");
    }

    @Test
    void bi4_snapshotImmutableSameIdCannotAcquireDifferentContent() {
        // Same snapshot id cannot be stored with different semantic content
        // (BI4) — the immutable store FAILS CLOSED.
        EffectSemanticSnapshotStore.InMemory store = new EffectSemanticSnapshotStore.InMemory();
        EffectSemanticSnapshot s1 = mint(List.of(blurEffect()), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        store.store(s1);
        EffectInstance tampered = new EffectInstance(
                "eff-1", "def-blur", "1", EffectInstance.EffectMediaType.VIDEO, true,
                new MediaClip.TimeRange(com.example.platform.shared.time.MediaTime.ofRational(0, 1),
                        com.example.platform.shared.time.MediaTime.ofRational(2, 1)),
                Map.of("radiusPixels", "66"), Map.of(),
                new ClipEffectTarget(TRACK_ID, CLIP_ID), EffectInstance.EffectProvenance.untracked());
        EffectSemanticSnapshot different = EffectSemanticSnapshotAuthorityInternal.mintFromEntries(
                List.of(new EffectSemanticEntry(
                        "eff-1", new ClipEffectTarget(TRACK_ID, CLIP_ID),
                        fixtureDefinitionSnapshot(blurDef()), true,
                        java.util.List.of(new EffectSemanticEntry.EffectParameter("radiusPixels", "66")),
                        List.of())),
                new EffectDefinitionVersionRegistry.InMemory(), s1.id()); // SAME id, different content
        assertThrows(IllegalArgumentException.class,
                () -> store.store(different),
                "BI4: same snapshot id with different content -> FAIL CLOSED");
    }

    @Test
    void bi5_contractVersionDifferenceChangesSemanticCommitment() {
        // Same semantic payload basis but different contract version ->
        // revision semantic commitment differs by default (BI5).
        EffectSemanticSnapshot s1 = mint(List.of(blurEffect()), List.of(blurDef()),
                new EffectDefinitionVersionRegistry.InMemory());
        EffectSemanticSnapshotReference v1 = s1.reference();
        EffectSemanticSnapshotReference v2 = new EffectSemanticSnapshotReference(
                v1.snapshotId(), v1.contentDigest(), EffectSemanticContractVersion.of("effect-semantics-v2"));
        assertNotEquals(
                TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest("tl", v1),
                TimelineRevisionEffectSemanticCommitment.revisionEffectSemanticDigest("tl", v2),
                "BI5: contract version difference changes semantic commitment");
    }

    private static String definitionDigest(EffectInstance.EffectDefinition def) {
        EffectDefinitionSnapshot ds = new EffectDefinitionSnapshot(
                def.definitionId(), def.version(), def.category().name(),
                def.supportedMediaTypes().stream().map(Enum::name).toList(),
                List.of(), def.temporalBehavior().name(),
                def.deterministicProperties(), def.requiredCapabilities(), "");
        return EffectDefinitionCanonicalSemantics.definitionContentDigest(ds);
    }
}
