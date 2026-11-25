package com.example.platform.operation.application;

import com.example.platform.operation.operation.OperationDefinition;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Projection of the code-owned definitions onto the Timeline batch transport. */
public final class TimelineOperationWireContract {
    private TimelineOperationWireContract() {}

    public record Description(String id, String version, String lifecycle, String target,
            int minCardinality, int maxCardinality, String applicability, List<String> invocationModes,
            Map<String, Field> parameters, List<String> inputKinds, String atomicity, ResultContract result, String purpose, Map<String, String> permissions,
            String versionPolicy, List<String> validation) {}

    public record Field(String type, boolean required) {}
    public record ResultContract(List<String> outcomes, String revisionIdentity, String contentIdentity) {}

    public static Description describe(OperationDefinition definition) {
        String id = definition.definitionId().value();
        var fields = new LinkedHashMap<String, Field>();
        boolean batch = true;
        switch (id) {
            case "timeline.clip.paste", "timeline.clip.ripple-insert" -> {
                fields(fields, "revision-id", "trackId");
                fields(fields, "string", "timelineStart");
                fields(fields, id.equals("timeline.clip.ripple-insert") ? "single-clip-id-array" : "paste-clip-id-array", "sourceClipIds", "newClipIds");
            }
            case "timeline.revision.restore" -> fields(fields, "revision-id", "sourceRevisionId");
            case "timeline.initialize", "timeline.import", "timeline.track.add" -> batch = false;
            case "timeline.media-clip.add" -> {
                fields(fields, "string", "trackId", "clipId", "mediaAssetId", "mediaStreamId", "artifactId",
                        "contentDigest", "sourceStart", "sourceEnd", "timelineStart", "timelineEnd");
                fields(fields, "integer", "rateNumerator", "rateDenominator");
                fields.put("direction", new Field("string", false));
            }
            case "timeline.move", "timeline.clip.relocate" -> {
                fields(fields, "string", "delta");
                fields(fields, "boolean", "absolute");
                if (id.equals("timeline.clip.relocate")) fields(fields, "string", "destinationTrackId");
            }
            case "timeline.trim" -> fields(fields, "string", "edge", "delta");
            case "timeline.clip.split" -> fields(fields, "string", "rightClipId", "splitTimelineTime");
            case "timeline.composition.aspect.set" -> fields(fields, "composition-aspect-rational-v1", "aspectRatio");
            case "timeline.clip.transform.set" -> {
                fields(fields, "string", "format");
                fields(fields, "integer", "positionXMicros", "positionYMicros", "scaleXMicros", "scaleYMicros",
                        "rotationMicroDegrees", "cropLeftMicros", "cropRightMicros", "cropTopMicros",
                        "cropBottomMicros", "opacityMicros");
            }
            case "timeline.clip.effect.set" -> {
                fields(fields, "number", "brightness", "contrast", "saturation", "fadeInSeconds", "fadeOutSeconds");
                fields(fields, "boolean", "grayscale", "enabled");
            }
            case "timeline.transition.cross-dissolve.create" -> fields(fields, "string", "transitionId", "duration", "incomingClipId");
            case "timeline.transition.cross-dissolve.update" -> fields(fields, "string", "duration");
            case "timeline.transition.cross-dissolve.delete" -> {}
            case "timeline.temporal.set-rate" -> fields(fields, "integer", "numerator", "denominator");
            case "timeline.temporal.set-direction" -> fields(fields, "string", "direction");
            case "timeline.temporal.freeze" -> fields(fields, "string", "sourcePosition");
            case "audio.gain.set" -> fields(fields, "number", "linear");
            case "audio.balance.set" -> fields(fields, "number", "value");
            case "audio.mute.set" -> fields(fields, "boolean", "muted");
            case "timeline.group.create" -> {
                fields(fields, "string", "groupId");
                fields(fields, "string-array", "members");
            }
            case "timeline.group.update-members" -> {
                fields.put("membersToAdd", new Field("string-array", false));
                fields.put("membersToRemove", new Field("string-array", false));
            }
            case "timeline.sync.create" -> fields(fields, "string", "endpointA", "endpointB", "localAnchorA", "localAnchorB");
            case "timeline.sync.update-anchor" -> fields(fields, "string", "localAnchorA", "localAnchorB");
            case "timeline.alignment.create" -> {
                fields(fields, "string", "alignmentId");
                fields(fields, "string-array", "sourceUnits", "derivedUnits");
                fields.put("attributes", new Field("alignment-attribute-array", false));
            }
            case "timeline.delete", "timeline.clip.ripple-delete", "timeline.clip.effect.remove", "timeline.sync.remove", "timeline.group.remove" -> {}
            default -> {
                if (definition.targetKind() != OperationDefinition.TargetKind.TEXT) {
                    throw new IllegalArgumentException("Unknown Timeline transport contract");
                }
                for (var field : definition.parameterType().getRecordComponents()) {
                    if (!field.getName().equals("textElementId")) {
                        fields(fields, field.getType().getSimpleName(), field.getName());
                    }
                }
            }
        }
        boolean aspect = definition.equals(OperationDefinition.V3.SET_COMPOSITION_ASPECT);
        String target = definition.targetKind() == OperationDefinition.TargetKind.TEXT
                ? (definition.equals(OperationDefinition.V1.ADD_TEXT_ELEMENT) ? "TIMELINE" : "TEXT_ELEMENT")
                : definition.targetKind().name();
        return new Description(id, definition.version().toString(), definition.lifecycle().name(), target,
                definition.minCardinality(), definition.maxCardinality(), batch ? "BATCH" : "DEDICATED_ENTRY",
                batch ? List.of("PREVIEW", "APPLY") : List.of(), Map.copyOf(fields),
                aspect ? List.of("source-display-aspect-v1") : List.of(), "ONE_TIMELINE_REVISION", batch ? new ResultContract(List.of("APPLIED", "NO_OP"), "newRevisionId", "newContentHash")
                        : new ResultContract(List.of("APPLIED", "NO_OP"), "newRevisionId", "resultContentHash|unchangedContentHash"),
                id.replace('.', ' ').replace('-', ' '), Map.of("inspect", "PROJECT.READ", "apply", "PROJECT.WRITE"),
                "An omitted version selects the definition version pinned by batch protocol v2.",
                List.of("exact scoped revision and content hash", "typed selector and parameter validation",
                        "candidate Timeline invariants"));
    }

