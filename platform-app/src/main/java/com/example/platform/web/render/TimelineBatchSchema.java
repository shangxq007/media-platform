package com.example.platform.web.render;

import com.example.platform.operation.application.TimelineOperationWireContract;
import com.example.platform.operation.application.TimelineBatchCommand;
import com.example.platform.operation.batch.OperationBatchRequest;
import com.example.platform.operation.operation.OperationDefinition;
import com.example.platform.timeline.canonical.CompositionAspectRatio;
import com.example.platform.timeline.semantics.effect.VideoTransform;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Public JSON Schema built from the same definition and wire field contracts used at admission. */
public final class TimelineBatchSchema {
    private TimelineBatchSchema() {}

    private static final Map<String, Object> STRING = Map.of("type", "string");
    private static final Map<String, Object> ID = Map.of("type", "string", "minLength", 1);
    private static final Map<String, Object> SOURCE_ID = Map.of("type", "string", "minLength", 1, "maxLength",
            com.example.platform.operation.operation.OperationParameters.SourceStreamInput.MAX_ID_LENGTH);
    private static final Map<String, Object> HASH = Map.of("type", "string", "pattern", "^[0-9a-f]{64}$");
    private static final Map<String, Object> POSITIVE = Map.of("type", "integer", "minimum", 1, "maximum", Integer.MAX_VALUE);
    private static final Map<String, Object> ASPECT = object(Map.of("format", literal(CompositionAspectRatio.FORMAT),
            "numerator", POSITIVE, "denominator", POSITIVE), List.of("format", "numerator", "denominator"));

    public static Map<String, Object> batch() {
        var alternatives = OperationDefinition.V3.ALL.stream()
                .filter(def -> TimelineOperationWireContract.describe(def).invocationModes().contains("APPLY"))
                .map(TimelineBatchSchema::step).toList();
        return object(Map.of("batchId", Map.of("type", "string", "pattern", "^[A-Za-z0-9._:-]{1,64}$"),
                "refId", Map.of("type", "string", "pattern", "^"
                        + com.example.platform.timeline.revisioncommand.RevisionRef.EDITING_REF_PATTERN + "$"),
                "baseRevisionId", Map.of("type", "string", "minLength", 1, "maxLength", 64),
                "baseContentHash", Map.of("type", "string", "pattern", "^" + TimelineBatchCommand.TIMELINE_DIGEST + "$"), "message", Map.of("type", "string", "maxLength", OperationBatchRequest.MAX_MESSAGE_LENGTH),
                "operations", Map.of("type", "array", "minItems", 1, "maxItems", OperationBatchRequest.MAX_STEPS,
                        "items", Map.of("oneOf", alternatives),
                        "if", Map.of("contains", Map.of("properties", Map.of("definitionId", literal(
                                OperationDefinition.V3.RESTORE_REVISION.definitionId().value())))),
                        "then", Map.of("maxItems", 1))), List.of("batchId", "baseRevisionId", "baseContentHash", "operations"));
    }

    public static Map<String, Object> step(OperationDefinition definition) {
        var wire = TimelineOperationWireContract.describe(definition);
        var fields = new LinkedHashMap<String, Object>();
        var required = new ArrayList<>(List.of("definitionId"));
        fields.put("definitionId", literal(wire.id()));
        fields.put("version", literal(wire.version()));
        var parameters = new LinkedHashMap<String, Object>();
        var requiredParameters = new ArrayList<String>();
        wire.parameters().entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(entry -> {
            String name = entry.getKey();
            var field = entry.getValue();
            parameters.put(name, name.equals("format") && definition.equals(OperationDefinition.V3.SET_VIDEO_TRANSFORM)
                    ? literal(VideoTransform.FORMAT) : definition.targetKind() == OperationDefinition.TargetKind.TEXT
                            ? com.example.platform.operation.application.TextParameterSchema.field(definition, name)
                            : parameterType(field.type()));
            if (definition.equals(OperationDefinition.V1.CREATE_GROUP) && name.equals("members")) {
                parameters.put(name, Map.of("type", "array", "items", ID, "minItems", definition.minCardinality(), "uniqueItems", true));
            }
            if (field.required()) requiredParameters.add(name);
        });
        fields.put("parameters", object(parameters, requiredParameters));
        if (!requiredParameters.isEmpty()) required.add("parameters");
        var target = definition.equals(OperationDefinition.V1.CREATE_GROUP) ? null : target(wire.target(), wire.maxCardinality());
        if (target != null) {
            fields.put("target", target);
            required.add("target");
        }
        if (!wire.inputKinds().isEmpty()) fields.put("input", object(
                Map.of("artifactId", SOURCE_ID, "contentDigest", HASH, "streamId", SOURCE_ID), List.of("artifactId", "contentDigest", "streamId")));
        return object(fields, required);
    }

