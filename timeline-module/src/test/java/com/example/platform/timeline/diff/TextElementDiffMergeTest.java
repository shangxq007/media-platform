package com.example.platform.timeline.diff;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TextElementId;
import com.example.platform.timeline.canonical.TestTextElements;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** ROADMAP_19 (C58/C59): TextElement semantic diff + TimelineMergeEngine-owned merge conflicts. */
class TextElementDiffMergeTest {

    private final CanonicalTimelineDiffCalculator diffCalculator = new CanonicalTimelineDiffCalculator();
    private final TimelineMergeConflictDetector detector = new TimelineMergeConflictDetector(diffCalculator);

    private TimelineDocument doc(TextElement... elements) {
        return new TimelineDocument(TimelineDocument.CURRENT_SCHEMA_VERSION,
                List.of(), TimelineMetadata.empty(), AudioMix.EMPTY, List.of(), List.of(elements));
    }

    private TimelineDocument baseDoc() {
        return doc(TestTextElements.textElement("elem-a"));
    }

    private CanonicalTimelineSnapshot snap(TimelineDocument d, String rev) {
        return TimelineSnapshotConverter.toSnapshot(d, rev);
    }

    @Test
    void diffDetectsTextElementAddedRemovedChanged() {
        TextElement a = TestTextElements.textElement("elem-a");
        TextElement b = TestTextElements.textElement("elem-b");
        TimelineDocument base = baseDoc();
        TimelineDocument added = doc(a, b);

        var addResult = diffCalculator.calculate(snap(base, "r0"), snap(added, "r1"));
        assertTrue(addResult.successful());
        assertTrue(addResult.diff().operations().stream()
                        .anyMatch(op -> op.type() == TimelineChangeType.TEXT_ELEMENT_CHANGED),
                "TextElement add must produce TEXT_ELEMENT_CHANGED");

        var removeResult = diffCalculator.calculate(snap(added, "r1"), snap(base, "r2"));
        assertTrue(removeResult.diff().operations().stream()
                        .anyMatch(op -> op.type() == TimelineChangeType.TEXT_ELEMENT_CHANGED),
                "TextElement remove must produce TEXT_ELEMENT_CHANGED");

        // changed content: same id, different text
        TextElement changed = TestTextElements.textElement("elem-a");
        // build a changed variant: different duration (semantic change)
        TextElement changedVariant = new TextElement(changed.id(), changed.start(),
                com.example.platform.fonttext.typography.FontRational.whole(9),
                changed.styledText(), changed.frame(), changed.fallbackPolicy(),
                changed.resolvedFontRuns());
        var changeResult = diffCalculator.calculate(snap(base, "r0"), snap(doc(changedVariant), "r1"));
        assertTrue(changeResult.diff().operations().stream()
                        .anyMatch(op -> op.type() == TimelineChangeType.TEXT_ELEMENT_CHANGED),
                "TextElement semantic change must produce TEXT_ELEMENT_CHANGED");
    }

    @Test
    void independentTextElementChangesMergeWithoutConflict() {
        TimelineDocument base = baseDoc();
        TextElement a = TestTextElements.textElement("elem-a");
        TextElement b = TestTextElements.textElement("elem-b");

        // ours: adds elem-b; theirs: modifies elem-a duration
        TextElement aChanged = new TextElement(a.id(), a.start(),
                com.example.platform.fonttext.typography.FontRational.whole(9),
                a.styledText(), a.frame(), a.fallbackPolicy(), a.resolvedFontRuns());
        CanonicalTimelineSnapshot ours = snap(doc(a, b), "r1");
        CanonicalTimelineSnapshot theirs = snap(doc(aChanged), "r2");

        var analysis = detector.analyze(snap(base, "r0"), ours, theirs);
        assertFalse(analysis.hasConflicts(), "independent TextElement changes must not conflict");
    }

    @Test
    void sameTextElementDivergentChangeConflicts() {
        TimelineDocument base = baseDoc();
        TextElement a = TestTextElements.textElement("elem-a");

        TextElement oursVariant = new TextElement(a.id(), a.start(),
                com.example.platform.fonttext.typography.FontRational.whole(9),
                a.styledText(), a.frame(), a.fallbackPolicy(), a.resolvedFontRuns());
        TextElement theirsVariant = new TextElement(a.id(), a.start(),
                com.example.platform.fonttext.typography.FontRational.whole(11),
                a.styledText(), a.frame(), a.fallbackPolicy(), a.resolvedFontRuns());

        var analysis = detector.analyze(snap(base, "r0"),
                snap(doc(oursVariant), "r1"), snap(doc(theirsVariant), "r2"));
        assertTrue(analysis.hasConflicts(), "divergent same-TextElement change must conflict deterministically");
    }

    @Test
    void removeVsModifySameElementConflicts() {
        TimelineDocument base = baseDoc();
        TextElement a = TestTextElements.textElement("elem-a");
        TextElement oursVariant = new TextElement(a.id(), a.start(),
                com.example.platform.fonttext.typography.FontRational.whole(9),
                a.styledText(), a.frame(), a.fallbackPolicy(), a.resolvedFontRuns());
        var analysis = detector.analyze(snap(base, "r0"),
                snap(doc(oursVariant), "r1"), snap(doc(), "r2"));
        assertTrue(analysis.hasConflicts(), "remove vs modify same TextElement must conflict");
    }

    @Test
    void identicalChangeMerges() {
        TimelineDocument base = baseDoc();
        TextElement a = TestTextElements.textElement("elem-a");
        TextElement changed = new TextElement(a.id(), a.start(),
                com.example.platform.fonttext.typography.FontRational.whole(9),
                a.styledText(), a.frame(), a.fallbackPolicy(), a.resolvedFontRuns());
        var analysis = detector.analyze(snap(base, "r0"),
                snap(doc(changed), "r1"), snap(doc(changed), "r2"));
        assertFalse(analysis.hasConflicts(), "identical changes must merge");
    }

    @Test
    void unicodeEqualityDistinguishesNormalizationForms() {
        // U+00E9 vs U+0065 U+0301 remain distinct authored sequences (C10)
        com.example.platform.fonttext.text.TextContent composed = new com.example.platform.fonttext.text.TextContent("\u00e9");
        com.example.platform.fonttext.text.TextContent decomposed = new com.example.platform.fonttext.text.TextContent("e\u0301");
        assertNotEquals(composed, decomposed);
    }
}
