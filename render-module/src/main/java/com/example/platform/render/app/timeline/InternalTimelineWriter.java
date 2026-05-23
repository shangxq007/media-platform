package com.example.platform.render.app.timeline;

import com.example.platform.render.domain.timeline.ExternalRenderNode;
import com.example.platform.render.domain.timeline.FinalComposerHint;
import com.example.platform.render.domain.timeline.TimelineClip;
import com.example.platform.render.domain.timeline.TimelineClipEffect;
import com.example.platform.render.domain.timeline.TimelineExtensions;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineOutputSpec;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineTextOverlay;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

/**
 * Writes {@link TimelineSpec} (+ extensions) as Internal Timeline Schema 1.0 JSON.
 */
@Service
public class InternalTimelineWriter {

    private final TimelineExtensionsReader extensionsReader;

    public InternalTimelineWriter(TimelineExtensionsReader extensionsReader) {
        this.extensionsReader = extensionsReader;
    }

    public String toJson(TimelineSpec spec) {
        return toJson(spec, extensionsReader.fromSpec(spec));
    }

    public String toJson(TimelineSpec spec, TimelineExtensions extensions) {
        try {
            int fps = spec.outputSpec() != null ? (int) spec.outputSpec().frameRate() : 30;
            if (fps <= 0) {
                fps = 30;
            }
            ObjectNode root = InternalTimelineJson.mapper().createObjectNode();
            root.put("schemaVersion", InternalTimelineJson.SCHEMA_V1);
            root.put("id", spec.id());
            root.put("name", spec.name() != null ? spec.name() : spec.id());
            int revision = 1;
            if (spec.metadata() != null && spec.metadata().containsKey("revision")) {
                try {
                    revision = Integer.parseInt(spec.metadata().get("revision"));
                } catch (NumberFormatException ignored) {
                    revision = 1;
                }
            }
            root.put("revision", revision);

            root.set("project", buildProject(spec, fps));
            root.set("assetRegistry", buildAssetRegistry(spec, fps));
            ObjectNode composition = buildComposition(spec, fps);
            root.set("composition", composition);
            root.set("styles", buildStyles(spec, extensions));
            root.set("templates", buildTemplates(spec, extensions));
            root.set("renderGraph", buildRenderGraph(spec, extensions, fps, composition));
            root.set("outputs", buildOutputs(spec));
            if (!extensions.packagingHints().isEmpty()) {
                ObjectNode packaging = InternalTimelineJson.mapper().createObjectNode();
                packaging.put("id", "packaging");
                extensions.packagingHints().forEach(packaging::put);
                root.set("packaging", packaging);
            }

            root.set("metadata", buildMetadata(spec, extensions));
            if (spec.metadata() != null && spec.metadata().containsKey("tenantId")) {
                root.put("tenantId", spec.metadata().get("tenantId"));
            }

            return InternalTimelineJson.write(InternalTimelineJson.deepCanonicalize(root));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write Internal Timeline 1.0 JSON", e);
        }
    }

    private ObjectNode buildProject(TimelineSpec spec, int fps) {
        ObjectNode project = InternalTimelineJson.mapper().createObjectNode();
        project.put("id", spec.id() + "_project");
        TimelineOutputSpec out = spec.outputSpec();
        int w = out != null ? out.width() : 1920;
        int h = out != null ? out.height() : 1080;
        project.put("width", w);
        project.put("height", h);
        project.set("frameRate", rationalRate(fps, 1));
        double durationSec = spec.computeDuration() > 0 ? spec.computeDuration() : 30;
        project.set("duration", frameRange(0, durationSec, fps));
        return project;
    }

