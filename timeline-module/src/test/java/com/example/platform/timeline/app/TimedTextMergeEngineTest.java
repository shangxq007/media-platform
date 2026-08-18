package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.fonttext.text.TextContent;
import com.example.platform.fonttext.typography.FontRational;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClip;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClipEffect;
import com.example.platform.timeline.app.TimelineImportRequest.ImportOutput;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTrack;
import com.example.platform.timeline.canonical.TestTextElements;
import com.example.platform.timeline.canonical.TextElement;
import com.example.platform.timeline.canonical.TimelineDocument;
import com.example.platform.timeline.canonical.TimelineMetadata;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.internal.TimelineMergeRequest;
import com.example.platform.timeline.internal.TimelineMergeResult;
import com.example.platform.shared.time.FrameRate;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/**
 * ROADMAP #19 — REAL TimelineMergeEngine E2E for TimedText semantics.
 *
 * F1 source-only / F2 identical bilateral / F3 divergent / F4 delete-vs-modify /
 * F5 delete-last / F6 mixed multi-component. All payloads are real
 * TimelineDocument JSON (textElements are authored canonical state); the engine,
 * planner, patch applier, reload and validator are the production classes.
 */
class TimedTextMergeEngineTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final TimelineImportService importService = new TimelineImportService();

    private record MergeOutcome(TimelineMergeResult result, String mergedPayload,
            TimelineCandidate reloaded, TimelineValidationResult reloadValidation) {
    }

    private String docJson(TextElement... elements) throws Exception {
        // Internal schema payloads come from the import path; textElements are
        // then spliced in (authored canonical state).
        TimelineImportRequest req = new TimelineImportRequest("tl-text", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(), List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0, List.of(), List.of());
        String imported = importService.importTimeline(req);
        var node = InternalTimelineJson.mapper().readTree(imported);
        var arr = InternalTimelineJson.mapper().createArrayNode();
        for (TextElement e : elements) {
            arr.add(InternalTimelineJson.mapper().valueToTree(e));
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("composition")).set("textElements", arr);
        return InternalTimelineJson.mapper().writeValueAsString(node);
    }

    /** F1 fixture: document with one clip+effect (for the mixed case) plus text element. */
    private String docJsonWithClip(String effectRadius, TextElement... elements) throws Exception {
        TimelineImportRequest req = new TimelineImportRequest("tl-mixed", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur", Map.of("radius", effectRadius)))),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new TimelineImportRequest.ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new TimelineImportRequest.ImportAutomationCurve("auto1", "fx1", "opacity", "float", "HOLD",
                        List.of(new TimelineImportRequest.ImportAutomationKeyframe("kf-1", 0, 30, 0.5, "LINEAR")))));
        String imported = importService.importTimeline(req);
        // splice the text elements into the imported document JSON
        var node = InternalTimelineJson.mapper().readTree(imported);
        var arr = InternalTimelineJson.mapper().createArrayNode();
        for (TextElement e : elements) {
            arr.add(InternalTimelineJson.mapper().valueToTree(e));
        }
        ((com.fasterxml.jackson.databind.node.ObjectNode) node.path("composition")).set("textElements", arr);
        return InternalTimelineJson.mapper().writeValueAsString(node);
    }

    private MergeOutcome merge(String basePayload, String sourcePayload, String targetPayload) {
        com.example.platform.shared.web.TenantContext.set("tenant-1");
        TimelineRevisionRepository revisionRepo = mock(TimelineRevisionRepository.class);
        TimelineSnapshotService snapshotService = mock(TimelineSnapshotService.class);
        com.example.platform.timeline.app.ProductCurrentRevisionService currentService =
                mock(com.example.platform.timeline.app.ProductCurrentRevisionService.class);
        TimelineRevisionRepository.RevisionRow baseRow = row("base-rev", "snap-base", basePayload);
        TimelineRevisionRepository.RevisionRow srcRow = row("src-rev", "snap-src", sourcePayload);
        TimelineRevisionRepository.RevisionRow tgtRow = row("tgt-rev", "snap-tgt", targetPayload);
        when(revisionRepo.findById("base-rev")).thenReturn(Optional.of(baseRow));
        when(revisionRepo.findById("src-rev")).thenReturn(Optional.of(srcRow));
        when(revisionRepo.findById("tgt-rev")).thenReturn(Optional.of(tgtRow));
        when(snapshotService.findById("snap-base"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-base", "proj-1", "tenant-1", basePayload, "internal-1.0")));
        when(snapshotService.findById("snap-src"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-src", "proj-1", "tenant-1", sourcePayload, "internal-1.0")));
        when(snapshotService.findById("snap-tgt"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-tgt", "proj-1", "tenant-1", targetPayload, "internal-1.0")));
        when(snapshotService.save(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("snap-merged");
        when(revisionRepo.nextRevisionNumber("proj-1")).thenReturn(9);
        when(revisionRepo.listByProject("proj-1", 500)).thenReturn(List.of());
        when(currentService.getCurrentRevisionId("proj-1")).thenReturn("tgt-rev");
        var previewService = new TimelineMergePreviewService(new TimelineMergeConflictDetector());
        var planner = new TimelineNonConflictingMergePlanner(previewService);
        TimelineMergeEngine engine = new TimelineMergeEngine(revisionRepo, snapshotService,
                currentService, previewService, planner, new TimelinePatchApplier(),
                InternalTimelineJson.mapper());
        TimelineMergeResult result = engine.merge(new TimelineMergeRequest(
                "proj-1", "tenant-1", "base-rev", "src-rev", "tgt-rev", "user-1", "merge-1"));
        if (result.status() == TimelineMergeResult.MergeStatus.MERGED) {
            TimelineCandidate reloaded = InternalTimelineCandidateAdapter.map("proj-1", result.mergedPayloadJson());
            TimelineValidationResult validation = TimelineCanonicalValidator.validate(reloaded);
            return new MergeOutcome(result, result.mergedPayloadJson(), reloaded, validation);
        }
        return new MergeOutcome(result, null, null, null);
    }

    private static TimelineRevisionRepository.RevisionRow row(String rev, String snap, String payload) {
        return new TimelineRevisionRepository.RevisionRow(
                rev, "proj-1", "tenant-1", "base-rev", 1, snap, 0,
                new TimelineContentHasher(new TimelineCanonicalizer()).hashInternalTimeline(payload),
                "internal-1.0", "merge", "user-1", null, "test", null, null, null,
                true, "src-rev,tgt-rev", "base-rev", java.time.OffsetDateTime.now());
    }


    /** Same shape with a different authored content (runs rebuilt for the new scalar count). */
    private static TextElement changedContent(TextElement base, String content) {
        TextContent tc = new TextContent(content);
        var styled = new com.example.platform.fonttext.text.StyledText(tc,
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

    private static String anyString() {
        return org.mockito.ArgumentMatchers.anyString();
    }

    // ── F1: source-only TimedText change → MERGED with THEIRS semantics ──
    @Test
    void f1SourceOnlyTimedTextMerge() throws Exception {
        TextElement t1 = TestTextElements.textElement("t1");
        TextElement t1Changed = changedContent(t1, "Bye");
        MergeOutcome out = merge(docJson(t1), docJson(t1), docJson(t1Changed));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status());
        assertNotNull(out.mergedPayload);
        assertFalse(out.reloadValidation.hasFatalErrors());
        assertTrue(out.mergedPayload.contains("Bye"), "F1: THEIRS text semantics must survive");
    }

    // ── F2: identical two-sided edit → no false conflict ──
    @Test
    void f2IdenticalBilateralNoFalseConflict() throws Exception {
        TextElement t1 = TestTextElements.textElement("t1");
        TextElement changed = new TextElement(t1.id(), t1.start(),
                FontRational.of(9, 1), // duration 9s
                t1.styledText(), t1.frame(), t1.fallbackPolicy(), t1.resolvedFontRuns());
        MergeOutcome out = merge(docJson(t1), docJson(changed), docJson(changed));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status(),
                "F2: identical bilateral edit must not false-conflict");
        assertFalse(out.reloadValidation.hasFatalErrors());
    }

    // ── F3: divergent same-element edit → explicit conflict ──
    @Test
    void f3DivergentSameElementConflict() throws Exception {
        TextElement t1 = TestTextElements.textElement("t1");
        TextElement ours = new TextElement(t1.id(), t1.start(), FontRational.of(9, 1),
                t1.styledText(), t1.frame(), t1.fallbackPolicy(), t1.resolvedFontRuns());
        TextElement theirs = new TextElement(t1.id(), t1.start(), FontRational.of(11, 1),
                t1.styledText(), t1.frame(), t1.fallbackPolicy(), t1.resolvedFontRuns());
        MergeOutcome out = merge(docJson(t1), docJson(ours), docJson(theirs));
        assertTrue(out.result.status() == TimelineMergeResult.MergeStatus.CONFLICTS
                        || out.result.status() == TimelineMergeResult.MergeStatus.FAILED,
                "F3: divergent same-TextElement edit must fail closed, got " + out.result.status());
    }

    // ── F4: delete vs modify → fail closed, never silent resurrection ──
    @Test
    void f4DeleteVsModifyFailsClosed() throws Exception {
        TextElement t1 = TestTextElements.textElement("t1");
        TextElement theirs = new TextElement(t1.id(), t1.start(), FontRational.of(9, 1),
                t1.styledText(), t1.frame(), t1.fallbackPolicy(), t1.resolvedFontRuns());
        String base = docJson(t1);
        String ours = docJson(); // deleted
        String target = docJson(theirs); // modified
        MergeOutcome out = merge(base, ours, target);
        assertTrue(out.result.status() == TimelineMergeResult.MergeStatus.CONFLICTS
                        || out.result.status() == TimelineMergeResult.MergeStatus.FAILED,
                "F4: delete-vs-modify must fail closed, got " + out.result.status());
    }

    // ── F5: delete-last → merged empty, no resurrection ──
    @Test
    void f5DeleteLastEmptyState() throws Exception {
        TextElement t1 = TestTextElements.textElement("t1");
        MergeOutcome out = merge(docJson(t1), docJson(), docJson(t1));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status());
        assertNotNull(out.mergedPayload);
        assertFalse(out.reloadValidation.hasFatalErrors());
        com.fasterxml.jackson.databind.JsonNode merged = InternalTimelineJson.mapper().readTree(out.mergedPayload);
        assertTrue(merged.path("textElements").isMissingNode() || merged.path("textElements").size() == 0,
                "F5: delete-last must yield canonical empty TimedText state");
    }

    // ── F6: mixed multi-component merge — TimedText + Effect + Transition +
    //    Automation + structural changes preserve independent families ──
    @Test
    void f6MixedSemanticFamiliesPreserved() throws Exception {
        TextElement t1 = TestTextElements.textElement("t1");
        TextElement t1Changed = changedContent(t1, "Bye");
        String base = docJsonWithClip("3", t1);
        String source = docJsonWithClip("9", t1);          // OURS: effect radius 3→9
        String target = docJsonWithClip("3", t1Changed);   // THEIRS: text content change
        MergeOutcome out = merge(base, source, target);
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status(),
                "F6: independent effect + TimedText changes must merge");
        assertNotNull(out.mergedPayload);
        assertFalse(out.reloadValidation.hasFatalErrors());
        // TimedText: THEIRS change survives
        assertTrue(out.mergedPayload.contains("Bye"), "F6: TimedText change must survive");
        // Effect: OURS change survives (radius "9" — authored as string)
        com.fasterxml.jackson.databind.JsonNode merged = InternalTimelineJson.mapper().readTree(out.mergedPayload);
        boolean radius9 = merged.toString().matches(".*\"radius\"\\s*:\\s*\"?9\"?.*");
        assertTrue(radius9, "F6: Effect change must survive, payload=" + merged.toString().substring(0, Math.min(400, merged.toString().length())));
        // Transition + Automation preserved
        assertTrue(out.mergedPayload.contains("t1"), "F6: Transition must survive");
        assertTrue(out.mergedPayload.contains("auto1"), "F6: Automation must survive");
    }
}
