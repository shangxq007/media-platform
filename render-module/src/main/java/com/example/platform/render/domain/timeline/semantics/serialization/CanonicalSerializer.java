package com.example.platform.render.domain.timeline.semantics.serialization;

import com.example.platform.render.domain.timeline.semantics.automation.Automation;
import com.example.platform.render.domain.timeline.semantics.clip.MediaClip;
import com.example.platform.render.domain.timeline.semantics.clip.MediaStreamSourceBinding;
import com.example.platform.render.domain.timeline.semantics.effect.EffectInstance;
import com.example.platform.render.domain.timeline.semantics.temporal.ConstantRateTemporalMapping;
import com.example.platform.render.domain.timeline.semantics.temporal.FreezeTemporalMapping;
import com.example.platform.render.domain.timeline.semantics.transition.TransitionInstance;
import com.example.platform.render.domain.timeline.semantics.validation.TimelineSemanticModel;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

/**
 * Canonical serialization for timeline semantic types.
 * Same semantic Timeline → same canonical payload → same digest.
 */
public final class CanonicalSerializer {

    public static final String CURRENT_SCHEMA_VERSION = "timeline-semantics-v1";
    public static final String SCHEMA_FIELD = "schemaVersion";

    private CanonicalSerializer() {}

    public static String serialize(TimelineSemanticModel model) {
        StringBuilder sb = new StringBuilder();
        sb.append('{');
        sb.append('"').append(SCHEMA_FIELD).append("\":\"").append(escapeJson(model.schemaVersion())).append('"');
        sb.append(',');
        clipsToArray(sb, model.clips());
        sb.append(',');
        transitionsToArray(sb, model.transitions());
        sb.append(',');
        effectsToArray(sb, model.effects());
        sb.append(',');
        automationsToArray(sb, model.automations());
        sb.append('}');
        return sb.toString();
    }