    private ObjectNode buildAssetRegistry(TimelineSpec spec, int fps) {
        ObjectNode registry = InternalTimelineJson.mapper().createObjectNode();
        ObjectNode assets = InternalTimelineJson.mapper().createObjectNode();
        Set<String> seen = new LinkedHashSet<>();
        if (spec.tracks() != null) {
            for (TimelineTrack track : spec.tracks()) {
                if (track.clips() == null) {
                    continue;
                }
                for (TimelineClip clip : track.clips()) {
                    if (clip.assetRef() == null || clip.assetRef().assetId() == null) {
                        continue;
                    }
                    String assetId = clip.assetRef().assetId();
                    if (!seen.add(assetId)) {
                        continue;
                    }
                    ObjectNode ast = InternalTimelineJson.mapper().createObjectNode();
                    ast.put("id", assetId);
                    ast.put("kind", track.type() == TimelineTrack.TrackType.AUDIO ? "AUDIO" : "VIDEO");
                    ast.put("uri", clip.assetRef().storageUri() != null
                            ? clip.assetRef().storageUri() : "asset://" + assetId);
                    ObjectNode probe = InternalTimelineJson.mapper().createObjectNode();
                    probe.put("width", clip.assetRef().width() > 0 ? clip.assetRef().width() : 1920);
                    probe.put("height", clip.assetRef().height() > 0 ? clip.assetRef().height() : 1080);
                    probe.set("duration", frameRange(0, clip.clipDuration(), fps));
                    ast.set("probe", probe);
                    assets.set(assetId, ast);
                }
            }
        }
        registry.set("assets", assets);
        return registry;
    }

    private ObjectNode buildComposition(TimelineSpec spec, int fps) {
        ObjectNode composition = InternalTimelineJson.mapper().createObjectNode();
        ArrayNode tracks = InternalTimelineJson.mapper().createArrayNode();
        if (spec.tracks() != null) {
            for (TimelineTrack track : spec.tracks()) {
                ObjectNode trk = InternalTimelineJson.mapper().createObjectNode();
                trk.put("id", track.id());
                trk.put("type", track.type().name());
                trk.put("role", "primary");
                trk.put("zIndex", track.layer());
                ArrayNode clips = InternalTimelineJson.mapper().createArrayNode();
                if (track.clips() != null) {
                    for (TimelineClip clip : track.clips()) {
                        ObjectNode clipNode = InternalTimelineJson.mapper().createObjectNode();
                        clipNode.put("id", clip.id());
                        String assetId = clip.assetRef() != null && clip.assetRef().assetId() != null
                                ? clip.assetRef().assetId() : "ast_" + clip.id();
                        clipNode.put("assetId", assetId);
                        clipNode.set("timelineRange",
                                frameRange(clip.timelineStart(), clip.clipDuration(), fps));
                        clipNode.set("sourceRange",
                                frameRange(clip.assetInPoint(), clip.assetOutPoint() - clip.assetInPoint(), fps));
                        clipNode.set("speed", InternalTimelineJson.mapper().createObjectNode()
                                .put("factor", 1.0));
                        if (clip.effects() != null && !clip.effects().isEmpty()) {
                            clipNode.set("effects", buildClipEffects(clip.effects()));
                        }
                        clips.add(clipNode);
                    }
                }
                trk.set("clips", clips);
                tracks.add(trk);
            }
        }
        composition.set("tracks", tracks);

        if (spec.textOverlays() != null && !spec.textOverlays().isEmpty()) {
            ArrayNode subtitleTracks = InternalTimelineJson.mapper().createArrayNode();
            ObjectNode subTrack = InternalTimelineJson.mapper().createObjectNode();
            subTrack.put("id", "sub_imported");
            subTrack.put("language", "und");
            subTrack.put("format", "INTERNAL");
            subTrack.put("styleId", "style_ass_main");
            ArrayNode cues = InternalTimelineJson.mapper().createArrayNode();
            for (TimelineTextOverlay overlay : spec.textOverlays()) {
                ObjectNode cue = InternalTimelineJson.mapper().createObjectNode();
                cue.put("id", overlay.id());
                cue.put("text", overlay.text());
                cue.set("timelineRange", frameRange(overlay.startTime(), overlay.duration(), fps));
                cues.add(cue);
            }
            subTrack.set("cues", cues);
            subtitleTracks.add(subTrack);
            composition.set("subtitleTracks", subtitleTracks);
        }
        return composition;
    }