    private static void fields(Map<String, Field> result, String type, String... names) {
        for (String name : names) result.put(name, new Field(type, true));
    }

    static void validate(OperationDefinition definition, TimelineOperationStep step, Map<String, Object> values) {
        Description wire = describe(definition);
        if (definition.equals(OperationDefinition.V3.SET_VIDEO_TRANSFORM)
                && !com.example.platform.timeline.semantics.effect.VideoTransform.FORMAT.equals(values.get("format"))) {
            throw new IllegalArgumentException("Unsupported composition transform format");
        }
        if (!wire.parameters().keySet().containsAll(values.keySet())) {
            throw new IllegalArgumentException("Unknown semantic parameter");
        }
        wire.parameters().forEach((name, field) -> {
            if (field.required() && !values.containsKey(name)) throw new IllegalArgumentException(name + " required");
            if ("single-clip-id-array".equals(field.type()) && (!(values.get(name) instanceof List<?> ids) || ids.size() != 1)) {
                throw new IllegalArgumentException("Ripple Insert requires exactly one clip in " + name);
            }
            if (values.containsKey(name) && values.get(name) == null) throw new IllegalArgumentException(name + " cannot be null");
        });
        var t = step.target();
        var keys = new java.util.HashSet<String>();
        if (t != null) {
            if (t.clipId() != null) keys.add("clipId");
            if (t.clipIds() != null) keys.add("clipIds");
            if (t.audioTrackId() != null) keys.add("audioTrackId");
            if (t.groupId() != null) keys.add("groupId");
            if (t.syncIdentityKey() != null) keys.add("syncIdentityKey");
            if (t.transitionId() != null) keys.add("transitionId");
            if (t.textElementId() != null) keys.add("textElementId");
        }
        if (definition.equals(OperationDefinition.V1.CREATE_GROUP)) {
            if (t != null || !(values.get("members") instanceof List<?> members)
                    || members.size() < definition.minCardinality() || new java.util.HashSet<>(members).size() != members.size()) {
                throw new IllegalArgumentException("Group creation requires distinct member identities as its clip scope");
            }
        }
        boolean valid = switch (wire.target()) {
            case "TIMELINE", "ALIGNMENT" -> t == null;
            case "AUDIO" -> keys.equals(Set.of("clipId", "audioTrackId"));
            case "GROUP" -> keys.equals(Set.of("groupId"));
            case "SYNC" -> keys.equals(Set.of("syncIdentityKey"));
            case "TRANSITION" -> keys.equals(Set.of("transitionId"));
            case "TEXT_ELEMENT" -> keys.equals(Set.of("textElementId"));
            case "CLIP_SCOPE" -> keys.equals(Set.of("clipId"))
                    || keys.equals(Set.of("clipIds")) && !t.clipIds().isEmpty()
                    || t == null && definition.equals(OperationDefinition.V1.CREATE_GROUP);
            default -> false;
        };
        if (t != null && t.clipIds() != null && (t.clipIds().size() > definition.maxCardinality()
                || new java.util.HashSet<>(t.clipIds()).size() != t.clipIds().size())) {
            throw new IllegalArgumentException("Invalid clip selection cardinality");
        }
        if (!valid) throw new IllegalArgumentException("Target coordinates differ from definition");
    }
}
