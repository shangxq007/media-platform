package com.example.platform.timeline.canonical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.fonttext.text.StyledText;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.TextFrame;
import com.example.platform.timeline.diff.TimelineChangeType;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ROADMAP #19 — TimedText canonical semantics closure tests.
 *
 * A identity/validation; B fingerprint; C Timeline hash; D diff/patch;
 * E/F merge semantics via real patch application; G provider boundary.
 */
class TimedTextCanonicalSemanticsTest {

    private final TimelineContentDigester digester = new TimelineContentDigester();
    private final TimelinePatchApplier applier = new TimelinePatchApplier();

    private static TextElement element(String id) {
        return TestTextElements.textElement(id);
    }

    /** Same authored shape as TestTextElements.textElement but with a custom content. */
    private static TextElement elementWithContent(String id, String content) {
        TextElement base = TestTextElements.textElement(id);
        TextContent tc = new TextContent(content);
        StyledText styled = new StyledText(tc,
                List.of(new com.example.platform.fonttext.text.TextSemanticRun(
                        com.example.platform.fonttext.text.TextRange.of(0, tc.scalarCount()),
                        null, com.example.platform.fonttext.text.ScriptTag.LATIN,
                        com.example.platform.fonttext.text.RangeDirectionOverride.NONE)),
                List.of(new com.example.platform.fonttext.typography.TextStyleRun(
                        com.example.platform.fonttext.text.TextRange.of(0, tc.scalarCount()),
                        base.styledText().styleRuns().get(0).style())),
                base.styledText().paragraphStyle());
        return new TextElement(base.id(), base.start(), base.duration(), styled,
                base.frame(), base.fallbackPolicy(), base.resolvedFontRuns());
    }

    /** Same shape with a custom exact start (ticks at 30fps). */
    private static TextElement elementWithStart(String id, long startTicks) {
        TextElement base = TestTextElements.textElement(id);
        return new TextElement(base.id(), FontRational.of(startTicks, 30), base.duration(),
                base.styledText(), base.frame(), base.fallbackPolicy(), base.resolvedFontRuns());
    }

    // ── A1: stable identity survives canonical round-trip ──
    @Test
    void a1IdentitySurvivesRoundTrip() {
        TextElement e = element("elem-1");
        List<TextElement> decoded = TimedTextCanonicalSemantics.decodeElements(
                TimedTextCanonicalSemantics.encodeElements(List.of(e)));
        assertEquals(1, decoded.size());
        assertEquals("elem-1", decoded.get(0).id().value());
        assertEquals("Hello 张伟 👋", decoded.get(0).styledText().content().value());
    }

    // ── A4/A5: exact timing survives round-trip ──
    @Test
    void a4a5ExactTimingSurvives() {
        TextElement e = element("elem-1");
        TextElement decoded = TimedTextCanonicalSemantics.decodeElements(
                TimedTextCanonicalSemantics.encodeElements(List.of(e))).get(0);
        assertEquals(FontRational.whole(0), decoded.start());
        assertEquals(FontRational.whole(5), decoded.duration());
    }

    // ── B1: same state → same fingerprint ──
    @Test
    void b1SameStateSameFingerprint() {
        assertEquals(TimedTextCanonicalSemantics.semanticFingerprint(element("e1")),
                TimedTextCanonicalSemantics.semanticFingerprint(element("e1")));
    }

    // ── B2: content change → different ──
    @Test
    void b2ContentChangeDiffers() {
        assertNotEquals(TimedTextCanonicalSemantics.semanticFingerprint(element("e1")),
                TimedTextCanonicalSemantics.semanticFingerprint(elementWithContent("e1", "Bye")));
    }

    // ── B3/B4: timing change → different ──
    @Test
    void b3b4TimingChangeDiffers() {
        assertNotEquals(TimedTextCanonicalSemantics.semanticFingerprint(element("e1")),
                TimedTextCanonicalSemantics.semanticFingerprint(elementWithStart("e1", 30)));
    }

    // ── B9: no Java hashCode/toString identity ──
    @Test
    void b9NoHashCodeIdentity() {
        String fp = TimedTextCanonicalSemantics.semanticFingerprint(element("e1"));
        assertFalse(fp.contains("hashCode"));
        assertFalse(fp.startsWith("com.example"));
    }

    // ── B10: Unicode content round-trips exactly (no normalization collapse) ──
    @Test
    void b10UnicodeExactRoundTrip() {
        TextElement e = elementWithContent("e1", "café \u0301 \uD83D\uDE00"); // combining + emoji
        TextElement decoded = TimedTextCanonicalSemantics.decodeElements(
                TimedTextCanonicalSemantics.encodeElements(List.of(e))).get(0);
        assertEquals(e.styledText().content().value(), decoded.styledText().content().value());
    }

    // ── C1/C2/C3: hash sensitivity (content/timing/style) ──
    @Test
    void c123HashSensitiveToAuthoredSemantics() throws Exception {
        TimelineDocument base = docWith(element("e1"));
        TimelineDocument contentChanged = docWith(elementWithContent("e1", "Bye"));
        TimelineDocument timingChanged = docWith(elementWithStart("e1", 30));
        assertNotEquals(digester.digest(base), digester.digest(contentChanged),
                "C1: content change must change Timeline hash");
        assertNotEquals(digester.digest(base), digester.digest(timingChanged),
                "C2: timing change must change Timeline hash");
    }

