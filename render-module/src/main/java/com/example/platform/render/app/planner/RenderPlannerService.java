package com.example.platform.render.app.planner;

import com.example.platform.render.app.MultiProviderPipelineService;
import com.example.platform.render.app.timeline.InternalTimelineJson;
import com.example.platform.render.app.timeline.SegmentTimelinePlanner;
import com.example.platform.render.domain.timeline.TimelineSegment;
import com.example.platform.render.domain.timeline.ExternalRenderNode;
import com.example.platform.render.domain.timeline.FinalComposerHint;
import com.example.platform.render.domain.timeline.TimelineExtensions;
import com.example.platform.render.domain.timeline.TimelineExtensionsReader;
import com.example.platform.render.domain.timeline.TimelineSpec;
import com.example.platform.render.domain.timeline.TimelineStickerReader;
import com.example.platform.render.domain.timeline.TimelineTrack;
import com.example.platform.shared.Ids;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Generates {@link PipelineExecutionPlan} (task DAG) from Internal Timeline JSON.
 */
@Service
public class RenderPlannerService {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final TimelineExtensionsReader extensionsReader;
    private final FinalComposerSelector finalComposerSelector;
    private final TimelineStickerReader stickerReader;
    private final SegmentTimelinePlanner segmentTimelinePlanner;

    @Value("${render.subtitle.libass.enabled:true}")
    private boolean libassEnabled = true;

    @Value("${render.providers.skia.enabled:false}")
    private boolean skiaEnabled;

    public RenderPlannerService(TimelineExtensionsReader extensionsReader,
                                FinalComposerSelector finalComposerSelector,
                                TimelineStickerReader stickerReader,
                                SegmentTimelinePlanner segmentTimelinePlanner) {
        this.extensionsReader = extensionsReader;
        this.finalComposerSelector = finalComposerSelector;
        this.stickerReader = stickerReader;
        this.segmentTimelinePlanner = segmentTimelinePlanner;
    }

    public PipelineExecutionPlan generatePlan(TimelineSpec timeline, String profile, String tier,
                                              String outputFormat) {
        return generatePlan(timeline, profile, tier, outputFormat, null);
    }

