package com.example.platform.render.app.operation;

import com.example.platform.audio.domain.mix.AudioGain;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import com.example.platform.timeline.semantics.effect.BasicVideoEffect;
import com.example.platform.audio.domain.mix.AudioMixInput;
import com.example.platform.audio.domain.mix.AudioMute;
import com.example.platform.audio.domain.mix.StereoBalance;
import com.example.platform.operation.batch.BatchErrorCode;
import com.example.platform.operation.batch.BatchException;
import com.example.platform.operation.batch.OperationBatchRequest;
import com.example.platform.operation.batch.VersionedAggregateRef;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.operation.operation.OperationParameters;
import com.example.platform.operation.operation.OperationRequest;
import com.example.platform.operation.operation.OperationTargetRequest;
import com.example.platform.shared.time.MediaTime;
import com.example.platform.timeline.canonical.CompositionAspectRatio;
import com.example.platform.timeline.canonical.TimelineClipId;
import com.example.platform.timeline.semantics.clip.MediaClip;
import com.example.platform.timeline.semantics.relationship.AlignmentAttribute;
import com.example.platform.timeline.semantics.relationship.AlignmentAttributeValue;
import com.example.platform.timeline.semantics.relationship.AlignmentId;
import com.example.platform.timeline.semantics.relationship.GroupId;
import com.example.platform.timeline.semantics.relationship.SourceUnitId;
import com.example.platform.timeline.semantics.selection.SelectionSpec;
import com.example.platform.timeline.semantics.temporal.PlaybackDirection;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * Maps the authenticated HTTP batch payload to the canonical
 * {@link OperationBatchRequest}. This is the only web-side mapping: every entry
 * (REST today, agent/MCP later) converges on the same generic port.
 */
@Component
public final class TimelineBatchCommandMapper {

    public static final String AGGREGATE_KIND = "TIMELINE";
    public static final String MAIN_REF = "main";

    public OperationBatchRequest toBatchRequest(String tenantId, String projectId,
                                                TimelineBatchCommand http) {
        if (http == null) {
            throw new IllegalArgumentException("batch request body required");
        }
        if (http.operations() == null || http.operations().isEmpty()) {
            throw new BatchException(BatchErrorCode.EMPTY_BATCH, null,
                    "batch must contain at least one operation");
        }
        VersionedAggregateRef target = new VersionedAggregateRef(
                AGGREGATE_KIND, tenantId, projectId, projectId, MAIN_REF);
        List<OperationRequest> operations = new ArrayList<>(http.operations().size());
        for (int index = 0; index < http.operations().size(); index++) {
            operations.add(toOperation(projectId, http.baseRevisionId(), http.baseContentHash(),
                    http.operations().get(index), index));
        }
        return new OperationBatchRequest(http.batchId(), target, http.baseRevisionId(),
                operations, http.message());
    }