    private ObjectNode buildRenderGraph(TimelineSpec spec, TimelineExtensions extensions, int fps,
                                          ObjectNode composition) {
        ObjectNode graph = InternalTimelineJson.mapper().createObjectNode();
        graph.put("finalComposer", extensions.finalComposer() != null
                ? extensions.finalComposer().name().toLowerCase()
                : FinalComposerHint.AUTO.name().toLowerCase());

        if (!extensions.externalRenderNodes().isEmpty()) {
            ArrayNode nodes = InternalTimelineJson.mapper().createArrayNode();
            for (ExternalRenderNode node : extensions.externalRenderNodes()) {
                nodes.add(buildExternalRenderNode(node, fps));
            }
            graph.set("externalRenderNodes", nodes);
        }

        ArrayNode layers = buildLayers(spec, fps, composition);
        if (!layers.isEmpty()) {
            graph.set("layers", layers);
        }
        ObjectNode segmentPolicy = readPreservedObject(spec, SegmentTimelinePlanner.META_SEGMENT_POLICY);
        if (segmentPolicy == null && spec.metadata() != null
                && "true".equals(spec.metadata().get("platform.segmentPolicyEnabled"))) {
            segmentPolicy = InternalTimelineJson.mapper().createObjectNode();
            segmentPolicy.put("enabled", true);
            ObjectNode segDur = InternalTimelineJson.mapper().createObjectNode();
            segDur.put("frame", 120);
            segmentPolicy.set("segmentDuration", segDur);
            segmentPolicy.put("overlapFrames", 2);
            segmentPolicy.put("cacheScope", "SEGMENT");
        }
        if (segmentPolicy != null && segmentPolicy.path("enabled").asBoolean(false)) {
            graph.set("segmentPolicy", segmentPolicy);
        }
        return graph;
    }

    private ObjectNode buildExternalRenderNode(ExternalRenderNode node, int fps) {
        ObjectNode n = InternalTimelineJson.mapper().createObjectNode();
        n.put("id", node.id());
        n.put("backend", node.backend());
        if (node.templateId() != null) {
            n.put("templateId", node.templateId());
        }
        if (node.graphId() != null) {
            n.put("graphId", node.graphId());
        }
        if (node.attachToClipId() != null) {
            n.put("attachToClipId", node.attachToClipId());
        }
        n.set("timelineRange", frameRange(node.timelineStart(), node.duration(), fps));
        Map<String, Object> params = node.params() != null ? new LinkedHashMap<>(node.params()) : new LinkedHashMap<>();
        if (params.containsKey("dependsOn")) {
            n.set("dependsOn", InternalTimelineJson.mapper().valueToTree(params.remove("dependsOn")));
        }
        if (!params.isEmpty()) {
            n.set("params", InternalTimelineJson.mapper().valueToTree(params));
        }
        if (node.intermediateFormat() != null && !node.intermediateFormat().isBlank()) {
            ObjectNode output = InternalTimelineJson.mapper().createObjectNode();
            output.put("format", node.intermediateFormat());
            if (node.intermediateFormat().contains("4444") || node.intermediateFormat().contains("png")) {
                output.put("alpha", true);
            }
            n.set("output", output);
        }
        ObjectNode render = InternalTimelineJson.mapper().createObjectNode();
        render.put("strategy", "EXTERNAL_SEGMENT");
        render.put("backendHint", node.backend());
        ObjectNode cache = InternalTimelineJson.mapper().createObjectNode();
        cache.put("scope", "LAYER");
        cache.put("reusable", true);
        ArrayNode cacheInputs = InternalTimelineJson.mapper().createArrayNode();
        cacheInputs.add(node.id());
        if (node.templateId() != null) {
            cacheInputs.add(node.templateId());
        }
        cache.set("cacheKeyInputs", cacheInputs);
        render.set("cachePolicy", cache);
        n.set("render", render);
        return n;
    }

