package com.example.platform.render.domain.transition;


import com.example.platform.render.domain.visual.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.example.platform.render.domain.interchange.TimelineSpec;
import com.example.platform.render.domain.legacy.TimelineClip;
import com.example.platform.render.domain.legacy.TimelineClipEffect;
import com.example.platform.render.domain.legacy.TimelineTrack;

/**
 * Pure, side-effect-free baseline transition planner.
 * Internal domain model.
 *
 * <p>Maps semantic timeline transition references to bounded internal baseline
 * transition operations with typed parameter validation and clip
 * relationship validation.</p>
 *
 * <p>Does not invoke provider runtimes, generate shell commands, expose raw provider expressions,
 * create RenderJob/Product, call StorageRuntime/ProductRuntime, call OpenCue,
 * or use Artifact DAG.</p>
 *
 * <p>Deterministic ordering: timeline order → track order → fromClip startMs →
 * toClip startMs → transition durationMs → operation type enum order →
 * transition id lexicographic.</p>
 */
public final class BaselineTransitionPlanner {

    private static final Map<String, BaselineTransitionOperationType> TRANSITION_MAP = Map.ofEntries(
            Map.entry("CUT", BaselineTransitionOperationType.CUT),
            Map.entry("FADE", BaselineTransitionOperationType.FADE),
            Map.entry("CROSSFADE", BaselineTransitionOperationType.CROSSFADE),
            Map.entry("DISSOLVE", BaselineTransitionOperationType.DISSOLVE),
            Map.entry("SLIDE", BaselineTransitionOperationType.SLIDE),
            Map.entry("WIPE", BaselineTransitionOperationType.WIPE),
            Map.entry("PUSH", BaselineTransitionOperationType.PUSH),
            Map.entry("ZOOM", BaselineTransitionOperationType.ZOOM)
    );

    private static final Set<String> FORBIDDEN_TRANSITION_KEYS = Set.of(
            "THREE_D_TRANSITION",
            "SHADER_TRANSITION",
            "ARBITRARY_TRANSITION_PLUGIN",
            "USER_DEFINED_TRANSITION_GRAPH",
            "PROVIDER_SPECIFIC_TRANSITION_GRAPH",
            "ARBITRARY_PROVIDER_EXPRESSION",
            "ARBITRARY_SHADER",
            "ARBITRARY_SCRIPT_EFFECT",
            "REMOTION_COMPONENT_EXECUTION",
            "USER_DEFINED_RENDER_DAG",
            "PLUGIN_INSERTED_RENDER_NODE",
            "PROVIDER_SPECIFIC_RAW_COMMAND"
    );

    private static final Set<String> FORBIDDEN_KEYWORDS = Set.of(
            "provider_expression", "provider expression", "rawCommand", "shell command",
            "Runtime.getRuntime", "ProcessBuilder", "npx remotion",
            "remotion render", "npm install", "pnpm", "yarn");

    private BaselineTransitionPlanner() {}

    /**
     * Plan baseline transitions from a timeline.
     */
    public static BaselineTransitionPlanningResult plan(BaselineTransitionPlanningRequest request) {
        if (request == null) {
            return BaselineTransitionPlanningResult.failed(List.of(
                    BaselineTransitionPlanIssue.error(
                            BaselineTransitionPlanIssueCode.INVALID_REQUEST,
                            "Request must not be null")));
        }

        BaselineTransitionPolicy policy = request.policy() != null
                ? request.policy() : BaselineTransitionPolicy.conservative();

        TimelineSpec timeline = request.timeline();
        if (timeline == null) {
            return BaselineTransitionPlanningResult.validationFailed(null, List.of(
                    BaselineTransitionPlanIssue.error(
                            BaselineTransitionPlanIssueCode.INVALID_TIMELINE,
                            "Timeline must not be null")));
        }

        List<BaselineTransitionOperation> operations = new ArrayList<>();
        List<BaselineTransitionPlanIssue> issues = new ArrayList<>();
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
        for (BaselineTransitionPlanIssue issue : issues) {
            if (issue.severity() == BaselineTransitionPlanIssueSeverity.WARNING) warningCount++;
            if (issue.code() == BaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN) forbiddenCount++;
        }
        for (BaselineTransitionOperation op : operations) {
            if (isPoc(op.type())) pocCount++;
            else baselineCount++;
        }

        BaselineTransitionPlanSummary summary = new BaselineTransitionPlanSummary(
                operations.size(), baselineCount, pocCount, forbiddenCount, warningCount, Map.of());

        BaselineTransitionPlanStatus planStatus = determinePlanStatus(issues, policy);
        BaselineTransitionPlan plan = new BaselineTransitionPlan(
                new BaselineTransitionPlanId("plan-" + request.id().value()),
                planStatus, operations, summary, issues, Map.of());

        if (planStatus == BaselineTransitionPlanStatus.BLOCKED
                || planStatus == BaselineTransitionPlanStatus.FAILED) {
            return BaselineTransitionPlanningResult.blocked(issues);
        }
        if (planStatus == BaselineTransitionPlanStatus.UNSUPPORTED) {
            return BaselineTransitionPlanningResult.unsupported(issues);
        }
        if (planStatus == BaselineTransitionPlanStatus.INVALID) {
            return BaselineTransitionPlanningResult.validationFailed(plan, issues);
        }

        return BaselineTransitionPlanningResult.planned(plan);
    }

    private static void planTrackTransitions(
            TimelineTrack track, TimelineSpec timeline,
            BaselineTransitionPolicy policy,
            List<BaselineTransitionOperation> operations,
            List<BaselineTransitionPlanIssue> issues,
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
            BaselineTransitionOperationType transitionType = BaselineTransitionOperationType.CUT;
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
                issues.add(BaselineTransitionPlanIssue.blocking(
                        BaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN,
                        "Forbidden transition: " + transitionKey));
                continue;
            }