    private OperationRequest toOperation(String projectId, String baseRevisionId,
                                         String baseContentHash,
                                         TimelineBatchCommand.Operation operation, int index) {
        if (operation == null || operation.definitionId() == null
                || operation.definitionId().isBlank()) {
            throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, index,
                    "definitionId required");
        }
        String definitionId = operation.definitionId();
        if (definitionId.startsWith("timeline.text.")) {
            throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, index,
                    "text operations are not part of the v1 batch scope");
        }
        if ("timeline.composition.aspect.set".equals(definitionId) && operation.target() != null) {
            throw new IllegalArgumentException("Composition aspect targets the scoped Timeline without object coordinates");
        }
        OperationDefinition definition = definition(definitionId, index);
        Map<String, Object> parameters = operation.parameters() == null
                ? Map.of() : operation.parameters();
        OperationTargetRequest target = target(definition, projectId, operation.target(),
                parameters, index);
        OperationParameters typed = parameters(definitionId, parameters, projectId,
                baseRevisionId, baseContentHash, index);
        return new OperationRequest(definition.definitionId(), definition.version(), target,
                typed, baseRevisionId, baseContentHash, null);
    }

    private OperationDefinition definition(String definitionId, int index) {
        for (OperationDefinition candidate : OperationDefinition.V3.ALL) {
            if (candidate.definitionId().value().equals(definitionId)) {
                return candidate;
            }
        }
        throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, index,
                "unsupported batch operation: " + definitionId);
    }

    private OperationTargetRequest target(OperationDefinition definition, String projectId,
                                          TimelineBatchCommand.Target target,
                                          Map<String, Object> parameters, int index) {
        return switch (definition.targetKind()) {
            case TIMELINE, TEXT, ALIGNMENT ->
                    new OperationTargetRequest.TimelineTargetRequest(projectId);
            case CLIP_SCOPE -> new OperationTargetRequest.ClipSelectionTargetRequest(projectId,
                    new SelectionSpec.ExplicitObjectSelection(clipSelection(
                            definition.definitionId().value(), target, parameters, index)),
                    SelectionSpec.ExpansionPolicy.EXACT);
            case GROUP -> new OperationTargetRequest.GroupTargetRequest(
                    GroupId.of(requireGroupId(target, index)));
            case SYNC -> new OperationTargetRequest.SyncTargetRequest(
                    requireSyncKey(target, index));
            case AUDIO -> new OperationTargetRequest.AudioTargetRequest(
                    AudioMixInput.of(requireAudioTrackId(target, index), requireClipId(target, index)));
        };
    }

    private OperationParameters parameters(String definitionId, Map<String, Object> parameters,
                                           String projectId, String baseRevisionId,
                                           String baseContentHash, int index) {
        return switch (definitionId) {
            case "timeline.media-clip.add" -> (OperationParameters.AddMediaClipParameters)
                    addMediaClip(parameters, baseRevisionId, baseContentHash, projectId).parameters();
            case "timeline.move" -> new OperationParameters.MoveParameters(
                    time(parameters, "delta"), requiredBoolean(parameters, "absolute"));
            case "timeline.clip.relocate" -> new OperationParameters.RelocateClipParameters(
                    time(parameters, "delta"), requiredBoolean(parameters, "absolute"),
                    requiredText(parameters, "destinationTrackId"));
            case "timeline.trim" -> new OperationParameters.TrimParameters(
                    OperationParameters.TrimParameters.TrimEdge.valueOf(
                            requiredText(parameters, "edge")),
                    time(parameters, "delta"));
            case "timeline.composition.aspect.set" -> new OperationParameters.CompositionAspectParameters(
                    CompositionAspectRatio.fromParameters(parameters));
            case "timeline.clip.transform.set" -> new OperationParameters.VideoTransformParameters(
                    VideoTransform.fromParameters(parameters));
            case "timeline.clip.effect.set" -> new OperationParameters.VideoEffectParameters(
                    new BasicVideoEffect(requiredDouble(parameters, "brightness"),
                            requiredDouble(parameters, "contrast"),
                            requiredDouble(parameters, "saturation"),
                            requiredBoolean(parameters, "grayscale"),
                            requiredDouble(parameters, "fadeInSeconds"),
                            requiredDouble(parameters, "fadeOutSeconds"),
                            requiredBoolean(parameters, "enabled")));
            case "timeline.clip.effect.remove" -> new OperationParameters.NoParameters();
            case "timeline.transition.cross-dissolve.create" -> new OperationParameters.TransitionCreateParameters(
                    requiredText(parameters, "transitionId"),
                    requiredText(parameters, "incomingClipId"), time(parameters, "duration"));
            case "timeline.transition.cross-dissolve.update" -> new OperationParameters.TransitionUpdateParameters(
                    requiredText(parameters, "transitionId"), time(parameters, "duration"));
            case "timeline.transition.cross-dissolve.delete" -> new OperationParameters.TransitionDeleteParameters(
                    requiredText(parameters, "transitionId"));
            case "timeline.clip.split" -> new OperationParameters.SplitParameters(
                    TimelineClipId.of(requiredText(parameters, "rightClipId")),
                    time(parameters, "splitTimelineTime"));
            case "timeline.delete", "timeline.group.remove", "timeline.sync.remove" ->
                    new OperationParameters.NoParameters();
            case "timeline.temporal.set-rate" -> new OperationParameters.SetTemporalRateParameters(
                    new MediaClip.Rational(requiredLong(parameters, "numerator"),
                            requiredLong(parameters, "denominator")));
            case "timeline.temporal.set-direction" ->
                    new OperationParameters.SetTemporalDirectionParameters(
                            PlaybackDirection.valueOf(requiredText(parameters, "direction")));
            case "timeline.temporal.freeze" -> new OperationParameters.FreezeParameters(
                    time(parameters, "sourcePosition"));
            case "audio.gain.set" -> new OperationParameters.AudioGainParameters(
                    new AudioGain(requiredDouble(parameters, "linear")));
            case "audio.mute.set" -> new OperationParameters.AudioMuteParameters(
                    new AudioMute(requiredBoolean(parameters, "muted")));
            case "audio.balance.set" -> new OperationParameters.StereoBalanceParameters(
                    new StereoBalance(requiredDouble(parameters, "value")));
            case "timeline.group.create" -> new OperationParameters.CreateGroupParameters(
                    GroupId.of(requiredText(parameters, "groupId")),
                    clipIds(parameters, "members"));
            case "timeline.group.update-members" ->
                    new OperationParameters.UpdateGroupMembershipParameters(
                            clipIdSet(parameters, "membersToAdd"),
                            clipIdSet(parameters, "membersToRemove"));
            case "timeline.sync.create" -> new OperationParameters.CreateSyncParameters(
                    TimelineClipId.of(requiredText(parameters, "endpointA")),
                    time(parameters, "localAnchorA"),
                    TimelineClipId.of(requiredText(parameters, "endpointB")),
                    time(parameters, "localAnchorB"));
            case "timeline.sync.update-anchor" ->
                    new OperationParameters.UpdateSyncAnchorParameters(
                            time(parameters, "localAnchorA"), time(parameters, "localAnchorB"));
            case "timeline.alignment.create" -> new OperationParameters.CreateAlignmentParameters(
                    AlignmentId.of(requiredText(parameters, "alignmentId")),
                    sourceUnits(parameters, "sourceUnits"),
                    sourceUnits(parameters, "derivedUnits"),
                    attributes(parameters));
            default -> throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, index,
                    "unsupported batch operation: " + definitionId);
        };
    }

    private OperationRequest addMediaClip(Map<String, Object> parameters, String baseRevisionId,
                                          String baseContentHash, String projectId) {
        AddMediaClipCommand command = new AddMediaClipCommand(
                baseRevisionId,
                baseContentHash,
                requiredText(parameters, "trackId"),
                requiredText(parameters, "clipId"),
                requiredText(parameters, "mediaAssetId"),
                requiredText(parameters, "mediaStreamId"),
                requiredText(parameters, "artifactId"),
                requiredText(parameters, "contentDigest"),
                requiredText(parameters, "sourceStart"),
                requiredText(parameters, "sourceEnd"),
                requiredText(parameters, "timelineStart"),
                requiredText(parameters, "timelineEnd"),
                requiredLong(parameters, "rateNumerator"),
                requiredLong(parameters, "rateDenominator"),
                direction(parameters));
        return TimelineMediaClipOperationService.toOperationRequest(projectId, command);
    }

    private static AddMediaClipCommand.Direction direction(Map<String, Object> parameters) {
        Object node = parameters.get("direction");
        if (node == null) {
            return AddMediaClipCommand.Direction.FORWARD;
        }
        return AddMediaClipCommand.Direction.valueOf(String.valueOf(node));
    }

    private static List<AlignmentAttribute> attributes(Map<String, Object> parameters) {
        Object raw = parameters.get("attributes");
        if (raw == null) {
            return List.of();
        }
        if (!(raw instanceof List<?> items)) {
            throw new IllegalArgumentException("attributes must be an array");
        }
        List<AlignmentAttribute> attributes = new ArrayList<>();
        for (Object item : items) {
            if (!(item instanceof Map<?, ?> value)) {
                throw new IllegalArgumentException("attribute entries must be objects");
            }
            String tag = requiredText(value, "tag");
            String kind = requiredText(value, "kind");
            Object attributeValue = value.get("value");
            AlignmentAttributeValue typed = switch (kind) {
                case "TEXT" -> new AlignmentAttributeValue.TextValue(String.valueOf(attributeValue));
                case "NUMBER" -> new AlignmentAttributeValue.NumberValue(
                        number(attributeValue, "attribute value").longValue());
                case "BOOLEAN" -> new AlignmentAttributeValue.BooleanValue(
                        Boolean.TRUE.equals(attributeValue));
                default -> throw new IllegalArgumentException("unsupported attribute kind: " + kind);
            };
            attributes.add(new AlignmentAttribute(tag, typed));
        }
        return attributes;
    }

    private static Set<SourceUnitId> sourceUnits(Map<String, Object> parameters, String field) {
        Set<SourceUnitId> units = new LinkedHashSet<>();
        for (String value : stringArray(parameters, field)) {
            units.add(SourceUnitId.of(value));
        }
        if (units.isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty");
        }
        return units;
    }

    private static List<TimelineClipId> clipIds(Map<String, Object> parameters, String field) {
        List<TimelineClipId> clips = new ArrayList<>();
        for (String value : stringArray(parameters, field)) {
            clips.add(TimelineClipId.of(value));
        }
        return clips;
    }

    private static Set<TimelineClipId> clipIdSet(Map<String, Object> parameters, String field) {
        Object raw = parameters.get(field);
        if (raw == null) {
            return Set.of();
        }
        Set<TimelineClipId> clips = new LinkedHashSet<>();
        for (String value : stringArray(parameters, field)) {
            clips.add(TimelineClipId.of(value));
        }
        return clips;
    }

    private static List<String> stringArray(Map<String, Object> parameters, String field) {
        Object raw = parameters.get(field);
        if (!(raw instanceof List<?> items)) {
            throw new IllegalArgumentException(field + " must be an array");
        }
        List<String> values = new ArrayList<>(items.size());
        for (Object item : items) {
            values.add(String.valueOf(item));
        }
        return values;
    }

    private static List<TimelineClipId> clipSelection(String definitionId,
                                                     TimelineBatchCommand.Target target,
                                                     Map<String, Object> parameters, int index) {
        List<String> selected;
        if (target != null && target.clipIds() != null && !target.clipIds().isEmpty()) {
            selected = target.clipIds();
        } else if ("timeline.group.create".equals(definitionId)) {
            // The group member set is the semantic clip scope of the create step.
            selected = stringArray(parameters, "members");
        } else {
            selected = List.of(requireClipId(target, index));
        }
        List<TimelineClipId> clips = new ArrayList<>(selected.size());
        for (String value : selected) {
            if (value == null || value.isBlank()) {
                throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, index,
                        "operation " + index + " has a blank clip selection");
            }
            clips.add(TimelineClipId.of(value));
        }
        if (clips.isEmpty()) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, index,
                    "operation " + index + " has an empty clip selection");
        }
        return clips;
    }

    private static String requireClipId(TimelineBatchCommand.Target target, int index) {
        if (target == null || target.clipId() == null || target.clipId().isBlank()) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, index,
                    "operation " + index + " requires target.clipId");
        }
        return target.clipId();
    }

    private static String requireGroupId(TimelineBatchCommand.Target target, int index) {
        if (target == null || target.groupId() == null || target.groupId().isBlank()) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, index,
                    "operation " + index + " requires target.groupId");
        }
        return target.groupId();
    }

    private static String requireSyncKey(TimelineBatchCommand.Target target, int index) {
        if (target == null || target.syncIdentityKey() == null
                || target.syncIdentityKey().isBlank()) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, index,
                    "operation " + index + " requires target.syncIdentityKey");
        }
        return target.syncIdentityKey();
    }

    private static String requireAudioTrackId(TimelineBatchCommand.Target target, int index) {
        if (target == null || target.audioTrackId() == null || target.audioTrackId().isBlank()) {
            throw new BatchException(BatchErrorCode.STEP_RESOLUTION_FAILED, index,
                    "audio operation " + index + " requires target.audioTrackId");
        }
        return target.audioTrackId();
    }

    private static String requiredText(Map<?, ?> parameters, String field) {
        Object value = parameters.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new IllegalArgumentException(field + " required");
        }
        return String.valueOf(value);
    }

    private static boolean requiredBoolean(Map<?, ?> parameters, String field) {
        Object value = parameters.get(field);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return flag;
    }

    private static long requiredLong(Map<?, ?> parameters, String field) {
        return number(parameters.get(field), field).longValue();
    }

    private static double requiredDouble(Map<?, ?> parameters, String field) {
        return number(parameters.get(field), field).doubleValue();
    }

    private static Number number(Object value, String field) {
        if (!(value instanceof Number number)) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return number;
    }

    private static MediaTime time(Map<String, Object> parameters, String field) {
        String value = requiredText(parameters, field);
        return MediaTime.parse(value.contains("/") || "0".equals(value) ? value : value + "/1");
    }
}
