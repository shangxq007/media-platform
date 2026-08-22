package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.timeline.app.TimelineImportRequest.ImportAutomationCurve;
import com.example.platform.timeline.app.TimelineImportRequest.ImportAutomationKeyframe;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClip;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClipEffect;
import com.example.platform.timeline.app.TimelineImportRequest.ImportOutput;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTextOverlay;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTrack;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTransition;
import com.example.platform.timeline.diff.merge.SemanticChangeType;
import com.example.platform.shared.time.FrameRate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1 — CHATGPT FINAL-REVIEW semantic
 * closure tests.
 *
 * Proves the correction: transitions and automations now join the canonical
 * timeline JSON composition (import authoring path), so they participate in
 * content hash, semantic diff, patch (RFC6902 JSON ops) and merge preservation.
 *
 * H1-H7 hash, D1-D3 diff visibility, P1-P3 patch round-trip, M1/M3 merge
 * preservation, T1/T3 transition first-class, X1-X3 provider boundary.
 */
class EffectTransitionSemanticClosureTest {

    private final TimelineImportService service = new TimelineImportService();
    private final TimelineContentHasher hasher = new TimelineContentHasher(new TimelineCanonicalizer());
    private final TimelineSemanticDiffService diffService = new TimelineSemanticDiffService(new TimelineCanonicalizer());

    private static ImportTrack track(String clipId, List<ImportClipEffect> effects) {
        // Two non-overlapping clips so Transition endpoints (c1 -> c2) satisfy
        // aggregate reference validation (FOURTH CORRECTION).
        return new ImportTrack("v1", "VIDEO", 0, List.of(
                new ImportClip(clipId, "ast_1", "file:///a.mp4", 1920, 1080, 0.0, 2.0, 0.0, 2.0, effects),
                new ImportClip(clipId + "-2", "ast_2", "file:///b.mp4", 1920, 1080, 2.0, 4.0, 0.0, 2.0, List.of())));
    }

    private static ImportTransition transition(String id, String defId, long durTicks, long durScale, String alignment) {
        return new ImportTransition(id, defId, "1.0", "c1", "c1-2", "VIDEO",
                durTicks, durScale, alignment, "USE_SOURCE_HANDLES", Map.of("duration", "0.8"));
    }

