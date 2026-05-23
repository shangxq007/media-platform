package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.internal.EntityKind;
import com.example.platform.render.domain.timeline.internal.EntityRef;
import com.example.platform.render.domain.timeline.internal.SemanticChange;
import com.example.platform.render.domain.timeline.internal.SemanticChangeType;
import com.example.platform.render.domain.timeline.internal.SemanticDiffResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.stereotype.Service;

/**
 * Semantic diff between two timelines by stable entity id (after canonicalization).
 */
@Service
public class TimelineSemanticDiffService {

    private final TimelineCanonicalizer canonicalizer;

    public TimelineSemanticDiffService(TimelineCanonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    public SemanticDiffResult diff(String oldJson, String newJson) throws java.io.IOException {
        JsonNode oldRoot = InternalTimelineJson.deepCanonicalize(
                InternalTimelineJson.parse(canonicalizer.canonicalize(oldJson).timelineJson()));
        JsonNode newRoot = InternalTimelineJson.deepCanonicalize(
                InternalTimelineJson.parse(canonicalizer.canonicalize(newJson).timelineJson()));

        boolean semanticallyEqual = InternalTimelineJson.jsonEqualsIgnoringRevision(oldRoot, newRoot);
        boolean byteEqual = InternalTimelineJson.jsonEquals(oldRoot, newRoot);
        List<SemanticChange> changes = new ArrayList<>();

        if (semanticallyEqual) {
            int oldRev = InternalTimelineJson.revision(oldRoot);
            int newRev = InternalTimelineJson.revision(newRoot);
            if (oldRev != newRev) {
                changes.add(SemanticChange.of(
                        SemanticChangeType.REVISION_ONLY,
                        new EntityRef(EntityKind.PROJECT, InternalTimelineJson.timelineId(newRoot)),
                        "revision " + oldRev + " -> " + newRev));
            }
            return buildResult(oldRoot, newRoot, changes, byteEqual);
        }

        Map<String, JsonNode> oldIndex = TimelineEntityIndex.indexAll(oldRoot);
        Map<String, JsonNode> newIndex = TimelineEntityIndex.indexAll(newRoot);
        Set<String> allKeys = new TreeSet<>();
        allKeys.addAll(oldIndex.keySet());
        allKeys.addAll(newIndex.keySet());

        for (String key : allKeys) {
            JsonNode oldEntity = oldIndex.get(key);
            JsonNode newEntity = newIndex.get(key);
            EntityRef ref = parseRef(key);
            if (oldEntity == null) {
                changes.addAll(classifyAdded(ref, newEntity));
            } else if (newEntity == null) {
                changes.addAll(classifyRemoved(ref));
            } else if (!entityEquals(oldEntity, newEntity)) {
                changes.addAll(classifyModified(ref, oldEntity, newEntity));
            }
        }

        return buildResult(oldRoot, newRoot, changes, false);
    }

    private SemanticDiffResult buildResult(JsonNode oldRoot, JsonNode newRoot,
                                           List<SemanticChange> changes, boolean structurallyEqual) {
        return new SemanticDiffResult(
                InternalTimelineJson.timelineId(oldRoot),
                InternalTimelineJson.timelineId(newRoot),
                InternalTimelineJson.revision(oldRoot),
                InternalTimelineJson.revision(newRoot),
                InternalTimelineJson.schemaVersion(newRoot),
                List.copyOf(changes),
                structurallyEqual);
    }

    private static EntityRef parseRef(String key) {
        int sep = key.indexOf(':');
        EntityKind kind = EntityKind.valueOf(key.substring(0, sep));
        String id = key.substring(sep + 1);
        return new EntityRef(kind, id);
    }

    private static boolean entityEquals(JsonNode a, JsonNode b) {
        try {
            return InternalTimelineJson.jsonEquals(a, b);
        } catch (java.io.IOException e) {
            return a.equals(b);
        }
    }

    private List<SemanticChange> classifyAdded(EntityRef ref, JsonNode entity) {
        return switch (ref.kind()) {
            case CLIP -> List.of(SemanticChange.of(SemanticChangeType.CLIP_ADDED, ref, "clip added"));
            case LAYER -> List.of(SemanticChange.of(SemanticChangeType.LAYER_ADDED, ref, "layer added"));
            case ASSET -> List.of(SemanticChange.of(SemanticChangeType.ASSET_URI_CHANGED, ref, "asset added"));
            default -> List.of(SemanticChange.of(SemanticChangeType.UNKNOWN, ref, "entity added"));
        };
    }

    private List<SemanticChange> classifyRemoved(EntityRef ref) {
        return switch (ref.kind()) {
            case CLIP -> List.of(SemanticChange.of(SemanticChangeType.CLIP_REMOVED, ref, "clip removed"));
            case LAYER -> List.of(SemanticChange.of(SemanticChangeType.LAYER_REMOVED, ref, "layer removed"));
            default -> List.of(SemanticChange.of(SemanticChangeType.UNKNOWN, ref, "entity removed"));
        };
    }

    private List<SemanticChange> classifyModified(EntityRef ref, JsonNode oldE, JsonNode newE) {
        List<SemanticChange> out = new ArrayList<>();
        switch (ref.kind()) {
            case PROJECT -> out.addAll(classifyProject(ref, oldE, newE));
            case ASSET -> out.addAll(classifyAsset(ref, oldE, newE));
            case CLIP -> out.addAll(classifyClip(ref, oldE, newE));
            case LAYER -> out.addAll(classifyLayer(ref, oldE, newE));
            case SUBTITLE_TRACK -> out.add(SemanticChange.of(SemanticChangeType.SUBTITLE_CUE_CHANGED, ref,
                    "subtitle track changed"));
            case STYLE -> out.add(SemanticChange.of(SemanticChangeType.SUBTITLE_STYLE_CHANGED, ref,
                    "style changed"));
            case AUDIO_BUS -> out.add(SemanticChange.of(SemanticChangeType.AUDIO_BUS_CHANGED, ref,
                    "audio bus changed"));
            case AUDIO_MIX -> out.add(SemanticChange.of(SemanticChangeType.AUDIO_STEM_CHANGED, ref,
                    "audio mix changed"));
            case EXTERNAL_NODE -> out.add(SemanticChange.of(SemanticChangeType.EXTERNAL_NODE_CHANGED, ref,
                    "external render node changed"));
            case TRANSITION -> out.add(SemanticChange.of(SemanticChangeType.TRANSITION_CHANGED, ref,
                    "transition changed"));
            case OUTPUT -> out.add(SemanticChange.of(SemanticChangeType.OUTPUT_PROFILE_CHANGED, ref,
                    "output profile changed"));
            case PACKAGING -> out.add(SemanticChange.of(SemanticChangeType.PACKAGING_PARAM_CHANGED, ref,
                    "packaging changed"));
            case FINAL_COMPOSER -> out.add(SemanticChange.of(SemanticChangeType.FINAL_COMPOSER_CHANGED, ref,
                    "final composer changed"));
            default -> out.add(SemanticChange.of(SemanticChangeType.UNKNOWN, ref, "entity modified"));
        }
        return out;
    }

    private List<SemanticChange> classifyProject(EntityRef ref, JsonNode oldE, JsonNode newE) {
        List<SemanticChange> out = new ArrayList<>();
        if (!jsonFieldEquals(oldE, newE, "width") || !jsonFieldEquals(oldE, newE, "height")) {
            out.add(SemanticChange.of(SemanticChangeType.PROJECT_RESOLUTION_CHANGED, ref,
                    "resolution changed"));
        }
        if (!jsonFieldEquals(oldE, newE, "frameRate")) {
            out.add(SemanticChange.of(SemanticChangeType.PROJECT_TIMEBASE_CHANGED, ref,
                    "frame rate / timebase changed"));
        }
        if (!nodesEqual(oldE.path("color"), newE.path("color"))) {
            out.add(SemanticChange.of(SemanticChangeType.PROJECT_COLOR_CHANGED, ref, "color changed"));
        }
        if (out.isEmpty()) {
            out.add(SemanticChange.of(SemanticChangeType.UNKNOWN, ref, "project changed"));
        }
        return out;
    }

    private List<SemanticChange> classifyAsset(EntityRef ref, JsonNode oldE, JsonNode newE) {
        List<SemanticChange> out = new ArrayList<>();
        if (!textEquals(oldE, newE, "uri")) {
            out.add(SemanticChange.of(SemanticChangeType.ASSET_URI_CHANGED, ref, "asset uri changed"));
        }
        if (!jsonFieldEquals(oldE.path("probe"), newE.path("probe"), "")) {
            out.add(SemanticChange.of(SemanticChangeType.ASSET_PROBE_CHANGED, ref, "asset probe changed"));
        }
        if (out.isEmpty()) {
            out.add(SemanticChange.of(SemanticChangeType.ASSET_PROBE_CHANGED, ref, "asset metadata changed"));
        }
        return out;
    }

    private List<SemanticChange> classifyClip(EntityRef ref, JsonNode oldE, JsonNode newE) {
        List<SemanticChange> out = new ArrayList<>();
        if (!jsonFieldEquals(oldE, newE, "timelineRange") || !jsonFieldEquals(oldE, newE, "sourceRange")) {
            out.add(SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, ref, "clip range changed"));
        }
        if (!jsonFieldEquals(oldE, newE, "speed")) {
            out.add(SemanticChange.of(SemanticChangeType.CLIP_SPEED_CHANGED, ref, "clip speed changed"));
        }
        if (!jsonFieldEquals(oldE, newE, "effects")) {
            out.add(SemanticChange.of(SemanticChangeType.CLIP_EFFECT_CHANGED, ref, "clip effects changed"));
        }
        if (!textEquals(oldE, newE, "assetId")) {
            out.add(SemanticChange.of(SemanticChangeType.ASSET_URI_CHANGED,
                    new EntityRef(EntityKind.ASSET, newE.path("assetId").asText("")), "clip asset changed"));
        }
        if (out.isEmpty()) {
            out.add(SemanticChange.of(SemanticChangeType.CLIP_RANGE_CHANGED, ref, "clip changed"));
        }
        return out;
    }

