package com.example.platform.render.domain.timeline.render.effect;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.visual.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure, side-effect-free FFmpeg baseline effect planner.
 * Internal domain model.
 *
 * <p>Maps semantic timeline effect references to bounded internal FFmpeg
 * baseline effect operations with typed parameter validation.</p>
 *
 * <p>Does not execute FFmpeg, generate shell commands, expose raw filtergraphs,
 * create RenderJob/Product, call StorageRuntime/ProductRuntime, call OpenCue,
 * or use Artifact DAG.</p>
 */
public final class FFmpegBaselineEffectPlanner {

    private static final Map<String, FFmpegBaselineEffectOperationType> EFFECT_MAP = Map.ofEntries(
            Map.entry("SCALE", FFmpegBaselineEffectOperationType.SCALE),
            Map.entry("CROP", FFmpegBaselineEffectOperationType.CROP),
            Map.entry("FIT", FFmpegBaselineEffectOperationType.FIT),
            Map.entry("FILL", FFmpegBaselineEffectOperationType.FILL),
            Map.entry("CONTAIN", FFmpegBaselineEffectOperationType.CONTAIN),
            Map.entry("ROTATE", FFmpegBaselineEffectOperationType.ROTATE),
            Map.entry("OPACITY", FFmpegBaselineEffectOperationType.OPACITY),
            Map.entry("FADE_IN", FFmpegBaselineEffectOperationType.FADE_IN),
            Map.entry("FADE_OUT", FFmpegBaselineEffectOperationType.FADE_OUT),
            Map.entry("TEXT_OVERLAY", FFmpegBaselineEffectOperationType.TEXT_OVERLAY),
            Map.entry("IMAGE_OVERLAY", FFmpegBaselineEffectOperationType.IMAGE_OVERLAY),
            Map.entry("CAPTION_OVERLAY", FFmpegBaselineEffectOperationType.CAPTION_OVERLAY),
            Map.entry("WATERMARK_OVERLAY", FFmpegBaselineEffectOperationType.WATERMARK_OVERLAY),
            Map.entry("BLUR", FFmpegBaselineEffectOperationType.BLUR),
            Map.entry("COLOR_ADJUST", FFmpegBaselineEffectOperationType.COLOR_ADJUST),
            Map.entry("BRIGHTNESS", FFmpegBaselineEffectOperationType.BRIGHTNESS),
            Map.entry("CONTRAST", FFmpegBaselineEffectOperationType.CONTRAST),
            Map.entry("SATURATION", FFmpegBaselineEffectOperationType.SATURATION),
            Map.entry("VOLUME_ADJUST", FFmpegBaselineEffectOperationType.VOLUME_ADJUST),
            Map.entry("AUDIO_FADE_IN", FFmpegBaselineEffectOperationType.AUDIO_FADE_IN),
            Map.entry("AUDIO_FADE_OUT", FFmpegBaselineEffectOperationType.AUDIO_FADE_OUT),
            Map.entry("PICTURE_IN_PICTURE", FFmpegBaselineEffectOperationType.PICTURE_IN_PICTURE),
            Map.entry("BACKGROUND_BLUR", FFmpegBaselineEffectOperationType.BACKGROUND_BLUR)
    );

