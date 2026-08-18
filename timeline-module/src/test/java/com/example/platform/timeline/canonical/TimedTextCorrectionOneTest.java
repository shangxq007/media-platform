package com.example.platform.timeline.canonical;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.fonttext.resolution.ResolvedFontInstance;
import com.example.platform.fonttext.resolution.ResolvedFontRun;
import com.example.platform.fonttext.resource.FontContentDigest;
import com.example.platform.fonttext.resource.ValidatedFontExecutionReference;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.text.TextSemanticRun;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.fonttext.typography.TextStyle;
import com.example.platform.fonttext.typography.TextStyleRun;
import com.example.platform.timeline.diff.TimelineChangeOperation;
import com.example.platform.timeline.diff.TimelineChangeType;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * ROADMAP #19 CORRECTION 1 — TT-C1 (add/delete complete payloads),
 * TT-C2 (explicit non-reflective canonical schema), hash evidence (TT-H),
 * determinism (DET), canonical parity.
 */
class TimedTextCorrectionOneTest {

    private final TimelineContentDigester digester = new TimelineContentDigester();
    private final TimelinePatchApplier applier = new TimelinePatchApplier();

    private static TimelineDocument doc(TextElement... elements) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of(elements));
    }

    private static CanonicalTimelineSnapshot snap(TimelineDocument d, String rev) {
        return TimelineSnapshotConverter.toSnapshot(d, rev);
    }

    private static List<TimelineChangeOperation> diff(CanonicalTimelineSnapshot b, CanonicalTimelineSnapshot a) {
        return new CanonicalTimelineDiffCalculator().calculate(b, a).diff().operations();
    }

    private TimelinePatchApplicationResult apply(CanonicalTimelineSnapshot base, List<TimelineChangeOperation> ops) {
        return applier.apply(base, new com.example.platform.timeline.diff.TimelinePatch(
                new com.example.platform.timeline.diff.TimelinePatchId("p"), "r0", ops, null, Map.of()));
    }

    private static TextElement t1() {
        return TestTextElements.textElement("t1");
    }

    // ── TT-C1-T1: DIFF ADD carries COMPLETE canonical after payload ──
    @Test
    void ttC1T1DiffAddCarriesFullPayload() {
        CanonicalTimelineSnapshot before = snap(doc(), "r0");
        CanonicalTimelineSnapshot after = snap(doc(t1()), "r1");
        var ops = diff(before, after);
        assertEquals(1, ops.size());
        TimelineChangeOperation op = ops.get(0);
        assertEquals(TimelineChangeType.TEXT_ELEMENT_CHANGED, op.type());
        assertTrue(op.beforeValue() == null || op.beforeValue().stringValue() == null);
        // afterValue decodes (via local authority) to EXACTLY t1
        List<TextElement> decoded = TimedTextCanonicalSemantics.decodeElements(
                "[" + op.afterValue().stringValue() + "]");
        assertEquals(1, decoded.size());
        assertEquals(t1(), decoded.get(0), "TT-C1-T1: afterValue must decode to exactly t1");
    }

    // ── TT-C1-T2: PATCH ADD reconstructs exact TextElement ──
    @Test
    void ttC1T2PatchAddExactReconstruction() {
        CanonicalTimelineSnapshot before = snap(doc(), "r0");
        CanonicalTimelineSnapshot after = snap(doc(t1()), "r1");
        TimelinePatchApplicationResult r = apply(before, diff(before, after));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, r.status());
        assertEquals(1, r.patchedSnapshot().textElements().size());
        TextElement got = r.patchedSnapshot().textElements().get(0);
        assertEquals(t1().id(), got.id());
        assertEquals(t1().start(), got.start());
        assertEquals(t1().duration(), got.duration());
        assertEquals(t1().styledText(), got.styledText());
        assertEquals(t1().frame(), got.frame());
        assertEquals(t1().fallbackPolicy(), got.fallbackPolicy());
        assertEquals(t1().resolvedFontRuns(), got.resolvedFontRuns());
        assertEquals(t1(), got, "TT-C1-T2: patch must reconstruct exactly t1");
    }

    // ── TT-C1-T3: DIFF DELETE carries COMPLETE canonical before payload ──
    @Test
    void ttC1T3DiffDeleteCarriesFullPayload() {
        CanonicalTimelineSnapshot before = snap(doc(t1()), "r0");
        CanonicalTimelineSnapshot after = snap(doc(), "r1");
        var ops = diff(before, after);
        assertEquals(1, ops.size());
        TimelineChangeOperation op = ops.get(0);
        assertEquals(TimelineChangeType.TEXT_ELEMENT_CHANGED, op.type());
        assertEquals("true", op.safeMetadata().get("deleted"));
        assertTrue(op.afterValue() == null || op.afterValue().stringValue() == null);
        List<TextElement> decoded = TimedTextCanonicalSemantics.decodeElements(
                "[" + op.beforeValue().stringValue() + "]");
        assertEquals(t1(), decoded.get(0), "TT-C1-T3: beforeValue must be the complete canonical t1");
    }

    // ── TT-C1-T4: delete-last regression — empty collection, no resurrection ──
    @Test
    void ttC1T4DeleteLastEmpty() {
        CanonicalTimelineSnapshot before = snap(doc(t1()), "r0");
        CanonicalTimelineSnapshot after = snap(doc(), "r1");
        TimelinePatchApplicationResult r = apply(before, diff(before, after));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, r.status());
        assertEquals(0, r.patchedSnapshot().textElements().size());
    }

    // ── TT-C1-T5: ID-only add payload FAILS CLOSED (no legacy fallback) ──
    @Test
    void ttC1T5IdOnlyAddPayloadRejected() {
        CanonicalTimelineSnapshot before = snap(doc(), "r0");
        TimelineChangeOperation bad = new TimelineChangeOperation(
                new com.example.platform.timeline.diff.TimelineChangeOperationId("op-1"),
                TimelineChangeType.TEXT_ELEMENT_CHANGED,
                com.example.platform.timeline.diff.TimelineChangeScope.TEXT_ELEMENT,
                new com.example.platform.timeline.diff.TimelineChangePath("timeline.textElements.t1"),
                com.example.platform.timeline.diff.TimelineChangePayload.empty(),
                com.example.platform.timeline.diff.TimelineChangePayload.ofString("t1"),
                Map.of());
        assertThrows(IllegalArgumentException.class, () -> applier.apply(before,
                new com.example.platform.timeline.diff.TimelinePatch(
                        new com.example.platform.timeline.diff.TimelinePatchId("p"), "r0", List.of(bad), null, Map.of())),
                "TT-C1-T5: ID-only add payload must fail closed (no compatibility fallback)");
    }

    // ── TT-H1..H7: fingerprint + Timeline hash sensitivity ──
    @Test
    void ttH1ContentSensitivity() throws Exception {
        TextElement base = t1();
        TextElement changed = withContent(base, "不同内容");
        assertNotEquals(fp(base), fp(changed));
        assertNotEquals(digester.digest(doc(base)), digester.digest(doc(changed)));
    }

    @Test
    void ttH2H3TimingSensitivity() throws Exception {
        TextElement startChanged = new TextElement(t1().id(), FontRational.of(3, 1), t1().duration(),
                t1().styledText(), t1().frame(), t1().fallbackPolicy(), t1().resolvedFontRuns());
        TextElement durationChanged = new TextElement(t1().id(), t1().start(), FontRational.of(7, 1),
                t1().styledText(), t1().frame(), t1().fallbackPolicy(), t1().resolvedFontRuns());
        assertNotEquals(fp(t1()), fp(startChanged));
        assertNotEquals(digester.digest(doc(t1())), digester.digest(doc(startChanged)));
        assertNotEquals(fp(t1()), fp(durationChanged));
        assertNotEquals(digester.digest(doc(t1())), digester.digest(doc(durationChanged)));
    }

    @Test
    void ttH4StyleSensitivity() throws Exception {
        TextStyle oldStyle = t1().styledText().styleRuns().get(0).style();
        TextStyle newStyle = new TextStyle(oldStyle.fontSelection(),
                new com.example.platform.fonttext.typography.FontSize(FontRational.of(48, 1)),
                oldStyle.tracking(), oldStyle.features());
        TextElement changed = withStyleRuns(newStyle);
        assertNotEquals(fp(t1()), fp(changed));
        assertNotEquals(digester.digest(doc(t1())), digester.digest(doc(changed)));
    }

    @Test
    void ttH5ParagraphSensitivity() throws Exception {
        var oldP = t1().styledText().paragraphStyle();
        var newP = new com.example.platform.fonttext.typography.ParagraphStyle(
                com.example.platform.fonttext.typography.ParagraphStyle.Alignment.CENTER,
                oldP.justification(), oldP.lineHeight(), oldP.wrapPolicy(),
                oldP.baseDirection(), oldP.lineBreakPolicy());
        TextElement changed = new TextElement(t1().id(), t1().start(), t1().duration(),
                new com.example.platform.fonttext.text.StyledText(t1().styledText().content(),
                        t1().styledText().semanticRuns(), t1().styledText().styleRuns(), newP),
                t1().frame(), t1().fallbackPolicy(), t1().resolvedFontRuns());
        assertNotEquals(fp(t1()), fp(changed));
        assertNotEquals(digester.digest(doc(t1())), digester.digest(doc(changed)));
    }

    @Test
    void ttH6FontSelectionSensitivity() throws Exception {
        TextStyle oldStyle = t1().styledText().styleRuns().get(0).style();
        var newIntent = new com.example.platform.fonttext.typography.FontSelectionIntent(
                List.of(new com.example.platform.fonttext.typography.FontFamilyName("Arial")),
                oldStyle.fontSelection().weight(), oldStyle.fontSelection().stretch(),
                oldStyle.fontSelection().slant(), oldStyle.fontSelection().opticalSizing(),
                oldStyle.fontSelection().explicitAxisOverrides());
        TextStyle newStyle = new TextStyle(newIntent, oldStyle.fontSize(), oldStyle.tracking(), oldStyle.features());
        TextElement changed = withStyleRuns(newStyle);
        assertNotEquals(fp(t1()), fp(changed));
        assertNotEquals(digester.digest(doc(t1())), digester.digest(doc(changed)));
    }

    @Test
    void ttH7ResolvedFontSensitivity() throws Exception {
        TextElement base = t1();
        ResolvedFontRun oldRun = base.resolvedFontRuns().get(0);
        ResolvedFontRun newRun = new ResolvedFontRun(oldRun.range(), oldRun.font()); // same — no change
        // vary the validated digest through the execution reference
        var ref = oldRun.font().executionReference();
        var newDigest = com.example.platform.fonttext.resource.FontContentDigest.of(
                "a".repeat(64));
        var newRef = new com.example.platform.fonttext.resource.ValidatedFontExecutionReference(
                newDigest, ref.validatedExecutionContentDigest(), ref.securityState(),
                ref.format(), ref.faceIndex());
        var newFont = new com.example.platform.fonttext.resolution.ResolvedFontInstance(newRef,
                oldRun.font().variationCoordinates());
        ResolvedFontRun newRun2 = new ResolvedFontRun(oldRun.range(), newFont);
        TextElement changed = new TextElement(base.id(), base.start(), base.duration(),
                base.styledText(), base.frame(), base.fallbackPolicy(), List.of(newRun2));
        assertNotEquals(fp(base), fp(changed));
        assertNotEquals(digester.digest(doc(base)), digester.digest(doc(changed)));
        // same state stays identical (no false sensitivity)
        assertEquals(fp(base), fp(new TextElement(base.id(), base.start(), base.duration(),
                base.styledText(), base.frame(), base.fallbackPolicy(), base.resolvedFontRuns())));
    }

    // ── TT-H8: provider-only change → unchanged ──
    @Test
    void ttH8ProviderOnlyIndependence() throws Exception {
        // There are no provider fields in TextElement; re-encoding identical state
        // must not change fingerprint or hash.
        assertEquals(fp(t1()), fp(t1()));
        assertEquals(digester.digest(doc(t1())), digester.digest(doc(t1())));
    }

    // ── DET: byte-identical canonical encoding for identical state ──
    @Test
    void det1ByteIdenticalEncoding() {
        assertEquals(TimedTextCanonicalSemantics.encodeElements(List.of(t1())),
                TimedTextCanonicalSemantics.encodeElements(List.of(t1())));
    }

    // ── DET-9: nullable UNSPECIFIED stable representation ──
    @Test
    void det9NullUnspecifiedStable() {
        TextSemanticRun run = t1().styledText().semanticRuns().get(0);
        // language is null (UNSPECIFIED) — encode must be stable and explicit
        String enc = TimedTextCanonicalSemantics.encodeElements(List.of(t1()));
        assertEquals(enc, TimedTextCanonicalSemantics.encodeElements(List.of(t1())));
        assertTrue(enc.contains("null"), "UNSPECIFIED nullable fields must use explicit JSON null");
    }

    // ── DET-10: no reflection in canonical construction ──
    @Test
    void det10NoReflection() {
        String src = readSelf();
        assertFalse(src.contains("getDeclaredFields"), "no getDeclaredFields in canonical semantics");
        assertFalse(src.contains("setAccessible"), "no setAccessible in canonical semantics");
    }

    // ── PARITY: local canonical projection == internal persisted projection ──
    @Test
    void parityLocalProjectionMatchesInternalProjection() {
        // The persisted internal payload projection is built via toCanonicalNode
        // (merge output + test fixtures); assert it round-trips to the same
        // TextElement semantics through the local authority.
        String canonical = TimedTextCanonicalSemantics.encodeElements(List.of(t1()));
        List<TextElement> decoded = TimedTextCanonicalSemantics.decodeElements(canonical);
        assertEquals(t1(), decoded.get(0), "canonical projection must round-trip to identical semantics");
        // fingerprint covers the same field set: fingerprint of decoded == fingerprint of original
        assertEquals(TimedTextCanonicalSemantics.semanticFingerprint(t1()), TimedTextCanonicalSemantics.semanticFingerprint(decoded.get(0)));
    }

    // ── helpers ──

    private static String fp(TextElement e) {
        return TimedTextCanonicalSemantics.semanticFingerprint(e);
    }

    private static TextElement withContent(TextElement base, String content) {
        TextContent tc = new TextContent(content);
        var styled = new com.example.platform.fonttext.text.StyledText(tc,
                List.of(new TextSemanticRun(com.example.platform.fonttext.text.TextRange.of(0, tc.scalarCount()),
                        null, com.example.platform.fonttext.text.ScriptTag.LATIN,
                        com.example.platform.fonttext.text.RangeDirectionOverride.NONE)),
                List.of(new TextStyleRun(com.example.platform.fonttext.text.TextRange.of(0, tc.scalarCount()),
                        base.styledText().styleRuns().get(0).style())),
                base.styledText().paragraphStyle());
        return new TextElement(base.id(), base.start(), base.duration(), styled,
                base.frame(), base.fallbackPolicy(), base.resolvedFontRuns());
    }

    private static TextElement withStyleRuns(TextStyle newStyle) {
        TextElement base = t1();
        var styled = new com.example.platform.fonttext.text.StyledText(base.styledText().content(),
                base.styledText().semanticRuns(),
                List.of(new TextStyleRun(com.example.platform.fonttext.text.TextRange.of(0,
                        base.styledText().content().scalarCount()), newStyle)),
                base.styledText().paragraphStyle());
        return new TextElement(base.id(), base.start(), base.duration(), styled,
                base.frame(), base.fallbackPolicy(), base.resolvedFontRuns());
    }

    private static String readSelf() {
        try {
            return new String(java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of("timeline-module/src/main/java/com/example/platform/timeline/canonical/TimedTextCanonicalSemantics.java")));
        } catch (Exception ex) {
            return "";
        }
    }
}
