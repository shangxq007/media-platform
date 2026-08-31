package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.app.TimelineImportRequest.ImportAutomationCurve;
import com.example.platform.timeline.app.TimelineImportRequest.ImportAutomationKeyframe;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClip;
import com.example.platform.timeline.app.TimelineImportRequest.ImportClipEffect;
import com.example.platform.timeline.app.TimelineImportRequest.ImportOutput;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTrack;
import com.example.platform.timeline.app.TimelineImportRequest.ImportTransition;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.timeline.app.TimelineCanonicalizer;
import com.example.platform.timeline.diff.merge.TimelineMergeResult;
import com.example.platform.timeline.app.TimelineCanonicalRejectionException;
import com.example.platform.timeline.diff.merge.TimelineMergeRequest;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * TIMELINE_EFFECT_TRANSITION_CANONICALIZATION_V1 — FOURTH CORRECTION:
 * TRUE END-TO-END TimelineMergeEngine proof.
 *
 * Every case runs the ACTUAL production sequence:
 * revision payloads -> canonical gate -> production diff -> conflict analysis
 * -> merge plan -> production patch/apply -> TimelineMergeEngine -> merged
 * internal payload -> reload through canonical adapter/gate -> assert.
 *
 * E2E-M1/M2/M3 source-only semantic merges, E2E-R1/R2 delete-last,
 * E2E-C1 divergent conflict (no silent resolution), E2E-X1 delete-Clip vs
 * Transition (fail closed), XV1-XV3 aggregate validation.
 */
class EffectTransitionEndToEndMergeTest {

    private final TimelineImportService importService = new TimelineImportService();

    // ── fixtures ──

    private static ImportTrack fullTrack(String clipId, String effectParamValue) {
        return new ImportTrack("v1", "VIDEO", 0, List.of(
                new ImportClip(clipId, "ast_1", "file:///a.mp4", 1920, 1080,
                        0.0, 2.0, 0.0, 2.0,
                        List.of(new ImportClipEffect("fx1", "blur",
                                Map.of("radius", Integer.valueOf(effectParamValue))))),
                new ImportClip(clipId + "-2", "ast_2", "file:///b.mp4", 1920, 1080,
                        2.0, 4.0, 0.0, 2.0, List.of())));
    }

    private static TimelineImportRequest base(String timelineId) {
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", "3")),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
    }

    private static TimelineImportRequest withEffectRadius(String timelineId, String radius) {
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", radius)),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
    }

    private static TimelineImportRequest withTransitionDuration(String timelineId, long ticks, long scale) {
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", "3")),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        ticks, scale, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
    }

    private static TimelineImportRequest withAutomationValue(String timelineId, double v) {
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", "3")),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, v, "LINEAR")))));
    }

    private static TimelineImportRequest withoutSemantics(String timelineId) {
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", "3")),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0, List.of(), List.of());
    }

    private static TimelineImportRequest withoutTransitionOnly(String timelineId) {
        // SOURCE: deletes the transition only; automation remains.
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", "3")),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0, List.of(),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
    }

    private static TimelineImportRequest withoutAutomationOnly(String timelineId) {
        // SOURCE: deletes the automation only; transition remains.
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", "3")),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of());
    }

    private static TimelineImportRequest withoutClipC1(String timelineId) {
        // OURS: delete Clip c1 (transition t1 references it) — this request is
        // INVALID by construction and must fail closed at the canonical gate.
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1-2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
    }