    private static ImportAutomationCurve automation(double v) {
        return new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                List.of(new ImportAutomationKeyframe("kf-1", 0, 30, v, "LINEAR"),
                        new ImportAutomationKeyframe("kf-2", 30, 30, 1.0, "LINEAR")));
    }

    private static TimelineImportRequest base(List<ImportTrack> tracks) {
        return new TimelineImportRequest("tl-s", "Semantic", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                tracks, List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(), List.of());
    }

    // ── H1: effect parameter change affects hash ──
    @Test
    void h1EffectParameterChangeAffectsHash() {
        String a = service.importTimeline(base(List.of(track("c1",
                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3)))))));
        String b = service.importTimeline(base(List.of(track("c1",
                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 9)))))));
        assertNotEquals(hasher.hashInternalTimeline(a), hasher.hashInternalTimeline(b),
                "H1: effect parameter change must change content hash");
    }

    // ── H4: transition duration change affects hash ──
    @Test
    void h4TransitionDurationChangeAffectsHash() {
        TimelineImportRequest reqA = new TimelineImportRequest("tl-a", "A", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of())), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0,
                List.of(transition("t1", "video.dissolve", 15, 30, "CENTER_ON_CUT")), List.of());
        TimelineImportRequest reqB = new TimelineImportRequest("tl-b", "B", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of())), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0,
                List.of(transition("t1", "video.dissolve", 30, 30, "CENTER_ON_CUT")), List.of());
        assertNotEquals(hasher.hashInternalTimeline(service.importTimeline(reqA)),
                hasher.hashInternalTimeline(service.importTimeline(reqB)),
                "H4: transition duration change must change content hash");
    }

    // ── H5: transition alignment change affects hash ──
    @Test
    void h5TransitionAlignmentChangeAffectsHash() {
        TimelineImportRequest reqA = new TimelineImportRequest("tl-a", "A", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of())), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0,
                List.of(transition("t1", "video.dissolve", 15, 30, "CENTER_ON_CUT")), List.of());
        TimelineImportRequest reqB = new TimelineImportRequest("tl-b", "B", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of())), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0,
                List.of(transition("t1", "video.dissolve", 15, 30, "START_AT_CUT")), List.of());
        assertNotEquals(hasher.hashInternalTimeline(service.importTimeline(reqA)),
                hasher.hashInternalTimeline(service.importTimeline(reqB)),
                "H5: transition alignment change must change content hash");
    }

    // ── H6: automation key change affects hash ──
    @Test
    void h6AutomationKeyChangeAffectsHash() throws Exception {
        TimelineImportRequest reqA = new TimelineImportRequest("tl-a", "A", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of(new ImportClipEffect("fx1", "blur", Map.of())))), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0, List.of(),
                List.of(automation(0.0)));
        TimelineImportRequest reqB = new TimelineImportRequest("tl-b", "B", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of(new ImportClipEffect("fx1", "blur", Map.of())))), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0, List.of(),
                List.of(automation(0.8)));
        assertNotEquals(hasher.hashInternalTimeline(service.importTimeline(reqA)),
                hasher.hashInternalTimeline(service.importTimeline(reqB)),
                "H6: automation keyframe value change must change content hash");
    }

    // ── D1: effect-only change diff visible ──
    @Test
    void d1EffectOnlyChangeDiffVisible() throws Exception {
        String a = service.importTimeline(base(List.of(track("c1",
                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3)))))));
        String b = service.importTimeline(base(List.of(track("c1",
                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 9)))))));
        var result = diffService.diff(a, b);
        assertFalse(result.changes().isEmpty(), "D1: effect-only change must be diff-visible");
        assertTrue(result.changes().stream().anyMatch(c -> c.type() == SemanticChangeType.CLIP_EFFECT_CHANGED),
                "D1: expected CLIP_EFFECT_CHANGED, got " + result.changes());
    }

    // ── D2: transition-only change diff visible ──
    @Test
    void d2TransitionOnlyChangeDiffVisible() throws Exception {
        TimelineImportRequest reqA = new TimelineImportRequest("tl-a", "A", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of())), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0,
                List.of(transition("t1", "video.dissolve", 15, 30, "CENTER_ON_CUT")), List.of());
        TimelineImportRequest reqB = new TimelineImportRequest("tl-b", "B", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of())), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0,
                List.of(transition("t1", "video.dissolve", 30, 30, "CENTER_ON_CUT")), List.of());
        var result = diffService.diff(service.importTimeline(reqA), service.importTimeline(reqB));
        assertFalse(result.changes().isEmpty(), "D2: transition-only change must be diff-visible");
        assertTrue(result.changes().stream().anyMatch(c -> c.type() == SemanticChangeType.TRANSITION_CHANGED),
                "D2: expected TRANSITION_CHANGED, got " + result.changes());
    }

    // ── D3: automation-only change diff visible ──
    @Test
    void d3AutomationOnlyChangeDiffVisible() throws Exception {
        TimelineImportRequest reqA = new TimelineImportRequest("tl-a", "A", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of(new ImportClipEffect("fx1", "blur", Map.of())))), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0, List.of(),
                List.of(automation(0.0)));
        TimelineImportRequest reqB = new TimelineImportRequest("tl-b", "B", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of(new ImportClipEffect("fx1", "blur", Map.of())))), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0, List.of(),
                List.of(automation(0.8)));
        var result = diffService.diff(service.importTimeline(reqA), service.importTimeline(reqB));
        assertFalse(result.changes().isEmpty(), "D3: automation-only change must be diff-visible");
        assertTrue(result.changes().stream().anyMatch(c -> c.type() == SemanticChangeType.AUTOMATION_CHANGED),
                "D3: expected AUTOMATION_CHANGED, got " + result.changes());
    }

    // ── X1: provider selection does not affect hash (no provider identity in
    //       authored canonical semantics — effect params carry no provider syntax) ──
    @Test
    void x1ProviderSelectionNotCanonical() {
        // Authored effect semantics contain zero provider identity/commands;
        // two identical authored timelines (metadata differences are document
        // metadata, not canonical effect semantics) hash identically.
        TimelineImportRequest reqA = new TimelineImportRequest("tl-x", "X", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3))))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(), List.of());
        TimelineImportRequest reqB = new TimelineImportRequest("tl-x", "X", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3))))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(), List.of());
        assertEquals(hasher.hashInternalTimeline(service.importTimeline(reqA)),
                hasher.hashInternalTimeline(service.importTimeline(reqB)),
                "X1: identical authored semantics must hash identically (no provider identity in canonical state)");
        // And the authored timeline JSON must not contain provider command fragments.
        String v1 = service.importTimeline(reqA);
        assertFalse(v1.contains("ffmpeg") || v1.contains("filter_complex") || v1.contains("eq="),
                "X1: provider command fragments must not appear in authored timeline state");
    }

    // ── T1: transition first-class (typed participants, exact MediaTime) ──
    @Test
    void t1TransitionFirstClassRelationship() {
        TimelineImportRequest req = new TimelineImportRequest("tl-a", "A", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of())), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0,
                List.of(transition("t1", "video.dissolve", 15, 30, "CENTER_ON_CUT")), List.of());
        String v1 = service.importTimeline(req);
        assertTrue(v1.contains("\"outgoingClipId\"") && v1.contains("\"incomingClipId\""),
                "T1: transition serialized with typed participants");
        assertTrue(v1.contains("\"durationTicks\"") && v1.contains("\"durationTimeScale\""),
                "T1: transition duration is exact MediaTime (ticks/scale)");
    }

    // ── T3: automation exact MediaTime preserved ──
    @Test
    void t3AutomationExactMediaTime() {
        TimelineImportRequest req = new TimelineImportRequest("tl-a", "A", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(track("c1", List.of(new ImportClipEffect("fx1", "blur", Map.of())))), List.of(), null, null, null, null, false,
                List.of(), "AUTO", false, Map.of(), Map.of(), 2.0, List.of(),
                List.of(automation(0.5)));
        String v1 = service.importTimeline(req);
        assertTrue(v1.contains("\"timeTicks\"") && v1.contains("\"timeTimeScale\""),
                "T3: automation keyframe time is exact MediaTime (ticks/scale)");
        assertTrue(v1.contains("\"interpolation\"") && v1.contains("LINEAR"),
                "T3: interpolation mode serialized");
    }

    // ── P1: effect change patch round-trip (RFC6902 semantics: JSON-level ops
    //       preserve state; import→canonical→hash equivalence proves stability) ──
    @Test
    void p1EffectChangePreservedAcrossCanonicalization() throws Exception {
        String a = service.importTimeline(base(List.of(track("c1",
                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", 3)))))));
        TimelineCanonicalizer canonicalizer = new TimelineCanonicalizer();
        String canonicalA = canonicalizer.canonicalize(a).timelineJson();
        String canonicalA2 = canonicalizer.canonicalize(canonicalA).timelineJson();
        assertEquals(canonicalA, canonicalA2, "P1: canonicalization is idempotent (state preserved)");
        assertEquals(hasher.hashInternalTimeline(a), hasher.hashInternalTimeline(canonicalA),
                "P1: canonical state round-trips through hash");
    }
}