    public PipelineExecutionPlan generatePlan(TimelineSpec timeline, String profile, String tier,
                                              String outputFormat, String timelineJson) {
        TimelineExtensions ext = extensionsReader.fromSpec(timeline);
        FinalComposerHint finalComposer = finalComposerSelector.resolve(timeline, ext);

        String planId = Ids.newId("pep");
        List<PipelineTask> tasks = new ArrayList<>();
        List<String> segmentTaskIds = new ArrayList<>();
        Map<String, String> meta = new LinkedHashMap<>();

        for (ExternalRenderNode node : ext.externalRenderNodes()) {
            String taskId = "xr_" + node.id();
            String backend = normalizeBackend(node.backend());
            List<String> dependsOn = resolveExternalDependsOn(node);
            Map<String, String> xrParams = buildExternalTaskParameters(timeline, node);
            tasks.add(new PipelineTask(
                    taskId,
                    "external_" + node.id(),
                    PipelineTaskType.EXTERNAL_RENDER,
                    backend,
                    dependsOn,
                    computeCacheKey(node, profile),
                    node.intermediateFormat(),
                    Map.copyOf(xrParams)));
            segmentTaskIds.add(taskId);
        }

        int videoTracks = countVideoTracks(timeline);
        if (videoTracks >= 2
                && finalComposer != FinalComposerHint.FFMPEG
                && !hasTaskType(tasks, PipelineTaskType.MLT_MULTITRACK)) {
            String taskId = "mlt_tracks";
            tasks.add(PipelineTask.of(taskId, "mlt_multitrack", PipelineTaskType.MLT_MULTITRACK, "mlt",
                    List.copyOf(segmentTaskIds),
                    Map.of("tracks", String.valueOf(videoTracks))));
            segmentTaskIds.add(taskId);
        }

        if (hasClipEffects(timeline) || hasSubtitleTrack(timeline)) {
            String taskId = "effects";
            tasks.add(PipelineTask.of(taskId, "effects", PipelineTaskType.EFFECTS,
                    selectEffectProvider(tier), List.copyOf(segmentTaskIds),
                    Map.of("subtitleBurnIn", String.valueOf(hasSubtitleTrack(timeline)))));
            segmentTaskIds.add(taskId);
        }

        if (libassEnabled && hasLibassTargets(timeline)) {
            String taskId = "subtitles";
            tasks.add(PipelineTask.of(taskId, "subtitles", PipelineTaskType.SUBTITLES, "libass",
                    List.copyOf(segmentTaskIds), Map.of("engine", "libass")));
            segmentTaskIds.add(taskId);
        }

        if (skiaEnabled && stickerReader.requiresSkiaOverlay(timeline)) {
            String taskId = "skia_overlay";
            tasks.add(PipelineTask.of(taskId, "skia_overlay", PipelineTaskType.SKIA_OVERLAY, "skia",
                    List.copyOf(segmentTaskIds), Map.of("engine", "java2d+ffmpeg")));
            segmentTaskIds.add(taskId);
        }

        if (timelineJson != null) {
            segmentTimelinePlanner.planFromSpec(timeline, timelineJson).ifPresent(plan -> {
                List<String> segTaskIds = new ArrayList<>();
                for (TimelineSegment segment : plan.segments()) {
                    Map<String, String> params = new LinkedHashMap<>();
                    params.put("startFrame", String.valueOf(segment.startFrame()));
                    params.put("durationFrames", String.valueOf(segment.durationFrames()));
                    params.put("cacheScope", plan.policy().cacheScope());
                    tasks.add(new PipelineTask(
                            segment.id(),
                            "segment_" + segment.id(),
                            PipelineTaskType.SEGMENT_RENDER,
                            "ffmpeg",
                            List.copyOf(segmentTaskIds),
                            segment.cacheKey(),
                            "mp4",
                            Map.copyOf(params)));
                    segTaskIds.add(segment.id());
                }
                segmentTaskIds.clear();
                segmentTaskIds.addAll(segTaskIds);
                meta.put("segmentCount", String.valueOf(segTaskIds.size()));
                meta.put("segmentPolicyEnabled", "true");
            });
        }

        String composeBackend = finalComposerSelector.backendKey(finalComposer);
        String composeTaskId = "final_compose";
        tasks.add(new PipelineTask(
                composeTaskId,
                "final_compose",
                PipelineTaskType.FINAL_COMPOSE,
                composeBackend,
                List.copyOf(segmentTaskIds),
                computeCacheKey(timeline.id(), profile, "compose", composeBackend),
                finalComposer == FinalComposerHint.MLT ? "mezzanine_mp4" : "mezzanine_mp4",
                Map.of("finalComposer", finalComposer.name().toLowerCase())));

        String transcodeTaskId = "transcode";
        if (finalComposer == FinalComposerHint.FFMPEG) {
            // FFmpeg FC often includes encode; still add transcode for provider router compatibility
            tasks.add(PipelineTask.of(transcodeTaskId, "transcode", PipelineTaskType.TRANSCODE,
                    selectTranscodeProvider(profile), List.of(composeTaskId), Map.of()));
        } else {
            tasks.add(PipelineTask.of(transcodeTaskId, "transcode", PipelineTaskType.TRANSCODE,
                    selectTranscodeProvider(profile), List.of(composeTaskId), Map.of()));
        }

        List<String> prePackageDeps = List.of(transcodeTaskId);
        if (isStreamingFormat(outputFormat)) {
            String packager = selectPackagingKey(outputFormat, ext);
            tasks.add(PipelineTask.of("packaging", "packaging", PipelineTaskType.PACKAGING, packager,
                    prePackageDeps, Map.of("format", outputFormat, "packager", packager)));
            prePackageDeps = List.of("packaging");
        }

        tasks.add(PipelineTask.of("qa", "qa", PipelineTaskType.QA, "probe",
                prePackageDeps, Map.of("checks", "probe,duration")));

        meta.put("profile", profile);
        meta.put("tier", tier);
        meta.put("outputFormat", outputFormat != null ? outputFormat : "mp4");
        meta.put("finalComposer", finalComposer.name().toLowerCase());
        meta.put("taskCount", String.valueOf(tasks.size()));

        return new PipelineExecutionPlan(planId, timeline.id(), finalComposer, List.copyOf(tasks), meta);
    }