    private static TimelineImportRequest withoutClipC1AndTransition(String timelineId) {
        // OURS: delete Clip c1 AND the transition referencing it AND the
        // automation targeting fx1 (hosted on c1) — a locally consistent
        // delete (valid aggregate: no dangling references of any kind).
        return new TimelineImportRequest(timelineId, "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1-2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0, List.of(), List.of());
    }

    // ── engine harness (real production path) ──

    /**
     * Runs the actual production merge sequence for base/source/target payloads
     * and returns the merged internal payload + reloaded semantic state.
     */
    private record MergeOutcome(
            TimelineMergeResult result,
            String mergedPayload,
            TimelineCandidate reloaded,
            TimelineValidationResult reloadValidation) {
    }

    private MergeOutcome merge(TimelineImportRequest baseReq, TimelineImportRequest sourceReq,
            TimelineImportRequest targetReq) {
        com.example.platform.shared.web.TenantContext.set("tenant-1");
        String base = canonicalPayload(importService.importTimeline(baseReq));
        String source = canonicalPayload(importService.importTimeline(sourceReq));
        String target = canonicalPayload(importService.importTimeline(targetReq));
        TimelineRevisionRepository revisionRepo = mock(TimelineRevisionRepository.class);
        TimelineSnapshotService snapshotService = mock(TimelineSnapshotService.class);
        com.example.platform.timeline.app.TimelineRevisionRefMutation currentService =
                mock(com.example.platform.timeline.app.TimelineRevisionRefMutation.class);
        TimelineRevisionRepository.RevisionRow baseRow = row("base-rev", "snap-base", base);
        TimelineRevisionRepository.RevisionRow srcRow = row("src-rev", "snap-src", source);
        TimelineRevisionRepository.RevisionRow tgtRow = row("tgt-rev", "snap-tgt", target);
        when(revisionRepo.findOwnedById("base-rev", "proj-1", "tenant-1")).thenReturn(Optional.of(baseRow));
        when(revisionRepo.findOwnedById("src-rev", "proj-1", "tenant-1")).thenReturn(Optional.of(srcRow));
        when(revisionRepo.findOwnedById("tgt-rev", "proj-1", "tenant-1")).thenReturn(Optional.of(tgtRow));
                when(snapshotService.findOwnedById("proj-1", "tenant-1", "snap-base"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-base", "proj-1", "tenant-1", base, "timeline-1.0")));
                when(snapshotService.findOwnedById("proj-1", "tenant-1", "snap-src"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-src", "proj-1", "tenant-1", source, "timeline-1.0")));
                when(snapshotService.findOwnedById("proj-1", "tenant-1", "snap-tgt"))
                .thenReturn(Optional.of(new TimelineSnapshotService.SnapshotInfo("snap-tgt", "proj-1", "tenant-1", target, "timeline-1.0")));
        when(revisionRepo.listOwnedByProject("proj-1", "tenant-1", null, null, null, 500)).thenReturn(List.of());
        when(currentService.currentHead(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any())).thenReturn("tgt-rev");
        var previewService = new TimelineMergePreviewService(new TimelineMergeConflictDetector());
        var planner = new TimelineNonConflictingMergePlanner(previewService);
        org.jooq.DSLContext dslMockEffe0 = org.mockito.Mockito.mock(org.jooq.DSLContext.class);
org.jooq.Configuration cfgdslMockEffe0 = org.mockito.Mockito.mock(org.jooq.Configuration.class);
        org.jooq.DSLContext txDsldslMockEffe0 = org.mockito.Mockito.mock(org.jooq.DSLContext.class, org.mockito.Answers.RETURNS_DEEP_STUBS);
        org.mockito.Mockito.when(cfgdslMockEffe0.dsl()).thenReturn(txDsldslMockEffe0);
        org.mockito.Mockito.when(dslMockEffe0.transactionResult(org.mockito.ArgumentMatchers.<org.jooq.TransactionalCallable<Object>>any()))
                .thenAnswer(inv -> {
                    org.jooq.TransactionalCallable<Object> callable = inv.getArgument(0);
                    return callable.run(cfgdslMockEffe0);
                });
        TimelineMergeEngine engine = new TimelineMergeEngine(revisionRepo, snapshotService,
                org.mockito.Mockito.mock(TimelineRevisionSaveService.class), previewService, planner, new TimelinePatchApplier(),
                InternalTimelineJson.mapper(),
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class),
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class),
                dslMockEffe0);
        TimelineMergeResult result = engine.mergeSemantic(TestTimelineMutationContexts.mergeRequest(
                "proj-1", "tenant-1", "base-rev", "src-rev", "tgt-rev", "user-1", "merge-1"));
        if (result.status() == TimelineMergeResult.MergeStatus.MERGED) {
            TimelineCandidate reloaded = TimelineDocumentCandidateMapper.map(
                    "proj-1", TimelineDocumentJsonSerializer.deserialize(result.mergedPayloadJson()));
            TimelineValidationResult validation = TimelineCanonicalValidator.validate(reloaded);
            return new MergeOutcome(result, result.mergedPayloadJson(), reloaded, validation);
        }
        return new MergeOutcome(result, null, null, null);
    }

    private static TimelineRevisionRepository.RevisionRow row(String rev, String snap, String payload) {
        return new TimelineRevisionRepository.RevisionRow(
                rev, "proj-1", "tenant-1", "base-rev", 1, snap, 0,
                new com.example.platform.timeline.canonical.TimelineContentDigester().digest(
                        TimelineDocumentJsonSerializer.deserialize(payload)),
                "timeline-1.0", "merge", "user-1", null, "test", null, null, null,
                true, "src-rev,tgt-rev", "base-rev", java.time.OffsetDateTime.now());
    }

    private static String canonicalPayload(String importPayload) {
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map("proj-1", importPayload);
        var document = com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter
                .toDocument(com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter
                        .toSnapshot(candidate, "fixture"));
        return TimelineDocumentJsonSerializer.serialize(document);
    }

    // ── E2E-M1: effect source-only merge ──
    @Test
    void e2eM1EffectSourceOnlySurvivesActualMerge() {
        MergeOutcome out = merge(base("tl"), withEffectRadius("tl", "9"), base("tl"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status());
        assertNotNull(out.mergedPayload, "E2E-M1: merged payload must be produced");
        assertFalse(out.reloadValidation.hasFatalErrors(), "E2E-M1: merged payload must pass canonical validation");
        // Reloaded semantics: effect radius = 9; transition + automation preserved.
        assertEquals(1, out.reloaded.tracks().get(0).clips().get(0).effects().size());
        assertTrue(out.reloaded.tracks().get(0).clips().get(0).effects().get(0).parameters()
                        .get("radius").toString().contains("9"),
                "E2E-M1: source-only effect change must survive actual merge");
        assertEquals(1, out.reloaded.transitions().size(), "E2E-M1: transition preserved");
        assertEquals(1, out.reloaded.automations().size(), "E2E-M1: automation preserved");
    }

    // ── FIFTH CORRECTION (F3.3): typed/nested Effect parameters survive the
    //    real TimelineMergeEngine losslessly (type + nested semantics). ──
    @Test
    void e2eF3TypedEffectParametersSurviveActualMerge() {
        Map<String, Object> typedParams = new java.util.LinkedHashMap<>();
        typedParams.put("radius", 9);                       // integer
        typedParams.put("label", "9");                      // string
        typedParams.put("enabled", true);                   // boolean
        typedParams.put("comma", "a,b");                    // comma value
        typedParams.put("equals", "a=b");                   // equals value
        Map<String, Object> nested = new java.util.LinkedHashMap<>();
        nested.put("z", 2);
        nested.put("a", 1);
        typedParams.put("nested", nested);                  // nested map
        typedParams.put("list", List.of(1, "2", true));     // list

        var typedBase = new TimelineImportRequest("tl-typed", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur",
                                        Map.of("radius", 3)))),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));
        var typedOurs = new TimelineImportRequest("tl-typed", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0,
                                List.of(new ImportClipEffect("fx1", "blur", typedParams))),
                        new ImportClip("c2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto-1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.0, "LINEAR")))));

        MergeOutcome out = merge(typedBase, typedOurs, typedBase);
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status(),
                "E2E-F3: typed Effect source-only merge must be MERGED");
        assertNotNull(out.mergedPayload);
        assertFalse(out.reloadValidation.hasFatalErrors(),
                "E2E-F3: merged payload must pass canonical reload validation");
        Map<String, Object> params = out.reloaded.tracks().get(0).clips().get(0)
                .effects().get(0).parameters();
        assertEquals(Integer.valueOf(9), params.get("radius"),
                "E2E-F3: integer parameter must survive merge as integer (not string)");
        assertEquals("9", params.get("label"), "E2E-F3: string parameter must survive as string");
        assertEquals(Boolean.TRUE, params.get("enabled"), "E2E-F3: boolean must survive");
        assertEquals("a,b", params.get("comma"), "E2E-F3: comma value must survive");
        assertEquals("a=b", params.get("equals"), "E2E-F3: equals value must survive");
        assertEquals(Map.of("z", 2, "a", 1), params.get("nested"),
                "E2E-F3: nested map semantics must survive");
        assertEquals(List.of(1, "2", true), params.get("list"),
                "E2E-F3: list must survive");
        assertNotEquals(params.get("radius").getClass(), params.get("label").getClass(),
                "E2E-F3: number/string type distinction must survive the merge");
        assertEquals(1, out.reloaded.transitions().size(), "E2E-F3: transition preserved");
        assertEquals(1, out.reloaded.automations().size(), "E2E-F3: automation preserved");
    }