            // Validate transition capability
            VisualCapabilityDefinition capability = resolveTransitionCapability(transitionKey);
            if (capability == null) {
                issues.add(BaselineTransitionPlanIssue.error(
                        BaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_NOT_FOUND,
                        "Unknown transition capability: " + transitionKey));
                continue;
            }

            // Check forbidden via capability policy
            if (VisualCapabilityPolicy.isForbidden(capability)) {
                issues.add(BaselineTransitionPlanIssue.blocking(
                        BaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_FORBIDDEN,
                        "Forbidden transition: " + transitionKey));
                continue;
            }

            // Check for raw provider expression keywords in effect parameters
            if (fromClip.effects() != null) {
                for (TimelineClipEffect effect : fromClip.effects()) {
                    if (effect.parameters() != null) {
                        for (Map.Entry<String, Object> entry : effect.parameters().entrySet()) {
                            String val = entry.getValue() != null ? entry.getValue().toString().toLowerCase() : "";
                            String key = entry.getKey() != null ? entry.getKey().toLowerCase() : "";
                            for (String kw : FORBIDDEN_KEYWORDS) {
                                if (val.contains(kw) || key.contains(kw)) {
                                    issues.add(BaselineTransitionPlanIssue.blocking(
                                            BaselineTransitionPlanIssueCode.RAW_PROVIDER_EXPRESSION_FORBIDDEN,
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
                    issues.add(BaselineTransitionPlanIssue.warning(
                            BaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_POC_ONLY,
                            "POC transition not allowed by policy: " + transitionKey));
                    continue;
                }
            }

            // Check restricted
            if (VisualCapabilityPolicy.requiresManualReview(capability)) {
                if (!policy.allowRestrictedTransitions()) {
                    issues.add(BaselineTransitionPlanIssue.blocking(
                            BaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_RESTRICTED,
                            "Restricted transition: " + transitionKey));
                    continue;
                }
            }

            // Map to operation type
            BaselineTransitionOperationType opType = TRANSITION_MAP.get(transitionKey);
            if (opType == null) {
                issues.add(BaselineTransitionPlanIssue.warning(
                        BaselineTransitionPlanIssueCode.TRANSITION_CAPABILITY_UNSUPPORTED,
                        "No mapping for transition: " + transitionKey));
                continue;
            }

            // Validate adjacency
            if (!adjacent && policy.failOnNonAdjacentClips()) {
                issues.add(BaselineTransitionPlanIssue.warning(
                        BaselineTransitionPlanIssueCode.NON_ADJACENT_CLIPS,
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
            if (opType != BaselineTransitionOperationType.CUT || !policy.allowCutWithZeroDuration()) {
                if (durationMs <= 0) {
                    issues.add(BaselineTransitionPlanIssue.error(
                            BaselineTransitionPlanIssueCode.INVALID_TRANSITION_DURATION,
                            "Transition duration must be > 0 for " + transitionKey));
                    continue;
                }
            }

            // Build parameters
            List<BaselineTransitionOperationParameter> params = new ArrayList<>();
            params.add(new BaselineTransitionOperationParameter(
                    "durationMs", BaselineTransitionParameterType.DURATION_MS, durationMs, Map.of()));

            // Build target
            BaselineTransitionOperationTarget target = new BaselineTransitionOperationTarget(
                    BaselineTransitionOperationTargetType.CLIP_PAIR,
                    fromClip.id(), toClip.id(),
                    track.id(), timeline.id(),
                    "transition-" + opSeq.incrementAndGet(),
                    Map.of());

            // Build operation
            BaselineTransitionOperationId opId = new BaselineTransitionOperationId(
                    "op-" + opSeq.incrementAndGet() + "-" + transitionKey.toLowerCase());
            BaselineTransitionOperationSource source = VisualCapabilityPolicy.isInternalOnly(capability)
                    ? BaselineTransitionOperationSource.VISUAL_CAPABILITY_RESOLVED
                    : BaselineTransitionOperationSource.BASIC_TIMELINE_TRANSITION_REF;

            operations.add(new BaselineTransitionOperation(
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
                || upper.contains("PROVIDER_EXPRESSION")
                || upper.contains("PROVIDER_EXPRESSION")
                || upper.contains("RAWCOMMAND");
    }

    private static boolean isPoc(BaselineTransitionOperationType type) {
        return switch (type) {
            case SLIDE, WIPE, PUSH, ZOOM -> true;
            default -> false;
        };
    }

    private static BaselineTransitionPlanStatus determinePlanStatus(
            List<BaselineTransitionPlanIssue> issues, BaselineTransitionPolicy policy) {
        boolean hasBlocking = issues.stream().anyMatch(i ->
                i.severity() == BaselineTransitionPlanIssueSeverity.BLOCKING);
        boolean hasError = issues.stream().anyMatch(i ->
                i.severity() == BaselineTransitionPlanIssueSeverity.ERROR);
        boolean hasWarning = issues.stream().anyMatch(i ->
                i.severity() == BaselineTransitionPlanIssueSeverity.WARNING);

        if (hasBlocking) return BaselineTransitionPlanStatus.BLOCKED;
        if (hasError) {
            return policy.failOnUnsupported()
                    ? BaselineTransitionPlanStatus.INVALID
                    : BaselineTransitionPlanStatus.VALID_WITH_WARNINGS;
        }
        if (hasWarning && !policy.allowWarnings()) {
            return BaselineTransitionPlanStatus.INVALID;
        }
        if (hasWarning) return BaselineTransitionPlanStatus.VALID_WITH_WARNINGS;
        return BaselineTransitionPlanStatus.READY;
    }
}
