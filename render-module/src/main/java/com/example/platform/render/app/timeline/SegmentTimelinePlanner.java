package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.SegmentPolicy;
import com.example.platform.render.domain.timeline.TimelineSegment;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.internal.SemanticChangeType;
import com.example.platform.render.domain.timeline.internal.SemanticDiffResult;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Plans segment windows and maps semantic diffs to dirty segment task ids.
 */
@Service
public class SegmentTimelinePlanner {

    public static final String META_SEGMENT_POLICY = "platform.segmentPolicy";

    public Optional<SegmentPlan> planFromTimelineJson(String timelineJson) {
        try {
            JsonNode root = InternalTimelineJson.parse(timelineJson);
            return planFromRoot(root);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    public Optional<SegmentPlan> planFromSpec(TimelineSpec spec, String timelineJson) {
        if (spec.metadata() != null && spec.metadata().containsKey(META_SEGMENT_POLICY)) {
            try {
                JsonNode policyNode = InternalTimelineJson.mapper()
                        .readTree(spec.metadata().get(META_SEGMENT_POLICY));
                return planFromPolicyNode(InternalTimelineJson.parse(timelineJson), policyNode);
            } catch (Exception ignored) {
                // fall through
            }
        }
        return planFromTimelineJson(timelineJson);
    }

    public Optional<SegmentPlan> planFromRoot(JsonNode root) {
        JsonNode policyNode = root.path("renderGraph").path("segmentPolicy");
        return planFromPolicyNode(root, policyNode);
    }

    private Optional<SegmentPlan> planFromPolicyNode(JsonNode root, JsonNode policyNode) {
        if (!policyNode.path("enabled").asBoolean(false)) {
            return Optional.empty();
        }
        int segmentFrames = policyNode.path("segmentDuration").path("frame").asInt(120);
        if (segmentFrames <= 0) {
            segmentFrames = 120;
        }
        int overlap = policyNode.path("overlapFrames").asInt(0);
        int fps = frameRate(root);
        int totalFrames = projectDurationFrames(root, fps);
        if (totalFrames <= 0) {
            totalFrames = segmentFrames;
        }
        int revision = InternalTimelineJson.revision(root);
        String timelineId = InternalTimelineJson.timelineId(root);
        String cacheScope = policyNode.path("cacheScope").asText("SEGMENT");

        List<TimelineSegment> segments = new ArrayList<>();
        int index = 0;
        int cursor = 0;
        while (cursor < totalFrames) {
            int duration = Math.min(segmentFrames, totalFrames - cursor);
            String id = "seg_" + index;
            String cacheKey = "segment:" + timelineId + ":" + id + ":r" + revision + ":" + cacheScope;
            segments.add(new TimelineSegment(id, cursor, duration, cacheKey));
            cursor += Math.max(1, segmentFrames - overlap);
            index++;
        }
        SegmentPolicy policy = new SegmentPolicy(true, segmentFrames, overlap, cacheScope);
        return Optional.of(new SegmentPlan(policy, List.copyOf(segments)));
    }

    /**
     * Segment task ids that must re-execute (e.g. {@code seg_2}).
     */
    public Set<String> dirtySegmentTaskIds(SemanticDiffResult diff,
                                          String newTimelineJson,
                                          SegmentPlan segmentPlan) {
        Set<String> dirty = new LinkedHashSet<>();
        if (diff == null || segmentPlan == null || segmentPlan.segments().isEmpty()) {
            return dirty;
        }
        boolean segmentRelated = diff.changes().stream().anyMatch(c ->
                c.type() == SemanticChangeType.CLIP_ADDED
                        || c.type() == SemanticChangeType.CLIP_REMOVED
                        || c.type() == SemanticChangeType.CLIP_RANGE_CHANGED
                        || c.type() == SemanticChangeType.CLIP_EFFECT_CHANGED
                        || c.type() == SemanticChangeType.LAYER_CONTENT_CHANGED
                        || c.type() == SemanticChangeType.LAYER_TRANSFORM_CHANGED
                        || c.type() == SemanticChangeType.SUBTITLE_CUE_CHANGED
                        || c.type() == SemanticChangeType.SUBTITLE_STYLE_CHANGED
                        || c.type() == SemanticChangeType.EXTERNAL_NODE_CHANGED
                        || c.type() == SemanticChangeType.PROJECT_TIMEBASE_CHANGED);
        if (!segmentRelated) {
            return dirty;
        }
        if (diff.changes().stream().anyMatch(c ->
                c.type() == SemanticChangeType.PROJECT_TIMEBASE_CHANGED
                        || c.type() == SemanticChangeType.PROJECT_RESOLUTION_CHANGED)) {
            segmentPlan.segments().forEach(s -> dirty.add(s.id()));
            return dirty;
        }
        try {
            JsonNode root = InternalTimelineJson.parse(newTimelineJson);
            int fps = frameRate(root);
            for (var change : diff.changes()) {
                if (change.entity() == null) {
                    continue;
                }
                int frame = entityMidFrame(root, change.entity().id(), fps);
                if (frame < 0) {
                    segmentPlan.segments().forEach(s -> dirty.add(s.id()));
                    continue;
                }
                for (TimelineSegment segment : segmentPlan.segments()) {
                    if (frame >= segment.startFrame()
                            && frame < segment.startFrame() + segment.durationFrames()) {
                        dirty.add(segment.id());
                    }
                }
            }
        } catch (Exception e) {
            segmentPlan.segments().forEach(s -> dirty.add(s.id()));
        }
        if (dirty.isEmpty()) {
            dirty.add(segmentPlan.segments().get(segmentPlan.segments().size() - 1).id());
        }
        return dirty;
    }

    private static int entityMidFrame(JsonNode root, String entityId, int fps) {
        JsonNode composition = root.path("composition");
        JsonNode tracks = composition.path("tracks");
        if (!tracks.isArray()) {
            return -1;
        }
        for (JsonNode track : tracks) {
            JsonNode clips = track.path("clips");
            if (!clips.isArray()) {
                continue;
            }
            for (JsonNode clip : clips) {
                if (!entityId.equals(clip.path("id").asText())) {
                    continue;
                }
                int start = clip.path("timelineRange").path("start").path("frame").asInt(0);
                int dur = clip.path("timelineRange").path("duration").path("frame").asInt(0);
                return start + Math.max(0, dur / 2);
            }
        }
        return -1;
    }

    private static int frameRate(JsonNode root) {
        JsonNode rate = root.path("project").path("frameRate");
        if (rate.has("num") && rate.has("den") && rate.get("den").asInt(1) > 0) {
            return Math.max(1, rate.get("num").asInt(30) / rate.get("den").asInt(1));
        }
        return 30;
    }

    private static int projectDurationFrames(JsonNode root, int fps) {
        JsonNode duration = root.path("project").path("duration");
        if (duration.has("frame")) {
            return duration.get("frame").asInt(fps * 30);
        }
        return fps * 30;
    }

    public record SegmentPlan(SegmentPolicy policy, List<TimelineSegment> segments) {}
}