    private ArrayNode buildLayers(TimelineSpec spec, int fps, ObjectNode composition) {
        ArrayNode layers = readPreservedArray(spec, InternalTimelineJson.META_RENDER_GRAPH_LAYERS);
        if (layers == null) {
            layers = InternalTimelineJson.mapper().createArrayNode();
        }
        syncSubtitleLayersFromComposition(layers, composition, fps);
        if (spec.textOverlays() != null && !spec.textOverlays().isEmpty()
                && !layerExists(layers, "layer_sub_imported")) {
            ObjectNode subLayer = InternalTimelineJson.mapper().createObjectNode();
            subLayer.put("id", "layer_sub_imported");
            subLayer.put("kind", "SUBTITLE");
            subLayer.put("subtitleTrackId", "sub_imported");
            subLayer.put("zIndex", 200);
            subLayer.set("render", defaultLayerRender("libass", "LAYER"));
            layers.add(subLayer);
        }
        return layers;
    }

    private void syncSubtitleLayersFromComposition(ArrayNode layers, ObjectNode composition, int fps) {
        JsonNode subtitleTracks = composition.path("subtitleTracks");
        if (!subtitleTracks.isArray()) {
            return;
        }
        for (JsonNode track : subtitleTracks) {
            String trackId = track.path("id").asText("");
            if (trackId.isBlank()) {
                continue;
            }
            String layerId = "layer_" + trackId;
            if (layerExists(layers, layerId)) {
                ensureSubtitleLayerLink(layers, layerId, trackId);
                continue;
            }
            ObjectNode layer = InternalTimelineJson.mapper().createObjectNode();
            layer.put("id", layerId);
            layer.put("kind", "SUBTITLE");
            layer.put("subtitleTrackId", trackId);
            if (track.has("language")) {
                layer.put("language", track.get("language").asText());
            }
            layer.put("zIndex", 200);
            if (track.has("cues") && track.get("cues").isArray() && !track.get("cues").isEmpty()) {
                JsonNode firstCue = track.get("cues").get(0);
                JsonNode lastCue = track.get("cues").get(track.get("cues").size() - 1);
                double start = rangeStartSec(firstCue.path("timelineRange"), fps);
                double end = rangeStartSec(lastCue.path("timelineRange"), fps)
                        + rangeDurationSec(lastCue.path("timelineRange"), fps);
                layer.set("timelineRange", frameRange(start, Math.max(0.1, end - start), fps));
            }
            layer.set("render", defaultLayerRender("libass", "LAYER"));
            layers.add(layer);
        }
    }

    private static void ensureSubtitleLayerLink(ArrayNode layers, String layerId, String trackId) {
        for (JsonNode layer : layers) {
            if (!layerId.equals(layer.path("id").asText())) {
                continue;
            }
            if (!layer.has("subtitleTrackId")) {
                ((ObjectNode) layer).put("subtitleTrackId", trackId);
            }
            return;
        }
    }

    private static double rangeStartSec(JsonNode range, int fps) {
        if (range.isMissingNode()) {
            return 0;
        }
        JsonNode start = range.path("start");
        int frame = start.path("frame").asInt(0);
        return frame / (double) Math.max(1, fps);
    }

    private static double rangeDurationSec(JsonNode range, int fps) {
        if (range.isMissingNode()) {
            return 0;
        }
        JsonNode dur = range.path("duration");
        int frame = dur.path("frame").asInt(0);
        return frame / (double) Math.max(1, fps);
    }

