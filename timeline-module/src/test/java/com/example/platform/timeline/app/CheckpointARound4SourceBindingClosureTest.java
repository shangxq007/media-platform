package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.platform.media.domain.identity.MediaAssetId;
import com.example.platform.media.domain.stream.MediaStreamId;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationResult;
import com.example.platform.timeline.diff.application.TimelinePatchApplicationStatus;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineClipSnapshot;
import com.example.platform.timeline.diff.calculation.CanonicalTimelineSnapshot;
import com.example.platform.timeline.diff.calculation.TimelineSnapshotConverter;
import com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.timeline.diff.TimelinePatch;
import com.example.platform.timeline.diff.TimelinePatchId;
import com.example.platform.timeline.internal.TimelineMergeRequest;
import com.example.platform.timeline.internal.TimelineMergeResult;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBindingCanonicalSemantics;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * CHECKPOINT_A Round 4 (R4-B): typed TimelineSourceBinding closure tests.
 *
 * <p>Proves the REAL typed semantic root
 * {@link MediaStreamSourceBinding} (implementing {@link TimelineSourceBinding})
 * survives: candidate → snapshot → diff → patch → production merge →
 * canonical serialized payload → reload. The flattened projection strings are
 * verified as DERIVED (never independent semantic authority): the typed object
 * comparison drives diff/merge, and exact ArtifactId + ContentDigest + source
 * range survive untouched.
 */
class CheckpointARound4SourceBindingClosureTest {

    private static final String PROJECT = "proj-sb";
    private static final String TENANT = "tenant-sb";

