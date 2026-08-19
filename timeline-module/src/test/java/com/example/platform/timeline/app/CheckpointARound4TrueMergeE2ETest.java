package com.example.platform.timeline.app;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.audio.domain.mix.AudioMasterBus;
import com.example.platform.audio.domain.mix.AudioMix;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioRoute;
import com.example.platform.shared.digest.ContentDigest;
import com.example.platform.shared.identity.ArtifactId;
import com.example.platform.shared.time.FrameRate;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.shared.web.TenantContext;
import com.example.platform.timeline.adapter.TimelineRevisionRepository;
import com.example.platform.timeline.adapter.TimelineSnapshotService;
import com.example.platform.timeline.canonical.TimedTextCanonicalSemantics;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.diff.application.TimelinePatchApplier;
import com.example.platform.timeline.diff.merge.TimelineMergeConflictDetector;
import com.example.platform.timeline.diff.merge.plan.TimelineNonConflictingMergePlanner;
import com.example.platform.timeline.diff.merge.preview.TimelineMergePreviewService;
import com.example.platform.timeline.internal.TimelineMergeRequest;
import com.example.platform.timeline.internal.TimelineMergeResult;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.timeline.semantics.clip.TimelineSourceBinding;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.GroupRelationship;
import com.example.platform.timeline.semantics.relationship.SemanticRelationship;
import com.example.platform.timeline.semantics.relationship.SyncRelationship;
import com.example.platform.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import com.example.platform.timeline.semantics.temporal.TemporalMapping;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * CHECKPOINT_A Round 4 (R4-C): TRUE combined production TimelineMergeEngine E2E.
 *
 * <p>One real three-way merge whose BASE contains ALL eight authored semantic
 * families under Checkpoint A:
 * Effect, Transition, Automation, TimedText, AudioMix, SemanticRelationship,
 * TimelineSourceBinding, TemporalMapping. OURS and THEIRS distribute INDEPENDENT
 * edits across the families; the REAL production TimelineMergeEngine (with its
 * real collaborators: canonical gate, diff, conflict analysis, non-conflicting
 * planner, patch applier) executes ONE merge; the merged payload is re-gated and
 * every family is asserted to survive with EXACT authored semantics.
 *
 * <p>Conflict tests exercise the REAL engine's conflict-planning path for the
 * new families (AudioMix / SemanticRelationship / SourceBinding /
 * TemporalMapping): identical changes merge, divergent changes conflict.
 */
class CheckpointARound4TrueMergeE2ETest {

    private static final String PROJECT = "proj-r4";
    private static final String TENANT = "tenant-r4";
    private static final String BASE_REV = "base-rev";
    private static final String SOURCE_REV = "src-rev";
    private static final String TARGET_REV = "tgt-rev";
    private static final long FPS = 30;

    private final ObjectMapper mapper = InternalTimelineJson.mapper();

    private TimelineMergeEngine engine;
    private TimelineRevisionRepository revisionRepository;
    private TimelineSnapshotService snapshotService;
    private ProductCurrentRevisionService currentRevisionService;

