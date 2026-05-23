package com.example.platform.render.app.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Entity-id-based diff summary between two Internal Timeline 1.0 documents.
 */
@Service
public class TimelineRevisionDiffService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public ChangeSummary summarize(String parentInternalJson, String newInternalJson) {
        try {
            if (parentInternalJson == null || parentInternalJson.isBlank()) {
                return summarizeInitial(newInternalJson);
            }
            JsonNode parent = InternalTimelineJson.parse(parentInternalJson);
            JsonNode current = InternalTimelineJson.parse(newInternalJson);
            if (!InternalTimelineJson.isInternalTimeline(parent) || !InternalTimelineJson.isInternalTimeline(current)) {
                return ChangeSummary.unsupported();
            }

            Map<String, JsonNode> parentTracks = indexTracks(parent);
            Map<String, JsonNode> currentTracks = indexTracks(current);
            Map<String, JsonNode> parentClips = indexClips(parent);
            Map<String, JsonNode> currentClips = indexClips(current);
            Map<String, JsonNode> parentAssets = indexAssets(parent);
            Map<String, JsonNode> currentAssets = indexAssets(current);

            Set<String> trackIds = union(parentTracks.keySet(), currentTracks.keySet());
            int tracksAdded = 0;
            int tracksRemoved = 0;
            int tracksModified = 0;
            for (String id : trackIds) {
                boolean inParent = parentTracks.containsKey(id);
                boolean inCurrent = currentTracks.containsKey(id);
                if (inParent && !inCurrent) {
                    tracksRemoved++;
                } else if (!inParent && inCurrent) {
                    tracksAdded++;
                } else if (inParent && clipSetChanged(parentTracks.get(id), currentTracks.get(id))) {
                    tracksModified++;
                }
            }

            Set<String> clipIds = union(parentClips.keySet(), currentClips.keySet());
            int clipsAdded = 0;
            int clipsRemoved = 0;
            int clipsModified = 0;
            for (String id : clipIds) {
                boolean inParent = parentClips.containsKey(id);
                boolean inCurrent = currentClips.containsKey(id);
                if (inParent && !inCurrent) {
                    clipsRemoved++;
                } else if (!inParent && inCurrent) {
                    clipsAdded++;
                } else if (inParent && !clipNodesEqual(parentClips.get(id), currentClips.get(id))) {
                    clipsModified++;
                }
            }

            Set<String> assetIds = union(parentAssets.keySet(), currentAssets.keySet());
            int assetsAdded = 0;
            int assetsRemoved = 0;
            for (String id : assetIds) {
                if (parentAssets.containsKey(id) && !currentAssets.containsKey(id)) {
                    assetsRemoved++;
                } else if (!parentAssets.containsKey(id) && currentAssets.containsKey(id)) {
                    assetsAdded++;
                }
            }

            int parentRev = InternalTimelineJson.revision(parent);
            int currentRev = InternalTimelineJson.revision(current);

            return new ChangeSummary(
                    true,
                    tracksAdded,
                    tracksRemoved,
                    tracksModified,
                    clipsAdded,
                    clipsRemoved,
                    clipsModified,
                    assetsAdded,
                    assetsRemoved,
                    parentRev,
                    currentRev);
        } catch (Exception e) {
            return ChangeSummary.unsupported();
        }
    }

    public String summarizeJson(String parentInternalJson, String newInternalJson) {
        try {
            ChangeSummary summary = summarize(parentInternalJson, newInternalJson);
            return MAPPER.writeValueAsString(summary);
        } catch (Exception e) {
            return "{}";
        }
    }

    public DetailedCompare compare(String fromInternalJson, String toInternalJson) {
        ChangeSummary summary = summarize(fromInternalJson, toInternalJson);
        if (!summary.supported()) {
            return new DetailedCompare(false, summary, List.of());
        }
        try {
            JsonNode from = InternalTimelineJson.parse(fromInternalJson);
            JsonNode to = InternalTimelineJson.parse(toInternalJson);
            List<EntityChange> changes = new ArrayList<>();
            diffEntityMap("track", indexTracks(from), indexTracks(to), changes, false);
            diffEntityMap("clip", indexClips(from), indexClips(to), changes, true);
            diffEntityMap("asset", indexAssets(from), indexAssets(to), changes, true);
            return new DetailedCompare(true, summary, changes);
        } catch (Exception e) {
            return new DetailedCompare(false, ChangeSummary.unsupported(), List.of());
        }
    }

    private static void diffEntityMap(
            String kind,
            Map<String, JsonNode> fromMap,
            Map<String, JsonNode> toMap,
            List<EntityChange> out,
            boolean deepEqual) {
        Set<String> ids = union(fromMap.keySet(), toMap.keySet());
        for (String id : ids) {
            boolean inFrom = fromMap.containsKey(id);
            boolean inTo = toMap.containsKey(id);
            if (inFrom && !inTo) {
                out.add(new EntityChange(kind, id, "removed"));
            } else if (!inFrom && inTo) {
                out.add(new EntityChange(kind, id, "added"));
            } else if (inFrom && inTo) {
                boolean equal = deepEqual
                        ? clipNodesEqual(fromMap.get(id), toMap.get(id))
                        : fromMap.get(id).equals(toMap.get(id));
                if (!equal) {
                    out.add(new EntityChange(kind, id, "modified"));
                }
            }
        }
    }

    private static ChangeSummary summarizeInitial(String newInternalJson) throws Exception {
        JsonNode current = InternalTimelineJson.parse(newInternalJson);
        Map<String, JsonNode> tracks = indexTracks(current);
        Map<String, JsonNode> clips = indexClips(current);
        Map<String, JsonNode> assets = indexAssets(current);
        return new ChangeSummary(
                true,
                tracks.size(),
                0,
                0,
                clips.size(),
                0,
                0,
                assets.size(),
                0,
                0,
                InternalTimelineJson.revision(current));
    }

    private static Map<String, JsonNode> indexTracks(JsonNode root) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        JsonNode tracks = root.path("composition").path("tracks");
        if (tracks.isArray()) {
            for (JsonNode track : tracks) {
                String id = track.path("id").asText("");
                if (!id.isBlank()) {
                    map.put(id, track);
                }
            }
        }
        return map;
    }

    private static Map<String, JsonNode> indexClips(JsonNode root) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        JsonNode tracks = root.path("composition").path("tracks");
        if (tracks.isArray()) {
            for (JsonNode track : tracks) {
                JsonNode clips = track.path("clips");
                if (!clips.isArray()) {
                    continue;
                }
                for (JsonNode clip : clips) {
                    String id = clip.path("id").asText("");
                    if (!id.isBlank()) {
                        map.put(id, clip);
                    }
                }
            }
        }
        return map;
    }

    private static Map<String, JsonNode> indexAssets(JsonNode root) {
        Map<String, JsonNode> map = new LinkedHashMap<>();
        JsonNode assets = root.path("assetRegistry").path("assets");
        if (assets.isObject()) {
            assets.fields().forEachRemaining(e -> map.put(e.getKey(), e.getValue()));
        }
        return map;
    }

    private static boolean clipSetChanged(JsonNode parentTrack, JsonNode currentTrack) {
        Set<String> parentClipIds = new HashSet<>();
        Set<String> currentClipIds = new HashSet<>();
        JsonNode pClips = parentTrack.path("clips");
        JsonNode cClips = currentTrack.path("clips");
        if (pClips.isArray()) {
            pClips.forEach(c -> parentClipIds.add(c.path("id").asText("")));
        }
        if (cClips.isArray()) {
            cClips.forEach(c -> currentClipIds.add(c.path("id").asText("")));
        }
        return !parentClipIds.equals(currentClipIds);
    }

    private static boolean clipNodesEqual(JsonNode a, JsonNode b) {
        try {
            ObjectNode na = (ObjectNode) a.deepCopy();
            ObjectNode nb = (ObjectNode) b.deepCopy();
            return InternalTimelineJson.write(InternalTimelineJson.deepCanonicalize(na))
                    .equals(InternalTimelineJson.write(InternalTimelineJson.deepCanonicalize(nb)));
        } catch (Exception e) {
            return false;
        }
    }

    private static Set<String> union(Set<String> a, Set<String> b) {
        Set<String> all = new HashSet<>(a);
        all.addAll(b);
        return all;
    }

    public record ChangeSummary(
            boolean supported,
            int tracksAdded,
            int tracksRemoved,
            int tracksModified,
            int clipsAdded,
            int clipsRemoved,
            int clipsModified,
            int assetsAdded,
            int assetsRemoved,
            int parentInternalRevision,
            int currentInternalRevision) {

        public static ChangeSummary unsupported() {
            return new ChangeSummary(false, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
        }

        public int totalTrackChanges() {
            return tracksAdded + tracksRemoved + tracksModified;
        }

        public int totalClipChanges() {
            return clipsAdded + clipsRemoved + clipsModified;
        }
    }

    public record EntityChange(String kind, String entityId, String action) {}

    public record DetailedCompare(boolean supported, ChangeSummary summary, List<EntityChange> entities) {}
}