    // ── SIXTH CORRECTION (S3): REAL three-way Effect-delete × Automation-modify
    //    must fail closed — never persist a dangling Automation target. ──
    @Test
    void e2eS3EffectDeleteVsAutomationModifyFailsClosed() {
        // BASE: Clip c1 with Effect fx1 + Automation auto1 targeting fx1.
        var s3Base = new TimelineImportRequest("tl-s3", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", "3")),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.5, "LINEAR")))));
        // OURS: delete Effect fx1 AND the dependent Automation auto1 (locally
        // consistent — no dangling references; valid canonical branch).
        var s3Ours = new TimelineImportRequest("tl-s3", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of()),
                        new ImportClip("c1-2", "ast_2", "file:///b.mp4", 1920, 1080,
                                2.0, 4.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of());
        // THEIRS: retain Effect fx1, modify Automation auto1 (value 0.5 → 0.8).
        var s3Theirs = new TimelineImportRequest("tl-s3", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(fullTrack("c1", "3")),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 4.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1-2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of("duration", "0.5"))),
                List.of(new ImportAutomationCurve("auto1", "fx1", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.8, "LINEAR")))));

        // All three branches are individually canonical-valid (import succeeds).
        String basePayload = importService.importTimeline(s3Base);
        String oursPayload = importService.importTimeline(s3Ours);
        String theirsPayload = importService.importTimeline(s3Theirs);
        assertTrue(basePayload.contains("auto1"), "S3: BASE must carry the automation");
        assertFalse(oursPayload.contains("auto1"), "S3: OURS must be locally consistent (automation removed)");

        MergeOutcome out = merge(s3Base, s3Ours, s3Theirs);
        // Fail-closed: no silently persisted invalid MERGED state.
        if (out.result.status() == TimelineMergeResult.MergeStatus.MERGED) {
            // If merged, the merged payload must NOT contain a dangling automation:
            // either the automation was removed together with the effect, or
            // canonical reload rejects it.
            assertFalse(out.mergedPayload.contains("auto1"),
                    "S3: merged payload must not contain Automation targeting a deleted Effect");
            assertFalse(out.reloadValidation.hasFatalErrors(),
                    "S3: merged payload must pass canonical reload validation");
        } else {
            assertTrue(out.result.status() == TimelineMergeResult.MergeStatus.CONFLICTS
                            || out.result.status() == TimelineMergeResult.MergeStatus.FAILED,
                    "S3: fail-closed via explicit conflict/blocked; got " + out.result.status());
        }
    }

    // ── E2E-M2: transition source-only merge ──
    @Test
    void e2eM2TransitionSourceOnlySurvivesActualMerge() {
        MergeOutcome out = merge(base("tl"), withTransitionDuration("tl", 30, 30), base("tl"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status());
        assertFalse(out.reloadValidation.hasFatalErrors());
        assertEquals(1, out.reloaded.tracks().get(0).clips().get(0).effects().size(),
                "E2E-M2: effect preserved");
        assertTrue(out.reloaded.transitions().get(0).duration().isEqualTo(MediaTime.ofTicks(30, 30)),
                "E2E-M2: source-only transition duration change must survive actual merge");
        assertEquals(1, out.reloaded.automations().size(), "E2E-M2: automation preserved");
    }

    // ── E2E-M3: automation source-only merge ──
    @Test
    void e2eM3AutomationSourceOnlySurvivesActualMerge() {
        MergeOutcome out = merge(base("tl"), withAutomationValue("tl", 0.8), base("tl"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status());
        assertFalse(out.reloadValidation.hasFatalErrors());
        assertEquals(0.8, out.reloaded.automations().get(0).keyframes().get(0).value(), 1e-9,
                "E2E-M3: source-only automation change must survive actual merge");
        assertEquals(1, out.reloaded.transitions().size(), "E2E-M3: transition preserved");
        assertEquals(1, out.reloaded.tracks().get(0).clips().get(0).effects().size(),
                "E2E-M3: effect preserved");
    }

    // ── E2E-R1: transition delete-last → merged payload empty/absent (no resurrection) ──
    @Test
    void e2eR1TransitionDeleteLastProducesEmptyMergedState() {
        MergeOutcome out = merge(base("tl"), withoutTransitionOnly("tl"), base("tl"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status());
        assertTrue(out.reloaded.transitions().isEmpty(),
                "E2E-R1: delete-last transition must be absent from merged payload");
        // Serialized payload must not resurrect target's transition.
        assertFalse(out.mergedPayload.contains("\"transitions\""),
                "E2E-R1: merged payload must carry canonical empty (field absent), no target resurrection");
        assertEquals(1, out.reloaded.automations().size(),
                "E2E-R1: unrelated automation preserved");
    }

    // ── E2E-R2: automation delete-last → merged payload empty/absent ──
    @Test
    void e2eR2AutomationDeleteLastProducesEmptyMergedState() {
        MergeOutcome out = merge(base("tl"), withoutAutomationOnly("tl"), base("tl"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, out.result.status());
        assertTrue(out.reloaded.automations().isEmpty(),
                "E2E-R2: delete-last automation must be absent from merged payload");
        assertFalse(out.mergedPayload.contains("\"automations\""),
                "E2E-R2: merged payload must carry canonical empty (field absent), no target resurrection");
        assertEquals(1, out.reloaded.transitions().size(),
                "E2E-R2: unrelated transition preserved");
    }

    // ── E2E-C1: divergent effect two-sided edit → NO silent merged revision ──
    @Test
    void e2eC1DivergentEffectEditDoesNotSilentlyMerge() {
        MergeOutcome out = merge(base("tl"), withEffectRadius("tl", "9"), withEffectRadius("tl", "15"));
        assertFalse(out.result.status() == TimelineMergeResult.MergeStatus.MERGED,
                "E2E-C1: divergent two-sided effect edit must not produce a silent merged revision; got "
                        + out.result.status() + " " + out.result.mergedRevisionId());
    }

    // ── E2E-X1: delete Clip vs Transition — REAL three-way cases ──
    // Case A (THEIRS retains t1): OURS deletes c1 AND resolves its own local
    // state by deleting the referencing transition → merged aggregate is valid
    // (no dangling endpoint) and may merge cleanly.
    // Case B (THEIRS modifies t1): OURS deletes c1 + t1 while THEIRS changes t1
    // → same-path delete-vs-modify must fail closed (explicit conflict).
    // Forbidden in both: a persisted/reloaded dangling Transition (t1 → missing c1).
    @Test
    void e2eX1DeleteClipVsTransitionFailsClosed() {
        // Case A: consistent delete on OURS side; THEIRS retains base.
        MergeOutcome outA = merge(base("tl"), withoutClipC1AndTransition("tl"), base("tl"));
        if (outA.result.status() == TimelineMergeResult.MergeStatus.MERGED) {
            // Reloaded aggregate must not contain a dangling transition.
            assertTrue(outA.reloaded.transitions().isEmpty(),
                    "E2E-X1A: merged aggregate must not carry a transition referencing deleted clip c1");
            assertFalse(outA.reloadValidation.hasFatalErrors(),
                    "E2E-X1A: merged payload must pass aggregate validation");
        } else {
            // Explicit conflict / blocked is also acceptable (fail-closed).
            assertTrue(outA.result.status() == TimelineMergeResult.MergeStatus.CONFLICTS
                            || outA.result.status() == TimelineMergeResult.MergeStatus.FAILED,
                    "E2E-X1A: fail-closed via conflict/blocked; got " + outA.result.status());
        }

        // Case B: THEIRS modifies t1 while OURS deletes c1 + t1 →
        // delete-vs-modify on the same transition path must fail closed.
        MergeOutcome outB = merge(base("tl"), withoutClipC1AndTransition("tl"),
                withTransitionDuration("tl", 30, 30));
        assertFalse(outB.result.status() == TimelineMergeResult.MergeStatus.MERGED,
                "E2E-X1B: delete-Clip/Transition vs modified Transition must not silently merge; got "
                        + outB.result.status() + " " + outB.result.mergedRevisionId());
    }

    // ── XV1/XV2/XV3: aggregate validation rejects dangling/self transition endpoints ──
    @Test
    void xv1TransitionOutgoingClipMissingFailsValidation() {
        var bad = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "ghost", "c1", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of())),
                List.of());
        assertRejected(bad, "XV1: transition with missing outgoing Clip must be rejected");
    }

    @Test
    void xv2TransitionIncomingClipMissingFailsValidation() {
        var bad = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "ghost", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of())),
                List.of());
        assertRejected(bad, "XV2: transition with missing incoming Clip must be rejected");
    }

    @Test
    void xv3TransitionSelfReferenceFailsValidation() {
        var bad = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0,
                List.of(new ImportTransition("t1", "video.dissolve", "1.0", "c1", "c1", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES", Map.of())),
                List.of());
        assertRejected(bad, "XV3: transition with outgoing == incoming must be rejected");
    }

    @Test
    void xv6AutomationMissingTargetFailsValidation() {
        var bad = new TimelineImportRequest("tl", "T", 1,
                new ImportOutput("mp4", 1920, 1080, FrameRate.of(30, 1)),
                List.of(new ImportTrack("v1", "VIDEO", 0, List.of(
                        new ImportClip("c1", "ast_1", "file:///a.mp4", 1920, 1080,
                                0.0, 2.0, 0.0, 2.0, List.of())))),
                List.of(), null, null, null, null, false, List.of(), "AUTO", false,
                Map.of(), Map.of(), 2.0, List.of(),
                List.of(new ImportAutomationCurve("auto-1", "ghost-fx", "opacity", "float", "HOLD",
                        List.of(new ImportAutomationKeyframe("kf-1", 0, 30, 0.5, "LINEAR")))));
        assertRejected(bad, "XV6: automation targeting a missing Effect must be rejected");
    }

    private void assertRejected(TimelineImportRequest req, String message) {
        try {
            importService.importTimeline(req);
            throw new AssertionError(message + " (import unexpectedly accepted)");
        } catch (TimelineCanonicalRejectionException expected) {
            // expected: aggregate validation fail-closed
        } catch (IllegalArgumentException expected) {
            // also acceptable: construction-time fail-closed (e.g. self-reference)
        }
    }
}
