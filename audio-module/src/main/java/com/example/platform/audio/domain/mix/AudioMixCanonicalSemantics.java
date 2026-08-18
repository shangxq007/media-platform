package com.example.platform.audio.domain.mix;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;

/**
 * COMPONENT_LOCAL_SEMANTIC_AUTHORITY_V1 (CHECKPOINT_A Round 4): the single
 * Audio-domain-owned canonical semantic authority for {@link AudioMix}.
 *
 * <p>Owns ONLY AudioMix-local semantics:
 * <ul>
 *   <li>canonical authored representation (deterministic JSON — no
 *       Timeline-side field grammar)</li>
 *   <li>deterministic equality ({@link #localSemanticsEquals})</li>
 *   <li>deterministic fingerprint ({@link #semanticFingerprint})</li>
 *   <li>lossless encode / decode ({@link #canonicalJson} /
 *       {@link #fromCanonicalJson})</li>
 * </ul>
 *
 * <p>Timeline keeps aggregate orchestration: whole-AudioMix conservative
 * comparison/replacement in diff/patch/merge, collection membership at the
 * aggregate level. Timeline MUST NOT understand AudioMasterBus internals,
 * AudioRoute internals, gain/mute/balance internals, or DSP grammar — all of
 * that stays inside this authority (and the record types it owns).
 *
 * <p>The whole-component three-way merge remains exactly conservative:
 * identical values merge silently, divergent values conflict; no per-route,
 * DSP, send/return, or audio-graph merge is defined here or anywhere.
 */
public final class AudioMixCanonicalSemantics {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true);

    private AudioMixCanonicalSemantics() {}

    /** Deterministic canonical JSON value — the single lossless representation. */
    public static ObjectNode canonicalValue(AudioMix mix) {
        AudioMix m = mix == null ? AudioMix.empty() : mix;
        try {
            return (ObjectNode) MAPPER.valueToTree(m);
        } catch (Exception e) {
            throw new IllegalStateException("AudioMix canonical encoding failed", e);
        }
    }

    /** Deterministic canonical JSON string. */
    public static String canonicalJson(AudioMix mix) {
        try {
            return MAPPER.writeValueAsString(canonicalValue(mix));
        } catch (Exception e) {
            throw new IllegalStateException("AudioMix canonical encoding failed", e);
        }
    }

    /** Lossless decode from canonical JSON. Malformed input fails closed. */
    public static AudioMix fromCanonicalJson(JsonNode node) {
        if (node == null || node.isNull() || !node.isObject() || node.isEmpty()) {
            return AudioMix.empty();
        }
        try {
            return MAPPER.treeToValue(node, AudioMix.class);
        } catch (Exception e) {
            throw new IllegalStateException("AudioMix canonical decode failed: " + e.getMessage(), e);
        }
    }

    /** Deterministic fingerprint — SHA-256 over the canonical value. */
    public static String semanticFingerprint(AudioMix mix) {
        try {
            byte[] json = MAPPER.writeValueAsBytes(canonicalValue(mix));
            byte[] hash = MessageDigest.getInstance("SHA-256").digest(json);
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("AudioMix canonical fingerprint failed", e);
        }
    }

    /** Local semantic equality — canonical representation equality. */
    public static boolean localSemanticsEquals(AudioMix a, AudioMix b) {
        return canonicalValue(a).equals(canonicalValue(b));
    }
}
