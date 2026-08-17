package com.example.platform.timeline.app;

import com.example.platform.shared.time.FrameRate;
import com.example.platform.timeline.canonicalmodel.TimelineCandidate;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalNormalizer;
import com.example.platform.timeline.canonicalmodel.TimelineCanonicalValidator;
import com.example.platform.timeline.canonicalmodel.TimelineValidationResult;
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
 * GCR-1 CORRECTION V2: Timeline-owned canonical constructor for Internal Timeline
 * Schema 1.0 documents.
 *
 * <p>This service is the SOLE production canonical construction / authoring write
 * authority: it maps a typed {@link TimelineImportRequest} (adapted from
 * external/editor/OTIO/legacy representations by the render boundary adapter)
 * into canonical internal-1.0 JSON, canonicalizes deterministically
 * ({@link InternalTimelineJson#deepCanonicalize}) and runs the E1b canonical gate
 * (internal-1.0 -&gt; {@link TimelineCandidate} -&gt; {@link TimelineCanonicalValidator}
 * -&gt; {@link TimelineCanonicalNormalizer}) before returning. No production code
 * outside timeline-module constructs canonical internal Timeline documents.</p>
 *
 * <p>Pure construction + validation: no repository, no network, no current-time
 * access, no randomness. Deterministic output for identical input.</p>
 */
@Service
public class TimelineImportService {

    /** Build the canonical Internal Timeline Schema 1.0 JSON for the given import request. */
    public String importTimeline(TimelineImportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("TimelineImportRequest is required");
        }
        ObjectNode root = buildDocument(request);
        String canonical;
        try {
            canonical = InternalTimelineJson.write(InternalTimelineJson.deepCanonicalize(root));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to write Internal Timeline 1.0 JSON", e);
        }
        // E1b canonical gate — the constructed document must be a valid canonical
        // internal-1.0 document before it may leave the import authority.
        TimelineCandidate candidate = InternalTimelineCandidateAdapter.map("import", canonical);
        TimelineValidationResult validation = TimelineCanonicalValidator.validate(candidate);
        if (validation.hasFatalErrors()) {
            throw new TimelineCanonicalRejectionException(validation.diagnostics());
        }
        TimelineCanonicalNormalizer.normalize(candidate)
                .orElseThrow(() -> new TimelineCanonicalRejectionException(validation.diagnostics()));
        return canonical;
    }

    private ObjectNode buildDocument(TimelineImportRequest request) {
        FrameRate rate = request.output() != null && request.output().frameRate() != null
                ? request.output().frameRate() : FrameRate.of(30, 1);
        // Integer fps convenience for the legacy spec model (used only for
        // coarse helpers that carry it; all canonical frame emission uses
        // the exact rational rate). Fractional rates never silently
        // truncate: intFps() throws for den != 1, so fall back to 30 only
        // for the non-authoritative int carrier used by display helpers.
        int fps = 30;
        try {
            fps = rate.intFps();
        } catch (ArithmeticException fractionalRate) {
            fps = 30;
        }
        ObjectNode root = InternalTimelineJson.mapper().createObjectNode();
        root.put("schemaVersion", InternalTimelineJson.SCHEMA_V1);
        root.put("id", request.id());
        root.put("name", request.name() != null ? request.name() : request.id());
        root.put("revision", request.revision());

        root.set("project", buildProject(request, fps, rate));
        root.set("assetRegistry", buildAssetRegistry(request, fps, rate));
        ObjectNode composition = buildComposition(request, fps, rate);
        root.set("composition", composition);
        root.set("styles", buildStyles(request));
        root.set("templates", buildTemplates(request));
        root.set("renderGraph", buildRenderGraph(request, fps, rate, composition));
        root.set("outputs", buildOutputs(request));
        if (request.packagingHints() != null && !request.packagingHints().isEmpty()) {
            ObjectNode packaging = InternalTimelineJson.mapper().createObjectNode();
            packaging.put("id", "packaging");
            request.packagingHints().forEach(packaging::put);
            root.set("packaging", packaging);
        }

        root.set("metadata", buildMetadata(request));
        if (request.metadata() != null && request.metadata().containsKey("tenantId")) {
            root.put("tenantId", request.metadata().get("tenantId"));
        }
        return root;
    }

    private ObjectNode buildProject(TimelineImportRequest request, int fps, FrameRate rate) {
        ObjectNode project = InternalTimelineJson.mapper().createObjectNode();
        project.put("id", request.id() + "_project");
        int w = request.output() != null && request.output().width() > 0 ? request.output().width() : 1920;
        int h = request.output() != null && request.output().height() > 0 ? request.output().height() : 1080;
        project.put("width", w);
        project.put("height", h);
        project.set("frameRate", rationalRate(rate.numerator().intValueExact(), rate.denominator()));
        double durationSec = request.durationSec() > 0 ? request.durationSec() : 30;
        project.set("duration", frameRange(0, durationSec, rate));
        return project;
    }

    private ObjectNode buildAssetRegistry(TimelineImportRequest request, int fps, FrameRate rate) {
        ObjectNode registry = InternalTimelineJson.mapper().createObjectNode();
        ObjectNode assets = InternalTimelineJson.mapper().createObjectNode();
        Set<String> seen = new LinkedHashSet<>();
        if (request.tracks() != null) {
            for (TimelineImportRequest.ImportTrack track : request.tracks()) {
                if (track.clips() == null) {
                    continue;
                }
                for (TimelineImportRequest.ImportClip clip : track.clips()) {
                    if (clip.assetId() == null) {
                        continue;
                    }
                    String assetId = clip.assetId();
                    if (!seen.add(assetId)) {
                        continue;
                    }
                    ObjectNode ast = InternalTimelineJson.mapper().createObjectNode();
                    ast.put("id", assetId);
                    ast.put("kind", "AUDIO".equals(track.type()) ? "AUDIO" : "VIDEO");
                    ast.put("uri", clip.storageUri() != null
                            ? clip.storageUri() : "asset://" + assetId);
                    ObjectNode probe = InternalTimelineJson.mapper().createObjectNode();
                    probe.put("width", clip.width() > 0 ? clip.width() : 1920);
                    probe.put("height", clip.height() > 0 ? clip.height() : 1080);
                    probe.set("duration", frameRange(0, clip.clipDurationSec(), rate));
                    ast.set("probe", probe);
                    assets.set(assetId, ast);
                }
            }
        }
        registry.set("assets", assets);
        return registry;
    }

    private ObjectNode buildComposition(TimelineImportRequest request, int fps, FrameRate rate) {
        ObjectNode composition = InternalTimelineJson.mapper().createObjectNode();
        ArrayNode tracks = InternalTimelineJson.mapper().createArrayNode();
        if (request.tracks() != null) {
            for (TimelineImportRequest.ImportTrack track : request.tracks()) {
                ObjectNode trk = InternalTimelineJson.mapper().createObjectNode();
                trk.put("id", track.id());
                trk.put("type", track.type());
                trk.put("role", "primary");
                trk.put("zIndex", track.zIndex());
                ArrayNode clips = InternalTimelineJson.mapper().createArrayNode();
                if (track.clips() != null) {
                    for (TimelineImportRequest.ImportClip clip : track.clips()) {
                        ObjectNode clipNode = InternalTimelineJson.mapper().createObjectNode();
                        clipNode.put("id", clip.id());
                        String assetId = clip.assetId() != null ? clip.assetId() : "ast_" + clip.id();
                        clipNode.put("assetId", assetId);
                        clipNode.set("timelineRange",
                                frameRange(clip.timelineStartSec(), clip.clipDurationSec(), rate));
                        clipNode.set("sourceRange",
                                frameRange(clip.assetInSec(), clip.assetOutSec() - clip.assetInSec(), rate));
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

        if (request.textOverlays() != null && !request.textOverlays().isEmpty()) {
            ArrayNode subtitleTracks = InternalTimelineJson.mapper().createArrayNode();
            ObjectNode subTrack = InternalTimelineJson.mapper().createObjectNode();
            subTrack.put("id", "sub_imported");
            subTrack.put("language", "und");
            subTrack.put("format", "INTERNAL");
            subTrack.put("styleId", "style_ass_main");
            ArrayNode cues = InternalTimelineJson.mapper().createArrayNode();
            for (TimelineImportRequest.ImportTextOverlay overlay : request.textOverlays()) {
                ObjectNode cue = InternalTimelineJson.mapper().createObjectNode();
                cue.put("id", overlay.id());
                cue.put("text", overlay.text());
                if (overlay.fontFamily() != null) {
                    cue.put("fontFamily", overlay.fontFamily().value());
                }
                cue.set("timelineRange", frameRange(overlay.startTimeSec(), overlay.durationSec(), rate));
                cues.add(cue);
            }
            subTrack.set("cues", cues);
            subtitleTracks.add(subTrack);
            composition.set("subtitleTracks", subtitleTracks);
        }
        // EFFECT_TRANSITION_CANONICALIZATION_V1 (C9/C7): first-class transitions
        // and exact-MediaTime automations join the canonical composition state —
        // they participate in content hash, semantic diff, patch and merge.
        if (request.transitions() != null && !request.transitions().isEmpty()) {
            ArrayNode transitions = InternalTimelineJson.mapper().createArrayNode();
            for (TimelineImportRequest.ImportTransition tr : request.transitions()) {
                ObjectNode trNode = InternalTimelineJson.mapper().createObjectNode();
                trNode.put("id", tr.id());
                trNode.put("transitionDefinitionId", tr.definitionId());
                trNode.put("transitionDefinitionVersion", tr.definitionVersion());
                trNode.put("outgoingClipId", tr.outgoingClipId());
                trNode.put("incomingClipId", tr.incomingClipId());
                trNode.put("mediaType", tr.mediaType());
                trNode.put("durationTicks", tr.durationTicks());
                trNode.put("durationTimeScale", tr.durationTimeScale());
                trNode.put("alignment", tr.alignment());
                trNode.put("temporalPolicy", tr.temporalPolicy());
                if (tr.parameters() != null && !tr.parameters().isEmpty()) {
                    trNode.set("parameters", InternalTimelineJson.mapper().valueToTree(tr.parameters()));
                }
                transitions.add(trNode);
            }
            composition.set("transitions", transitions);
        }
        if (request.automations() != null && !request.automations().isEmpty()) {
            ArrayNode automations = InternalTimelineJson.mapper().createArrayNode();
            for (TimelineImportRequest.ImportAutomationCurve curve : request.automations()) {
                ObjectNode curveNode = InternalTimelineJson.mapper().createObjectNode();
                curveNode.put("automationId", curve.automationId());
                curveNode.put("targetEntityId", curve.targetEntityId());
                curveNode.put("parameterPath", curve.parameterPath());
                curveNode.put("valueType", curve.valueType());
                curveNode.put("extrapolation", curve.extrapolation());
                ArrayNode keyframes = InternalTimelineJson.mapper().createArrayNode();
                for (TimelineImportRequest.ImportAutomationKeyframe kf : curve.keyframes()) {
                    ObjectNode kfNode = InternalTimelineJson.mapper().createObjectNode();
                    kfNode.put("keyframeId", kf.keyframeId());
                    kfNode.put("timeTicks", kf.timeTicks());
                    kfNode.put("timeTimeScale", kf.timeTimeScale());
                    kfNode.put("value", kf.value());
                    kfNode.put("interpolation", kf.interpolation());
                    keyframes.add(kfNode);
                }
                curveNode.set("keyframes", keyframes);
                automations.add(curveNode);
            }
            composition.set("automations", automations);
        }
        return composition;
    }

    private ObjectNode buildRenderGraph(TimelineImportRequest request, int fps, FrameRate rate,
                                        ObjectNode composition) {
        ObjectNode graph = InternalTimelineJson.mapper().createObjectNode();
        graph.put("finalComposer", request.finalComposer() != null && !request.finalComposer().isBlank()
                ? request.finalComposer().toLowerCase()
                : "auto");

        if (request.externalRenderNodes() != null && !request.externalRenderNodes().isEmpty()) {
            ArrayNode nodes = InternalTimelineJson.mapper().createArrayNode();
            for (TimelineImportRequest.ImportExternalRenderNode node : request.externalRenderNodes()) {
                nodes.add(buildExternalRenderNode(node, fps, rate));
            }
            graph.set("externalRenderNodes", nodes);
        }

        ArrayNode layers = request.renderGraphLayers() != null && request.renderGraphLayers().isArray()
                ? (ArrayNode) request.renderGraphLayers().deepCopy()
                : InternalTimelineJson.mapper().createArrayNode();
        syncSubtitleLayersFromComposition(layers, composition, fps, rate);
        if (request.textOverlays() != null && !request.textOverlays().isEmpty()
                && !layerExists(layers, "layer_sub_imported")) {
            ObjectNode subLayer = InternalTimelineJson.mapper().createObjectNode();
            subLayer.put("id", "layer_sub_imported");
            subLayer.put("kind", "SUBTITLE");
            subLayer.put("subtitleTrackId", "sub_imported");
            subLayer.put("zIndex", 200);
            subLayer.set("render", defaultLayerRender("libass", "LAYER"));
            layers.add(subLayer);
        }
        if (!layers.isEmpty()) {
            graph.set("layers", layers);
        }
        ObjectNode segmentPolicy = request.segmentPolicy() != null && request.segmentPolicy().isObject()
                ? (ObjectNode) request.segmentPolicy().deepCopy()
                : null;
        if (segmentPolicy == null && request.segmentPolicyEnabled()) {
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

    private ObjectNode buildExternalRenderNode(TimelineImportRequest.ImportExternalRenderNode node,
                                               int fps, FrameRate rate) {
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
        n.set("timelineRange", frameRange(node.timelineStartSec(), node.durationSec(), rate));
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

    private void syncSubtitleLayersFromComposition(ArrayNode layers, ObjectNode composition, int fps, FrameRate rate) {
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
                double start = rangeStartSec(firstCue.path("timelineRange"), rate);
                double end = rangeStartSec(lastCue.path("timelineRange"), rate)
                        + rangeDurationSec(lastCue.path("timelineRange"), rate);
                layer.set("timelineRange", frameRange(start, Math.max(0.1, end - start), rate));
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

    private static double rangeStartSec(JsonNode range, FrameRate rate) {
        if (range.isMissingNode()) {
            return 0;
        }
        JsonNode start = range.path("start");
        int frame = start.path("frame").asInt(0);
        return frame * rate.denominator() / rate.numerator().doubleValue();
    }

    private static double rangeDurationSec(JsonNode range, FrameRate rate) {
        if (range.isMissingNode()) {
            return 0;
        }
        JsonNode dur = range.path("duration");
        int frame = dur.path("frame").asInt(0);
        return frame * rate.denominator() / rate.numerator().doubleValue();
    }

    private static boolean layerExists(ArrayNode layers, String id) {
        for (JsonNode layer : layers) {
            if (id.equals(layer.path("id").asText())) {
                return true;
            }
        }
        return false;
    }

    private ObjectNode buildStyles(TimelineImportRequest request) {
        ObjectNode styles = request.styles() != null && request.styles().isObject()
                ? (ObjectNode) request.styles().deepCopy()
                : InternalTimelineJson.mapper().createObjectNode();
        if (request.textOverlays() != null && !request.textOverlays().isEmpty() && !styles.has("style_ass_main")) {
            ObjectNode style = InternalTimelineJson.mapper().createObjectNode();
            style.put("id", "style_ass_main");
            style.put("engine", "libass");
            styles.set("style_ass_main", style);
        }
        return styles;
    }

    private ObjectNode buildTemplates(TimelineImportRequest request) {
        ObjectNode templates = request.templates() != null && request.templates().isObject()
                ? (ObjectNode) request.templates().deepCopy()
                : InternalTimelineJson.mapper().createObjectNode();
        if (request.externalRenderNodes() != null) {
            for (TimelineImportRequest.ImportExternalRenderNode node : request.externalRenderNodes()) {
                String tplId = resolveTemplateId(node);
                if (templates.has(tplId)) {
                    continue;
                }
                templates.set(tplId, buildTemplateEntry(node, tplId, templates));
            }
        }
        return templates;
    }

    private static String resolveTemplateId(TimelineImportRequest.ImportExternalRenderNode node) {
        if (node.templateId() != null && !node.templateId().isBlank()) {
            return node.templateId().startsWith("tpl_") ? node.templateId() : "tpl_" + node.templateId();
        }
        return "tpl_" + node.backend() + "_" + node.id();
    }

    private ObjectNode buildTemplateEntry(TimelineImportRequest.ImportExternalRenderNode node, String tplId,
                                          ObjectNode templatesRoot) {
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
            case "remotion" -> {
                copyParam(tpl, params, "compositionId");
                copyParam(tpl, params, "projectDir");
                if (params.containsKey("props")) {
                    tpl.set("paramSchema", InternalTimelineJson.mapper().createObjectNode().put("props", "object"));
                }
            }
            case "blender" -> {
                copyParam(tpl, params, "blendUri");
                tpl.put("allowScripts", false);
            }
            case "natron" -> {
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

    private ObjectNode buildMetadata(TimelineImportRequest request) {
        ObjectNode metadata = InternalTimelineJson.mapper().createObjectNode();
        metadata.put("platform.source", "InternalTimelineWriter");
        metadata.put("platform.otio.roundTrip.lossy", String.valueOf(request.otioExportLossy()));
        if (request.metadata() != null) {
            request.metadata().forEach((key, value) -> {
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

    private static ArrayNode buildClipEffects(List<TimelineImportRequest.ImportClipEffect> effects) {
        ArrayNode arr = InternalTimelineJson.mapper().createArrayNode();
        int idx = 0;
        for (TimelineImportRequest.ImportClipEffect effect : effects) {
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

    private ArrayNode buildOutputs(TimelineImportRequest request) {
        ArrayNode outputs = InternalTimelineJson.mapper().createArrayNode();
        if (request.output() == null) {
            return outputs;
        }
        ObjectNode out = InternalTimelineJson.mapper().createObjectNode();
        out.put("id", "out_main");
        out.put("format", request.output().format());
        out.put("container", request.output().format());
        out.put("width", request.output().width());
        out.put("height", request.output().height());
        outputs.add(out);
        return outputs;
    }

    private static ObjectNode rationalRate(long num, long den) {
        ObjectNode rate = InternalTimelineJson.mapper().createObjectNode();
        rate.put("num", num);
        rate.put("den", den);
        return rate;
    }

    /**
     * Legacy input boundary: double seconds (import contract) -&gt; frame at the
     * EXACT rational rate with explicit round-half-up; the emitted wire rate is
     * the EXACT rational project rate (num/den preserved — never den=1
     * reconstruction).
     */
    private static ObjectNode frameRange(double startSec, double durationSec, FrameRate rate) {
        int startFrame = (int) Math.round(startSec * rate.numerator().doubleValue() / rate.denominator());
        int durationFrames = Math.max(1, (int) Math.round(durationSec * rate.numerator().doubleValue() / rate.denominator()));
        ObjectNode rateNode = rationalRate(rate.numerator().intValueExact(), rate.denominator());
        ObjectNode range = InternalTimelineJson.mapper().createObjectNode();
        ObjectNode start = InternalTimelineJson.mapper().createObjectNode();
        start.put("frame", startFrame);
        start.set("rate", rateNode);
        ObjectNode duration = InternalTimelineJson.mapper().createObjectNode();
        duration.put("frame", durationFrames);
        duration.set("rate", rateNode);
        range.set("start", start);
        range.set("duration", duration);
        return range;
    }
}