    private static Map<String, Object> target(String kind, int max) {
        return switch (kind) {
            case "GROUP" -> identity("groupId");
            case "SYNC" -> identity("syncIdentityKey");
            case "TRANSITION" -> identity("transitionId");
            case "TEXT_ELEMENT" -> identity("textElementId");
            case "AUDIO" -> object(Map.of("audioTrackId", ID, "clipId", ID), List.of("audioTrackId", "clipId"));
            case "CLIP_SCOPE" -> Map.of("oneOf", List.of(identity("clipId"), object(Map.of("clipIds",
                    Map.of("type", "array", "items", ID, "minItems", 1, "maxItems", max, "uniqueItems", true)), List.of("clipIds"))));
            default -> null;
        };
    }

    private static Map<String, Object> parameterType(String type) {
        return switch (type) {
            case "integer", "number", "boolean", "string" -> Map.of("type", type);
            case "composition-aspect-rational-v1" -> ASPECT;
            case "revision-id" -> Map.of("type", "string", "pattern", "^"
                    + com.example.platform.operation.operation.OperationParameters.RestoreRevisionParameters.SOURCE_REVISION_ID_PATTERN + "$");
            case "cue-time" -> Map.of("type","string","pattern","^(?:0|[0-9]{1,19}/[1-9][0-9]{0,18})$");
            case "cue-font" -> Map.of("type", "string", "pattern", "^"
                    + com.example.platform.fonttext.typography.SubtitleBlockLayout.PROFILE_ID_PATTERN + "$");
            case "cue-font-runs" -> Map.of("type", "array", "maxItems", com.example.platform.fonttext.typography.SubtitleFontRun.MAX_RUNS,
                    "items", object(Map.of("start", integerRange(0,383), "end", integerRange(1,384),
                            "fontProfile", parameterType("cue-font")), List.of("start","end","fontProfile")));
            case "cue-selected-text-fade" -> object(Map.of(
                    "start", integerRange(0,383), "end", integerRange(1,384), "time", parameterType("cue-time"),
                    "duration", Map.of("type", "string", "pattern", "^[1-9][0-9]{0,18}/[1-9][0-9]{0,18}$")),
                    List.of("start","end","time","duration"));
            case "cue-width" -> integerRange(40,304);
            case "cue-font-size" -> integerRange(12,44);
            case "cue-key-array" -> Map.of("type","array","maxItems",16,
                    "not", Map.of("minItems",1,"maxItems",1),
                    "items", object(Map.of("time",parameterType("cue-time"),
                            "xMicros",integerRange(-16000000,16000000), "yMicros",integerRange(-16000000,16000000),
                            "opacityMicros",integerRange(0,1000000)), List.of("time","xMicros","yMicros","opacityMicros")));
            case "position-key-array" -> {
                int limit = com.example.platform.timeline.semantics.effect.PositionKeys.LIMIT;
                int count = com.example.platform.timeline.semantics.effect.PositionKeys.MAX_KEYS;
                var axis = Map.of("type", (Object) "integer", "minimum", -limit, "maximum", limit);
                var frame = object(Map.of("time", Map.of("type", "string", "pattern", "^[0-9]+/[1-9][0-9]*$"),
                        "xMicros", axis, "yMicros", axis), List.of("time", "xMicros", "yMicros"));
                yield Map.of("type", "array", "items", frame, "maxItems", count,
                        "not", Map.of("minItems", 1, "maxItems", 1));
            }
            case "single-clip-id-array" -> Map.of("type", "array", "items", ID, "minItems", 1, "maxItems", 1, "uniqueItems", true);
            case "paste-clip-id-array" -> Map.of("type", "array", "items", ID,
                    "minItems", 1, "maxItems", com.example.platform.operation.operation.OperationParameters.PasteClipsParameters.MAX_CLIPS, "uniqueItems", true);
            case "string-array" -> Map.of("type", "array", "items", ID);
            case "alignment-attribute-array" -> Map.of("type", "array", "items", Map.of("oneOf", List.of(
                    attribute("TEXT", STRING), attribute("NUMBER", Map.of("type", "integer")),
                    attribute("BOOLEAN", Map.of("type", "boolean")))));
            default -> throw new IllegalArgumentException("Unsupported wire parameter type " + type);
        };
    }

    private static Map<String, Object> integerRange(int min, int max) {
        return Map.of("type","integer","minimum",min,"maximum",max);
    }

    private static Map<String, Object> attribute(String kind, Map<String, Object> value) {
        return object(Map.of("kind", literal(kind), "tag", ID, "value", value), List.of("kind", "tag", "value"));
    }

    private static Map<String, Object> literal(String value) {
        return Map.of("type", "string", "enum", List.of(value));
    }

    private static Map<String, Object> identity(String key) {
        return object(Map.of(key, ID), List.of(key));
    }

    private static Map<String, Object> object(Map<String, ?> properties, List<String> required) {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("additionalProperties", false);
        if (!required.isEmpty()) schema.put("required", required.stream().sorted().toList());
        return Map.copyOf(schema);
    }
}
