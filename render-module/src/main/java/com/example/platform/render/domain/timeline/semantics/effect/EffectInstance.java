package com.example.platform.render.domain.timeline.semantics.effect;

import com.example.platform.shared.time.MediaTime;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Effect instance: a strongly-typed effect applied to a clip or track.
 *
 * @param effectInstanceId      stable, unique identifier
 * @param effectDefinitionId    reference to the effect definition
 * @param effectDefinitionVersion version of the definition
 * @param mediaType             video, audio, etc.
 * @param enabled               whether the effect is active
 * @param applicationRange      when the effect is applied (timeline-relative)
 * @param parameters            typed parameter map
 * @param automationBindings    parameter path -> automation curve ID
 * @param provenance            source of this effect instance
 */
public record EffectInstance(
    String effectInstanceId,
    String effectDefinitionId,
    String effectDefinitionVersion,
    EffectMediaType mediaType,
    boolean enabled,
    MediaClip.TimeRange applicationRange,
    Map<String, String> parameters,
    Map<String, String> automationBindings,
    EffectProvenance provenance
) {
    public EffectInstance {
        Objects.requireNonNull(effectInstanceId, "effectInstanceId");
        Objects.requireNonNull(effectDefinitionId, "effectDefinitionId");
        Objects.requireNonNull(mediaType, "mediaType");
        if (effectInstanceId.isBlank()) throw new IllegalArgumentException("effectInstanceId must not be blank");
        if (effectDefinitionId.isBlank()) throw new IllegalArgumentException("effectDefinitionId must not be blank");
        parameters = parameters != null ? Map.copyOf(parameters) : Map.of();
        automationBindings = automationBindings != null ? Map.copyOf(automationBindings) : Map.of();
        provenance = provenance != null ? provenance : EffectProvenance.untracked();
    }

    /**
     * Media type for effects.
     */
    public enum EffectMediaType {
        VIDEO, AUDIO, AUDIO_VIDEO
    }

    /**
     * Provenance tracking for an effect instance.
     */
    public record EffectProvenance(String source, String sourceId, long createdAt) {
        public EffectProvenance {
            Objects.requireNonNull(source, "source");
            if (source.isBlank()) throw new IllegalArgumentException("source must not be blank");
        }

        public static EffectProvenance untracked() {
            return new EffectProvenance("untracked", "", System.currentTimeMillis());
        }
    }

    /**
     * Returns true if this effect targets video.
     */
    public boolean isVideoEffect() {
        return mediaType == EffectMediaType.VIDEO || mediaType == EffectMediaType.AUDIO_VIDEO;
    }

    /**
     * Returns true if this effect targets audio.
     */
    public boolean isAudioEffect() {
        return mediaType == EffectMediaType.AUDIO || mediaType == EffectMediaType.AUDIO_VIDEO;
    }

    /**
     * Effect definition contract.
     *
     * @param definitionId              stable definition ID
     * @param version                   semantic version string
     * @param category                  effect category
     * @param supportedMediaTypes       which media types this effect supports
     * @param parameterSchema           parameter path -> type and constraints
     * @param temporalBehavior          how the effect affects duration
     * @param deterministicProperties   which properties are guaranteed deterministic
     * @param requiredCapabilities      which backend capabilities are required
     * @param supportedBackendCapabilities which backends can execute this
     */
    public record EffectDefinition(
        String definitionId,
        String version,
        EffectCategory category,
        List<EffectMediaType> supportedMediaTypes,
        Map<String, ParameterSchema> parameterSchema,
        EffectTemporalBehavior temporalBehavior,
        List<String> deterministicProperties,
        List<String> requiredCapabilities,
        List<String> supportedBackendCapabilities
    ) {
        public EffectDefinition {
            Objects.requireNonNull(definitionId, "definitionId");
            Objects.requireNonNull(version, "version");
            Objects.requireNonNull(category, "category");
            Objects.requireNonNull(supportedMediaTypes, "supportedMediaTypes");
            Objects.requireNonNull(parameterSchema, "parameterSchema");
            Objects.requireNonNull(temporalBehavior, "temporalBehavior");
            if (definitionId.isBlank()) throw new IllegalArgumentException("definitionId must not be blank");
            if (version.isBlank()) throw new IllegalArgumentException("version must not be blank");
            supportedMediaTypes = List.copyOf(supportedMediaTypes);
            parameterSchema = Map.copyOf(parameterSchema);
            deterministicProperties = deterministicProperties != null ? List.copyOf(deterministicProperties) : List.of();
            requiredCapabilities = requiredCapabilities != null ? List.copyOf(requiredCapabilities) : List.of();
            supportedBackendCapabilities = supportedBackendCapabilities != null ? List.copyOf(supportedBackendCapabilities) : List.of();
        }
    }

    /**
     * Effect categories.
     */
    public enum EffectCategory {
        TRANSFORM, CROP, OPACITY, BLEND_MODE, COLOR_ADJUSTMENT,
        GAUSSIAN_BLUR, FADE, GAIN, PAN, EQUALIZER, COMPRESSOR, LIMITER
    }

    /**
     * Temporal behavior of an effect.
     */
    public enum EffectTemporalBehavior {
        PRESERVE_DURATION,
        CHANGE_DURATION,
        REQUIRE_SOURCE_HANDLES,
        ADD_PRE_ROLL,
        ADD_POST_ROLL,
        VARIABLE_OUTPUT_DURATION
    }

    /**
     * Parameter schema entry.
     *
     * @param path         parameter path (e.g., "transform.x")
     * @param valueType    type string (e.g., "float", "int", "string", "enum:...")
     * @param minValue     minimum allowed value (or null if no min)
     * @param maxValue     maximum allowed value (or null if no max)
     * @param defaultValue default value as string
     * @param enumValues   allowed enum values (for enum types)
     */
    public record ParameterSchema(
        String path,
        String valueType,
        Double minValue,
        Double maxValue,
        String defaultValue,
        List<String> enumValues
    ) {
        public ParameterSchema {
            Objects.requireNonNull(path, "path");
            Objects.requireNonNull(valueType, "valueType");
            if (path.isBlank()) throw new IllegalArgumentException("path must not be blank");
            enumValues = enumValues != null ? List.copyOf(enumValues) : List.of();
        }

        /**
         * Returns true if the given value string is valid for this schema.
         */
        public boolean isValidValue(String value) {
            if (value == null) return false;
            if (valueType.startsWith("enum:")) {
                return enumValues.contains(value);
            }
            try {
                double d = Double.parseDouble(value);
                if (minValue != null && d < minValue) return false;
                if (maxValue != null && d > maxValue) return false;
                return true;
            } catch (NumberFormatException e) {
                return false;
            }
        }
    }
}
