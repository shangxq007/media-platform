package com.example.platform.render.domain.effect;


import com.example.platform.render.domain.visual.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.interchange.TimelineTextOverlay;
import com.example.platform.render.domain.legacy.TimelineClip;
import com.example.platform.render.domain.legacy.TimelineClipEffect;
import com.example.platform.render.domain.legacy.TimelineTrack;

/**
 * Pure, side-effect-free baseline effect planner.
 * Internal domain model.
 *
 * <p>Maps semantic timeline effect references to bounded internal baseline
 * effect operations with typed parameter validation.</p>
 *
 * <p>Does not invoke provider runtimes, generate shell commands, expose raw provider expressions,
 * create RenderJob/Product, call StorageRuntime/ProductRuntime, call OpenCue,
 * or use Artifact DAG.</p>
 */
public final class BaselineEffectPlanner {

    private static final Map<String, BaselineEffectOperationType> EFFECT_MAP = Map.ofEntries(
            Map.entry("SCALE", BaselineEffectOperationType.SCALE),
            Map.entry("CROP", BaselineEffectOperationType.CROP),
            Map.entry("FIT", BaselineEffectOperationType.FIT),
            Map.entry("FILL", BaselineEffectOperationType.FILL),
            Map.entry("CONTAIN", BaselineEffectOperationType.CONTAIN),
            Map.entry("ROTATE", BaselineEffectOperationType.ROTATE),
            Map.entry("OPACITY", BaselineEffectOperationType.OPACITY),
            Map.entry("FADE_IN", BaselineEffectOperationType.FADE_IN),
            Map.entry("FADE_OUT", BaselineEffectOperationType.FADE_OUT),
            Map.entry("TEXT_OVERLAY", BaselineEffectOperationType.TEXT_OVERLAY),
            Map.entry("IMAGE_OVERLAY", BaselineEffectOperationType.IMAGE_OVERLAY),
            Map.entry("CAPTION_OVERLAY", BaselineEffectOperationType.CAPTION_OVERLAY),
            Map.entry("WATERMARK_OVERLAY", BaselineEffectOperationType.WATERMARK_OVERLAY),
            Map.entry("BLUR", BaselineEffectOperationType.BLUR),
            Map.entry("COLOR_ADJUST", BaselineEffectOperationType.COLOR_ADJUST),
            Map.entry("BRIGHTNESS", BaselineEffectOperationType.BRIGHTNESS),
            Map.entry("CONTRAST", BaselineEffectOperationType.CONTRAST),
            Map.entry("SATURATION", BaselineEffectOperationType.SATURATION),
            Map.entry("VOLUME_ADJUST", BaselineEffectOperationType.VOLUME_ADJUST),
            Map.entry("AUDIO_FADE_IN", BaselineEffectOperationType.AUDIO_FADE_IN),
            Map.entry("AUDIO_FADE_OUT", BaselineEffectOperationType.AUDIO_FADE_OUT),
            Map.entry("PICTURE_IN_PICTURE", BaselineEffectOperationType.PICTURE_IN_PICTURE),
            Map.entry("BACKGROUND_BLUR", BaselineEffectOperationType.BACKGROUND_BLUR)
    );

    private static final Set<String> FORBIDDEN_EFFECT_KEYS = Set.of(
            "ARBITRARY_PROVIDER_EXPRESSION",
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
            "provider_expression", "provider expression", "rawCommand", "shell command",
            "Runtime.getRuntime", "ProcessBuilder", "npx remotion",
            "remotion render", "npm install", "pnpm", "yarn");

    private BaselineEffectPlanner() {}