    private static final MediaStreamSourceBinding BINDING = new MediaStreamSourceBinding(
            new MediaAssetId("asset-1"),
            new MediaStreamId("stream-1"),
            new ArtifactId("art-1"),
            ContentDigest.sha256("a".repeat(64)),
            new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofTicks(60, 30)));

    private static final MediaStreamSourceBinding OTHER_BINDING = new MediaStreamSourceBinding(
            new MediaAssetId("asset-1"),
            new MediaStreamId("stream-1"),
            new ArtifactId("art-2"),
            ContentDigest.sha256("b".repeat(64)),
            new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofTicks(60, 30)));

    @Test
    void typedBindingSurvivesCandidateToSnapshot() {
        TimelineCandidate candidate = candidateWithBinding(BINDING);
        assertTrue(candidate.tracks().get(0).clips().get(0).sourceBinding()
                        instanceof MediaStreamSourceBinding,
                "candidate must carry the typed binding");
        CanonicalTimelineSnapshot snapshot =
                TimelineSnapshotConverter.toSnapshot(candidate, "r1");
        assertTrue(snapshot.tracks().get(0).clips().get(0).sourceBinding()
                        instanceof MediaStreamSourceBinding,
                "snapshot must carry the typed binding");
        assertEquals(BINDING, snapshot.tracks().get(0).clips().get(0).sourceBinding(),
                "typed binding must survive candidate → snapshot exactly");
    }

    @Test
    void typedBindingSurvivesDiffPatchReload() {
        CanonicalTimelineSnapshot base = TimelineSnapshotConverter.toSnapshot(
                candidateWithBinding(null), "r0");
        CanonicalTimelineSnapshot after = TimelineSnapshotConverter.toSnapshot(
                candidateWithBinding(BINDING), "r1");
        var ops = new com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator()
                .calculate(base, after).diff().operations();
        TimelinePatchApplicationResult result = new TimelinePatchApplier().apply(
                base, new TimelinePatch(new TimelinePatchId("p"), "r0", ops, null, Map.of()));
        assertEquals(TimelinePatchApplicationStatus.APPLIED, result.status());
        CanonicalTimelineClipSnapshot reloaded = result.patchedSnapshot().tracks().get(0).clips().get(0);
        assertTrue(reloaded.sourceBinding() instanceof MediaStreamSourceBinding,
                "patch must reconstruct the TYPED binding (no String-field narrowing)");
        assertEquals(BINDING.artifactId(), reloaded.sourceBinding() instanceof MediaStreamSourceBinding m
                ? m.artifactId() : null, "exact ArtifactId must survive");
        assertEquals(BINDING.contentDigest(), reloaded.sourceBinding() instanceof MediaStreamSourceBinding m
                ? m.contentDigest() : null, "exact ContentDigest must survive");
        assertEquals(BINDING.sourceRange(), reloaded.sourceBinding() instanceof MediaStreamSourceBinding m
                ? m.sourceRange() : null, "exact source range must survive");
    }

    @Test
    void differentBindingsProduceSemanticChangeIdenticalDoesNot() {
        CanonicalTimelineSnapshot base = TimelineSnapshotConverter.toSnapshot(
                candidateWithBinding(BINDING), "r0");
        CanonicalTimelineSnapshot changed = TimelineSnapshotConverter.toSnapshot(
                candidateWithBinding(OTHER_BINDING), "r1");
        var changedOps = new com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator()
                .calculate(base, changed).diff().operations();
        assertTrue(changedOps.stream().anyMatch(o -> o.path().value().endsWith(".sourceSemantics")),
                "different MediaStreamSourceBinding must produce a semantic change");

        CanonicalTimelineSnapshot identical = TimelineSnapshotConverter.toSnapshot(
                candidateWithBinding(BINDING), "r2");
        var identicalOps = new com.example.platform.timeline.diff.calculation.CanonicalTimelineDiffCalculator()
                .calculate(base, identical).diff().operations();
        assertTrue(identicalOps.stream().noneMatch(o -> o.path().value().endsWith(".sourceSemantics")),
                "identical binding must NOT produce a semantic change");
    }

    @Test
    void typedBindingSurvivesProductionMergeAndReload() throws Exception {
        TenantContext.set(TENANT);
        // BASE: clip with BINDING; OURS: adds an independent effect edit while
        // keeping the SAME typed binding; THEIRS: unchanged — the real merge
        // materializes the effect change and the typed binding must survive
        // exactly through merge → canonical serialized payload → reload.
        String baseJson = payloadWithBinding(BINDING);
        String oursJson = payloadWithEffect(payloadWithBinding(BINDING));
        String theirsJson = payloadWithBinding(BINDING);

        TimelineMergeEngine engine = newEngine(baseJson, oursJson, theirsJson);
        TimelineMergeResult result = engine.merge(new TimelineMergeRequest(
                PROJECT, TENANT, "base-rev", "src-rev", "tgt-rev", "u", "m"));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status(),
                "merge must succeed: " + result.summary());

        // canonical serialized payload → reload
        TimelineCandidate reloaded = InternalTimelineCandidateAdapter.map(PROJECT,
                result.mergedPayloadJson());
        TimelineSourceBinding mergedBinding =
                reloaded.tracks().get(0).clips().get(0).sourceBinding();
        assertTrue(mergedBinding instanceof MediaStreamSourceBinding,
                "merged/reloaded clip must carry the typed binding");
        assertEquals(BINDING.artifactId(), ((MediaStreamSourceBinding) mergedBinding).artifactId());
        assertEquals(BINDING.contentDigest(), ((MediaStreamSourceBinding) mergedBinding).contentDigest());

        // wire shape: nested sourceBinding object with exact artifactId + digest
        JsonNode wire = InternalTimelineJson.parse(result.mergedPayloadJson())
                .path("composition").path("tracks").path(0).path("clips").path(0)
                .path("sourceBinding");
        assertEquals("art-1", wire.path("artifactId").asText());
        assertEquals("a".repeat(64), wire.path("contentDigest").path("value").asText());
        TenantContext.clear();
    }

    @Test
    void divergentBindingReplacementConflictsThroughRealEngine() throws Exception {
        TenantContext.set(TENANT);
        String baseJson = payloadWithBinding(BINDING);
        String oursJson = payloadWithBinding(OTHER_BINDING);
        String theirsJson = payloadWithBinding(
                new MediaStreamSourceBinding(new MediaAssetId("asset-1"),
                        new MediaStreamId("stream-1"), new ArtifactId("art-3"),
                        ContentDigest.sha256("c".repeat(64)),
                        new MediaClip.TimeRange(MediaTime.ZERO, MediaTime.ofTicks(60, 30))));
        TimelineMergeEngine engine = newEngine(baseJson, oursJson, theirsJson);
        TimelineMergeResult result = engine.merge(new TimelineMergeRequest(
                PROJECT, TENANT, "base-rev", "src-rev", "tgt-rev", "u", "m"));
        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status(),
                "divergent binding replacement must conflict through the REAL engine");
        TenantContext.clear();
    }

    @Test
    void derivedProjectionIsNotIndependentAuthority() {
        // The flattened accessors are derived from the typed binding — they
        // cannot carry authority the typed binding lacks.
        CanonicalTimelineClipSnapshot clip = new CanonicalTimelineClipSnapshot(
                "c1", "ast-1", MediaTime.ofTicks(0, 30), MediaTime.ofTicks(60, 30),
                MediaTime.ofTicks(0, 30), MediaTime.ofTicks(60, 30), FrameRate.of(30, 1),
                List.of(), Map.of(), BINDING, null);
        assertEquals("MEDIA_STREAM", clip.sourceKind(), "sourceKind is derived from binding");
        assertEquals("stream-1", clip.mediaStreamId(), "mediaStreamId is derived from binding");
        assertEquals("art-1", clip.artifactId(), "artifactId is derived from binding");
        assertEquals("a".repeat(64), clip.contentDigest(), "contentDigest is derived from binding");
        // null binding → derived projections are null (no fabricated values)
        CanonicalTimelineClipSnapshot bare = new CanonicalTimelineClipSnapshot(
                "c1", "ast-1", MediaTime.ofTicks(0, 30), MediaTime.ofTicks(60, 30),
                MediaTime.ofTicks(0, 30), MediaTime.ofTicks(60, 30), FrameRate.of(30, 1),
                List.of(), Map.of(), (TimelineSourceBinding) null, null);
        assertNull(bare.sourceKind());
        assertNull(bare.mediaStreamId());
        assertNull(bare.artifactId());
        assertNull(bare.contentDigest());
    }

    @Test
    void bindingCanonicalCodecRoundTrip() {
        String encoded = TimelineSourceBindingCanonicalSemantics.encode(BINDING);
        TimelineSourceBinding decoded = TimelineSourceBindingCanonicalSemantics.decode(encoded);
        assertEquals(BINDING, decoded, "binding canonical codec round-trip must be lossless");
        assertTrue(TimelineSourceBindingCanonicalSemantics.localSemanticsEquals(BINDING, decoded));
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private TimelineCandidate candidateWithBinding(TimelineSourceBinding binding) {
        TimelineCandidate.Clip clip = new TimelineCandidate.Clip(
                "c1", new com.example.platform.timeline.canonicalmodel.TimelineSourceRef("ast-1"),
                MediaTime.ofTicks(0, 30), MediaTime.ofTicks(0, 30), MediaTime.ofTicks(60, 30),
                FrameRate.of(30, 1), List.of(), List.of(),
                "MEDIA_STREAM", "asset-1", "stream-1", "art-1", "a".repeat(64), null, binding);
        return new TimelineCandidate("tl-1", PROJECT,
                com.example.platform.timeline.canonicalmodel.TimelineCanonicalProfile.CANONICAL_TIMELINE_FOUNDATION_V1,
                List.of(new TimelineCandidate.Track("v1", TimelineCandidate.TrackType.VIDEO, 0, null,
                        List.of(clip))),
                List.of(), List.of(), List.of(),
                com.example.platform.audio.domain.mix.AudioMix.empty(), List.of());
    }

    private String payloadWithEffect(String basePayload) throws Exception {
        ObjectNode root = (ObjectNode) InternalTimelineJson.parse(basePayload);
        ((ObjectNode) root.path("composition").path("tracks").path(0).path("clips").path(0))
                .set("effects", InternalTimelineJson.mapper().createArrayNode()
                        .add(InternalTimelineJson.mapper().createObjectNode()
                                .put("id", "fx1")
                                .put("effectKey", "blur")
                                .set("parameters", InternalTimelineJson.mapper().createObjectNode()
                                        .put("radius", 8))));
        return InternalTimelineJson.mapper().writeValueAsString(root);
    }

    private String payloadWithBinding(MediaStreamSourceBinding binding) throws Exception {
        ObjectMapper mapper = InternalTimelineJson.mapper();
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", "tl-sb");
        ObjectNode composition = root.putObject("composition");
        ArrayNode tracks = composition.putArray("tracks");
        ObjectNode track = tracks.addObject();
        track.put("id", "v1");
        track.put("type", "VIDEO");
        ArrayNode clips = track.putArray("clips");
        ObjectNode clip = clips.addObject();
        clip.put("id", "c1");
        clip.put("assetId", "asset-1");
        ObjectNode rate = mapper.createObjectNode();
        rate.put("num", 30);
        rate.put("den", 1);
        ObjectNode start = mapper.createObjectNode();
        start.put("frame", 0);
        start.set("rate", rate);
        ObjectNode duration = mapper.createObjectNode();
        duration.put("frame", 60);
        duration.set("rate", rate);
        ObjectNode timelineRange = mapper.createObjectNode();
        timelineRange.set("start", start);
        timelineRange.set("duration", duration);
        clip.set("timelineRange", timelineRange);
        ObjectNode sourceRange = mapper.createObjectNode();
        ObjectNode srcStart = mapper.createObjectNode();
        srcStart.put("frame", 0);
        srcStart.set("rate", rate);
        ObjectNode srcDuration = mapper.createObjectNode();
        srcDuration.put("frame", 60);
        srcDuration.set("rate", rate);
        sourceRange.set("start", srcStart);
        sourceRange.set("duration", srcDuration);
        clip.set("sourceRange", sourceRange);
        if (binding != null) {
            clip.set("sourceBinding",
                    TimelineSourceBindingCanonicalSemantics.canonicalValue(binding));
        }
        return mapper.writeValueAsString(root);
    }

    private TimelineMergeEngine newEngine(String basePayload, String srcPayload,
            String tgtPayload) throws Exception {
        ObjectMapper mapper = InternalTimelineJson.mapper();
        TimelineRevisionRepository repo = mock(TimelineRevisionRepository.class);
        TimelineSnapshotService snap = mock(TimelineSnapshotService.class);
        com.example.platform.timeline.app.ProductCurrentRevisionService cur =
                mock(com.example.platform.timeline.app.ProductCurrentRevisionService.class);
        when(repo.findById("base-rev")).thenReturn(Optional.of(row("base-rev", "snap-b")));
        when(repo.findById("src-rev")).thenReturn(Optional.of(row("src-rev", "snap-s")));
        when(repo.findById("tgt-rev")).thenReturn(Optional.of(row("tgt-rev", "snap-t")));
        when(snap.findById("snap-b")).thenReturn(Optional.of(info("snap-b", basePayload)));
        when(snap.findById("snap-s")).thenReturn(Optional.of(info("snap-s", srcPayload)));
        when(snap.findById("snap-t")).thenReturn(Optional.of(info("snap-t", tgtPayload)));
        when(snap.save(anyString(), anyString(), anyString(), anyString())).thenReturn("snap-m");
        when(repo.nextRevisionNumber(PROJECT)).thenReturn(4);
        when(repo.listByProject(PROJECT, 500)).thenReturn(List.of());
        when(cur.getCurrentRevisionId(PROJECT)).thenReturn("tgt-rev");
        TimelineMergePreviewService pv = new TimelineMergePreviewService(new TimelineMergeConflictDetector());
        return new TimelineMergeEngine(repo, snap, cur, pv,
                new TimelineNonConflictingMergePlanner(pv), new TimelinePatchApplier(), mapper);
    }

    private TimelineRevisionRepository.RevisionRow row(String rev, String snapId) {
        return new TimelineRevisionRepository.RevisionRow(
                rev, PROJECT, TENANT, "base-rev", 1, snapId, 0, "h", "internal-1.0",
                "merge", "u", null, "m", null, null, null, false, null, null,
                java.time.OffsetDateTime.now());
    }

    private TimelineSnapshotService.SnapshotInfo info(String id, String payload) {
        return new TimelineSnapshotService.SnapshotInfo(id, PROJECT, TENANT, payload, "internal-1.0");
    }
}