    private List<SemanticChange> classifyLayer(EntityRef ref, JsonNode oldE, JsonNode newE) {
        List<SemanticChange> out = new ArrayList<>();
        if (!jsonFieldEquals(oldE, newE, "transform") || !jsonFieldEquals(oldE, newE, "zIndex")) {
            out.add(SemanticChange.of(SemanticChangeType.LAYER_TRANSFORM_CHANGED, ref, "layer transform changed"));
        }
        if (!jsonFieldEquals(oldE, newE, "stickers") || !jsonFieldEquals(oldE, newE, "subtitleTrackId")
                || !jsonFieldEquals(oldE, newE, "cues")) {
            out.add(SemanticChange.of(SemanticChangeType.LAYER_CONTENT_CHANGED, ref, "layer content changed"));
        }
        if (out.isEmpty()) {
            out.add(SemanticChange.of(SemanticChangeType.LAYER_CONTENT_CHANGED, ref, "layer changed"));
        }
        return out;
    }

    private static boolean jsonFieldEquals(JsonNode oldE, JsonNode newE, String field) {
        return nodesEqual(oldE.path(field), newE.path(field));
    }

    private static boolean nodesEqual(JsonNode oldF, JsonNode newF) {
        if (oldF.isMissingNode() && newF.isMissingNode()) {
            return true;
        }
        try {
            return InternalTimelineJson.jsonEquals(oldF, newF);
        } catch (java.io.IOException e) {
            return oldF.equals(newF);
        }
    }

    private static boolean textEquals(JsonNode oldE, JsonNode newE, String field) {
        return oldE.path(field).asText("").equals(newE.path(field).asText(""));
    }
}