    /**
     * Plan baseline effects from a timeline.
     */
    public static BaselineEffectPlanningResult plan(BaselineEffectPlanningRequest request) {
        if (request == null) {
            return BaselineEffectPlanningResult.failed(List.of(
                    BaselineEffectPlanIssue.error(
                            BaselineEffectPlanIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        BaselineEffectPolicy policy = request.policy() != null
                ? request.policy() : BaselineEffectPolicy.conservative();

        TimelineSpec timeline = request.timeline();
        if (timeline == null) {
            return BaselineEffectPlanningResult.validationFailed(null, List.of(
                    BaselineEffectPlanIssue.error(
                            BaselineEffectPlanIssueCode.INVALID_TIMELINE,
                            "Timeline must not be null")));
        }

        List<BaselineEffectOperation> operations = new ArrayList<>();
        List<BaselineEffectPlanIssue> issues = new ArrayList<>();
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
        for (BaselineEffectPlanIssue issue : issues) {
            if (issue.severity() == BaselineEffectPlanIssueSeverity.WARNING) warningCount++;
            if (issue.code() == BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN) forbiddenCount++;
        }
        for (BaselineEffectOperation op : operations) {
            if (isPoc(op.type())) pocCount++;
            else baselineCount++;
        }

        BaselineEffectPlanSummary summary = new BaselineEffectPlanSummary(
                operations.size(), baselineCount, pocCount, forbiddenCount, warningCount, Map.of());

        BaselineEffectPlanStatus planStatus = determinePlanStatus(issues, policy);
        BaselineEffectPlan plan = new BaselineEffectPlan(
                new BaselineEffectPlanId("plan-" + request.id().value()),
                planStatus, operations, summary, issues, Map.of());

        if (planStatus == BaselineEffectPlanStatus.BLOCKED
                || planStatus == BaselineEffectPlanStatus.FAILED) {
            return BaselineEffectPlanningResult.blocked(issues);
        }
        if (planStatus == BaselineEffectPlanStatus.UNSUPPORTED) {
            return BaselineEffectPlanningResult.unsupported(issues);
        }
        if (planStatus == BaselineEffectPlanStatus.INVALID) {
            return BaselineEffectPlanningResult.validationFailed(plan, issues);
        }

        return BaselineEffectPlanningResult.planned(plan);
    }

    private static void planEffect(
            TimelineClipEffect effect, TimelineClip clip, TimelineTrack track,
            TimelineSpec timeline, BaselineEffectPolicy policy,
            List<BaselineEffectOperation> operations,
            List<BaselineEffectPlanIssue> issues,
            AtomicInteger opSeq) {

        String effectKey = effect.effectKey();
        if (effectKey == null || effectKey.isBlank()) {
            issues.add(BaselineEffectPlanIssue.error(
                    BaselineEffectPlanIssueCode.EFFECT_NOT_FOUND,
                    "Effect key is blank for clip " + clip.id()));
            return;
        }

        // Check forbidden
        if (isForbidden(effectKey)) {
            issues.add(BaselineEffectPlanIssue.blocking(
                    BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN,
                    "Forbidden effect: " + effectKey));
            return;
        }

        // Check for raw provider expression keywords in parameters
        if (effect.parameters() != null) {
            for (Map.Entry<String, Object> entry : effect.parameters().entrySet()) {
                String val = entry.getValue() != null ? entry.getValue().toString().toLowerCase() : "";
                String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
                for (String kw : FORBIDDEN_KEYWORDS) {
                    if (val.contains(kw) || key.contains(kw)) {
                        issues.add(BaselineEffectPlanIssue.blocking(
                                BaselineEffectPlanIssueCode.RAW_PROVIDER_EXPRESSION_FORBIDDEN,
                                "Forbidden keyword in parameter: " + kw));
                        return;
                    }
                }
            }
        }

        // Resolve capability
        VisualCapabilityDefinition capability = resolveCapability(effectKey);
        if (capability == null) {
            issues.add(BaselineEffectPlanIssue.error(
                    BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_NOT_FOUND,
                    "Unknown capability: " + effectKey));
            return;
        }

        // Check capability status
        if (VisualCapabilityPolicy.isForbidden(capability)) {
            issues.add(BaselineEffectPlanIssue.blocking(
                    BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_FORBIDDEN,
                    "Capability is forbidden: " + effectKey));
            return;
        }

        if (VisualCapabilityPolicy.isInternalOnly(capability)) {
            if (!policy.allowPocEffects()) {
                issues.add(BaselineEffectPlanIssue.warning(
                        BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_POC_ONLY,
                        "POC capability not allowed by policy: " + effectKey));
                return;
            }
        }

        if (VisualCapabilityPolicy.requiresManualReview(capability)) {
            if (!policy.allowRestrictedEffects()) {
                issues.add(BaselineEffectPlanIssue.blocking(
                        BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_RESTRICTED,
                        "Restricted capability: " + effectKey));
                return;
            }
        }

        // Map to operation type
        BaselineEffectOperationType opType = EFFECT_MAP.get(effectKey.toUpperCase());
        if (opType == null) {
            issues.add(BaselineEffectPlanIssue.warning(
                    BaselineEffectPlanIssueCode.EFFECT_CAPABILITY_UNSUPPORTED,
                    "No mapping for capability: " + effectKey));
            return;
        }

        // Validate parameters
        List<BaselineEffectOperationParameter> params = validateParameters(
                opType, effect.parameters(), issues);

        // Build target
        BaselineEffectOperationTarget target = new BaselineEffectOperationTarget(
                BaselineEffectOperationTargetType.CLIP, clip.id(), Map.of());

        // Build operation
        BaselineEffectOperationId opId = new BaselineEffectOperationId(
                "op-" + opSeq.incrementAndGet() + "-" + effectKey.toLowerCase());
        BaselineEffectOperationSource source = VisualCapabilityPolicy.isInternalOnly(capability)
                ? BaselineEffectOperationSource.VISUAL_CAPABILITY_RESOLVED
                : BaselineEffectOperationSource.BASIC_TIMELINE_EFFECT_REF;

        operations.add(new BaselineEffectOperation(
                opId, opType, target, params, source, Map.of()));
    }

    private static void planCaptionOverlay(
            TimelineTextOverlay overlay, TimelineSpec timeline,
            BaselineEffectPolicy policy,
            List<BaselineEffectOperation> operations,
            List<BaselineEffectPlanIssue> issues,
            AtomicInteger opSeq) {

        if (overlay.text() == null || overlay.text().isBlank()) {
            issues.add(BaselineEffectPlanIssue.error(
                    BaselineEffectPlanIssueCode.MISSING_REQUIRED_PARAMETER,
                    "Caption overlay text is blank: " + overlay.id()));
            return;
        }

        BaselineEffectOperationTarget target = new BaselineEffectOperationTarget(
                BaselineEffectOperationTargetType.OVERLAY,
                overlay.id() != null ? overlay.id() : "text-overlay-" + opSeq.get(),
                Map.of());

        List<BaselineEffectOperationParameter> params = List.of(
                new BaselineEffectOperationParameter(
                        "text", BaselineEffectParameterType.STRING, overlay.text(), Map.of()),
                new BaselineEffectOperationParameter(
                        "x", BaselineEffectParameterType.STRING, overlay.positionX(), Map.of()),
                new BaselineEffectOperationParameter(
                        "y", BaselineEffectParameterType.STRING, overlay.positionY(), Map.of()),
                new BaselineEffectOperationParameter(
                        "startTime", BaselineEffectParameterType.DURATION_MS, overlay.startTime(), Map.of()),
                new BaselineEffectOperationParameter(
                        "duration", BaselineEffectParameterType.DURATION_MS, overlay.duration(), Map.of()));

        BaselineEffectOperationId opId = new BaselineEffectOperationId(
                "op-" + opSeq.incrementAndGet() + "-text_overlay");

        operations.add(new BaselineEffectOperation(
                opId, BaselineEffectOperationType.TEXT_OVERLAY,
                target, params, BaselineEffectOperationSource.BASIC_TIMELINE_EFFECT_REF, Map.of()));
    }

    private static List<BaselineEffectOperationParameter> validateParameters(
            BaselineEffectOperationType opType,
            Map<String, Object> rawParams,
            List<BaselineEffectPlanIssue> issues) {

        if (rawParams == null || rawParams.isEmpty()) {
            return List.of();
        }

        List<BaselineEffectOperationParameter> result = new ArrayList<>();
        for (Map.Entry<String, Object> entry : rawParams.entrySet()) {
            String name = entry.getKey();
            Object value = entry.getValue();
            BaselineEffectParameterType paramType = inferParameterType(name, value);

            // Validate specific parameters
            if (name.equals("width") || name.equals("height") || name.equals("targetWidth") || name.equals("targetHeight")) {
                if (value instanceof Number n && n.intValue() <= 0) {
                    issues.add(BaselineEffectPlanIssue.error(
                            BaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER,
                            name + " must be > 0"));
                }
            }
            if (name.equals("opacity")) {
                if (value instanceof Number n && (n.doubleValue() < 0 || n.doubleValue() > 1)) {
                    issues.add(BaselineEffectPlanIssue.error(
                            BaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER,
                            "opacity must be 0..1"));
                }
            }
            if (name.equals("durationMs")) {
                if (value instanceof Number n && n.doubleValue() <= 0) {
                    issues.add(BaselineEffectPlanIssue.error(
                            BaselineEffectPlanIssueCode.INVALID_EFFECT_PARAMETER,
                            "durationMs must be > 0"));
                }
            }

            result.add(new BaselineEffectOperationParameter(name, paramType, value, Map.of()));
        }
        return result;
    }

    private static BaselineEffectParameterType inferParameterType(String name, Object value) {
        if (name.contains("duration") || name.contains("Ms")) return BaselineEffectParameterType.DURATION_MS;
        if (name.contains("opacity") || name.contains("percent")) return BaselineEffectParameterType.PERCENT;
        if (name.contains("width") || name.contains("height") || name.contains("x") || name.contains("y")) {
            return BaselineEffectParameterType.PIXEL;
        }
        if (name.contains("degrees")) return BaselineEffectParameterType.DECIMAL;
        if (name.contains("Ref") || name.contains("Id")) return BaselineEffectParameterType.SAFE_REF;
        if (value instanceof Integer) return BaselineEffectParameterType.INTEGER;
        if (value instanceof Double || value instanceof Float) return BaselineEffectParameterType.DECIMAL;
        if (value instanceof Boolean) return BaselineEffectParameterType.BOOLEAN;
        return BaselineEffectParameterType.STRING;
    }

    private static boolean isForbidden(String effectKey) {
        String upper = effectKey.toUpperCase();
        return FORBIDDEN_EFFECT_KEYS.contains(upper)
                || upper.contains("PROVIDER_EXPRESSION")
                || upper.contains("PROVIDER_EXPRESSION")
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

    private static boolean isPoc(BaselineEffectOperationType type) {
        return switch (type) {
            case BLUR, COLOR_ADJUST, BRIGHTNESS, CONTRAST, SATURATION,
                 VOLUME_ADJUST, AUDIO_FADE_IN, AUDIO_FADE_OUT,
                 PICTURE_IN_PICTURE, BACKGROUND_BLUR -> true;
            default -> false;
        };
    }

    private static BaselineEffectPlanStatus determinePlanStatus(
            List<BaselineEffectPlanIssue> issues, BaselineEffectPolicy policy) {
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == BaselineEffectPlanIssueSeverity.BLOCKING);
        boolean hasError = issues.stream().anyMatch(i ->
                i.severity() == BaselineEffectPlanIssueSeverity.ERROR);
        boolean hasWarning = issues.stream().anyMatch(i ->
                i.severity() == BaselineEffectPlanIssueSeverity.WARNING);

        if (hasBlocking) return BaselineEffectPlanStatus.BLOCKED;
        if (hasError) {
            return policy.failOnUnsupported()
                    ? BaselineEffectPlanStatus.INVALID
                    : BaselineEffectPlanStatus.VALID_WITH_WARNINGS;
        }
        if (hasWarning && !policy.allowWarnings()) {
            return BaselineEffectPlanStatus.INVALID;
        }
        if (hasWarning) return BaselineEffectPlanStatus.VALID_WITH_WARNINGS;
        return BaselineEffectPlanStatus.READY;
    }
}