    private static final Set<String> FORBIDDEN_EFFECT_KEYS = Set.of(
            "ARBITRARY_FFMPEG_FILTERGRAPH",
            "ARBITRARY_SHADER",
            "ARBITRARY_SCRIPT_EFFECT",
            "ARBITRARY_OFX_PLUGIN",
            "NATRON_NODE_GRAPH",
            "BLENDER_COMPOSITOR_GRAPH",
            "REMOTION_COMPONENT_EXECUTION",
            "USER_DEFINED_RENDER_DAG",
            "PLUGIN_INSERTED_RENDER_NODE",
            "PROVIDER_SPECIFIC_RAW_COMMAND"
    );

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "filter_complex", "filtergraph", "rawCommand", "shell command",
            "Runtime.getRuntime", "ProcessBuilder", "npx remotion",
            "remotion render", "npm install", "pnpm", "yarn");

    private FFmpegBaselineEffectPlanner() {}

    /**
     * Plan FFmpeg baseline effects from a timeline.
     */
    public static FFmpegBaselineEffectPlanningResult plan(FFmpegBaselineEffectPlanningRequest request) {
        if (request == null) {
            return FFmpegBaselineEffectPlanningResult.failed(List.of(
                    FFmpegBaselineEffectPlanIssue.error(
                            FFmpegBaselineEffectPlanIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        FFmpegBaselineEffectPolicy policy = request.policy() != null
                ? request.policy() : FFmpegBaselineEffectPolicy.conservative();

        TimelineSpec timeline = request.timeline();
        if (timeline == null) {
            return FFmpegBaselineEffectPlanningResult.validationFailed(null, List.of(
                    FFmpegBaselineEffectPlanIssue.error(
                            FFmpegBaselineEffectPlanIssueCode.INVALID_TIMELINE,
                            "Timeline must not be null")));
        }

        List<FFmpegBaselineEffectOperation> operations = new ArrayList<>();
        List<FFmpegBaselineEffectPlanIssue> issues = new ArrayList<>();
        AtomicInteger opSeq = new AtomicInteger(0);

        // Scan tracks → clips → effects
        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                if (track.clips() == null) continue;
                for (TimelineClip clip : track.clips()) {
                    if (clip.effects() == null) continue;
                    for (TimelineClipEffect effect : clip.effects()) {
                        planEffect(effect, clip, track, timeline, policy, operations, issues, opSeq);
                    }
                }
            }
        }

        // Scan text overlays (captions)
        if (timeline.textOverlays() != null) {
            for (TimelineTextOverlay overlay : timeline.textOverlays()) {
                planCaptionOverlay(overlay, timeline, policy, operations, issues, opSeq);
            }
        }

        // Build summary
        int baselineCount = 0, pocCount = 0, forbiddenCount = 0, warningCount = 0;
        for (FFmpegBaselineEffectPlanIssue issue : issues) {
            if (issue.severity() == FFmpegBaselineEffectPlanIssueSeverity.WARNING) warningCount++;
            if (issue.code() == FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN) forbiddenCount++;
        }
        for (FFmpegBaselineEffectOperation op : operations) {
            if (isPoc(op.type())) pocCount++;
            else baselineCount++;
        }

        FFmpegBaselineEffectPlanSummary summary = new FFmpegBaselineEffectPlanSummary(
                operations.size(), baselineCount, pocCount, forbiddenCount, warningCount, Map.of());

        FFmpegBaselineEffectPlanStatus planStatus = determinePlanStatus(issues, policy);
        FFmpegBaselineEffectPlan plan = new FFmpegBaselineEffectPlan(
                new FFmpegBaselineEffectPlanId("plan-" + request.id().value()),
                planStatus, operations, summary, issues, Map.of());

        if (planStatus == FFmpegBaselineEffectPlanStatus.BLOCKED
                || planStatus == FFmpegBaselineEffectPlanStatus.FAILED) {
            return FFmpegBaselineEffectPlanningResult.blocked(issues);
        }
        if (planStatus == FFmpegBaselineEffectPlanStatus.UNSUPPORTED) {
            return FFmpegBaselineEffectPlanningResult.unsupported(issues);
        }
        if (planStatus == FFmpegBaselineEffectPlanStatus.INVALID) {
            return FFmpegBaselineEffectPlanningResult.validationFailed(plan, issues);
        }

        return FFmpegBaselineEffectPlanningResult.planned(plan);
    }

    private static void planEffect(
            TimelineClipEffect effect, TimelineClip clip, TimelineTrack track,
            TimelineSpec timeline, FFmpegBaselineEffectPolicy policy,
            List<FFmpegBaselineEffectOperation> operations,
            List<FFmpegBaselineEffectPlanIssue> issues,
            AtomicInteger opSeq) {

        String effectKey = effect.effectKey();
        if (effectKey == null || effectKey.isBlank()) {
            issues.add(FFmpegBaselineEffectPlanIssue.error(
                    FFmpegBaselineEffectPlanIssueCode.EFFECT_NOT_FOUND,
                    "Effect key is blank for clip " + clip.id()));
            return;
        }

        // Check forbidden
        if (isForbidden(effectKey)) {
            issues.add(FFmpegBaselineEffectPlanIssue.blocking(
                    FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN,
                    "Forbidden effect: " + effectKey));
            return;
        }

        // Check for raw filtergraph keywords in parameters
        if (effect.parameters() != null) {
            for (Map.Entry<String, Object> entry : effect.parameters().entrySet()) {
                String val = entry.getValue() != null ? entry.getValue().toString().toLowerCase() : "";
                String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
                for (String kw : FORBIDDEN_KEYWORDS) {
                    if (val.contains(kw) || key.contains(kw)) {
                        issues.add(FFmpegBaselineEffectPlanIssue.blocking(
                                FFmpegBaselineEffectPlanIssueCode.RAW_FILTERGRAPH_FORBIDDEN,
                                "Forbidden keyword in parameter: " + kw));
                        return;
                    }
                }
            }
        }

        // Resolve capability
        VisualCapabilityDefinition capability = resolveCapability(effectKey);
        if (capability == null) {
            issues.add(FFmpegBaselineEffectPlanIssue.error(
                    FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_NOT_FOUND,
                    "Unknown capability: " + effectKey));
            return;
        }

        // Check capability status
        if (VisualCapabilityPolicy.isForbidden(capability)) {
            issues.add(FFmpegBaselineEffectPlanIssue.blocking(
                    FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN,
                    "Capability is forbidden: " + effectKey));
            return;
        }

        if (VisualCapabilityPolicy.isInternalOnly(capability)) {
            if (!policy.allowPocEffects()) {
                issues.add(FFmpegBaselineEffectPlanIssue.warning(
                        FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_POC_ONLY,
                        "POC capability not allowed by policy: " + effectKey));
                return;
            }
        }

        if (VisualCapabilityPolicy.requiresManualReview(capability)) {
            if (!policy.allowRestrictedEffects()) {
                issues.add(FFmpegBaselineEffectPlanIssue.blocking(
                        FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_RESTRICTED,
                        "Restricted capability: " + effectKey));
                return;
            }
        }

        // Map to operation type
        FFmpegBaselineEffectOperationType opType = EFFECT_MAP.get(effectKey.toUpperCase());
        if (opType == null) {
            issues.add(FFmpegBaselineEffectPlanIssue.warning(
                    FFmpegBaselineEffectPlanIssueCode.EFFECT_CAPABILITY_UNSUPPORTED,
                    "No mapping for capability: " + effectKey));
            return;
        }

        // Validate parameters
        List<FFmpegBaselineEffectOperationParameter> params = validateParameters(
                opType, effect.parameters(), issues);

        // Build target
        FFmpegBaselineEffectOperationTarget target = new FFmpegBaselineEffectOperationTarget(
                FFmpegBaselineEffectOperationTargetType.CLIP, clip.id(), Map.of());

        // Build operation
        FFmpegBaselineEffectOperationId opId = new FFmpegBaselineEffectOperationId(
                "op-" + opSeq.incrementAndGet() + "-" + effectKey.toLowerCase());
        FFmpegBaselineEffectOperationSource source = VisualCapabilityPolicy.isInternalOnly(capability)
                ? FFmpegBaselineEffectOperationSource.VISUAL_CAPABILITY_RESOLVED
                : FFmpegBaselineEffectOperationSource.BASIC_TIMELINE_EFFECT_REF;

        operations.add(new FFmpegBaselineEffectOperation(
                opId, opType, target, params, source, Map.of()));
    }

    private static void planCaptionOverlay(
            TimelineTextOverlay overlay, TimelineSpec timeline,
            FFmpegBaselineEffectPolicy policy,
            List<FFmpegBaselineEffectOperation> operations,
            List<FFmpegBaselineEffectPlanIssue> issues,
            AtomicInteger opSeq) {

        if (overlay.text() == null || overlay.text().isBlank()) {
            issues.add(FFmpegBaselineEffectPlanIssue.error(
                    FFmpegBaselineEffectPlanIssueCode.MISSING_REQUIRED_PARAMETER,
                    "Caption overlay text is blank: " + overlay.id()));
            return;
        }

        FFmpegBaselineEffectOperationTarget target = new FFmpegBaselineEffectOperationTarget(
                FFmpegBaselineEffectOperationTargetType.OVERLAY,
                overlay.id() != null ? overlay.id() : "text-overlay-" + opSeq.get(),
                Map.of());

        List<FFmpegBaselineEffectOperationParameter> params = List.of(
                new FFmpegBaselineEffectOperationParameter(
                        "text", FFmpegBaselineEffectParameterType.STRING, overlay.text(), Map.of()),
                new FFmpegBaselineEffectOperationParameter(
                        "x", FFmpegBaselineEffectParameterType.STRING, overlay.positionX(), Map.of()),
                new FFmpegBaselineEffectOperationParameter(
                        "y", FFmpegBaselineEffectParameterType.STRING, overlay.positionY(), Map.of()),
                new FFmpegBaselineEffectOperationParameter(
                        "startTime", FFmpegBaselineEffectParameterType.DURATION_MS, overlay.startTime(), Map.of()),
                new FFmpegBaselineEffectOperationParameter(
                        "duration", FFmpegBaselineEffectParameterType.DURATION_MS, overlay.duration(), Map.of()));

        FFmpegBaselineEffectOperationId opId = new FFmpegBaselineEffectOperationId(
                "op-" + opSeq.incrementAndGet() + "-text_overlay");

        operations.add(new FFmpegBaselineEffectOperation(
                opId, FFmpegBaselineEffectOperationType.TEXT_OVERLAY,
                target, params, FFmpegBaselineEffectOperationSource.BASIC_TIMELINE_EFFECT_REF, Map.of()));
    }

    private static List<FFmpegBaselineEffectOperationParameter> validateParameters(
            FFmpegBaselineEffectOperationType opType,
            Map<String, Object> rawParams,
            List<FFmpegBaselineEffectPlanIssue> issues) {

        if (rawParams == null || rawParams.isEmpty()) {
            return List.of();
        }

        List<FFmpegBaselineEffectOperationParameter> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            FFmpegBaselineEffectParameterType paramType = inferParameterType(name, value);

            // Validate specific parameters
            if (name.equals("width") || name.equals("height") || name.equals("targetWidth") || name.equals("targetHeight")) {
                if (value instanceof Number n && n.intValue() <= 0) {
                    issues.add(FFmpegBaselineEffectPlanIssue.error(
                            FFmpegBaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER,
                            name + " must be > 0"));
                }
            }
            if (name.equals("opacity")) {
                if (value instanceof Number n && (n.doubleValue() < 0 || n.doubleValue() > 1)) {
                    issues.add(FFmpegBaselineEffectPlanIssue.error(
                            FFmpegBaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER,
                            "opacity must be 0..1"));
                }
            }
            if (name.equals("durationMs")) {
                if (value instanceof Number n && n.doubleValue() <= 0) {
                    issues.add(FFmpegBaselineEffectPlanIssue.error(
                            FFmpegBaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER,
                            "durationMs must be > 0"));
                }
            }

            result.add(new FFmpegBaselineEffectOperationParameter(name, paramType, value, Map.of()));
        }
        return result;
    }

    private static FFmpegBaselineEffectParameterType inferParameterType(String name, Object value) {
        if (name.contains("duration") || name.contains("Ms")) return FFmpegBaselineEffectParameterType.DURATION_MS;
        if (name.contains("opacity") || name.contains("percent")) return FFmpegBaselineEffectParameterType.PERCENT;
        if (name.contains("width") || name.contains("height") || name.contains("x") || name.contains("y")) {
            return FFmpegBaselineEffectParameterType.PIXEL;
        }
        if (name.contains("degrees")) return FFmpegBaselineEffectParameterType.DECIMAL;
        if (name.contains("Ref") || name.contains("Id")) return FFmpegBaselineEffectParameterType.SAFE_REF;
        if (value instanceof Integer) return FFmpegBaselineEffectParameterType.INTEGER;
        if (value instanceof Double || value instanceof Float) return FFmpegBaselineEffectParameterType.DECIMAL;
        if (value instanceof Boolean) return FFmpegBaselineEffectParameterType.BOOLEAN;
        return FFmpegBaselineEffectParameterType.STRING;
    }

    private static boolean isForbidden(String effectKey) {
        String upper = effectKey.toUpperCase();
        return FORBIDDEN_EFFECT_KEYS.contains(upper)
                || upper.contains("FILTERGRAPH")
                || upper.contains("FILTER_COMPLEX")
                || upper.contains("RAWCOMMAND");
    }

    private static VisualCapabilityDefinition resolveCapability(String effectKey) {
        String upper = effectKey.toUpperCase();
        return switch (upper) {
            case "SCALE" -> EffectCapabilityProfile.scale();
            case "CROP" -> EffectCapabilityProfile.crop();
            case "FIT" -> EffectCapabilityProfile.fit();
            case "FILL" -> EffectCapabilityProfile.fill();
            case "CONTAIN" -> EffectCapabilityProfile.contain();
            case "ROTATE" -> EffectCapabilityProfile.rotate();
            case "OPACITY" -> EffectCapabilityProfile.opacity();
            case "FADE_IN" -> EffectCapabilityProfile.fadeIn();
            case "FADE_OUT" -> EffectCapabilityProfile.fadeOut();
            case "TEXT_OVERLAY" -> EffectCapabilityProfile.textOverlay();
            case "IMAGE_OVERLAY" -> EffectCapabilityProfile.imageOverlay();
            case "CAPTION_OVERLAY" -> EffectCapabilityProfile.captionOverlay();
            case "WATERMARK_OVERLAY" -> EffectCapabilityProfile.watermarkOverlay();
            case "BLUR" -> EffectCapabilityProfile.blur();
            case "COLOR_ADJUST" -> EffectCapabilityProfile.colorAdjust();
            case "BRIGHTNESS" -> EffectCapabilityProfile.brightness();
            case "CONTRAST" -> EffectCapabilityProfile.contrast();
            case "SATURATION" -> EffectCapabilityProfile.saturation();
            case "VOLUME_ADJUST" -> EffectCapabilityProfile.volumeAdjust();
            case "AUDIO_FADE_IN" -> EffectCapabilityProfile.audioFadeIn();
            case "AUDIO_FADE_OUT" -> EffectCapabilityProfile.audioFadeOut();
            case "PICTURE_IN_PICTURE" -> EffectCapabilityProfile.pictureInPicture();
            case "BACKGROUND_BLUR" -> EffectCapabilityProfile.backgroundBlur();
            default -> null;
        };
    }

    private static boolean isPoc(FFmpegBaselineEffectOperationType type) {
        return switch (type) {
            case BLUR, COLOR_ADJUST, BRIGHTNESS, CONTRAST, SATURATION,
                 VOLUME_ADJUST, AUDIO_FADE_IN, AUDIO_FADE_OUT,
                 PICTURE_IN_PICTURE, BACKGROUND_BLUR -> true;
            default -> false;
        };
    }

    private static FFmpegBaselineEffectPlanStatus determinePlanStatus(
            List<FFmpegBaselineEffectPlanIssue> issues, FFmpegBaselineEffectPolicy policy) {
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == FFmpegBaselineEffectPlanIssueSeverity.BLOCKING);
        boolean hasError = issues.stream().anyMatch(i ->
                i.severity() == FFmpegBaselineEffectPlanIssueSeverity.ERROR);
        boolean hasWarning = issues.stream().anyMatch(i ->
                i.severity() == FFmpegBaselineEffectPlanIssueSeverity.WARNING);

        if (hasBlocking) return FFmpegBaselineEffectPlanStatus.BLOCKED;
        if (hasError) {
            return policy.failOnUnsupported()
                    ? FFmpegBaselineEffectPlanStatus.INVALID
                    : FFmpegBaselineEffectPlanStatus.VALID_WITH_WARNINGS;
        }
        if (hasWarning && !policy.allowWarnings()) {
            return FFmpegBaselineEffectPlanStatus.INVALID;
        }
        if (hasWarning) return FFmpegBaselineEffectPlanStatus.VALID_WITH_WARNINGS;
        return FFmpegBaselineEffectPlanStatus.READY;
    }
}