    private static boolean layerExists(ArrayNode layers, String id) {
        for (JsonNode layer : layers) {
            if (id.equals(layer.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode buildStyles(TimelineSpec spec, TimelineExtensions extensions) {
        ObjectNode styles = readPreservedObject(spec, InternalTimelineJson.META_STYLES);
        if (styles == null) {
            styles = InternalTimelineJson.mapper().createObjectNode();
        }
        if (spec.textOverlays() != null && !spec.textOverlays().isEmpty() && !styles.has("style_ass_main")) {
            ObjectNode style = InternalTimelineJson.mapper().createObjectNode();
            style.put("id", "style_ass_main");
            style.put("engine", "libass");
            style.put("fontFamily", "DejaVu Sans");
            styles.set("style_ass_main", style);
        }
        return styles;
    }

    private ObjectNode buildTemplates(TimelineSpec spec, TimelineExtensions extensions) {
        ObjectNode templates = readPreservedObject(spec, InternalTimelineJson.META_TEMPLATES);
        if (templates == null) {
            templates = InternalTimelineJson.mapper().createObjectNode();
        }
        if (extensions.externalRenderNodes() != null) {
            for (ExternalRenderNode node : extensions.externalRenderNodes()) {
                String tplId = resolveTemplateId(node);
                if (templates.has(tplId)) {
                    continue;
                }
                templates.set(tplId, buildTemplateEntry(node, tplId, templates));
            }
        }
        return templates;
    }

    private static String resolveTemplateId(ExternalRenderNode node) {
        if (node.templateId() != null && !node.templateId().isBlank()) {
            return node.templateId().startsWith("tpl_") ? node.templateId() : "tpl_" + node.templateId();
        }
        return "tpl_" + node.backend() + "_" + node.id();
    }

    private ObjectNode buildTemplateEntry(ExternalRenderNode node, String tplId, ObjectNode templatesRoot) {
        ObjectNode tpl;
        if (templatesRoot != null && templatesRoot.has(tplId) && templatesRoot.get(tplId).isObject()) {
            tpl = (ObjectNode) templatesRoot.get(tplId).deepCopy();
        } else {
            tpl = InternalTimelineJson.mapper().createObjectNode();
            tpl.put("id", tplId);
            tpl.put("backend", node.backend());
        }
        if (!tpl.has("backend")) {
            tpl.put("backend", node.backend());
        }
        if (node.graphId() != null) {
            tpl.put("graphId", node.graphId());
        }
        Map<String, Object> params = node.params() != null ? node.params() : Map.of();
        switch (node.backend()) {
            case ExternalRenderNode.BACKEND_REMOTION -> {
                copyParam(tpl, params, "compositionId");
                copyParam(tpl, params, "projectDir");
                if (params.containsKey("props")) {
                    tpl.set("paramSchema", InternalTimelineJson.mapper().createObjectNode().put("props", "object"));
                }
            }
            case ExternalRenderNode.BACKEND_BLENDER -> {
                copyParam(tpl, params, "blendUri");
                tpl.put("allowScripts", false);
            }
            case ExternalRenderNode.BACKEND_NATRON -> {
                if (!tpl.has("graphId") && node.graphId() != null) {
                    tpl.put("graphId", node.graphId());
                }
                if (params.containsKey("allowedPlugins")) {
                    tpl.set("allowedPlugins", InternalTimelineJson.mapper().valueToTree(params.get("allowedPlugins")));
                }
            }
            default -> {
                if (!params.isEmpty()) {
                    tpl.set("paramSchema", InternalTimelineJson.mapper().valueToTree(params.keySet()));
                }
            }
        }
        return tpl;
    }

    private static void copyParam(ObjectNode target, Map<String, Object> params, String key) {
        if (params.containsKey(key)) {
            target.set(key, InternalTimelineJson.mapper().valueToTree(params.get(key)));
        }
    }

    private static ObjectNode readPreservedObject(TimelineSpec spec, String metadataKey) {
        JsonNode node = readPreservedJson(spec, metadataKey);
        return node != null && node.isObject() ? (ObjectNode) node.deepCopy() : null;
    }

    private static ArrayNode readPreservedArray(TimelineSpec spec, String metadataKey) {
        JsonNode node = readPreservedJson(spec, metadataKey);
        return node != null && node.isArray() ? (ArrayNode) node.deepCopy() : null;
    }

    private static JsonNode readPreservedJson(TimelineSpec spec, String metadataKey) {
        if (spec.metadata() == null || !spec.metadata().containsKey(metadataKey)) {
            return null;
        }
        try {
            return InternalTimelineJson.mapper().readTree(spec.metadata().get(metadataKey));
        } catch (Exception e) {
            return null;
        }
    }

    private ObjectNode buildMetadata(TimelineSpec spec, TimelineExtensions extensions) {
        ObjectNode metadata = InternalTimelineJson.mapper().createObjectNode();
        metadata.put("platform.source", "InternalTimelineWriter");
        metadata.put("platform.otio.roundTrip.lossy", String.valueOf(extensions.otioExportLossy()));
        if (spec.metadata() != null) {
            spec.metadata().forEach((key, value) -> {
                if (key.startsWith("platform.")) {
                    metadata.put(key, value);
                }
            });
        }
        return metadata;
    }

    private static ObjectNode defaultLayerRender(String backend, String scope) {
        ObjectNode render = InternalTimelineJson.mapper().createObjectNode();
        render.put("strategy", "PRE_RENDER_ALPHA");
        render.put("backendHint", backend);
        ObjectNode cache = InternalTimelineJson.mapper().createObjectNode();
        cache.put("reusable", true);
        cache.put("scope", scope);
        render.set("cachePolicy", cache);
        return render;
    }

    private static ArrayNode buildClipEffects(List<TimelineClipEffect> effects) {
        ArrayNode arr = InternalTimelineJson.mapper().createArrayNode();
        int idx = 0;
        for (TimelineClipEffect effect : effects) {
            ObjectNode fx = InternalTimelineJson.mapper().createObjectNode();
            fx.put("id", effect.id() != null ? effect.id() : "fx_" + (++idx));
            fx.put("effectKey", effect.effectKey());
            if (effect.parameters() != null && !effect.parameters().isEmpty()) {
                fx.set("parameters", InternalTimelineJson.mapper().valueToTree(effect.parameters()));
            }
            arr.add(fx);
        }
        return arr;
    }

    private ArrayNode buildOutputs(TimelineSpec spec) {
        ArrayNode outputs = InternalTimelineJson.mapper().createArrayNode();
        if (spec.outputSpec() == null) {
            return outputs;
        }
        ObjectNode out = InternalTimelineJson.mapper().createObjectNode();
        out.put("id", "out_main");
        out.put("format", spec.outputSpec().format());
        out.put("container", spec.outputSpec().format());
        out.put("width", spec.outputSpec().width());
        out.put("height", spec.outputSpec().height());
        outputs.add(out);
        return outputs;
    }

    private static ObjectNode rationalRate(int num, int den) {
        ObjectNode rate = InternalTimelineJson.mapper().createObjectNode();
        rate.put("num", num);
        rate.put("den", den);
        return rate;
    }

    private static ObjectNode frameRange(double startSec, double durationSec, int fps) {
        int startFrame = (int) Math.round(startSec * fps);
        int durationFrames = Math.max(1, (int) Math.round(durationSec * fps));
        ObjectNode rate = rationalRate(fps, 1);
        ObjectNode range = InternalTimelineJson.mapper().createObjectNode();
        ObjectNode start = InternalTimelineJson.mapper().createObjectNode();
        start.put("frame", startFrame);
        start.set("rate", rate);
        ObjectNode duration = InternalTimelineJson.mapper().createObjectNode();
        duration.put("frame", durationFrames);
        duration.set("rate", rate);
        range.set("start", start);
        range.set("duration", duration);
        return range;
    }
}
