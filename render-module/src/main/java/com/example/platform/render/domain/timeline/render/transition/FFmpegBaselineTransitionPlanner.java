package com.example.platform.render.domain.timeline.render.transition;

import com.example.platform.render.domain.timeline.*;
import com.example.platform.render.domain.visual.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pure, side-effect-free FFmpeg baseline transition planner.
 * Internal domain model.
 *
 * <p>Maps semantic timeline transition references to bounded internal FFmpeg
 * baseline transition operations with typed parameter validation and clip
 * relationship validation.</p>
 *
 * <p>Does not execute FFmpeg, generate shell commands, expose raw filtergraphs,
 * create RenderJob/Product, call StorageRuntime/ProductRuntime, call OpenCue,
 * or use Artifact DAG.</p>
 *
 * <p>Deterministic ordering: timeline order → track order → fromClip startMs →
 * toClip startMs → transition durationMs → operation type enum order →
 * transition id lexicographic.</p>
 */
public final class FFmpegBaselineTransitionPlanner {

    private static final Map<String, FFmpegBaselineTransitionOperationType> TRANSITION_MAP = Map.ofEntries(
            Map.entry("CUT", FFmpegBaselineTransitionOperationType.CUT),
            Map.entry("FADE", FFmpegBaselineTransitionOperationType.FADE),
            Map.entry("CROSSFADE", FFmpegBaselineTransitionOperationType.CROSSFADE),
            Map.entry("DISSOLVE", FFmpegBaselineTransitionOperationType.DISSOLVE),
            Map.entry("SLIDE", FFmpegBaselineTransitionOperationType.SLIDE),
            Map.entry("WIPE", FFmpegBaselineTransitionOperationType.WIPE),
            Map.entry("PUSH", FFmpegBaselineTransitionOperationType.PUSH),
            Map.entry("ZOOM", FFmpegBaselineTransitionOperationType.ZOOM)
    );