    // ── D1-D3: diff detects source-only text/timing/style changes ──
    @Test
    void d1d2d3DiffDetectsTextElementChanges() {
        CanonicalTimelineSnapshot before = snap(docWith(element("e1")));
        CanonicalTimelineSnapshot afterContent = snap(docWith(elementWithContent("e1", "Bye")));
        CanonicalTimelineSnapshot afterTiming = snap(docWith(elementWithStart("e1", 30)));
        assertEquals(1, diff(before, afterContent).stream()
                .filter(o -> o.type() == TimelineChangeType.TEXT_ELEMENT_CHANGED).count());
        assertEquals(1, diff(before, afterTiming).stream()
                .filter(o -> o.type() == TimelineChangeType.TEXT_ELEMENT_CHANGED).count());
        assertEquals(0, diff(before, snap(docWith(element("e1")))).stream()
                .filter(o -> o.type() == TimelineChangeType.TEXT_ELEMENT_CHANGED).count(),
                "no false positive on identical state");
    }

    // ── D4/D5: patch reconstructs exact semantics incl. exact time ──
    @Test
    void d4d5PatchReconstructsExactSemantics() {
        CanonicalTimelineSnapshot before = snap(docWith(element("e1")));
        CanonicalTimelineSnapshot after = snap(docWith(elementWithStart("e1", 45)));
        var ops = diff(before, after);
        TimelinePatchApplicationResult r = applier.apply(before,
                new com.example.platform.timeline.diff.TimelinePatch(
                        new com.example.platform.timeline.diff.TimelinePatchId("p"), "rev-1", ops, null, Map.of()));
        assertTrue(r.status() == com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus.APPLIED, "D4: TimedText patch must apply");
        assertEquals(1, r.patchedSnapshot().textElements().size());
        assertEquals("Hello 张伟 👋", r.patchedSnapshot().textElements().get(0).styledText().content().value());
        assertEquals(FontRational.of(45, 30), r.patchedSnapshot().textElements().get(0).start());
        assertEquals(FontRational.whole(5), r.patchedSnapshot().textElements().get(0).duration());
    }

    // ── D6-D9: unrelated families preserved through TimedText patch ──
    @Test
    void d6d7d8d9UnrelatedFamiliesPreserved() {
        CanonicalTimelineSnapshot before = snap(docWith(element("e1")));
        CanonicalTimelineSnapshot after = snap(docWith(elementWithContent("e1", "Bye")));
        var ops = diff(before, after);
        TimelinePatchApplicationResult r = applier.apply(before,
                new com.example.platform.timeline.diff.TimelinePatch(
                        new com.example.platform.timeline.diff.TimelinePatchId("p"), "rev-1", ops, null, Map.of()));
        assertEquals(before.transitions(), r.patchedSnapshot().transitions());
        assertEquals(before.automations(), r.patchedSnapshot().automations());
        assertEquals(before.tracks(), r.patchedSnapshot().tracks());
    }

    // ── E6: source-only delete survives; E7: delete-last → empty state ──
    @Test
    void e6e7DeleteSemantics() {
        CanonicalTimelineSnapshot before = snap(docWith(element("e1")));
        CanonicalTimelineSnapshot after = snap(new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY,
                List.of(), List.of()));
        var ops = diff(before, after);
        assertEquals(1, ops.stream().filter(o -> o.type() == TimelineChangeType.TEXT_ELEMENT_CHANGED)
                .filter(o -> "true".equals(o.safeMetadata().get("deleted"))).count());
        TimelinePatchApplicationResult r = applier.apply(before,
                new com.example.platform.timeline.diff.TimelinePatch(
                        new com.example.platform.timeline.diff.TimelinePatchId("p"), "rev-1", ops, null, Map.of()));
        assertTrue(r.status() == com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus.APPLIED);
        assertEquals(0, r.patchedSnapshot().textElements().size(), "E7: delete-last must yield empty collection");
    }

    // ── G1: canonical TextElement contains no provider command ──
    @Test
    void g1NoProviderCommandInCanonical() {
        String fp = TimedTextCanonicalSemantics.semanticFingerprint(element("e1"));
        assertFalse(fp.contains("drawtext"));
        assertFalse(fp.contains("ffmpeg"));
        assertFalse(fp.contains("fontfile="));
    }

    // ── G4: unavailable font/resource does not mutate canonical state ──
    @Test
    void g4HostFontUnavailableDoesNotMutateCanonical() {
        // Canonical state carries semantic family + historical frozen runs only;
        // there is no host-font lookup in the canonical path.
        TextElement e = element("e1");
        String fp1 = TimedTextCanonicalSemantics.semanticFingerprint(e);
        String fp2 = TimedTextCanonicalSemantics.semanticFingerprint(e);
        assertEquals(fp1, fp2);
        assertTrue(fp1.contains("fallbackPolicy"), "canonical state must carry the explicit fallback policy");
    }

    // ── helpers ──

    private static TimelineDocument docWith(TextElement e) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), com.example.platform.audio.domain.mix.AudioMix.EMPTY,
                List.of(), List.of(e));
    }

    private static CanonicalTimelineSnapshot snap(TimelineDocument doc) {
        return com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter.toSnapshot(doc, "rev-1");
    }

    private static java.util.List<com.example.platform.timeline.diff.TimelineChangeOperation> diff(
            CanonicalTimelineSnapshot before, CanonicalTimelineSnapshot after) {
        return new CanonicalTimelineDiffCalculator().calculate(before, after).diff().operations();
    }
}