    /**
     * Converts pipeline plan to legacy {@link MultiProviderPipelineService.PipelineStage} list.
     */
    public List<MultiProviderPipelineService.PipelineStage> toPipelineStages(PipelineExecutionPlan plan) {
        List<MultiProviderPipelineService.PipelineStage> stages = new ArrayList<>();
        for (PipelineTask task : plan.tasks()) {
            switch (task.type()) {
                case SEGMENT_RENDER -> stages.add(stageFromTask(task.taskId(), task));
                case MLT_MULTITRACK -> stages.add(stageFromTask("mlt_multitrack", task));
                case EFFECTS -> stages.add(stageFromTask("effects", task));
                case SUBTITLES -> stages.add(stageFromTask("subtitles", task));
                case SKIA_OVERLAY -> stages.add(stageFromTask("skia_overlay", task));
                case FINAL_COMPOSE -> stages.add(stageFromTask("final_compose", task));
                case TRANSCODE -> stages.add(stageFromTask("transcode", task));
                case PACKAGING -> stages.add(stageFromTask("packaging", task));
                case EXTERNAL_RENDER, ENCODE, QA -> { /* DAG executor / QA — not multi-provider stages */ }
                default -> { }
            }
        }
        if (stages.stream().noneMatch(s -> "transcode".equals(s.name()))) {
            stages.add(new MultiProviderPipelineService.PipelineStage(
                    "transcode", "javacv", Map.of()));
        }
        return stages;
    }

    private static MultiProviderPipelineService.PipelineStage stageFromTask(String stageName, PipelineTask task) {
        Map<String, String> params = new LinkedHashMap<>();
        if (task.parameters() != null) {
            params.putAll(task.parameters());
        }
        params.put("taskId", task.taskId());
        if (task.cacheKey() != null) {
            params.put("cacheKey", task.cacheKey());
        }
        return new MultiProviderPipelineService.PipelineStage(stageName, task.backend(), Map.copyOf(params));
    }

    private String selectEffectProvider(String tier) {
        if ("PRO".equals(tier) || "TEAM".equals(tier) || "ENTERPRISE".equals(tier)
                || "EXPERIMENTAL".equals(tier)) {
            return "ofx";
        }
        return "javacv";
    }

    private String selectTranscodeProvider(String profile) {
        if (profile != null && profile.startsWith("gpu_")) {
            return "gpu-h264";
        }
        if (profile != null && profile.startsWith("remote_")) {
            return "remote-javacv";
        }
        return "javacv";
    }

    private String selectPackagingKey(String outputFormat, TimelineExtensions ext) {
        if ("dash_drm".equalsIgnoreCase(outputFormat)) {
            return "bento4";
        }
        if (ext.packagingHints() != null && ext.packagingHints().containsKey("packager")) {
            String packager = ext.packagingHints().get("packager").toLowerCase();
            return switch (packager) {
                case "shaka", "bento4", "gpac" -> packager;
                default -> "gpac";
            };
        }
        return "gpac";
    }

    private boolean isStreamingFormat(String outputFormat) {
        if (outputFormat == null) {
            return false;
        }
        return switch (outputFormat.toLowerCase()) {
            case "dash", "hls", "cmaf", "dash_drm" -> true;
            default -> false;
        };
    }

    private int countVideoTracks(TimelineSpec timeline) {
        if (timeline.tracks() == null) {
            return 0;
        }
        return (int) timeline.tracks().stream()
                .filter(t -> t.type() == TimelineTrack.TrackType.VIDEO)
                .count();
    }

    private boolean hasClipEffects(TimelineSpec timeline) {
        return timeline.tracks().stream()
                .anyMatch(track -> track.clips().stream()
                        .anyMatch(clip -> clip.effects() != null && !clip.effects().isEmpty()));
    }

    private boolean hasSubtitleTrack(TimelineSpec timeline) {
        return timeline.tracks().stream()
                .anyMatch(track -> track.type() == TimelineTrack.TrackType.SUBTITLE);
    }

