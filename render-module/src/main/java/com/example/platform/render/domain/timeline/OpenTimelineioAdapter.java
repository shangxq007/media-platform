package com.example.platform.render.domain.timeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * OpenTimelineIO JSON interchange (canonical model remains {@link TimelineSpec}).
 *
 * <p>Supports Clip, Gap, Transition, Marker, Effect, and {@code platform.*} metadata.</p>
 */
public final class OpenTimelineioAdapter {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String NS = "platform.";

    private OpenTimelineioAdapter() {
    }

    public static String toOtioJson(TimelineSpec timeline) {
        return toOtioJson(timeline, TimelineExtensions.defaults());
    }

    public static String toOtioJson(TimelineSpec timeline, TimelineExtensions extensions) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("OTIO_SCHEMA", "Timeline.1");
            root.put("id", timeline.id());
            root.put("name", timeline.name());

            ObjectNode metadata = root.putObject("metadata");
            if (timeline.metadata() != null) {
                timeline.metadata().forEach(metadata::put);
            }
            metadata.put(NS + "schemaVersion", extensions.schemaVersion());
            metadata.put(NS + "finalComposer", extensions.finalComposer().name().toLowerCase());
            if (extensions.otioExportLossy()) {
                metadata.put(NS + "otio.exportLossy", "true");
            }
            if (!extensions.externalRenderNodes().isEmpty()) {
                metadata.set(NS + "externalRenderNodes", MAPPER.valueToTree(extensions.externalRenderNodes()));
            }

            ArrayNode tracks = root.putArray("tracks");
            if (timeline.tracks() != null) {
                for (TimelineTrack track : timeline.tracks()) {
                    ObjectNode trackNode = tracks.addObject();
                    trackNode.put("OTIO_SCHEMA", "Track.1");
                    trackNode.put("id", track.id());
                    trackNode.put("name", track.name());
                    trackNode.put("kind", mapTrackKind(track.type()));
                    ArrayNode children = trackNode.putArray("children");
                    if (track.clips() != null) {
                        TimelineClip prev = null;
                        for (TimelineClip clip : track.clips()) {
                            if (prev != null) {
                                appendTransitionIfAny(children, prev, clip);
                            }
                            appendClipOrGap(children, clip);
                            prev = clip;
                        }
                    }
                }
            }

            if (extensions.markers() != null && !extensions.markers().isEmpty()) {
                ArrayNode markersArr = root.putArray("markers");
                for (TimelineMarker marker : extensions.markers()) {
                    ObjectNode m = markersArr.addObject();
                    m.put("OTIO_SCHEMA", "Marker.1");
                    m.put("name", marker.name());
                    m.set("marked_range", formatRational(marker.timeSeconds(), 1.0));
                    if (marker.comment() != null) {
                        m.putObject("metadata").put("comment", marker.comment());
                    }
                }
            }

            if (timeline.outputSpec() != null) {
                root.set("outputSpec", MAPPER.valueToTree(timeline.outputSpec()));
            }
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to export OTIO JSON", e);
        }
    }

    public static TimelineSpec fromOtioJson(String otioJson) {
        return new TimelineScriptParser(new TimelineExtensionsReader()).parse(otioJson)
                .orElseThrow(() -> new IllegalArgumentException("Invalid OTIO/timeline JSON"));
    }

    public static OtioImportResult importWithReport(String otioJson) {
        TimelineSpec spec = fromOtioJson(otioJson);
        TimelineExtensionsReader reader = new TimelineExtensionsReader();
        TimelineExtensions ext = reader.fromSpec(spec);
        List<String> warnings = new ArrayList<>();
        if (ext.otioExportLossy() || "true".equals(spec.metadata().get(NS + "otio.exportLossy"))) {
            warnings.add("Timeline may contain lossy round-trip fields; check platform.* metadata");
        }
        if (!ext.externalRenderNodes().isEmpty()) {
            warnings.add("External render nodes preserved in metadata only");
        }
        return new OtioImportResult(spec, ext, warnings);
    }

    private static void appendClipOrGap(ArrayNode children, TimelineClip clip) {
        if (clip.assetRef() == null || clip.assetRef().storageUri() == null
                || clip.assetRef().storageUri().isBlank()) {
            ObjectNode gap = children.addObject();
            gap.put("OTIO_SCHEMA", "Gap.1");
            gap.put("name", clip.id());
            gap.set("source_range", formatRational(0, clip.clipDuration()));
            return;
        }
        ObjectNode clipNode = children.addObject();
        clipNode.put("OTIO_SCHEMA", "Clip.1");
        clipNode.put("id", clip.id());
        clipNode.put("name", clip.id());
        ObjectNode mediaRef = clipNode.putObject("media_reference");
        mediaRef.put("OTIO_SCHEMA", "ExternalReference.1");
        mediaRef.put("target_url", clip.assetRef().storageUri());
        ObjectNode sourceRange = clipNode.putObject("source_range");
        sourceRange.set("start_time", formatRational(clip.assetInPoint(), 1.0));
        sourceRange.set("duration", formatRational(clip.clipDuration(), 1.0));
        clipNode.put("timeline_start", clip.timelineStart());
        appendEffects(clipNode, clip);
    }

    private static void appendTransitionIfAny(ArrayNode children, TimelineClip prev, TimelineClip next) {
        boolean hasDissolve = prev.effects() != null && prev.effects().stream()
                .anyMatch(e -> "video.cross_dissolve".equals(e.effectKey()));
        if (!hasDissolve) {
            return;
        }
        ObjectNode transition = children.addObject();
        transition.put("OTIO_SCHEMA", "Transition.1");
        transition.put("name", "cross_dissolve");
        transition.put("transition_type", "cross_dissolve");
        ObjectNode inOffset = transition.putObject("in_offset");
        inOffset.put("value", 0);
        inOffset.put("rate", 1);
        ObjectNode outOffset = transition.putObject("out_offset");
        outOffset.put("value", 0.5);
        outOffset.put("rate", 1);
        transition.putObject("metadata").put(NS + "effectKey", "video.cross_dissolve");
    }

    private static void appendEffects(ObjectNode clipNode, TimelineClip clip) {
        if (clip.effects() == null || clip.effects().isEmpty()) {
            return;
        }
        ArrayNode effects = clipNode.putArray("effects");
        for (TimelineClipEffect effect : clip.effects()) {
            ObjectNode eff = effects.addObject();
            eff.put("OTIO_SCHEMA", "Effect.1");
            eff.put("name", effect.effectKey());
            eff.putObject("metadata").put(NS + "effectKey", effect.effectKey());
            if (effect.parameters() != null) {
                eff.set("parameters", MAPPER.valueToTree(effect.parameters()));
            }
        }
    }

    private static String mapTrackKind(TimelineTrack.TrackType type) {
        return switch (type) {
            case AUDIO -> "Audio";
            case SUBTITLE -> "Text";
            default -> "Video";
        };
    }

    private static ObjectNode formatRational(double value, double rate) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("OTIO_SCHEMA", "RationalTime.1");
        node.put("value", value);
        node.put("rate", rate);
        return node;
    }

    public record OtioImportResult(
            TimelineSpec timeline,
            TimelineExtensions extensions,
            List<String> warnings) {}
}
