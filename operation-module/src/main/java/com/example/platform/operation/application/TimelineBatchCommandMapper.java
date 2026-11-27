package com.example.platform.operation.application;

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

    private static final com.fasterxml.jackson.databind.ObjectMapper TEXT_PARAMETERS =
            com.fasterxml.jackson.databind.json.JsonMapper.builder()
                    .disable(com.fasterxml.jackson.databind.MapperFeature.ALLOW_COERCION_OF_SCALARS)
                    .disable(com.fasterxml.jackson.databind.DeserializationFeature.ACCEPT_FLOAT_AS_INT)
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES)
                    .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_NUMBERS_FOR_ENUMS)
                    .build();

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
        String refId = http.refId() == null ? MAIN_REF : http.refId();
        if (!refId.matches(com.example.platform.timeline.revisioncommand.RevisionRef.EDITING_REF_PATTERN)) {
            throw new IllegalArgumentException("Invalid Timeline ref identity");
        }
        VersionedAggregateRef target = new VersionedAggregateRef(
                AGGREGATE_KIND, tenantId, projectId, projectId, refId);
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
                                         TimelineOperationStep operation, int index) {
        if (operation == null || operation.definitionId() == null
                || operation.definitionId().isBlank()) {
            throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, index,
                    "definitionId required");
        }
        String definitionId = operation.definitionId();
        if ("timeline.composition.aspect.set".equals(definitionId) && operation.target() != null) {
            throw new IllegalArgumentException("Composition aspect targets the scoped Timeline without object coordinates");
        }
        OperationDefinition definition = definition(definitionId, index);
        if (!TimelineOperationWireContract.describe(definition).invocationModes().contains("APPLY")) {
            throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, index, "Dedicated entry required");
        }
        if (operation.version() != null && !definition.version().toString().equals(operation.version())) {
            throw new BatchException(BatchErrorCode.UNSUPPORTED_BATCH_OPERATION, index, "Unsupported definition version");
        }
        Map<String, Object> parameters = operation.parameters() == null
                ? Map.of() : operation.parameters();
        TimelineOperationWireContract.validate(definition, operation, parameters);
        OperationTargetRequest target = target(definition, projectId, operation.target(),
                parameters, index);
        OperationParameters typed;
        if (definition.targetKind() == OperationDefinition.TargetKind.TEXT) {
            parameters.forEach((name, value) -> TextParameterSchema.validate(TextParameterSchema.field(definition, name), value));
            var fields = new java.util.LinkedHashMap<>(parameters);
            if (fields.containsKey("textElementId")) throw new IllegalArgumentException("Use the text element target");
            if (target instanceof OperationTargetRequest.TextElementTargetRequest text) {
                fields.put("textElementId", text.textElementId().value());
            }
            typed = TEXT_PARAMETERS.convertValue(fields, definition.parameterType());
        } else if (target instanceof OperationTargetRequest.TransitionTargetRequest transition) {
            typed = definition.equals(OperationDefinition.V3.UPDATE_CROSS_DISSOLVE)
                    ? new OperationParameters.TransitionUpdateParameters(transition.transitionId(), time(parameters, "duration"))
                    : new OperationParameters.TransitionDeleteParameters(transition.transitionId());
        } else {
            typed = parameters(definitionId, parameters, projectId,
                baseRevisionId, baseContentHash, index);
        }
        if (operation.input() != null) {
            if (!(typed instanceof OperationParameters.CompositionAspectParameters aspect)) {
                throw new IllegalArgumentException("Unsupported read-only input for definition");
            }
            typed = new OperationParameters.CompositionAspectParameters(aspect.aspectRatio(), operation.input());
        }
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
            case TRANSITION -> new OperationTargetRequest.TransitionTargetRequest(target.transitionId());
            case TEXT -> definition.equals(OperationDefinition.V1.ADD_TEXT_ELEMENT)
                    ? new OperationTargetRequest.TimelineTargetRequest(projectId)
                    : new OperationTargetRequest.TextElementTargetRequest(projectId, new com.example.platform.timeline.canonical.TextElementId(
                            java.util.Objects.requireNonNull(target, "Text target required").textElementId()));
            case TIMELINE, ALIGNMENT ->
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
            case "timeline.clip.slide" -> new OperationParameters.SlideParameters(requiredText(parameters, "trackId"),
                    TimelineClipId.of(requiredText(parameters, "leftClipId")), TimelineClipId.of(requiredText(parameters, "middleClipId")),
                    TimelineClipId.of(requiredText(parameters, "rightClipId")),
                    OperationParameters.SignedTimeOffset.parse(requiredText(parameters, "offset").strip()));
            case "timeline.clip.slip" -> OperationParameters.SlipParameters.parse(requiredText(parameters, "offset").strip());
            case "timeline.clip.roll" -> new OperationParameters.RollCutParameters(requiredText(parameters, "trackId"),
                    TimelineClipId.of(requiredText(parameters, "leftClipId")), TimelineClipId.of(requiredText(parameters, "rightClipId")),
                    time(parameters, "cutTime"));
            case "timeline.clip.paste", "timeline.clip.ripple-insert" -> new OperationParameters.PasteClipsParameters(
                    requiredText(parameters, "trackId"), clipIds(parameters, "sourceClipIds"),
                    clipIds(parameters, "newClipIds"), time(parameters, "timelineStart"));
            case "timeline.revision.restore" -> new OperationParameters.RestoreRevisionParameters(
                    requiredText(parameters, "sourceRevisionId"));
            case "timeline.media-clip.add" -> (OperationParameters.AddMediaClipParameters)
                    addMediaClip(parameters, baseRevisionId, baseContentHash, projectId).parameters();
            case "timeline.move" -> {
                String offset = requiredText(parameters, "delta").strip();
                boolean absolute = requiredBoolean(parameters, "absolute");
                if (!offset.startsWith("-")) {
                    yield new OperationParameters.MoveParameters(time(parameters, "delta"), absolute);
                }
                String magnitude = offset.substring(1);
                if (!magnitude.matches("[0-9]+(/[1-9][0-9]*)?")) {
                    throw new IllegalArgumentException("Move offset must be an exact signed rational");
                }
                yield new OperationParameters.MoveParameters(
                        MediaTime.parse(magnitude.contains("/") ? magnitude : magnitude + "/1"), absolute, true);
            }
            case "timeline.clip.relocate" -> new OperationParameters.RelocateClipParameters(
                    time(parameters, "delta"), requiredBoolean(parameters, "absolute"),
                    requiredText(parameters, "destinationTrackId"));
            case "timeline.trim" -> new OperationParameters.TrimParameters(
                    OperationParameters.TrimParameters.TrimEdge.valueOf(
                            requiredText(parameters, "edge")),
                    time(parameters, "delta"));
            case "timeline.composition.aspect.set" -> new OperationParameters.CompositionAspectParameters(
                    CompositionAspectRatio.fromParameters(parameters));
            case "timeline.subtitle.cue.set" -> subtitleCue(parameters);
            case "timeline.subtitle.cue.remove" -> new OperationParameters.RemoveSubtitleCueParameters(requiredText(parameters, "cueId"));
            case "timeline.clip.gain.keyframes.set" -> gainKeys(parameters);
            case "timeline.clip.scale.keyframes.set" -> scaleKeys(parameters);
            case "timeline.clip.position.keyframes.set" -> positionKeys(parameters);
            case "timeline.clip.opacity.keyframes.set" -> new OperationParameters.OpacityParameters(
                    new com.example.platform.timeline.semantics.effect.OpacityKeys(time(parameters, "firstTime"),
                            opacity(parameters, "firstValue"), time(parameters, "lastTime"), opacity(parameters, "lastValue")));
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
            case "timeline.clip.split" -> new OperationParameters.SplitParameters(
                    TimelineClipId.of(requiredText(parameters, "rightClipId")),
                    time(parameters, "splitTimelineTime"));
            case "timeline.delete", "timeline.clip.ripple-delete", "timeline.group.remove", "timeline.sync.remove" ->
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
            if (!value.keySet().equals(Set.of("tag", "kind", "value"))) {
                throw new IllegalArgumentException("Invalid alignment attribute fields");
            }
            String tag = requiredText(value, "tag");
            String kind = requiredText(value, "kind");
            AlignmentAttributeValue typed = switch (kind) {
                case "TEXT" -> new AlignmentAttributeValue.TextValue(requiredText(value, "value"));
                case "NUMBER" -> new AlignmentAttributeValue.NumberValue(
                        requiredLong(value, "value"));
                case "BOOLEAN" -> new AlignmentAttributeValue.BooleanValue(
                        requiredBoolean(value, "value"));
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
            if (!(item instanceof String text) || text.isBlank()) {
                throw new IllegalArgumentException(field + " must contain nonblank strings");
            }
            values.add(text);
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
        if (!(value instanceof String text) || text.isBlank()) {
            throw new IllegalArgumentException(field + " required");
        }
        return text;
    }

    private static boolean requiredBoolean(Map<?, ?> parameters, String field) {
        Object value = parameters.get(field);
        if (!(value instanceof Boolean flag)) {
            throw new IllegalArgumentException(field + " must be a boolean");
        }
        return flag;
    }

    private static long requiredLong(Map<?, ?> parameters, String field) {
        Object value = parameters.get(field);
        if (value instanceof Integer integer) return integer.longValue();
        if (value instanceof Long integer) return integer;
        throw new IllegalArgumentException(field + " must be an exact signed 64-bit integer");
    }

    private static OperationParameters.SubtitleCueParameters subtitleCue(Map<String, Object> p) {
        if (!(p.get("keys") instanceof List<?> keys) || keys.size() > com.example.platform.timeline.canonical.SubtitleCueMotion.MAX_KEYS) {
            throw new IllegalArgumentException("Subtitle keys must be a bounded array");
        }
        String start = requiredText(p, "start"), end = requiredText(p, "end");
        String rational = "(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})";
        if (!start.matches(rational) || !end.matches(rational)) throw new IllegalArgumentException("Subtitle times must be exact rationals");
        var frames = new ArrayList<com.example.platform.timeline.canonical.SubtitleCueMotion.Key>();
        for (Object item : keys) {
            if (!(item instanceof Map<?, ?> key) || !key.keySet().equals(Set.of("time","xMicros","yMicros","opacityMicros"))
                    || !(key.get("time") instanceof String time)) throw new IllegalArgumentException("Invalid subtitle key fields");
            frames.add(new com.example.platform.timeline.canonical.SubtitleCueMotion.Key(time,
                    cueInteger(key,"xMicros"),cueInteger(key,"yMicros"),cueInteger(key,"opacityMicros")));
        }
        var runs = new ArrayList<com.example.platform.fonttext.typography.SubtitleFontRun>();
        if (!(p.getOrDefault("fontRuns", List.of()) instanceof List<?> supplied) || supplied.size() > com.example.platform.fonttext.typography.SubtitleFontRun.MAX_RUNS)
            throw new IllegalArgumentException("Expected a bounded array of subtitle font ranges");
        for (Object item : supplied) {
            if (!(item instanceof Map<?,?> range) || !range.keySet().equals(Set.of("start","end","fontProfile"))
                    || !(range.get("fontProfile") instanceof String pin)) throw new IllegalArgumentException("Invalid inline font fields");
            runs.add(new com.example.platform.fonttext.typography.SubtitleFontRun(cueInteger(range,"start"), cueInteger(range,"end"), pin));
        }
        var fade = selectedTextFade(p);
        var fadeIntent = fade != null ? OperationParameters.SubtitleCueParameters.FadeIntent.SET
                : p.containsKey("selectedTextFade") ? OperationParameters.SubtitleCueParameters.FadeIntent.CLEAR
                : OperationParameters.SubtitleCueParameters.FadeIntent.PRESERVE;
        return new OperationParameters.SubtitleCueParameters(requiredText(p,"cueId"), MediaTime.parse(start), MediaTime.parse(end),
                requiredText(p,"text"), cueInteger(p,"width"),cueInteger(p,"fontSize"),
                new com.example.platform.timeline.canonical.SubtitleCueMotion(
                        p.containsKey("fontProfile") ? requiredText(p, "fontProfile") : null, frames, runs, fade, decoration(p)),
                !p.containsKey("fontProfile"), false, p.containsKey("fontRuns"), fadeIntent, p.containsKey("decoration"));
    }

    private static com.example.platform.timeline.canonical.SubtitleCueMotion.SelectedTextFade selectedTextFade(Map<String, Object> p) {
        if (p.get("selectedTextFade") == null) return null;
        if (!(p.get("selectedTextFade") instanceof Map<?, ?> fade)
                || !fade.keySet().equals(Set.of("start", "end", "time", "duration"))
                || !(fade.get("time") instanceof String time) || !(fade.get("duration") instanceof String duration)) {
            throw new IllegalArgumentException("Invalid selected text fade fields");
        }
        return new com.example.platform.timeline.canonical.SubtitleCueMotion.SelectedTextFade(
                cueInteger(fade, "start"), cueInteger(fade, "end"), time, duration);
    }

    private static com.example.platform.timeline.canonical.SubtitleCueMotion.Decoration decoration(Map<String, Object> p) {
        if (p.get("decoration") == null) return null;
        if (!(p.get("decoration") instanceof Map<?, ?> value)
                || !value.keySet().equals(Set.of("assetId", "placement", "enabled", "start", "end"))
                || !(value.get("assetId") instanceof String asset) || !(value.get("placement") instanceof String placement)
                || !(value.get("enabled") instanceof Boolean enabled)
                || !(value.get("start") instanceof String start) || !(value.get("end") instanceof String end)) {
            throw new IllegalArgumentException("Invalid subtitle decoration fields");
        }
        return new com.example.platform.timeline.canonical.SubtitleCueMotion.Decoration(asset, placement, enabled, start, end);
    }

    private static int cueInteger(Map<?, ?> map, String key) {
        long value = requiredLong(map,key);
        if (value < Integer.MIN_VALUE || value > Integer.MAX_VALUE) throw new IllegalArgumentException("Subtitle integer out of range");
        return (int)value;
    }

    private static OperationParameters.GainParameters gainKeys(Map<String, Object> parameters) {
        if (!(parameters.get("keys") instanceof List<?> raw)
                || raw.size() > com.example.platform.timeline.semantics.effect.GainKeys.MAX_KEYS) {
            throw new IllegalArgumentException("Gain keys must be a bounded array");
        }
        var keys = new java.util.ArrayList<com.example.platform.timeline.semantics.effect.GainKeys.Key>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> key) || !key.keySet().equals(java.util.Set.of("time", "multiplierMicros"))
                    || !(key.get("time") instanceof String time) || !time.matches("[0-9]{1,19}/[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException("Each gain key requires a nonnegative rational time and multiplierMicros");
            }
            keys.add(new com.example.platform.timeline.semantics.effect.GainKeys.Key(MediaTime.parse(time), requiredLong(key, "multiplierMicros")));
        }
        return new OperationParameters.GainParameters(new com.example.platform.timeline.semantics.effect.GainKeys(keys));
    }
    private static OperationParameters.ScaleParameters scaleKeys(Map<String, Object> parameters) {
        if (!(parameters.get("keys") instanceof List<?> raw)
                || raw.size() > com.example.platform.timeline.semantics.effect.ScaleKeys.MAX_KEYS) {
            throw new IllegalArgumentException("Scale keys must be a bounded array");
        }
        var keys = new java.util.ArrayList<com.example.platform.timeline.semantics.effect.ScaleKeys.Key>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> key) || !key.keySet().equals(java.util.Set.of("time", "multiplierMicros"))
                    || !(key.get("time") instanceof String time) || !time.matches("[0-9]{1,19}/[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException("Each scale key requires a nonnegative rational time and multiplierMicros");
            }
            keys.add(new com.example.platform.timeline.semantics.effect.ScaleKeys.Key(MediaTime.parse(time), requiredLong(key, "multiplierMicros")));
        }
        return new OperationParameters.ScaleParameters(new com.example.platform.timeline.semantics.effect.ScaleKeys(keys));
    }

    private static OperationParameters.PositionParameters positionKeys(Map<String, Object> parameters) {
        if (!(parameters.get("keys") instanceof List<?> raw)
                || raw.size() > com.example.platform.timeline.semantics.effect.PositionKeys.MAX_KEYS) {
            throw new IllegalArgumentException("Position keys must be a bounded array");
        }
        var keys = new java.util.ArrayList<com.example.platform.timeline.semantics.effect.PositionKeys.Key>();
        for (Object item : raw) {
            if (!(item instanceof Map<?, ?> key) || !key.keySet().equals(java.util.Set.of("time", "xMicros", "yMicros"))) {
                throw new IllegalArgumentException("Each position key requires time, xMicros and yMicros");
            }
            if (!(key.get("time") instanceof String time) || !time.matches("[0-9]+/[1-9][0-9]*")) {
                throw new IllegalArgumentException("Position key time must be an exact nonnegative rational");
            }
            long x = requiredLong(key, "xMicros"), y = requiredLong(key, "yMicros");
            int limit = com.example.platform.timeline.semantics.effect.PositionKeys.LIMIT;
            if (x < -limit || x > limit || y < -limit || y > limit) {
                throw new IllegalArgumentException("Position key offsets must be within 16 composition dimensions");
            }
            keys.add(new com.example.platform.timeline.semantics.effect.PositionKeys.Key(MediaTime.parse(time), (int) x, (int) y));
        }
        return new OperationParameters.PositionParameters(new com.example.platform.timeline.semantics.effect.PositionKeys(keys));
    }

    private static double opacity(Map<?, ?> parameters, String field) {
        var value = new java.math.BigDecimal(number(parameters.get(field), field).toString());
        if (value.signum() < 0 || value.compareTo(java.math.BigDecimal.ONE) > 0) {
            throw new IllegalArgumentException(field + " must be between zero and one");
        }
        return value.doubleValue();
    }

    private static double requiredDouble(Map<?, ?> parameters, String field) {
        return number(parameters.get(field), field).doubleValue();
    }

    private static Number number(Object value, String field) {
        if (!(value instanceof Number number) || !Double.isFinite(number.doubleValue())) {
            throw new IllegalArgumentException(field + " must be a number");
        }
        return number;
    }

    private static MediaTime time(Map<String, Object> parameters, String field) {
        String value = requiredText(parameters, field);
        return MediaTime.parse(value.contains("/") || "0".equals(value) ? value : value + "/1");
    }
}