    @BeforeEach
    void setUp() {
        TenantContext.set(TENANT);
        revisionRepository = mock(TimelineRevisionRepository.class);
        snapshotService = mock(TimelineSnapshotService.class);
        currentRevisionService = mock(ProductCurrentRevisionService.class);
        TimelineMergePreviewService previewService = new TimelineMergePreviewService(
                new TimelineMergeConflictDetector());
        TimelineNonConflictingMergePlanner planner =
                new TimelineNonConflictingMergePlanner(previewService);
        com.example.platform.timeline.app.TimelineArtifactPinValidator pinValidator =
                org.mockito.Mockito.mock(com.example.platform.timeline.app.TimelineArtifactPinValidator.class);
        // R5-C: the pin boundary runs unconditionally in the persistent merge
        // path — the test validator must answer VALID (the E2E's artifacts are
        // in-scope fixtures; pin validation itself is exercised by the R5-C
        // real-PG tests).
        org.mockito.Mockito.when(pinValidator.validate(org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyList()))
                .thenReturn(new com.example.platform.timeline.app.TimelineArtifactPinValidator.ValidationResult(true, java.util.List.of()));
        engine = new TimelineMergeEngine(revisionRepository, snapshotService, currentRevisionService,
                previewService, planner, new TimelinePatchApplier(), mapper,
                pinValidator,
                org.mockito.Mockito.mock(com.example.platform.artifact.app.ArtifactPinService.class));
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    // ── fixtures ──────────────────────────────────────────────────────────

    private ObjectNode rangeNode(long startMs, long durationMs) {
        ObjectNode rate = mapper.createObjectNode();
        rate.put("num", FPS);
        rate.put("den", 1);
        ObjectNode start = mapper.createObjectNode();
        start.put("frame", (startMs * FPS) / 1000L);
        start.set("rate", rate);
        ObjectNode duration = mapper.createObjectNode();
        duration.put("frame", (durationMs * FPS) / 1000L);
        duration.set("rate", rate);
        ObjectNode range = mapper.createObjectNode();
        range.set("start", start);
        range.set("duration", duration);
        return range;
    }

    private ObjectNode clipNode(String id, long startMs, long durationMs) {
        ObjectNode clip = mapper.createObjectNode();
        clip.put("id", id);
        clip.put("assetId", "asset-" + id);
        clip.set("timelineRange", rangeNode(startMs, durationMs));
        clip.set("sourceRange", rangeNode(0L, durationMs));
        return clip;
    }

    private ObjectNode trackNode(String id, JsonNode... clips) {
        ObjectNode track = mapper.createObjectNode();
        track.put("id", id);
        track.put("type", "VIDEO");
        ArrayNode clipArray = mapper.createArrayNode();
        for (JsonNode clip : clips) {
            clipArray.add(clip);
        }
        track.set("clips", clipArray);
        return track;
    }

    private ObjectNode internalTimeline(JsonNode... tracks) {
        ObjectNode root = mapper.createObjectNode();
        root.put("schemaVersion", "1.0");
        root.put("id", "tl-r4");
        ObjectNode composition = mapper.createObjectNode();
        ArrayNode trackArray = mapper.createArrayNode();
        for (JsonNode track : tracks) {
            trackArray.add(track);
        }
        composition.set("tracks", trackArray);
        root.set("composition", composition);
        return root;
    }

    private ObjectNode transitionNode(String id, String defId, String version,
            String outgoing, String incoming, String mediaType, long durationTicks,
            long durationScale, String alignment, String policy, ObjectNode params) {
        ObjectNode t = mapper.createObjectNode();
        t.put("id", id);
        t.put("transitionDefinitionId", defId);
        t.put("transitionDefinitionVersion", version);
        t.put("outgoingClipId", outgoing);
        t.put("incomingClipId", incoming);
        t.put("mediaType", mediaType);
        t.put("durationTicks", durationTicks);
        t.put("durationTimeScale", durationScale);
        t.put("alignment", alignment);
        t.put("temporalPolicy", policy);
        t.set("parameters", params != null ? params : mapper.createObjectNode());
        return t;
    }

    private ObjectNode automationNode(String id, String target, String path,
            String valueType, String extrapolation, ObjectNode... keyframes) {
        ObjectNode a = mapper.createObjectNode();
        a.put("automationId", id);
        a.put("targetEntityId", target);
        a.put("parameterPath", path);
        a.put("valueType", valueType);
        a.put("extrapolation", extrapolation);
        ArrayNode kfs = mapper.createArrayNode();
        for (ObjectNode kf : keyframes) {
            kfs.add(kf);
        }
        a.set("keyframes", kfs);
        return a;
    }

    private ObjectNode keyframeNode(String id, long ticks, long scale, double value, String interp) {
        ObjectNode kf = mapper.createObjectNode();
        kf.put("keyframeId", id);
        kf.put("timeTicks", ticks);
        kf.put("timeTimeScale", scale);
        kf.put("value", value);
        kf.put("interpolation", interp);
        return kf;
    }

    private ObjectNode sourceBindingNode(String artifactId, String digest) {
        ObjectNode sb = mapper.createObjectNode();
        sb.put("sourceKind", "MEDIA_STREAM");
        sb.put("mediaAssetId", "asset-1");
        sb.put("mediaStreamId", "stream-1");
        sb.put("artifactId", artifactId);
        ObjectNode digestNode = sb.putObject("contentDigest");
        digestNode.put("algorithm", "SHA_256");
        digestNode.put("value", digest);
        sb.put("sourceRangeStart", "0/1");
        sb.put("sourceRangeEnd", "60/30");
        return sb;
    }

    private ObjectNode audioMixNode(double gain) {
        ObjectNode mix = mapper.createObjectNode();
        ObjectNode master = mix.putObject("masterBus");
        master.put("busId", "master");
        ArrayNode routes = mix.putArray("routes");
        ObjectNode route = routes.addObject();
        ObjectNode input = route.putObject("input");
        input.put("trackId", "v1");
        input.put("clipId", "c1");
        ObjectNode gainNode = route.putObject("gain");
        gainNode.put("linear", gain);
        return mix;
    }

    private ObjectNode groupRelNode(String gid, String... members) {
        ObjectNode rel = mapper.createObjectNode();
        rel.put("kind", "GROUP");
        ObjectNode gidNode = rel.putObject("groupId");
        gidNode.put("value", gid);
        ArrayNode memberArray = rel.putArray("members");
        for (String m : members) {
            memberArray.add(m);   // TimelineClipId @JsonValue: scalar wire form
        }
        return rel;
    }

    private ObjectNode syncRelNode(String a, String b, long anchorA) {
        ObjectNode rel = mapper.createObjectNode();
        rel.put("kind", "SYNC");
        rel.put("endpointA", a);        // TimelineClipId @JsonValue: scalar
        rel.put("localAnchorA", anchorA + "/1");  // MediaTime codec: "ticks/scale"
        rel.put("endpointB", b);        // TimelineClipId @JsonValue: scalar
        rel.put("localAnchorB", "0");   // MediaTime codec: canonical "0"
        return rel;
    }

    private String json(ObjectNode node) throws Exception {
        return mapper.writeValueAsString(node);
    }

    private void stubRevisions(String baseJson, String sourceJson, String targetJson) {
        when(revisionRepository.findById(BASE_REV))
                .thenReturn(Optional.of(row(BASE_REV, "snap-base")));
        when(revisionRepository.findById(SOURCE_REV))
                .thenReturn(Optional.of(row(SOURCE_REV, "snap-source")));
        when(revisionRepository.findById(TARGET_REV))
                .thenReturn(Optional.of(row(TARGET_REV, "snap-target")));
        when(snapshotService.findById("snap-base"))
                .thenReturn(Optional.of(info("snap-base", baseJson)));
        when(snapshotService.findById("snap-source"))
                .thenReturn(Optional.of(info("snap-source", sourceJson)));
        when(snapshotService.findById("snap-target"))
                .thenReturn(Optional.of(info("snap-target", targetJson)));
        when(snapshotService.save(anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(inv -> "snap-merged-" + inv.getArgument(2).hashCode());
        when(revisionRepository.nextRevisionNumber(PROJECT)).thenReturn(9);
        when(revisionRepository.listByProject(PROJECT, 500)).thenReturn(List.of());
        when(currentRevisionService.getCurrentRevisionId(PROJECT)).thenReturn(TARGET_REV);
    }

    private TimelineRevisionRepository.RevisionRow row(String rev, String snap) {
        return new TimelineRevisionRepository.RevisionRow(
                rev, PROJECT, TENANT, "base-rev", 1, snap, 0, "hash",
                "internal-1.0", "merge", "user-1", null, "test", null, null, null,
                true, "src-rev,tgt-rev", "base-rev", java.time.OffsetDateTime.now());
    }

    private TimelineSnapshotService.SnapshotInfo info(String id, String payload) {
        return new TimelineSnapshotService.SnapshotInfo(id, PROJECT, TENANT, payload, "internal-1.0");
    }

    private TimelineMergeResult merge(String baseJson, String sourceJson, String targetJson) {
        stubRevisions(baseJson, sourceJson, targetJson);
        return engine.merge(new TimelineMergeRequest(
                PROJECT, TENANT, BASE_REV, SOURCE_REV, TARGET_REV, "user-1", "merge-r4"));
    }

    private TimelineCandidate reload(String mergedPayload) {
        return InternalTimelineCandidateAdapter.map(PROJECT, mergedPayload);
    }

    private JsonNode firstClip(String payload) throws Exception {
        return InternalTimelineJson.parse(payload).path("composition").path("tracks").path(0).path("clips").path(0);
    }

    private JsonNode firstTrack(String payload) throws Exception {
        return InternalTimelineJson.parse(payload).path("composition").path("tracks").path(0);
    }

    // ── TRUE 8-FAMILY PRODUCTION MERGE E2E ────────────────────────────────

    @Test
    void trueEightFamilyProductionMergeE2E() throws Exception {
        // BASE: all eight families present.
        ObjectNode baseClip = clipNode("c1", 0, 2000);
        baseClip.set("sourceBinding", sourceBindingNode("art-1", "a".repeat(64)));
        baseClip.set("temporalMapping", mapper.valueToTree(
                ConstantRateTemporalMapping.of(30, 30, PlaybackDirection.FORWARD)));
        ObjectNode base = internalTimeline(trackNode("v1", baseClip));
        ObjectNode baseComposition = (ObjectNode) base.path("composition");
        baseComposition.set("transitions", mapper.createArrayNode()
                .add(transitionNode("t1", "video.dissolve", "1.0", "c1", "c2", "VIDEO",
                        15, 30, "CENTER_ON_CUT", "USE_SOURCE_HANDLES",
                        (ObjectNode) mapper.createObjectNode().put("duration", "0.5"))));
        baseComposition.set("automations", mapper.createArrayNode()
                .add(automationNode("auto1", "fx1", "opacity", "float", "HOLD",
                        keyframeNode("kf-1", 0, 30, 0.5, "LINEAR"))));
        baseComposition.set("textElements", mapper.createArrayNode()
                .add(TimedTextCanonicalSemantics.toCanonicalNode(
                        com.example.platform.timeline.canonical.TestTextElements.textElement("t1"))));
        baseComposition.set("audioMix", audioMixNode(0.5));
        baseComposition.set("semanticRelationships", mapper.createArrayNode()
                .add(groupRelNode("g1", "c1", "c2"))
                .add(syncRelNode("c1", "c2", 10)));
        // clip c2 exists only as a transition participant: give it a real clip.
        ArrayNode trackClips = (ArrayNode) base.path("composition").path("tracks").path(0).path("clips");
        trackClips.add(clipNode("c2", 2000, 2000));
        // clip c1 effect (family 1: Effect)
        ((ObjectNode) trackClips.get(0)).set("effects", mapper.createArrayNode()
                .add(mapper.createObjectNode()
                        .put("id", "fx1")
                        .put("effectKey", "blur")
                        .set("parameters", mapper.createObjectNode().put("radius", 4))));

        String baseJson = json(base);

        // OURS: Effect changed, AudioMix changed, one Relationship independently
        // changed (sync anchor edit → anchor change), TimedText unchanged.
        ObjectNode ours = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ObjectNode oursComposition = (ObjectNode) ours.path("composition");
        ((ObjectNode) oursComposition.path("tracks").path(0).path("clips").path(0))
                .set("effects", mapper.createArrayNode()
                        .add(mapper.createObjectNode()
                                .put("id", "fx1")
                                .put("effectKey", "blur")
                                .set("parameters", mapper.createObjectNode().put("radius", 8))));
        oursComposition.set("audioMix", audioMixNode(0.9));
        ((ArrayNode) oursComposition.path("semanticRelationships")).remove(1);
        ((ArrayNode) oursComposition.path("semanticRelationships"))
                .add(syncRelNode("c1", "c2", 25));
        String oursJson = json(ours);

        // THEIRS: TimedText changed, Transition changed, Automation changed,
        // SourceBinding + TemporalMapping changed on an independent clip (c2).
        ObjectNode theirs = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ObjectNode theirsComposition = (ObjectNode) theirs.path("composition");
        theirsComposition.set("textElements", mapper.createArrayNode()
                .add(TimedTextCanonicalSemantics.toCanonicalNode(
                        com.example.platform.timeline.canonical.TestTextElements.textElement("t1"))
                        .deepCopy()));
        // TimedText edit: change the text content of the single element
        // (styledText.content is the scalar authored text — canonical shape).
        ObjectNode textEl = (ObjectNode) theirsComposition.path("textElements").get(0);
        ((com.fasterxml.jackson.databind.node.ObjectNode) textEl.path("styledText"))
                .put("content", "Hello 世界 👋 R4");
        ((ObjectNode) theirsComposition.path("transitions").get(0)).put("alignment", "START_AT_CUT");
        ((ObjectNode) theirsComposition.path("automations").get(0))
                .set("keyframes", mapper.createArrayNode()
                        .add(keyframeNode("kf-1", 0, 30, 0.75, "LINEAR"))
                        .add(keyframeNode("kf-2", 15, 30, 0.25, "HOLD")));
        // SourceBinding change on clip c2 (independent clip).
        ObjectNode c2 = (ObjectNode) theirsComposition.path("tracks").path(0).path("clips").path(1);
        c2.set("sourceBinding", sourceBindingNode("art-2", "b".repeat(64)));
        c2.set("temporalMapping",
                mapper.valueToTree(new FreezeTemporalMapping(MediaTime.ofTicks(15, 30))));
        String theirsJson = json(theirs);

        // ── THE ONE REAL THREE-WAY MERGE ──
        TimelineMergeResult result = merge(baseJson, oursJson, theirsJson);

        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status(),
                "independent edits across families must merge: " + result.summary());
        assertNotNull(result.mergedPayloadJson(), "merged payload must be produced");

        // canonical re-gate/reload of the merged payload
        TimelineCandidate reloaded = reload(result.mergedPayloadJson());
        var validation = com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator
                .validate(reloaded);
        assertTrue(!validation.hasFatalErrors(), "merged payload must re-pass the canonical gate");

        JsonNode mergedComposition = InternalTimelineJson.parse(result.mergedPayloadJson())
                .path("composition");

        // 1. Effect: OURS radius=8 survives
        JsonNode mergedFx = mergedComposition.path("tracks").path(0).path("clips").path(0)
                .path("effects").path(0);
        assertEquals(8, mergedFx.path("parameters").path("radius").asInt(),
                "OURS effect change must survive");
        // 2. Transition: THEIRS alignment change survives
        assertEquals("START_AT_CUT", mergedComposition.path("transitions").path(0)
                .path("alignment").asText(), "THEIRS transition change must survive");
        // 3. Automation: THEIRS two-keyframe curve survives
        assertEquals(2, mergedComposition.path("automations").path(0).path("keyframes").size(),
                "THEIRS automation keyframes must survive");
        assertEquals(0.75, mergedComposition.path("automations").path(0).path("keyframes").path(0)
                .path("value").asDouble(), "THEIRS automation keyframe value must survive");
        // 4. TimedText: THEIRS text change survives
        String mergedText = mergedComposition.path("textElements").path(0)
                .path("styledText").path("content").asText();
        assertEquals("Hello 世界 👋 R4", mergedText, "THEIRS timedtext change must survive");
        // 5. AudioMix: OURS gain 0.9 survives
        assertEquals(0.9, mergedComposition.path("audioMix").path("routes").path(0)
                .path("gain").path("linear").asDouble(), "OURS audio mix change must survive");
        // 6. SemanticRelationship: OURS sync anchor 25 survives (group g1 untouched)
        JsonNode mergedRels = mergedComposition.path("semanticRelationships");
        boolean groupSurvived = false;
        boolean syncAnchor25 = false;
        for (JsonNode rel : mergedRels) {
            if ("GROUP".equals(rel.path("kind").asText())
                    && "g1".equals(rel.path("groupId").path("value").asText())) {
                groupSurvived = true;
            }
            if ("SYNC".equals(rel.path("kind").asText())
                    && rel.path("localAnchorA").asText().equals("25/1")) {
                syncAnchor25 = true;
            }
        }
        assertTrue(groupSurvived, "base group relationship must survive");
        assertTrue(syncAnchor25, "OURS sync anchor edit must survive");
        // 7. TimelineSourceBinding: THEIRS art-2 binding on clip c2 survives
        assertEquals("art-2", mergedComposition.path("tracks").path(0).path("clips").path(1)
                .path("sourceBinding").path("artifactId").asText(),
                "THEIRS source binding must survive");
        // 8. TemporalMapping: THEIRS freeze on clip c2 survives
        assertEquals("FREEZE", mergedComposition.path("tracks").path(0).path("clips").path(1)
                .path("temporalMapping").path("kind").asText(),
                "THEIRS temporal mapping must survive");
        // BASE artifact/digest pin on clip c1 (untouched clip) survives exactly
        assertEquals("art-1", mergedComposition.path("tracks").path(0).path("clips").path(0)
                .path("sourceBinding").path("artifactId").asText(),
                "base artifact pin must survive exactly");
    }

    // ── REAL ENGINE CONFLICT TESTS FOR NEW FAMILIES ───────────────────────

    @Test
    void audioMixIdenticalChangeMerges() throws Exception {
        ObjectNode base = internalTimeline(trackNode("v1", clipNode("c1", 0, 2000)));
        ((ObjectNode) base.path("composition")).set("audioMix", audioMixNode(0.5));
        String baseJson = json(base);
        ObjectNode ours = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ObjectNode) ours.path("composition")).set("audioMix", audioMixNode(0.9));
        ObjectNode theirs = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ObjectNode) theirs.path("composition")).set("audioMix", audioMixNode(0.9));
        TimelineMergeResult result = merge(baseJson, json(ours), json(theirs));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status(),
                "identical bilateral AudioMix change must merge");
        assertEquals(0.9, InternalTimelineJson.parse(result.mergedPayloadJson())
                .path("composition").path("audioMix").path("routes").path(0)
                .path("gain").path("linear").asDouble());
    }

    @Test
    void audioMixDivergentChangeConflicts() throws Exception {
        ObjectNode base = internalTimeline(trackNode("v1", clipNode("c1", 0, 2000)));
        ((ObjectNode) base.path("composition")).set("audioMix", audioMixNode(0.5));
        String baseJson = json(base);
        ObjectNode ours = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ObjectNode) ours.path("composition")).set("audioMix", audioMixNode(0.9));
        ObjectNode theirs = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ObjectNode) theirs.path("composition")).set("audioMix", audioMixNode(0.1));
        TimelineMergeResult result = merge(baseJson, json(ours), json(theirs));
        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status(),
                "divergent AudioMix change must conflict through the REAL engine");
    }

    @Test
    void relationshipIndependentEditsMerge() throws Exception {
        ObjectNode base = internalTimeline(trackNode("v1", clipNode("c1", 0, 2000),
                clipNode("c2", 2000, 2000), clipNode("c3", 4000, 2000)));
        ((ObjectNode) base.path("composition")).set("semanticRelationships",
                mapper.createArrayNode().add(groupRelNode("g1", "c1", "c2")));
        String baseJson = json(base);
        ObjectNode ours = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ArrayNode) ours.path("composition").path("semanticRelationships"))
                .add(groupRelNode("g2", "c2", "c3"));
        ObjectNode theirs = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ArrayNode) theirs.path("composition").path("semanticRelationships"))
                .add(syncRelNode("c1", "c3", 5));
        TimelineMergeResult result = merge(baseJson, json(ours), json(theirs));
        assertEquals(TimelineMergeResult.MergeStatus.MERGED, result.status(),
                "independent relationship edits must merge: " + result.summary());
        assertEquals(3, InternalTimelineJson.parse(result.mergedPayloadJson())
                .path("composition").path("semanticRelationships").size(),
                "both new relationships must survive");
    }

    @Test
    void relationshipSameIdentityDivergentEditConflicts() throws Exception {
        ObjectNode base = internalTimeline(trackNode("v1", clipNode("c1", 0, 2000),
                clipNode("c2", 2000, 2000), clipNode("c3", 4000, 2000)));
        ((ObjectNode) base.path("composition")).set("semanticRelationships",
                mapper.createArrayNode().add(groupRelNode("g1", "c1", "c2")));
        String baseJson = json(base);
        ObjectNode ours = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ArrayNode) ours.path("composition").path("semanticRelationships"))
                .add(groupRelNode("g1", "c1", "c2", "c3"));
        ObjectNode theirs = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ArrayNode) theirs.path("composition").path("semanticRelationships"))
                .add(groupRelNode("g1", "c1", "c2", "c4"));
        TimelineMergeResult result = merge(baseJson, json(ours), json(theirs));
        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status(),
                "divergent edit of the same relationship identity must conflict");
    }

    @Test
    void relationshipDeleteVsModifyConflicts() throws Exception {
        ObjectNode base = internalTimeline(trackNode("v1", clipNode("c1", 0, 2000),
                clipNode("c2", 2000, 2000), clipNode("c3", 4000, 2000)));
        ((ObjectNode) base.path("composition")).set("semanticRelationships",
                mapper.createArrayNode().add(groupRelNode("g1", "c1", "c2")));
        String baseJson = json(base);
        ObjectNode ours = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ArrayNode) ours.path("composition").path("semanticRelationships")).remove(0);
        ObjectNode theirs = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ArrayNode) theirs.path("composition").path("semanticRelationships"))
                .add(groupRelNode("g1", "c1", "c2", "c3"));
        TimelineMergeResult result = merge(baseJson, json(ours), json(theirs));
        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status(),
                "relationship delete-vs-modify must conflict through the REAL engine");
    }

    @Test
    void sourceBindingDivergentReplacementConflicts() throws Exception {
        ObjectNode baseClip = clipNode("c1", 0, 2000);
        baseClip.set("sourceBinding", sourceBindingNode("art-1", "a".repeat(64)));
        ObjectNode base = internalTimeline(trackNode("v1", baseClip));
        String baseJson = json(base);
        ObjectNode ours = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ObjectNode) ours.path("composition").path("tracks").path(0).path("clips").path(0))
                .set("sourceBinding", sourceBindingNode("art-2", "b".repeat(64)));
        ObjectNode theirs = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ObjectNode) theirs.path("composition").path("tracks").path(0).path("clips").path(0))
                .set("sourceBinding", sourceBindingNode("art-3", "c".repeat(64)));
        TimelineMergeResult result = merge(baseJson, json(ours), json(theirs));
        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status(),
                "divergent source binding replacement must conflict through the REAL engine");
    }

    @Test
    void temporalMappingDivergentReplacementConflicts() throws Exception {
        ObjectNode baseClip = clipNode("c1", 0, 2000);
        baseClip.set("temporalMapping", mapper.valueToTree(
                ConstantRateTemporalMapping.of(30, 30, PlaybackDirection.FORWARD)));
        ObjectNode base = internalTimeline(trackNode("v1", baseClip));
        String baseJson = json(base);
        ObjectNode ours = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ObjectNode) ours.path("composition").path("tracks").path(0).path("clips").path(0))
                .set("temporalMapping",
                        mapper.valueToTree(new FreezeTemporalMapping(MediaTime.ofTicks(15, 30))));
        ObjectNode theirs = (ObjectNode) InternalTimelineJson.parse(baseJson);
        ((ObjectNode) theirs.path("composition").path("tracks").path(0).path("clips").path(0))
                .set("temporalMapping",
                        mapper.valueToTree(new FreezeTemporalMapping(MediaTime.ofTicks(20, 30))));
        TimelineMergeResult result = merge(baseJson, json(ours), json(theirs));
        assertEquals(TimelineMergeResult.MergeStatus.CONFLICTS, result.status(),
                "divergent temporal mapping replacement must conflict through the REAL engine");
    }
}