    private static final Set<String> FORBIDDEN_TRANSITION_KEYS = Set.of(
            "THREE_D_TRANSITION",
            "SHADER_TRANSITION",
            "ARBITRARY_TRANSITION_PLUGIN",
            "USER_DEFINED_TRANSITION_GRAPH",
            "PROVIDER_SPECIFIC_TRANSITION_GRAPH",
            "ARBITRARY_FFMPEG_FILTERGRAPH",
            "ARBITRARY_SHADER",
            "ARBITRARY_SCRIPT_EFFECT",
            "REMOTION_COMPONENT_EXECUTION",
            "USER_DEFINED_RENDER_DAG",
            "PLUGIN_INSERTED_RENDER_NODE",
            "PROVIDER_SPECIFIC_RAW_COMMAND"
    );

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "filter_complex", "filtergraph", "rawCommand", "shell command",
            "Runtime.getRuntime", "ProcessBuilder", "npx remotion",
            "remotion render", "npm install", "pnpm", "yarn");

    private FFmpegBaselineTransitionPlanner() {}

    /**
     * Plan FFmpeg baseline transitions from a timeline.
     */
    public static FFmpegBaselineTransitionPlanningResult plan(FFmpegBaselineTransitionPlanningRequest request) {
        if (request == null) {
            return FFmpegBaselineTransitionPlanningResult.failed(List.of(
                    FFmpegBaselineTransitionPlanIssue.error(
                            FFmpegBaselineTransitionPlanIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        FFmpegBaselineTransitionPolicy policy = request.policy() != null
                ? request.policy() : FFmpegBaselineTransitionPolicy.conservative();

        TimelineSpec timeline = request.timeline();
        if (timeline == null) {
            return FFmpegBaselineTransitionPlanningResult.validationFailed(null, List.of(
                    FFmpegBaselineTransitionPlanIssue.error(
                            FFmpegBaselineTransitionPlanIssueCode.INVALID_TIMELINE,
                            "Timeline must not be null")));
        }

        List<FFmpegBaselineTransitionOperation> operations = new ArrayList<>();
        List<FFmpegBaselineTransitionPlanIssue> issues = new ArrayList<>();
        AtomicInteger opSeq = new AtomicInteger(0);

        // Scan tracks → transitions
        if (timeline.tracks() != null) {
            for (TimelineTrack track : timeline.tracks()) {
                if (track.clips() == null || track.clips().size() < 2) continue;
                planTrackTransitions(track, timeline, policy, operations, issues, opSeq);
            }
        }

        // Build summary
        int baselineCount = 0, pocCount = 0, forbiddenCount = 0, warningCount = 0;
        for (FFmpegBaselineTransitionPlanIssue issue : issues) {
            if (issue.severity() == FFmpegBaselineTransitionPlanIssueSeverity.WARNING) warningCount++;
            if (issue.code() == FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN) forbiddenCount++;
        }
        for (FFmpegBaselineTransitionOperation op : operations) {
            if (isPoc(op.type())) pocCount++;
            else baselineCount++;
        }

        FFmpegBaselineTransitionPlanSummary summary = new FFmpegBaselineTransitionPlanSummary(
                operations.size(), baselineCount, pocCount, forbiddenCount, warningCount, Map.of());

        FFmpegBaselineTransitionPlanStatus planStatus = determinePlanStatus(issues, policy);
        FFmpegBaselineTransitionPlan plan = new FFmpegBaselineTransitionPlan(
                new FFmpegBaselineTransitionPlanId("plan-" + request.id().value()),
                planStatus, operations, summary, issues, Map.of());

        if (planStatus == FFmpegBaselineTransitionPlanStatus.BLOCKED
                || planStatus == FFmpegBaselineTransitionPlanStatus.FAILED) {
            return FFmpegBaselineTransitionPlanningResult.blocked(issues);
        }
        if (planStatus == FFmpegBaselineTransitionPlanStatus.UNSUPPORTED) {
            return FFmpegBaselineTransitionPlanningResult.unsupported(issues);
        }
        if (planStatus == FFmpegBaselineTransitionPlanStatus.INVALID) {
            return FFmpegBaselineTransitionPlanningResult.validationFailed(plan, issues);
        }

        return FFmpegBaselineTransitionPlanningResult.planned(plan);
    }

    private static void planTrackTransitions(
            TimelineTrack track, TimelineSpec timeline,
            FFmpegBaselineTransitionPolicy policy,
            List<FFmpegBaselineTransitionOperation> operations,
            List<FFmpegBaselineTransitionPlanIssue> issues,
            AtomicInteger opSeq) {

        // Sort clips by timelineStart for deterministic ordering
        List<TimelineClip> sortedClips = new ArrayList<>(track.clips());
        sortedClips.sort(Comparator.comparingDouble(TimelineClip::timelineStart));

        for (int i = 0; i < sortedClips.size() - 1; i++) {
            TimelineClip fromClip = sortedClips.get(i);
            TimelineClip toClip = sortedClips.get(i + 1);

            // Check adjacency
            double fromEnd = fromClip.timelineStart() + fromClip.clipDuration();
            double toStart = toClip.timelineStart();
            boolean adjacent = Math.abs(fromEnd - toStart) < 0.001;

            // Determine transition type — default to CUT for adjacent clips
            FFmpegBaselineTransitionOperationType transitionType = FFmpegBaselineTransitionOperationType.CUT;
            String transitionKey = "CUT";
            boolean hasExplicitTransition = false;

            // Check if clips have explicit transition effects
            if (fromClip.effects() != null) {
                for (TimelineClipEffect effect : fromClip.effects()) {
                    if (effect.effectKey() != null && isTransitionEffect(effect.effectKey())) {
                        transitionKey = effect.effectKey().toUpperCase();
                        hasExplicitTransition = true;
                        break;
                    }
                }
            }

            // Check forbidden keys first
            if (isForbidden(transitionKey)) {
                issues.add(FFmpegBaselineTransitionPlanIssue.blocking(
                        FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN,
                        "Forbidden transition: " + transitionKey));
                continue;
            }

            // Validate transition capability
            VisualCapabilityDefinition capability = resolveTransitionCapability(transitionKey);
            if (capability == null) {
                issues.add(FFmpegBaselineTransitionPlanIssue.error(
                        FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_NOT_FOUND,
                        "Unknown transition capability: " + transitionKey));
                continue;
            }

            // Check forbidden via capability policy
            if (VisualCapabilityPolicy.isForbidden(capability)) {
                issues.add(FFmpegBaselineTransitionPlanIssue.blocking(
                        FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN,
                        "Forbidden transition: " + transitionKey));
                continue;
            }

            // Check for raw filtergraph keywords in effect parameters
            if (fromClip.effects() != null) {
                for (TimelineClipEffect effect : fromClip.effects()) {
                    if (effect.parameters() != null) {
                        for (Map.Entry<String, Object> entry : effect.parameters().entrySet()) {
                            String val = entry.getValue() != null ? entry.getValue().toString().toLowerCase() : "";
                            String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
                            for (String kw : FORBIDDEN_KEYWORDS) {
                                if (val.contains(kw) || key.contains(kw)) {
                                    issues.add(FFmpegBaselineTransitionPlanIssue.blocking(
                                            FFmpegBaselineTransitionPlanIssueCode.RAW_FILTERGRAPH_FORBIDDEN,
                                            "Forbidden keyword in transition parameter: " + kw));
                                    return;
                                }
                            }
                        }
                    }
                }
            }

            // Check POC
            if (VisualCapabilityPolicy.isInternalOnly(capability)) {
                if (!policy.allowPocTransitions()) {
                    issues.add(FFmpegBaselineTransitionPlanIssue.warning(
                            FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_POC_ONLY,
                            "POC transition not allowed by policy: " + transitionKey));
                    continue;
                }
            }

            // Check restricted
            if (VisualCapabilityPolicy.requiresManualReview(capability)) {
                if (!policy.allowRestrictedTransitions()) {
                    issues.add(FFmpegBaselineTransitionPlanIssue.blocking(
                            FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_RESTRICTED,
                            "Restricted transition: " + transitionKey));
                    continue;
                }
            }

            // Map to operation type
            FFmpegBaselineTransitionOperationType opType = TRANSITION_MAP.get(transitionKey);
            if (opType == null) {
                issues.add(FFmpegBaselineTransitionPlanIssue.warning(
                        FFmpegBaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_UNSUPPORTED,
                        "No mapping for transition: " + transitionKey));
                continue;
            }

            // Validate adjacency
            if (!adjacent && policy.failOnNonAdjacentClips()) {
                issues.add(FFmpegBaselineTransitionPlanIssue.warning(
                        FFmpegBaselineTransitionPlanIssueCode.NON_ADJACENT_CLIPS,
                        "Non-adjacent clips: " + fromClip.id() + " -> " + toClip.id()));
            }

            // Determine duration
            double durationMs = 0;
            if (hasExplicitTransition && fromClip.effects() != null) {
                for (TimelineClipEffect effect : fromClip.effects()) {
                    if (effect.effectKey() != null && effect.effectKey().equalsIgnoreCase(transitionKey)) {
                        if (effect.parameters() != null && effect.parameters().containsKey("durationMs")) {
                            Object dur = effect.parameters().get("durationMs");
                            if (dur instanceof Number n) durationMs = n.doubleValue();
                        }
                    }
                }
            }

            // Validate duration
            if (opType != FFmpegBaselineTransitionOperationType.CUT || !policy.allowCutWithZeroDuration()) {
                if (durationMs <= 0) {
                    issues.add(FFmpegBaselineTransitionPlanIssue.error(
                            FFmpegBaselineTransitionPlanIssueCode.INVALID_TRANSITION_DURATION,
                            "Transition duration must be > 0 for " + transitionKey));
                    continue;
                }
            }

            // Build parameters
            List<FFmpegBaselineTransitionOperationParameter> params = new ArrayList<>();
            params.add(new FFmpegBaselineTransitionOperationParameter(
                    "durationMs", FFmpegBaselineTransitionParameterType.DURATION_MS, durationMs, Map.of()));

            // Build target
            FFmpegBaselineTransitionOperationTarget target = new FFmpegBaselineTransitionOperationTarget(
                    FFmpegBaselineTransitionOperationTargetType.CLIP_PAIR,
                    fromClip.id(), toClip.id(),
                    track.id(), timeline.id(),
                    "transition-" + opSeq.incrementAndGet(),
                    Map.of());

            // Build operation
            FFmpegBaselineTransitionOperationId opId = new FFmpegBaselineTransitionOperationId(
                    "op-" + opSeq.incrementAndGet() + "-" + transitionKey.toLowerCase());
            FFmpegBaselineTransitionOperationSource source = VisualCapabilityPolicy.isInternalOnly(capability)
                    ? FFmpegBaselineTransitionOperationSource.VISUAL_CAPABILITY_RESOLVED
                    : FFmpegBaselineTransitionOperationSource.BASIC_TIMELINE_TRANSITION_REF;

            operations.add(new FFmpegBaselineTransitionOperation(
                    opId, opType, target, params, source, Map.of()));
        }
    }

    private static boolean isTransitionEffect(String effectKey) {
        String upper = effectKey.toUpperCase();
        return TRANSITION_MAP.containsKey(upper) || FORBIDDEN_TRANSITION_KEYS.contains(upper);
    }

    private static VisualCapabilityDefinition resolveTransitionCapability(String transitionKey) {
        String upper = transitionKey.toUpperCase();
        return switch (upper) {
            case "CUT" -> TransitionCapabilityProfile.cut();
            case "FADE" -> TransitionCapabilityProfile.fade();
            case "CROSSFADE" -> TransitionCapabilityProfile.crossfade();
            case "DISSOLVE" -> TransitionCapabilityProfile.dissolve();
            case "SLIDE" -> TransitionCapabilityProfile.slide();
            case "WIPE" -> TransitionCapabilityProfile.wipe();
            case "PUSH" -> TransitionCapabilityProfile.push();
            case "ZOOM" -> TransitionCapabilityProfile.zoom();
            default -> null;
        };
    }

    private static boolean isForbidden(String transitionKey) {
        String upper = transitionKey.toUpperCase();
        return FORBIDDEN_TRANSITION_KEYS.contains(upper)
                || upper.contains("FILTERGRAPH")
                || upper.contains("FILTER_COMPLEX")
                || upper.contains("RAWCOMMAND");
    }

    private static boolean isPoc(FFmpegBaselineTransitionOperationType type) {
        return switch (type) {
            case SLIDE, WIPE, PUSH, ZOOM -> true;
            default -> false;
        };
    }

    private static FFmpegBaselineTransitionPlanStatus determinePlanStatus(
            List<FFmpegBaselineTransitionPlanIssue> issues, FFmpegBaselineTransitionPolicy policy) {
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == FFmpegBaselineTransitionPlanIssueSeverity.BLOCKING);
        boolean hasError = issues.stream().anyMatch(i ->
                i.severity() == FFmpegBaselineTransitionPlanIssueSeverity.ERROR);
        boolean hasWarning = issues.stream().anyMatch(i ->
                i.severity() == FFmpegBaselineTransitionPlanIssueSeverity.WARNING);

        if (hasBlocking) return FFmpegBaselineTransitionPlanStatus.BLOCKED;
        if (hasError) {
            return policy.failOnUnsupported()
                    ? FFmpegBaselineTransitionPlanStatus.INVALID
                    : FFmpegBaselineTransitionPlanStatus.VALID_WITH_WARNINGS;
        }
        if (hasWarning && !policy.allowWarnings()) {
            return FFmpegBaselineTransitionPlanStatus.INVALID;
        }
        if (hasWarning) return FFmpegBaselineTransitionPlanStatus.VALID_WITH_WARNINGS;
        return FFmpegBaselineTransitionPlanStatus.READY;
    }
}