    private boolean hasLibassTargets(TimelineSpec timeline) {
        return (timeline.textOverlays() != null && !timeline.textOverlays().isEmpty())
                || hasSubtitleTrack(timeline);
    }

    private boolean hasTaskType(List<PipelineTask> tasks, PipelineTaskType type) {
        return tasks.stream().anyMatch(t -> t.type() == type);
    }

    private String normalizeBackend(String backend) {
        if (backend == null) {
            return "blender";
        }
        return switch (backend.toLowerCase()) {
            case "remotion" -> "remotion";
            case "natron" -> "natron";
            case "vapoursynth", "vs" -> "vapoursynth";
            default -> "blender";
        };
    }

    private String computeCacheKey(ExternalRenderNode node, String profile) {
        return computeCacheKey(node.id(), profile, node.backend(), node.templateId() + node.graphId());
    }

    private String computeCacheKey(String... parts) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            for (String p : parts) {
                if (p != null) {
                    md.update(p.getBytes(StandardCharsets.UTF_8));
                }
            }
            return HexFormat.of().formatHex(md.digest()).substring(0, 16);
        } catch (Exception e) {
            return Ids.newId("ck");
        }
    }

    private List<String> resolveExternalDependsOn(ExternalRenderNode node) {
        if (node.params() == null) {
            return List.of();
        }
        Object raw = node.params().get("dependsOn");
        if (raw == null) {
            return List.of();
        }
        List<String> ids = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                addDependsOnId(ids, String.valueOf(item));
            }
        } else {
            for (String part : String.valueOf(raw).split(",")) {
                addDependsOnId(ids, part);
            }
        }
        return List.copyOf(ids);
    }

    private Map<String, String> buildExternalTaskParameters(TimelineSpec timeline, ExternalRenderNode node) {
        String tplId = resolveTemplateId(node);
        Map<String, String> params = new LinkedHashMap<>();
        params.put("templateId", nullToEmpty(node.templateId()));
        params.put("graphId", firstNonEmpty(nullToEmpty(node.graphId()), templateText(timeline, tplId, "graphId")));
        params.put("attachToClipId", nullToEmpty(node.attachToClipId()));
        params.put("compositionId", firstNonEmpty(paramOrEmpty(node, "compositionId"),
                templateText(timeline, tplId, "compositionId")));
        params.put("projectDir", firstNonEmpty(paramOrEmpty(node, "projectDir"),
                templateText(timeline, tplId, "projectDir")));
        params.put("blendUri", firstNonEmpty(paramOrEmpty(node, "blendUri"),
                templateText(timeline, tplId, "blendUri")));
        params.put("timelineStart", String.valueOf(node.timelineStart()));
        params.put("duration", String.valueOf(node.duration()));
        return params;
    }

    private static String resolveTemplateId(ExternalRenderNode node) {
        if (node.templateId() != null && !node.templateId().isBlank()) {
            return node.templateId().startsWith("tpl_") ? node.templateId() : "tpl_" + node.templateId();
        }
        return "tpl_" + node.backend() + "_" + node.id();
    }

    private static String templateText(TimelineSpec timeline, String tplId, String field) {
        if (timeline.metadata() == null || !timeline.metadata().containsKey(InternalTimelineJson.META_TEMPLATES)) {
            return "";
        }
        try {
            JsonNode templates = MAPPER.readTree(timeline.metadata().get(InternalTimelineJson.META_TEMPLATES));
            JsonNode tpl = templates.path(tplId);
            if (tpl.isMissingNode() || tpl.isNull()) {
                return "";
            }
            return tpl.path(field).asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private static String firstNonEmpty(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        return fallback != null ? fallback : "";
    }

    private static String paramOrEmpty(ExternalRenderNode node, String key) {
        if (node.params() == null || !node.params().containsKey(key)) {
            return "";
        }
        Object v = node.params().get(key);
        return v != null ? String.valueOf(v) : "";
    }

    private static void addDependsOnId(List<String> ids, String rawId) {
        String dep = rawId != null ? rawId.trim() : "";
        if (dep.isBlank()) {
            return;
        }
        ids.add(dep.startsWith("xr_") ? dep : "xr_" + dep);
    }

    private static String nullToEmpty(String s) {
        return s != null ? s : "";
    }
}