    public static String digest(TimelineSemanticModel model) {
        String canonical = serialize(model);
        try {
            MessageDigest sha256 = MessageDigest.getInstance("SHA-256");
            byte[] hash = sha256.digest(canonical.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    public static void validateSchemaVersion(String version) {
        if (!CURRENT_SCHEMA_VERSION.equals(version)) {
            throw new IllegalArgumentException(
                "Unsupported schema version: " + version + ". Expected: " + CURRENT_SCHEMA_VERSION);
        }
    }

    // ===== Array serializers =====

    private static void clipsToArray(StringBuilder sb, List<MediaClip> clips) {
        sb.append('"').append("clips").append("\":[");
        for (int i = 0; i < clips.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('{');
            appendClip(sb, clips.get(i));
            sb.append('}');
        }
        sb.append(']');
    }

    private static void transitionsToArray(StringBuilder sb, List<TransitionInstance> list) {
        sb.append('"').append("transitions").append("\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('{');
            appendTransition(sb, list.get(i));
            sb.append('}');
        }
        sb.append(']');
    }

    private static void effectsToArray(StringBuilder sb, List<EffectInstance> list) {
        sb.append('"').append("effects").append("\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('{');
            appendEffect(sb, list.get(i));
            sb.append('}');
        }
        sb.append(']');
    }

    private static void automationsToArray(StringBuilder sb, List<Automation.AutomationCurve> list) {
        sb.append('"').append("automations").append("\":[");
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('{');
            appendAutomation(sb, list.get(i));
            sb.append('}');
        }
        sb.append(']');
    }

    // ===== Individual serializers =====

    private static void appendClip(StringBuilder sb, MediaClip clip) {
        strField(sb, "clipId", clip.clipId(), true);
        strField(sb, "trackId", clip.trackId(), false);
        strField(sb, "timelineStart", clip.timelineRange().start().toString(), false);
        strField(sb, "timelineEnd", clip.timelineRange().end().toString(), false);
        strField(sb, "sourceStart", clip.sourceRange().start().toString(), false);
        strField(sb, "sourceEnd", clip.sourceRange().end().toString(), false);
        // TemporalMapping canonical serialization (TM20): typed discriminator +
        // exact rationals + explicit direction; never "IDENTITY" (R1).
        sb.append("\"temporalMapping\":{");
        if (clip.temporalMapping() instanceof ConstantRateTemporalMapping cm) {
            sb.append("\"kind\":\"CONSTANT_RATE\",\"rate\":\"")
                    .append(cm.rate().numerator()).append('/').append(cm.rate().denominator())
                    .append("\",\"direction\":\"").append(cm.direction().name()).append('"');
        } else if (clip.temporalMapping() instanceof FreezeTemporalMapping fm) {
            sb.append("\"kind\":\"FREEZE\",\"sourcePosition\":\"")
                    .append(fm.sourcePosition().toString()).append('"');
        } else {
            throw new IllegalStateException("unsupported TemporalMapping subtype");
        }
        sb.append('}');
        appendMediaStreamSourceBinding(sb, clip.sourceBinding());
    }

    private static void appendMediaStreamSourceBinding(StringBuilder sb, MediaStreamSourceBinding binding) {
        sb.append("\"sourceBinding\":{");
        // ROADMAP_17 (S12): source-kind discriminator participates in canonical serialization
        strField(sb, "sourceKind", binding.sourceKind().name(), true);
        strField(sb, "mediaAssetId", binding.mediaAssetId().value(), false);
        strField(sb, "mediaStreamId", binding.mediaStreamId().value(), false);
        strField(sb, "artifactId", binding.artifactId().value(), false);
        sb.append("\"contentDigest\":{");
        strField(sb, "algorithm", binding.contentDigest().algorithm().name(), true);
        strField(sb, "value", binding.contentDigest().value(), false);
        sb.append('}');
        strField(sb, "sourceRangeStart", binding.sourceRange().start().toString(), false);
        strField(sb, "sourceRangeEnd", binding.sourceRange().end().toString(), false);
        sb.append('}');
    }

    private static void appendTransition(StringBuilder sb, TransitionInstance t) {
        strField(sb, "transitionId", t.transitionId(), true);
        strField(sb, "transitionDefinitionId", t.transitionDefinitionId(), false);
        strField(sb, "transitionDefinitionVersion", t.transitionDefinitionVersion(), false);
        strField(sb, "outgoingClipId", t.outgoingClipId(), false);
        strField(sb, "incomingClipId", t.incomingClipId(), false);
        strField(sb, "mediaType", t.mediaType().name(), false);
        strField(sb, "duration", t.duration().toString(), false);
        strField(sb, "alignment", t.alignment().name(), false);
        strField(sb, "temporalPolicy", t.temporalPolicy().name(), false);
    }

    private static void appendEffect(StringBuilder sb, EffectInstance e) {
        strField(sb, "effectInstanceId", e.effectInstanceId(), true);
        strField(sb, "effectDefinitionId", e.effectDefinitionId(), false);
        strField(sb, "effectDefinitionVersion", e.effectDefinitionVersion(), false);
        strField(sb, "mediaType", e.mediaType().name(), false);
        boolField(sb, "enabled", e.enabled(), false);
        strField(sb, "applicationRangeStart", e.applicationRange().start().toString(), false);
        strField(sb, "applicationRangeEnd", e.applicationRange().end().toString(), false);
    }

    private static void appendAutomation(StringBuilder sb, Automation.AutomationCurve c) {
        strField(sb, "automationId", c.automationId(), true);
        strField(sb, "targetEntityId", c.targetEntityId(), false);
        strField(sb, "parameterPath", c.parameterPath(), false);
        strField(sb, "valueType", c.valueType(), false);
        strField(sb, "extrapolation", c.extrapolation().name(), false);
        sb.append('"').append("keyframes").append("\":[");
        for (int i = 0; i < c.keyframes().size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('{');
            appendKeyframe(sb, c.keyframes().get(i));
            sb.append('}');
        }
        sb.append(']');
    }

    private static void appendKeyframe(StringBuilder sb, Automation.Keyframe k) {
        strField(sb, "keyframeId", k.keyframeId(), true);
        strField(sb, "time", k.time().toString(), false);
        doubleField(sb, "value", k.value(), false);
        strField(sb, "interpolation", k.interpolation().name(), false);
    }

    // ===== Field appenders =====

    private static void strField(StringBuilder sb, String key, String value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(escapeJson(key)).append("\":\"").append(escapeJson(value)).append('"');
    }

    private static void longField(StringBuilder sb, String key, long value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(escapeJson(key)).append("\":").append(value);
    }

    private static void doubleField(StringBuilder sb, String key, double value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(escapeJson(key)).append("\":").append(value);
    }

    private static void boolField(StringBuilder sb, String key, boolean value, boolean first) {
        if (!first) sb.append(',');
        sb.append('"').append(escapeJson(key)).append("\":").append(value);
    }

    public static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    public record SchemaEnvelope(String schemaVersion) {
        public SchemaEnvelope {
            Objects.requireNonNull(schemaVersion, "schemaVersion");
            if (schemaVersion.isBlank()) {
                throw new IllegalArgumentException("schemaVersion must not be blank");
            }
        }
    }
}
